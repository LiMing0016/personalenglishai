package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientResponsesPayloadTest {

    @Test
    void buildTextResponsesPayloadShouldIncludePromptCachingFields() throws Exception {
        OpenAiClient client = new OpenAiClient(
                "",
                "test",
                "gpt-4o",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        OpenAiResponsesTextRequest request = new OpenAiResponsesTextRequest(
                "gpt-4o",
                "score instructions",
                "user input",
                "resp_prev",
                "score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2",
                "24h",
                true,
                2048
        );

        Method method = OpenAiClient.class.getDeclaredMethod("buildTextResponsesPayload", OpenAiResponsesTextRequest.class);
        method.setAccessible(true);

        JsonNode payload = (JsonNode) method.invoke(client, request);

        assertThat(payload.path("model").asText()).isEqualTo("gpt-4o");
        assertThat(payload.path("instructions").asText()).isEqualTo("score instructions");
        assertThat(payload.path("input").asText()).isEqualTo("user input");
        assertThat(payload.path("previous_response_id").asText()).isEqualTo("resp_prev");
        assertThat(payload.path("prompt_cache_key").asText())
                .isEqualTo("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        assertThat(payload.path("prompt_cache_retention").asText()).isEqualTo("24h");
        assertThat(payload.path("store").asBoolean()).isTrue();
        assertThat(payload.path("max_output_tokens").asInt()).isEqualTo(2048);
    }
}
