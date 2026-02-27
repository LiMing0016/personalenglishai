package com.personalenglishai.backend.ai.orchestrator.impl;

import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.ContextBuilder;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.debug.DebugFailResolver;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.dto.AiResult;
import com.personalenglishai.backend.ai.dto.FinalResult;
import com.personalenglishai.backend.ai.handler.IntentHandler;
import com.personalenglishai.backend.ai.orchestrator.AIOrchestrator;
import com.personalenglishai.backend.ai.orchestrator.IntentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultAIOrchestrator implements AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultAIOrchestrator.class);
    private final IntentRegistry intentRegistry;
    private final ContextBuilder contextBuilder;
    private final DebugFailResolver debugFailResolver;

    public DefaultAIOrchestrator(IntentRegistry intentRegistry,
                                 ContextBuilder contextBuilder,
                                 DebugFailResolver debugFailResolver) {
        this.intentRegistry = intentRegistry;
        this.contextBuilder = contextBuilder;
        this.debugFailResolver = debugFailResolver;
    }

    @Override
    public AICommandResponse execute(AICommandRequest req, RequestContext ctx) {
        debugFailResolver.resolveFailCode().ifPresent(code -> {
            ctx.setXDebugFail(String.valueOf(code));
            log.debug("Debug fail injection enabled, code={}", code);
        });

        String traceId = UUID.randomUUID().toString();
        ctx.setRequestId(traceId);
        String intent = req.getIntent();
        log.info("AIOrchestrator executing intent={} traceId={}", intent, traceId);

        AIContext aiContext = contextBuilder.build(req, ctx);
        if (aiContext.isFailed()) {
            return failed(traceId, aiContext.getErrorContent());
        }

        IntentHandler handler = intentRegistry.getHandler(intent);
        if (handler == null) {
            return failed(traceId, "unsupported intent: " + intent);
        }

        AICommandResponse response = handler.handle(req, ctx, aiContext);
        response.setTraceId(traceId);
        return response;
    }

    private AICommandResponse failed(String traceId, String message) {
        AICommandResponse response = new AICommandResponse();
        response.setTraceId(traceId);
        response.setStatus("failed");

        AiResult result = new AiResult();
        result.setApply("");
        response.setResult(result);

        FinalResult finalResult = new FinalResult();
        finalResult.setContent(message);
        response.setFinalResult(finalResult);
        return response;
    }
}
