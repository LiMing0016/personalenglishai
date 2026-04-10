package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.entity.EssayPrompt;
import com.personalenglishai.backend.service.writing.EssayPromptService;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingExamPromptServiceImplTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private EssayPromptService essayPromptService;

    @Mock
    private WritingPromptSheetService writingPromptSheetService;

    @Test
    void generateShouldParseChartPromptResponseAndIncludeStageReferences() {
        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                openAiClient,
                essayPromptService,
                new ObjectMapper(),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        EssayPrompt sample = new EssayPrompt();
        sample.setPromptText("Write an essay based on the chart below and give your comments.");
        sample.setMaterialText("2021-2024 college students using AI tools.");
        when(essayPromptService.listByStage(4)).thenReturn(List.of(sample));
        when(openAiClient.callWithProvider(nullable(String.class), anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "promptType": "chart",
                          "topic": "人工智能学习工具使用变化",
                          "promptText": "Write an essay based on the table below. In your essay, you should describe the changes, analyze the reasons, and give your comments.",
                          "requirements": "1) describe the changes 2) analyze the reasons 3) give your comments",
                          "genre": "议论文",
                          "wordRange": "160-200",
                          "maxScore": 20,
                          "chartSpec": {
                            "title": "College Students Using AI Study Tools",
                            "displayType": "table",
                            "columns": ["Year", "Usage Rate"],
                            "rows": [["2021", "18%"], ["2022", "31%"], ["2023", "47%"], ["2024", "63%"]],
                            "summary": "The usage rate rose steadily from 18% to 63%."
                          }
                        }
                        """);
        when(openAiClient.generateImageWithProvider(nullable(String.class), anyString(), anyString()))
                .thenReturn("data:image/png;base64,chart-image");
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(101L);
        promptSheet.setPaper("ai-20260410-abc12345");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);
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
        assertThat(response.getChartSpec().getRows()).hasSize(4);
        assertThat(response.getComicScenes()).isEmpty();
        assertThat(response.getAttachmentImageUrl()).isEqualTo("data:image/png;base64,chart-image");
        assertThat(response.getPromptSheetId()).isEqualTo(101L);
        assertThat(response.getPaper()).isEqualTo("ai-20260410-abc12345");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(
                nullable(String.class),
                systemPromptCaptor.capture(),
                userPromptCaptor.capture(),
                anyString(),
                anyDouble(),
                anyInt()
        );
        assertThat(systemPromptCaptor.getValue())
                .contains("你是一位英语考试命题助手")
                .contains("只生成 1 道题")
                .contains("除 JSON 外不要输出任何其他内容。");
        assertThat(userPromptCaptor.getValue())
                .contains("study_stage=postgrad")
                .contains("requested_prompt_type=chart")
                .contains("2021-2024")
                .contains("Write an essay based on the chart below and give your comments.")
                .contains("2021-2024 college students using AI tools.");
        verify(openAiClient).generateImageWithProvider(nullable(String.class), contains("College Students Using AI Study Tools"), anyString());
        verify(writingPromptSheetService).createGeneratedPromptSheet(eq(request), any());
    }

    @Test
    void generateShouldNotRequestImageForGeneralPrompt() {
        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                openAiClient,
                essayPromptService,
                new ObjectMapper(),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        when(essayPromptService.listByStage(4)).thenReturn(List.of());
        when(openAiClient.callWithProvider(nullable(String.class), anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "promptType": "general",
                          "topic": "青年责任",
                          "promptText": "Write an essay on the responsibilities of young people.",
                          "requirements": "give your comments",
                          "genre": "Essay",
                          "wordRange": "160-200",
                          "maxScore": 20
                        }
                        """);
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(303L);
        promptSheet.setPaper("ai-20260410-general1");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput("给我一题关于青年责任的英语作文");
        request.setStudyStage("postgrad");
        request.setTopic("青年责任");
        request.setPromptType("general");
        request.setWordRange("160-200");
        request.setMaxScore(20);

        var response = service.generate(request);

        assertThat(response.getAttachmentImageUrl()).isNull();
        assertThat(response.getPromptSheetId()).isEqualTo(303L);
        assertThat(response.getPaper()).isEqualTo("ai-20260410-general1");
        verify(openAiClient, never()).generateImageWithProvider(nullable(String.class), anyString(), anyString());
        verify(writingPromptSheetService).createGeneratedPromptSheet(eq(request), any());
    }

    @Test
    void generateShouldNotRequestImageForComicPrompt() {
        WritingExamPromptServiceImpl service = new WritingExamPromptServiceImpl(
                openAiClient,
                essayPromptService,
                new ObjectMapper(),
                new WritingPromptSheetAssembler(),
                writingPromptSheetService
        );

        when(essayPromptService.listByStage(4)).thenReturn(List.of());
        when(openAiClient.callWithProvider(nullable(String.class), anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "promptType": "comic",
                          "topic": "OpenAI 与 Anthropic 的竞争",
                          "promptText": "Write an essay of about 200 words based on the following comic strips.",
                          "requirements": "1) describe the scenes briefly 2) explain the meaning 3) give your comments",
                          "genre": "看图作文",
                          "wordRange": "160-200",
                          "maxScore": 20,
                          "comicScenes": [
                            {
                              "title": "Scene 1",
                              "description": "Two AI companies face each other in a campus exhibition hall.",
                              "dialogue": "Speed matters."
                            }
                          ]
                        }
                        """);
        when(openAiClient.generateImageWithProvider(nullable(String.class), anyString(), anyString()))
                .thenReturn("data:image/png;base64,comic-image");
        WritingPromptSheet promptSheet = new WritingPromptSheet();
        promptSheet.setId(202L);
        promptSheet.setPaper("ai-20260410-comic123");
        when(writingPromptSheetService.createGeneratedPromptSheet(any(), any())).thenReturn(promptSheet);

        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput("写一篇关于 OpenAI 和 Anthropic 竞争的图画作文，字数在 200 以内");
        request.setStudyStage("postgrad");
        request.setTopic("OpenAI 与 Anthropic 的竞争");
        request.setPromptType("comic");
        request.setTaskType("task2");
        request.setWordRange("160-200");
        request.setMaxScore(20);

        var response = service.generate(request);

        assertThat(response.getPromptType()).isEqualTo("comic");
        assertThat(response.getComicScenes()).hasSize(1);
        assertThat(response.getAttachmentImageUrl()).isEqualTo("data:image/png;base64,comic-image");
        assertThat(response.getTaskType()).isEqualTo("task2");
        assertThat(response.getPromptSheetId()).isEqualTo(202L);
        verify(openAiClient).generateImageWithProvider(nullable(String.class), contains("Two AI companies face each other"), anyString());
    }
}
