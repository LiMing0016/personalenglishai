package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock UserVocabularyPreferenceMapper preferences;
    VocabularyCaptureItemService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new VocabularyCaptureItemService(cards, sources, jobs, preferences,
                new VocabularyTermNormalizer(), new VocabularyTemplateRegistry(objectMapper), objectMapper);
    }

    @Test
    void createsCardSourceAndPendingJobBeforeReturning() {
        var request = VocabularyCaptureRequest.manual("req-1", List.of("In·nova·tive"), "en", "basic");

        VocabularyCaptureResponse.Item result = service.captureOne(7L, request, 0);

        assertEquals("created", result.action());
        assertEquals("generating", result.status());
        InOrder order = inOrder(cards, sources, jobs);
        order.verify(cards).insert(argThat(card ->
                card.getNormalizedTerm().equals("innovative") && card.getUserId().equals(7L)));
        order.verify(sources).insertSource(argThat(source -> source.getIdempotencyKey().equals("req-1:0")));
        order.verify(jobs).insertJob(argThat(job ->
                job.getStatus().equals("pending") && job.getAttemptCount() == 0));
        verify(preferences).upsertDefaultTemplate(7L, "basic");
    }

    @Test
    void mergesARepeatedTermWithoutCreatingAnotherJob() {
        VocabularyCard existing = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user");
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(existing);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-2", List.of("innovative"), "en", "exam"), 0);

        assertEquals("source_merged", result.action());
        verify(sources).insertSource(argThat(source -> source.getIdempotencyKey().equals("req-2:0")));
        verify(cards).touch(eq(7L), eq("card_1"), any());
        verifyNoInteractions(jobs);
    }

    @Test
    void marksInvalidInputForReviewWithoutSchedulingAi() {
        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-3", List.of("你好"), "en", "basic"), 0);

        assertEquals("needs_review", result.action());
        assertEquals("needs_review", result.status());
        verify(cards).insert(argThat(card -> card.getStatus().equals("needs_review")));
        verify(sources).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void retryingTheSameRequestDoesNotInsertAnotherSourceOrJob() {
        when(sources.findSourceByIdempotencyKey(7L, "req-4:0"))
                .thenReturn(VocabularyTestFixtures.manualSource(null));

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-4", List.of("innovative"), "en", "basic"), 0);

        assertEquals("source_merged", result.action());
        assertEquals("card_1", result.cardUid());
        verify(sources, never()).insertSource(any());
        verify(cards, never()).insert(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void recaptureRestoresTheSameSoftDeletedCardWithoutRegeneratingAnActiveRevision() {
        VocabularyCard deleted = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        deleted.setDeletedAt(LocalDateTime.now());
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(deleted);

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-5", List.of("innovative"), "en", "basic"), 0);

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

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-restore", List.of("innovative"), "en", "basic"), 0);

        assertEquals("source_merged", result.action());
        assertEquals("generating", result.status());
        verify(cards).restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), eq("generating"), any());
        verify(jobs).insertJob(argThat(job -> job.getCardUid().equals("card_1")));
    }

    @Test
    void concurrentCardInsertReselectsIdentityAndDoesNotCreateAnotherJob() {
        VocabularyCard winner = VocabularyTestFixtures.generating("card_winner", null);
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative"))
                .thenReturn(null, winner);
        doThrow(new DuplicateKeyException("duplicate identity")).when(cards).insert(any());

        var result = service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-race", List.of("innovative"), "en", "basic"), 0);

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
                VocabularyCaptureRequest.manual("req-source-race", List.of("innovative"), "en", "basic"), 0);

        assertEquals("card_1", result.cardUid());
        assertEquals("source_merged", result.action());
        verifyNoInteractions(jobs);
    }

    @Test
    void capsContextAtGlobalLimitBeforePersistence() {
        String context = "x".repeat(2_001);
        VocabularyCaptureRequest request = new VocabularyCaptureRequest(
                "req-context", List.of("innovative"), "en", "basic",
                new VocabularyCaptureRequest.Source("manual", null, "Manual", null, context, Map.of()));
        ArgumentCaptor<VocabularyCardSource> sourceCaptor = ArgumentCaptor.forClass(VocabularyCardSource.class);

        service.captureOne(7L, request, 0);

        verify(sources).insertSource(sourceCaptor.capture());
        assertEquals(2_000, sourceCaptor.getValue().getContextText().length());
    }

    @Test
    void usesStoredDefaultWhenRequestOmitsTemplate() {
        UserVocabularyPreference preference = new UserVocabularyPreference();
        preference.setUserId(7L);
        preference.setDefaultTemplateKey("exam");
        when(preferences.findPreferenceByUser(7L)).thenReturn(preference);

        service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-default", List.of("innovative"), "en", null), 0);

        verify(preferences).upsertDefaultTemplate(7L, "exam");
        verify(cards).insert(argThat(card ->
                card.getTemplateKey().equals("exam") && card.getTemplateVersion() == 1));
        verify(jobs).insertJob(argThat(job -> job.getTemplateKey().equals("exam")));
    }

    @Test
    void captureOneUsesRequiresNewTransaction() throws NoSuchMethodException {
        Method method = VocabularyCaptureItemService.class.getMethod(
                "captureOne", Long.class, VocabularyCaptureRequest.class, int.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    void generatedIdentifiersUseStablePrefixes() {
        ArgumentCaptor<VocabularyCard> cardCaptor = ArgumentCaptor.forClass(VocabularyCard.class);
        ArgumentCaptor<VocabularyCardSource> sourceCaptor = ArgumentCaptor.forClass(VocabularyCardSource.class);

        service.captureOne(7L,
                VocabularyCaptureRequest.manual("req-uids", List.of("innovative"), "en", "basic"), 0);

        verify(cards).insert(cardCaptor.capture());
        verify(sources).insertSource(sourceCaptor.capture());
        assertTrue(cardCaptor.getValue().getCardUid().matches("card_[0-9a-f]{32}"));
        assertTrue(sourceCaptor.getValue().getSourceUid().matches("src_[0-9a-f]{32}"));
        verify(jobs).insertJob(argThat(job -> job.getJobUid().matches("job_[0-9a-f]{32}")));
    }
}
