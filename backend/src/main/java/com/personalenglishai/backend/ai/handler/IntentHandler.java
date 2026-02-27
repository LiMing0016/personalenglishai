package com.personalenglishai.backend.ai.handler;

import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;

/**
 * 单个 intent 的处理器，可扩展 expand/explain/translate 等
 */
public interface IntentHandler {

    /**
     * 处理该 intent；AIContext 由 Orchestrator 通过 ContextBuilder 构建（含 draft 等）。
     * 返回的 response 中可不带 traceId（由 Orchestrator 统一填充）。
     */
    AICommandResponse handle(AICommandRequest req, RequestContext ctx, AIContext aiContext);
}
