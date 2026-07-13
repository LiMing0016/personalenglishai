package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class VocabularyCardGenerator {

    private static final Logger log = LoggerFactory.getLogger(VocabularyCardGenerator.class);
    private static final String DICTIONARY_LANGUAGE = "en-gb";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final double MARKDOWN_TEMPERATURE = 0.2;
    private static final int MAX_TOKENS = 1200;
    private static final int MAX_MARKDOWN_CHARS = 20_000;
    private static final Pattern RAW_HTML = Pattern.compile(
            "(?is)<\\s*(?:!|\\?|/?\\s*[a-z])[^>]*>");

    private final VocabularyDictionaryEnricher dictionaryEnricher;
    private final OpenAiClient openAiClient;
    private final VocabularyGenerationCache cache;
    private final VocabularyCoreContentCodec coreCodec;
    private final VocabularyCoreFallbackGenerator fallbackGenerator;
    private final VocabularyMarkdownPromptBuilder promptBuilder;

    public VocabularyCardGenerator(
            VocabularyDictionaryEnricher dictionaryEnricher,
            OpenAiClient openAiClient,
            VocabularyGenerationCache cache,
            VocabularyCoreContentCodec coreCodec,
            VocabularyCoreFallbackGenerator fallbackGenerator,
            VocabularyMarkdownPromptBuilder promptBuilder) {
        this.dictionaryEnricher = dictionaryEnricher;
        this.openAiClient = openAiClient;
        this.cache = cache;
        this.coreCodec = coreCodec;
        this.fallbackGenerator = fallbackGenerator;
        this.promptBuilder = promptBuilder;
    }

    public GeneratedVocabularyCard generate(
            VocabularyCard card,
            List<VocabularyCardSource> sources,
            ResolvedVocabularyTheme theme,
            String traceId) {
        requireInputs(card, theme);
        String expectedTerm = valueOrEmpty(card.getDisplayTerm());
        String sourceContext = firstCapturedContext(sources);
        DictionaryLookupResponse dictionary = lookupDictionary(card, traceId);
        ObjectNode core = coreCodec.fromDictionary(expectedTerm, dictionary);
        if (!hasPhoneticOrSense(core)) {
            core = fallbackCore(expectedTerm, sourceContext, traceId);
        }
        coreCodec.validate(expectedTerm, core);

        String cacheKey = cache.key(theme.themeUid(), theme.version(), core, sourceContext);
        Optional<GeneratedVocabularyCard> cached = cachedCard(
                cacheKey, expectedTerm, core, theme, traceId);
        if (cached.isPresent()) {
            return cached.get();
        }

        String markdown = generateMarkdown(theme, core, sourceContext, traceId);
        if (markdown == null) {
            return new GeneratedVocabularyCard(
                    core, "", theme.contentFormatVersion(), valueOrEmpty(openAiClient.getModel()),
                    "Generated validated core; Markdown unavailable", true,
                    "partial", "markdown_unavailable");
        }

        cache.put(cacheKey, new VocabularyGenerationCache.CachedGeneration(core, markdown), CACHE_TTL);
        return new GeneratedVocabularyCard(
                core, markdown, theme.contentFormatVersion(), valueOrEmpty(openAiClient.getModel()),
                "AI generated with " + valueOrEmpty(theme.name()), false,
                "complete", null);
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

    private ObjectNode fallbackCore(String expectedTerm, String sourceContext, String traceId) {
        try {
            ObjectNode generated = fallbackGenerator.generate(expectedTerm, sourceContext, traceId);
            if (!hasPhoneticOrSense(generated)) {
                throw new IllegalArgumentException("Generated core contains no phonetic or sense");
            }
            coreCodec.validate(expectedTerm, generated);
            return generated;
        } catch (RuntimeException exception) {
            log.warn("Vocabulary core fallback failed traceId={} reasonType={}",
                    safeTraceId(traceId), exception.getClass().getSimpleName());
            throw new VocabularyGenerationException(
                    "CORE_CONTENT_UNAVAILABLE", true, "Vocabulary core content is unavailable");
        }
    }

    private Optional<GeneratedVocabularyCard> cachedCard(
            String cacheKey,
            String expectedTerm,
            JsonNode currentCore,
            ResolvedVocabularyTheme theme,
            String traceId) {
        Optional<VocabularyGenerationCache.CachedGeneration> cached = cache.get(cacheKey);
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            coreCodec.validate(expectedTerm, cached.get().core());
            if (!currentCore.equals(cached.get().core())) {
                throw new IllegalArgumentException("Cached core differs from current core");
            }
            validateMarkdown(cached.get().markdown());
            return Optional.of(new GeneratedVocabularyCard(
                    currentCore.deepCopy(), cached.get().markdown(),
                    theme.contentFormatVersion(), "cache", "Reused validated generated content", false));
        } catch (IllegalArgumentException exception) {
            log.warn("Vocabulary generation cache entry rejected traceId={} themeUid={}",
                    safeTraceId(traceId), valueOrEmpty(theme.themeUid()));
            cache.evict(cacheKey);
            return Optional.empty();
        }
    }

    private String generateMarkdown(
            ResolvedVocabularyTheme theme,
            JsonNode core,
            String sourceContext,
            String traceId) {
        try {
            String markdown = openAiClient.callWithTraceId(
                    promptBuilder.systemPrompt(theme),
                    promptBuilder.userPrompt(theme, core, sourceContext),
                    traceId,
                    MARKDOWN_TEMPERATURE,
                    MAX_TOKENS);
            validateMarkdown(markdown);
            return markdown.trim();
        } catch (RuntimeException exception) {
            log.warn("Vocabulary Markdown generation degraded traceId={} reasonType={}",
                    safeTraceId(traceId), exception.getClass().getSimpleName());
            return null;
        }
    }

    private void validateMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank() || markdown.length() > MAX_MARKDOWN_CHARS
                || RAW_HTML.matcher(markdown).find()) {
            throw new IllegalArgumentException("Vocabulary Markdown is invalid");
        }
    }

    private boolean hasPhoneticOrSense(JsonNode core) {
        return core != null
                && ((core.path("phonetics").isArray() && !core.path("phonetics").isEmpty())
                || (core.path("senses").isArray() && !core.path("senses").isEmpty()));
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
