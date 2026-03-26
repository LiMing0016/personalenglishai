package com.personalenglishai.backend.ai.englishassistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnglishAssistantContextAssembler {

    private static final int CHARACTER_COUNT_THRESHOLD = 3600;
    private static final int SOFT_INPUT_TOKEN_BUDGET = 2800;
    private static final int HARD_INPUT_TOKEN_BUDGET = 3600;
    private static final Pattern PARAGRAPH_INDEX_PATTERN = Pattern.compile("(第([一二三四五六七八九十0-9]+)段|paragraph\\s*(\\d+))", Pattern.CASE_INSENSITIVE);

    private final OpenAiEnglishAssistantClient client;

    public EnglishAssistantContextAssembler(OpenAiEnglishAssistantClient client) {
        this.client = client;
    }

    public EnglishAssistantContextBundle assemble(EnglishAssistantAnswerRequest request,
                                                  EnglishAssistantConversationState state) {
        String selectedText = normalize(request.getSelectedText());
        String assignmentText = normalize(request.getAssignmentText());
        String assistantOutputExcerpt = normalize(resolveAssistantOutputExcerpt(request));
        String draftExcerpt = normalize(resolveDraftExcerpt(request));
        String rubricSummary = normalize(request.getRubricSummary());
        String recentTurnsText = buildRecentTurnsText(state, request);
        String summaryText = normalize(request.getSummaryText());
        String trimmedMode = "full";

        Integer inputTokens = countTokens(request, assignmentText, selectedText, draftExcerpt, assistantOutputExcerpt,
                rubricSummary, recentTurnsText, summaryText, trimmedMode);
        boolean softExceeded = exceeds(inputTokens, SOFT_INPUT_TOKEN_BUDGET);
        boolean hardExceeded = exceeds(inputTokens, HARD_INPUT_TOKEN_BUDGET);

        if (softExceeded) {
            trimmedMode = "soft_trimmed";
            recentTurnsText = null;
            rubricSummary = minimalRubric(request.getRubricKey(), rubricSummary);
            assignmentText = shorten(assignmentText, 480);
            draftExcerpt = minimizeExcerpt(draftExcerpt);
            assistantOutputExcerpt = minimizeExcerpt(assistantOutputExcerpt);
            inputTokens = countTokens(request, assignmentText, selectedText, draftExcerpt, assistantOutputExcerpt,
                    rubricSummary, null, summaryText, trimmedMode);
            hardExceeded = exceeds(inputTokens, HARD_INPUT_TOKEN_BUDGET);
        }

        if (hardExceeded) {
            trimmedMode = "hard_trimmed";
            recentTurnsText = null;
            summaryText = null;
            rubricSummary = null;
            assignmentText = shorten(assignmentText, 240);
            draftExcerpt = minimizeExcerpt(draftExcerpt);
            assistantOutputExcerpt = minimizeExcerpt(assistantOutputExcerpt);
            inputTokens = countTokens(request, assignmentText, selectedText, draftExcerpt, assistantOutputExcerpt,
                    null, null, null, trimmedMode);
        }

        return new EnglishAssistantContextBundle(
                blankToNull(assignmentText),
                blankToNull(selectedText),
                blankToNull(draftExcerpt),
                blankToNull(assistantOutputExcerpt),
                blankToNull(rubricSummary),
                blankToNull(recentTurnsText),
                blankToNull(summaryText),
                trimmedMode,
                "soft_trimmed".equals(trimmedMode) || "hard_trimmed".equals(trimmedMode),
                "hard_trimmed".equals(trimmedMode),
                inputTokens
        );
    }

    private String resolveDraftExcerpt(EnglishAssistantAnswerRequest request) {
        if (!request.getUseDraftContext()) {
            return null;
        }
        if (!isBlank(request.getSelectedText())) {
            return null;
        }
        return excerptForMessage(request.getDraftText(), request.getMessage(), false);
    }

    private String resolveAssistantOutputExcerpt(EnglishAssistantAnswerRequest request) {
        if (isBlank(request.getAssistantOutputText())) {
            return null;
        }
        return excerptForMessage(request.getAssistantOutputText(), request.getMessage(), true);
    }

    private String excerptForMessage(String source, String message, boolean preferTail) {
        if (isBlank(source)) {
            return null;
        }
        List<String> paragraphs = splitParagraphs(source);
        if (paragraphs.isEmpty()) {
            return normalize(source);
        }

        String normalizedMessage = normalize(message).toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("最后一段")) {
            return joinWindow(paragraphs, paragraphs.size() - 1, preferTail ? 0 : 1);
        }

        Integer paragraphIndex = resolveParagraphIndex(normalizedMessage);
        if (paragraphIndex != null && paragraphIndex > 0 && paragraphIndex <= paragraphs.size()) {
            return joinWindow(paragraphs, paragraphIndex - 1, 1);
        }

        if (normalizedMessage.contains("最后一句")) {
            return sentenceWindow(source, Math.max(0, splitSentences(source).size() - 1));
        }

        if (preferTail) {
            return joinTail(paragraphs, 2);
        }
        return joinFallback(paragraphs);
    }

    private String buildRecentTurnsText(EnglishAssistantConversationState state, EnglishAssistantAnswerRequest request) {
        List<EnglishAssistantTurn> turns = selectRecentTurns(state, request);
        if (turns.isEmpty()) {
            return null;
        }
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            blocks.add(turns.get(i).toPromptBlock(i + 1));
        }
        return String.join("\n\n", blocks);
    }

    private List<EnglishAssistantTurn> selectRecentTurns(EnglishAssistantConversationState state,
                                                         EnglishAssistantAnswerRequest request) {
        if (state == null) {
            return List.of();
        }
        List<EnglishAssistantTurn> source;
        int limit;
        String scope = normalize(request.getScope());
        if ("current_draft".equals(scope)) {
            source = state.draftRecentTurns();
            limit = 1;
        } else if ("assistant_output".equals(scope)) {
            boolean usesDraftArtifact = "draft".equals(normalize(request.getArtifactChain()));
            source = usesDraftArtifact ? state.draftRecentTurns() : state.generalRecentTurns();
            limit = 2;
        } else if ("session_meta".equals(scope)) {
            source = state.generalRecentTurns();
            limit = 2;
        } else {
            source = state.generalRecentTurns();
            limit = 4;
        }
        if (source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - limit);
        return source.subList(from, source.size());
    }

    private Integer countTokens(EnglishAssistantAnswerRequest request,
                                String assignmentText,
                                String selectedText,
                                String draftExcerpt,
                                String assistantOutputExcerpt,
                                String rubricSummary,
                                String recentTurnsText,
                                String summaryText,
                                String trimmedMode) {
        EnglishAssistantAnswerRequest tokenRequest = copyForCount(request, assignmentText, selectedText, draftExcerpt,
                assistantOutputExcerpt, rubricSummary, recentTurnsText, summaryText, trimmedMode);
        int dynamicChars = totalChars(assignmentText, selectedText, draftExcerpt, assistantOutputExcerpt, rubricSummary,
                recentTurnsText, summaryText, request.getMessage());
        if (shouldCountPrecisely(request, dynamicChars, assignmentText, draftExcerpt, assistantOutputExcerpt,
                rubricSummary, recentTurnsText, summaryText)) {
            Integer counted = client.countInputTokens(tokenRequest);
            if (counted != null && counted > 0) {
                return counted;
            }
        }
        return estimateTokens(dynamicChars);
    }

    private boolean shouldCountPrecisely(EnglishAssistantAnswerRequest request,
                                         int dynamicChars,
                                         String assignmentText,
                                         String draftExcerpt,
                                         String assistantOutputExcerpt,
                                         String rubricSummary,
                                         String recentTurnsText,
                                         String summaryText) {
        if (dynamicChars >= CHARACTER_COUNT_THRESHOLD) {
            return true;
        }
        int heavySections = 0;
        heavySections += isBlank(assignmentText) ? 0 : 1;
        heavySections += isBlank(draftExcerpt) ? 0 : 1;
        heavySections += isBlank(assistantOutputExcerpt) ? 0 : 1;
        heavySections += isBlank(rubricSummary) ? 0 : 1;
        heavySections += isBlank(recentTurnsText) ? 0 : 1;
        heavySections += isBlank(summaryText) ? 0 : 1;
        return dynamicChars >= 2200
                && heavySections >= 3
                && ("current_draft".equals(normalize(request.getScope()))
                || "assistant_output".equals(normalize(request.getScope())));
    }

    private EnglishAssistantAnswerRequest copyForCount(EnglishAssistantAnswerRequest request,
                                                       String assignmentText,
                                                       String selectedText,
                                                       String draftExcerpt,
                                                       String assistantOutputExcerpt,
                                                       String rubricSummary,
                                                       String recentTurnsText,
                                                       String summaryText,
                                                       String trimmedMode) {
        EnglishAssistantAnswerRequest copy = new EnglishAssistantAnswerRequest();
        copy.setConversationId(request.getConversationId());
        copy.setScope(request.getScope());
        copy.setTaskType(request.getTaskType());
        copy.setUseDraftContext(request.getUseDraftContext());
        copy.setMessage(request.getMessage());
        copy.setAssignmentText(assignmentText);
        copy.setSelectedText(selectedText);
        copy.setDraftText(draftExcerpt);
        copy.setAssistantOutputText(assistantOutputExcerpt);
        copy.setRubricKey(request.getRubricKey());
        copy.setRubricSummary(rubricSummary);
        copy.setRecentTurnsText(recentTurnsText);
        copy.setSummaryText(summaryText);
        copy.setTrimmedContextMode(trimmedMode);
        copy.setArtifactChain(request.getArtifactChain());
        copy.setPreviousResponseId(request.getPreviousResponseId());
        copy.setPromptCacheKey(request.getPromptCacheKey());
        copy.setTraceId(request.getTraceId());
        return copy;
    }

    private int totalChars(String... values) {
        int sum = 0;
        for (String value : values) {
            if (value != null) {
                sum += value.length();
            }
        }
        return sum;
    }

    private int estimateTokens(int chars) {
        if (chars <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(chars / 2.2));
    }

    private boolean exceeds(Integer inputTokens, int limit) {
        return inputTokens != null && inputTokens > limit;
    }

    private String minimalRubric(String rubricKey, String rubricSummary) {
        if (isBlank(rubricKey) && isBlank(rubricSummary)) {
            return null;
        }
        String key = isBlank(rubricKey) ? "" : "rubric_key=" + rubricKey;
        String summary = shorten(rubricSummary, 120);
        return blankToNull((key + "\n" + summary).trim());
    }

    private String minimizeExcerpt(String excerpt) {
        return shorten(excerpt, 900);
    }

    private String joinFallback(List<String> paragraphs) {
        if (paragraphs.size() <= 3) {
            return String.join("\n\n", paragraphs);
        }
        List<String> result = new ArrayList<>();
        result.add(paragraphs.get(0));
        result.add(paragraphs.get(paragraphs.size() - 1));
        return String.join("\n\n", result);
    }

    private String joinTail(List<String> paragraphs, int count) {
        int from = Math.max(0, paragraphs.size() - count);
        return String.join("\n\n", paragraphs.subList(from, paragraphs.size()));
    }

    private String joinWindow(List<String> paragraphs, int centerIndex, int radius) {
        int from = Math.max(0, centerIndex - radius);
        int to = Math.min(paragraphs.size(), centerIndex + radius + 1);
        return String.join("\n\n", paragraphs.subList(from, to));
    }

    private Integer resolveParagraphIndex(String normalizedMessage) {
        Matcher matcher = PARAGRAPH_INDEX_PATTERN.matcher(normalizedMessage);
        if (!matcher.find()) {
            return null;
        }
        String zh = matcher.group(2);
        if (zh != null) {
            return chineseNumberToInt(zh);
        }
        String digit = matcher.group(3);
        if (digit != null && !digit.isBlank()) {
            return Integer.parseInt(digit);
        }
        return null;
    }

    private int chineseNumberToInt(String text) {
        return switch (text) {
            case "一", "1" -> 1;
            case "二", "2" -> 2;
            case "三", "3" -> 3;
            case "四", "4" -> 4;
            case "五", "5" -> 5;
            case "六", "6" -> 6;
            case "七", "7" -> 7;
            case "八", "8" -> 8;
            case "九", "9" -> 9;
            case "十", "10" -> 10;
            default -> 1;
        };
    }

    private String sentenceWindow(String source, int centerIndex) {
        List<String> sentences = splitSentences(source);
        if (sentences.isEmpty()) {
            return shorten(source, 900);
        }
        int from = Math.max(0, centerIndex - 1);
        int to = Math.min(sentences.size(), centerIndex + 2);
        return String.join(" ", sentences.subList(from, to));
    }

    private List<String> splitParagraphs(String source) {
        String normalized = normalize(source);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] blocks = normalized.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        for (String block : blocks) {
            String trimmed = normalize(block);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            result.add(normalized);
        }
        return result;
    }

    private List<String> splitSentences(String source) {
        String normalized = normalize(source);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] parts = normalized.split("(?<=[.!?。！？])\\s+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = normalize(part);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String shorten(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized.length() <= maxLength) {
            return blankToNull(normalized);
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
