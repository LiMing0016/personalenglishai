package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class VocabularyCardBlocksCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyCardBlocksCodec codec = new VocabularyCardBlocksCodec();

    @Test
    void validatesAllSupportedBlockTypesAgainstCoreMeaningIds() throws Exception {
        assertDoesNotThrow(() -> codec.validate(blocks(), core()));
    }

    @Test
    void rejectsDuplicateIdsDanglingReferencesAndUnknownTypes() throws Exception {
        ObjectNode duplicate = blocks();
        ((ObjectNode) duplicate.path("blocks").get(1)).put("id", "block_examples_01");
        assertThrows(IllegalArgumentException.class, () -> codec.validate(duplicate, core()));

        ObjectNode dangling = blocks();
        ((ObjectNode) dangling.path("blocks").get(0))
                .putArray("meaningRefs")
                .add("meaning_missing");
        assertThrows(IllegalArgumentException.class, () -> codec.validate(dangling, core()));

        ObjectNode unknown = blocks();
        ((ObjectNode) unknown.path("blocks").get(0)).put("type", "dictionaryDump");
        assertThrows(IllegalArgumentException.class, () -> codec.validate(unknown, core()));
    }

    @Test
    void rejectsWrongStructuredShapesAndRawHtmlNotes() throws Exception {
        ObjectNode wrongShape = blocks();
        ((ObjectNode) wrongShape.path("blocks").get(0))
                .set("content", objectMapper.createObjectNode().putArray("points").add("wrong"));
        assertThrows(IllegalArgumentException.class, () -> codec.validate(wrongShape, core()));

        ObjectNode rawHtml = blocks();
        ((ObjectNode) rawHtml.path("blocks").get(5)).put("content", "<script>alert('x')</script>");
        assertThrows(IllegalArgumentException.class, () -> codec.validate(rawHtml, core()));
    }

    @Test
    void allowsMarkdownLiteralsInsideCodeButRejectsLegacySourceForGeneratedContent() throws Exception {
        ObjectNode codeLiteral = blocks();
        ((ObjectNode) codeLiteral.path("blocks").get(5))
                .put("content", "Use `<script>alert('x')</script>` literally.");
        assertDoesNotThrow(() -> codec.validate(codeLiteral, core()));

        ObjectNode legacy = blocks();
        ((ObjectNode) legacy.path("blocks").get(0)).put("source", "legacy");
        assertThrows(IllegalArgumentException.class, () -> codec.validateGenerated(legacy, core()));
    }

    private ObjectNode core() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "term": "anthropic",
                  "phonetics": [],
                  "senses": [{
                    "id": "sense_adjective_01",
                    "partOfSpeech": "adjective",
                    "meanings": [{
                      "id": "meaning_human_01",
                      "definitionEn": "related to human existence or influence",
                      "definitionZh": "与人类存在或影响有关的"
                    }]
                  }]
                }
                """);
    }

    private ObjectNode blocks() throws Exception {
        JsonNode value = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "blocks": [
                    {
                      "id": "block_examples_01",
                      "type": "exampleList",
                      "title": "常用例句",
                      "meaningRefs": ["meaning_human_01"],
                      "format": "structured",
                      "content": {"items": [{"sentence": "An anthropic explanation.", "translation": "一种人择论解释。"}]},
                      "source": "ai",
                      "sourceRef": null,
                      "sortOrder": 10,
                      "userEdited": false,
                      "locked": false
                    },
                    {
                      "id": "block_collocations_01",
                      "type": "collocationList",
                      "title": "搭配表达",
                      "meaningRefs": ["meaning_human_01"],
                      "format": "structured",
                      "content": {"items": [{"expression": "anthropic principle", "translation": "人择原理"}]},
                      "source": "ai",
                      "sourceRef": null,
                      "sortOrder": 20,
                      "userEdited": false,
                      "locked": false
                    },
                    {
                      "id": "block_usage_01",
                      "type": "usageBoundary",
                      "title": "使用边界",
                      "meaningRefs": ["meaning_human_01"],
                      "format": "structured",
                      "content": {"useWhen": ["哲学或宇宙学语境"], "avoidWhen": ["指人类物种时"]},
                      "source": "ai",
                      "sourceRef": null,
                      "sortOrder": 30,
                      "userEdited": false,
                      "locked": false
                    },
                    {
                      "id": "block_contrast_01",
                      "type": "contrastTable",
                      "title": "易混辨析",
                      "meaningRefs": ["meaning_human_01"],
                      "format": "structured",
                      "content": {"rows": [{"term": "anthropic", "focus": "与人类存在有关", "typicalContext": "宇宙学"}]},
                      "source": "ai",
                      "sourceRef": null,
                      "sortOrder": 40,
                      "userEdited": false,
                      "locked": false
                    },
                    {
                      "id": "block_memory_01",
                      "type": "memoryTip",
                      "title": "记忆提示",
                      "meaningRefs": [],
                      "format": "structured",
                      "content": {"points": ["anthro- 表示人类"]},
                      "source": "ai",
                      "sourceRef": null,
                      "sortOrder": 50,
                      "userEdited": false,
                      "locked": false
                    },
                    {
                      "id": "block_note_01",
                      "type": "note",
                      "title": "我的笔记",
                      "meaningRefs": ["meaning_human_01"],
                      "format": "markdown",
                      "content": "和 **human influence** 联系记忆。",
                      "source": "user",
                      "sourceRef": null,
                      "sortOrder": 60,
                      "userEdited": true,
                      "locked": true
                    }
                  ]
                }
                """);
        return (ObjectNode) value;
    }
}
