package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record VocabularyImportAnalysisPythonResponse(
        int contractVersion,
        String traceId,
        String inputFingerprint,
        String rawText,
        List<String> warnings,
        List<Item> items,
        Generation generation) {

    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "contractVersion", "traceId", "inputFingerprint", "rawText", "warnings", "items", "generation");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "itemId", "observedText", "normalizedTerm", "status", "suggestions", "contextText", "confidence", "evidence");
    private static final Set<String> GENERATION_FIELDS = Set.of(
            "provider", "model", "promptVersion", "modelCallCount", "traceId", "usage");
    private static final Set<String> USAGE_FIELDS = Set.of("inputTokens", "outputTokens");

    public VocabularyImportAnalysisPythonResponse {
        if (contractVersion != 1 || !validOpaqueId(traceId) || !validFingerprint(inputFingerprint)
                || rawText == null || rawText.length() > 20_000 || warnings == null || warnings.size() > 1
                || items == null || items.size() > 30 || generation == null
                || !traceId.equals(generation.traceId())) {
            throw invalid();
        }
        warnings = List.copyOf(warnings);
        items = List.copyOf(items);
        if (warnings.stream().anyMatch(warning -> !"CANDIDATE_LIMIT_REACHED".equals(warning))) {
            throw invalid();
        }
    }

    static VocabularyImportAnalysisPythonResponse fromJson(JsonNode node) {
        requireExactFields(node, RESPONSE_FIELDS);
        return new VocabularyImportAnalysisPythonResponse(
                integer(required(node, "contractVersion")),
                opaqueId(required(node, "traceId")),
                fingerprint(required(node, "inputFingerprint")),
                text(required(node, "rawText"), 20_000, true),
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
            double confidence,
            String evidence) {

        public Item {
            if (!validOpaqueId(itemId) || !validText(observedText, 200, false)
                    || !validText(normalizedTerm, 200, false)
                    || !Set.of("accepted", "suspected_typo").contains(status)
                    || suggestions == null || suggestions.size() > 3
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1
                    || (contextText != null && !validText(contextText, 2_000, true))
                    || !Set.of("text", "image", "text_image").contains(evidence)) {
                throw invalid();
            }
            suggestions = List.copyOf(suggestions);
            if (suggestions.stream().anyMatch(value -> !validText(value, 200, false))) {
                throw invalid();
            }
            if (("accepted".equals(status) && !suggestions.isEmpty())
                    || ("suspected_typo".equals(status) && suggestions.isEmpty())) {
                throw invalid();
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
                    || !"vocabulary-import-analysis-v1".equals(promptVersion)
                    || modelCallCount < 1 || modelCallCount > 2 || !validOpaqueId(traceId)) {
                throw invalid();
            }
        }
    }

    public record Usage(Integer inputTokens, Integer outputTokens) {
        public Usage {
            if (inputTokens == null || outputTokens == null || inputTokens < 0 || outputTokens < 0) {
                throw invalid();
            }
        }
    }

    private static List<String> warnings(JsonNode node) {
        if (!node.isArray() || node.size() > 1) {
            throw invalid();
        }
        ArrayList<String> result = new ArrayList<>();
        node.forEach(value -> result.add(text(value, 100, false)));
        return result;
    }

    private static List<Item> items(JsonNode node) {
        if (!node.isArray() || node.size() > 30) {
            throw invalid();
        }
        ArrayList<Item> result = new ArrayList<>();
        for (JsonNode item : node) {
            requireExactFields(item, ITEM_FIELDS);
            JsonNode context = required(item, "contextText");
            result.add(new Item(
                    opaqueId(required(item, "itemId")),
                    text(required(item, "observedText"), 200, false),
                    text(required(item, "normalizedTerm"), 200, false),
                    text(required(item, "status"), 20, false),
                    suggestions(required(item, "suggestions")),
                    context.isNull() ? null : text(context, 2_000, true),
                    confidence(required(item, "confidence")),
                    text(required(item, "evidence"), 20, false)));
        }
        return result;
    }

    private static List<String> suggestions(JsonNode node) {
        if (!node.isArray() || node.size() > 3) {
            throw invalid();
        }
        ArrayList<String> result = new ArrayList<>();
        node.forEach(value -> result.add(text(value, 200, false)));
        return result;
    }

    private static Generation generation(JsonNode node) {
        requireExactFields(node, GENERATION_FIELDS);
        JsonNode usage = required(node, "usage");
        return new Generation(
                text(required(node, "provider"), 100, false),
                text(required(node, "model"), 200, false),
                text(required(node, "promptVersion"), 100, false),
                integer(required(node, "modelCallCount")),
                opaqueId(required(node, "traceId")),
                usage.isNull() ? null : usage(usage));
    }

    private static Usage usage(JsonNode node) {
        requireExactFields(node, USAGE_FIELDS);
        return new Usage(integer(required(node, "inputTokens")), integer(required(node, "outputTokens")));
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid();
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static int integer(JsonNode node) {
        if (!node.isInt()) {
            throw invalid();
        }
        return node.intValue();
    }

    private static double confidence(JsonNode node) {
        if (!node.isNumber()) {
            throw invalid();
        }
        return node.doubleValue();
    }

    private static String opaqueId(JsonNode node) {
        String value = text(node, 128, false);
        if (!validOpaqueId(value)) {
            throw invalid();
        }
        return value;
    }

    private static String fingerprint(JsonNode node) {
        String value = text(node, 64, false);
        if (!validFingerprint(value)) {
            throw invalid();
        }
        return value;
    }

    private static String text(JsonNode node, int maxLength, boolean allowBlank) {
        if (!node.isTextual() || !validText(node.textValue(), maxLength, allowBlank)) {
            throw invalid();
        }
        return node.textValue();
    }

    private static boolean validText(String value, int maxLength, boolean allowBlank) {
        return value != null && value.length() <= maxLength && (allowBlank || !value.isBlank());
    }

    private static boolean validOpaqueId(String value) {
        return validText(value, 128, false) && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static boolean validFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Python import analysis response is invalid");
    }
}
