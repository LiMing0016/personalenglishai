package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Component
public class PythonAssistantClient {
    private final WebClient webClient;
    private final Duration timeout;

    public PythonAssistantClient(
            WebClient.Builder builder,
            @Value("${assistant.orchestrator.base-url:http://127.0.0.1:8002}") String baseUrl,
            @Value("${assistant.orchestrator.timeout-ms:60000}") long timeoutMs) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public PythonAssistantReply chat(PythonAssistantChatRequest request, String authorization) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("message", request.message());
        body.add("conversation_id", request.conversationId());
        if (request.studyStage() != null && !request.studyStage().isBlank()) {
            body.add("study_stage", request.studyStage().trim());
        }
        if (request.assistantMode() != null && !request.assistantMode().isBlank()) {
            body.add("assistant_mode", request.assistantMode().trim());
        }

        try {
            return webClient.post()
                    .uri("/chat")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> {
                        if (authorization != null && !authorization.isBlank()) {
                            headers.set("Authorization", authorization);
                        }
                    })
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(PythonAssistantReply.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE, resolveUpstreamMessage(e));
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE);
        }
    }

    private String resolveUpstreamMessage(WebClientResponseException e) {
        if (e.getStatusCode().is5xxServerError()) {
            return ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE.getMessage();
        }
        return e.getResponseBodyAsString().isBlank()
                ? ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE.getMessage()
                : e.getResponseBodyAsString();
    }

    public record PythonAssistantChatRequest(
            String message,
            String conversationId,
            String studyStage,
            String assistantMode) {
    }

    public static class PythonAssistantReply {
        private String reply;
        private String outputText;
        private String agentName;
        private String agent_name;

        public String getReply() {
            return reply;
        }

        public void setReply(String reply) {
            this.reply = reply;
        }

        public String getOutputText() {
            return outputText;
        }

        public void setOutputText(String outputText) {
            this.outputText = outputText;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getAgent_name() {
            return agent_name;
        }

        public void setAgent_name(String agent_name) {
            this.agent_name = agent_name;
        }

        public String text() {
            if (reply != null && !reply.isBlank()) {
                return reply;
            }
            return outputText == null ? "" : outputText;
        }
    }
}
