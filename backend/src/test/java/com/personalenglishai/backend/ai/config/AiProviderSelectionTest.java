package com.personalenglishai.backend.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderSelectionTest {

    @Test
    void explicitProviderShouldOverrideDefaultActiveProvider() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("openai");

        AiProviderProperties.Provider openai = new AiProviderProperties.Provider();
        openai.setApiKey("openai-key");
        openai.setBaseUrl("https://api.openai.com");
        openai.setModel("gpt-4o");
        openai.setImageModel("gpt-image-1");
        properties.getProviders().put("openai", openai);

        AiProviderProperties.Provider kimi = new AiProviderProperties.Provider();
        kimi.setApiKey("kimi-key");
        kimi.setBaseUrl("https://api.moonshot.cn/v1");
        kimi.setModel("kimi-k2.5");
        properties.getProviders().put("kimi", kimi);

        AiProviderSelection selection = AiProviderSelection.from(properties);

        assertThat(selection.resolve("kimi").provider()).isEqualTo("kimi");
        assertThat(selection.resolve("kimi").apiKey()).isEqualTo("kimi-key");
        assertThat(selection.resolve("kimi").baseUrl()).isEqualTo("https://api.moonshot.cn/v1");
        assertThat(selection.resolve("kimi").model()).isEqualTo("kimi-k2.5");
        assertThat(selection.resolve("openai").imageModel()).isEqualTo("gpt-image-1");
    }

    @Test
    void missingExplicitProviderShouldFallBackToActiveProvider() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("qwen");

        AiProviderProperties.Provider qwen = new AiProviderProperties.Provider();
        qwen.setApiKey("qwen-key");
        qwen.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        qwen.setModel("qwen-plus");
        qwen.setImageModel("wanx2.1");
        properties.getProviders().put("qwen", qwen);

        AiProviderSelection selection = AiProviderSelection.from(properties);

        assertThat(selection.resolve(null).provider()).isEqualTo("qwen");
        assertThat(selection.resolve("").apiKey()).isEqualTo("qwen-key");
        assertThat(selection.resolve(null).imageModel()).isEqualTo("wanx2.1");
    }
}
