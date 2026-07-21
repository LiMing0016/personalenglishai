package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationFinalizerTest {

    @Mock private VocabularyGenerationJobMapper jobs;
    @Mock private VocabularyCardMapper cards;
    @Mock private VocabularyRevisionMapper revisions;
    @Mock private VocabularySourceMapper sources;
    @Mock private VocabularyProductEventService productEvents;

    private VocabularyGenerationFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new VocabularyGenerationFinalizer(
                jobs, cards, revisions, sources, productEvents, new ObjectMapper());
    }

    @Test
    void successfulFinalizationTransitionsJobBeforeAppendingAndActivatingRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_1", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardRevision revision = aiRevision("rev_generated", null);
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(jobs.markSucceeded("job_1", "lease_new", "rev_generated", "complete", null)).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", null, "rev_generated", "ready", "basic", 1, null, null))
                .thenReturn(1);

        assertEquals(
                VocabularyGenerationFinalizer.SuccessOutcome.ACTIVATED,
                finalizer.finalizeSuccess(job, "lease_new", revision, "complete", null));

        InOrder order = inOrder(cards, jobs, revisions);
        order.verify(cards).findByUidForUpdate("card_1");
        order.verify(jobs).markSucceeded("job_1", "lease_new", "rev_generated", "complete", null);
        order.verify(revisions).insertRevision(revision);
        order.verify(cards).updateActiveRevision(
                7L, "card_1", null, "rev_generated", "ready", "basic", 1, null, null);
    }

    @Test
    void oldLeaseCannotFinalizeAndMarkSucceededZeroLeavesNoRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_old", "card_1", null, 0);
        VocabularyCardRevision revision = aiRevision("rev_old", null);
        revision.setGenerationMetadataJson("{\"provider\":\"python\"}");
        when(cards.findByUidForUpdate("card_1"))
                .thenReturn(VocabularyTestFixtures.generating("card_1", null));
        when(jobs.markSucceeded("job_old", "lease_old", "rev_old", "complete", null)).thenReturn(0);

        assertThrows(
                VocabularyGenerationFinalizer.LeaseLostException.class,
                () -> finalizer.finalizeSuccess(job, "lease_old", revision, "complete", null));

        verifyNoInteractions(revisions);
        verify(cards, never()).updateActiveRevision(
                eq(7L), eq("card_1"), eq(null), anyString(), anyString(), anyString(), eq(1),
                eq((String) null), eq((Integer) null));
    }

    @Test
    void deletionBetweenGenerationAndFinalizationCancelsWithoutRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_deleted", "card_1", null, 0);
        VocabularyCard deleted = VocabularyTestFixtures.generating("card_1", null);
        deleted.setDeletedAt(LocalDateTime.now());
        when(cards.findByUidForUpdate("card_1")).thenReturn(deleted);
        when(jobs.cancel("job_deleted", "lease_1")).thenReturn(1);

        assertEquals(
                VocabularyGenerationFinalizer.SuccessOutcome.CANCELLED,
                finalizer.finalizeSuccess(
                        job, "lease_1", aiRevision("rev_deleted", null), "complete", null));

        verify(jobs).cancel("job_deleted", "lease_1");
        verify(jobs, never()).markSucceeded(
                anyString(), anyString(), anyString(), anyString(), eq((String) null));
        verifyNoInteractions(revisions);
    }

    @Test
    void staleUserRevisionKeepsCandidateAndMarksNeedsReview() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_user", "card_1", "rev_ai_old", 0);
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", "rev_user");
        VocabularyCardRevision candidate = aiRevision("rev_candidate", "rev_ai_old");
        candidate.setGenerationMetadataJson("{\"provider\":\"python\"}");
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(revisions.findRevision("rev_user"))
                .thenReturn(VocabularyTestFixtures.userRevision("rev_user"));
        when(jobs.markSucceeded("job_user", "lease_1", "rev_candidate", "complete", null)).thenReturn(1);
        when(cards.markConflictCandidate("card_1", "rev_candidate")).thenReturn(1);

        assertEquals(
                VocabularyGenerationFinalizer.SuccessOutcome.NEEDS_REVIEW,
                finalizer.finalizeSuccess(job, "lease_1", candidate, "complete", null));

        verify(revisions).insertRevision(candidate);
        assertEquals("{\"provider\":\"python\"}", candidate.getGenerationMetadataJson());
        verify(cards).markConflictCandidate("card_1", "rev_candidate");
        verify(cards, never()).updateActiveRevision(
                eq(7L), eq("card_1"), anyString(), anyString(), anyString(), anyString(), eq(1),
                eq((String) null), eq((Integer) null));
    }

    @Test
    void concurrentAiRevisionIsReplacedUsingLockedCurrentRevisionAsGuard() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_ai", "card_1", "rev_ai_old", 0);
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", "rev_ai_current");
        VocabularyCardRevision candidate = aiRevision("rev_candidate", "rev_ai_old");
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(revisions.findRevision("rev_ai_current"))
                .thenReturn(aiRevision("rev_ai_current", "rev_ai_old"));
        when(jobs.markSucceeded("job_ai", "lease_1", "rev_candidate", "complete", null)).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", "rev_ai_current", "rev_candidate", "ready", "basic", 1, null, null))
                .thenReturn(1);

        assertEquals(
                VocabularyGenerationFinalizer.SuccessOutcome.ACTIVATED,
                finalizer.finalizeSuccess(job, "lease_1", candidate, "complete", null));

        verify(cards).updateActiveRevision(
                7L, "card_1", "rev_ai_current", "rev_candidate", "ready", "basic", 1, null, null);
    }

    @Test
    void failedOldWorkerCannotRevertConcurrentReadyCard() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_old", "card_1", null, 0);
        VocabularyCard ready = VocabularyTestFixtures.ready("card_1", "rev_success");
        when(cards.findByUidForUpdate("card_1")).thenReturn(ready);
        when(jobs.markFailed(
                "job_old", "lease_old", "AI_TIMEOUT", "timeout", LocalDateTime.MAX, false))
                .thenReturn(0);

        assertEquals(false, finalizer.finalizeFailure(
                job, "lease_old", "AI_TIMEOUT", "timeout", LocalDateTime.MAX, false));

        verify(cards, never()).markGenerationFailed(anyString(), eq(false));
    }

    @Test
    void partialGenerationActivatesRevisionAsNeedsReviewWithLeaseAndBaseGuards() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_partial", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardRevision revision = aiRevision("rev_partial", null);
        revision.setThemeUid("theme_frozen");
        revision.setThemeVersion(3);
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(jobs.markSucceeded("job_partial", "lease_partial", "rev_partial",
                "partial", "markdown_unavailable")).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", null, "rev_partial", "needs_review", "basic", 1,
                "theme_frozen", 3))
                .thenReturn(1);

        assertEquals(
                VocabularyGenerationFinalizer.SuccessOutcome.NEEDS_REVIEW,
                finalizer.finalizeSuccess(
                        job, "lease_partial", revision, "partial", "markdown_unavailable"));

        InOrder order = inOrder(cards, jobs, revisions);
        order.verify(cards).findByUidForUpdate("card_1");
        order.verify(jobs).markSucceeded("job_partial", "lease_partial", "rev_partial",
                "partial", "markdown_unavailable");
        order.verify(revisions).insertRevision(revision);
        order.verify(cards).updateActiveRevision(
                7L, "card_1", null, "rev_partial", "needs_review", "basic", 1,
                "theme_frozen", 3);
    }

    @Test
    void ownedFailureUpdatesOnlyStillGeneratingCardWithoutActiveRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_fail", "card_1", null, 0);
        VocabularyCard generating = VocabularyTestFixtures.generating("card_1", null);
        when(cards.findByUidForUpdate("card_1")).thenReturn(generating);
        when(jobs.markFailed(
                "job_fail", "lease_1", "AI_TIMEOUT", "timeout", LocalDateTime.MAX, true))
                .thenReturn(1);
        when(cards.markGenerationFailed("card_1", true)).thenReturn(1);

        assertEquals(true, finalizer.finalizeFailure(
                job, "lease_1", "AI_TIMEOUT", "timeout", LocalDateTime.MAX, true));

        verify(cards).markGenerationFailed("card_1", true);
    }

    @Test
    void finalizerMethodsDeclareTransactionalBoundary() throws Exception {
        Method success = VocabularyGenerationFinalizer.class.getMethod(
                "finalizeSuccess", VocabularyGenerationJob.class, String.class,
                VocabularyCardRevision.class, String.class, String.class);
        Method failure = VocabularyGenerationFinalizer.class.getMethod(
                "finalizeFailure", VocabularyGenerationJob.class, String.class,
                String.class, String.class, LocalDateTime.class, boolean.class);
        Method cancel = VocabularyGenerationFinalizer.class.getMethod(
                "cancel", VocabularyGenerationJob.class, String.class);

        assertNotNull(success.getAnnotation(Transactional.class));
        assertNotNull(failure.getAnnotation(Transactional.class));
        assertNotNull(cancel.getAnnotation(Transactional.class));
    }

    @Test
    void activatedReadyRevisionRecordsStableSourceLinkedEvent() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_ready", "card_1", null, 0);
        job.setRequestJson("{\"clientRequestId\":\"req-safe\",\"sourceUid\":\"src_ocr\"}");
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardRevision revision = aiRevision("rev_ready", null);
        VocabularyCardSource source = VocabularyTestFixtures.manualSource(null);
        source.setSourceUid("src_ocr");
        source.setSourceType("ocr_image");
        source.setMetadataJson(
                "{\"recognitionTraceId\":\"trace-safe\",\"fileName\":\"private.png\"}");
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(jobs.markSucceeded("job_ready", "lease_ready", "rev_ready", "complete", null)).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", null, "rev_ready", "ready", "basic", 1, null, null)).thenReturn(1);
        when(sources.findBySourceUid("src_ocr", 7L, "card_1")).thenReturn(source);

        assertEquals(VocabularyGenerationFinalizer.SuccessOutcome.ACTIVATED,
                finalizer.finalizeSuccess(job, "lease_ready", revision, "complete", null));

        ArgumentCaptor<VocabularyProductEventService.ServerEvent> event =
                ArgumentCaptor.forClass(VocabularyProductEventService.ServerEvent.class);
        verify(productEvents).recordServerEvent(eq(7L), event.capture());
        assertEquals("vocabulary-cards-ready:rev_ready", event.getValue().eventUid());
        assertEquals("vocabulary_cards_ready", event.getValue().eventName());
        assertEquals("trace-safe", event.getValue().traceId());
        assertEquals("card_1", event.getValue().cardUid());
        assertEquals(Map.of("sourceType", "ocr_image"), event.getValue().properties());
    }

    @Test
    void eventFailureDoesNotRollbackReadyFinalization() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_ready", "card_1", null, 0);
        job.setRequestJson("{\"sourceUid\":\"src_1\"}");
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardRevision revision = aiRevision("rev_ready", null);
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(jobs.markSucceeded("job_ready", "lease_ready", "rev_ready", "complete", null)).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", null, "rev_ready", "ready", "basic", 1, null, null)).thenReturn(1);
        when(sources.findBySourceUid("src_1", 7L, "card_1"))
                .thenReturn(VocabularyTestFixtures.manualSource(null));
        when(productEvents.recordServerEvent(eq(7L), any()))
                .thenThrow(new RuntimeException("unavailable"));

        assertEquals(VocabularyGenerationFinalizer.SuccessOutcome.ACTIVATED,
                finalizer.finalizeSuccess(job, "lease_ready", revision, "complete", null));
    }

    @Test
    void readyEventIsSkippedWhenSourceIsNotOwnedByCurrentUserAndCard() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_ready", "card_1", null, 0);
        job.setRequestJson("{\"sourceUid\":\"src_other_owner\"}");
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardRevision revision = aiRevision("rev_ready", null);
        when(cards.findByUidForUpdate("card_1")).thenReturn(card);
        when(jobs.markSucceeded("job_ready", "lease_ready", "rev_ready", "complete", null)).thenReturn(1);
        when(cards.updateActiveRevision(
                7L, "card_1", null, "rev_ready", "ready", "basic", 1, null, null)).thenReturn(1);
        when(sources.findBySourceUid("src_other_owner", 7L, "card_1")).thenReturn(null);

        assertEquals(VocabularyGenerationFinalizer.SuccessOutcome.ACTIVATED,
                finalizer.finalizeSuccess(job, "lease_ready", revision, "complete", null));

        verify(productEvents, never()).recordServerEvent(any(), any());
    }

    private VocabularyCardRevision aiRevision(String revisionUid, String baseRevisionUid) {
        VocabularyCardRevision revision = VocabularyTestFixtures.userRevision(revisionUid);
        revision.setAuthorType("ai");
        revision.setBaseRevisionUid(baseRevisionUid);
        revision.setChangeSummary("Generated fixture");
        return revision;
    }
}
