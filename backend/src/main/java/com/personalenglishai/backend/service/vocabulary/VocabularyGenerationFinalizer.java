package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class VocabularyGenerationFinalizer {
    private static final Logger log = LoggerFactory.getLogger(VocabularyGenerationFinalizer.class);

    private final VocabularyGenerationJobMapper jobs;
    private final VocabularyCardMapper cards;
    private final VocabularyRevisionMapper revisions;
    private final VocabularySourceMapper sources;
    private final VocabularyProductEventService productEvents;
    private final ObjectMapper objectMapper;

    public VocabularyGenerationFinalizer(
            VocabularyGenerationJobMapper jobs,
            VocabularyCardMapper cards,
            VocabularyRevisionMapper revisions,
            VocabularySourceMapper sources,
            VocabularyProductEventService productEvents,
            ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.cards = cards;
        this.revisions = revisions;
        this.sources = sources;
        this.productEvents = productEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SuccessOutcome finalizeSuccess(
            VocabularyGenerationJob job,
            String leaseToken,
            VocabularyCardRevision revision,
            String generationOutcome,
            String warning) {
        requireRevisionMatchesJob(job, revision);
        boolean partial = "partial".equals(generationOutcome);
        if (!partial && !"complete".equals(generationOutcome)) {
            throw new IllegalArgumentException("generation outcome must be complete or partial");
        }
        VocabularyCard card = cards.findByUidForUpdate(job.getCardUid());
        if (card == null || card.getDeletedAt() != null) {
            cancelOwned(job, leaseToken);
            return SuccessOutcome.CANCELLED;
        }
        if (jobs.markSucceeded(
                job.getJobUid(), leaseToken, revision.getRevisionUid(), generationOutcome, warning) != 1) {
            throw new LeaseLostException(job.getJobUid());
        }

        revisions.insertRevision(revision);
        String currentRevisionUid = card.getActiveRevisionUid();
        if (Objects.equals(job.getBaseRevisionUid(), currentRevisionUid)) {
            activate(card, currentRevisionUid, job, revision, activationStatus(partial));
            scheduleReadyEvent(card, job, revision, partial);
            return successOutcome(partial);
        }

        VocabularyCardRevision currentRevision = currentRevisionUid == null
                ? null
                : revisions.findRevision(currentRevisionUid);
        boolean currentIsAi = currentRevision != null
                && Objects.equals(card.getCardUid(), currentRevision.getCardUid())
                && "ai".equals(currentRevision.getAuthorType());
        if (currentIsAi) {
            activate(card, currentRevisionUid, job, revision, activationStatus(partial));
            scheduleReadyEvent(card, job, revision, partial);
            return successOutcome(partial);
        }
        if (cards.markConflictCandidate(card.getCardUid(), revision.getRevisionUid()) != 1) {
            throw new FinalizationConflictException(job.getJobUid());
        }
        return SuccessOutcome.NEEDS_REVIEW;
    }

    private void scheduleReadyEvent(
            VocabularyCard card,
            VocabularyGenerationJob job,
            VocabularyCardRevision revision,
            boolean partial) {
        if (partial) return;
        Runnable recorder = () -> recordReadyEvent(card, job, revision);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recorder.run();
                }
            });
        } else {
            recorder.run();
        }
    }

    private void recordReadyEvent(
            VocabularyCard card, VocabularyGenerationJob job, VocabularyCardRevision revision) {
        try {
            JsonNode request = objectMapper.readTree(job.getRequestJson());
            String sourceUid = textValue(request, "sourceUid");
            if (sourceUid == null) {
                log.warn("Vocabulary ready event skipped cardUid={} reason=source_missing", card.getCardUid());
                return;
            }
            VocabularyCardSource source = sources.findBySourceUid(sourceUid);
            if (source == null) {
                log.warn("Vocabulary ready event skipped cardUid={} reason=source_unavailable", card.getCardUid());
                return;
            }
            String traceId = sourceTraceId(source, request);
            productEvents.recordServerEvent(card.getUserId(), new VocabularyProductEventService.ServerEvent(
                    "vocabulary-cards-ready:" + revision.getRevisionUid(),
                    "vocabulary_cards_ready",
                    traceId,
                    card.getCardUid(),
                    Map.of("sourceType", source.getSourceType())));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn(
                    "Vocabulary ready event write failed cardUid={} errorType={}",
                    card.getCardUid(), exception.getClass().getSimpleName());
        }
    }

    private String sourceTraceId(VocabularyCardSource source, JsonNode request) {
        if ("ocr_image".equals(source.getSourceType()) && source.getMetadataJson() != null) {
            try {
                String recognitionTraceId = textValue(
                        objectMapper.readTree(source.getMetadataJson()), "recognitionTraceId");
                if (recognitionTraceId != null) return recognitionTraceId;
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                log.warn(
                        "Vocabulary ready source metadata unavailable sourceUid={} errorType={}",
                        source.getSourceUid(), exception.getClass().getSimpleName());
            }
        }
        return textValue(request, "clientRequestId");
    }

    private String textValue(JsonNode object, String field) {
        if (object == null) return null;
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) return null;
        return value.textValue();
    }

    @Transactional
    public boolean finalizeFailure(
            VocabularyGenerationJob job,
            String leaseToken,
            String errorCode,
            String errorMessage,
            LocalDateTime availableAt,
            boolean terminal) {
        VocabularyCard card = cards.findByUidForUpdate(job.getCardUid());
        if (card == null || card.getDeletedAt() != null) {
            return cancelOwned(job, leaseToken);
        }
        int updated = jobs.markFailed(
                job.getJobUid(), leaseToken, errorCode, errorMessage, availableAt, terminal);
        if (updated != 1) {
            return false;
        }
        if (card.getActiveRevisionUid() == null && "generating".equals(card.getStatus())) {
            if (cards.markGenerationFailed(card.getCardUid(), terminal) != 1) {
                throw new FinalizationConflictException(job.getJobUid());
            }
        }
        return true;
    }

    @Transactional
    public boolean cancel(VocabularyGenerationJob job, String leaseToken) {
        cards.findByUidForUpdate(job.getCardUid());
        return cancelOwned(job, leaseToken);
    }

    private boolean cancelOwned(VocabularyGenerationJob job, String leaseToken) {
        return jobs.cancel(job.getJobUid(), leaseToken) == 1;
    }

    private void activate(
            VocabularyCard card,
            String expectedRevisionUid,
            VocabularyGenerationJob job,
            VocabularyCardRevision revision,
            String status) {
        int updated = cards.updateActiveRevision(
                card.getUserId(),
                card.getCardUid(),
                expectedRevisionUid,
                revision.getRevisionUid(),
                status,
                revision.getTemplateKey(),
                revision.getTemplateVersion(),
                revision.getThemeUid(),
                revision.getThemeVersion());
        if (updated != 1) {
            throw new FinalizationConflictException(job.getJobUid());
        }
    }

    private String activationStatus(boolean partial) {
        return partial ? "needs_review" : "ready";
    }

    private SuccessOutcome successOutcome(boolean partial) {
        return partial ? SuccessOutcome.NEEDS_REVIEW : SuccessOutcome.ACTIVATED;
    }

    private void requireRevisionMatchesJob(
            VocabularyGenerationJob job,
            VocabularyCardRevision revision) {
        if (job == null
                || revision == null
                || !Objects.equals(job.getCardUid(), revision.getCardUid())
                || !Objects.equals(job.getBaseRevisionUid(), revision.getBaseRevisionUid())
                || !Objects.equals(job.getTemplateKey(), revision.getTemplateKey())
                || !Objects.equals(job.getTemplateVersion(), revision.getTemplateVersion())
                || !"ai".equals(revision.getAuthorType())) {
            throw new IllegalArgumentException("AI revision does not match generation job");
        }
    }

    public enum SuccessOutcome {
        ACTIVATED,
        NEEDS_REVIEW,
        CANCELLED
    }

    public static final class LeaseLostException extends RuntimeException {
        LeaseLostException(String jobUid) {
            super("Generation lease is no longer owned for job " + jobUid);
        }
    }

    private static final class FinalizationConflictException extends RuntimeException {
        FinalizationConflictException(String jobUid) {
            super("Generation finalization guard failed for job " + jobUid);
        }
    }
}
