package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiEnglishAssistantClientTest {

    @Test
    void buildAnswerInputShouldPlaceRubricBeforeDraftSections() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());
        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setTaskType("evaluate");
        request.setScope("current_draft");
        request.setUseDraftContext(true);
        request.setRubricKey("postgrad-exam-v1");
        request.setRubricSummary("rubric summary");
        request.setAssignmentText("assignment");
        request.setSelectedText("selected");
        request.setDraftText("draft");
        request.setRecentTurnsText("recent turns");
        request.setSummaryText("summary text");
        request.setMessage("message");

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildAnswerInput", EnglishAssistantAnswerRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(client, request);

        assertThat(result).contains("<rubric>");
        assertThat(result.indexOf("<rubric>")).isLessThan(result.indexOf("<assignment>"));
        assertThat(result.indexOf("<rubric>")).isLessThan(result.indexOf("<selected_text>"));
        assertThat(result.indexOf("<rubric>")).isLessThan(result.indexOf("<draft_excerpt>"));
        assertThat(result.indexOf("<draft_excerpt>")).isLessThan(result.indexOf("<recent_turns>"));
        assertThat(result.indexOf("<recent_turns>")).isLessThan(result.indexOf("<summary>"));
    }

    @Test
    void buildRouterPayloadShouldIncludePreviousResponseIdWhenProvided() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setMessage("关于大学生就业的");
        request.setUseDraftContext(false);
        request.setPreferredAction("ask");

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildRouterPayload", EnglishAssistantChatRequest.class, String.class, boolean.class);
        method.setAccessible(true);

        JsonNode payload = (JsonNode) method.invoke(client, request, "resp-general-old", true);

        assertThat(payload.path("previous_response_id").asText()).isEqualTo("resp-general-old");
        assertThat(payload.path("prompt_cache_key").asText()).isEqualTo("english-router-v1");
    }

    @Test
    void buildRouterTextConfigShouldExposeArtifactAndSensitiveScopes() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildRouterTextConfig");
        method.setAccessible(true);

        JsonNode textConfig = (JsonNode) method.invoke(client);
        JsonNode scopeEnum = textConfig.path("format").path("schema").path("properties").path("scope").path("enum");

        assertThat(scopeEnum.isArray()).isTrue();
        assertThat(scopeEnum.toString()).contains("assistant_output");
        assertThat(scopeEnum.toString()).contains("session_meta");
        assertThat(scopeEnum.toString()).contains("sensitive_refuse");
    }

    @Test
    void buildAnswerInputShouldIncludeAssistantOutputBeforeUserMessage() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());
        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setTaskType("translate");
        request.setScope("assistant_output");
        request.setUseDraftContext(false);
        request.setAssistantOutputText("In conclusion, graduates can build fulfilling careers.");
        request.setRecentTurnsText("turn 1");
        request.setSummaryText("summary");
        request.setMessage("翻译一下最后一段");

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildAnswerInput", EnglishAssistantAnswerRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(client, request);

        assertThat(result).contains("<assistant_output_excerpt>");
        assertThat(result.indexOf("<assistant_output_excerpt>")).isLessThan(result.indexOf("<recent_turns>"));
        assertThat(result.indexOf("<recent_turns>")).isLessThan(result.indexOf("<summary>"));
        assertThat(result.indexOf("<summary>")).isLessThan(result.indexOf("<user_message>"));
        assertThat(result).contains("In conclusion, graduates can build fulfilling careers.");
    }

    @Test
    void buildRouterInputShouldExposeRealAssistantOutputFlag() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setMessage("翻译一下最后一段");
        request.setUseDraftContext(false);
        request.setPreferredAction("translate");

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildRouterInput", EnglishAssistantChatRequest.class, boolean.class);
        method.setAccessible(true);

        JsonNode withoutArtifact = (JsonNode) method.invoke(client, request, false);
        JsonNode withArtifact = (JsonNode) method.invoke(client, request, true);

        String withoutArtifactText = withoutArtifact.get(0).path("content").get(0).path("text").asText();
        String withArtifactText = withArtifact.get(0).path("content").get(0).path("text").asText();

        assertThat(withoutArtifactText).contains("\"hasAssistantOutput\":false");
        assertThat(withArtifactText).contains("\"hasAssistantOutput\":true");
    }

    @Test
    void buildAnswerPayloadShouldEnableAutoTruncation() throws Exception {
        OpenAiClientConfig config = new OpenAiClientConfig();
        OpenAiEnglishAssistantClient client =
                new OpenAiEnglishAssistantClient("", config, new ObjectMapper());
        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setTaskType("ask");
        request.setScope("english_general");
        request.setUseDraftContext(false);
        request.setPromptCacheKey("english-answer-general-v1");
        request.setMessage("hello");

        Method method = OpenAiEnglishAssistantClient.class
                .getDeclaredMethod("buildAnswerPayload", EnglishAssistantAnswerRequest.class, boolean.class);
        method.setAccessible(true);

        JsonNode payload = (JsonNode) method.invoke(client, request, false);

        assertThat(payload.path("truncation").asText()).isEqualTo("auto");
    }
}
