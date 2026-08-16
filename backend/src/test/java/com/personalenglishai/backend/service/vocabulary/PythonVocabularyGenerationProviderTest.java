package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PythonVocabularyGenerationProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private VocabularyGenerationPythonClient client;
    private PythonVocabularyGenerationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PythonVocabularyGenerationProvider(
                client,
                new VocabularyCoreContentCodec(objectMapper),
                new VocabularyCardBlocksCodec(),
                objectMapper,
                Duration.ofSeconds(45));
    }

    @Test
    void mapsCoreTwoAndBlocksOneContractAndReturnsTypedContent() {
        VocabularyGenerationInput input = input("job_123:attempt_1");
        VocabularyGenerationMetadata metadata = metadata("job_123_attempt_1");
        when(client.generate(any())).thenReturn(complete("record", metadata));

        GeneratedVocabularyCard generated = provider.generate(input);

        ArgumentCaptor<VocabularyGenerationPythonRequest> request =
                ArgumentCaptor.forClass(VocabularyGenerationPythonRequest.class);
        verify(client).generate(request.capture());
        assertEquals(2, request.getValue().contractVersion());
        assertEquals(2, request.getValue().coreSchemaVersion());
        assertEquals(1, request.getValue().cardBlocksSchemaVersion());
        assertEquals("record", request.getValue().dictionaryCore().term());
        assertEquals("sense_1", request.getValue().dictionaryCore().senses().get(0).id());
        assertEquals("meaning_1_1", request.getValue().dictionaryCore().senses().get(0).meanings().get(0).id());
        assertEquals("exam-blocks-v1", request.getValue().theme().promptStrategyKey());
        assertFalse(generated.partial());
        assertEquals(1, generated.cardBlocksSchemaVersion());
        assertEquals("exampleList", generated.cardBlocks().path("blocks").get(0).path("type").asText());
        assertEquals(null, generated.markdown());
        assertEquals(metadata, generated.generationMetadata());
    }

    @Test
    void preservesPythonUsageMetadataForTheWorker() {
        VocabularyGenerationMetadata metadata = new VocabularyGenerationMetadata(
                "python",
                "python-model",
                "vocabulary-card-blocks-v1",
                2,
                "job_usage_attempt_1",
                new VocabularyGenerationMetadata.Usage(40L, 10L, 20L, 60L, 2));
        when(client.generate(any())).thenReturn(complete("record", metadata));

        GeneratedVocabularyCard generated = provider.generate(input("job_usage_attempt_1"));

        assertEquals(60L, generated.generationMetadata().usage().totalTokens());
        assertEquals(2, generated.generationMetadata().usage().requests());
    }

    @Test
    void convertsPartialResponseWithoutInventingBlocks() {
        VocabularyGenerationMetadata metadata = metadata("job_partial");
        when(client.generate(any())).thenReturn(partial("record", metadata));

        GeneratedVocabularyCard generated = provider.generate(input("job_partial"));

        assertTrue(generated.partial());
        assertEquals("card_blocks_unavailable", generated.warning());
        assertTrue(generated.cardBlocks().path("blocks").isEmpty());
    }

    @Test
    void rejectsChangedTermAndDanglingMeaningReferences() {
        when(client.generate(any())).thenReturn(complete("different", metadata("job_term")));
        VocabularyGenerationException changedTerm = assertThrows(
                VocabularyGenerationException.class,
                () -> provider.generate(input("job_term")));
        assertEquals("INVALID_PROVIDER_RESULT", changedTerm.code());

        VocabularyGenerationPythonResponse dangling = complete("record", metadata("job_dangling"));
        ((ObjectNode) dangling.cardBlocks().path("blocks").get(0))
                .putArray("meaningRefs")
                .add("meaning_missing");
        when(client.generate(any())).thenReturn(dangling);
        VocabularyGenerationException invalidRef = assertThrows(
                VocabularyGenerationException.class,
                () -> provider.generate(input("job_dangling")));
        assertEquals("INVALID_PROVIDER_RESULT", invalidRef.code());
    }

    @Test
    void rejectsStructurallyValidButIncompleteCore() {
        when(client.generate(any())).thenReturn(new VocabularyGenerationPythonResponse(
                2,
                2,
                1,
                new VocabularyGenerationPythonRequest.Core("record", List.of(), List.of()),
                blocks(false),
                "complete",
                null,
                metadata("job_incomplete")));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> provider.generate(input("job_incomplete")));
        assertEquals("INVALID_PROVIDER_RESULT", exception.code());
        assertFalse(exception.retryable());
    }

    @Test
    void propagatesTypedClientFailuresWithoutFallback() {
        VocabularyGenerationException expected = new VocabularyGenerationException(
                "PYTHON_GENERATION_TIMEOUT", true, "Python generation request timed out");
        when(client.generate(any())).thenThrow(expected);

        VocabularyGenerationException actual = assertThrows(
                VocabularyGenerationException.class,
                () -> provider.generate(input("job_failure")));
        assertSame(expected, actual);
    }

    @Test
    void capsConfiguredTimeoutByRemainingBudget() {
        VocabularyGenerationInput input = input("job_budget", 1_234);
        when(client.generate(any())).thenReturn(complete("record", metadata("job_budget")));

        provider.generate(input);

        ArgumentCaptor<VocabularyGenerationPythonRequest> request =
                ArgumentCaptor.forClass(VocabularyGenerationPythonRequest.class);
        verify(client).generate(request.capture());
        assertEquals(1_234, request.getValue().timeoutBudgetMs());
    }

    private VocabularyGenerationInput input(String traceId) {
        return input(traceId, 45_000);
    }

    private VocabularyGenerationInput input(String traceId, int timeoutBudgetMs) {
        return new VocabularyGenerationInput(
                "record",
                legacyCore(),
                "The record was complete.",
                new ResolvedVocabularyTheme(
                        "theme_exam_3", 3, "Exam", "Exam preparation", "exam-markdown-v1", 1, "exam"),
                traceId,
                timeoutBudgetMs);
    }

    private ObjectNode legacyCore() {
        ObjectNode core = objectMapper.createObjectNode();
        core.put("schemaVersion", 1);
        core.put("term", "record");
        core.putArray("phonetics").addObject()
                .put("region", "uk")
                .put("text", "rekord")
                .putNull("audioUrl");
        core.putArray("senses").addObject()
                .put("partOfSpeech", "noun")
                .putArray("meanings").addObject()
                .put("definitionEn", "a written account")
                .put("definitionZh", "记录");
        return core;
    }

    private VocabularyGenerationPythonResponse complete(
            String term,
            VocabularyGenerationMetadata metadata) {
        return new VocabularyGenerationPythonResponse(
                2,
                2,
                1,
                core(term),
                blocks(false),
                "complete",
                null,
                metadata);
    }

    private VocabularyGenerationPythonResponse partial(
            String term,
            VocabularyGenerationMetadata metadata) {
        return new VocabularyGenerationPythonResponse(
                2,
                2,
                1,
                core(term),
                blocks(true),
                "partial",
                "card_blocks_unavailable",
                metadata);
    }

    private VocabularyGenerationPythonRequest.Core core(String term) {
        return new VocabularyGenerationPythonRequest.Core(
                term,
                List.of(new VocabularyGenerationPythonRequest.Phonetic("uk", "rekord", null)),
                List.of(new VocabularyGenerationPythonRequest.Sense(
                        "sense_1",
                        "noun",
                        List.of(new VocabularyGenerationPythonRequest.Meaning(
                                "meaning_1_1", "a written account", "记录")))));
    }

    private ObjectNode blocks(boolean empty) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        if (empty) {
            root.putArray("blocks");
            return root;
        }
        ObjectNode block = root.putArray("blocks").addObject();
        block.put("id", "block_examples_01");
        block.put("type", "exampleList");
        block.put("title", "常用例句");
        block.putArray("meaningRefs").add("meaning_1_1");
        block.put("format", "structured");
        block.putObject("content").putArray("items").addObject()
                .put("sentence", "The record was complete.")
                .put("translation", "记录很完整。");
        block.put("source", "ai");
        block.putNull("sourceRef");
        block.put("sortOrder", 10);
        block.put("userEdited", false);
        block.put("locked", false);
        return root;
    }

    private VocabularyGenerationMetadata metadata(String traceId) {
        return new VocabularyGenerationMetadata(
                "python", "python-model", "vocabulary-card-blocks-v1", 2, traceId);
    }
}
