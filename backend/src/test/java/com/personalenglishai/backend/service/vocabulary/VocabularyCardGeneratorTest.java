package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyCardGeneratorTest {

    @Mock
    private DictionaryLookupService dictionary;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyCoreContentCodec codec;

    @BeforeEach
    void setUp() {
        codec = new VocabularyCoreContentCodec(objectMapper);
    }

    @Test
    void looksUpDictionaryOnceCapturesFirstContextAndPassesDeepCopiedTrustedCore() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        RecordingProvider provider = new RecordingProvider("java") {
            @Override
            public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
                calls++;
                observedInput = input;
                ObjectNode mutableCopy = input.dictionaryCore();
                ((ObjectNode) mutableCopy.path("senses").get(0).path("meanings").get(0))
                        .put("definitionEn", "provider mutation");
                return card(input.dictionaryCore(), input.theme());
            }
        };

        GeneratedVocabularyCard result = generator("java", provider).generate(
                VocabularyTestFixtures.generating("record"),
                Arrays.asList(null, VocabularyTestFixtures.manualSource("  "),
                        VocabularyTestFixtures.manualSource("first context"),
                        VocabularyTestFixtures.manualSource("later context")),
                theme(), "trace id/unsafe");

        assertEquals("first context", provider.observedInput.sourceContext());
        assertEquals("trace_id_unsafe", provider.observedInput.traceId());
        assertEquals("a written account", result.core().path("senses").get(0)
                .path("meanings").get(0).path("definitionEn").asText());
        verify(dictionary, times(1)).lookup("record", "en-gb");
    }

    @Test
    void invokesExactlyTheConfiguredProvider() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        RecordingProvider javaProvider = new RecordingProvider("java");
        RecordingProvider selectedProvider = new RecordingProvider("selected");

        GeneratedVocabularyCard result = generator("selected", javaProvider, selectedProvider).generate(
                VocabularyTestFixtures.generating("record"), List.of(), theme(), "trace-selected");

        assertFalse(result.partial());
        assertEquals(0, javaProvider.calls);
        assertEquals(1, selectedProvider.calls);
    }

    @Test
    void rejectsUnknownProviderWithoutCallingAnyProvider() {
        RecordingProvider javaProvider = new RecordingProvider("java");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> generator("python", javaProvider));

        assertTrue(exception.getMessage().contains("python"));
        assertEquals(0, javaProvider.calls);
    }

    @Test
    void rejectsDuplicateProviderKeys() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> generator("java", new RecordingProvider("java"), new RecordingProvider("java")));

        assertTrue(exception.getMessage().contains("Duplicate"));
    }

    @Test
    void rejectsInvalidProviderResultAfterGeneration() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        RecordingProvider provider = new RecordingProvider("java") {
            @Override
            public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
                calls++;
                ObjectNode invalidCore = input.dictionaryCore();
                invalidCore.put("term", "replacement");
                return card(invalidCore, input.theme());
            }
        };

        VocabularyGenerationException exception = assertThrows(VocabularyGenerationException.class,
                () -> generator("java", provider).generate(
                        VocabularyTestFixtures.generating("record"), List.of(), theme(), "trace-invalid"));

        assertEquals("INVALID_PROVIDER_RESULT", exception.code());
        assertFalse(exception.retryable());
        assertEquals(1, provider.calls);
    }

    @Test
    void rejectsProviderMarkdownOutsideTheValidatedBoundary() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        RecordingProvider provider = new RecordingProvider("java") {
            @Override
            public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
                calls++;
                return new GeneratedVocabularyCard(
                        input.dictionaryCore(), "<section>unsafe</section>", input.theme().contentFormatVersion(),
                        "test-model", "Generated fixture", false);
            }
        };

        VocabularyGenerationException exception = assertThrows(VocabularyGenerationException.class,
                () -> generator("java", provider).generate(
                        VocabularyTestFixtures.generating("record"), List.of(), theme(), "trace-markdown"));

        assertEquals("INVALID_PROVIDER_RESULT", exception.code());
        assertFalse(exception.retryable());
    }

    @Test
    void dictionaryFailureIsRetryableAndDoesNotCallProvider() {
        when(dictionary.lookup("record", "en-gb"))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.TIMEOUT));
        RecordingProvider provider = new RecordingProvider("java");

        VocabularyGenerationException exception = assertThrows(VocabularyGenerationException.class,
                () -> generator("java", provider).generate(
                        VocabularyTestFixtures.generating("record"), List.of(), theme(), "trace-dict"));

        assertEquals("DICTIONARY_LOOKUP_FAILED", exception.code());
        assertTrue(exception.retryable());
        assertEquals(0, provider.calls);
    }

    private VocabularyCardGenerator generator(String providerKey, VocabularyGenerationProvider... providers) {
        return new VocabularyCardGenerator(
                new VocabularyDictionaryEnricher(dictionary), codec, List.of(providers), providerKey);
    }

    private ResolvedVocabularyTheme theme() {
        return new ResolvedVocabularyTheme(
                "theme_system_basic", 1, "Theme", "", "basic-markdown-v1", 1, "basic");
    }

    private GeneratedVocabularyCard card(ObjectNode core, ResolvedVocabularyTheme theme) {
        return new GeneratedVocabularyCard(
                core, "## Usage", theme.contentFormatVersion(), "test-model", "Generated fixture", false);
    }

    private class RecordingProvider implements VocabularyGenerationProvider {
        private final String key;
        protected int calls;
        protected VocabularyGenerationInput observedInput;

        private RecordingProvider(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
            calls++;
            observedInput = input;
            return card(input.dictionaryCore(), input.theme());
        }
    }
}
