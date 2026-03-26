package com.personalenglishai.backend.ai.assistant;

import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;

public interface AssistantToolExecutor {

    AssistantToolResult execute(
            String toolName,
            String argumentsJson,
            AICommandRequest request,
            RequestContext ctx,
            AIContext aiContext
    );
}
