package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingExamPromptServiceImplTest {

    @Mock
    private WritingPromptSheetService writingPromptSheetService;

    @TempDir
    Path uploadRoot;

    @Test
    void generateShouldProxyPythonCanvasAgentAndPersistPromptSheet() {
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(101L);
        promptSheet.setPaper("ai-20260410-abc12345");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                webClientWithJson("""
                        {
                          "promptType": "chart",
                          "topic": "人工智能学习工具使用变化",
                          "promptText": "Write an essay based on the table below. In your essay, you should describe the changes, analyze the reasons, and give your comments.",
                          "requirements": "1) describe the changes 2) analyze the reasons 3) give your comments",
                          "genre": "议论文",
                          "wordRange": "160-200",
                          "maxScore": 20,
                          "sourceType": "ai_generated",
                          "taskType": "task1",
                          "chartSpec": {
                            "title": "College Students Using AI Study Tools",
                            "displayType": "table",
                            "columns": ["Year", "Usage Rate"],
                            "rows": [["2021", "18%"], ["2022", "31%"], ["2023", "47%"], ["2024", "63%"]],
                            "summary": "The usage rate rose steadily from 18% to 63%."
                          }
                        }
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput("请给我出一道图表题，内容是 2021-2024 年大学生使用 AI 学习工具的比例变化。");
        request.setStudyStage("postgrad");
        request.setTopic("人工智能学习工具使用变化");
        request.setPromptType("chart");
        request.setTaskType("task1");
        request.setRequirements("突出近四年变化趋势");
        request.setWordRange("160-200");
        request.setMaxScore(20);

        var response = service.generate(request);

        assertThat(response.getPromptType()).isEqualTo("chart");
        assertThat(response.getSourceType()).isEqualTo("ai_generated");
        assertThat(response.getTaskType()).isEqualTo("task1");
        assertThat(response.getRecommendedMaxWords()).isEqualTo(200);
        assertThat(response.getChartSpec()).isNotNull();
        assertThat(response.getChartSpec().getColumns()).containsExactly("Year", "Usage Rate");
        assertThat(response.getAttachmentType()).isEqualTo("visual");
        assertThat(response.getVisualKind()).isEqualTo("table");
        assertThat(response.getPromptSheetId()).isEqualTo(101L);
        assertThat(response.getPaper()).isEqualTo("ai-20260410-abc12345");
        verify(writingPromptSheetService).createGeneratedPromptSheet(eq(request), any());
    }

    @Test
    void generateShouldRenderChartPromptSheetImageBeforePersisting() {
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(202L);
        promptSheet.setPaper("ai-20260428-chartimg");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                webClientWithJson("""
                        {
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
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService,
                new PromptSheetChartImageService(uploadRoot, "/uploads")
        );

        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput("给我一篇中国 GDP 近 10 年增长的图表作文");
        request.setStudyStage("postgrad");
        request.setPromptType("chart");
        request.setTaskType("task2");
        request.setWordRange("160-200");

        var response = service.generate(request);

        ArgumentCaptor<GenerateExamPromptResponse> responseCaptor =
                ArgumentCaptor.forClass(GenerateExamPromptResponse.class);
        verify(writingPromptSheetService).createGeneratedPromptSheet(eq(request), responseCaptor.capture());
        assertThat(response.getAttachmentImageUrl()).startsWith("/uploads/prompt-sheets/charts/");
        assertThat(response.getVisualKind()).isEqualTo("image");
        assertThat(responseCaptor.getValue().getAttachmentImageUrl()).isEqualTo(response.getAttachmentImageUrl());
    }

    @Test
    void generateShouldDefaultMissingTaskTypeToTask1() {
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(303L);
        promptSheet.setPaper("ai-20260410-general1");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                webClientWithJson("""
                        {
                          "promptType": "general",
                          "topic": "青年责任",
                          "promptText": "Write an essay on the responsibilities of young people.",
                          "requirements": "give your comments",
                          "genre": "Essay",
                          "wordRange": "160-200",
                          "maxScore": 20,
                          "sourceType": "ai_generated"
                        }
                        """),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput("给我一题关于青年责任的英语作文");
        request.setStudyStage("postgrad");
        request.setTopic("青年责任");
        request.setPromptType("general");
        request.setWordRange("160-200");
        request.setMaxScore(20);

        var response = service.generate(request);

        assertThat(response.getTaskType()).isEqualTo("task1");
        assertThat(response.getAttachmentType()).isEqualTo("none");
        assertThat(response.getPromptSheetId()).isEqualTo(303L);
        assertThat(response.getPaper()).isEqualTo("ai-20260410-general1");
        verify(writingPromptSheetService).createGeneratedPromptSheet(eq(request), any());
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
