package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.dto.writing.PromptSheetChatRequest;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptSheetChatServiceImplTest {

    @Mock
    private WritingPromptSheetService writingPromptSheetService;

    @TempDir
    Path uploadRoot;

    @Test
    void chatOnlyShouldProxyPythonResponseWithoutPersistingCanvas() {
        PromptSheetChatServiceImpl service = new PromptSheetChatServiceImpl(
                webClientWithJson("""
                        {
                          "reply": "这个主题适合 IELTS Task 2，因为可以形成双方观点。",
                          "action": "chat_only",
                          "needsCanvasUpdate": false,
                          "needsConfirmation": false,
                          "canvasInstruction": null,
                          "patch": null
                        }
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        PromptSheetChatRequest request = new PromptSheetChatRequest();
        request.setMessage("这个题适合雅思吗？");
        request.setStudyStage("ielts");
        request.setTaskType("task2");
        request.setHasCanvas(true);

        var response = service.chat(request);

        assertThat(response.getReply()).contains("适合");
        assertThat(response.getAction()).isEqualTo("chat_only");
        assertThat(response.getNeedsCanvasUpdate()).isFalse();
        assertThat(response.getPromptSheet()).isNull();
        verify(writingPromptSheetService, never()).createGeneratedPromptSheet(any(), any());
    }

    @Test
    void canvasUpdateShouldPersistEmbeddedPromptSheetReturnedByPython() {
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(88L);
        promptSheet.setPaper("ai-20260426-test");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        PromptSheetChatServiceImpl service = new PromptSheetChatServiceImpl(
                webClientWithJson("""
                        {
                          "reply": "我已经把右侧题单改成 IELTS Task 2 环保主题。",
                          "action": "update_prompt_sheet",
                          "needsCanvasUpdate": true,
                          "needsConfirmation": false,
                          "canvasInstruction": "改成环保主题 Task 2。",
                          "promptSheet": {
                            "promptType": "general",
                            "topic": "Environmental Responsibility",
                            "promptText": "Some people believe individuals should protect the environment, while others think governments should take the main responsibility. Discuss both views and give your own opinion.",
                            "requirements": "Discuss both views and give your own opinion.",
                            "genre": "argumentative essay",
                            "wordRange": "250+",
                            "maxScore": 100,
                            "sourceType": "ai_generated",
                            "taskType": "task2"
                          }
                        }
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        PromptSheetChatRequest request = new PromptSheetChatRequest();
        request.setMessage("把主题换成环保");
        request.setStudyStage("ielts");
        request.setTaskType("task2");
        request.setHasCanvas(true);

        var response = service.chat(request);

        assertThat(response.getNeedsCanvasUpdate()).isTrue();
        assertThat(response.getPromptSheet()).isNotNull();
        assertThat(response.getPromptSheet().getPromptSheetId()).isEqualTo(88L);
        assertThat(response.getPromptSheet().getPaper()).isEqualTo("ai-20260426-test");
        assertThat(response.getPromptSheet().getAttachmentType()).isEqualTo("none");
        assertThat(response.getPromptSheet().getMinWords()).isEqualTo(250);
        verify(writingPromptSheetService).createGeneratedPromptSheet(any(), any());
    }

    @Test
    void canvasUpdateShouldRenderChartImageBeforePersistingEmbeddedPromptSheet() {
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(89L);
        promptSheet.setPaper("ai-20260428-chat-chart");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        PromptSheetChatServiceImpl service = new PromptSheetChatServiceImpl(
                webClientWithJson("""
                        {
                          "reply": "我已经把右侧题单改成考研图表题。",
                          "action": "update_prompt_sheet",
                          "needsCanvasUpdate": true,
                          "needsConfirmation": false,
                          "canvasInstruction": "生成 GDP 图表题。",
                          "promptSheet": {
                            "promptType": "chart",
                            "topic": "GDP 总量与增速（2014-2023）",
                            "promptText": "The chart shows changes in GDP total and growth rate from 2014 to 2023.",
                            "requirements": "Describe the trend and comment on its significance.",
                            "genre": "chart",
                            "wordRange": "160-200",
                            "sourceType": "ai_generated",
                            "taskType": "task2",
                            "chartSpec": {
                              "title": "中国近 10 年 GDP 增长情况",
                              "displayType": "chart",
                              "columns": ["Year", "GDP (trillion yuan)", "Growth Rate (%)"],
                              "rows": [["2014", "63.6", "7.3%"], ["2015", "67.7", "6.9%"], ["2016", "74.0", "6.7%"], ["2017", "82.1", "6.9%"], ["2018", "90.0", "6.7%"], ["2019", "99.1", "6.0%"], ["2020", "101.6", "2.2%"], ["2021", "114.4", "8.4%"], ["2022", "120.5", "3.0%"], ["2023", "126.1", "5.2%"]],
                              "summary": "GDP rose overall while growth rate fluctuated."
                            }
                          }
                        }
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService,
                new PromptSheetChartImageService(uploadRoot, "/uploads")
        );

        PromptSheetChatRequest request = new PromptSheetChatRequest();
        request.setMessage("把右边改成 GDP 图表作文");
        request.setStudyStage("postgrad");
        request.setPromptType("chart");
        request.setTaskType("task2");
        request.setHasCanvas(true);

        var response = service.chat(request);

        ArgumentCaptor<GenerateExamPromptResponse> responseCaptor =
                ArgumentCaptor.forClass(GenerateExamPromptResponse.class);
        verify(writingPromptSheetService).createGeneratedPromptSheet(any(), responseCaptor.capture());
        assertThat(response.getPromptSheet().getAttachmentImageUrl()).startsWith("/uploads/prompt-sheets/charts/");
        assertThat(response.getPromptSheet().getVisualKind()).isEqualTo("image");
        assertThat(responseCaptor.getValue().getAttachmentImageUrl()).isEqualTo(response.getPromptSheet().getAttachmentImageUrl());
    }

    private WebClient webClientWithJson(String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }
}
