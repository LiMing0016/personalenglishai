package com.personalenglishai.backend.ai.englishassistant;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnglishAssistantSummaryService {

    public boolean shouldGenerate(String chain,
                                  EnglishAssistantConversationState state,
                                  EnglishAssistantChatRequest request,
                                  EnglishAssistantRouterResult route,
                                  boolean softLimitExceeded) {
        if ("assistant_output".equals(route.scope())) {
            return false;
        }
        if (isLocalOperation(request, route)) {
            return false;
        }
        int turnCount = "draft".equals(chain) ? state.draftTurnCount() : state.generalTurnCount();
        int softOverflowCount = "draft".equals(chain) ? state.draftSoftOverflowCount() : state.generalSoftOverflowCount();
        return turnCount >= 8 || (softLimitExceeded && softOverflowCount >= 1);
    }

    public String buildSummary(String chain,
                               String existingSummary,
                               List<EnglishAssistantTurn> turns,
                               EnglishAssistantChatRequest request,
                               EnglishAssistantRouterResult route,
                               EnglishAssistantAnswerRequest answerRequest) {
        String currentGoal = "当前用户目标：" + compact(request == null ? null : request.getMessage(), 72);
        String constraints = "已确认约束：" + compact(buildConstraints(chain, route, answerRequest), 72);
        String completed = "已完成结果：" + compact(buildCompleted(existingSummary, turns), 72);
        String pending = "待继续问题：" + compact(resolvePending(request, route), 72);
        return compact(String.join("\n", currentGoal, constraints, completed, pending), 280);
    }

    private String buildConstraints(String chain,
                                    EnglishAssistantRouterResult route,
                                    EnglishAssistantAnswerRequest answerRequest) {
        StringBuilder sb = new StringBuilder();
        sb.append("chain=").append(chain);
        if (route != null && route.scope() != null) {
            sb.append("，scope=").append(route.scope());
        }
        if (answerRequest != null && answerRequest.getUseDraftContext()) {
            sb.append("，引用当前作文");
        }
        if (answerRequest != null && answerRequest.getRubricKey() != null) {
            sb.append("，按 ").append(answerRequest.getRubricKey());
        }
        return sb.toString();
    }

    private String buildCompleted(String existingSummary, List<EnglishAssistantTurn> turns) {
        if (turns != null && !turns.isEmpty()) {
            EnglishAssistantTurn latest = turns.get(turns.size() - 1);
            if (!latest.assistantMessage().isBlank()) {
                return latest.assistantMessage();
            }
        }
        if (existingSummary != null && !existingSummary.isBlank()) {
            return existingSummary;
        }
        return "暂无长程摘要，保留最近轮次继续衔接。";
    }

    private String resolvePending(EnglishAssistantChatRequest request, EnglishAssistantRouterResult route) {
        String taskType = route == null ? "ask" : route.taskType();
        return switch (taskType == null ? "" : taskType) {
            case "translate" -> "继续围绕当前目标文本做翻译或解释。";
            case "rewrite", "polish" -> "继续输出可直接应用的改写结果。";
            case "evaluate" -> "继续围绕当前标准点评并指出改进点。";
            default -> compact(request == null ? null : request.getMessage(), 72);
        };
    }

    private boolean isLocalOperation(EnglishAssistantChatRequest request, EnglishAssistantRouterResult route) {
        if (request != null && request.getSelectedText() != null && !request.getSelectedText().isBlank()) {
            return true;
        }
        String scope = route == null ? "" : route.scope();
        if ("assistant_output".equals(scope)) {
            String message = request == null ? "" : request.getMessage();
            return containsAny(message, "最后一段", "最后一句", "上一段", "这段", "这句");
        }
        return false;
    }

    private boolean containsAny(String message, String... tokens) {
        String normalized = message == null ? "" : message.trim();
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String compact(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, limit - 1)) + "…";
    }
}
