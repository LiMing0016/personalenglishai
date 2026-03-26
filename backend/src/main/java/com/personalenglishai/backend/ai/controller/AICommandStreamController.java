package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.assistant.AssistantEvent;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.ContextBuilder;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.debug.DebugFailResolver;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.dto.AiResult;
import com.personalenglishai.backend.ai.dto.FinalResult;
import com.personalenglishai.backend.ai.handler.impl.ChatHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class AICommandStreamController {

    private static final Logger log = LoggerFactory.getLogger(AICommandStreamController.class);

    private final AIRequestContextResolver requestContextResolver;
    private final ContextBuilder contextBuilder;
    private final DebugFailResolver debugFailResolver;
    private final ChatHandler chatHandler;

    public AICommandStreamController(AIRequestContextResolver requestContextResolver,
                                     ContextBuilder contextBuilder,
                                     DebugFailResolver debugFailResolver,
                                     ChatHandler chatHandler) {
        this.requestContextResolver = requestContextResolver;
        this.contextBuilder = contextBuilder;
        this.debugFailResolver = debugFailResolver;
        this.chatHandler = chatHandler;
    }

    @PostMapping(path = "/command/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter commandStream(@Valid @RequestBody AICommandRequest request,
                                    HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestContext ctx = requestContextResolver.build(httpRequest);
        debugFailResolver.resolveFailCode().ifPresent(code -> ctx.setXDebugFail(String.valueOf(code)));
        String traceId = UUID.randomUUID().toString();
        ctx.setRequestId(traceId);

        CompletableFuture.runAsync(() -> streamChat(request, ctx, emitter));
        return emitter;
    }

    private void streamChat(AICommandRequest request, RequestContext ctx, SseEmitter emitter) {
        String traceId = ctx.getRequestId();
        try {
            if (!"chat".equalsIgnoreCase(request.getIntent())) {
                AICommandResponse failed = failed(traceId, "stream endpoint only supports chat intent");
                send(emitter, "final", finalPayload(traceId, failed));
                emitter.complete();
                return;
            }

            send(emitter, "meta", Map.of("traceId", traceId, "status", "streaming"));

            AIContext aiContext = contextBuilder.build(request, ctx);
            if (aiContext.isFailed()) {
                AICommandResponse failed = failed(traceId, aiContext.getErrorContent());
                send(emitter, "final", finalPayload(traceId, failed));
                emitter.complete();
                return;
            }

            AICommandResponse response = chatHandler.stream(request, ctx, aiContext, event -> sendAssistantEvent(emitter, traceId, event));
            response.setTraceId(traceId);
            send(emitter, "final", finalPayload(traceId, response));
            emitter.complete();
        } catch (Exception e) {
            log.warn("AI stream failed traceId={} error={}", traceId, e.getMessage(), e);
            try {
                send(emitter, "error", Map.of(
                        "traceId", traceId,
                        "status", "failed",
                        "message", "stream chat failed"
                ));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
        }
    }

    private void sendAssistantEvent(SseEmitter emitter, String traceId, AssistantEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("type", event.type());
        payload.put("status", event.status());
        payload.put("message", event.message());
        payload.put("toolRuns", event.toolRuns());
        payload.put("finalResult", event.finalResult());
        try {
            send(emitter, "assistant_event", payload);
        } catch (IOException e) {
            throw new RuntimeException("failed to send assistant stream event", e);
        }
    }

    private void send(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(payload));
    }

    private Map<String, Object> finalPayload(String traceId, AICommandResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("response", response);
        return payload;
    }

    private AICommandResponse failed(String traceId, String message) {
        AICommandResponse response = new AICommandResponse();
        response.setTraceId(traceId);
        response.setStatus("failed");
        response.setMessage(message);

        AiResult result = new AiResult();
        result.setApply("");
        response.setResult(result);

        FinalResult finalResult = new FinalResult();
        finalResult.setContent(message);
        response.setFinalResult(finalResult);
        return response;
    }
}
