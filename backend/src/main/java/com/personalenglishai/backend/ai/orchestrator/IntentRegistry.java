package com.personalenglishai.backend.ai.orchestrator;

import com.personalenglishai.backend.ai.handler.IntentHandler;

/**
 * intent -> handler 注册表，支持后续扩展 expand/explain/translate
 */
public interface IntentRegistry {

    void register(String intent, IntentHandler handler);

    IntentHandler getHandler(String intent);
}
