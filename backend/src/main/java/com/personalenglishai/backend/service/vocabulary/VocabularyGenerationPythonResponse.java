package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

public record VocabularyGenerationPythonResponse(
        int contractVersion,
        int coreSchemaVersion,
        int cardBlocksSchemaVersion,
        VocabularyGenerationPythonRequest.Core core,
        JsonNode cardBlocks,
        String outcome,
        String warning,
        VocabularyGenerationMetadata generation) {

    public VocabularyGenerationPythonResponse {
        if (contractVersion != VocabularyGenerationPythonRequest.VERSION
                || coreSchemaVersion != VocabularyGenerationPythonRequest.VERSION
                || cardBlocksSchemaVersion != VocabularyGenerationPythonRequest.CARD_BLOCKS_VERSION) {
            throw invalid("response versions are unsupported");
        }
        if (core == null || generation == null || cardBlocks == null || !cardBlocks.isObject()) {
            throw invalid("response content is invalid");
        }
        requireExactFields(cardBlocks, Set.of("schemaVersion", "blocks"));
        version(required(cardBlocks, "schemaVersion"), "cardBlocks.schemaVersion", 1);
        JsonNode blocks = required(cardBlocks, "blocks");
        if (!blocks.isArray()) {
            throw invalid("cardBlocks.blocks must be an array");
        }
        if ("complete".equals(outcome)) {
            if (blocks.isEmpty() || warning != null) {
                throw invalid("complete response has an invalid Card Blocks state");
            }
        } else if ("partial".equals(outcome)) {
            if (!blocks.isEmpty() || !"card_blocks_unavailable".equals(warning)) {
                throw invalid("partial response has an invalid Card Blocks state");
            }
        } else {
            throw invalid("response outcome is invalid");
        }
    }

    static VocabularyGenerationPythonResponse fromJson(JsonNode node) {
        requireExactFields(node, Set.of(
                "contractVersion", "coreSchemaVersion", "cardBlocksSchemaVersion", "core",
                "cardBlocks", "outcome", "warning", "generation"));
        JsonNode warning = required(node, "warning");
        return new VocabularyGenerationPythonResponse(
                version(required(node, "contractVersion"), "contractVersion", 2),
                version(required(node, "coreSchemaVersion"), "coreSchemaVersion", 2),
                version(required(node, "cardBlocksSchemaVersion"), "cardBlocksSchemaVersion", 1),
                VocabularyGenerationPythonRequest.Core.fromJson(required(node, "core")),
                required(node, "cardBlocks").deepCopy(),
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

    private static int version(JsonNode node, String field, int expected) {
        if (!node.isInt() || node.intValue() != expected) {
            throw invalid(field + " is unsupported");
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
