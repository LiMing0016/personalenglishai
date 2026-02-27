package com.personalenglishai.backend.ai.handler.impl;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.TooManyRequests;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.dto.AiResult;
import com.personalenglishai.backend.ai.dto.FinalResult;
import com.personalenglishai.backend.ai.handler.IntentHandler;
import com.personalenglishai.backend.ai.parser.AiResultParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class RewriteHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(RewriteHandler.class);

    private static final String SYSTEM_PROMPT = """
            You are an English writing assistant.
            Rewrite text according to user instruction and return ONLY valid JSON:
            {\"apply\":\"string\",\"explain\":[\"string\"]}
            Rules:
            - apply must be non-empty and directly usable.
            - explain must be an array with concise bullet-like points.
            - do not output markdown fences.
            """;

    private final OpenAiClient openAiClient;
    private final AiResultParser parser;

    public RewriteHandler(OpenAiClient openAiClient, AiResultParser parser) {
        this.openAiClient = openAiClient;
        this.parser = parser;
    }

    @Override
    public AICommandResponse handle(AICommandRequest req, RequestContext ctx, AIContext aiContext) {
        long startTime = System.currentTimeMillis();
        String traceId = ctx.getRequestId();
        String docId = aiContext.getDocId();
        boolean found = aiContext.isDocFound();

        String draftContent = aiContext.getDraftContent() != null ? aiContext.getDraftContent() : "";
        int inputLength = draftContent.length();

        log.info("RewriteHandler handling traceId={} docId={} found={} inputLength={}", traceId, docId, found, inputLength);

        if (draftContent.isEmpty()) {
            return fail("document content is empty");
        }

        // dev/local failure injection
        if ("429".equals(ctx.getXDebugFail())) {
            long latencyMs = System.currentTimeMillis() - startTime;
            TooManyRequests simulated = new TooManyRequests("429 Too Many Requests (debug simulated)");
            log.error("OpenAI call failed traceId={} attempt=1 latencyMs={} errorType=UPSTREAM_ERROR httpStatus=429 rootCauseClass=TooManyRequests rootCauseMsg={} inputLength={} openaiRequestId=debug",
                    traceId, latencyMs, simulated.getMessage(), inputLength);
            return fail("AI service rate limited, please try again");
        }

        if ("502".equals(ctx.getXDebugFail())) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("OpenAI call failed traceId={} attempt=1 latencyMs={} errorType=UPSTREAM_ERROR httpStatus=502 rootCauseClass=WebClientResponseException rootCauseMsg=502 Bad Gateway (debug simulated) inputLength={} openaiRequestId=debug",
                    traceId, latencyMs, inputLength);
            return fail("AI upstream service unavailable, please try again");
        }

        try {
            String instruction = req.getInstruction() == null ? "" : req.getInstruction().trim();
            String userPrompt = "instruction:\n" + instruction + "\n\n" +
                    "original_text:\n" + draftContent;

            String raw = openAiClient.callWithTraceId(SYSTEM_PROMPT, userPrompt, traceId, ctx.getXDebugFail());
            AiResult parsed = parser.parseStrict(raw);

            AICommandResponse response = new AICommandResponse();
            response.setStatus("success");
            response.setResult(parsed);

            FinalResult legacy = new FinalResult();
            legacy.setContent(parsed.getApply());
            response.setFinalResult(legacy);

            log.info("RewriteHandler succeeded traceId={} docId={} found={} latencyMs={} outputLength={}",
                    traceId, docId, found, System.currentTimeMillis() - startTime, parsed.getApply().length());
            return response;

        } catch (WebClientResponseException e) {
            log.error("RewriteHandler upstream failed traceId={} docId={} found={} latencyMs={} status={}",
                    traceId, docId, found, System.currentTimeMillis() - startTime, e.getStatusCode().value());
            return fail("AI upstream service unavailable, please try again");
        } catch (Exception e) {
            log.error("RewriteHandler failed traceId={} docId={} found={} latencyMs={} error={}",
                    traceId, docId, found, System.currentTimeMillis() - startTime, e.getMessage());
            return fail("AI rewrite failed, please try again");
        }
    }

    private AICommandResponse fail(String message) {
        AICommandResponse response = new AICommandResponse();
        response.setStatus("failed");

        AiResult result = new AiResult();
        result.setApply("");
        response.setResult(result);

        FinalResult legacy = new FinalResult();
        legacy.setContent(message);
        response.setFinalResult(legacy);
        return response;
    }
}
