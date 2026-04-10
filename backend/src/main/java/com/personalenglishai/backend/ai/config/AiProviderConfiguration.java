package com.personalenglishai.backend.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfiguration {

    @Bean
    public AiProviderSelection aiProviderSelection(AiProviderProperties properties) {
        return AiProviderSelection.from(properties);
    }
}
