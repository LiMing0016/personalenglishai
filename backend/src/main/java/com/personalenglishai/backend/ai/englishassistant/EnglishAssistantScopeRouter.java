package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.ai.context.RequestContext;

public interface EnglishAssistantScopeRouter {

    EnglishAssistantRouterResult route(EnglishAssistantChatRequest request,
                                       RequestContext ctx,
                                       String previousResponseId,
                                       boolean hasAssistantOutput);
}
