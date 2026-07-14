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

    private VocabularyCoreContentCodec coreCodec;
    private PythonVocabularyGenerationProvider provider;

    @BeforeEach
    void setUp() {
        coreCodec = new VocabularyCoreContentCodec(objectMapper);
        provider = new PythonVocabularyGenerationProvider(client, coreCodec, objectMapper, Duration.ofSeconds(45));
    }

    @Test
    void mapsTheFrozenInputToTheExactTypedPythonRequestAndReturnsTypedMetadata() {
        VocabularyGenerationInput input = input("job_123:attempt_1");
        VocabularyGenerationMetadata metadata = metadata("job_123_attempt_1");
        when(client.generate(any())).thenReturn(complete(input, metadata));

        GeneratedVocabularyCard generated = provider.generate(input);

        ArgumentCaptor<VocabularyGenerationPythonRequest> request =
                ArgumentCaptor.forClass(VocabularyGenerationPythonRequest.class);
        verify(client).generate(request.capture());
        assertEquals("request_job_123_attempt_1", request.getValue().requestId());
        assertEquals("job_123_attempt_1", request.getValue().traceId());
        assertEquals(45_000, request.getValue().timeoutBudgetMs());
        assertEquals("record", request.getValue().term());
        assertEquals("record", request.getValue().dictionaryCore().term());
        assertEquals("The record was complete.", request.getValue().sourceContext());
        assertEquals("theme_exam_3", request.getValue().theme().uid());
        assertEquals(3, request.getValue().theme().version());
        assertEquals("Exam", request.getValue().theme().name());
        assertEquals("Exam preparation", request.getValue().theme().purpose());
        assertEquals("exam-markdown-v1", request.getValue().theme().promptStrategyKey());
        assertEquals(1, request.getValue().theme().contentFormatVersion());
        assertEquals("python", provider.key());
        assertFalse(generated.partial());
        assertEquals("## Exam focus", generated.markdown());
        assertEquals("python-model", generated.model());
        assertEquals(metadata, generated.generationMetadata());
    }

    @Test
    void convertsValidatedPartialPythonResponsesWithoutInventingMarkdown() {
        VocabularyGenerationInput input = input("job_partial");
        VocabularyGenerationMetadata metadata = metadata("job_partial");
        when(client.generate(any())).thenReturn(partial(input, metadata));

        GeneratedVocabularyCard generated = provider.generate(input);

        assertTrue(generated.partial());
        assertEquals("partial", generated.generationOutcome());
        assertEquals("markdown_unavailable", generated.warning());
        assertEquals("", generated.markdown());
        assertEquals(metadata, generated.generationMetadata());
    }

    @Test
    void rejectsAResponseThatChangesTheCardTermAfterClientValidation() {
        VocabularyGenerationInput input = input("job_term");
        VocabularyGenerationMetadata metadata = metadata("job_term");
        when(client.generate(any())).thenReturn(response(
                "different", "## Exam focus", "complete", null, metadata));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class, () -> provider.generate(input));

        assertEquals("INVALID_PROVIDER_RESULT", exception.code());
        assertFalse(exception.retryable());
    }

    @Test
    void createsSafeDeterministicOpaqueIdsForSanitizedInputTrace() {
        VocabularyGenerationInput input = input("/job id");
        VocabularyGenerationMetadata metadata = metadata("trace__job_id");
        when(client.generate(any())).thenReturn(complete(input, metadata));

        provider.generate(input);
        provider.generate(input);

        ArgumentCaptor<VocabularyGenerationPythonRequest> request =
                ArgumentCaptor.forClass(VocabularyGenerationPythonRequest.class);
        verify(client, org.mockito.Mockito.times(2)).generate(request.capture());
        assertEquals(request.getAllValues().get(0).requestId(), request.getAllValues().get(1).requestId());
        assertEquals(request.getAllValues().get(0).traceId(), request.getAllValues().get(1).traceId());
        assertTrue(request.getAllValues().get(0).requestId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"));
        assertTrue(request.getAllValues().get(0).traceId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"));
    }

    @Test
    void propagatesTypedPythonClientFailuresWithoutFallback() {
        VocabularyGenerationException expected = new VocabularyGenerationException(
                "PYTHON_GENERATION_TIMEOUT", true, "Python generation request timed out");
        when(client.generate(any())).thenThrow(expected);

        VocabularyGenerationException actual = assertThrows(
                VocabularyGenerationException.class, () -> provider.generate(input("job_failure")));

        assertSame(expected, actual);
    }

    private VocabularyGenerationInput input(String traceId) {
        return new VocabularyGenerationInput(
                "record", core(), "The record was complete.",
                new ResolvedVocabularyTheme(
                        "theme_exam_3", 3, "Exam", "Exam preparation", "exam-markdown-v1", 1, "exam"),
                traceId);
    }

    private ObjectNode core() {
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
                .put("definitionZh", "record");
        return core;
    }

    private VocabularyGenerationPythonResponse complete(
            VocabularyGenerationInput input, VocabularyGenerationMetadata metadata) {
        return response(input.term(), "## Exam focus", "complete", null, metadata);
    }

    private VocabularyGenerationPythonResponse partial(
            VocabularyGenerationInput input, VocabularyGenerationMetadata metadata) {
        return response(input.term(), "", "partial", "markdown_unavailable", metadata);
    }

    private VocabularyGenerationPythonResponse response(
            String term,
            String markdown,
            String outcome,
            String warning,
            VocabularyGenerationMetadata metadata) {
        return new VocabularyGenerationPythonResponse(
                1, 1,
                new VocabularyGenerationPythonRequest.Core(
                        term,
                        List.of(new VocabularyGenerationPythonRequest.Phonetic("uk", "rekord", null)),
                        List.of(new VocabularyGenerationPythonRequest.Sense(
                                "noun", List.of(new VocabularyGenerationPythonRequest.Meaning(
                                        "a written account", "record"))))),
                markdown, 1, outcome, warning, metadata);
    }

    private VocabularyGenerationMetadata metadata(String traceId) {
        return new VocabularyGenerationMetadata(
                "python", "python-model", "vocabulary-card-markdown-v1", 1, traceId);
    }
}
