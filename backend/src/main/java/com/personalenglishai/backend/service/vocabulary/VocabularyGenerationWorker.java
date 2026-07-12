package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VocabularyGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(VocabularyGenerationWorker.class);
    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final VocabularyGenerationJobMapper jobs;
    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyCardGenerator generator;
    private final VocabularyTemplateRegistry templates;
    private final VocabularyThemeMapper themes;
    private final VocabularyCoreContentCodec coreCodec;
    private final ObjectMapper objectMapper;
    private final VocabularyGenerationFinalizer finalizer;
    private final int leaseSeconds;

    public VocabularyGenerationWorker(
            VocabularyGenerationJobMapper jobs,
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyCardGenerator generator,
            VocabularyTemplateRegistry templates,
            VocabularyThemeMapper themes,
            VocabularyCoreContentCodec coreCodec,
            ObjectMapper objectMapper,
            VocabularyGenerationFinalizer finalizer,
            @Value("${vocabulary.generation.scheduler.lease-ms:300000}") long leaseMs) {
        this.jobs = jobs;
        this.cards = cards;
        this.sources = sources;
        this.generator = generator;
        this.templates = templates;
        this.themes = themes;
        this.coreCodec = coreCodec;
        this.objectMapper = objectMapper;
        this.finalizer = finalizer;
        this.leaseSeconds = leaseSeconds(leaseMs);
    }

    public int processPendingJobs(int batchSize) {
        int claimed = 0;
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<VocabularyGenerationJob> candidates = jobs.selectClaimable(limit);
        for (VocabularyGenerationJob candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String leaseToken = uid("lease_");
            if (jobs.markRunning(candidate.getJobUid(), leaseToken, leaseSeconds) != 1) {
                continue;
            }
            claimed++;
            processClaimed(candidate, leaseToken);
        }
        return claimed;
    }

    private void processClaimed(VocabularyGenerationJob job, String leaseToken) {
        VocabularyCard card = cards.findByUidIncludingDeleted(job.getCardUid());
        if (card == null || card.getDeletedAt() != null) {
            finalizer.cancel(job, leaseToken);
            return;
        }

        try {
            ResolvedVocabularyTheme theme = requireTheme(job);
            GeneratedVocabularyCard generated = generator.generate(
                    card, sources.listSources(card.getCardUid()), theme, job.getJobUid());
            VocabularyCardRevision revision = newRevision(job, generated);
            finalizer.finalizeSuccess(job, leaseToken, revision, generated.partial());
        } catch (VocabularyGenerationException exception) {
            recordFailure(job, leaseToken, exception);
        } catch (VocabularyGenerationFinalizer.LeaseLostException exception) {
            log.info(
                    "Vocabulary generation result ignored after lease loss jobUid={} cardUid={}",
                    safeId(job.getJobUid()), safeId(job.getCardUid()));
        }
    }

    private VocabularyTemplateRegistry.TemplateDefinition requireTemplate(String templateKey) {
        try {
            return templates.require(templateKey);
        } catch (IllegalArgumentException exception) {
            throw permanentFailure(
                    "INVALID_GENERATION_REQUEST", "Vocabulary generation request is invalid");
        }
    }

    private ResolvedVocabularyTheme requireTheme(VocabularyGenerationJob job) {
        if (job.getThemeUid() != null && !job.getThemeUid().isBlank() && job.getThemeVersion() != null) {
            VocabularyThemeRevision revision = themes.findRevision(job.getThemeUid(), job.getThemeVersion());
            if (revision == null
                    || revision.getVersion() == null
                    || revision.getContentFormatVersion() == null) {
                throw permanentFailure(
                        "INVALID_GENERATION_REQUEST", "Vocabulary theme version is no longer available");
            }
            return new ResolvedVocabularyTheme(
                    revision.getThemeUid(), revision.getVersion(), revision.getNameSnapshot(),
                    revision.getPurpose(), revision.getPromptStrategyKey(),
                    revision.getContentFormatVersion(), job.getTemplateKey());
        }

        VocabularyTemplateRegistry.TemplateDefinition template = requireTemplate(job.getTemplateKey());
        if (!Objects.equals(job.getTemplateVersion(), template.version())) {
            throw permanentFailure(
                    "INVALID_GENERATION_REQUEST", "Vocabulary template version is no longer available");
        }
        return new ResolvedVocabularyTheme(
                "theme_system_" + template.key(), template.version(), template.name(), "",
                template.key() + "-markdown-v1", 1, template.key());
    }

    private VocabularyCardRevision newRevision(
            VocabularyGenerationJob job,
            GeneratedVocabularyCard generated) {
        if (generated == null || generated.core() == null) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary content is missing");
        }
        try {
            coreCodec.validate(generated.core());
        } catch (IllegalArgumentException exception) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary content is invalid");
        }

        VocabularyCardRevision revision = new VocabularyCardRevision();
        revision.setRevisionUid(uid("rev_"));
        revision.setCardUid(job.getCardUid());
        revision.setBaseRevisionUid(job.getBaseRevisionUid());
        revision.setAuthorType("ai");
        revision.setTemplateKey(job.getTemplateKey());
        revision.setTemplateVersion(job.getTemplateVersion());
        revision.setThemeUid(job.getThemeUid());
        revision.setThemeVersion(job.getThemeVersion());
        revision.setContentJson(writeContent(generated));
        revision.setCoreJson(writeCore(generated));
        revision.setContentMarkdown(generated.markdown());
        revision.setContentFormatVersion(generated.contentFormatVersion());
        revision.setChangeSummary(limit(generated.changeSummary(), 255));
        return revision;
    }

    private String writeContent(GeneratedVocabularyCard generated) {
        try {
            ObjectNode compatibility = (ObjectNode) generated.core().deepCopy();
            compatibility.put("markdown", generated.markdown() == null ? "" : generated.markdown());
            return objectMapper.writeValueAsString(compatibility);
        } catch (JsonProcessingException exception) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary content cannot be stored");
        }
    }

    private String writeCore(GeneratedVocabularyCard generated) {
        try {
            return objectMapper.writeValueAsString(generated.core());
        } catch (JsonProcessingException exception) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary core cannot be stored");
        }
    }

    private void recordFailure(
            VocabularyGenerationJob job,
            String leaseToken,
            VocabularyGenerationException exception) {
        int completedAttempts = safeAttemptCount(job) + 1;
        boolean terminal = !exception.retryable() || completedAttempts >= MAX_ATTEMPTS;
        LocalDateTime availableAt = LocalDateTime.now().plusSeconds(30L * completedAttempts);
        String errorCode = limit(exception.code(), MAX_ERROR_CODE_LENGTH);
        String errorMessage = limit(exception.getMessage(), MAX_ERROR_MESSAGE_LENGTH);
        boolean finalized = finalizer.finalizeFailure(
                job, leaseToken, errorCode, errorMessage, availableAt, terminal);
        if (finalized) {
            log.warn(
                    "Vocabulary generation job failed jobUid={} cardUid={} code={} attempt={} terminal={}",
                    safeId(job.getJobUid()), safeId(job.getCardUid()), errorCode, completedAttempts, terminal);
        } else {
            log.info(
                    "Vocabulary generation failure ignored after lease loss jobUid={} cardUid={} attempt={}",
                    safeId(job.getJobUid()), safeId(job.getCardUid()), completedAttempts);
        }
    }

    private int safeAttemptCount(VocabularyGenerationJob job) {
        return job.getAttemptCount() == null ? 0 : Math.max(0, job.getAttemptCount());
    }

    private int leaseSeconds(long leaseMs) {
        long positiveLeaseMs = Math.max(1L, leaseMs);
        long roundedSeconds = ((positiveLeaseMs - 1L) / 1_000L) + 1L;
        return (int) Math.min(Integer.MAX_VALUE, roundedSeconds);
    }

    private VocabularyGenerationException permanentFailure(String code, String message) {
        return new VocabularyGenerationException(code, false, message);
    }

    private String uid(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeId(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return limit(sanitized, 80);
    }
}
