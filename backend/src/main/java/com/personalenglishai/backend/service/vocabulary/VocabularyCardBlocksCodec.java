package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyCardBlocksCodec {

    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_BLOCKS = 50;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_SCALAR_LENGTH = 2_000;
    private static final int MAX_MARKDOWN_LENGTH = 20_000;
    private static final String OPAQUE_ID = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}";
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "blocks");
    private static final Set<String> BLOCK_FIELDS = Set.of(
            "id", "type", "title", "meaningRefs", "format", "content", "source",
            "sourceRef", "sortOrder", "userEdited", "locked");
    private static final Set<String> SOURCES = Set.of("ai", "user", "assistant", "legacy");
    private static final Set<String> GENERATED_SOURCES = Set.of("ai", "user", "assistant");

    public void validate(JsonNode cardBlocks, JsonNode core) {
        validate(cardBlocks, core, false);
    }

    public void validateGenerated(JsonNode cardBlocks, JsonNode core) {
        validate(cardBlocks, core, true);
    }

    private void validate(JsonNode cardBlocks, JsonNode core, boolean generatedOnly) {
        requireObject(cardBlocks, "cardBlocks");
        requireExactFields(cardBlocks, ROOT_FIELDS, "cardBlocks");
        requireVersion(required(cardBlocks, "schemaVersion"));
        JsonNode blocks = required(cardBlocks, "blocks");
        if (!blocks.isArray() || blocks.size() > MAX_BLOCKS) {
            throw invalid("cardBlocks.blocks is invalid");
        }

        Set<String> meaningIds = meaningIds(core);
        Set<String> blockIds = new HashSet<>();
        for (JsonNode block : blocks) {
            requireObject(block, "block");
            requireExactFields(block, BLOCK_FIELDS, "block");
            String id = opaqueId(required(block, "id"), "block.id");
            if (!blockIds.add(id)) {
                throw invalid("block ids must be unique");
            }
            text(required(block, "title"), "block.title", 200, true);
            validateMeaningRefs(required(block, "meaningRefs"), meaningIds);
            String source = text(required(block, "source"), "block.source", 20, true);
            if (!SOURCES.contains(source) || (generatedOnly && !GENERATED_SOURCES.contains(source))) {
                throw invalid("block.source is invalid");
            }
            JsonNode sourceRef = required(block, "sourceRef");
            if (!sourceRef.isNull()) {
                opaqueId(sourceRef, "block.sourceRef");
            }
            integer(required(block, "sortOrder"), "block.sortOrder", 0, 1_000_000);
            bool(required(block, "userEdited"), "block.userEdited");
            bool(required(block, "locked"), "block.locked");
            validateTypedContent(block);
        }
    }

    private void validateTypedContent(JsonNode block) {
        String type = text(required(block, "type"), "block.type", 40, true);
        String format = text(required(block, "format"), "block.format", 20, true);
        JsonNode content = required(block, "content");
        switch (type) {
            case "exampleList" -> {
                requireStructured(format);
                validateObjectItems(content, Set.of("sentence", "translation"), "example");
            }
            case "collocationList" -> {
                requireStructured(format);
                validateObjectItems(content, Set.of("expression", "translation"), "collocation");
            }
            case "usageBoundary" -> {
                requireStructured(format);
                requireObject(content, "usageBoundary.content");
                requireExactFields(content, Set.of("useWhen", "avoidWhen"), "usageBoundary.content");
                int useCount = validateStringArray(required(content, "useWhen"), "useWhen");
                int avoidCount = validateStringArray(required(content, "avoidWhen"), "avoidWhen");
                if (useCount + avoidCount == 0) {
                    throw invalid("usageBoundary content must not be empty");
                }
            }
            case "contrastTable" -> {
                requireStructured(format);
                validateRows(content);
            }
            case "memoryTip" -> {
                requireStructured(format);
                requireObject(content, "memoryTip.content");
                requireExactFields(content, Set.of("points"), "memoryTip.content");
                if (validateStringArray(required(content, "points"), "points") == 0) {
                    throw invalid("memoryTip points must not be empty");
                }
            }
            case "note" -> validateNote(format, content);
            default -> throw invalid("block.type is unsupported");
        }
    }

    private void validateObjectItems(
            JsonNode content,
            Set<String> itemFields,
            String itemName) {
        requireObject(content, itemName + "List.content");
        requireExactFields(content, Set.of("items"), itemName + "List.content");
        JsonNode items = required(content, "items");
        requireNonEmptyBoundedArray(items, itemName + "List.items");
        for (JsonNode item : items) {
            requireObject(item, itemName);
            requireExactFields(item, itemFields, itemName);
            for (String field : itemFields) {
                text(required(item, field), itemName + "." + field, MAX_SCALAR_LENGTH, true);
            }
        }
    }

    private void validateRows(JsonNode content) {
        requireObject(content, "contrastTable.content");
        requireExactFields(content, Set.of("rows"), "contrastTable.content");
        JsonNode rows = required(content, "rows");
        requireNonEmptyBoundedArray(rows, "contrastTable.rows");
        for (JsonNode row : rows) {
            requireObject(row, "contrastTable.row");
            Set<String> fields = Set.of("term", "focus", "typicalContext");
            requireExactFields(row, fields, "contrastTable.row");
            for (String field : fields) {
                text(required(row, field), "contrastTable." + field, MAX_SCALAR_LENGTH, true);
            }
        }
    }

    private void validateNote(String format, JsonNode content) {
        if (!"markdown".equals(format)) {
            throw invalid("note.format must be markdown");
        }
        String markdown = text(content, "note.content", MAX_MARKDOWN_LENGTH, true);
        if (VocabularyMarkdownValidator.containsRawHtml(markdown)) {
            throw invalid("note.content contains raw HTML");
        }
    }

    private void requireStructured(String format) {
        if (!"structured".equals(format)) {
            throw invalid("structured block has an invalid format");
        }
    }

    private void validateMeaningRefs(JsonNode refs, Set<String> meaningIds) {
        if (!refs.isArray() || refs.size() > 30) {
            throw invalid("block.meaningRefs is invalid");
        }
        Set<String> unique = new HashSet<>();
        for (JsonNode ref : refs) {
            String value = opaqueId(ref, "block.meaningRef");
            if (!unique.add(value) || !meaningIds.contains(value)) {
                throw invalid("block.meaningRefs contains an invalid reference");
            }
        }
    }

    private Set<String> meaningIds(JsonNode core) {
        requireObject(core, "core");
        JsonNode senses = required(core, "senses");
        if (!senses.isArray()) {
            throw invalid("core.senses is invalid");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode sense : senses) {
            JsonNode meanings = required(sense, "meanings");
            if (!meanings.isArray()) {
                throw invalid("sense.meanings is invalid");
            }
            for (JsonNode meaning : meanings) {
                JsonNode id = meaning.get("id");
                if (id != null && !ids.add(opaqueId(id, "meaning.id"))) {
                    throw invalid("meaning ids must be unique");
                }
            }
        }
        return ids;
    }

    private int validateStringArray(JsonNode value, String field) {
        if (!value.isArray() || value.size() > MAX_ITEMS) {
            throw invalid(field + " is invalid");
        }
        for (JsonNode item : value) {
            text(item, field, MAX_SCALAR_LENGTH, true);
        }
        return value.size();
    }

    private void requireNonEmptyBoundedArray(JsonNode value, String field) {
        if (!value.isArray() || value.isEmpty() || value.size() > MAX_ITEMS) {
            throw invalid(field + " is invalid");
        }
    }

    private void requireVersion(JsonNode value) {
        if (!value.isInt() || value.intValue() != SCHEMA_VERSION) {
            throw invalid("cardBlocks.schemaVersion must be 1");
        }
    }

    private String opaqueId(JsonNode value, String field) {
        String id = text(value, field, 128, true);
        if (!id.matches(OPAQUE_ID)) {
            throw invalid(field + " is invalid");
        }
        return id;
    }

    private String text(JsonNode value, String field, int maxLength, boolean nonBlank) {
        if (value == null || !value.isTextual() || value.textValue().length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        String text = value.textValue();
        if (nonBlank && text.isBlank()) {
            throw invalid(field + " is blank");
        }
        return text;
    }

    private void integer(JsonNode value, String field, int min, int max) {
        if (!value.isInt() || value.intValue() < min || value.intValue() > max) {
            throw invalid(field + " is invalid");
        }
    }

    private void bool(JsonNode value, String field) {
        if (!value.isBoolean()) {
            throw invalid(field + " is invalid");
        }
    }

    private void requireObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) {
            throw invalid(field + " must be an object");
        }
    }

    private JsonNode required(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null) {
            throw invalid("missing field: " + field);
        }
        return value;
    }

    private void requireExactFields(JsonNode value, Set<String> expected, String field) {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(field + " has an invalid field set");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
