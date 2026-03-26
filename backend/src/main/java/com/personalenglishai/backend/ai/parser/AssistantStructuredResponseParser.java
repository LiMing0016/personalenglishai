package com.personalenglishai.backend.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.assistant.AssistantAction;
import com.personalenglishai.backend.ai.assistant.AssistantStructuredResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AssistantStructuredResponseParser {

    private final ObjectMapper objectMapper;

    public AssistantStructuredResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssistantStructuredResponse parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(raw));
            String message = text(root, "message");
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            return new AssistantStructuredResponse(
                    message.trim(),
                    readStringArray(root.get("summary")),
                    readActions(root.get("actions"))
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid assistant structured output", e);
        }
    }

    private List<AssistantAction> readActions(JsonNode node) {
        List<AssistantAction> actions = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return actions;
        }
        for (JsonNode item : node) {
            String type = text(item, "type");
            String label = text(item, "label");
            if (type == null || type.isBlank() || label == null || label.isBlank()) {
                continue;
            }
            actions.add(new AssistantAction(
                    type.trim(),
                    label.trim(),
                    trimToNull(text(item, "text")),
                    trimToNull(text(item, "panel"))
            ));
        }
        return actions;
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private String stripCodeFences(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private String text(JsonNode root, String name) {
        JsonNode node = root == null ? null : root.get(name);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
