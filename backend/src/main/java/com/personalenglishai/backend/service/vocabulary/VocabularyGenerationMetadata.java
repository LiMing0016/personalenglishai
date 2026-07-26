package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VocabularyGenerationMetadata(
        @JsonProperty("provider") String provider,
        @JsonProperty("model") String model,
        @JsonProperty("promptVersion") String promptVersion,
        @JsonProperty("modelCallCount") int modelCallCount,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("usage") Usage usage) {

    public VocabularyGenerationMetadata {
        requireNonBlank(provider, "generation.provider", 100);
        requireNonBlank(model, "generation.model", 200);
        requireNonBlank(promptVersion, "generation.promptVersion", 200);
        if (modelCallCount < 1 || modelCallCount > 2) {
            throw invalid("generation.modelCallCount is invalid");
        }
        requireOpaqueId(traceId, "generation.traceId");
    }

    public VocabularyGenerationMetadata(
            String provider,
            String model,
            String promptVersion,
            int modelCallCount,
            String traceId) {
        this(provider, model, promptVersion, modelCallCount, traceId, null);
    }

    static VocabularyGenerationMetadata fromJson(JsonNode node) {
        requireMetadataFields(node);
        JsonNode callCount = required(node, "modelCallCount");
        if (!callCount.isInt()) {
            throw invalid("generation.modelCallCount is invalid");
        }
        JsonNode usage = node.get("usage");
        return new VocabularyGenerationMetadata(
                text(required(node, "provider"), "generation.provider", 100),
                text(required(node, "model"), "generation.model", 200),
                text(required(node, "promptVersion"), "generation.promptVersion", 200),
                callCount.intValue(),
                text(required(node, "traceId"), "generation.traceId", 128),
                usage == null || usage.isNull() ? null : Usage.fromJson(usage));
    }

    private static void requireMetadataFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("generation must be an object");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        Set<String> base = Set.of("provider", "model", "promptVersion", "modelCallCount", "traceId");
        Set<String> withUsage = new HashSet<>(base);
        withUsage.add("usage");
        if (!actual.equals(base) && !actual.equals(withUsage)) {
            throw invalid("generation has an invalid field set");
        }
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid("generation must be an object");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("generation has an invalid field set");
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalid("missing field: " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field, int maxLength) {
        if (!node.isTextual() || node.textValue().length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return node.textValue();
    }

    private static void requireNonBlank(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
    }

    private static void requireOpaqueId(String value, String field) {
        requireNonBlank(value, field, 128);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw invalid(field + " is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Usage(
            @JsonProperty("inputTokens") Long inputTokens,
            @JsonProperty("cachedInputTokens") Long cachedInputTokens,
            @JsonProperty("outputTokens") Long outputTokens,
            @JsonProperty("totalTokens") Long totalTokens,
            @JsonProperty("requests") Integer requests) {

        public Usage {
            inputTokens = nonNegative(inputTokens, "generation.usage.inputTokens");
            cachedInputTokens = nonNegative(cachedInputTokens, "generation.usage.cachedInputTokens");
            outputTokens = nonNegative(outputTokens, "generation.usage.outputTokens");
            totalTokens = nonNegative(totalTokens, "generation.usage.totalTokens");
            if (requests == null || requests < 0) {
                throw invalid("generation.usage.requests is invalid");
            }
        }

        private static Usage fromJson(JsonNode node) {
            requireExactFields(node, Set.of(
                    "inputTokens",
                    "cachedInputTokens",
                    "outputTokens",
                    "totalTokens",
                    "requests"));
            return new Usage(
                    number(required(node, "inputTokens"), "generation.usage.inputTokens"),
                    number(required(node, "cachedInputTokens"), "generation.usage.cachedInputTokens"),
                    number(required(node, "outputTokens"), "generation.usage.outputTokens"),
                    number(required(node, "totalTokens"), "generation.usage.totalTokens"),
                    integer(required(node, "requests"), "generation.usage.requests"));
        }

        private static Long number(JsonNode node, String field) {
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw invalid(field + " is invalid");
            }
            return node.longValue();
        }

        private static Integer integer(JsonNode node, String field) {
            if (!node.isInt()) {
                throw invalid(field + " is invalid");
            }
            return node.intValue();
        }

        private static Long nonNegative(Long value, String field) {
            if (value == null || value < 0L) {
                throw invalid(field + " is invalid");
            }
            return value;
        }
    }
}
