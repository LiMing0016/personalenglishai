package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import com.personalenglishai.backend.dto.writing.ExamWorkbenchMessageDto;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnResponse;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.service.writing.impl.WritingExamDialogueServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingExamDialogueServiceTest {

    @Mock
    private AuditTopicService auditTopicService;

    @Mock
    private WritingExamPromptService writingExamPromptService;

    @Test
    void generateTurn_marksDraftWhenWordRangeIsMissing() {
        WritingExamDialogueServiceImpl service = new WritingExamDialogueServiceImpl(
                auditTopicService,
                writingExamPromptService
        );

        when(auditTopicService.audit(any(AuditTopicRequest.class), eq("openai")))
                .thenReturn(AuditTopicResponse.needMoreInfo(
                        "Write an essay based on the picture below.",
                        "comic",
                        "看图作文",
                        null,
                        "1) describe the picture briefly 2) interpret the meaning 3) give your comments",
                        "请补充字数范围"
                ));

        GenerateExamPromptResponse promptDraft = new GenerateExamPromptResponse();
        promptDraft.setPromptType("comic");
        promptDraft.setTopic("Write an essay based on the picture below.");
        promptDraft.setPromptText("Write an essay based on the picture below.");
        promptDraft.setRequirements("1) describe the picture briefly 2) interpret the meaning 3) give your comments");
        when(writingExamPromptService.generate(any())).thenReturn(promptDraft);

        GenerateExamDialogueTurnResponse response = service.generateTurn(1L, requestWithSingleUserMessage());

        assertThat(response.getPreviewStatus()).isEqualTo("draft");
        assertThat(response.getMissingFields()).contains("待补充字数");
        assertThat(response.getAssistantReplyBlocks()).hasSize(3);
        assertThat(response.getPromptSheetDraft()).isNotNull();
        assertThat(response.getPromptSheetDraft().getPromptType()).isEqualTo("comic");

        ArgumentCaptor<AuditTopicRequest> auditCaptor = ArgumentCaptor.forClass(AuditTopicRequest.class);
        verify(auditTopicService).audit(auditCaptor.capture(), eq("openai"));
        assertThat(auditCaptor.getValue().getTopic()).contains("不要改图片原题");
    }

    @Test
    void generateTurn_prefersSingleAgentAssistantReplyAndAttachmentMetadata() {
        WritingExamDialogueServiceImpl service = new WritingExamDialogueServiceImpl(
                auditTopicService,
                writingExamPromptService
        );

        AuditTopicResponse auditResponse = AuditTopicResponse.needMoreInfo(
                "",
                "comic",
                "",
                "",
                null,
                "请先确认主题方向。"
        );
        auditResponse.setAssistantReply("可以，我先按考试风格给你整理图画作文方向。你想让我直接出一道题，还是先给你两个主题方向？");
        auditResponse.setTargetStyle("exam");
        auditResponse.setNeedsMoreInfo(true);
        auditResponse.setPromptReady(false);
        auditResponse.setReadyReason("缺少主题方向");
        auditResponse.setNextAction("generate_attachment");
        auditResponse.setNeedsAttachment(true);
        auditResponse.setAttachmentType("image");
        auditResponse.setAttachmentSource("agent_generate");
        auditResponse.setAttachmentReady(false);
        auditResponse.setAttachmentTitle("图画作文配图");
        auditResponse.setAttachmentInstruction("待主题明确后生成黑白考试漫画。");
        auditResponse.setAttachmentPayload(java.util.Map.of(
                "imagePrompt", "黑白考试漫画，表现年轻人在责任与娱乐之间做选择",
                "imageStyle", "black_white_exam_cartoon"
        ));
        auditResponse.setMissingFields(List.of("topic"));

        when(auditTopicService.audit(any(AuditTopicRequest.class), eq("openai")))
                .thenReturn(auditResponse);

        GenerateExamPromptResponse promptDraft = new GenerateExamPromptResponse();
        promptDraft.setPromptType("comic");
        promptDraft.setTopic("请围绕责任与选择写一篇作文。");
        promptDraft.setPromptText("请围绕责任与选择写一篇作文。");
        when(writingExamPromptService.generate(any())).thenReturn(promptDraft);

        GenerateExamDialogueTurnResponse response = service.generateTurn(1L, requestWithSingleUserMessage());

        assertThat(response.getAssistantReply()).contains("图画作文方向");
        assertThat(response.getAssistantReplyBlocks()).hasSize(1);
        assertThat(response.getAssistantReplyBlocks().get(0).getText()).contains("图画作文方向");
        assertThat(response.getPreviewStatus()).isEqualTo("draft");
        assertThat(response.getMissingFields()).containsExactly("topic");
        assertThat(response.getPromptSheetDraft()).isNotNull();
        assertThat(response.getPromptSheetDraft().getAttachmentType()).isEqualTo("visual");
        assertThat(response.getPromptSheetDraft().getVisualKind()).isEqualTo("comic");
        assertThat(response.getPromptSheetDraft().getAttachmentSource()).isEqualTo("agent_generate");
        assertThat(response.getPromptSheetDraft().getAttachmentTitle()).isEqualTo("图画作文配图");
        assertThat(response.getPromptSheetDraft().getAttachmentContent()).contains("责任与娱乐");
    }

    @Test
    void generateTurn_onlyUsesLatestUserTextWhenBuildingOriginalInput() {
        WritingExamDialogueServiceImpl service = new WritingExamDialogueServiceImpl(
                auditTopicService,
                writingExamPromptService
        );

        when(auditTopicService.audit(any(AuditTopicRequest.class), eq("openai")))
                .thenReturn(AuditTopicResponse.needMoreInfo(
                        "",
                        "comic",
                        "",
                        "",
                        null,
                        "请先明确主题。"
                ));
        when(writingExamPromptService.generate(any())).thenReturn(new GenerateExamPromptResponse());

        GenerateExamDialogueTurnRequest request = new GenerateExamDialogueTurnRequest();
        request.setStudyStage("postgrad");
        request.setAiProvider("openai");
        request.setSelectedMode("exam");

        ExamWorkbenchMessageDto first = new ExamWorkbenchMessageDto();
        first.setRole("user");
        first.setKind("text");
        first.setText("我想写一篇关于大学生考研赛境的作文");

        ExamWorkbenchMessageDto second = new ExamWorkbenchMessageDto();
        second.setRole("user");
        second.setKind("text");
        second.setText("我要的是图画作文，你给我配一张图");

        ExamWorkbenchMessageDto image = new ExamWorkbenchMessageDto();
        image.setRole("user");
        image.setKind("asset");
        image.setAssetType("image");
        image.setAssetSummary("已添加图片附件，请优先保留原图命题场景。");

        request.setMessages(List.of(first, second, image));

        service.generateTurn(1L, request);

        ArgumentCaptor<AuditTopicRequest> auditCaptor = ArgumentCaptor.forClass(AuditTopicRequest.class);
        verify(auditTopicService).audit(auditCaptor.capture(), eq("openai"));
        assertThat(auditCaptor.getValue().getTopic())
                .contains("我要的是图画作文，你给我配一张图")
                .contains("[image] 已添加图片附件，请优先保留原图命题场景。")
                .doesNotContain("我想写一篇关于大学生考研赛境的作文");
    }

    private GenerateExamDialogueTurnRequest requestWithSingleUserMessage() {
        GenerateExamDialogueTurnRequest request = new GenerateExamDialogueTurnRequest();
        request.setStudyStage("postgrad");
        request.setAiProvider("openai");
        request.setSelectedMode("exam");

        ExamWorkbenchMessageDto message = new ExamWorkbenchMessageDto();
        message.setRole("user");
        message.setKind("text");
        message.setText("不要改图片原题");
        request.setMessages(List.of(message));
        return request;
    }
}
