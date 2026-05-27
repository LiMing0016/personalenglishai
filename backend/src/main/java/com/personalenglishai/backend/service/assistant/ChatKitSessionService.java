package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.ChatKitSessionRequest;
import com.personalenglishai.backend.controller.dto.assistant.ChatKitSessionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ChatKitSessionService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiProviderSelection providerSelection;
    private final OpenAiClientConfig openAiClientConfig;
    private final String defaultWorkflowId;

    public ChatKitSessionService(
            AiProviderSelection providerSelection,
            OpenAiClientConfig openAiClientConfig,
            @Value("${assistant.chatkit.workflow-id:${OPENAI_CHATKIT_WORKFLOW_ID:}}") String defaultWorkflowId) {
        this.providerSelection = providerSelection;
        this.openAiClientConfig = openAiClientConfig;
        this.defaultWorkflowId = defaultWorkflowId == null ? "" : defaultWorkflowId.trim();
    }

    public ChatKitSessionResponse createWritingCoachSession(Long userId, ChatKitSessionRequest request) {
        String workflowId = firstNonBlank(request.getWorkflowId(), defaultWorkflowId);
        if (isBlank(workflowId)) {
            throw new BizException(ErrorCode.ASSISTANT_CHATKIT_NOT_CONFIGURED);
        }

        AiProviderSelection.SelectedProvider provider = providerSelection.resolve("openai");
        if (isBlank(provider.apiKey())) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE, "OpenAI API key 未配置");
        }

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode workflow = body.putObject("workflow");
        workflow.put("id", workflowId);
        body.put("user", "user_" + userId);

        ObjectNode stateVariables = body.putObject("state_variables");
        for (Map.Entry<String, Object> entry : buildStateVariables(request).entrySet()) {
            putPrimitiveValue(stateVariables, entry.getKey(), entry.getValue());
        }

        String baseUrl = normalizeBaseUrl(firstNonBlank(provider.baseUrl(), openAiClientConfig.getBaseUrl(), "https://api.openai.com"));
        JsonNode response = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("OpenAI-Beta", "chatkit_beta=v1")
                .build()
                .post()
                .uri("/v1/chatkit/sessions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("OpenAI ChatKit session response is empty");
        }
        String clientSecret = textValue(response, "client_secret");
        if (isBlank(clientSecret) && response.path("client_secret").isObject()) {
            clientSecret = textValue(response.path("client_secret"), "value");
        }
        if (isBlank(clientSecret)) {
            throw new IllegalStateException("OpenAI ChatKit session response does not include client_secret");
        }

        return new ChatKitSessionResponse(
                clientSecret,
                textValue(response, "id"),
                response.path("expires_at").isNumber() ? response.path("expires_at").asLong() : null
        );
    }

    private Map<String, Object> buildStateVariables(ChatKitSessionRequest request) {
        Map<String, Object> stateVariables = new LinkedHashMap<>();
        if (!isBlank(request.getConversationId())) {
            stateVariables.put("appConversationId", request.getConversationId().trim());
        }
        Map<String, Object> writingContext = request.getWritingContext();
        if (writingContext != null && !writingContext.isEmpty()) {
            stateVariables.put("writingContextJson", toJson(writingContext));
            copyIfPresent(stateVariables, writingContext, "inputAsText");
            copyIfPresent(stateVariables, writingContext, "writingMode");
            copyIfPresent(stateVariables, writingContext, "studyStage");
            copyIfPresent(stateVariables, writingContext, "taskType");
            copyIfPresent(stateVariables, writingContext, "essayQuestion");
            copyIfPresent(stateVariables, writingContext, "essayGenre");
            copyIfPresent(stateVariables, writingContext, "minWords");
            copyIfPresent(stateVariables, writingContext, "maxWords");
            copyIfPresent(stateVariables, writingContext, "includeDraft");
        }
        if (request.getStateVariables() != null) {
            stateVariables.putAll(request.getStateVariables());
        }
        return stateVariables;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid writing context", e);
        }
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putPrimitiveValue(ObjectNode node, String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        if (value instanceof Boolean booleanValue) {
            node.put(key, booleanValue);
        } else if (value instanceof Integer integerValue) {
            node.put(key, integerValue);
        } else if (value instanceof Long longValue) {
            node.put(key, longValue);
        } else if (value instanceof Float floatValue) {
            node.put(key, floatValue);
        } else if (value instanceof Double doubleValue) {
            node.put(key, doubleValue);
        } else if (value instanceof Number numberValue) {
            node.put(key, numberValue.doubleValue());
        } else {
            node.put(key, String.valueOf(value));
        }
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = firstNonBlank(value, "https://api.openai.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
