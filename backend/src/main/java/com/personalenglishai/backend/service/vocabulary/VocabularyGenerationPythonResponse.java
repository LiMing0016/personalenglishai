package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

public record VocabularyGenerationPythonResponse(
        int contractVersion,
        int coreSchemaVersion,
        VocabularyGenerationPythonRequest.Core core,
        String contentMarkdown,
        int contentFormatVersion,
        String outcome,
        String warning,
        VocabularyGenerationMetadata generation) {

    public VocabularyGenerationPythonResponse {
        if (contractVersion != VocabularyGenerationPythonRequest.VERSION
                || coreSchemaVersion != VocabularyGenerationPythonRequest.VERSION
                || contentFormatVersion != VocabularyGenerationPythonRequest.VERSION) {
            throw invalid("response versions must be 1");
        }
        if (core == null || generation == null || contentMarkdown == null || contentMarkdown.length() > 20_000
                || VocabularyMarkdownValidator.containsRawHtml(contentMarkdown)) {
            throw invalid("response content is invalid");
        }
        if ("complete".equals(outcome)) {
            if (contentMarkdown.isBlank() || warning != null) {
                throw invalid("complete response has an invalid Markdown state");
            }
        } else if ("partial".equals(outcome)) {
            if (!contentMarkdown.isEmpty() || !"markdown_unavailable".equals(warning)) {
                throw invalid("partial response has an invalid Markdown state");
            }
        } else {
            throw invalid("response outcome is invalid");
        }
    }

    static VocabularyGenerationPythonResponse fromJson(JsonNode node) {
        requireExactFields(node, Set.of(
                "contractVersion", "coreSchemaVersion", "core", "contentMarkdown", "contentFormatVersion",
                "outcome", "warning", "generation"));
        JsonNode warning = required(node, "warning");
        return new VocabularyGenerationPythonResponse(
                version(required(node, "contractVersion"), "contractVersion"),
                version(required(node, "coreSchemaVersion"), "coreSchemaVersion"),
                VocabularyGenerationPythonRequest.Core.fromJson(required(node, "core")),
                text(required(node, "contentMarkdown"), "contentMarkdown", 20_000),
                version(required(node, "contentFormatVersion"), "contentFormatVersion"),
                text(required(node, "outcome"), "outcome", 20),
                warning.isNull() ? null : text(warning, "warning", 100),
                VocabularyGenerationMetadata.fromJson(required(node, "generation")));
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid("response must be an object");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("response has an invalid field set");
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalid("missing field: " + field);
        }
        return value;
    }

    private static int version(JsonNode node, String field) {
        if (!node.isInt() || node.intValue() != VocabularyGenerationPythonRequest.VERSION) {
            throw invalid(field + " must be 1");
        }
        return node.intValue();
    }

    private static String text(JsonNode node, String field, int maxLength) {
        if (!node.isTextual() || node.textValue().length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return node.textValue();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
