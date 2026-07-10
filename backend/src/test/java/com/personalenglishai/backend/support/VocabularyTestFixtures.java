package com.personalenglishai.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.service.vocabulary.GeneratedVocabularyCard;

import java.time.LocalDateTime;
import java.util.List;

public final class VocabularyTestFixtures {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Long DEFAULT_USER_ID = 7L;

    private VocabularyTestFixtures() {
    }

    public static VocabularyCard ready(
            String cardUid,
            Long userId,
            String normalizedTerm,
            String activeRevisionUid) {
        LocalDateTime now = LocalDateTime.now();
        VocabularyCard card = new VocabularyCard();
        card.setId(1L);
        card.setCardUid(cardUid);
        card.setUserId(userId);
        card.setLanguage("en");
        card.setOriginalTerm(normalizedTerm);
        card.setNormalizedTerm(normalizedTerm);
        card.setDisplayTerm(normalizedTerm);
        card.setTemplateKey("basic");
        card.setTemplateVersion(1);
        card.setStatus("ready");
        card.setActiveRevisionUid(activeRevisionUid);
        card.setLastCapturedAt(now);
        card.setDeletedAt(null);
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        return card;
    }

    public static VocabularyCard ready(String cardUid, String activeRevisionUid) {
        return ready(cardUid, DEFAULT_USER_ID, "innovative", activeRevisionUid);
    }

    public static VocabularyCard generating(String term) {
        return generatingCard("card_1", term, null);
    }

    public static VocabularyCard generating(String cardUid, String activeRevisionUid) {
        return generatingCard(cardUid, "innovative", activeRevisionUid);
    }

    public static VocabularyCardSource manualSource(String contextText) {
        LocalDateTime now = LocalDateTime.now();
        VocabularyCardSource source = new VocabularyCardSource();
        source.setId(1L);
        source.setSourceUid("src_1");
        source.setCardUid("card_1");
        source.setUserId(DEFAULT_USER_ID);
        source.setSourceType("manual");
        source.setSourceRef(null);
        source.setSourceTitle("Manual input");
        source.setSourceUrl(null);
        source.setContextText(contextText);
        source.setRawTerm("innovative");
        source.setIdempotencyKey("req-1:0");
        source.setCapturedAt(now);
        source.setMetadataJson("{}");
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        return source;
    }

    public static VocabularyGenerationJob pendingJob(
            String jobUid,
            String cardUid,
            String baseRevisionUid,
            int attemptCount) {
        LocalDateTime now = LocalDateTime.now();
        VocabularyGenerationJob job = new VocabularyGenerationJob();
        job.setId(1L);
        job.setJobUid(jobUid);
        job.setCardUid(cardUid);
        job.setBaseRevisionUid(baseRevisionUid);
        job.setTemplateKey("basic");
        job.setTemplateVersion(1);
        job.setStatus("pending");
        job.setAttemptCount(attemptCount);
        job.setRequestJson("{}");
        job.setResultRevisionUid(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setAvailableAt(now);
        job.setStartedAt(null);
        job.setLeaseToken(null);
        job.setLeaseExpiresAt(null);
        job.setFinishedAt(null);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }

    public static VocabularyCardRevision userRevision(String revisionUid) {
        VocabularyCardRevision revision = new VocabularyCardRevision();
        revision.setId(1L);
        revision.setRevisionUid(revisionUid);
        revision.setCardUid("card_1");
        revision.setBaseRevisionUid(null);
        revision.setAuthorType("user");
        revision.setTemplateKey("basic");
        revision.setTemplateVersion(1);
        revision.setContentJson("{\"term\":\"innovative\",\"definitions\":[],\"examples\":[]}");
        revision.setChangeSummary("User edit");
        revision.setCreatedAt(LocalDateTime.now());
        return revision;
    }

    public static GeneratedVocabularyCard basicGeneratedCard() {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("term", "innovative");
        content.put("phonetic", "");
        content.put("partOfSpeech", "adjective");
        content.putArray("definitions").add("introducing new ideas");
        content.putArray("examples");
        content.put("notes", "");
        return new GeneratedVocabularyCard(content, "test-model", "Generated fixture");
    }

    public static DictionaryLookupResponse dictionaryLookup(
            String word,
            String partOfSpeech,
            String definition) {
        DictionaryEntryDto entry = new DictionaryEntryDto(partOfSpeech);
        entry.setDefinitions(List.of(definition));
        entry.setExamples(List.of());

        DictionaryLookupResponse response = new DictionaryLookupResponse();
        response.setWord(word);
        response.setLanguage("en-gb");
        response.setSource("fixture");
        response.setEntries(List.of(entry));
        response.setFavorite(false);
        response.setLookupCount(0);
        return response;
    }

    private static VocabularyCard generatingCard(
            String cardUid,
            String normalizedTerm,
            String activeRevisionUid) {
        VocabularyCard card = ready(cardUid, DEFAULT_USER_ID, normalizedTerm, activeRevisionUid);
        card.setStatus("generating");
        return card;
    }
}
