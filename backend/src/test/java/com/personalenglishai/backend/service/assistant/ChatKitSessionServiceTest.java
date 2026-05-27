package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.ai.config.AiProviderProperties;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.ChatKitSessionRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatKitSessionServiceTest {
    @Test
    void createWritingCoachSession_reportsMissingWorkflowIdAsBusinessError() {
        ChatKitSessionService service = new ChatKitSessionService(
                providerSelection(),
                new OpenAiClientConfig(),
                "");

        assertThatThrownBy(() -> service.createWritingCoachSession(1L, new ChatKitSessionRequest()))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ASSISTANT_CHATKIT_NOT_CONFIGURED));
    }

    private AiProviderSelection providerSelection() {
        AiProviderProperties properties = new AiProviderProperties();
        AiProviderProperties.Provider provider = new AiProviderProperties.Provider();
        provider.setApiKey("test-key");
        provider.setBaseUrl("https://api.openai.com");
        properties.setActive("openai");
        properties.setProviders(Map.of("openai", provider));
        return AiProviderSelection.from(properties);
    }
}
