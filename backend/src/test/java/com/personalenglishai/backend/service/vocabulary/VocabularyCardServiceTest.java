package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyCardServiceTest {
    @Mock VocabularyCardMapper cards;
    @Mock VocabularySourceMapper sources;
    @Mock VocabularyRevisionMapper revisions;
    @Mock VocabularyGenerationJobMapper jobs;
    @Mock UserVocabularyPreferenceMapper preferences;

    ObjectMapper objectMapper;
    VocabularyTemplateRegistry templateRegistry;
    VocabularyCardService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        templateRegistry = new VocabularyTemplateRegistry(objectMapper);
        service = new VocabularyCardService(
                cards, sources, revisions, jobs, preferences, templateRegistry, objectMapper);
    }

    @Test
    void rejectsMissingOrForeignCardBeforeReadingChildren() {
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.getDetail(7L, "card_1"));

        assertEquals(ErrorCode.VOCABULARY_CARD_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(sources, revisions, jobs);
    }

    @Test
    void mapsOwnedCardSourcesActiveRevisionJsonAndLatestGeneration() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        card.setDisplayTerm("Innovative");
        VocabularyCardSource ownedSource = VocabularyTestFixtures.manualSource("A product context");
        ownedSource.setMetadataJson("{\"selection\":\"innovative\"}");
        VocabularyCardSource foreignSource = VocabularyTestFixtures.manualSource("foreign context");
        foreignSource.setSourceUid("src_foreign");
        foreignSource.setUserId(8L);
        VocabularyGenerationJob job = VocabularyTestFixtures.pendingJob("job_1", "card_1", null, 1);
        job.setStatus("failed");
        job.setErrorMessage("upstream timeout");

        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of(ownedSource, foreignSource));
        when(revisions.findRevision("rev_1")).thenReturn(VocabularyTestFixtures.userRevision("rev_1"));
        when(jobs.findLatestByCard("card_1")).thenReturn(job);

        var result = service.getDetail(7L, "card_1");

        assertEquals("card_1", result.cardUid());
        assertEquals("innovative", result.content().get("term").asText());
        assertEquals(List.of("manual"), result.sourceTypes());
        assertEquals(1, result.sources().size());
        assertEquals("manual", result.sources().get(0).sourceType());
        assertEquals("innovative", result.sources().get(0).metadata().get("selection").asText());
        assertEquals("failed", result.generationStatus());
        assertEquals("upstream timeout", result.generationError());
        assertEquals(card.getCreatedAt(), result.createdAt());
    }

    @Test
    void listsOwnedCardsWithClampedPaginationAndDistinctOwnedSourceTypes() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        VocabularyCardSource manual = VocabularyTestFixtures.manualSource(null);
        VocabularyCardSource duplicateManual = VocabularyTestFixtures.manualSource(null);
        duplicateManual.setSourceUid("src_2");
        VocabularyCardSource foreignDictionary = VocabularyTestFixtures.manualSource(null);
        foreignDictionary.setSourceUid("src_3");
        foreignDictionary.setUserId(8L);
        foreignDictionary.setSourceType("dictionary");
        when(cards.listByUser(7L, "inno", "ready", "manual", 0, 50)).thenReturn(List.of(card));
        when(cards.countByUser(7L, "inno", "ready", "manual")).thenReturn(1L);
        when(sources.listSources("card_1"))
                .thenReturn(List.of(manual, duplicateManual, foreignDictionary));

        var result = service.list(7L, "inno", "ready", "manual", 0, 99);

        assertEquals(1, result.getPage());
        assertEquals(50, result.getSize());
        assertEquals(1, result.getTotal());
        assertEquals(List.of("manual"), result.getItems().get(0).sourceTypes());
    }

    @Test
    void templateCatalogFallsBackToBasicWhenPreferenceIsMissing() {
        when(preferences.findPreferenceByUser(7L)).thenReturn(null);

        var result = service.templateCatalog(7L);

        assertEquals("basic", result.defaultTemplateKey());
        assertEquals(List.of("basic", "exam", "reading"),
                result.items().stream().map(VocabularyTemplateResponse::key).toList());
    }

    @Test
    void templateCatalogFallsBackToBasicWhenPreferenceIsUnsupported() {
        UserVocabularyPreference preference = new UserVocabularyPreference();
        preference.setUserId(7L);
        preference.setDefaultTemplateKey("retired");
        when(preferences.findPreferenceByUser(7L)).thenReturn(preference);

        var result = service.templateCatalog(7L);

        assertEquals("basic", result.defaultTemplateKey());
    }

    @Test
    void templateCatalogKeepsSupportedPreference() {
        UserVocabularyPreference preference = new UserVocabularyPreference();
        preference.setUserId(7L);
        preference.setDefaultTemplateKey("exam");
        when(preferences.findPreferenceByUser(7L)).thenReturn(preference);

        var result = service.templateCatalog(7L);

        assertEquals("exam", result.defaultTemplateKey());
        assertTrue(result.items().stream().anyMatch(item -> item.key().equals("exam")));
        verify(preferences).findPreferenceByUser(7L);
    }
}
