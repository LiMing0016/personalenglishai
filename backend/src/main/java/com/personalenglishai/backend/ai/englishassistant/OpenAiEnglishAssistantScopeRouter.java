package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.ai.context.RequestContext;
import org.springframework.stereotype.Service;

@Service
public class OpenAiEnglishAssistantScopeRouter implements EnglishAssistantScopeRouter {

    private final OpenAiEnglishAssistantClient client;

    public OpenAiEnglishAssistantScopeRouter(OpenAiEnglishAssistantClient client) {
        this.client = client;
    }

    @Override
    public EnglishAssistantRouterResult route(EnglishAssistantChatRequest request,
                                              RequestContext ctx,
                                              String previousResponseId,
                                              boolean hasAssistantOutput) {
        return client.route(request, ctx == null ? null : ctx.getRequestId(), previousResponseId, hasAssistantOutput);
    }
}
