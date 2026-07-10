package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VocabularyGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(VocabularyGenerationWorker.class);
    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ACTIVATION_RETRIES = 5;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final VocabularyGenerationJobMapper jobs;
    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyRevisionMapper revisions;
    private final VocabularyCardGenerator generator;
    private final VocabularyTemplateRegistry templates;
    private final ObjectMapper objectMapper;

    public VocabularyGenerationWorker(
            VocabularyGenerationJobMapper jobs,
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyRevisionMapper revisions,
            VocabularyCardGenerator generator,
            VocabularyTemplateRegistry templates,
            ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.cards = cards;
        this.sources = sources;
        this.revisions = revisions;
        this.generator = generator;
        this.templates = templates;
        this.objectMapper = objectMapper;
    }

    public int processPendingJobs(int batchSize) {
        int claimed = 0;
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<VocabularyGenerationJob> candidates = jobs.selectClaimable(limit);
        for (VocabularyGenerationJob candidate : candidates) {
            if (candidate == null || jobs.markRunning(candidate.getJobUid()) != 1) {
                continue;
            }
            claimed++;
            processClaimed(candidate);
        }
        return claimed;
    }

    private void processClaimed(VocabularyGenerationJob job) {
        VocabularyCard card = cards.findByUidIncludingDeleted(job.getCardUid());
        if (card == null || card.getDeletedAt() != null) {
            jobs.cancel(job.getJobUid());
            return;
        }

        try {
            VocabularyTemplateRegistry.TemplateDefinition template = requireTemplate(job.getTemplateKey());
            if (!Objects.equals(job.getTemplateVersion(), template.version())) {
                throw permanentFailure(
                        "INVALID_GENERATION_REQUEST", "Vocabulary template version is no longer available");
            }
            GeneratedVocabularyCard generated = generator.generate(
                    card, sources.listSources(card.getCardUid()), template, job.getJobUid());
            VocabularyCard currentCard = cards.findByUidIncludingDeleted(job.getCardUid());
            if (currentCard == null || currentCard.getDeletedAt() != null) {
                jobs.cancel(job.getJobUid());
                return;
            }
            VocabularyCardRevision revision = newRevision(job, generated);
            revisions.insertRevision(revision);

            ActivationResult activation = activateRevision(job, currentCard, revision);
            if (activation != ActivationResult.CANCELLED) {
                jobs.markSucceeded(job.getJobUid(), revision.getRevisionUid());
            }
        } catch (VocabularyGenerationException exception) {
            recordFailure(job, card, exception);
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

    private VocabularyCardRevision newRevision(
            VocabularyGenerationJob job,
            GeneratedVocabularyCard generated) {
        if (generated == null || generated.content() == null) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary content is missing");
        }
        try {
            templates.validate(job.getTemplateKey(), generated.content());
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
        revision.setContentJson(writeContent(generated));
        revision.setChangeSummary(limit(generated.changeSummary(), 255));
        return revision;
    }

    private String writeContent(GeneratedVocabularyCard generated) {
        try {
            return objectMapper.writeValueAsString(generated.content());
        } catch (JsonProcessingException exception) {
            throw permanentFailure("INVALID_GENERATED_CONTENT", "Generated vocabulary content cannot be stored");
        }
    }

    private ActivationResult activateRevision(
            VocabularyGenerationJob job,
            VocabularyCard initialCard,
            VocabularyCardRevision revision) {
        if (updateActiveRevision(initialCard, job.getBaseRevisionUid(), job, revision) == 1) {
            return ActivationResult.ACTIVATED;
        }

        for (int attempt = 0; attempt < MAX_ACTIVATION_RETRIES; attempt++) {
            VocabularyCard currentCard = cards.findByUidIncludingDeleted(job.getCardUid());
            if (currentCard == null || currentCard.getDeletedAt() != null) {
                jobs.cancel(job.getJobUid());
                return ActivationResult.CANCELLED;
            }

            String currentRevisionUid = currentCard.getActiveRevisionUid();
            VocabularyCardRevision currentRevision = currentRevisionUid == null
                    ? null
                    : revisions.findRevision(currentRevisionUid);
            boolean currentIsAi = currentRevision != null
                    && Objects.equals(job.getCardUid(), currentRevision.getCardUid())
                    && "ai".equals(currentRevision.getAuthorType());
            if (!currentIsAi) {
                cards.markConflictCandidate(job.getCardUid());
                return ActivationResult.NEEDS_REVIEW;
            }
            if (updateActiveRevision(currentCard, currentRevisionUid, job, revision) == 1) {
                return ActivationResult.ACTIVATED;
            }
        }

        cards.markConflictCandidate(job.getCardUid());
        return ActivationResult.NEEDS_REVIEW;
    }

    private int updateActiveRevision(
            VocabularyCard card,
            String expectedRevisionUid,
            VocabularyGenerationJob job,
            VocabularyCardRevision revision) {
        return cards.updateActiveRevision(
                card.getUserId(),
                card.getCardUid(),
                expectedRevisionUid,
                revision.getRevisionUid(),
                "ready",
                job.getTemplateKey(),
                job.getTemplateVersion());
    }

    private void recordFailure(
            VocabularyGenerationJob job,
            VocabularyCard card,
            VocabularyGenerationException exception) {
        int completedAttempts = safeAttemptCount(job) + 1;
        boolean terminal = !exception.retryable() || completedAttempts >= MAX_ATTEMPTS;
        LocalDateTime availableAt = LocalDateTime.now().plusSeconds(30L * completedAttempts);
        String errorCode = limit(exception.code(), MAX_ERROR_CODE_LENGTH);
        String errorMessage = limit(exception.getMessage(), MAX_ERROR_MESSAGE_LENGTH);
        int updated = jobs.markFailed(
                job.getJobUid(), errorCode, errorMessage, availableAt, terminal);
        if (updated == 1
                && card.getDeletedAt() == null
                && card.getActiveRevisionUid() == null) {
            cards.markGenerationFailed(card.getCardUid(), terminal);
        }
        log.warn(
                "Vocabulary generation job failed jobUid={} cardUid={} code={} attempt={} terminal={}",
                safeId(job.getJobUid()), safeId(job.getCardUid()), errorCode, completedAttempts, terminal);
    }

    private int safeAttemptCount(VocabularyGenerationJob job) {
        return job.getAttemptCount() == null ? 0 : Math.max(0, job.getAttemptCount());
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

    private enum ActivationResult {
        ACTIVATED,
        NEEDS_REVIEW,
        CANCELLED
    }
}
