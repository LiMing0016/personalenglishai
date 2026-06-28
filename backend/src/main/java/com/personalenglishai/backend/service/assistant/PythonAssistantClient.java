package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Component
public class PythonAssistantClient {
    private final WebClient webClient;
    private final Duration timeout;

    public PythonAssistantClient(
            WebClient.Builder builder,
            @Value("${assistant.orchestrator.base-url:http://127.0.0.1:8011}") String baseUrl,
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
        for (PythonAssistantFile file : request.files()) {
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(parseMediaType(file.contentType()));
            body.add("files", new HttpEntity<>(
                    new NamedByteArrayResource(file.content(), file.filename()),
                    fileHeaders));
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
            String assistantMode,
            List<PythonAssistantFile> files) {
        public PythonAssistantChatRequest(
                String message,
                String conversationId,
                String studyStage,
                String assistantMode) {
            this(message, conversationId, studyStage, assistantMode, List.of());
        }
    }

    public PythonAssistantReply run(AssistantRequest request, String authorization) {
        try {
            return webClient.post()
                    .uri("/assistant/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (authorization != null && !authorization.isBlank()) {
                            headers.set("Authorization", authorization);
                        }
                    })
                    .bodyValue(request)
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

    public Flux<String> streamRun(AssistantRequest request, String authorization) {
        return webClient.post()
                .uri("/assistant/run/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
                    if (authorization != null && !authorization.isBlank()) {
                        headers.set("Authorization", authorization);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.class, e ->
                        new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE, resolveUpstreamMessage(e)));
    }

    public LearningCanvasOrganizeResponse organizeLearningAsset(LearningCanvasOrganizeRequest request) {
        try {
            return webClient.post()
                    .uri("/learning-assets/organize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LearningCanvasOrganizeResponse.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE, resolveUpstreamMessage(e));
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE);
        }
    }

    public record PythonAssistantFile(String filename, String contentType, byte[] content) {
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

    }

    public static class PythonAssistantReply {
        private String reply;
        private String outputText;
        private String agentName;
        private String agent_name;
        private AssistantRunMetadataResponse run;

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

        public AssistantRunMetadataResponse getRun() {
            return run;
        }

        public void setRun(AssistantRunMetadataResponse run) {
            this.run = run;
        }

        public String text() {
            if (reply != null && !reply.isBlank()) {
                return reply;
            }
            return outputText == null ? "" : outputText;
        }
    }
}
