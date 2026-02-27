package com.personalenglishai.backend.ai.orchestrator.impl;

import com.personalenglishai.backend.ai.handler.IntentHandler;
import com.personalenglishai.backend.ai.orchestrator.IntentRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultIntentRegistry implements IntentRegistry {

    private final Map<String, IntentHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(String intent, IntentHandler handler) {
        handlers.put(intent, handler);
    }

    @Override
    public IntentHandler getHandler(String intent) {
        return intent != null ? handlers.get(intent) : null;
    }
}
