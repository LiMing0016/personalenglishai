package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class PythonVocabularyGenerationProvider implements VocabularyGenerationProvider {

    private static final int MAX_MARKDOWN_CHARS = 20_000;

    private final VocabularyGenerationPythonClient client;
    private final VocabularyCoreContentCodec coreCodec;
    private final ObjectMapper objectMapper;
    private final int timeoutBudgetMs;

    @Autowired
    public PythonVocabularyGenerationProvider(
            VocabularyGenerationPythonClient client,
            VocabularyCoreContentCodec coreCodec,
            ObjectMapper objectMapper,
            @Value("${vocabulary.generation.python.timeout-ms:60000}") long timeoutMs) {
        this(client, coreCodec, objectMapper, Duration.ofMillis(timeoutMs));
    }

    PythonVocabularyGenerationProvider(
            VocabularyGenerationPythonClient client,
            VocabularyCoreContentCodec coreCodec,
            ObjectMapper objectMapper,
            Duration timeoutBudget) {
        if (client == null || coreCodec == null || objectMapper == null) {
            throw new IllegalArgumentException("Python vocabulary generation dependencies are required");
        }
        if (timeoutBudget == null || timeoutBudget.isZero() || timeoutBudget.isNegative()
                || timeoutBudget.toMillis() > VocabularyGenerationPythonRequest.MAX_TIMEOUT_BUDGET_MS) {
            throw new IllegalArgumentException("Python vocabulary generation timeout budget is invalid");
        }
        this.client = client;
        this.coreCodec = coreCodec;
        this.objectMapper = objectMapper;
        this.timeoutBudgetMs = Math.toIntExact(timeoutBudget.toMillis());
    }

    @Override
    public String key() {
        return "python";
    }

    @Override
    public GeneratedVocabularyCard generate(VocabularyGenerationInput input) {
        VocabularyGenerationPythonRequest request = requestFor(input);
        VocabularyGenerationPythonResponse response = client.generate(request);
        try {
            ObjectNode core = responseCore(response);
            coreCodec.validate(input.term(), core);
            if (!coreCodec.isComplete(input.term(), core)) {
                throw invalidProviderResult();
            }
            validateResponse(response, request);
            return new GeneratedVocabularyCard(
                    core,
                    response.contentMarkdown(),
                    response.contentFormatVersion(),
                    response.generation().model(),
                    changeSummary(response, input.theme()),
                    "partial".equals(response.outcome()),
                    response.outcome(),
                    response.warning(),
                    response.generation());
        } catch (VocabularyGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidProviderResult();
        }
    }

    private VocabularyGenerationPythonRequest requestFor(VocabularyGenerationInput input) {
        try {
            String traceId = safeOpaqueId(input.traceId(), "trace");
            return new VocabularyGenerationPythonRequest(
                    "request_" + traceId,
                    traceId,
                    Math.min(timeoutBudgetMs, input.timeoutBudgetMs()),
                    input.term(),
                    VocabularyGenerationPythonRequest.Core.fromJson(input.dictionaryCore()),
                    input.sourceContext(),
                    new VocabularyGenerationPythonRequest.Theme(
                            input.theme().themeUid(),
                            input.theme().version(),
                            input.theme().name(),
                            input.theme().purpose(),
                            input.theme().promptStrategyKey(),
                            input.theme().contentFormatVersion()));
        } catch (RuntimeException exception) {
            throw new VocabularyGenerationException(
                    "PYTHON_GENERATION_INVALID_REQUEST", false,
                    "Python generation request is invalid");
        }
    }

    private ObjectNode responseCore(VocabularyGenerationPythonResponse response) {
        JsonNode value = objectMapper.valueToTree(response.core());
        if (value == null || !value.isObject()) {
            throw invalidProviderResult();
        }
        return (ObjectNode) value;
    }

    private void validateResponse(
            VocabularyGenerationPythonResponse response, VocabularyGenerationPythonRequest request) {
        if (response == null
                || response.generation() == null
                || !request.term().equals(response.core().term())
                || !request.traceId().equals(response.generation().traceId())) {
            throw invalidProviderResult();
        }
        if ("complete".equals(response.outcome())) {
            if (response.warning() != null || !validCompleteMarkdown(response.contentMarkdown())) {
                throw invalidProviderResult();
            }
            return;
        }
        if (!"partial".equals(response.outcome())
                || !"markdown_unavailable".equals(response.warning())
                || response.contentMarkdown() == null
                || !response.contentMarkdown().isEmpty()) {
            throw invalidProviderResult();
        }
    }

    private boolean validCompleteMarkdown(String markdown) {
        return markdown != null
                && !markdown.isBlank()
                && markdown.length() <= MAX_MARKDOWN_CHARS
                && !VocabularyMarkdownValidator.containsRawHtml(markdown);
    }

    private String changeSummary(VocabularyGenerationPythonResponse response, ResolvedVocabularyTheme theme) {
        return "partial".equals(response.outcome())
                ? "Generated validated core; Markdown unavailable"
                : "Python generated with " + theme.name();
    }

    private String safeOpaqueId(String value, String fallback) {
        String candidate = value == null ? "" : value.replaceAll("[^A-Za-z0-9._:-]", "_");
        if (candidate.isBlank()) {
            return fallback;
        }
        if (!Character.isLetterOrDigit(candidate.charAt(0))) {
            candidate = fallback + "_" + candidate;
        }
        return candidate.length() <= 100 ? candidate : candidate.substring(0, 100);
    }

    private VocabularyGenerationException invalidProviderResult() {
        return new VocabularyGenerationException(
                "INVALID_PROVIDER_RESULT", false,
                "Vocabulary generation provider returned invalid content");
    }
}
