package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyGenerationFinalizer {

    private final VocabularyGenerationJobMapper jobs;
    private final VocabularyCardMapper cards;
    private final VocabularyRevisionMapper revisions;

    public VocabularyGenerationFinalizer(
            VocabularyGenerationJobMapper jobs,
            VocabularyCardMapper cards,
            VocabularyRevisionMapper revisions) {
        this.jobs = jobs;
        this.cards = cards;
        this.revisions = revisions;
    }

    @Transactional
    public SuccessOutcome finalizeSuccess(
            VocabularyGenerationJob job,
            String leaseToken,
            VocabularyCardRevision revision) {
        requireRevisionMatchesJob(job, revision);
        VocabularyCard card = cards.findByUidForUpdate(job.getCardUid());
        if (card == null || card.getDeletedAt() != null) {
            cancelOwned(job, leaseToken);
            return SuccessOutcome.CANCELLED;
        }
        if (jobs.markSucceeded(job.getJobUid(), leaseToken, revision.getRevisionUid()) != 1) {
            throw new LeaseLostException(job.getJobUid());
        }

        revisions.insertRevision(revision);
        String currentRevisionUid = card.getActiveRevisionUid();
        if (Objects.equals(job.getBaseRevisionUid(), currentRevisionUid)) {
            activate(card, currentRevisionUid, job, revision);
            return SuccessOutcome.ACTIVATED;
        }

        VocabularyCardRevision currentRevision = currentRevisionUid == null
                ? null
                : revisions.findRevision(currentRevisionUid);
        boolean currentIsAi = currentRevision != null
                && Objects.equals(card.getCardUid(), currentRevision.getCardUid())
                && "ai".equals(currentRevision.getAuthorType());
        if (currentIsAi) {
            activate(card, currentRevisionUid, job, revision);
            return SuccessOutcome.ACTIVATED;
        }
        if (cards.markConflictCandidate(card.getCardUid()) != 1) {
            throw new FinalizationConflictException(job.getJobUid());
        }
        return SuccessOutcome.NEEDS_REVIEW;
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
            VocabularyCardRevision revision) {
        int updated = cards.updateActiveRevision(
                card.getUserId(),
                card.getCardUid(),
                expectedRevisionUid,
                revision.getRevisionUid(),
                "ready",
                job.getTemplateKey(),
                job.getTemplateVersion());
        if (updated != 1) {
            throw new FinalizationConflictException(job.getJobUid());
        }
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
