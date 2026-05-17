package com.personalenglishai.backend.service.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.learning.LearningEvidence;
import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import com.personalenglishai.backend.entity.learning.LearningRawCandidate;
import com.personalenglishai.backend.mapper.learning.LearningEvidenceMapper;
import com.personalenglishai.backend.mapper.learning.LearningExtractionRunMapper;
import com.personalenglishai.backend.mapper.learning.LearningRawCandidateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LearningCaptureService {
    private static final Logger log = LoggerFactory.getLogger(LearningCaptureService.class);
    private static final String LOCAL_EXTRACTOR = "local";
    private static final String DEEPSEEK_EXTRACTOR = "deepseek";
    private static final BigDecimal EVIDENCE_THRESHOLD = BigDecimal.valueOf(60);

    private final LearningExtractionRunMapper extractionRunMapper;
    private final LearningRawCandidateMapper rawCandidateMapper;
    private final LearningEvidenceMapper evidenceMapper;
    private final LearningLocalCandidateExtractor localCandidateExtractor;
    private final ObjectMapper objectMapper;

    public LearningCaptureService(
            LearningExtractionRunMapper extractionRunMapper,
            LearningRawCandidateMapper rawCandidateMapper,
            LearningEvidenceMapper evidenceMapper,
            LearningLocalCandidateExtractor localCandidateExtractor,
            ObjectMapper objectMapper) {
        this.extractionRunMapper = extractionRunMapper;
        this.rawCandidateMapper = rawCandidateMapper;
        this.evidenceMapper = evidenceMapper;
        this.localCandidateExtractor = localCandidateExtractor;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void captureMessage(
            Long userId,
            String conversationUid,
            String messageUid,
            String sourceRole,
            String content) {
        if (userId == null || isBlank(conversationUid) || isBlank(messageUid) || isBlank(content)) {
            return;
        }

        try {
            captureMessageInternal(userId, conversationUid, messageUid, sourceRole, content);
        } catch (Exception e) {
            log.warn("learning capture failed. userId={} conversationUid={} messageUid={} role={}",
                    userId,
                    conversationUid,
                    messageUid,
                    sourceRole,
                    e);
        }
    }

    private void captureMessageInternal(
            Long userId,
            String conversationUid,
            String messageUid,
            String sourceRole,
            String content) {
        LearningExtractionRun localRun = ensureRun(userId, conversationUid, messageUid, LOCAL_EXTRACTOR, "pending");
        ensureRun(userId, conversationUid, messageUid, DEEPSEEK_EXTRACTOR, "pending");

        extractionRunMapper.markProcessing(localRun.getRunUid());
        List<LearningLocalCandidateExtractor.ExtractedCandidate> candidates = localCandidateExtractor.extract(content);
        for (LearningLocalCandidateExtractor.ExtractedCandidate candidate : candidates) {
            LearningRawCandidate persisted = upsertCandidate(
                    userId,
                    conversationUid,
                    messageUid,
                    sourceRole,
                    content,
                    localRun.getRunUid(),
                    candidate);
            maybeCreateEvidence(userId, messageUid, persisted, candidate);
        }

        extractionRunMapper.updateCompleted(
                localRun.getRunUid(),
                "local-regex-v1",
                null,
                null,
                toJson(Map.of("candidateCount", candidates.size(), "sourceRole", sourceRole)));
    }

    private LearningExtractionRun ensureRun(
            Long userId,
            String conversationUid,
            String messageUid,
            String extractorType,
            String status) {
        LearningExtractionRun existing = extractionRunMapper.findByMessageAndExtractor(messageUid, extractorType);
        if (existing != null) {
            return existing;
        }
        LearningExtractionRun run = new LearningExtractionRun();
        run.setRunUid("lrun-" + UUID.randomUUID());
        run.setUserId(userId);
        run.setConversationUid(conversationUid);
        run.setMessageUid(messageUid);
        run.setExtractorType(extractorType);
        run.setStatus(status);
        extractionRunMapper.insert(run);
        return extractionRunMapper.findByRunUid(run.getRunUid());
    }

    private LearningRawCandidate upsertCandidate(
            Long userId,
            String conversationUid,
            String messageUid,
            String sourceRole,
            String content,
            String runUid,
            LearningLocalCandidateExtractor.ExtractedCandidate extracted) {
        LocalDateTime now = LocalDateTime.now();
        LearningRawCandidate candidate = new LearningRawCandidate();
        candidate.setCandidateUid("lcand-" + UUID.randomUUID());
        candidate.setUserId(userId);
        candidate.setConversationUid(conversationUid);
        candidate.setMessageUid(messageUid);
        candidate.setSourceRole(sourceRole);
        candidate.setCandidateType(extracted.type());
        candidate.setText(truncate(extracted.text(), 1000));
        candidate.setNormalizedText(truncate(extracted.normalizedText(), 255));
        candidate.setExtractorType(LOCAL_EXTRACTOR);
        candidate.setExtractionRunUid(runUid);
        candidate.setSourceExcerpt(buildExcerpt(content, extracted.text()));
        candidate.setLocalSignalsJson(extracted.signalsJson());
        candidate.setLocalFeatureJson(toJson(Map.of(
                "sourceRole", sourceRole,
                "normalizedLength", extracted.normalizedText().length())));
        candidate.setLocalPrefilterScore(extracted.score());
        candidate.setFinalCandidateScore(extracted.score());
        candidate.setOccurrenceCount(extracted.occurrenceCount());
        candidate.setFirstSeenAt(now);
        candidate.setLastSeenAt(now);
        rawCandidateMapper.insertOrUpdateOccurrence(candidate);

        LearningRawCandidate persisted = rawCandidateMapper.findByDedupeKey(
                userId,
                extracted.type(),
                candidate.getNormalizedText(),
                LOCAL_EXTRACTOR);
        return persisted == null ? candidate : persisted;
    }

    private void maybeCreateEvidence(
            Long userId,
            String messageUid,
            LearningRawCandidate persisted,
            LearningLocalCandidateExtractor.ExtractedCandidate extracted) {
        if (persisted == null || extracted.score().compareTo(EVIDENCE_THRESHOLD) < 0) {
            return;
        }
        if (evidenceMapper.findByCandidateUid(persisted.getCandidateUid()) != null) {
            return;
        }

        LearningEvidence evidence = new LearningEvidence();
        evidence.setEvidenceUid("levd-" + UUID.randomUUID());
        evidence.setCandidateUid(persisted.getCandidateUid());
        evidence.setUserId(userId);
        evidence.setEvidenceType(toEvidenceType(extracted.type()));
        evidence.setText(truncate(extracted.text(), 1000));
        evidence.setScore(extracted.score());
        evidence.setSignalsJson(extracted.signalsJson());
        evidence.setExtractorSourcesJson("[\"local\"]");
        evidence.setComparisonStatus(persisted.getComparisonStatus());
        evidence.setSourceMessageIdsJson("[\"" + messageUid + "\"]");
        evidence.setStatus("pending");
        evidenceMapper.insert(evidence);
    }

    private String toEvidenceType(String candidateType) {
        return switch (candidateType) {
            case "sentence" -> "practice_sentence";
            case "sentence_pattern" -> "sentence_pattern";
            default -> "key_expression";
        };
    }

    private String buildExcerpt(String content, String target) {
        String normalizedContent = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        String normalizedTarget = target == null ? "" : target.replaceAll("\\s+", " ").trim();
        int index = normalizedContent.toLowerCase().indexOf(normalizedTarget.toLowerCase());
        if (index < 0) {
            return truncate(normalizedContent, 300);
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(normalizedContent.length(), index + normalizedTarget.length() + 80);
        return truncate(normalizedContent.substring(start, end), 300);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("learning capture json serialization failed", e);
            return "{}";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
