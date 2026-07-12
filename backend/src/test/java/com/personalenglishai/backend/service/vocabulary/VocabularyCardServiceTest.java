package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.ResolveVocabularyConflictRequest;
import com.personalenglishai.backend.dto.vocabulary.RegenerateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.UpdateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
    @Mock VocabularyRevisionWriteService revisionWriter;
    @Mock VocabularyThemeService themeService;
    @Mock VocabularyThemeMapper themes;

    ObjectMapper objectMapper;
    VocabularyTemplateRegistry templateRegistry;
    VocabularyCardService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        templateRegistry = new VocabularyTemplateRegistry(objectMapper);
        service = new VocabularyCardService(
                cards, sources, revisions, jobs, preferences, themeService, themes,
                templateRegistry, new VocabularyCoreContentCodec(objectMapper), objectMapper, revisionWriter);
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
    void rejectsMalformedActiveRevisionJsonWithoutExposingRawContent() {
        String malformedContent = "{\"raw-secret\":\"private-value\"";
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        var revision = VocabularyTestFixtures.userRevision("rev_1");
        revision.setContentJson(malformedContent);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(revisions.findRevision("rev_1")).thenReturn(revision);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.getDetail(7L, "card_1"));

        assertEquals("invalid stored content_json", error.getMessage());
        assertFalse(error.getMessage().contains(malformedContent));
        assertNull(error.getCause());
    }

    @Test
    void rejectsMalformedSourceMetadataJsonWithoutExposingRawContent() {
        String malformedMetadata = "{\"raw-secret\":\"private-value\"";
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", null);
        VocabularyCardSource source = VocabularyTestFixtures.manualSource(null);
        source.setMetadataJson(malformedMetadata);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of(source));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.getDetail(7L, "card_1"));

        assertEquals("invalid stored metadata_json", error.getMessage());
        assertFalse(error.getMessage().contains(malformedMetadata));
        assertNull(error.getCause());
    }

    @Test
    void listsOwnedCardsWithClampedPaginationAndDistinctOwnedSourceTypes() {
        VocabularyCard firstCard = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        VocabularyCard secondCard = VocabularyTestFixtures.ready("card_2", 7L, "sustainable", "rev_2");
        VocabularyCardSource manual = VocabularyTestFixtures.manualSource(null);
        manual.setSourceCount(2);
        VocabularyCardSource dictionary = VocabularyTestFixtures.manualSource(null);
        dictionary.setSourceUid("src_2");
        dictionary.setSourceType("dictionary");
        dictionary.setSourceCount(2);
        VocabularyCardSource secondManual = VocabularyTestFixtures.manualSource(null);
        secondManual.setSourceUid("src_3");
        secondManual.setCardUid("card_2");
        secondManual.setSourceCount(1);
        var firstRevision = VocabularyTestFixtures.userRevision("rev_1");
        firstRevision.setContentJson("""
                {"term":"innovative","phonetic":"/in/","partOfSpeech":"adjective","definitions":["using new ideas"],"examples":[],"notes":""}
                """);
        var secondRevision = VocabularyTestFixtures.userRevision("rev_2");
        secondRevision.setCardUid("card_2");
        VocabularyGenerationJob running = VocabularyTestFixtures.pendingJob("job_1", "card_1", "rev_1", 1);
        running.setStatus("running");
        when(cards.listByUser(7L, null, "ready", null, "az", 0, 50))
                .thenReturn(List.of(firstCard, secondCard));
        when(cards.countByUser(7L, null, "ready", null)).thenReturn(2L);
        when(sources.listDistinctSourceTypesByCardUids(7L, List.of("card_1", "card_2")))
                .thenReturn(List.of(manual, dictionary, secondManual));
        when(jobs.listLatestByCardUids(7L, List.of("card_1", "card_2"))).thenReturn(List.of(running));
        when(revisions.listRevisions("card_1")).thenReturn(List.of(firstRevision));
        when(revisions.listRevisions("card_2")).thenReturn(List.of(secondRevision));

        var result = service.list(7L, null, "ready", null, "az", 0, 99);

        assertEquals(1, result.getPage());
        assertEquals(50, result.getSize());
        assertEquals(2, result.getTotal());
        assertEquals(List.of("manual", "dictionary"), result.getItems().get(0).sourceTypes());
        assertEquals(List.of("manual"), result.getItems().get(1).sourceTypes());
        assertEquals("running", result.getItems().get(0).generationStatus());
        assertNull(result.getItems().get(1).generationStatus());
        assertEquals("/in/", result.getItems().get(0).phonetic());
        assertEquals("using new ideas", result.getItems().get(0).coreDefinition());
        assertEquals(2, result.getItems().get(0).sourceCount());
        verify(sources).listDistinctSourceTypesByCardUids(7L, List.of("card_1", "card_2"));
        verify(jobs).listLatestByCardUids(7L, List.of("card_1", "card_2"));
        verify(sources, never()).listSources(anyString());
    }

    @Test
    void listRejectsUnsupportedSort() {
        assertThrows(IllegalArgumentException.class,
                () -> service.list(7L, null, null, null, "oldest", 1, 20));
        verifyNoInteractions(cards, sources, revisions, jobs);
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

    @Test
    void updateAppendsUserRevisionPreservesTermAndActivatesWithBaseGuard() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"term":"client override","phonetic":"","partOfSpeech":"adjective","definitions":["new"],"examples":[],"notes":"edited"}
                """);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisionWriter.appendAndActivate(eq(7L), eq(card), any()))
                .thenReturn(VocabularyRevisionWriteService.WriteOutcome.ACTIVATED);

        service.update(7L, "card_1", new UpdateVocabularyCardRequest("rev_1", null, null, content, "Edited"));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisionWriter).appendAndActivate(eq(7L), eq(card), revision.capture());
        assertEquals("user", revision.getValue().getAuthorType());
        assertEquals("rev_1", revision.getValue().getBaseRevisionUid());
        assertEquals("innovative", objectMapper.readTree(revision.getValue().getContentJson()).get("term").asText());
    }

    @Test
    void staleUpdateReturnsCurrentAndCandidateConflictSummary() throws Exception {
        VocabularyCard staleSnapshot = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_old");
        VocabularyCard currentCard = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        currentCard.setStatus("needs_review");
        AtomicReference<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> candidate =
                new AtomicReference<>();
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(staleSnapshot, currentCard);
        when(revisionWriter.appendAndActivate(eq(7L), eq(staleSnapshot), any())).thenAnswer(invocation -> {
            candidate.set(invocation.getArgument(2));
            return VocabularyRevisionWriteService.WriteOutcome.STALE;
        });
        when(revisions.findRevision("rev_old")).thenReturn(VocabularyTestFixtures.userRevision("rev_old"));
        when(revisions.findRevision("rev_current")).thenReturn(VocabularyTestFixtures.userRevision("rev_current"));
        when(revisions.listRevisions("card_1")).thenAnswer(ignored -> List.of(candidate.get()));
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":"edited"}
                """);

        VocabularyRevisionConflictException error = assertThrows(
                VocabularyRevisionConflictException.class,
                () -> service.update(7L, "card_1", new UpdateVocabularyCardRequest(
                        "rev_old", null, null, content, "Edited")));

        assertEquals(ErrorCode.VOCABULARY_REVISION_CONFLICT, error.getErrorCode());
        assertEquals("rev_current", error.getConflict().currentRevisionUid());
        assertEquals(candidate.get().getRevisionUid(), error.getConflict().candidateRevisionUid());
        assertEquals("user", candidate.get().getAuthorType());
        verify(revisions).listRevisions("card_1");
    }

    @Test
    void legacyDetailProjectsCoreWhileRetainingLegacyContent() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_legacy");
        var revision = VocabularyTestFixtures.userRevision("rev_legacy");
        revision.setContentJson(VocabularyTestFixtures.legacyVocabularyContent(objectMapper).toString());
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(revisions.findRevision("rev_legacy")).thenReturn(revision);

        var result = service.getDetail(7L, "card_1");

        assertEquals("wrong-ai-term", result.content().path("term").asText());
        assertEquals("record", result.core().path("term").asText());
        assertEquals("/ˈrekɔːd/", result.core().path("phonetics").get(0).path("text").asText());
        assertNull(result.markdown());
        assertNull(result.contentFormatVersion());
    }

    @Test
    void legacyClientEditRemainsLegacyForLaterFieldMerge() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_legacy");
        var base = VocabularyTestFixtures.userRevision("rev_legacy");
        ObjectNode editedContent = VocabularyTestFixtures.legacyVocabularyContent(objectMapper);
        editedContent.put("notes", "legacy edit notes");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_legacy")).thenReturn(base);
        when(revisionWriter.appendAndActivate(eq(7L), eq(card), any()))
                .thenReturn(VocabularyRevisionWriteService.WriteOutcome.ACTIVATED);

        service.update(7L, "card_1", new UpdateVocabularyCardRequest(
                "rev_legacy", null, null, editedContent, "Legacy edit"));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> edited =
                org.mockito.ArgumentCaptor.forClass(
                        com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisionWriter).appendAndActivate(eq(7L), eq(card), edited.capture());
        var current = edited.getValue();
        assertNull(current.getContentFormatVersion());
        assertTrue(current.getCoreJson() != null && !current.getCoreJson().isBlank());

        reset(cards, revisions);
        VocabularyCard conflicted = VocabularyTestFixtures.ready(
                "card_1", 7L, "record", current.getRevisionUid());
        conflicted.setStatus("needs_review");
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid(current.getRevisionUid());
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(conflicted);
        when(revisions.findRevision(current.getRevisionUid())).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq(current.getRevisionUid()), anyString(),
                eq("ready"), eq("basic"), eq(1))).thenReturn(1);
        var mergedDefinitions = objectMapper.createArrayNode().add("merged legacy definition");

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "definitions", mergedDefinitions,
                        "notes", objectMapper.getNodeFactory().textNode("merged legacy notes"))));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> merged =
                org.mockito.ArgumentCaptor.forClass(
                        com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisions).insertRevision(merged.capture());
        JsonNode stored = objectMapper.readTree(merged.getValue().getContentJson());
        assertEquals("merged legacy notes", stored.path("notes").asText());
        assertEquals("merged legacy definition", stored.path("definitions").get(0).asText());
        assertNull(merged.getValue().getContentFormatVersion());
    }

    @Test
    void newClientEditStoresCoreMarkdownAndCompatibilityContentWithCardIdentity() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_1");
        card.setThemeUid("theme_user_latest");
        card.setThemeVersion(5);
        var base = VocabularyTestFixtures.userRevision("rev_1");
        base.setThemeUid("theme_user_1");
        base.setThemeVersion(3);
        base.setContentFormatVersion(2);
        ObjectNode core = (ObjectNode) new VocabularyCoreContentCodec(objectMapper)
                .fromLegacy("candidate override", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_1")).thenReturn(base);
        when(revisionWriter.appendAndActivate(eq(7L), eq(card), any()))
                .thenReturn(VocabularyRevisionWriteService.WriteOutcome.ACTIVATED);

        service.update(7L, "card_1", new UpdateVocabularyCardRequest(
                "rev_1", core, "## Exam tips", null, "Edited"));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisionWriter).appendAndActivate(eq(7L), eq(card), revision.capture());
        var stored = revision.getValue();
        assertEquals("record", objectMapper.readTree(stored.getCoreJson()).path("term").asText());
        assertEquals("record", objectMapper.readTree(stored.getContentJson()).path("term").asText());
        assertEquals("## Exam tips", objectMapper.readTree(stored.getContentJson()).path("markdown").asText());
        assertEquals("## Exam tips", stored.getContentMarkdown());
        assertEquals("theme_user_1", stored.getThemeUid());
        assertEquals(3, stored.getThemeVersion());
        assertEquals(2, stored.getContentFormatVersion());
    }

    @Test
    void newClientEditRejectsRawHtmlMarkdownBeforeAppending() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_1");
        ObjectNode core = new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", null);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);

        assertThrows(IllegalArgumentException.class, () -> service.update(
                7L, "card_1", new UpdateVocabularyCardRequest(
                        "rev_1", core, "## Tips\n<script>alert(1)</script>", null, "Edited")));

        verifyNoInteractions(revisionWriter);
    }

    @Test
    void staleNewClientEditRetainsCoreAndMarkdownOnConflictCandidate() throws Exception {
        VocabularyCard stale = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_old");
        VocabularyCard current = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_current");
        current.setStatus("needs_review");
        AtomicReference<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> candidate =
                new AtomicReference<>();
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(stale, current);
        when(revisionWriter.appendAndActivate(eq(7L), eq(stale), any())).thenAnswer(invocation -> {
            candidate.set(invocation.getArgument(2));
            return VocabularyRevisionWriteService.WriteOutcome.STALE;
        });
        when(revisions.findRevision("rev_old")).thenReturn(VocabularyTestFixtures.userRevision("rev_old"));
        when(revisions.findRevision("rev_current")).thenReturn(VocabularyTestFixtures.userRevision("rev_current"));
        when(revisions.listRevisions("card_1")).thenAnswer(ignored -> List.of(candidate.get()));
        ObjectNode core = new VocabularyCoreContentCodec(objectMapper).fromLegacy("wrong", null);

        assertThrows(VocabularyRevisionConflictException.class, () -> service.update(
                7L, "card_1", new UpdateVocabularyCardRequest(
                        "rev_old", core, "## Candidate", null, "Edited")));

        assertEquals("record", objectMapper.readTree(candidate.get().getCoreJson()).path("term").asText());
        assertEquals("## Candidate", candidate.get().getContentMarkdown());
        assertEquals("## Candidate", objectMapper.readTree(candidate.get().getContentJson()).path("markdown").asText());
    }

    @Test
    void detailAndRevisionResponsesExposeFrozenThemeCoreAndMarkdown() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_1");
        var revision = VocabularyTestFixtures.userRevision("rev_1");
        ObjectNode core = new VocabularyCoreContentCodec(objectMapper).fromLegacy(
                "record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        revision.setThemeUid("theme_user_1");
        revision.setThemeVersion(3);
        revision.setCoreJson(core.toString());
        revision.setContentMarkdown("## Exam tips");
        revision.setContentFormatVersion(2);
        VocabularyThemeRevision theme = new VocabularyThemeRevision();
        theme.setThemeUid("theme_user_1");
        theme.setVersion(3);
        theme.setNameSnapshot("My exam theme");
        theme.setPurpose("Exam review");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(sources.listSources("card_1")).thenReturn(List.of());
        when(revisions.findRevision("rev_1")).thenReturn(revision);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(revision));
        when(themes.findRevision("theme_user_1", 3)).thenReturn(theme);

        var detail = service.getDetail(7L, "card_1");
        var history = service.revisions(7L, "card_1").items().get(0);

        assertEquals("theme_user_1", detail.theme().themeUid());
        assertEquals("My exam theme", detail.theme().name());
        assertEquals(3, detail.themeVersion());
        assertEquals("record", detail.core().path("term").asText());
        assertEquals("## Exam tips", detail.markdown());
        assertEquals(2, detail.contentFormatVersion());
        assertEquals("theme_user_1", history.theme().themeUid());
        assertEquals("record", history.core().path("term").asText());
        assertEquals("## Exam tips", history.markdown());
    }

    @Test
    void deleteSoftDeletesOwnedCardAndCancelsPendingAndRunningJobs() {
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(VocabularyTestFixtures.ready("card_1", "rev_1"));
        when(cards.softDelete(7L, "card_1")).thenReturn(1);

        service.delete(7L, "card_1");

        verify(jobs).cancelActiveForCard("card_1");
        verify(cards).softDelete(7L, "card_1");
    }

    @Test
    void regenerateCancelsExistingWorkAndQueuesJobFromActiveRevision() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);

        var result = service.regenerate(7L, "card_1");

        assertEquals("pending", result.status());
        org.mockito.ArgumentCaptor<VocabularyGenerationJob> job =
                org.mockito.ArgumentCaptor.forClass(VocabularyGenerationJob.class);
        verify(jobs).cancelActiveForCard("card_1");
        verify(jobs).insertJob(job.capture());
        assertEquals("rev_1", job.getValue().getBaseRevisionUid());
    }

    @Test
    void regenerateUsesRequestedTemplateAndVersion() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(themeService.resolve(7L, null, "exam")).thenReturn(new ResolvedVocabularyTheme(
                "theme_system_exam", 1, "Exam", "", "", 1, "exam"));
        when(themeService.resolve(7L, null, "unsupported"))
                .thenThrow(new IllegalArgumentException("unsupported legacy template"));

        service.regenerate(7L, "card_1", new RegenerateVocabularyCardRequest(null, true, "exam"));

        org.mockito.ArgumentCaptor<VocabularyGenerationJob> job =
                org.mockito.ArgumentCaptor.forClass(VocabularyGenerationJob.class);
        verify(jobs).insertJob(job.capture());
        assertEquals("exam", job.getValue().getTemplateKey());
        assertEquals(1, job.getValue().getTemplateVersion());
        assertThrows(IllegalArgumentException.class,
                () -> service.regenerate(7L, "card_1", new RegenerateVocabularyCardRequest(
                        null, true, "unsupported")));
    }

    @Test
    void regenerateKeepsTheFrozenThemeWhenLatestVersionIsNotRequested() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        card.setThemeUid("theme_user_1");
        card.setThemeVersion(2);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);

        service.regenerate(7L, "card_1", new RegenerateVocabularyCardRequest("theme_user_1", false, null));

        org.mockito.ArgumentCaptor<VocabularyGenerationJob> job =
                org.mockito.ArgumentCaptor.forClass(VocabularyGenerationJob.class);
        verify(jobs).insertJob(job.capture());
        assertEquals("theme_user_1", job.getValue().getThemeUid());
        assertEquals(2, job.getValue().getThemeVersion());
        verifyNoInteractions(themeService);
    }

    @Test
    void regenerateResolvesAndFreezesTheLatestThemeOnlyWhenRequested() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        card.setThemeUid("theme_user_1");
        card.setThemeVersion(2);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(themeService.resolve(7L, "theme_user_1", null)).thenReturn(new ResolvedVocabularyTheme(
                "theme_user_1", 3, "Personal", "Purpose", "custom-markdown-v1", 1, "basic"));

        service.regenerate(7L, "card_1", new RegenerateVocabularyCardRequest("theme_user_1", true, null));

        org.mockito.ArgumentCaptor<VocabularyGenerationJob> job =
                org.mockito.ArgumentCaptor.forClass(VocabularyGenerationJob.class);
        verify(themeService).resolve(7L, "theme_user_1", null);
        verify(jobs).insertJob(job.capture());
        assertEquals("theme_user_1", job.getValue().getThemeUid());
        assertEquals(3, job.getValue().getThemeVersion());
        assertEquals("basic", job.getValue().getTemplateKey());
    }

    @Test
    void regenerateLatestVersionWithoutThemeUidUsesTheCardsFrozenThemeUid() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        card.setThemeUid("theme_user_1");
        card.setThemeVersion(2);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(themeService.resolve(7L, "theme_user_1", null)).thenReturn(new ResolvedVocabularyTheme(
                "theme_user_1", 3, "Personal", "Purpose", "custom-markdown-v1", 1, "basic"));

        service.regenerate(7L, "card_1", new RegenerateVocabularyCardRequest(null, true, null));

        org.mockito.ArgumentCaptor<VocabularyGenerationJob> job =
                org.mockito.ArgumentCaptor.forClass(VocabularyGenerationJob.class);
        verify(themeService).resolve(7L, "theme_user_1", null);
        verify(jobs).insertJob(job.capture());
        assertEquals("theme_user_1", job.getValue().getThemeUid());
        assertEquals(3, job.getValue().getThemeVersion());
    }

    @Test
    void retryRequeuesOnlyLatestFailedJob() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        VocabularyGenerationJob failed = VocabularyTestFixtures.pendingJob("job_1", "card_1", "rev_1", 3);
        failed.setStatus("failed");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(jobs.findLatestByCard("card_1")).thenReturn(failed);
        when(jobs.retryFailed("card_1", "job_1")).thenReturn(1);

        var result = service.retry(7L, "card_1");

        assertEquals("job_1", result.jobUid());
        verify(jobs).retryFailed("card_1", "job_1");
    }

    @Test
    void revisionsExposeCandidateContentAndConflictState() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_old");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));

        var result = service.revisions(7L, "card_1");

        assertEquals("rev_current", result.currentRevisionUid());
        assertEquals("needs_review", result.conflictStatus());
        assertEquals("rev_candidate", result.items().get(0).revisionUid());
        assertTrue(result.items().get(0).candidate());
        assertEquals("innovative", result.items().get(0).content().get("term").asText());
    }

    @Test
    void mergeConflictOnlyAcceptsTemplateFieldsAndCreatesSystemMergeRevision() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_old");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));

        assertThrows(IllegalArgumentException.class, () -> service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "term", objectMapper.getNodeFactory().textNode("blocked"),
                        "notes", objectMapper.getNodeFactory().textNode("merged")))));
    }

    @Test
    void mergeConflictAppendsSystemMergeRevisionAndUsesCurrentRevisionGuard() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        current.setContentJson("""
                {"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":"current"}
                """);
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_old");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_current"), anyString(),
                eq("ready"), eq("basic"), eq(1))).thenReturn(1);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "notes", objectMapper.getNodeFactory().textNode("merged"))));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisions).insertRevision(revision.capture());
        assertEquals("system_merge", revision.getValue().getAuthorType());
        assertEquals("rev_current", revision.getValue().getBaseRevisionUid());
        assertEquals("innovative", objectMapper.readTree(revision.getValue().getContentJson()).get("term").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"keep_current", "use_ai", "merge_fields"})
    void everyConflictChoiceAppendsGuardedSystemMergeRevision(String choice) throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        current.setContentJson("""
                {"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":["current"],"examples":[],"notes":"current"}
                """);
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_current");
        candidate.setContentJson("""
                {"term":"tampered","phonetic":"","partOfSpeech":"adjective","definitions":["candidate"],"examples":[],"notes":"candidate"}
                """);
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_current"), anyString(),
                eq("ready"), eq("basic"), eq(1))).thenReturn(1);
        ResolveVocabularyConflictRequest request = "merge_fields".equals(choice)
                ? new ResolveVocabularyConflictRequest(choice, Map.of(
                        "notes", objectMapper.getNodeFactory().textNode("merged")))
                : new ResolveVocabularyConflictRequest(choice, null);

        service.resolveConflict(7L, "card_1", "rev_candidate", request);

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisions).insertRevision(revision.capture());
        assertEquals("system_merge", revision.getValue().getAuthorType());
        assertEquals("rev_current", revision.getValue().getBaseRevisionUid());
        assertEquals("innovative", objectMapper.readTree(revision.getValue().getContentJson()).get("term").asText());
    }

    @Test
    void keepCurrentPreservesLegacyTemplateFieldsWhenProjectionCoreExists() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        ObjectNode legacy = VocabularyTestFixtures.legacyVocabularyContent(objectMapper);
        legacy.put("notes", "historical legacy notes");
        current.setContentJson(legacy.toString());
        current.setCoreJson(new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", legacy).toString());
        current.setContentFormatVersion(1);
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_current"), anyString(),
                eq("ready"), eq("basic"), eq(1))).thenReturn(1);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("keep_current", null));

        var resolved = capturedInsertedRevision();
        JsonNode stored = objectMapper.readTree(resolved.getContentJson());
        assertEquals("historical legacy notes", stored.path("notes").asText());
        assertEquals("The record was complete.", stored.path("examples").get(0).asText());
        assertNull(resolved.getContentFormatVersion());
    }

    @Test
    void emptyMergeOnNewFormatCurrentAppendsFrozenRevisionWithoutChangingContent() throws Exception {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy(
                "record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of()));

        var merged = capturedInsertedRevision();
        assertEquals(currentCore, objectMapper.readTree(merged.getCoreJson()));
        assertEquals("## Current notes", merged.getContentMarkdown());
        assertFrozenNewFormatResolution(merged);
    }

    @Test
    void newFormatMergeCanReplaceCoreWithoutChangingMarkdown() throws Exception {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", null);
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current);
        ObjectNode replacementCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy(
                "record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of("core", replacementCore)));

        var merged = capturedInsertedRevision();
        assertEquals(replacementCore, objectMapper.readTree(merged.getCoreJson()));
        assertEquals("## Current notes", merged.getContentMarkdown());
        assertFrozenNewFormatResolution(merged);
    }

    @Test
    void newFormatMergeCanReplaceMarkdownWithoutChangingCore() throws Exception {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy(
                "record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "markdown", objectMapper.getNodeFactory().textNode("## Merged notes"))));

        var merged = capturedInsertedRevision();
        assertEquals(currentCore, objectMapper.readTree(merged.getCoreJson()));
        assertEquals("## Merged notes", merged.getContentMarkdown());
        assertFrozenNewFormatResolution(merged);
    }

    @Test
    void newFormatMergeAcceptsMarkdownAtTwentyThousandCharacters() {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", null);
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current);
        String markdown = "a".repeat(20_000);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "markdown", objectMapper.getNodeFactory().textNode(markdown))));

        assertEquals(markdown, capturedInsertedRevision().getContentMarkdown());
    }

    @Test
    void newFormatMergeRejectsMarkdownOverTwentyThousandCharacters() {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", null);
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current, false);

        assertThrows(IllegalArgumentException.class, () -> service.resolveConflict(
                7L,
                "card_1",
                "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "markdown", objectMapper.getNodeFactory().textNode("a".repeat(20_001))))));

        verify(revisions, never()).insertRevision(any());
    }

    @Test
    void newFormatMergeKeepsCardTermIdentityAndRejectsRawHtmlMarkdown() throws Exception {
        ObjectNode currentCore = new VocabularyCoreContentCodec(objectMapper).fromLegacy("record", null);
        var current = newFormatConflictCurrent(currentCore, "## Current notes");
        arrangeNewFormatConflict(current);
        ObjectNode tamperedCore = currentCore.deepCopy();
        tamperedCore.put("term", "tampered");

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of("core", tamperedCore)));

        var merged = capturedInsertedRevision();
        assertEquals("record", objectMapper.readTree(merged.getCoreJson()).path("term").asText());
        assertFrozenNewFormatResolution(merged);

        reset(revisions, cards);
        arrangeNewFormatConflict(current, false);
        assertThrows(IllegalArgumentException.class, () -> service.resolveConflict(
                7L,
                "card_1",
                "rev_candidate",
                new ResolveVocabularyConflictRequest("merge_fields", Map.of(
                        "markdown", objectMapper.getNodeFactory().textNode("<script>alert(1)</script>")))));
    }

    @Test
    void acceptingNewFormatCandidatePreservesCoreMarkdownAndFrozenTheme() throws Exception {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        ObjectNode core = new VocabularyCoreContentCodec(objectMapper).fromLegacy(
                "candidate override", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_current");
        candidate.setThemeUid("theme_user_1");
        candidate.setThemeVersion(3);
        candidate.setCoreJson(core.toString());
        candidate.setContentMarkdown("## Candidate notes");
        candidate.setContentFormatVersion(2);
        candidate.setContentJson(core.deepCopy().put("markdown", "## Candidate notes").toString());
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_current"), anyString(),
                eq("ready"), eq("basic"), eq(1))).thenReturn(1);

        service.resolveConflict(7L, "card_1", "rev_candidate",
                new ResolveVocabularyConflictRequest("use_ai", null));

        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisions).insertRevision(revision.capture());
        assertEquals("record", objectMapper.readTree(revision.getValue().getCoreJson()).path("term").asText());
        assertEquals("## Candidate notes", revision.getValue().getContentMarkdown());
        assertEquals("theme_user_1", revision.getValue().getThemeUid());
        assertEquals(3, revision.getValue().getThemeVersion());
        assertEquals(2, revision.getValue().getContentFormatVersion());
    }

    @Test
    void conflictResolutionRejectsAnOlderNonCurrentCandidate() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_current");
        card.setStatus("needs_review");
        var current = VocabularyTestFixtures.userRevision("rev_current");
        var latestCandidate = VocabularyTestFixtures.userRevision("rev_candidate_latest");
        latestCandidate.setAuthorType("ai");
        var staleCandidate = VocabularyTestFixtures.userRevision("rev_candidate_old");
        staleCandidate.setAuthorType("ai");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate_old")).thenReturn(staleCandidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(latestCandidate, staleCandidate, current));

        assertThrows(BizException.class, () -> service.resolveConflict(
                7L,
                "card_1",
                "rev_candidate_old",
                new ResolveVocabularyConflictRequest("keep_current", null)));

        verify(revisions, never()).insertRevision(any());
        verify(cards, never()).updateActiveRevision(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(Integer.class));
    }

    private com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision newFormatConflictCurrent(
            ObjectNode core,
            String markdown) {
        var current = VocabularyTestFixtures.userRevision("rev_current");
        current.setThemeUid("theme_user_1");
        current.setThemeVersion(3);
        current.setCoreJson(core.toString());
        current.setContentMarkdown(markdown);
        current.setContentFormatVersion(2);
        current.setContentJson(core.deepCopy().put("markdown", markdown).toString());
        return current;
    }

    private void arrangeNewFormatConflict(
            com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision current) {
        arrangeNewFormatConflict(current, true);
    }

    private void arrangeNewFormatConflict(
            com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision current,
            boolean expectActivation) {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "record", "rev_current");
        card.setStatus("needs_review");
        var candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setAuthorType("ai");
        candidate.setBaseRevisionUid("rev_current");
        when(cards.findOwnedByUid(7L, "card_1")).thenReturn(card);
        when(revisions.findRevision("rev_current")).thenReturn(current);
        when(revisions.findRevision("rev_candidate")).thenReturn(candidate);
        when(revisions.listRevisions("card_1")).thenReturn(List.of(candidate, current));
        if (expectActivation) {
            when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_current"), anyString(),
                    eq("ready"), eq("basic"), eq(1))).thenReturn(1);
        }
    }

    private com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision capturedInsertedRevision() {
        org.mockito.ArgumentCaptor<com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision> revision =
                org.mockito.ArgumentCaptor.forClass(
                        com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision.class);
        verify(revisions).insertRevision(revision.capture());
        return revision.getValue();
    }

    private void assertFrozenNewFormatResolution(
            com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision revision) {
        assertEquals("system_merge", revision.getAuthorType());
        assertEquals("rev_current", revision.getBaseRevisionUid());
        assertEquals("theme_user_1", revision.getThemeUid());
        assertEquals(3, revision.getThemeVersion());
        assertEquals(2, revision.getContentFormatVersion());
    }
}
