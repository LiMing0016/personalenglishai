package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyCoreFallbackGenerator {

    private static final String SCHEMA_NAME = "vocabulary_core_v1";
    private static final double TEMPERATURE = 0.0;
    private static final int MAX_TOKENS = 1200;
    private static final int MAX_SCALAR_LENGTH = 2000;

    private final OpenAiClient openAiClient;
    private final VocabularyCoreContentCodec codec;
    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public VocabularyCoreFallbackGenerator(
            OpenAiClient openAiClient,
            VocabularyCoreContentCodec codec,
            ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.schema = buildSchema();
    }

    public ObjectNode generate(String expectedTerm, String sourceContext, String traceId) {
        String raw = openAiClient.callStructuredWithTraceId(
                systemPrompt(), userPrompt(expectedTerm, sourceContext), traceId,
                SCHEMA_NAME, schema, TEMPERATURE, MAX_TOKENS);
        ObjectNode core = parseObject(raw);
        core.put("term", valueOrEmpty(expectedTerm));
        codec.validate(valueOrEmpty(expectedTerm), core);
        return core;
    }

    private String systemPrompt() {
        return "Generate only the strict vocabulary core JSON requested by the schema. "
                + "Use the supplied term as the card identity. Do not add fields or Markdown.";
    }

    private String userPrompt(String term, String sourceContext) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("term", valueOrEmpty(term));
        input.put("sourceContext", valueOrEmpty(sourceContext));
        return input.toString();
    }

    private ObjectNode parseObject(String raw) {
        try {
            JsonNode parsed = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(raw == null ? "" : raw.trim());
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("Structured vocabulary core must be an object");
            }
            return (ObjectNode) parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Structured vocabulary core is invalid", exception);
        }
    }

    private JsonNode buildSchema() {
        ObjectNode root = closedObject();
        ObjectNode rootProperties = root.putObject("properties");
        rootProperties.set("schemaVersion", objectMapper.createObjectNode().put("type", "integer").put("const", 1));
        rootProperties.set("term", stringSchema());

        ObjectNode phonetic = closedObject();
        ObjectNode phoneticProperties = phonetic.putObject("properties");
        ObjectNode region = objectMapper.createObjectNode();
        region.put("type", "string");
        region.putArray("enum").add("uk").add("us").add("other");
        phoneticProperties.set("region", region);
        phoneticProperties.set("text", stringSchema());
        ObjectNode audioUrl = objectMapper.createObjectNode();
        audioUrl.putArray("type").add("string").add("null");
        audioUrl.put("maxLength", MAX_SCALAR_LENGTH);
        phoneticProperties.set("audioUrl", audioUrl);
        require(phonetic, "region", "text", "audioUrl");
        rootProperties.set("phonetics", arraySchema(phonetic, 10));

        ObjectNode meaning = closedObject();
        ObjectNode meaningProperties = meaning.putObject("properties");
        meaningProperties.set("definitionEn", stringSchema());
        meaningProperties.set("definitionZh", stringSchema());
        require(meaning, "definitionEn", "definitionZh");

        ObjectNode sense = closedObject();
        ObjectNode senseProperties = sense.putObject("properties");
        senseProperties.set("partOfSpeech", stringSchema());
        senseProperties.set("meanings", arraySchema(meaning, 30));
        require(sense, "partOfSpeech", "meanings");
        rootProperties.set("senses", arraySchema(sense, 20));
        require(root, "schemaVersion", "term", "phonetics", "senses");
        return root;
    }

    private ObjectNode closedObject() {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("type", "object");
        value.put("additionalProperties", false);
        return value;
    }

    private ObjectNode stringSchema() {
        return objectMapper.createObjectNode().put("type", "string").put("maxLength", MAX_SCALAR_LENGTH);
    }

    private ObjectNode arraySchema(JsonNode items, int maxItems) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("type", "array");
        value.put("maxItems", maxItems);
        value.set("items", items);
        return value;
    }

    private void require(ObjectNode object, String... fields) {
        ArrayNode required = object.putArray("required");
        for (String field : fields) {
            required.add(field);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
