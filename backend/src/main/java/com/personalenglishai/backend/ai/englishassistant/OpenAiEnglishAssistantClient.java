package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OpenAiEnglishAssistantClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEnglishAssistantClient.class);
    private static final String MODEL = "gpt-4o";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final OpenAiClientConfig config;

    public OpenAiEnglishAssistantClient(@Value("${OPENAI_API_KEY:}") String apiKey,
                                        OpenAiClientConfig config,
                                        ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.config = config;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(config.getResponseTimeoutMs()));
        ProxyTarget proxyTarget = resolveProxy(config);
        if (proxyTarget != null) {
            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyTarget.host())
                    .port(proxyTarget.port()));
        }
        this.webClient = WebClient.builder()
                .baseUrl(resolveBaseUrl(config))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey == null ? "" : apiKey))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public EnglishAssistantRouterResult route(EnglishAssistantChatRequest request,
                                              String traceId,
                                              String previousResponseId,
                                              boolean hasAssistantOutput) {
        String outputText = "";
        try {
            ObjectNode payload = buildRouterPayload(request, previousResponseId, hasAssistantOutput);
            JsonNode response = postJson(payload);
            outputText = extractResponsesText(response);
            JsonNode parsed = objectMapper.readTree(outputText);
            return new EnglishAssistantRouterResult(
                    parsed.path("scope").asText("off_topic"),
                    parsed.path("taskType").asText("ask"),
                    parsed.path("needsDraftContext").asBoolean(false),
                    parsed.path("refusalReason").isNull() ? null : parsed.path("refusalReason").asText(null)
            );
        } catch (Exception e) {
            String errorBody = outputText;
            if (e instanceof WebClientResponseException we) {
                errorBody = sanitizeErrorBody(we.getResponseBodyAsString());
            }
            log.warn("english assistant router parse failed traceId={} error={} body={}", traceId, e.getMessage(), errorBody, e);
            return new EnglishAssistantRouterResult("off_topic", "ask", false, "router_parse_failed");
        }
    }

    public EnglishAssistantAnswerResult answer(EnglishAssistantAnswerRequest request) {
        ObjectNode payload = buildAnswerPayload(request, false);
        JsonNode response = postJson(payload);
        String responseId = response.path("id").asText(null);
        JsonNode usage = response.path("usage");
        Integer inputTokens = usage.path("input_tokens").isInt() ? usage.path("input_tokens").asInt() : null;
        Integer cachedTokens = usage.path("input_tokens_details").path("cached_tokens").isInt()
                ? usage.path("input_tokens_details").path("cached_tokens").asInt() : null;
        String text = extractResponsesText(response).trim();
        log.info("english assistant answer traceId={} scope={} taskType={} inputTokens={} cachedTokens={}",
                request.getTraceId(), request.getScope(), request.getTaskType(), inputTokens, cachedTokens);
        return new EnglishAssistantAnswerResult(responseId, text, inputTokens, cachedTokens);
    }

    public EnglishAssistantAnswerResult streamAnswer(EnglishAssistantAnswerRequest request, EnglishAssistantStreamListener listener) {
        return streamAnswerWithFallback(request, listener);
    }

    public Integer countInputTokens(EnglishAssistantAnswerRequest request) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", MODEL);
            payload.put("instructions", buildAnswerInstructions());
            if (!isBlank(request.getPreviousResponseId())) {
                payload.put("previous_response_id", request.getPreviousResponseId());
            }
            payload.set("input", objectMapper.getNodeFactory().textNode(buildAnswerInput(request)));
            JsonNode response = postJson("/v1/responses/input_tokens", payload);
            return response.path("input_tokens").isInt() ? response.path("input_tokens").asInt() : null;
        } catch (Exception e) {
            log.debug("english assistant input token count failed traceId={} error={}",
                    request.getTraceId(), e.getMessage());
            return null;
        }
    }

    private EnglishAssistantAnswerResult streamAnswerWithFallback(EnglishAssistantAnswerRequest request,
                                                                EnglishAssistantStreamListener listener) {
        ObjectNode payload = buildAnswerPayload(request, true);
        StringBuilder lineBuffer = new StringBuilder();
        StringBuilder messageBuffer = new StringBuilder();
        AtomicReference<String> currentEvent = new AtomicReference<>("message");
        List<String> dataLines = new ArrayList<>();
        AtomicReference<String> responseId = new AtomicReference<>(null);
        AtomicInteger inputTokens = new AtomicInteger(-1);
        AtomicInteger cachedTokens = new AtomicInteger(-1);

        try {
            Flux<String> flux = webClient.post()
                .uri("/v1/responses")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(config.getOverallTimeoutMs()));

        flux.doOnNext(chunk -> {
                    lineBuffer.append(chunk);
                    processSseBuffer(lineBuffer, currentEvent, dataLines, responseId, inputTokens, cachedTokens, messageBuffer, listener);
                })
                .blockLast(Duration.ofMillis(config.getOverallTimeoutMs()));

            if (!dataLines.isEmpty()) {
                flushEvent(currentEvent.get(), dataLines, responseId, inputTokens, cachedTokens, messageBuffer, listener);
            }

            Integer resolvedInputTokens = inputTokens.get() >= 0 ? inputTokens.get() : null;
            Integer resolvedCachedTokens = cachedTokens.get() >= 0 ? cachedTokens.get() : null;

            String streamedMessage = messageBuffer.toString().trim();
            if (!streamedMessage.isBlank()) {
                log.info("english assistant stream answer traceId={} scope={} taskType={} inputTokens={} cachedTokens={}",
                        request.getTraceId(), request.getScope(), request.getTaskType(), resolvedInputTokens, resolvedCachedTokens);
                return new EnglishAssistantAnswerResult(responseId.get(), streamedMessage, resolvedInputTokens, resolvedCachedTokens);
            }
        } catch (Exception e) {
            log.warn("english assistant stream answer parse/transport failed traceId={} fallback to non-stream error={}",
                    request.getTraceId(), e.getMessage(), e);
        }

        log.info("english assistant stream fallback to non-stream traceId={} scope={} taskType={}",
                request.getTraceId(), request.getScope(), request.getTaskType());
        return answer(request);
    }

    private JsonNode postJson(ObjectNode payload) {
        return postJson("/v1/responses", payload);
    }

    private JsonNode postJson(String uri, ObjectNode payload) {
        try {
            return webClient.post()
                    .uri(uri)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(config.getOverallTimeoutMs()))
                    .block();
        } catch (WebClientResponseException e) {
            String body = sanitizeErrorBody(e.getResponseBodyAsString());
            log.warn("english assistant responses request failed status={} error={} body={}",
                    e.getStatusCode().value(), e.getMessage(), body);
            throw e;
        }
    }

    private ObjectNode buildAnswerPayload(EnglishAssistantAnswerRequest request, boolean stream) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", MODEL);
        payload.put("store", true);
        payload.put("stream", stream);
        payload.put("prompt_cache_key", request.getPromptCacheKey());
        payload.put("truncation", "auto");
        payload.put("instructions", buildAnswerInstructions());
        if (!isBlank(request.getPreviousResponseId())) {
            payload.put("previous_response_id", request.getPreviousResponseId());
        }
        payload.set("input", objectMapper.getNodeFactory().textNode(buildAnswerInput(request)));
        payload.put("max_output_tokens", 900);
        ObjectNode text = payload.putObject("text");
        text.putObject("format").put("type", "text");
        return payload;
    }

    private String buildAnswerInstructions() {
        return """
                你是站内专用英语学习与英语写作助手。
                只处理英语相关问题，以及当前作文相关问题。
                也可以处理用户对上一轮助手生成内容的继续操作，例如翻译、改写、解释最后一段。
                可以回答少量与当前会话使用方式相关的元问题，例如是否记住上下文、是否引用作文、是否继续上一轮。
                默认用中文回答，必要时给简短英文例句。
                不要自称通用助手，不讨论与英语无关的话题。
                若问题是政治、色情、暴力、违法、极端等敏感高风险话题，应拒答并把话题引回英语学习。
                若问题只是与当前助手使用方式有关的元问题，可以简短直接回答。
                若问题不是英语学习、英语写作或会话元问题，应礼貌收口并把话题引回英语。
                若 useDraftContext=false，则忽略任何作文上下文。
                若 useDraftContext=true，则只能基于提供的题目、选中文本、草稿回答，不得捏造作文原文。
                若提供了 assistant_output，则可以基于上一轮助手输出继续处理，不要要求用户重复粘贴刚生成的内容。
                有 selected_text 时，优先围绕 selected_text。
                若 task_type 是 rewrite、polish、translate，请直接输出最终可应用的文本，不要额外解释。
                若 task_type 是 ask、explain、evaluate、generate，请自然回答，像老师一样清楚。
                """;
    }

    private ObjectNode buildRouterPayload(EnglishAssistantChatRequest request,
                                          String previousResponseId,
                                          boolean hasAssistantOutput) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", MODEL);
        payload.put("store", false);
        payload.put("prompt_cache_key", "english-router-v1");
        payload.put("instructions", """
                你是站内英语助手的请求路由器。
                你只做分类，不直接回答用户问题。
                分类规则：
                1. 英语词汇、语法、翻译、表达、写作技巧、英语作文方法，归类为 english_general。
                2. 明确围绕当前作文、当前句子、上文、这篇文章、这段怎么改，归类为 current_draft。
                3. 明确围绕上一轮助手刚生成的内容，例如上面那篇、刚才那篇、最后一段、上一条内容，归类为 assistant_output。
                4. 询问当前会话能力、是否记住上下文、是否引用作文、是否继续上一轮等元问题，归类为 session_meta。
                5. 涉及政治、色情、暴力、违法、极端或其他敏感高风险话题，归类为 sensitive_refuse。
                6. 数学、历史、新闻、编程、泛常识等与英语学习无关且也不属于会话元问题的内容，归类为 off_topic。
                只输出符合 schema 的 JSON。
                """);
        if (!isBlank(previousResponseId)) {
            payload.put("previous_response_id", previousResponseId);
        }
        payload.set("input", buildRouterInput(request, hasAssistantOutput));
        payload.set("text", buildRouterTextConfig());
        payload.put("max_output_tokens", 120);
        return payload;
    }

    private ArrayNode buildRouterInput(EnglishAssistantChatRequest request, boolean hasAssistantOutput) {
        ArrayNode input = objectMapper.createArrayNode();

        ObjectNode node = objectMapper.createObjectNode();
        node.put("message", safe(request.getMessage()));
        node.put("useDraftContext", Boolean.TRUE.equals(request.getUseDraftContext()));
        node.put("hasAssignmentText", !isBlank(request.getAssignmentText()));
        node.put("hasSelectedText", !isBlank(request.getSelectedText()));
        node.put("hasDraftText", !isBlank(request.getDraftText()));
        node.put("hasAssistantOutput", hasAssistantOutput);
        node.put("preferredAction", safe(request.getPreferredAction()));
        String nodeText;
        try {
            nodeText = objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            nodeText = "{\"message\":\"" + safe(request.getMessage()) + "\"}";
        }
        ObjectNode inputItem = objectMapper.createObjectNode();
        inputItem.put("role", "user");
        ArrayNode contentArray = inputItem.putArray("content");
        ObjectNode contentText = contentArray.addObject();
        contentText.put("type", "input_text");
        contentText.put("text", nodeText);
        input.add(inputItem);
        return input;
    }

    private String buildAnswerInput(EnglishAssistantAnswerRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("task_type=").append(safe(request.getTaskType())).append('\n');
        sb.append("scope=").append(safe(request.getScope())).append('\n');
        sb.append("useDraftContext=").append(request.getUseDraftContext()).append('\n');
        if (!isBlank(request.getTrimmedContextMode())) {
            sb.append("trimmed_context_mode=").append(safe(request.getTrimmedContextMode())).append('\n');
        }
        if (!isBlank(request.getRubricKey())) {
            sb.append("rubric_key=").append(safe(request.getRubricKey())).append('\n');
        }
        if (request.getUseDraftContext()) {
            appendSection(sb, "rubric", request.getRubricSummary());
            appendSection(sb, "assignment", request.getAssignmentText());
            appendSection(sb, "selected_text", request.getSelectedText());
            appendSection(sb, "draft_excerpt", request.getDraftText());
        }
        appendSection(sb, "assistant_output_excerpt", request.getAssistantOutputText());
        appendSection(sb, "recent_turns", request.getRecentTurnsText());
        appendSection(sb, "summary", request.getSummaryText());
        appendSection(sb, "user_message", request.getMessage());
        return sb.toString().trim();
    }

    private ObjectNode buildRouterTextConfig() {
        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "english_router_result");
        format.put("strict", true);

        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode scope = properties.putObject("scope");
        scope.put("type", "string");
        ArrayNode scopeEnum = scope.putArray("enum");
        scopeEnum.add("english_general");
        scopeEnum.add("current_draft");
        scopeEnum.add("assistant_output");
        scopeEnum.add("session_meta");
        scopeEnum.add("sensitive_refuse");
        scopeEnum.add("off_topic");

        ObjectNode taskType = properties.putObject("taskType");
        taskType.put("type", "string");
        ArrayNode taskEnum = taskType.putArray("enum");
        taskEnum.add("ask");
        taskEnum.add("explain");
        taskEnum.add("rewrite");
        taskEnum.add("polish");
        taskEnum.add("translate");
        taskEnum.add("evaluate");
        taskEnum.add("generate");

        properties.putObject("needsDraftContext").put("type", "boolean");
        ObjectNode refusalReason = properties.putObject("refusalReason");
        refusalReason.putArray("type").add("string").add("null");
        refusalReason.put("description", "非英语问题时返回拒答原因，英语问题可返回空字符串");

        ArrayNode required = schema.putArray("required");
        required.add("scope");
        required.add("taskType");
        required.add("needsDraftContext");
        required.add("refusalReason");
        schema.put("additionalProperties", false);
        return text;
    }

    private void processSseBuffer(StringBuilder buffer,
                                  AtomicReference<String> currentEvent,
                                  List<String> dataLines,
                                  AtomicReference<String> responseId,
                                  AtomicInteger inputTokens,
                                  AtomicInteger cachedTokens,
                                  StringBuilder messageBuffer,
                                  EnglishAssistantStreamListener listener) {
        int boundary = buffer.indexOf("\n");
        while (boundary >= 0) {
            String line = buffer.substring(0, boundary).replace("\r", "");
            buffer.delete(0, boundary + 1);
            if (line.isEmpty()) {
                flushEvent(currentEvent.get(), dataLines, responseId, inputTokens, cachedTokens, messageBuffer, listener);
                currentEvent.set("message");
            } else if (line.startsWith("event:")) {
                currentEvent.set(line.substring(6).trim());
            } else if (line.startsWith("data:")) {
                dataLines.add(line.substring(5).trim());
            }
            boundary = buffer.indexOf("\n");
        }
    }

    private void flushEvent(String eventName,
                            List<String> dataLines,
                            AtomicReference<String> responseId,
                            AtomicInteger inputTokens,
                            AtomicInteger cachedTokens,
                            StringBuilder messageBuffer,
                            EnglishAssistantStreamListener listener) {
        if (dataLines.isEmpty()) {
            return;
        }
        String raw = String.join("\n", dataLines);
        dataLines.clear();
        try {
            JsonNode node = objectMapper.readTree(raw);
            switch (eventName) {
                case "response.created" -> responseId.set(node.path("response").path("id").asText(responseId.get()));
                case "response.output_text.delta", "response.output_text.done", "response.output_item.added", "response.output_item.done" -> {
                    String delta = extractStreamText(node);
                    if (!delta.isEmpty()) {
                        messageBuffer.append(delta);
                        listener.onDelta(delta);
                    }
                }
                case "response.completed" -> {
                    JsonNode response = node.path("response");
                    responseId.set(response.path("id").asText(responseId.get()));
                    String completedText = extractResponsesText(response).trim();
                    if (!completedText.isEmpty() && messageBuffer.isEmpty()) {
                        messageBuffer.append(completedText);
                        listener.onDelta(completedText);
                    }
                    JsonNode usage = response.path("usage");
                    if (usage.path("input_tokens").isInt()) {
                        inputTokens.set(usage.path("input_tokens").asInt());
                    }
                    if (usage.path("input_tokens_details").path("cached_tokens").isInt()) {
                        cachedTokens.set(usage.path("input_tokens_details").path("cached_tokens").asInt());
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.debug("english assistant stream parse skipped event={} raw={} error={}", eventName, raw, e.getMessage());
        }
    }

    private String extractStreamText(JsonNode eventNode) {
        JsonNode deltaNode = eventNode.path("delta");
        if (deltaNode.isTextual()) {
            return deltaNode.asText("");
        }
        if (deltaNode.isObject() && deltaNode.path("text").isTextual()) {
            return deltaNode.path("text").asText("");
        }
        if (eventNode.path("text").isTextual()) {
            return eventNode.path("text").asText("");
        }
        if (eventNode.path("output_text").isTextual()) {
            return eventNode.path("output_text").asText("");
        }
        if (eventNode.path("content").isTextual()) {
            return eventNode.path("content").asText("");
        }

        String fromContent = extractTextFromArray(eventNode.path("content"));
        if (!fromContent.isBlank()) {
            return fromContent;
        }
        fromContent = extractTextFromOutput(eventNode.path("output"));
        if (!fromContent.isBlank()) {
            return fromContent;
        }
        return "";
    }

    private String extractTextFromArray(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : arrayNode) {
            String value = extractTextFromPart(part);
            if (!value.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String extractTextFromOutput(JsonNode outputNode) {
        if (!outputNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : outputNode) {
            JsonNode contentNode = item.path("content");
            String itemText = extractTextFromArray(contentNode);
            if (!itemText.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(itemText);
            }
        }
        return sb.toString();
    }

    private String extractTextFromPart(JsonNode partNode) {
        if (!partNode.isObject()) {
            return "";
        }
        String type = partNode.path("type").asText("");
        if ("output_text".equals(type) || "text".equals(type)) {
            if (partNode.path("text").isTextual()) {
                return partNode.path("text").asText("");
            }
            if (partNode.path("content").isTextual()) {
                return partNode.path("content").asText("");
            }
        }
        if (partNode.path("text").isTextual()) {
            return partNode.path("text").asText("");
        }
        return "";
    }

    private String extractResponsesText(JsonNode responseNode) {
        if (responseNode == null) {
            return "";
        }
        JsonNode outputText = responseNode.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText("");
        }
        JsonNode output = responseNode.path("output");
        if (!output.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                String type = part.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String name, String value) {
        if (isBlank(value)) {
            return;
        }
        sb.append('<').append(name).append('>').append('\n')
                .append(value.trim()).append('\n')
                .append("</").append(name).append('>').append('\n');
    }

    private String resolveBaseUrl(OpenAiClientConfig config) {
        return (config.getBaseUrl() == null || config.getBaseUrl().isBlank())
                ? "https://api.openai.com"
                : config.getBaseUrl().trim();
    }

    private ProxyTarget resolveProxy(OpenAiClientConfig config) {
        try {
            if (config.getProxyUrl() != null && !config.getProxyUrl().isBlank()) {
                URI uri = URI.create(config.getProxyUrl().trim());
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return new ProxyTarget(uri.getHost(), uri.getPort());
                }
            }
        } catch (Exception ignored) {
        }
        if (config.getProxyHost() != null && !config.getProxyHost().isBlank() && config.getProxyPort() != null) {
            return new ProxyTarget(config.getProxyHost().trim(), config.getProxyPort());
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String sanitizeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ProxyTarget(String host, int port) {
    }
}
