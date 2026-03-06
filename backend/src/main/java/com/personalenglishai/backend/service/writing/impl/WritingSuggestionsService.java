package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.SuggestionsResponse;
import com.personalenglishai.backend.dto.writing.SuggestionsResponse.ErrorItem;
import com.personalenglishai.backend.dto.writing.SuggestionsResponse.SuggestionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GPT 复检硬性错误。
 */
@Service
public class WritingSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(WritingSuggestionsService.class);

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    /** 允许的硬性错误类型 */
    private static final Set<String> HARD_ERROR_TYPES = Set.of(
            "spelling", "subject_verb", "tense", "article", "plural",
            "countability", "comparative", "syntax", "part_of_speech"
    );

    private static final String SYSTEM_PROMPT = """
         You are an expert English grammar checker.

        Task:
        Find only objective grammar/spelling errors in the essay.

        ONLY output these hard error types:
        - spelling
        - subject_verb
        - tense
        - article
        - plural
        - countability
        - comparative
        - syntax
        - part_of_speech

        Strict definition of "error":
        An item is an error only if the original text is grammatically incorrect,
        misspelled, or uses a clearly wrong inflection/form in standard English.

        Do NOT report:
        - stylistic improvements
        - more natural phrasing
        - synonym replacements
        - conciseness improvements
        - tone/formality improvements
        - collocation preferences if the original is still acceptable English
        - full phrase rewrites when only one word is wrong
        - any change that is "better" but not strictly necessary

        Examples of NOT errors:
        - "a lot of problems" -> "many problems"
        - "less exercise" vs "fewer sports" style substitutions
        - replacing an acceptable phrase with a more natural one
        - changing articles unless the original article is clearly wrong or missing

        Rules:
        - "original" MUST be an exact substring from the essay
        - "suggestion" must be the minimal correction, changing as few words as possible
        - preserve the original meaning
        - if multiple words are wrong, only fix the words required for correctness
        - "reason" must be Chinese, under 30 chars
        - do NOT output markdown or explanations outside JSON

        Output JSON only:
        {"errors":[{"id":"e1","type":"<hard_type>","severity":"major","original":"exact text","suggestion":"fix","reason":"中文原因"}],"suggestions":[]}

        If no hard errors remain:
        {"errors":[],"suggestions":[]}
        """;

    public WritingSuggestionsService(OpenAiClient openAiClient,
                                     ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public SuggestionsResponse analyze(String essayText) {
        if (essayText == null || essayText.isBlank()) {
            return new SuggestionsResponse(List.of(), List.of());
        }

        String trimmed = essayText.trim();

        List<ErrorItem> errors = callGptForErrors(trimmed);
        List<SuggestionItem> suggestions = List.of();

        log.info("AI suggestions done. gptErrors={} suggestions={}", errors.size(), suggestions.size());
        return new SuggestionsResponse(errors, suggestions);
    }

    // ════════════════════════════════════════════════════════════════
    //  GPT 硬性错误复检
    // ════════════════════════════════════════════════════════════════

    private List<ErrorItem> callGptForErrors(String essayText) {
        try {
            long start = System.currentTimeMillis();
            String userPrompt = "Essay to analyze:\n\n" + essayText;
            String raw = openAiClient.callWithTraceId(SYSTEM_PROMPT, userPrompt, "suggestions",
                    0.3, 1024);
            long elapsed = System.currentTimeMillis() - start;
            List<ErrorItem> errors = parseGptResponse(raw, essayText);
            log.info("GPT error recheck done. errors={} elapsed={}ms", errors.size(), elapsed);
            return errors;
        } catch (Exception e) {
            log.warn("GPT error recheck failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ErrorItem> parseGptResponse(String raw, String essayText) {
        if (raw == null || raw.isBlank()) return List.of();

        try {
            String cleaned = raw.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").strip();
            }

            Matcher matcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(cleaned);
            if (!matcher.find()) return List.of();

            JsonNode root = objectMapper.readTree(matcher.group());
            JsonNode arr = root.path("errors");
            if (!arr.isArray()) return List.of();

            List<ErrorItem> items = new ArrayList<>();
            int idx = 1;
            for (JsonNode node : arr) {
                String original = node.path("original").asText("").trim();
                String suggestion = node.path("suggestion").asText("").trim();
                String type = node.path("type").asText("");
                String severity = node.path("severity").asText("major");
                String reason = node.path("reason").asText("");

                if (original.isEmpty() || suggestion.isEmpty()) continue;
                if (original.equals(suggestion)) continue;
                if (!essayText.contains(original)) continue;
                if (!HARD_ERROR_TYPES.contains(type)) continue;

                ErrorItem item = new ErrorItem();
                item.setId("ge" + idx++);
                item.setType(type);
                item.setSeverity(severity);
                item.setOriginal(original);
                item.setSuggestion(suggestion);
                item.setReason(reason);
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("Failed to parse GPT response: {}", e.getMessage());
            return List.of();
        }
    }

}
