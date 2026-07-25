package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class JavaVocabularyGenerationProvider implements VocabularyGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(JavaVocabularyGenerationProvider.class);
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final double MARKDOWN_TEMPERATURE = 0.2;
    private static final int MAX_TOKENS = 1200;
    private static final int MAX_MARKDOWN_CHARS = 20_000;
    private static final Pattern RAW_HTML = Pattern.compile(
            "(?is)<\\s*(?:!|\\?|/?\\s*[a-z])[^>]*>");

    private final OpenAiClient openAiClient;
    private final VocabularyGenerationCache cache;
    private final VocabularyCoreContentCodec coreCodec;
    private final VocabularyCoreFallbackGenerator fallbackGenerator;
    private final VocabularyMarkdownPromptBuilder promptBuilder;

    public JavaVocabularyGenerationProvider(
            OpenAiClient openAiClient,
            VocabularyGenerationCache cache,
            VocabularyCoreContentCodec coreCodec,
            VocabularyCoreFallbackGenerator fallbackGenerator,
            VocabularyMarkdownPromptBuilder promptBuilder) {
        this.openAiClient = openAiClient;
        this.cache = cache;
        this.coreCodec = coreCodec;
        this.fallbackGenerator = fallbackGenerator;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String key() {
        return "java";
    }

    @Override
    public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
        String expectedTerm = input.term();
        String sourceContext = input.sourceContext();
        ResolvedVocabularyTheme theme = input.theme();
        String traceId = input.traceId();
        ObjectNode core = input.dictionaryCore();
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
                    traceId, exception.getClass().getSimpleName());
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
                    traceId, valueOrEmpty(theme.themeUid()));
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
                    traceId, exception.getClass().getSimpleName());
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
