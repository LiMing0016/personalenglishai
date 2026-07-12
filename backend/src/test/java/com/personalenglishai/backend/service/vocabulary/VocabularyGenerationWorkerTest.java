package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationWorkerTest {

    @Mock private VocabularyGenerationJobMapper jobs;
    @Mock private VocabularyCardMapper cards;
    @Mock private VocabularySourceMapper sources;
    @Mock private VocabularyCardGenerator generator;
    @Mock private VocabularyGenerationFinalizer finalizer;
    @Mock private VocabularyThemeMapper themes;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyTemplateRegistry templates;
    private VocabularyGenerationWorker worker;

    @BeforeEach
    void setUp() {
        templates = new VocabularyTemplateRegistry(objectMapper);
        worker = new VocabularyGenerationWorker(
                jobs, cards, sources, generator, templates, themes,
                new VocabularyCoreContentCodec(objectMapper), objectMapper, finalizer, 300_000L);
    }

    @Test
    void claimsWithFreshLeaseAndFinalizesUsingTheSameFence() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_1", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_1"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_1")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());

        assertEquals(1, worker.processPendingJobs(10));

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> finalizationToken = ArgumentCaptor.forClass(String.class);
        verify(jobs).markRunning(eq("job_1"), claimToken.capture(), eq(300));
        verify(finalizer).finalizeSuccess(
                eq(job), finalizationToken.capture(), any(VocabularyCardRevision.class), eq(false));
        assertEquals(claimToken.getValue(), finalizationToken.getValue());
        InOrder order = org.mockito.Mockito.inOrder(generator, finalizer);
        order.verify(generator).generate(any(), anyList(), any(), eq("job_1"));
        order.verify(finalizer).finalizeSuccess(eq(job), eq(claimToken.getValue()), any(), eq(false));
    }

    @Test
    void skipsCandidateWhenAnotherWorkerClaimsItFirst() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_claimed", "card_1", null, 0);
        when(jobs.selectClaimable(20)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_claimed"), anyString(), eq(300))).thenReturn(0);

        assertEquals(0, worker.processPendingJobs(50));

        verifyNoInteractions(cards, sources, generator, finalizer);
    }

    @Test
    void delegatesSoftDeletedCardCancellationThroughLeaseGuardedFinalizer() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_deleted", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        card.setDeletedAt(LocalDateTime.now());
        when(jobs.selectClaimable(5)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_deleted"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);

        worker.processPendingJobs(5);

        ArgumentCaptor<String> leaseToken = ArgumentCaptor.forClass(String.class);
        verify(jobs).markRunning(eq("job_deleted"), leaseToken.capture(), eq(300));
        verify(finalizer).cancel(job, leaseToken.getValue());
        verifyNoInteractions(sources, generator);
    }

    @Test
    void delegatesTransientFailureWithSameLeaseAndDeterministicBackoff() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_retry", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_retry"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_retry")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", true, "AI request timed out"));
        LocalDateTime before = LocalDateTime.now();

        worker.processPendingJobs(10);

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> availableAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobs).markRunning(eq("job_retry"), claimToken.capture(), eq(300));
        verify(finalizer).finalizeFailure(
                eq(job), eq(claimToken.getValue()), eq("AI_TIMEOUT"), eq("AI request timed out"),
                availableAt.capture(), eq(false));
        LocalDateTime after = LocalDateTime.now();
        assertFalse(availableAt.getValue().isBefore(before.plusSeconds(30)));
        assertFalse(availableAt.getValue().isAfter(after.plusSeconds(30)));
    }

    @Test
    void delegatesThirdTransientFailureAsTerminal() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_retry_3", "card_1", null, 2);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_retry_3"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_retry_3")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", true, "AI request timed out"));

        worker.processPendingJobs(10);

        verify(finalizer).finalizeFailure(
                eq(job), anyString(), eq("AI_TIMEOUT"), anyString(), any(), eq(true));
    }

    @Test
    void invalidGeneratedContentNeverReachesSuccessFinalization() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_invalid", "card_1", null, 0);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        ObjectNode invalid = objectMapper.createObjectNode().put("term", "innovative");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_invalid"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_invalid")))
                .thenReturn(new GeneratedVocabularyCard(
                        invalid, "", 1, "test-model", "invalid fixture", true));

        worker.processPendingJobs(10);

        verify(finalizer, never()).finalizeSuccess(any(), anyString(), any(), anyBoolean());
        verify(finalizer).finalizeFailure(
                eq(job), anyString(), eq("INVALID_GENERATED_CONTENT"), anyString(), any(), eq(true));
    }

    @Test
    void resolvesTheFrozenThemeUidAndVersionFromTheJob() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_theme", "card_1", null, 0);
        job.setThemeUid("theme_custom");
        job.setThemeVersion(4);
        VocabularyThemeRevision revision = themeRevision("theme_custom", 4);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_theme"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(VocabularyTestFixtures.generating("card_1", null));
        when(themes.findRevision("theme_custom", 4)).thenReturn(revision);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_theme")))
                .thenReturn(VocabularyTestFixtures.basicGeneratedCard());

        worker.processPendingJobs(10);

        ArgumentCaptor<ResolvedVocabularyTheme> resolved =
                ArgumentCaptor.forClass(ResolvedVocabularyTheme.class);
        verify(generator).generate(any(), anyList(), resolved.capture(), eq("job_theme"));
        assertEquals("theme_custom", resolved.getValue().themeUid());
        assertEquals(4, resolved.getValue().version());
        assertEquals("frozen purpose", resolved.getValue().purpose());
        assertEquals("custom-markdown-v1", resolved.getValue().promptStrategyKey());
    }

    @Test
    void persistedRevisionFreezesThemeAndUsesCardTermIdentity() throws Exception {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_persist", "card_1", null, 0);
        job.setThemeUid("theme_user_1");
        job.setThemeVersion(3);
        VocabularyCard card = VocabularyTestFixtures.generating("card_1", null);
        card.setNormalizedTerm("record");
        VocabularyThemeRevision theme = themeRevision("theme_user_1", 3);
        GeneratedVocabularyCard generated = VocabularyTestFixtures.basicGeneratedCard();
        ObjectNode generatedCore = (ObjectNode) generated.core().deepCopy();
        generatedCore.put("term", "candidate override");
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_persist"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(card);
        when(themes.findRevision("theme_user_1", 3)).thenReturn(theme);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(generator.generate(any(), anyList(), any(), eq("job_persist")))
                .thenReturn(new GeneratedVocabularyCard(
                        generatedCore, "## Exam tips", 2, "test-model", "Generated", false));

        worker.processPendingJobs(10);

        ArgumentCaptor<VocabularyCardRevision> revision = ArgumentCaptor.forClass(VocabularyCardRevision.class);
        verify(finalizer).finalizeSuccess(eq(job), anyString(), revision.capture(), eq(false));
        assertEquals("theme_user_1", revision.getValue().getThemeUid());
        assertEquals(3, revision.getValue().getThemeVersion());
        assertEquals("record", objectMapper.readTree(revision.getValue().getCoreJson()).path("term").asText());
        assertEquals("record", objectMapper.readTree(revision.getValue().getContentJson()).path("term").asText());
        assertEquals("## Exam tips", revision.getValue().getContentMarkdown());
        assertEquals(2, revision.getValue().getContentFormatVersion());
    }

    @Test
    void unavailableFrozenThemeRevisionFailsPermanentlyWithoutGeneration() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_theme_missing", "card_1", null, 0);
        job.setThemeUid("theme_custom");
        job.setThemeVersion(4);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_theme_missing"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(VocabularyTestFixtures.generating("card_1", null));
        when(themes.findRevision("theme_custom", 4)).thenReturn(null);

        worker.processPendingJobs(10);

        verifyNoInteractions(sources, generator);
        verify(finalizer).finalizeFailure(
                eq(job), anyString(), eq("INVALID_GENERATION_REQUEST"),
                eq("Vocabulary theme version is no longer available"), any(), eq(true));
    }

    @Test
    void partialGenerationIsPassedToLeaseGuardedFinalizer() {
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_partial", "card_1", null, 0);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning(eq("job_partial"), anyString(), eq(300))).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(VocabularyTestFixtures.generating("card_1", null));
        when(sources.listSources("card_1")).thenReturn(List.of());
        GeneratedVocabularyCard complete = VocabularyTestFixtures.basicGeneratedCard();
        when(generator.generate(any(), anyList(), any(), eq("job_partial")))
                .thenReturn(new GeneratedVocabularyCard(
                        complete.core(), "", complete.contentFormatVersion(), complete.model(),
                        "Markdown unavailable", true));

        worker.processPendingJobs(10);

        verify(finalizer).finalizeSuccess(
                eq(job), anyString(), any(VocabularyCardRevision.class), eq(true));
    }

    private VocabularyThemeRevision themeRevision(String themeUid, int version) {
        VocabularyThemeRevision revision = new VocabularyThemeRevision();
        revision.setThemeUid(themeUid);
        revision.setVersion(version);
        revision.setNameSnapshot("Frozen theme");
        revision.setPurpose("frozen purpose");
        revision.setPromptStrategyKey("custom-markdown-v1");
        revision.setContentFormatVersion(2);
        return revision;
    }
}
