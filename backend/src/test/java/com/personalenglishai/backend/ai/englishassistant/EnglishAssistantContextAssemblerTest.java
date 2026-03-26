package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnglishAssistantContextAssemblerTest {

    @Test
    void assembleShouldPreferSelectedTextOverFullDraft() {
        EnglishAssistantContextAssembler assembler = new EnglishAssistantContextAssembler(
                new OpenAiEnglishAssistantClient("", new OpenAiClientConfig(), new ObjectMapper())
        );
        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setScope("current_draft");
        request.setTaskType("explain");
        request.setUseDraftContext(true);
        request.setSelectedText("This is the selected sentence.");
        request.setDraftText("Paragraph 1.\n\nParagraph 2.\n\nParagraph 3.");
        request.setMessage("这句为什么别扭");

        EnglishAssistantContextBundle bundle = assembler.assemble(request, new EnglishAssistantConversationState(null, null, null, null, null));

        assertThat(bundle.selectedText()).isEqualTo("This is the selected sentence.");
        assertThat(bundle.draftExcerpt()).isNull();
    }

    @Test
    void assembleShouldExtractLastParagraphFromAssistantOutput() {
        EnglishAssistantContextAssembler assembler = new EnglishAssistantContextAssembler(
                new OpenAiEnglishAssistantClient("", new OpenAiClientConfig(), new ObjectMapper())
        );
        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setScope("assistant_output");
        request.setTaskType("translate");
        request.setUseDraftContext(false);
        request.setAssistantOutputText("Paragraph 1.\n\nParagraph 2.\n\nIn conclusion, graduates can build fulfilling careers.");
        request.setMessage("翻译一下最后一段");

        EnglishAssistantContextBundle bundle = assembler.assemble(request, new EnglishAssistantConversationState(
                "resp-general",
                null,
                null,
                request.getAssistantOutputText(),
                null,
                "general",
                "resp-general",
                request.getAssistantOutputText(),
                "generate",
                List.of(new EnglishAssistantTurn("u1", "a1", "english_general", "ask")),
                List.of(),
                null,
                null,
                1,
                0,
                0,
                0
        ));

        assertThat(bundle.assistantOutputExcerpt()).isEqualTo("In conclusion, graduates can build fulfilling careers.");
    }

    @Test
    void assembleShouldCountTokensEarlierForLargeChineseContext() {
        OpenAiEnglishAssistantClient client = Mockito.mock(OpenAiEnglishAssistantClient.class);
        when(client.countInputTokens(Mockito.any())).thenReturn(3200);
        EnglishAssistantContextAssembler assembler = new EnglishAssistantContextAssembler(client);

        EnglishAssistantAnswerRequest request = new EnglishAssistantAnswerRequest();
        request.setScope("current_draft");
        request.setTaskType("evaluate");
        request.setUseDraftContext(true);
        request.setRubricKey("postgrad-exam-v1");
        request.setRubricSummary("评分维度".repeat(80));
        request.setAssignmentText("题目要求".repeat(120));
        request.setDraftText("这是中文作文内容。".repeat(260));
        request.setMessage("帮我看看这篇作文");

        EnglishAssistantContextBundle bundle = assembler.assemble(request, new EnglishAssistantConversationState(null, null, null, null, null));

        verify(client).countInputTokens(Mockito.any());
        assertThat(bundle.trimmedContextMode()).isNotEqualTo("full");
    }
}
