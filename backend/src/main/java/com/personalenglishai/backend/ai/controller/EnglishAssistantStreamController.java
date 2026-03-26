package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatRequest;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatResponse;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantService;
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
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/english-assistant")
public class EnglishAssistantStreamController {

    private static final Logger log = LoggerFactory.getLogger(EnglishAssistantStreamController.class);

    private final EnglishAssistantService englishAssistantService;
    private final AIRequestContextResolver requestContextResolver;

    public EnglishAssistantStreamController(EnglishAssistantService englishAssistantService,
                                            AIRequestContextResolver requestContextResolver) {
        this.englishAssistantService = englishAssistantService;
        this.requestContextResolver = requestContextResolver;
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody EnglishAssistantChatRequest request,
                             HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            RequestContext ctx = requestContextResolver.build(httpRequest);
            CompletableFuture.runAsync(() -> doStream(request, ctx, emitter));
        } catch (Exception e) {
            log.warn("english assistant stream bootstrap failed error={}", e.getMessage(), e);
            CompletableFuture.runAsync(() -> emitBootstrapError(emitter, e));
        }
        return emitter;
    }

    private void doStream(EnglishAssistantChatRequest request, RequestContext ctx, SseEmitter emitter) {
        try {
            EnglishAssistantChatResponse response = englishAssistantService.stream(
                    request,
                    ctx,
                    route -> send(emitter, "meta", buildMetaPayload(request, route)),
                    text -> send(emitter, "delta", Map.of("text", text))
            );
            send(emitter, "done", finalPayload(response));
            emitter.complete();
        } catch (Exception e) {
            log.warn("english assistant stream failed traceId={} error={}", ctx.getRequestId(), e.getMessage(), e);
            fallbackToNonStreamResponse(emitter, request, ctx, e);
        }
    }

    private void fallbackToNonStreamResponse(SseEmitter emitter,
                                            EnglishAssistantChatRequest request,
                                            RequestContext ctx,
                                            Exception originalError) {
        try {
            EnglishAssistantChatResponse response = englishAssistantService.chat(request, ctx);
            send(emitter, "done", finalPayload(response));
            emitter.complete();
            log.info("english assistant stream fallback used traceId={} error={}", ctx.getRequestId(), originalError.getMessage());
        } catch (Exception fallbackError) {
            log.warn("english assistant stream fallback failed traceId={} fallbackError={}",
                    ctx.getRequestId(), fallbackError.getMessage(), fallbackError);
            emitBootstrapError(emitter, fallbackError);
        }
    }

    private Map<String, Object> finalPayload(EnglishAssistantChatResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", response.getConversationId());
        payload.put("responseId", response.getResponseId());
        payload.put("scope", response.getScope());
        payload.put("taskType", response.getTaskType());
        payload.put("usedDraftContext", response.getUsedDraftContext());
        payload.put("response", response);
        return payload;
    }

    private Map<String, Object> buildMetaPayload(EnglishAssistantChatRequest request,
                                                 com.personalenglishai.backend.ai.englishassistant.EnglishAssistantRouterResult route) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", request.getConversationId());
        payload.put("scope", route.scope());
        payload.put("taskType", route.taskType());
        payload.put("usedDraftContext", "current_draft".equals(route.scope()) && Boolean.TRUE.equals(request.getUseDraftContext()));
        return payload;
    }

    private void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException e) {
            throw new RuntimeException("failed to send english assistant event", e);
        }
    }

    private void emitBootstrapError(SseEmitter emitter, Exception error) {
        try {
            send(emitter, "error", Map.of(
                    "message", error.getMessage() == null || error.getMessage().isBlank()
                            ? "english assistant stream failed"
                            : error.getMessage()
            ));
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(sendError);
        }
    }
}
