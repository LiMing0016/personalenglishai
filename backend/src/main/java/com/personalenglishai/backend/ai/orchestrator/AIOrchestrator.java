package com.personalenglishai.backend.ai.orchestrator;

import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;

/**
 * AI 指令编排：按 intent 分发到对应 Handler，traceId 在此生成
 */
public interface AIOrchestrator {

    AICommandResponse execute(AICommandRequest req, RequestContext ctx);
}
