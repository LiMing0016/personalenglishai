package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record VocabularyImageRecognitionPythonResponse(
        int contractVersion,
        String traceId,
        String rawText,
        List<String> warnings,
        List<Item> items,
        Generation generation) {

    private static final int VERSION = 1;
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "contractVersion", "traceId", "rawText", "warnings", "items", "generation");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "itemId", "observedText", "normalizedTerm", "status", "suggestions", "contextText", "confidence");
    private static final Set<String> GENERATION_FIELDS = Set.of(
            "provider", "model", "promptVersion", "modelCallCount", "traceId", "usage");
    private static final Set<String> USAGE_FIELDS = Set.of("inputTokens", "outputTokens");

    public VocabularyImageRecognitionPythonResponse {
        if (contractVersion != VERSION || !validOpaqueId(traceId) || rawText == null || rawText.length() > 20_000
                || warnings == null || warnings.size() > 1 || items == null || items.size() > 30 || generation == null
                || !traceId.equals(generation.traceId())) {
            throw invalid("response is invalid");
        }
        warnings = List.copyOf(warnings);
        items = List.copyOf(items);
        for (String warning : warnings) {
            if (!"CANDIDATE_LIMIT_REACHED".equals(warning)) {
                throw invalid("response warning is invalid");
            }
        }
    }

    static VocabularyImageRecognitionPythonResponse fromJson(JsonNode node) {
        requireExactFields(node, RESPONSE_FIELDS);
        return new VocabularyImageRecognitionPythonResponse(
                integer(required(node, "contractVersion"), "contractVersion"),
                opaqueId(required(node, "traceId"), "traceId"),
                text(required(node, "rawText"), "rawText", 20_000, true),
                warnings(required(node, "warnings")),
                items(required(node, "items")),
                generation(required(node, "generation")));
    }

    public record Item(
            String itemId,
            String observedText,
            String normalizedTerm,
            String status,
            List<String> suggestions,
            String contextText,
            double confidence) {

        public Item {
            if (!validOpaqueId(itemId) || !validText(observedText, 200, false)
                    || !validText(normalizedTerm, 200, false) || !Set.of("accepted", "suspected_typo").contains(status)
                    || suggestions == null || suggestions.size() > 3 || !Double.isFinite(confidence)
                    || confidence < 0 || confidence > 1 || (contextText != null && !validText(contextText, 2_000, true))) {
                throw invalid("response item is invalid");
            }
            suggestions = List.copyOf(suggestions);
            for (String suggestion : suggestions) {
                if (!validText(suggestion, 200, false)) {
                    throw invalid("response suggestion is invalid");
                }
            }
            if (("accepted".equals(status) && !suggestions.isEmpty())
                    || ("suspected_typo".equals(status) && suggestions.isEmpty())) {
                throw invalid("response suggestion state is invalid");
            }
        }
    }

    public record Generation(
            String provider,
            String model,
            String promptVersion,
            int modelCallCount,
            String traceId,
            Usage usage) {

        public Generation {
            if (!validText(provider, 100, false) || !validText(model, 200, false)
                    || !"vocabulary-image-recognition-v1".equals(promptVersion) || modelCallCount < 1 || modelCallCount > 2
                    || !validOpaqueId(traceId)) {
                throw invalid("response generation is invalid");
            }
        }
    }

    public record Usage(Integer inputTokens, Integer outputTokens) {

        public Usage {
            if (inputTokens == null || outputTokens == null || inputTokens < 0 || outputTokens < 0) {
                throw invalid("response usage is invalid");
            }
        }
    }

    private static List<String> warnings(JsonNode node) {
        if (!node.isArray() || node.size() > 1) {
            throw invalid("response warnings are invalid");
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (JsonNode warning : node) {
            result.add(text(warning, "warning", 100, false));
        }
        return result;
    }

    private static List<Item> items(JsonNode node) {
        if (!node.isArray() || node.size() > 30) {
            throw invalid("response items are invalid");
        }
        java.util.ArrayList<Item> result = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            requireExactFields(item, ITEM_FIELDS);
            JsonNode contextText = required(item, "contextText");
            result.add(new Item(
                    opaqueId(required(item, "itemId"), "itemId"),
                    text(required(item, "observedText"), "observedText", 200, false),
                    text(required(item, "normalizedTerm"), "normalizedTerm", 200, false),
                    text(required(item, "status"), "status", 20, false),
                    suggestions(required(item, "suggestions")),
                    contextText.isNull() ? null : text(contextText, "contextText", 2_000, true),
                    confidence(required(item, "confidence"))));
        }
        return result;
    }

    private static List<String> suggestions(JsonNode node) {
        if (!node.isArray() || node.size() > 3) {
            throw invalid("response suggestions are invalid");
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (JsonNode suggestion : node) {
            result.add(text(suggestion, "suggestion", 200, false));
        }
        return result;
    }

    private static Generation generation(JsonNode node) {
        requireExactFields(node, GENERATION_FIELDS);
        JsonNode usage = required(node, "usage");
        return new Generation(
                text(required(node, "provider"), "provider", 100, false),
                text(required(node, "model"), "model", 200, false),
                text(required(node, "promptVersion"), "promptVersion", 100, false),
                integer(required(node, "modelCallCount"), "modelCallCount"),
                opaqueId(required(node, "traceId"), "generation.traceId"),
                usage.isNull() ? null : usage(usage));
    }

    private static Usage usage(JsonNode node) {
        requireExactFields(node, USAGE_FIELDS);
        return new Usage(
                integer(required(node, "inputTokens"), "inputTokens"),
                integer(required(node, "outputTokens"), "outputTokens"));
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid("response object is invalid");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("response field set is invalid");
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalid("response field is missing");
        }
        return value;
    }

    private static int integer(JsonNode node, String field) {
        if (!node.isInt()) {
            throw invalid(field + " is invalid");
        }
        return node.intValue();
    }

    private static double confidence(JsonNode node) {
        if (!node.isNumber()) {
            throw invalid("confidence is invalid");
        }
        return node.doubleValue();
    }

    private static String opaqueId(JsonNode node, String field) {
        String value = text(node, field, 128, false);
        if (!validOpaqueId(value)) {
            throw invalid(field + " is invalid");
        }
        return value;
    }

    private static String text(JsonNode node, String field, int maxLength, boolean allowBlank) {
        if (!node.isTextual() || !validText(node.textValue(), maxLength, allowBlank)) {
            throw invalid(field + " is invalid");
        }
        return node.textValue();
    }

    private static boolean validText(String value, int maxLength, boolean allowBlank) {
        return value != null && value.length() <= maxLength && (allowBlank || !value.isBlank());
    }

    private static boolean validOpaqueId(String value) {
        return validText(value, 128, false) && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
