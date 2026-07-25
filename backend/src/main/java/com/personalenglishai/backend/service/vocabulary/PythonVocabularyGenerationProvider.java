package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class PythonVocabularyGenerationProvider implements VocabularyGenerationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PythonVocabularyGenerationProvider.class);

    private final VocabularyGenerationPythonClient client;
    private final VocabularyCoreContentCodec coreCodec;
    private final VocabularyCardBlocksCodec cardBlocksCodec;
    private final ObjectMapper objectMapper;
    private final int timeoutBudgetMs;

    @Autowired
    public PythonVocabularyGenerationProvider(
            VocabularyGenerationPythonClient client,
            VocabularyCoreContentCodec coreCodec,
            VocabularyCardBlocksCodec cardBlocksCodec,
            ObjectMapper objectMapper,
            @Value("${vocabulary.generation.python.timeout-ms:60000}") long timeoutMs) {
        this(client, coreCodec, cardBlocksCodec, objectMapper, Duration.ofMillis(timeoutMs));
    }

    PythonVocabularyGenerationProvider(
            VocabularyGenerationPythonClient client,
            VocabularyCoreContentCodec coreCodec,
            ObjectMapper objectMapper,
            Duration timeoutBudget) {
        this(client, coreCodec, new VocabularyCardBlocksCodec(), objectMapper, timeoutBudget);
    }

    PythonVocabularyGenerationProvider(
            VocabularyGenerationPythonClient client,
            VocabularyCoreContentCodec coreCodec,
            VocabularyCardBlocksCodec cardBlocksCodec,
            ObjectMapper objectMapper,
            Duration timeoutBudget) {
        if (client == null || coreCodec == null || cardBlocksCodec == null || objectMapper == null) {
            throw new IllegalArgumentException("Python vocabulary generation dependencies are required");
        }
        if (timeoutBudget == null || timeoutBudget.isZero() || timeoutBudget.isNegative()
                || timeoutBudget.toMillis() > VocabularyGenerationPythonRequest.MAX_TIMEOUT_BUDGET_MS) {
            throw new IllegalArgumentException("Python vocabulary generation timeout budget is invalid");
        }
        this.client = client;
        this.coreCodec = coreCodec;
        this.cardBlocksCodec = cardBlocksCodec;
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
        String validationStep = "response-core";
        try {
            ObjectNode core = responseCore(response);
            validationStep = "core-schema";
            coreCodec.validate(input.term(), core);
            validationStep = "core-completeness";
            if (!coreCodec.isComplete(input.term(), core)) {
                throw invalidProviderResult();
            }
            validationStep = "card-blocks-schema";
            ObjectNode cardBlocks = responseCardBlocks(response);
            cardBlocksCodec.validateGenerated(cardBlocks, core);
            validationStep = "response-envelope";
            validateResponse(response, request);
            return new GeneratedVocabularyCard(
                    core,
                    cardBlocks,
                    response.cardBlocksSchemaVersion(),
                    null,
                    input.theme().contentFormatVersion(),
                    response.generation().model(),
                    changeSummary(response, input.theme()),
                    "partial".equals(response.outcome()),
                    response.outcome(),
                    response.warning(),
                    response.generation());
        } catch (VocabularyGenerationException exception) {
            if ("INVALID_PROVIDER_RESULT".equals(exception.code())) {
                LOG.warn(
                        "Rejected Python vocabulary generation response traceId={} step={}",
                        input.traceId(),
                        validationStep);
            }
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Rejected Python vocabulary generation response traceId={} step={} reason={}",
                    input.traceId(), validationStep,
                    exception.getMessage());
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
                            blocksStrategy(input.theme().promptStrategyKey()),
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

    private ObjectNode responseCardBlocks(VocabularyGenerationPythonResponse response) {
        JsonNode value = response.cardBlocks();
        if (value == null || !value.isObject()) {
            throw invalidProviderResult();
        }
        return ((ObjectNode) value).deepCopy();
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
            if (response.warning() != null || response.cardBlocks().path("blocks").isEmpty()) {
                throw invalidProviderResult();
            }
            return;
        }
        if (!"partial".equals(response.outcome())
                || !"card_blocks_unavailable".equals(response.warning())
                || !response.cardBlocks().path("blocks").isEmpty()) {
            throw invalidProviderResult();
        }
    }

    private String changeSummary(VocabularyGenerationPythonResponse response, ResolvedVocabularyTheme theme) {
        return "partial".equals(response.outcome())
                ? "Generated validated core; Card Blocks unavailable"
                : "Python generated with " + theme.name();
    }

    private String blocksStrategy(String strategy) {
        if (strategy == null) {
            return null;
        }
        return strategy.endsWith("-markdown-v1")
                ? strategy.substring(0, strategy.length() - "-markdown-v1".length()) + "-blocks-v1"
                : strategy;
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
