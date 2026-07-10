package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationWorkerTest {

    @Mock private VocabularyGenerationJobMapper jobs;
    @Mock private VocabularyCardMapper cards;
    @Mock private VocabularySourceMapper sources;
    @Mock private VocabularyRevisionMapper revisions;
    @Mock private VocabularyCardGenerator generator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyTemplateRegistry templates;
    private VocabularyGenerationWorker worker;

    @BeforeEach
    void setUp() {
        templates = new VocabularyTemplateRegistry(objectMapper);
        worker = new VocabularyGenerationWorker(
                jobs, cards, sources, revisions, generator, templates, objectMapper);
    }

    @Test
    void activatesSuccessfulRevisionWhenBaseStillMatches() throws Exception {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_1", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_1")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_1")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());
        when(cards.updateActiveRevision(
                eq(7L), eq("card_1"), isNull(), anyString(), eq("ready"), eq("basic"), eq(1)))
                .thenReturn(1);

        assertEquals(1, worker.processPendingJobs(10));

        ArgumentCaptor<VocabularyCardRevision> revisionCaptor =
                ArgumentCaptor.forClass(VocabularyCardRevision.class);
        verify(revisions).insertRevision(revisionCaptor.capture());
        VocabularyCardRevision revision = revisionCaptor.getValue();
        assertEquals("card_1", revision.getCardUid());
        assertEquals("ai", revision.getAuthorType());
        assertEquals("basic", revision.getTemplateKey());
        assertEquals(1, revision.getTemplateVersion());
        assertEquals("innovative", objectMapper.readTree(revision.getContentJson()).path("term").asText());
        verify(jobs).markSucceeded("job_1", revision.getRevisionUid());
    }

    @Test
    void skipsCandidateWhenAnotherWorkerClaimsItFirst() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_claimed", "card_1", null, 0);
        when(jobs.selectClaimable(20)).thenReturn(List.of(job));
        when(jobs.markRunning("job_claimed")).thenReturn(0);

        assertEquals(0, worker.processPendingJobs(50));

        verifyNoInteractions(cards, sources, revisions, generator);
    }

    @Test
    void cancelsClaimedJobForSoftDeletedCardWithoutGenerating() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_deleted", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        card.setDeletedAt(LocalDateTime.now());
        when(jobs.selectClaimable(5)).thenReturn(List.of(job));
        when(jobs.markRunning("job_deleted")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);

        assertEquals(1, worker.processPendingJobs(5));

        verify(jobs).cancel("job_deleted");
        verifyNoInteractions(sources, revisions, generator);
    }

    @Test
    void ignoresResultWhenCardIsSoftDeletedDuringGeneration() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_deleted_during_generation", "card_1", null, 0);
        VocabularyCard activeCard = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCard deletedCard = VocabularyTestFixtures.generating("card_1", null);
        deletedCard.setDeletedAt(LocalDateTime.now());
        when(jobs.selectClaimable(5)).thenReturn(List.of(job));
        when(jobs.markRunning("job_deleted_during_generation")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(activeCard, deletedCard);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_deleted_during_generation")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());

        worker.processPendingJobs(5);

        verify(jobs).cancel("job_deleted_during_generation");
        verifyNoInteractions(revisions);
        verify(cards, never()).updateActiveRevision(
                anyLong(), anyString(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void storesCandidateAndMarksNeedsReviewWhenUserRevisionReplacedBase() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_user_conflict", "card_1", "rev_ai_old", 0);
        VocabularyCard beforeGeneration = VocabularyTestFixtures.ready("card_1", "rev_ai_old");
        VocabularyCard afterGeneration = VocabularyTestFixtures.ready("card_1", "rev_user");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_user_conflict")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(beforeGeneration, afterGeneration);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_user_conflict")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());
        when(cards.updateActiveRevision(
                eq(7L), eq("card_1"), eq("rev_ai_old"), anyString(),
                eq("ready"), eq("basic"), eq(1)))
                .thenReturn(0);
        when(revisions.findRevision("rev_user"))
                .thenReturn(VocabularyTestFixtures.userRevision("rev_user"));

        assertEquals(1, worker.processPendingJobs(10));

        ArgumentCaptor<VocabularyCardRevision> revisionCaptor =
                ArgumentCaptor.forClass(VocabularyCardRevision.class);
        verify(revisions).insertRevision(revisionCaptor.capture());
        verify(cards).markConflictCandidate("card_1");
        verify(jobs).markSucceeded("job_user_conflict", revisionCaptor.getValue().getRevisionUid());
    }

    @Test
    void activatesLatestSuccessfulResultOverAConcurrentAiRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_ai_conflict", "card_1", "rev_ai_old", 0);
        VocabularyCard beforeGeneration = VocabularyTestFixtures.ready("card_1", "rev_ai_old");
        VocabularyCard afterGeneration = VocabularyTestFixtures.ready("card_1", "rev_ai_newer");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_ai_conflict")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(beforeGeneration, afterGeneration);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_ai_conflict")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());
        when(cards.updateActiveRevision(
                eq(7L), eq("card_1"), eq("rev_ai_old"), anyString(),
                eq("ready"), eq("basic"), eq(1)))
                .thenReturn(0);
        when(cards.updateActiveRevision(
                eq(7L), eq("card_1"), eq("rev_ai_newer"), anyString(),
                eq("ready"), eq("basic"), eq(1)))
                .thenReturn(1);
        when(revisions.findRevision("rev_ai_newer")).thenReturn(aiRevision("rev_ai_newer"));

        assertEquals(1, worker.processPendingJobs(10));

        verify(cards).updateActiveRevision(
                eq(7L), eq("card_1"), eq("rev_ai_newer"), anyString(),
                eq("ready"), eq("basic"), eq(1));
        verify(cards, never()).markConflictCandidate(anyString());
        verify(jobs).markSucceeded(eq("job_ai_conflict"), anyString());
    }

    @Test
    void requeuesTransientFailureWithDeterministicBackoff() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_retry", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_retry")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_retry")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", true, "AI request timed out"));
        when(jobs.markFailed(eq("job_retry"), eq("AI_TIMEOUT"), eq("AI request timed out"), any(), eq(false)))
                .thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        assertEquals(1, worker.processPendingJobs(10));

        ArgumentCaptor<LocalDateTime> availableAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobs).markFailed(
                eq("job_retry"), eq("AI_TIMEOUT"), eq("AI request timed out"),
                availableAt.capture(), eq(false));
        LocalDateTime after = LocalDateTime.now();
        assertFalse(availableAt.getValue().isBefore(before.plusSeconds(30)));
        assertFalse(availableAt.getValue().isAfter(after.plusSeconds(30)));
        verify(cards).markGenerationFailed("card_1", false);
    }

    @Test
    void makesThirdTransientFailureTerminal() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_retry_3", "card_1", null, 2);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_retry_3")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_retry_3")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", true, "AI request timed out"));
        when(jobs.markFailed(eq("job_retry_3"), eq("AI_TIMEOUT"), anyString(), any(), eq(true)))
                .thenReturn(1);

        worker.processPendingJobs(10);

        verify(jobs).markFailed(eq("job_retry_3"), eq("AI_TIMEOUT"), anyString(), any(), eq(true));
        verify(cards).markGenerationFailed("card_1", true);
    }

    @Test
    void makesPermanentFailureTerminalOnFirstAttempt() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_permanent", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_permanent")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_permanent")))
                .thenThrow(new VocabularyGenerationException(
                        "INVALID_GENERATION_REQUEST", false, "Generation request is invalid"));
        when(jobs.markFailed(
                eq("job_permanent"), eq("INVALID_GENERATION_REQUEST"), anyString(), any(), eq(true)))
                .thenReturn(1);

        worker.processPendingJobs(10);

        verify(jobs).markFailed(
                eq("job_permanent"), eq("INVALID_GENERATION_REQUEST"),
                eq("Generation request is invalid"), any(), eq(true));
    }

    @Test
    void preservesActiveContentWhenRegenerationFails() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob(
                "job_regenerate", "card_1", "rev_active", 0);
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", "rev_active");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_regenerate")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_regenerate")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", true, "AI request timed out"));
        when(jobs.markFailed(eq("job_regenerate"), anyString(), anyString(), any(), eq(false)))
                .thenReturn(1);

        worker.processPendingJobs(10);

        verify(cards, never()).markGenerationFailed(anyString(), any(Boolean.class));
        verify(cards, never()).updateActiveRevision(
                anyLong(), anyString(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsInvalidGeneratedContentBeforeInsertingRevision() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_invalid", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        ObjectNode invalid = objectMapper.createObjectNode().put("term", "innovative");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_invalid")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_invalid")))
                .thenReturn(new GeneratedVocabularyCard(invalid, "test-model", "invalid fixture"));
        when(jobs.markFailed(
                eq("job_invalid"), eq("INVALID_GENERATED_CONTENT"), anyString(), any(), eq(true)))
                .thenReturn(1);

        worker.processPendingJobs(10);

        verifyNoInteractions(revisions);
        verify(jobs).markFailed(
                eq("job_invalid"), eq("INVALID_GENERATED_CONTENT"), anyString(), any(), eq(true));
    }

    private VocabularyCardRevision aiRevision(String revisionUid) {
        VocabularyCardRevision revision = VocabularyTestFixtures.userRevision(revisionUid);
        revision.setAuthorType("ai");
        return revision;
    }
}
