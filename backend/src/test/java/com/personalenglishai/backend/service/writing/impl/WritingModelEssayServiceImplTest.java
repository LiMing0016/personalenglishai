package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingModelEssayRequest;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingModelEssayServiceImplTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private RubricService rubricService;

    @Mock
    private RubricTextBuilder rubricTextBuilder;

    @Test
    void generateShouldReturnTwoLearningCardsForExam() {
        WritingModelEssayServiceImpl service = createService();
        when(rubricService.normalizeStage(anyString())).thenReturn("postgrad");
        when(rubricService.normalizeMode(anyString())).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(mockRubric("postgrad-exam-v1"));
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("content_quality: A...");
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        {
                          "excellentEssay": {
                            "label": "优秀作文",
                            "essay": "This is an excellent model essay.",
                            "summary": "结构清楚、内容扎实。",
                            "highScoreReasons": ["任务完成：三步动作完整", "结构：段落推进自然"],
                            "improvementGuidance": ["评论部分还可更具体", "数据对比可以更鲜明"]
                          },
                          "perfectEssay": {
                            "label": "满分作文",
                            "essay": "This is a perfect model essay.",
                            "summary": "高档表达与任务完成度兼具。",
                            "highScoreReasons": ["内容质量：分析深入", "表达：正式自然"],
                            "improvementGuidance": ["增加更具体的数据解读", "评论层次可以再提升"]
                          }
                        }
                        """);

        WritingModelEssayRequest request = new WritingModelEssayRequest();
        request.setEssay("This is the student essay.");
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");
        request.setTaskType("task2");
        request.setTopicContent("图表显示某市近三年公园数量增长。");
        request.setTaskPrompt("describe the chart and give your comments.");
        request.setMinWords(160);
        request.setRecommendedMaxWords(200);

        var response = service.generate(request);

        assertThat(response.getRubricKey()).isEqualTo("postgrad-exam-v1");
        assertThat(response.getExcellentEssay().getLabel()).isEqualTo("优秀作文");
        assertThat(response.getPerfectEssay().getLabel()).isEqualTo("满分作文");
        assertThat(response.getExcellentEssay().getHighScoreReasons()).contains("任务完成：三步动作完整");
        assertThat(response.getPerfectEssay().getImprovementGuidance()).contains("增加更具体的数据解读");
    }

    @Test
    void generateShouldInstructThemeInferenceForFreeModeWithoutTopic() {
        WritingModelEssayServiceImpl service = createService();
        when(rubricService.normalizeStage(anyString())).thenReturn("highschool");
        when(rubricService.normalizeMode(anyString())).thenReturn("free");
        when(rubricService.getActiveRubric("highschool", "free")).thenReturn(mockRubric("highschool-free-v1"));
        when(rubricTextBuilder.buildRubricText("highschool", "free")).thenReturn("structure: A...");
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        {
                          "excellentEssay": {"label":"优秀作文","essay":"Essay A","summary":"A","highScoreReasons":[],"improvementGuidance":[]},
                          "perfectEssay": {"label":"满分作文","essay":"Essay B","summary":"B","highScoreReasons":[],"improvementGuidance":[]}
                        }
                        """);

        WritingModelEssayRequest request = new WritingModelEssayRequest();
        request.setEssay("My draft talks about volunteering in the community and how it changes people.");
        request.setStudyStage("highschool");
        request.setWritingMode("free");

        service.generate(request);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithTraceId(
                org.mockito.ArgumentMatchers.anyString(),
                userPromptCaptor.capture(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt()
        );

        assertThat(userPromptCaptor.getValue())
                .contains("[TOPIC_CONTENT]")
                .contains("(empty)")
                .contains("请先从学生作文中提炼中心主题");
    }

    private WritingModelEssayServiceImpl createService() {
        return new WritingModelEssayServiceImpl(
                openAiClient,
                rubricService,
                rubricTextBuilder,
                new ObjectMapper()
        );
    }

    private RubricActiveResponse mockRubric(String key) {
        RubricActiveResponse response = new RubricActiveResponse();
        response.setRubricKey(key);
        RubricActiveResponse.DimensionDto dimension = new RubricActiveResponse.DimensionDto();
        dimension.setDimensionKey("content_quality");
        dimension.setDisplayName("内容质量");
        RubricActiveResponse.LevelDto level = new RubricActiveResponse.LevelDto();
        level.setLevel("A");
        level.setScore(90);
        level.setCriteria("A");
        dimension.setLevels(List.of(level));
        response.setDimensions(List.of(dimension));
        return response;
    }
}
