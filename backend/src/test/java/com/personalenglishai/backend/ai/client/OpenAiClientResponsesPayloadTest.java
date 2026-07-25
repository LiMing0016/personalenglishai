package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalenglishai.backend.ai.config.AiProviderProperties;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientResponsesPayloadTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void buildTextResponsesPayloadShouldIncludePromptCachingFields() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelection("openai", "https://api.openai.com", "gpt-4o"),
                "test",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        OpenAiResponsesTextRequest request = new OpenAiResponsesTextRequest(
                "qwen",
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

    @Test
    void constructorShouldExposeActiveProviderModel() {
        OpenAiClient client = new OpenAiClient(
                providerSelection("qwen", "https://dashscope.aliyuncs.com/compatible-mode", "qwen-plus"),
                "test",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        assertThat(client.getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void requestTemperatureShouldBeForcedToOneForKimiK25() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelection("kimi", "https://api.moonshot.cn/v1", "kimi-k2.5"),
                "test",
                "chat_completions",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        Method method = OpenAiClient.class.getDeclaredMethod(
                "normalizeChatCompletionsTemperature",
                AiProviderSelection.SelectedProvider.class,
                Double.class
        );
        method.setAccessible(true);

        AiProviderSelection.SelectedProvider kimi = providerSelection("kimi", "https://api.moonshot.cn/v1", "kimi-k2.5")
                .resolve("kimi");
        AiProviderSelection.SelectedProvider openai = providerSelection("openai", "https://api.openai.com", "gpt-4o")
                .resolve("openai");

        Double kimiTemperature = (Double) method.invoke(client, kimi, 0.3d);
        Double openAiTemperature = (Double) method.invoke(client, openai, 0.3d);

        assertThat(kimiTemperature).isEqualTo(1.0d);
        assertThat(openAiTemperature).isEqualTo(0.3d);
    }

    @Test
    void resolveImageProviderShouldFallbackToOpenAiWhenRequestedProviderHasNoImageModel() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelectionWithImages(),
                "test",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        Method method = OpenAiClient.class.getDeclaredMethod("resolveImageProvider", String.class);
        method.setAccessible(true);

        AiProviderSelection.SelectedProvider selected =
                (AiProviderSelection.SelectedProvider) method.invoke(client, "kimi");

        assertThat(selected.provider()).isEqualTo("openai");
        assertThat(selected.imageModel()).isEqualTo("gpt-image-1.5");
    }

    @Test
    void buildImageRequestPayloadShouldOnlyIncludeSupportedMinimalFields() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelection("openai", "https://api.openai.com", "gpt-4o", "gpt-image-1.5"),
                "test",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        Method method = OpenAiClient.class.getDeclaredMethod(
                "buildImageRequestPayload",
                AiProviderSelection.SelectedProvider.class,
                String.class
        );
        method.setAccessible(true);

        AiProviderSelection.SelectedProvider selectedProvider =
                providerSelection("openai", "https://api.openai.com", "gpt-4o", "gpt-image-1.5").resolve("openai");
        JsonNode payload = (JsonNode) method.invoke(client, selectedProvider, "A campus life comic scene");

        assertThat(payload.path("model").asText()).isEqualTo("gpt-image-1.5");
        assertThat(payload.path("prompt").asText()).isEqualTo("A campus life comic scene");
        assertThat(payload.path("size").asText()).isEqualTo("1024x1024");
        assertThat(payload.path("n").asInt()).isEqualTo(1);
        assertThat(payload.has("response_format")).isFalse();
    }

    @Test
    void buildVisionChatCompletionsPayloadShouldIncludeRealImageItem() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelection("openai", "https://api.openai.com", "gpt-4o"),
                "test",
                "chat_completions",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        Method method = OpenAiClient.class.getDeclaredMethod(
                "buildVisionChatCompletionsPayload",
                AiProviderSelection.SelectedProvider.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        AiProviderSelection.SelectedProvider selectedProvider =
                providerSelection("openai", "https://api.openai.com", "gpt-4o").resolve("openai");
        JsonNode payload = (JsonNode) method.invoke(
                client,
                selectedProvider,
                "gpt-4o",
                "system prompt",
                "transcribe the image",
                "data:image/png;base64,abc"
        );

        assertThat(payload.path("model").asText()).isEqualTo("gpt-4o");
        assertThat(payload.path("messages").isArray()).isTrue();
        assertThat(payload.path("messages").get(1).path("content").isArray()).isTrue();
        assertThat(payload.path("messages").get(1).path("content").get(0).path("type").asText())
                .isEqualTo("text");
        assertThat(payload.path("messages").get(1).path("content").get(1).path("type").asText())
                .isEqualTo("image_url");
        assertThat(payload.path("messages").get(1).path("content").get(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/png;base64,abc");
        assertThat(payload.toString()).doesNotContain("\"imageBase64\"");
    }

    @Test
    void structuredResponsesPayloadShouldUseStrictJsonSchemaFormat() throws Exception {
        OpenAiClient client = client("responses");
        JsonNode schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"type\":\"object\",\"additionalProperties\":false}");
        Method method = OpenAiClient.class.getDeclaredMethod(
                "buildStructuredResponsesPayload",
                String.class, String.class, String.class, String.class,
                JsonNode.class, Double.class, Integer.class);
        method.setAccessible(true);

        JsonNode payload = (JsonNode) method.invoke(
                client, "gpt-4o", "system", "user", "vocabulary_core_v1", schema, 0.0, 1200);

        JsonNode format = payload.path("text").path("format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("name").asText()).isEqualTo("vocabulary_core_v1");
        assertThat(format.path("strict").asBoolean()).isTrue();
        assertThat(format.path("schema")).isEqualTo(schema);
        assertThat(payload.path("temperature").asDouble()).isEqualTo(0.0);
        assertThat(payload.path("max_output_tokens").asInt()).isEqualTo(1200);
    }

    @Test
    void structuredChatPayloadShouldUseEquivalentNestedJsonSchema() throws Exception {
        OpenAiClient client = client("chat_completions");
        JsonNode schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"type\":\"object\",\"additionalProperties\":false}");
        Method method = OpenAiClient.class.getDeclaredMethod(
                "buildStructuredChatCompletionsPayload",
                AiProviderSelection.SelectedProvider.class,
                String.class, String.class, String.class, String.class,
                JsonNode.class, Double.class, Integer.class);
        method.setAccessible(true);
        AiProviderSelection.SelectedProvider provider =
                providerSelection("openai", "https://api.openai.com", "gpt-4o").resolve("openai");

        JsonNode payload = (JsonNode) method.invoke(
                client, provider, "gpt-4o", "system", "user",
                "vocabulary_core_v1", schema, 0.0, 1200);

        JsonNode format = payload.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("name").asText())
                .isEqualTo("vocabulary_core_v1");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema")).isEqualTo(schema);
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(1200);
    }

    @Test
    void publicStructuredCallRetainsSchemaAcrossResponsesToChatFallback() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requests.add(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(exchange.getRequestBody()));
            respond(exchange, 400, """
                    {"error":{"code":"unsupported_endpoint","message":"Responses unsupported"}}
                    """);
        });
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"id":"chat-1","choices":[{"message":{"role":"assistant","content":"{\\"term\\":\\"record\\"}"}}]}
                    """);
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        OpenAiClientConfig config = new OpenAiClientConfig();
        config.setMaxRetries(0);
        OpenAiClient client = new OpenAiClient(
                providerSelection("openai", baseUrl, "gpt-test"),
                "test", "responses", "gpt-fallback", false, false, 12000, config);
        JsonNode schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"type\":\"object\",\"additionalProperties\":false}");

        String result = client.callStructuredWithTraceId(
                "system", "user", "trace-fallback", "vocabulary_core_v1",
                schema, 0.0, 1200);

        assertThat(result).isEqualTo("{\"term\":\"record\"}");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path("text").path("format").path("schema"))
                .isEqualTo(schema);
        assertThat(requests.get(1).path("response_format").path("json_schema").path("schema"))
                .isEqualTo(schema);
        assertThat(requests.get(1).path("response_format").path("json_schema")
                .path("name").asText()).isEqualTo("vocabulary_core_v1");
        assertThat(requests.get(1).path("response_format").path("json_schema")
                .path("strict").asBoolean()).isTrue();
    }

    private void respond(HttpExchange exchange, int status, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private OpenAiClient client(String endpointMode) {
        return new OpenAiClient(
                providerSelection("openai", "https://api.openai.com", "gpt-4o"),
                "test", endpointMode, "gpt-4o", false, false, 12000,
                new OpenAiClientConfig());
    }

    private AiProviderSelection providerSelection(String provider, String baseUrl, String model) {
        return providerSelection(provider, baseUrl, model, null);
    }

    private AiProviderSelection providerSelection(String provider, String baseUrl, String model, String imageModel) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive(provider);

        AiProviderProperties.Provider configured = new AiProviderProperties.Provider();
        configured.setApiKey(provider + "-key");
        configured.setBaseUrl(baseUrl);
        configured.setModel(model);
        configured.setImageModel(imageModel);
        properties.getProviders().put(provider, configured);
        return AiProviderSelection.from(properties);
    }

    private AiProviderSelection providerSelectionWithImages() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("kimi");

        AiProviderProperties.Provider openai = new AiProviderProperties.Provider();
        openai.setApiKey("openai-key");
        openai.setBaseUrl("https://api.openai.com");
        openai.setModel("gpt-4o");
        openai.setImageModel("gpt-image-1.5");
        properties.getProviders().put("openai", openai);

        AiProviderProperties.Provider kimi = new AiProviderProperties.Provider();
        kimi.setApiKey("kimi-key");
        kimi.setBaseUrl("https://api.moonshot.cn/v1");
        kimi.setModel("kimi-k2.5");
        properties.getProviders().put("kimi", kimi);
        return AiProviderSelection.from(properties);
    }
}
