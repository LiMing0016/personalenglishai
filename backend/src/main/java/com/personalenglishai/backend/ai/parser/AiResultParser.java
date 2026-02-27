package com.personalenglishai.backend.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.dto.AiResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiResultParser {

    private final ObjectMapper objectMapper;

    public AiResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiResult parseStrict(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String apply = text(root, "apply");
            if (apply == null || apply.isBlank()) {
                throw new IllegalArgumentException("apply is required");
            }

            AiResult result = new AiResult();
            result.setApply(apply.trim());
            result.setExplain(readStringArray(root.get("explain")));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid AI JSON output", e);
        }
    }

    private static String text(JsonNode root, String name) {
        JsonNode node = root.get(name);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private static List<String> readStringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                String s = item.asText("").trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
