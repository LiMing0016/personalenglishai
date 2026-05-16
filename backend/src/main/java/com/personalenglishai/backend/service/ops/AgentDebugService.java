package com.personalenglishai.backend.service.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.mapper.ops.AgentDebugMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentDebugService {
    private static final Logger log = LoggerFactory.getLogger(AgentDebugService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentDebugMapper agentDebugMapper;
    private final ObjectMapper objectMapper;

    public AgentDebugService(AgentDebugMapper agentDebugMapper, ObjectMapper objectMapper) {
        this.agentDebugMapper = agentDebugMapper;
        this.objectMapper = objectMapper;
    }

    public void recordAssistantRun(Long userId,
                                   String conversationId,
                                   String rawUserMessage,
                                   AssistantRunMetadataResponse run,
                                   String finalOutput) {
        if (run == null || isBlank(run.getRunId())) {
            return;
        }
        try {
            Map<String, Object> routingDecision = safeMap(run.getRoutingDecision());
            Map<String, Object> routeRequest = safeMap(run.getRouteRequest());
            Map<String, Object> usage = usageMap(run.getUsage());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", run.getRunId());
            row.put("traceId", run.getTraceId());
            row.put("userId", userId);
            row.put("conversationId", conversationId);
            row.put("rawUserMessage", truncate(rawUserMessage, 4000));
            row.put("intent", firstString(routingDecision.get("intent"), run.getIntent()));
            row.put("routeType", firstString(routingDecision.get("route_type"), routingDecision.get("routeType")));
            row.put("workflow", firstString(routingDecision.get("workflow")));
            row.put("targetAgent", firstString(routingDecision.get("target_agent"), routingDecision.get("targetAgent"), run.getAgentName()));
            row.put("agentName", run.getAgentName());
            row.put("model", run.getModel());
            row.put("status", "completed");
            row.put("latencyMs", run.getLatencyMs());
            row.put("responseId", run.getOpenai() == null ? null : run.getOpenai().getResponseId());
            row.put("totalTokens", usage.get("totalTokens"));
            row.put("routeRequestJson", writeJson(sanitize(routeRequest)));
            row.put("routingDecisionJson", writeJson(sanitize(routingDecision)));
            row.put("usageJson", writeJson(sanitize(usage)));
            row.put("outputJson", writeJson(sanitize(Map.of("finalOutput", truncate(finalOutput, 4000)))));
            row.put("errorMessage", null);
            agentDebugMapper.insertRun(row);

            insertDefaultSteps(run, routeRequest, routingDecision, usage, finalOutput);
            insertPromptSnapshots(run);
        } catch (Exception e) {
            log.warn("Agent debug run record failed runId={}", run.getRunId(), e);
        }
    }

    public AdminPageResponse<Map<String, Object>> listRuns(String status,
                                                           String intent,
                                                           String targetAgent,
                                                           String model,
                                                           Long userId,
                                                           String conversationId,
                                                           String createdFrom,
                                                           String createdTo,
                                                           int page,
                                                           int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> items = agentDebugMapper.searchRuns(
                blankToNull(status),
                blankToNull(intent),
                blankToNull(targetAgent),
                blankToNull(model),
                userId,
                blankToNull(conversationId),
                blankToNull(createdFrom),
                blankToNull(createdTo),
                offset,
                safeSize
        );
        long total = agentDebugMapper.countRuns(
                blankToNull(status),
                blankToNull(intent),
                blankToNull(targetAgent),
                blankToNull(model),
                userId,
                blankToNull(conversationId),
                blankToNull(createdFrom),
                blankToNull(createdTo)
        );
        return new AdminPageResponse<>(sanitizeRows(items), total, safePage, safeSize);
    }

    public Map<String, Object> getRunDetail(String runId) {
        Map<String, Object> run = agentDebugMapper.findRunByRunId(runId);
        if (run == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Agent run 不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>(sanitizeMap(run));
        detail.put("routeRequest", parseJsonObject(run.get("routeRequestJson")));
        detail.put("routingDecision", parseJsonObject(run.get("routingDecisionJson")));
        detail.put("usage", parseJsonObject(run.get("usageJson")));
        detail.put("output", parseJsonObject(run.get("outputJson")));
        detail.put("steps", sanitizeRows(agentDebugMapper.listSteps(runId)));
        detail.put("prompts", sanitizeRows(agentDebugMapper.listPromptSnapshots(runId)));
        return detail;
    }

    public List<Map<String, Object>> listSteps(String runId) {
        return sanitizeRows(agentDebugMapper.listSteps(runId));
    }

    public List<Map<String, Object>> listPromptSnapshots(String runId) {
        return sanitizeRows(agentDebugMapper.listPromptSnapshots(runId));
    }

    public AdminPageResponse<Map<String, Object>> listPromptSnapshots(String promptKey,
                                                                      String promptHash,
                                                                      String agentName,
                                                                      String model,
                                                                      String createdFrom,
                                                                      String createdTo,
                                                                      int page,
                                                                      int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> items = agentDebugMapper.searchPromptSnapshots(
                blankToNull(promptKey),
                blankToNull(promptHash),
                blankToNull(agentName),
                blankToNull(model),
                blankToNull(createdFrom),
                blankToNull(createdTo),
                offset,
                safeSize
        );
        long total = agentDebugMapper.countPromptSnapshots(
                blankToNull(promptKey),
                blankToNull(promptHash),
                blankToNull(agentName),
                blankToNull(model),
                blankToNull(createdFrom),
                blankToNull(createdTo)
        );
        return new AdminPageResponse<>(sanitizeRows(items), total, safePage, safeSize);
    }

    private void insertDefaultSteps(AssistantRunMetadataResponse run,
                                    Map<String, Object> routeRequest,
                                    Map<String, Object> routingDecision,
                                    Map<String, Object> usage,
                                    String finalOutput) {
        insertStep(run.getRunId(), 1, "route_agent", "RouteAgent", routeRequest, routingDecision, null, null);
        insertStep(
                run.getRunId(),
                2,
                "target_agent",
                run.getAgentName(),
                routingDecision,
                Map.of("finalOutput", truncate(finalOutput, 4000)),
                usage,
                null
        );
        if (run.getSteps() == null) {
            return;
        }
        int order = 3;
        for (Map<String, Object> step : run.getSteps()) {
            Map<String, Object> row = new LinkedHashMap<>(sanitizeMap(step));
            row.putIfAbsent("runId", run.getRunId());
            row.putIfAbsent("stepOrder", order++);
            row.putIfAbsent("stepType", "agent_step");
            row.putIfAbsent("agentName", row.get("agent_name"));
            agentDebugMapper.insertStep(row);
        }
    }

    private void insertStep(String runId,
                            int order,
                            String stepType,
                            String agentName,
                            Object input,
                            Object output,
                            Object usage,
                            String errorMessage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", runId);
        row.put("stepOrder", order);
        row.put("stepType", stepType);
        row.put("agentName", agentName);
        row.put("inputJson", writeJson(sanitize(input)));
        row.put("outputJson", writeJson(sanitize(output)));
        row.put("usageJson", writeJson(sanitize(usage)));
        row.put("errorMessage", errorMessage);
        agentDebugMapper.insertStep(row);
    }

    private void insertPromptSnapshots(AssistantRunMetadataResponse run) {
        if (run.getPromptSnapshots() == null) {
            return;
        }
        for (Map<String, Object> promptSnapshot : run.getPromptSnapshots()) {
            Map<String, Object> row = new LinkedHashMap<>(sanitizeMap(promptSnapshot));
            row.putIfAbsent("runId", run.getRunId());
            agentDebugMapper.insertPromptSnapshot(row);
        }
    }

    private Map<String, Object> usageMap(AssistantRunMetadataResponse.Usage usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requests", usage.getRequests());
        map.put("inputTokens", usage.getInputTokens());
        map.put("cachedInputTokens", usage.getCachedInputTokens());
        map.put("outputTokens", usage.getOutputTokens());
        map.put("totalTokens", usage.getTotalTokens());
        return map;
    }

    private Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> parseJsonObject(Object value) {
        if (value == null || value.toString().isBlank()) {
            return Map.of();
        }
        try {
            return sanitizeMap(objectMapper.readValue(value.toString(), MAP_TYPE));
        } catch (Exception e) {
            return Map.of("raw", value.toString());
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sanitized.put(key, isSensitiveKey(key) ? "[REDACTED]" : sanitize(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : list) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        if (value instanceof String text && looksLikeApiKey(text)) {
            return "[REDACTED]";
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        return (Map<String, Object>) sanitize(map == null ? Map.of() : map);
    }

    private List<Map<String, Object>> sanitizeRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::sanitizeMap).toList();
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("authorization")
                || lower.contains("cookie")
                || lower.equals("token")
                || lower.endsWith("_token")
                || lower.endsWith("token")
                || lower.contains("access_token")
                || lower.contains("refresh_token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("password");
    }

    private boolean looksLikeApiKey(String value) {
        return value.startsWith("sk-") || value.startsWith("sk-proj-");
    }

    private String firstString(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
