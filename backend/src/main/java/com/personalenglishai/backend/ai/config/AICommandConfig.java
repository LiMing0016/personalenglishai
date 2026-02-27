package com.personalenglishai.backend.ai.config;

import com.personalenglishai.backend.ai.handler.impl.GenerateHandler;
import com.personalenglishai.backend.ai.handler.impl.ChatHandler;
import com.personalenglishai.backend.ai.handler.impl.RewriteHandler;
import com.personalenglishai.backend.ai.orchestrator.IntentRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AICommandConfig {

    private final IntentRegistry intentRegistry;
    private final RewriteHandler rewriteHandler;
    private final GenerateHandler generateHandler;
    private final ChatHandler chatHandler;

    public AICommandConfig(IntentRegistry intentRegistry,
                           RewriteHandler rewriteHandler,
                           GenerateHandler generateHandler,
                           ChatHandler chatHandler) {
        this.intentRegistry = intentRegistry;
        this.rewriteHandler = rewriteHandler;
        this.generateHandler = generateHandler;
        this.chatHandler = chatHandler;
    }

    @PostConstruct
    public void registerHandlers() {
        intentRegistry.register("generate", generateHandler);
        intentRegistry.register("rewrite", rewriteHandler);
        intentRegistry.register("chat", chatHandler);
    }
}
