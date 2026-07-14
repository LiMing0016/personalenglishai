package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class VocabularyCardGenerator {

    private static final Logger log = LoggerFactory.getLogger(VocabularyCardGenerator.class);
    private static final String DICTIONARY_LANGUAGE = "en-gb";
    private static final int MAX_MARKDOWN_CHARS = 20_000;
    private static final Pattern RAW_HTML = Pattern.compile(
            "(?is)<\\s*(?:!|\\?|/?\\s*[a-z])[^>]*>");

    private final VocabularyDictionaryEnricher dictionaryEnricher;
    private final VocabularyCoreContentCodec coreCodec;
    private final VocabularyGenerationProvider provider;
    private final LongSupplier nanoTime;

    @Autowired
    public VocabularyCardGenerator(
            VocabularyDictionaryEnricher dictionaryEnricher,
            VocabularyCoreContentCodec coreCodec,
            List<VocabularyGenerationProvider> providers,
            @Value("${vocabulary.generation.provider:java}") String providerKey) {
        this(dictionaryEnricher, coreCodec, providers, providerKey, System::nanoTime);
    }

    VocabularyCardGenerator(
            VocabularyDictionaryEnricher dictionaryEnricher,
            VocabularyCoreContentCodec coreCodec,
            List<VocabularyGenerationProvider> providers,
            String providerKey,
            LongSupplier nanoTime) {
        this.dictionaryEnricher = dictionaryEnricher;
        this.coreCodec = coreCodec;
        this.provider = selectProvider(providers, providerKey);
        this.nanoTime = nanoTime;
    }

    public GeneratedVocabularyCard generate(
            VocabularyCard card,
            List<VocabularyCardSource> sources,
            ResolvedVocabularyTheme theme,
            String traceId) {
        return generate(card, sources, theme, traceId, VocabularyGenerationDeadline.fromNow(
                VocabularyGenerationPythonRequest.MAX_TIMEOUT_BUDGET_MS, 0L, nanoTime));
    }

    public GeneratedVocabularyCard generate(
            VocabularyCard card,
            List<VocabularyCardSource> sources,
            ResolvedVocabularyTheme theme,
            String traceId,
            VocabularyGenerationDeadline deadline) {
        requireInputs(card, theme);
        if (deadline == null) {
            throw new VocabularyGenerationException(
                    "INVALID_GENERATION_REQUEST", false, "Vocabulary generation deadline is missing");
        }
        String expectedTerm = valueOrEmpty(card.getDisplayTerm());
        String sourceContext = firstCapturedContext(sources);
        DictionaryLookupResponse dictionary = lookupDictionary(card, traceId);
        ObjectNode trustedCore = coreCodec.fromDictionary(expectedTerm, dictionary);
        int timeoutBudgetMs = deadline.remainingBudgetMs(
                nanoTime, VocabularyGenerationPythonRequest.MAX_TIMEOUT_BUDGET_MS);
        GeneratedVocabularyCard generated = provider.generate(new VocabularyGenerationInput(
                expectedTerm, trustedCore, sourceContext, theme, traceId, timeoutBudgetMs));
        validateProviderResult(generated, expectedTerm, trustedCore, theme);
        return new GeneratedVocabularyCard(
                generated.core().deepCopy(), generated.markdown(), generated.contentFormatVersion(),
                generated.model(), generated.changeSummary(), generated.partial(),
                generated.generationOutcome(), generated.warning(), generated.generationMetadata());
    }

    private DictionaryLookupResponse lookupDictionary(VocabularyCard card, String traceId) {
        try {
            return dictionaryEnricher.lookupWithoutUserState(card.getDisplayTerm(), DICTIONARY_LANGUAGE);
        } catch (RuntimeException exception) {
            log.warn("Vocabulary dictionary enrichment failed traceId={} reasonType={}",
                    safeTraceId(traceId), exception.getClass().getSimpleName());
            throw new VocabularyGenerationException(
                    "DICTIONARY_LOOKUP_FAILED", true, "Dictionary enrichment failed");
        }
    }

    private void validateProviderResult(
            GeneratedVocabularyCard generated,
            String expectedTerm,
            JsonNode trustedCore,
            ResolvedVocabularyTheme theme) {
        try {
            if (generated == null || generated.core() == null
                    || generated.contentFormatVersion() != theme.contentFormatVersion()) {
                throw new IllegalArgumentException("Provider result is incomplete");
            }
            coreCodec.validate(expectedTerm, generated.core());
            coreCodec.validatePreservesTrustedFields(expectedTerm, trustedCore, generated.core());
            validateProviderContent(generated);
        } catch (RuntimeException exception) {
            throw new VocabularyGenerationException(
                    "INVALID_PROVIDER_RESULT", false, "Vocabulary generation provider returned invalid content");
        }
    }

    private void validateProviderContent(GeneratedVocabularyCard generated) {
        if (generated.partial()) {
            if (!"partial".equals(generated.generationOutcome())
                    || !"markdown_unavailable".equals(generated.warning())
                    || generated.markdown() == null || !generated.markdown().isEmpty()) {
                throw new IllegalArgumentException("Provider partial result is invalid");
            }
            return;
        }
        if (!"complete".equals(generated.generationOutcome()) || generated.warning() != null) {
            throw new IllegalArgumentException("Provider complete result is invalid");
        }
        String markdown = generated.markdown();
        if (markdown == null || markdown.isBlank() || markdown.length() > MAX_MARKDOWN_CHARS
                || RAW_HTML.matcher(markdown).find()) {
            throw new IllegalArgumentException("Provider Markdown is invalid");
        }
    }

    private VocabularyGenerationProvider selectProvider(
            List<VocabularyGenerationProvider> providers, String providerKey) {
        Map<String, VocabularyGenerationProvider> providersByKey = new LinkedHashMap<>();
        if (providers != null) {
            for (VocabularyGenerationProvider candidate : providers) {
                if (candidate == null || candidate.key() == null || candidate.key().isBlank()) {
                    throw new IllegalStateException("Vocabulary generation provider is invalid");
                }
                VocabularyGenerationProvider previous = providersByKey.putIfAbsent(candidate.key(), candidate);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate vocabulary generation provider key: " + candidate.key());
                }
            }
        }
        VocabularyGenerationProvider selected = providersByKey.get(providerKey);
        if (selected == null) {
            throw new IllegalStateException("Unknown vocabulary generation provider: " + providerKey);
        }
        return selected;
    }

    private String firstCapturedContext(List<VocabularyCardSource> sources) {
        if (sources == null) {
            return "";
        }
        return sources.stream()
                .filter(source -> source != null
                        && source.getContextText() != null
                        && !source.getContextText().isBlank())
                .map(VocabularyCardSource::getContextText)
                .findFirst()
                .orElse("");
    }

    private void requireInputs(VocabularyCard card, ResolvedVocabularyTheme theme) {
        if (card == null || theme == null || card.getDisplayTerm() == null
                || card.getDisplayTerm().isBlank()) {
            throw new VocabularyGenerationException(
                    "INVALID_GENERATION_REQUEST", false,
                    "Vocabulary generation request is incomplete");
        }
    }

    private String safeTraceId(String traceId) {
        if (traceId == null) {
            return "";
        }
        String sanitized = traceId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
