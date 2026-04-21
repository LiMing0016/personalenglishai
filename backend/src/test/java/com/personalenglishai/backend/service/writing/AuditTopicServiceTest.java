package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AuditTopicServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    @Test
    void shouldKeepOriginalTopicWhenModelReturnsGenericSummary() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Write an essay based on the following drawing. In your essay, you should first describe the drawing, then interpret its meaning, and give your comment on it.");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {"status":"complete","topic":"根据所给图表写一篇作文","promptType":"comic","genre":"看图作文","wordRange":"160-200","requirements":"first describe the drawing, then interpret its meaning, and give your comment on it.","message":null}
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getTopic()).isEqualTo(request.getTopic());
        assertThat(response.getGenre()).isEqualTo("看图作文");
        assertThat(response.getWordRange()).isEqualTo("160-200");
        assertThat(response.getPromptType()).isEqualTo("comic");
    }

    @Test
    void shouldFallbackWhenModelReturnsSchemaBreakingPromptType() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Please design a chart-based writing task about online shopping.");
        request.setGenre("议论文");
        request.setWordRange("120-150");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {"status":"complete","topic":"Online shopping","promptType":"diagram","genre":"article","wordRange":"120-150","requirements":"compare the figures","message":null}
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getStatus()).isEqualTo("complete");
        assertThat(response.getTopic()).isEqualTo(request.getTopic());
        assertThat(response.getPromptType()).isEqualTo("chart");
        assertThat(response.getGenre()).isEqualTo("议论文");
        assertThat(response.getWordRange()).isEqualTo("120-150");
        assertThat(response.getRequirements()).isNull();
    }

    @Test
    void shouldAcceptNeedMoreInfoWhenStructuredOutputMatchesSchema() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Write about the role of AI in education.");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {
                          "status":"need_more_info",
                          "topic":"Write about the role of AI in education.",
                          "promptType":"general",
                          "genre":"议论文",
                          "wordRange":null,
                          "requirements":"discuss the benefits and risks",
                          "message":"还缺少字数范围，请补充后我再整理题单。"
                        }
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getStatus()).isEqualTo("need_more_info");
        assertThat(response.getTopic()).isEqualTo("Write about the role of AI in education.");
        assertThat(response.getPromptType()).isEqualTo("general");
        assertThat(response.getGenre()).isEqualTo("议论文");
        assertThat(response.getWordRange()).isNull();
        assertThat(response.getRequirements()).isEqualTo("discuss the benefits and risks");
        assertThat(response.getMessage()).isEqualTo("还缺少字数范围，请补充后我再整理题单。");
    }

    @Test
    void shouldParseExtendedStructuredFieldsWhenOutputMatchesSchema() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Write an essay based on the picture below.");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {
                          "status":"complete",
                          "topic":"Write an essay based on the picture below.",
                          "promptType":"comic",
                          "genre":"看图作文",
                          "wordRange":"160-200",
                          "requirements":"describe the picture, interpret its meaning, and give your comments",
                          "message":null,
                          "isCompleteOriginalPrompt":true,
                          "shouldPreserveOriginalWording":true,
                          "isExamStyleCompatible":true,
                          "styleCompatibilityReasons":["题干、要求和字数范围齐全"],
                          "missingFields":[],
                          "requiresUserConfirmation":false,
                          "confirmationQuestion":null
                        }
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getIsCompleteOriginalPrompt()).isTrue();
        assertThat(response.getShouldPreserveOriginalWording()).isTrue();
        assertThat(response.getIsExamStyleCompatible()).isTrue();
        assertThat(response.getStyleCompatibilityReasons()).containsExactly("题干、要求和字数范围齐全");
        assertThat(response.getMissingFields()).isEmpty();
        assertThat(response.getRequiresUserConfirmation()).isFalse();
        assertThat(response.getConfirmationQuestion()).isNull();
    }

    @Test
    void shouldFallbackWhenExtendedFieldsBreakSchema() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Write an essay based on the picture below.");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {
                          "status":"complete",
                          "topic":"Write an essay based on the picture below.",
                          "promptType":"comic",
                          "genre":"看图作文",
                          "wordRange":"160-200",
                          "requirements":"describe the picture",
                          "message":null,
                          "isCompleteOriginalPrompt":"yes",
                          "shouldPreserveOriginalWording":true,
                          "isExamStyleCompatible":true,
                          "styleCompatibilityReasons":["题干完整"],
                          "missingFields":[],
                          "requiresUserConfirmation":false,
                          "confirmationQuestion":null
                        }
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getTopic()).isEqualTo(request.getTopic());
        assertThat(response.getPromptType()).isEqualTo("comic");
        assertThat(response.getIsCompleteOriginalPrompt()).isNull();
        assertThat(response.getStyleCompatibilityReasons()).isNull();
        assertThat(response.getMissingFields()).isNull();
        assertThat(response.getRequiresUserConfirmation()).isNull();
    }

    @Test
    void shouldParseSingleAgentSchemaForComicPrompt() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("给我来一道考试风格的图画作文");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {
                          "status":"need_more_info",
                          "topic":"",
                          "promptType":"comic",
                          "genre":"",
                          "wordRange":"",
                          "requirements":"",
                          "message":"还需要确认主题方向后再生成题单。",
                          "targetStyle":"exam",
                          "needsMoreInfo":true,
                          "assistantReply":"可以，我会按考试风格帮你整理图画作文。常见方向有成长选择、社会现象和责任意识。你想让我直接出一道题，还是先给你几个主题方向？",
                          "promptReady":false,
                          "readyReason":"题型和风格已明确，但主题方向尚未确定。",
                          "nextAction":"ask_user",
                          "needsAttachment":true,
                          "attachmentType":"image",
                          "attachmentSource":"agent_generate",
                          "attachmentReady":false,
                          "attachmentTitle":"图画作文配图",
                          "attachmentInstruction":"待确定主题后生成黑白考试漫画。",
                          "attachmentPayload":{
                            "imagePrompt":"黑白考试漫画，表现年轻人在责任与娱乐之间做选择",
                            "imageStyle":"black_white_exam_cartoon"
                          },
                          "isCompleteOriginalPrompt":false,
                          "shouldPreserveOriginalWording":false,
                          "isExamStyleCompatible":false,
                          "styleCompatibilityReasons":["缺少主题方向"],
                          "missingFields":["topic"],
                          "requiresUserConfirmation":false,
                          "confirmationQuestion":null
                        }
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getPromptType()).isEqualTo("comic");
        assertThat(response.getTargetStyle()).isEqualTo("exam");
        assertThat(response.getNeedsMoreInfo()).isTrue();
        assertThat(response.getAssistantReply()).contains("考试风格");
        assertThat(response.getPromptReady()).isFalse();
        assertThat(response.getNextAction()).isEqualTo("ask_user");
        assertThat(response.getNeedsAttachment()).isTrue();
        assertThat(response.getAttachmentType()).isEqualTo("image");
        assertThat(response.getAttachmentSource()).isEqualTo("agent_generate");
        assertThat(response.getAttachmentReady()).isFalse();
        assertThat(response.getAttachmentPayload()).containsEntry("imageStyle", "black_white_exam_cartoon");
    }

    @Test
    void shouldInjectStudyStageIntoAuditPromptSemantics() {
        AuditTopicService service = new AuditTopicService(openAiClient);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("我想写一篇关于大学生考研境遇的作文");
        request.setStudyStage("postgrad");

        when(openAiClient.callWithProvider(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("""
                        {
                          "status":"need_more_info",
                          "topic":"",
                          "promptType":"general",
                          "genre":"",
                          "wordRange":"",
                          "requirements":"",
                          "message":"还缺少字数范围。",
                          "targetStyle":"exam",
                          "needsMoreInfo":true,
                          "assistantReply":"我会按考研英语风格继续帮你整理。",
                          "promptReady":false,
                          "readyReason":"还缺字数。",
                          "nextAction":"ask_user",
                          "needsAttachment":false,
                          "attachmentType":"none",
                          "attachmentSource":"none",
                          "attachmentReady":true,
                          "attachmentTitle":"",
                          "attachmentInstruction":"",
                          "attachmentPayload":{},
                          "isCompleteOriginalPrompt":false,
                          "shouldPreserveOriginalWording":false,
                          "isExamStyleCompatible":false,
                          "styleCompatibilityReasons":[],
                          "missingFields":["wordRange"],
                          "requiresUserConfirmation":false,
                          "confirmationQuestion":null
                        }
                        """);

        service.audit(request);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(any(), systemPromptCaptor.capture(), userPromptCaptor.capture(), anyString(), any(), any());

        assertThat(systemPromptCaptor.getValue()).contains("如果提供了当前学段");
        assertThat(userPromptCaptor.getValue()).contains("当前学段（硬约束，必须按该学段考试风格整理）：postgrad");
    }
}
