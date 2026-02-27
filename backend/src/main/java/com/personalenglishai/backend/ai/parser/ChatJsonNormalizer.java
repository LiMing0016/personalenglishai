package com.personalenglishai.backend.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.prompt.PromptTemplates;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ChatJsonNormalizer {

    private final ObjectMapper objectMapper;

    public ChatJsonNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeOrThrow(String raw, String scope) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("root must be object");
            }

            ObjectNode out = objectMapper.createObjectNode();
            out.put("mode", "chat");

            String taskType = readText(root, "task_type");
            out.put("task_type", (taskType == null || taskType.isBlank()) ? "other" : taskType);

            ObjectNode content = objectMapper.createObjectNode();
            JsonNode contentNode = root.get("content");
            if (contentNode == null || !contentNode.isObject()) {
                throw new IllegalArgumentException("content must be object");
            }
            String primaryText = readText(contentNode, "primary_text");
            if (primaryText == null || primaryText.isBlank()) {
                throw new IllegalArgumentException("content.primary_text is required");
            }
            content.put("primary_text", primaryText);

            String secondaryText = readText(contentNode, "secondary_text");
            if (secondaryText != null && !secondaryText.isBlank()) {
                content.put("secondary_text", secondaryText);
            }

            JsonNode bulletsNode = contentNode.get("bullets");
            if (bulletsNode != null && bulletsNode.isArray()) {
                ArrayNode bullets = objectMapper.createArrayNode();
                for (JsonNode n : bulletsNode) {
                    if (n != null && !n.isNull()) {
                        String b = n.asText("").trim();
                        if (!b.isEmpty()) {
                            bullets.add(b);
                        }
                    }
                }
                if (!bullets.isEmpty()) {
                    content.set("bullets", bullets);
                }
            }
            out.set("content", content);

            JsonNode actionsNode = root.get("actions");
            if (actionsNode != null && actionsNode.isArray()) {
                ArrayNode actions = objectMapper.createArrayNode();
                for (JsonNode actionNode : actionsNode) {
                    if (actionNode == null || !actionNode.isObject()) {
                        continue;
                    }
                    String type = readText(actionNode, "type");
                    if (type == null || type.isBlank()) {
                        continue;
                    }
                    ObjectNode action = objectMapper.createObjectNode();
                    action.put("type", type);
                    String text = readText(actionNode, "text");
                    if (text != null && !text.isBlank()) {
                        action.put("text", text);
                    } else {
                        action.set("text", NullNode.getInstance());
                    }
                    actions.add(action);
                }
                if (!actions.isEmpty()) {
                    out.set("actions", actions);
                }
            }

            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("scope", normalizeScope(scope));
            meta.put("policy_tag", PromptTemplates.CHAT_POLICY_TAG);
            out.set("meta", meta);

            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid chat json", e);
        }
    }

    public String applyRuntimeMeta(String normalizedJson, String scope, boolean repairUsed, boolean fallbackUsed) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("normalized json root must be object");
            }
            ObjectNode out = (ObjectNode) root;
            ObjectNode meta = out.has("meta") && out.get("meta").isObject()
                    ? (ObjectNode) out.get("meta")
                    : objectMapper.createObjectNode();
            meta.put("scope", normalizeScope(scope));
            meta.put("policy_tag", PromptTemplates.CHAT_POLICY_TAG);
            meta.put("repair_used", repairUsed);
            meta.put("fallback_used", fallbackUsed);
            out.set("meta", meta);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to attach runtime meta", e);
        }
    }

    public String fallbackJson(String scope, boolean repairUsed, boolean fallbackUsed) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("mode", "chat");
        out.put("task_type", "other");

        ObjectNode content = objectMapper.createObjectNode();
        content.put("primary_text", "I can help with translation, rewrite, explanation, or study advice. Please provide your exact target.");
        out.set("content", content);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("scope", normalizeScope(scope));
        meta.put("policy_tag", PromptTemplates.CHAT_POLICY_TAG);
        meta.put("repair_used", repairUsed);
        meta.put("fallback_used", fallbackUsed);
        out.set("meta", meta);

        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"mode\":\"chat\",\"task_type\":\"other\",\"content\":{\"primary_text\":\"I can help with translation, rewrite, explanation, or study advice. Please provide your exact target.\"},\"meta\":{\"scope\":\""
                    + normalizeScope(scope)
                    + "\",\"policy_tag\":\""
                    + PromptTemplates.CHAT_POLICY_TAG
                    + "\",\"repair_used\":"
                    + repairUsed
                    + ",\"fallback_used\":"
                    + fallbackUsed
                    + "}}";
        }
    }

    private String readText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText("").trim();
    }

    private String normalizeScope(String scope) {
        if (scope == null) {
            return "document";
        }
        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        return "selection".equals(normalized) ? "selection" : "document";
    }
}

