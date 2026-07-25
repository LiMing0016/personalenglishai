package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyCaptureItemServiceTest {
    @Mock VocabularyCardMapper cards;
    @Mock VocabularySourceMapper sources;
    @Mock VocabularyGenerationJobMapper jobs;
    VocabularyCaptureItemService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new VocabularyCaptureItemService(cards, sources, jobs,
                new VocabularyTermNormalizer(), new VocabularyTemplateRegistry(objectMapper), objectMapper);
    }

    @Test
    void createsCardSourceAndPendingJobBeforeReturning() {
        var request = VocabularyCaptureRequest.manual("req-1", List.of("In·nova·tive"), "en", "basic");

        var outcome = service.captureOne(7L, request, this::basicTheme, 0);
        VocabularyCaptureResponse.Item result = outcome.response();

        assertEquals("created", result.action());
        assertEquals("generating", result.status());
        assertEquals(null, outcome.readyRevisionUid());
        InOrder order = inOrder(cards, sources, jobs);
        order.verify(cards).insert(argThat(card ->
                card.getNormalizedTerm().equals("innovative") && card.getUserId().equals(7L)));
        order.verify(sources).insertSource(argThat(source -> source.getIdempotencyKey().equals("req-1:0")));
        order.verify(jobs).insertJob(argThat(job ->
                job.getStatus().equals("pending") && job.getAttemptCount() == 0));
    }

    @Test
    void freezesResolvedCustomThemeOnNewCardsAndJobs() {
        ResolvedVocabularyTheme theme = new ResolvedVocabularyTheme(
                "theme_user_1", 3, "Personal", "Purpose", "custom-markdown-v1", 1, "basic");
        VocabularyCaptureRequest request = new VocabularyCaptureRequest(
                "req-theme", List.of("innovative"), "en", "theme_user_1", null,
                new VocabularyCaptureRequest.Source("manual", null, "Manual", null, null, Map.of()));
        ArgumentCaptor<VocabularyCard> card = ArgumentCaptor.forClass(VocabularyCard.class);
        ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob> job =
                ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob.class);

        service.captureOne(7L, request, () -> theme, 0);

        verify(cards).insert(card.capture());
        verify(jobs).insertJob(job.capture());
        assertEquals("theme_user_1", card.getValue().getThemeUid());
        assertEquals(3, card.getValue().getThemeVersion());
        assertEquals("basic", card.getValue().getTemplateKey());
        assertEquals("theme_user_1", job.getValue().getThemeUid());
        assertEquals(3, job.getValue().getThemeVersion());
        assertEquals("basic", job.getValue().getTemplateKey());
    }

    @Test
    void freezesLegacyExamTemplateAsItsSystemTheme() {
        ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob> job =
                ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob.class);

        service.captureOne(7L, VocabularyCaptureRequest.manual("req-legacy", List.of("innovative"), "en", "exam"),
                () -> systemTheme("exam"), 0);

        verify(jobs).insertJob(job.capture());
        assertEquals("theme_system_exam", job.getValue().getThemeUid());
        assertEquals(1, job.getValue().getThemeVersion());
        assertEquals("exam", job.getValue().getTemplateKey());
    }

    @Test
    void mergesARepeatedTermWithoutCreatingAnotherJob() {
        VocabularyCard existing = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user");
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(existing);

        var outcome = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-2", List.of("innovative"), "en", "exam"),
                this::basicTheme, 0);
        var result = outcome.response();

        assertEquals("source_merged", result.action());
        assertEquals("rev_user", outcome.readyRevisionUid());
        verify(sources).insertSource(argThat(source -> source.getIdempotencyKey().equals("req-2:0")));
        verify(cards).touch(eq(7L), eq("card_1"), any());
        verifyNoInteractions(jobs);
    }

    @Test
    void marksInvalidInputForReviewWithoutSchedulingAi() {
        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-3", List.of("你好"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("needs_review", result.action());
        assertEquals("needs_review", result.status());
        verify(cards).insert(argThat(card -> card.getStatus().equals("needs_review")));
        verify(sources).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void marksExistingIdentityNeedsReviewWithoutReplacingRevisionOrTemplate() {
        String malformed = "(".repeat(121) + "innovative" + ")".repeat(121);
        VocabularyCard existing = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user");
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(existing);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-review-existing", List.of(malformed), "en", "exam"),
                this::basicTheme, 0).response();

        assertEquals("card_1", result.cardUid());
        assertEquals("needs_review", result.action());
        assertEquals("needs_review", result.status());
        verify(cards).touch(eq(7L), eq("card_1"), any());
        verify(cards).markNeedsReview(7L, "card_1");
        assertEquals("rev_user", existing.getActiveRevisionUid());
        assertEquals("basic", existing.getTemplateKey());
        verifyNoInteractions(jobs);
    }

    @Test
    void retryingTheSameRequestDoesNotInsertAnotherSourceOrJob() {
        when(sources.findSourceByIdempotencyKey(7L, "req-4:0"))
                .thenReturn(VocabularyTestFixtures.manualSource(null));
        when(cards.findByUidIncludingDeleted("card_1"))
                .thenReturn(VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user"));

        var outcome = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-4", List.of("innovative"), "en", "basic"),
                () -> {
                    throw new AssertionError("idempotent replay must not resolve a theme");
                }, 0);
        var result = outcome.response();

        assertEquals("source_merged", result.action());
        assertEquals("card_1", result.cardUid());
        assertEquals("rev_user", outcome.readyRevisionUid());
        verify(sources, never()).insertSource(any());
        verify(cards, never()).insert(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void repeatedDictionaryFavoriteRestoresSoftDeletedCardDespiteExistingIdempotencyKey() {
        VocabularyCardSource dictionarySource = VocabularyTestFixtures.manualSource(null);
        dictionarySource.setSourceType("dictionary");
        VocabularyCard deleted = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user");
        deleted.setDeletedAt(LocalDateTime.now());
        when(sources.findSourceByIdempotencyKey(7L, "dictionary-favorite:0"))
                .thenReturn(dictionarySource);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(deleted);
        when(cards.restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("ready"), any()))
                .thenReturn(1);

        var result = service.captureOne(7L,
                new VocabularyCaptureRequest(
                        "dictionary-favorite",
                        List.of("innovative"),
                        "en",
                        null,
                        null,
                        new VocabularyCaptureRequest.Source(
                                "dictionary", "dictionary:innovative", "词典收藏", null, null, Map.of())),
                this::basicTheme, 0).response();

        assertEquals("card_1", result.cardUid());
        assertEquals("source_merged", result.action());
        assertEquals("ready", result.status());
        verify(cards).restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("ready"), any());
        verify(sources, never()).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void idempotentRestoreUsesTheCardsFrozenThemeWithoutResolvingTheCurrentDefault() {
        VocabularyCardSource source = VocabularyTestFixtures.manualSource(null);
        VocabularyCard deleted = VocabularyTestFixtures.generating("card_1", null);
        deleted.setDeletedAt(LocalDateTime.now());
        deleted.setThemeUid("theme_user_1");
        deleted.setThemeVersion(3);
        when(sources.findSourceByIdempotencyKey(7L, "req-idempotent-restore:0")).thenReturn(source);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(deleted);
        when(cards.restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("generating"), any()))
                .thenReturn(1);

        var outcome = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-idempotent-restore", List.of("innovative"), "en", null),
                () -> {
                    throw new AssertionError("idempotent restore must not resolve the current default");
                }, 0);

        assertEquals("source_merged", outcome.response().action());
        assertEquals("theme_user_1", outcome.effectiveThemeUid());
        verify(jobs).insertJob(argThat(job ->
                job.getThemeUid().equals("theme_user_1") && job.getThemeVersion() == 3));
    }

    @Test
    void recaptureRestoresTheSameSoftDeletedCardWithoutRegeneratingAnActiveRevision() {
        VocabularyCard deleted = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        deleted.setDeletedAt(LocalDateTime.now());
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(deleted);
        when(cards.restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("ready"), any()))
                .thenReturn(1);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-5", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("card_1", result.cardUid());
        assertEquals("source_merged", result.action());
        assertEquals("ready", result.status());
        verify(cards).restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("ready"), any());
        verifyNoInteractions(jobs);
    }

    @Test
    void recaptureRestoresAndSchedulesCardWithoutAnActiveRevision() {
        VocabularyCard deleted = VocabularyTestFixtures.generating("card_1", null);
        deleted.setDeletedAt(LocalDateTime.now());
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(deleted);
        when(cards.restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("generating"), any()))
                .thenReturn(1);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-restore", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("source_merged", result.action());
        assertEquals("generating", result.status());
        verify(cards).restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("generating"), any());
        verify(jobs).insertJob(argThat(job -> job.getCardUid().equals("card_1")));
    }

    @Test
    void concurrentRestoreLoserReselectsActiveCardWithoutSchedulingAnotherJob() {
        VocabularyCard staleDeleted = VocabularyTestFixtures.generating("card_1", null);
        staleDeleted.setDeletedAt(LocalDateTime.now());
        VocabularyCard restoredByWinner = VocabularyTestFixtures.generating("card_1", null);
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative"))
                .thenReturn(staleDeleted, restoredByWinner);
        when(cards.restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("generating"), any()))
                .thenReturn(0);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-restore-race", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("card_1", result.cardUid());
        assertEquals("source_merged", result.action());
        assertEquals("generating", result.status());
        verify(cards).touch(eq(7L), eq("card_1"), any());
        verify(sources).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void concurrentCardInsertReselectsIdentityAndDoesNotCreateAnotherJob() {
        VocabularyCard winner = VocabularyTestFixtures.generating("card_winner", null);
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative"))
                .thenReturn(null, winner);
        doThrow(new DuplicateKeyException("duplicate identity")).when(cards).insert(any());

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-race", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("card_winner", result.cardUid());
        assertEquals("source_merged", result.action());
        verify(cards).touch(eq(7L), eq("card_winner"), any());
        verify(sources).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void concurrentRequestRetryUsesTheWinningSourceWithoutCreatingAnotherJob() {
        VocabularyCard existing = VocabularyTestFixtures.generating("card_1", null);
        VocabularyCardSource winningSource = VocabularyTestFixtures.manualSource(null);
        winningSource.setIdempotencyKey("req-source-race:0");
        when(sources.findSourceByIdempotencyKey(7L, "req-source-race:0"))
                .thenReturn(null, winningSource);
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(existing);
        doThrow(new DuplicateKeyException("duplicate source")).when(sources).insertSource(any());

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-source-race", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0).response();

        assertEquals("card_1", result.cardUid());
        assertEquals("source_merged", result.action());
        verifyNoInteractions(jobs);
    }

    @Test
    void capsContextAtGlobalLimitBeforePersistence() {
        String context = "x".repeat(2_001);
        VocabularyCaptureRequest request = new VocabularyCaptureRequest(
                "req-context", List.of("innovative"), "en", null, "basic",
                new VocabularyCaptureRequest.Source("manual", null, "Manual", null, context, Map.of()));
        ArgumentCaptor<VocabularyCardSource> sourceCaptor = ArgumentCaptor.forClass(VocabularyCardSource.class);

        service.captureOne(7L, request, this::basicTheme, 0);

        verify(sources).insertSource(sourceCaptor.capture());
        assertEquals(2_000, sourceCaptor.getValue().getContextText().length());
    }

    @Test
    void ocrCaptureMergesBatchAndIndexedSourceMetadataAndContext() throws Exception {
        VocabularyCaptureRequest request = new VocabularyCaptureRequest(
                "req-ocr", List.of("receive", "package"), "en", null, "basic",
                new VocabularyCaptureRequest.Source(
                        "ocr_image", "recognition:trace-1", "图片识别", null, "batch context",
                        Map.of(
                                "recognitionTraceId", "trace-1",
                                "fileName", "words.png",
                                "provider", "openai",
                                "model", "vision-model",
                                "promptVersion", "vocabulary-image-recognition-v1")),
                List.of(
                        new VocabularyCaptureRequest.ItemSource(
                                "I receive it",
                                Map.of("observedText", "recieve", "resolution", "suggestion_applied")),
                        new VocabularyCaptureRequest.ItemSource(
                                null,
                                Map.of("observedText", "package", "resolution", "accepted"))));
        ArgumentCaptor<VocabularyCardSource> sourceCaptor = ArgumentCaptor.forClass(VocabularyCardSource.class);

        service.captureOne(7L, request, this::basicTheme, 0);
        service.captureOne(7L, request, this::basicTheme, 1);

        verify(sources, org.mockito.Mockito.times(2)).insertSource(sourceCaptor.capture());
        List<VocabularyCardSource> inserted = sourceCaptor.getAllValues();
        assertEquals("ocr_image", inserted.get(0).getSourceType());
        assertEquals("I receive it", inserted.get(0).getContextText());
        assertEquals("batch context", inserted.get(1).getContextText());
        Map<?, ?> firstMetadata = new ObjectMapper().readValue(inserted.get(0).getMetadataJson(), Map.class);
        assertEquals("trace-1", firstMetadata.get("recognitionTraceId"));
        assertEquals("recieve", firstMetadata.get("observedText"));
        assertEquals("suggestion_applied", firstMetadata.get("resolution"));
        verify(jobs).insertJob(argThat(job -> job.getRequestJson().contains("\"index\":0")));
        verify(jobs).insertJob(argThat(job -> job.getRequestJson().contains("\"index\":1")));
    }

    @Test
    void usesResolvedThemeWhenRequestOmitsTemplate() {
        service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-default", List.of("innovative"), "en", null),
                () -> systemTheme("exam"), 0);

        verify(cards).insert(argThat(card ->
                card.getTemplateKey().equals("exam") && card.getTemplateVersion() == 1));
        verify(jobs).insertJob(argThat(job -> job.getTemplateKey().equals("exam")));
    }

    @Test
    void captureOneUsesRequiresNewTransaction() throws NoSuchMethodException {
        Method method = VocabularyCaptureItemService.class.getMethod(
                "captureOne", Long.class, VocabularyCaptureRequest.class, java.util.function.Supplier.class, int.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    void captureOneInCallerTransactionUsesRequiredTransaction() throws NoSuchMethodException {
        Method method = VocabularyCaptureItemService.class.getMethod(
                "captureOneInCallerTransaction", Long.class, VocabularyCaptureRequest.class,
                java.util.function.Supplier.class, int.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    void legacyCaptureOverloadsAreRemoved() {
        assertThrows(NoSuchMethodException.class, () -> VocabularyCaptureItemService.class.getDeclaredMethod(
                "captureOne", Long.class, VocabularyCaptureRequest.class, int.class));
        assertThrows(NoSuchMethodException.class, () -> VocabularyCaptureItemService.class.getDeclaredMethod(
                "captureOneInCallerTransaction", Long.class, VocabularyCaptureRequest.class, int.class));
    }

    @Test
    void generatedIdentifiersUseStablePrefixes() {
        ArgumentCaptor<VocabularyCard> cardCaptor = ArgumentCaptor.forClass(VocabularyCard.class);
        ArgumentCaptor<VocabularyCardSource> sourceCaptor = ArgumentCaptor.forClass(VocabularyCardSource.class);

        service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-uids", List.of("innovative"), "en", "basic"),
                this::basicTheme, 0);

        verify(cards).insert(cardCaptor.capture());
        verify(sources).insertSource(sourceCaptor.capture());
        assertTrue(cardCaptor.getValue().getCardUid().matches("card_[0-9a-f]{32}"));
        assertTrue(sourceCaptor.getValue().getSourceUid().matches("src_[0-9a-f]{32}"));
        verify(jobs).insertJob(argThat(job -> job.getJobUid().matches("job_[0-9a-f]{32}")));
    }

    private ResolvedVocabularyTheme basicTheme() {
        return systemTheme("basic");
    }

    private ResolvedVocabularyTheme systemTheme(String templateKey) {
        return new ResolvedVocabularyTheme(
                "theme_system_" + templateKey, 1, templateKey, "", "", 1, templateKey);
    }
}
