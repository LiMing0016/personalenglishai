package com.personalenglishai.backend.ai.englishassistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnglishAssistantSummaryServiceTest {

    @Test
    void shouldGenerateShouldTriggerAfterEightTurns() {
        EnglishAssistantSummaryService service = new EnglishAssistantSummaryService();
        EnglishAssistantConversationState state = new EnglishAssistantConversationState(
                null, null, null, null, null,
                null, null, null, null,
                List.of(), List.of(), null, null,
                8, 0, 0, 0
        );
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setMessage("继续帮我看这篇作文");
        EnglishAssistantRouterResult route = new EnglishAssistantRouterResult("english_general", "ask", false, null);

        assertThat(service.shouldGenerate("general", state, request, route, false)).isTrue();
    }

    @Test
    void buildSummaryShouldProduceFourStableSections() {
        EnglishAssistantSummaryService service = new EnglishAssistantSummaryService();
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setMessage("继续帮我改成更正式的表达");
        EnglishAssistantRouterResult route = new EnglishAssistantRouterResult("current_draft", "rewrite", true, null);
        EnglishAssistantAnswerRequest answerRequest = new EnglishAssistantAnswerRequest();
        answerRequest.setUseDraftContext(true);
        answerRequest.setRubricKey("postgrad-exam-v1");

        String summary = service.buildSummary(
                "draft",
                null,
                List.of(new EnglishAssistantTurn("上一轮问题", "上一轮回答", "current_draft", "rewrite")),
                request,
                route,
                answerRequest
        );

        assertThat(summary).contains("当前用户目标：");
        assertThat(summary).contains("已确认约束：");
        assertThat(summary).contains("已完成结果：");
        assertThat(summary).contains("待继续问题：");
    }
}
