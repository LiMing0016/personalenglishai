package com.personalenglishai.backend.ai.handler.impl;

import com.personalenglishai.backend.ai.client.OpenAiClient;
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

import java.util.Locale;

@Component
public class GenerateHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerateHandler.class);

    private static final String SYSTEM_PROMPT = """
            You are an English writing assistant.
            Return ONLY valid JSON with schema:
            {\"apply\":\"string\",\"explain\":[\"string\"]}
            Rules:
            - apply must be non-empty English text.
            - explain must be an array; keep it concise.
            - do not output markdown fences.
            """;

    private final OpenAiClient openAiClient;
    private final AiResultParser parser;

    public GenerateHandler(OpenAiClient openAiClient, AiResultParser parser) {
        this.openAiClient = openAiClient;
        this.parser = parser;
    }

    @Override
    public AICommandResponse handle(AICommandRequest req, RequestContext ctx, AIContext aiContext) {
        String traceId = ctx.getRequestId();
        long start = System.currentTimeMillis();

        String instruction = req.getInstruction() == null ? "" : req.getInstruction().trim();
        if (instruction.isBlank()) {
            return fail("instruction is required for generate");
        }

        String mode = req.getMode() == null ? "md" : req.getMode().trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("sm") && !mode.equals("md") && !mode.equals("lg")) {
            mode = "md";
        }

        String contextText = aiContext.getDraftContent() == null ? "" : aiContext.getDraftContent();
        String docId = aiContext.getDocId();
        boolean found = aiContext.isDocFound();

        String userPrompt = "instruction:\n" + instruction + "\n\n"
                + "mode:\n" + mode + "\n\n"
                + "context_doc_id:\n" + (docId == null ? "" : docId) + "\n\n"
                + "context_document:\n" + contextText;

        try {
            String raw = openAiClient.callWithTraceId(SYSTEM_PROMPT, userPrompt, traceId, ctx.getXDebugFail());
            AiResult parsed = parser.parseStrict(raw);

            AICommandResponse response = new AICommandResponse();
            response.setStatus("success");
            response.setResult(parsed);

            FinalResult legacy = new FinalResult();
            legacy.setContent(parsed.getApply());
            response.setFinalResult(legacy);

            log.info("GenerateHandler succeeded traceId={} docId={} found={} latencyMs={}",
                    traceId, docId, found, System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            log.error("GenerateHandler failed traceId={} docId={} found={} latencyMs={} error={}",
                    traceId, docId, found, System.currentTimeMillis() - start, e.getMessage());
            return fail("AI generation failed, please try again");
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
