package com.personalenglishai.backend.service.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.mapper.ops.AgentDebugMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDebugServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordAssistantRunPersistsRouteDecisionAndRedactsSecrets() {
        FakeAgentDebugMapper mapper = new FakeAgentDebugMapper();
        AgentDebugService service = new AgentDebugService(mapper, objectMapper);

        AssistantRunMetadataResponse run = new AssistantRunMetadataResponse();
        run.setRunId("run_123");
        run.setTraceId("trace_123");
        run.setAgentName("Polish Agent");
        run.setModel("gpt-5.4-mini");
        run.setMode("daily_explain");
        run.setIntent("free_chat");
        run.setScope("message_only");
        run.setLatencyMs(1234L);
        AssistantRunMetadataResponse.Usage usage = new AssistantRunMetadataResponse.Usage();
        usage.setInputTokens(100);
        usage.setCachedInputTokens(20);
        usage.setOutputTokens(30);
        usage.setTotalTokens(130);
        usage.setRequests(1);
        run.setUsage(usage);
        run.setRouteRequest(new LinkedHashMap<>(Map.of(
                "message", "帮我润色",
                "Authorization", "Bearer secret-token"
        )));
        run.setRoutingDecision(new LinkedHashMap<>(Map.of(
                "intent", "polish",
                "route_type", "run_workflow",
                "target_agent", "polish",
                "confidence", 0.97
        )));

        service.recordAssistantRun(42L, "conv-1", "帮我润色", run, "Polished text");

        assertThat(mapper.insertedRuns).hasSize(1);
        Map<String, Object> row = mapper.insertedRuns.get(0);
        assertThat(row)
                .containsEntry("runId", "run_123")
                .containsEntry("traceId", "trace_123")
                .containsEntry("userId", 42L)
                .containsEntry("conversationId", "conv-1")
                .containsEntry("rawUserMessage", "帮我润色")
                .containsEntry("intent", "polish")
                .containsEntry("routeType", "run_workflow")
                .containsEntry("targetAgent", "polish")
                .containsEntry("model", "gpt-5.4-mini")
                .containsEntry("status", "completed")
                .containsEntry("latencyMs", 1234L);
        assertThat(row.get("routeRequestJson").toString()).contains("\"Authorization\":\"[REDACTED]\"");
        assertThat(row.get("routingDecisionJson").toString()).contains("\"target_agent\":\"polish\"");
        assertThat(row.get("usageJson").toString()).contains("\"cachedInputTokens\":20");

        assertThat(mapper.insertedSteps).hasSize(2);
        assertThat(mapper.insertedSteps.get(0)).containsEntry("stepType", "route_agent");
        assertThat(mapper.insertedSteps.get(1)).containsEntry("stepType", "target_agent");
    }

    @Test
    void listRunsReturnsPagedDataAndDetailCombinesStepsAndPrompts() {
        FakeAgentDebugMapper mapper = new FakeAgentDebugMapper();
        mapper.rows.add(new LinkedHashMap<>(Map.of(
                "runId", "run_123",
                "createdAt", LocalDateTime.parse("2026-05-15T12:00:00"),
                "rawUserMessage", "帮我润色这句话",
                "intent", "polish",
                "routeType", "run_workflow",
                "targetAgent", "polish",
                "model", "gpt-5.4-mini",
                "totalTokens", 130,
                "latencyMs", 1234,
                "status", "completed"
        )));
        mapper.detail = new LinkedHashMap<>(mapper.rows.get(0));
        mapper.detail.put("routeRequestJson", "{\"message\":\"帮我润色\"}");
        mapper.detail.put("routingDecisionJson", "{\"intent\":\"polish\"}");
        mapper.steps.add(new LinkedHashMap<>(Map.of("stepType", "route_agent", "stepOrder", 1)));
        mapper.prompts.add(new LinkedHashMap<>(Map.of("promptKey", "route_decision", "agentName", "RouteAgent")));
        AgentDebugService service = new AgentDebugService(mapper, objectMapper);

        AdminPageResponse<Map<String, Object>> page = service.listRuns(null, null, null, null, null, null, null, null, 1, 20);
        Map<String, Object> detail = service.getRunDetail("run_123");

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).singleElement().satisfies(row ->
                assertThat(row).containsEntry("runId", "run_123").containsEntry("status", "completed"));
        assertThat(detail).containsEntry("runId", "run_123");
        assertThat(detail.get("routeRequest")).isInstanceOf(Map.class);
        assertThat(detail.get("routingDecision")).isInstanceOf(Map.class);
        assertThat(detail.get("steps")).asList().singleElement().satisfies(step ->
                assertThat(step).isInstanceOf(Map.class));
        assertThat(detail.get("prompts")).asList().singleElement().satisfies(prompt ->
                assertThat(prompt).isInstanceOf(Map.class));
    }

    private static final class FakeAgentDebugMapper implements AgentDebugMapper {
        private final List<Map<String, Object>> insertedRuns = new ArrayList<>();
        private final List<Map<String, Object>> insertedSteps = new ArrayList<>();
        private final List<Map<String, Object>> insertedPrompts = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<Map<String, Object>> steps = new ArrayList<>();
        private final List<Map<String, Object>> prompts = new ArrayList<>();
        private Map<String, Object> detail;

        @Override
        public void insertRun(Map<String, Object> row) {
            insertedRuns.add(new LinkedHashMap<>(row));
        }

        @Override
        public void insertStep(Map<String, Object> row) {
            insertedSteps.add(new LinkedHashMap<>(row));
        }

        @Override
        public void insertPromptSnapshot(Map<String, Object> row) {
            insertedPrompts.add(new LinkedHashMap<>(row));
        }

        @Override
        public List<Map<String, Object>> searchRuns(String status, String intent, String targetAgent, String model,
                                                    Long userId, String conversationId, String createdFrom,
                                                    String createdTo, int offset, int limit) {
            return rows;
        }

        @Override
        public long countRuns(String status, String intent, String targetAgent, String model,
                              Long userId, String conversationId, String createdFrom, String createdTo) {
            return rows.size();
        }

        @Override
        public Map<String, Object> findRunByRunId(String runId) {
            return detail;
        }

        @Override
        public List<Map<String, Object>> listSteps(String runId) {
            return steps;
        }

        @Override
        public List<Map<String, Object>> listPromptSnapshots(String runId) {
            return prompts;
        }

        @Override
        public List<Map<String, Object>> searchPromptSnapshots(String promptKey, String promptHash, String agentName,
                                                               String model, String createdFrom, String createdTo,
                                                               int offset, int limit) {
            return prompts;
        }

        @Override
        public long countPromptSnapshots(String promptKey, String promptHash, String agentName,
                                         String model, String createdFrom, String createdTo) {
            return prompts.size();
        }
    }
}
