package com.personalenglishai.backend.service.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextRequest;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.learning.LearningEvidence;
import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import com.personalenglishai.backend.entity.learning.LearningRawCandidate;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.learning.LearningEvidenceMapper;
import com.personalenglishai.backend.mapper.learning.LearningExtractionRunMapper;
import com.personalenglishai.backend.mapper.learning.LearningRawCandidateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LearningDeepseekCleaningService {
    private static final Logger log = LoggerFactory.getLogger(LearningDeepseekCleaningService.class);
    private static final String EXTRACTOR_TYPE = "deepseek";
    private static final String LOCAL_EXTRACTOR = "local";
    private static final BigDecimal EVIDENCE_THRESHOLD = BigDecimal.valueOf(65);

    private final LearningExtractionRunMapper extractionRunMapper;
    private final LearningRawCandidateMapper rawCandidateMapper;
    private final LearningEvidenceMapper evidenceMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public LearningDeepseekCleaningService(
            LearningExtractionRunMapper extractionRunMapper,
            LearningRawCandidateMapper rawCandidateMapper,
            LearningEvidenceMapper evidenceMapper,
            AssistantMessageMapper assistantMessageMapper,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper,
            @Value("${learning.capture.deepseek.model:deepseek-chat}") String model) {
        this.extractionRunMapper = extractionRunMapper;
        this.rawCandidateMapper = rawCandidateMapper;
        this.evidenceMapper = evidenceMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public int processPendingRuns(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        List<LearningExtractionRun> runs = extractionRunMapper.selectPendingByExtractor(EXTRACTOR_TYPE, safeLimit);
        int processed = 0;
        for (LearningExtractionRun run : runs) {
            if (processRun(run)) {
                processed++;
            }
        }
        return processed;
    }

    public int processPendingRunsForUserDay(Long userId, LocalDate date, int limit) {
        if (userId == null || date == null) {
            return 0;
        }
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 100);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<LearningExtractionRun> runs = extractionRunMapper.selectPendingByExtractorAndUserCreatedRange(
                EXTRACTOR_TYPE,
                userId,
                from,
                to,
                safeLimit);
        int processed = 0;
        for (LearningExtractionRun run : runs) {
            if (processRun(run)) {
                processed++;
            }
        }
        return processed;
    }

    @Transactional
    public boolean processMessage(String messageUid) {
        if (messageUid == null || messageUid.isBlank()) {
            return false;
        }
        LearningExtractionRun existing = extractionRunMapper.findByMessageAndExtractor(messageUid, EXTRACTOR_TYPE);
        if (existing != null) {
            return processRun(existing);
        }

        AssistantMessage message = assistantMessageMapper.findByMessageUid(messageUid);
        if (message == null || message.getUserId() == null || message.getConversationUid() == null) {
            return false;
        }
        LearningExtractionRun run = new LearningExtractionRun();
        run.setRunUid("lrun-" + UUID.randomUUID());
        run.setUserId(message.getUserId());
        run.setConversationUid(message.getConversationUid());
        run.setMessageUid(messageUid);
        run.setExtractorType(EXTRACTOR_TYPE);
        run.setStatus("pending");
        extractionRunMapper.insert(run);
        return processRun(run);
    }

    @Transactional
    public boolean processRun(LearningExtractionRun run) {
        if (run == null || run.getRunUid() == null) {
            return false;
        }
        extractionRunMapper.markProcessing(run.getRunUid());
        try {
            AssistantMessage message = assistantMessageMapper.findByMessageUid(run.getMessageUid());
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                extractionRunMapper.updateFailed(run.getRunUid(), "assistant message not found or empty");
                return false;
            }

            OpenAiResponsesTextResult result = openAiClient.createTextResponse(new OpenAiResponsesTextRequest(
                    EXTRACTOR_TYPE,
                    model,
                    buildInstructions(),
                    buildInput(message),
                    null,
                    "learning-capture:" + run.getUserId(),
                    "24h",
                    false,
                    1200));
            List<ModelCandidate> candidates = parseCandidates(result.outputText());
            for (ModelCandidate candidate : candidates) {
                persistCandidate(run, message, candidate);
            }
            extractionRunMapper.updateCompleted(
                    run.getRunUid(),
                    model,
                    toLong(result.inputTokens()),
                    toLong(result.outputTokens()),
                    toJson(new DeepseekRunResult(result.responseId(), candidates.size(), result.cachedTokens(), result.totalTokens())));
            return true;
        } catch (Exception e) {
            log.warn("deepseek learning cleaning failed. runUid={} messageUid={}", run.getRunUid(), run.getMessageUid(), e);
            extractionRunMapper.updateFailed(run.getRunUid(), e.getMessage());
            return false;
        }
    }

    private void persistCandidate(LearningExtractionRun run, AssistantMessage message, ModelCandidate modelCandidate) {
        String type = normalizeType(modelCandidate.type());
        String normalizedText = normalize(modelCandidate.text());
        if (type == null || normalizedText.isBlank()) {
            return;
        }

        BigDecimal score = BigDecimal.valueOf(modelCandidate.confidence())
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        String comparisonStatus = rawCandidateMapper.findByDedupeKey(
                run.getUserId(),
                type,
                truncate(normalizedText, 255),
                LOCAL_EXTRACTOR) == null ? "deepseek_only" : "overlap";

        LocalDateTime now = LocalDateTime.now();
        LearningRawCandidate candidate = new LearningRawCandidate();
        candidate.setCandidateUid("lcand-" + UUID.randomUUID());
        candidate.setUserId(run.getUserId());
        candidate.setConversationUid(run.getConversationUid());
        candidate.setMessageUid(run.getMessageUid());
        candidate.setSourceRole(message.getRole());
        candidate.setCandidateType(type);
        candidate.setText(truncate(modelCandidate.text(), 1000));
        candidate.setNormalizedText(truncate(normalizedText, 255));
        candidate.setExtractorType(EXTRACTOR_TYPE);
        candidate.setExtractionRunUid(run.getRunUid());
        candidate.setSourceExcerpt(truncate(modelCandidate.reason(), 300));
        candidate.setModelConfidence(BigDecimal.valueOf(modelCandidate.confidence()).setScale(4, RoundingMode.HALF_UP));
        candidate.setComparisonStatus(comparisonStatus);
        candidate.setJudgeScore(score);
        candidate.setFinalCandidateScore(score);
        candidate.setOccurrenceCount(1);
        candidate.setFirstSeenAt(now);
        candidate.setLastSeenAt(now);
        rawCandidateMapper.insertOrUpdateOccurrence(candidate);

        LearningRawCandidate persisted = rawCandidateMapper.findByDedupeKey(
                run.getUserId(),
                type,
                candidate.getNormalizedText(),
                EXTRACTOR_TYPE);
        if (persisted != null) {
            maybeCreateEvidence(run, persisted, type, modelCandidate, score, comparisonStatus);
        }
    }

    private void maybeCreateEvidence(
            LearningExtractionRun run,
            LearningRawCandidate candidate,
            String type,
            ModelCandidate modelCandidate,
            BigDecimal score,
            String comparisonStatus) {
        if (score.compareTo(EVIDENCE_THRESHOLD) < 0 || evidenceMapper.findByCandidateUid(candidate.getCandidateUid()) != null) {
            return;
        }
        LearningEvidence evidence = new LearningEvidence();
        evidence.setEvidenceUid("levd-" + UUID.randomUUID());
        evidence.setCandidateUid(candidate.getCandidateUid());
        evidence.setUserId(run.getUserId());
        evidence.setEvidenceType(toEvidenceType(type));
        evidence.setText(truncate(modelCandidate.text(), 1000));
        evidence.setScore(score);
        evidence.setSignalsJson(toJson(modelCandidate));
        evidence.setModelJudgementJson(toJson(modelCandidate));
        evidence.setExtractorSourcesJson("[\"deepseek\"]");
        evidence.setComparisonStatus(comparisonStatus);
        evidence.setSourceMessageIdsJson("[\"" + run.getMessageUid() + "\"]");
        evidence.setStatus("pending");
        evidenceMapper.insert(evidence);
    }

    private List<ModelCandidate> parseCandidates(String rawOutput) throws Exception {
        String json = stripJsonFence(rawOutput);
        JsonNode root = objectMapper.readTree(json);
        JsonNode array = root.has("candidates") ? root.get("candidates") : root;
        if (!array.isArray()) {
            return List.of();
        }
        List<ModelCandidate> candidates = new ArrayList<>();
        for (JsonNode node : array) {
            candidates.add(new ModelCandidate(
                    node.path("type").asText(""),
                    node.path("text").asText(""),
                    node.path("reason").asText(""),
                    clampConfidence(node.path("confidence").asDouble(0.5))));
        }
        return candidates;
    }

    private String stripJsonFence(String rawOutput) {
        String value = rawOutput == null ? "" : rawOutput.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private String buildInstructions() {
        return """
                你是英语学习内容清洗 Agent。只从输入消息中提取用户值得复习的英语学习资产。
                输出严格 JSON，不要 Markdown。
                格式：
                {"candidates":[{"type":"word|phrase|sentence|sentence_pattern","text":"...","reason":"...","confidence":0.0}]}
                规则：
                - 优先选择用户练习、讲解重点、可迁移写作表达、自然短语、句型模板。
                - 不要提取普通功能词、太泛的短词、无学习价值的整段废话。
                - sentence 应该是完整英文句子；phrase 应该是 2 到 8 个词的表达；sentence_pattern 是可迁移模板。
                - 最多返回 20 条。
                """;
    }

    private String buildInput(AssistantMessage message) {
        return """
                role: %s
                message_uid: %s
                content:
                %s
                """.formatted(message.getRole(), message.getMessageUid(), message.getContent());
    }

    private String normalizeType(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "word", "phrase", "sentence", "sentence_pattern" -> type.trim().toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9'\\s+-]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String toEvidenceType(String candidateType) {
        return switch (candidateType) {
            case "sentence" -> "practice_sentence";
            case "sentence_pattern" -> "sentence_pattern";
            default -> "key_expression";
        };
    }

    private double clampConfidence(double confidence) {
        return Math.max(0, Math.min(1, confidence));
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ModelCandidate(String type, String text, String reason, double confidence) {
    }

    private record DeepseekRunResult(String responseId, int candidateCount, Integer cachedTokens, Integer totalTokens) {
    }
}
