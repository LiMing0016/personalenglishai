package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnglishAssistantRubricContextServiceTest {

    @Mock
    private RubricService rubricService;

    @Test
    void resolveShouldBuildCompactSummaryFromActiveRubric() {
        EnglishAssistantRubricContextService service = new EnglishAssistantRubricContextService(rubricService);
        RubricActiveResponse rubric = new RubricActiveResponse();
        rubric.setRubricKey("postgrad-exam-v1");
        rubric.setMode("exam");
        rubric.setDimensions(List.of(
                dimension(
                        "task_achievement",
                        "任务完成",
                        level("A", 90, "完整完成描述材料、解释含义、给出评论"),
                        level("C", 60, "完成主要任务但展开不足"),
                        level("E", 20, "严重偏题或基本未完成任务")
                ),
                dimension(
                        "grammar",
                        "语法",
                        level("A", 90, "结构稳定且准确"),
                        level("C", 60, "存在明显语法问题但可理解")
                )
        ));
        when(rubricService.normalizeStage("postgrad")).thenReturn("postgrad");
        when(rubricService.normalizeMode("exam")).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(rubric);

        EnglishAssistantRubricContext context = service.resolve("postgrad", "exam");

        assertThat(context).isNotNull();
        assertThat(context.rubricKey()).isEqualTo("postgrad-exam-v1");
        assertThat(context.summary()).contains("rubric_key=postgrad-exam-v1");
        assertThat(context.summary()).contains("task_achievement");
        assertThat(context.summary()).contains("grammar");
        assertThat(context.summary()).contains("A(90)");
        assertThat(context.summary()).contains("E(20)");
    }

    @Test
    void resolveShouldReturnNullWhenNoActiveRubric() {
        EnglishAssistantRubricContextService service = new EnglishAssistantRubricContextService(rubricService);
        when(rubricService.normalizeStage("cet4")).thenReturn("cet4");
        when(rubricService.normalizeMode("exam")).thenReturn("exam");
        when(rubricService.getActiveRubric("cet4", "exam")).thenReturn(null);

        EnglishAssistantRubricContext context = service.resolve("cet4", "exam");

        assertThat(context).isNull();
    }

    private RubricActiveResponse.DimensionDto dimension(String key,
                                                        String displayName,
                                                        RubricActiveResponse.LevelDto... levels) {
        RubricActiveResponse.DimensionDto dto = new RubricActiveResponse.DimensionDto();
        dto.setDimensionKey(key);
        dto.setDisplayName(displayName);
        dto.setLevels(List.of(levels));
        return dto;
    }

    private RubricActiveResponse.LevelDto level(String level, Integer score, String criteria) {
        RubricActiveResponse.LevelDto dto = new RubricActiveResponse.LevelDto();
        dto.setLevel(level);
        dto.setScore(score);
        dto.setCriteria(criteria);
        return dto;
    }
}
