package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WritingPromptSheetAssemblerTest {

    @Test
    void shouldPopulateVisualPromptSheetFieldsForChartPrompt() {
        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setTopic("AI Agent adoption");

        GenerateExamPromptResponse response = new GenerateExamPromptResponse();
        response.setPromptType("chart");
        response.setTopic("AI Agent adoption");
        response.setPromptText("Write an essay based on the table below.");
        response.setRequirements("describe the changes and give your comments");

        GenerateExamPromptResponse.ChartSpec chartSpec = new GenerateExamPromptResponse.ChartSpec();
        chartSpec.setTitle("Adoption of AI Agent Tools");
        chartSpec.setDisplayType("table");
        chartSpec.setColumns(List.of("Year", "Rate"));
        chartSpec.setRows(List.of(List.of("2021", "18%"), List.of("2024", "63%")));
        chartSpec.setSummary("The rate rose steadily.");
        response.setChartSpec(chartSpec);

        WritingPromptSheetAssembler assembler = new WritingPromptSheetAssembler();
        assembler.populate(request, response);

        assertThat(response.getPart()).isEqualTo("Part B");
        assertThat(response.getDirections()).isEqualTo("Directions:");
        assertThat(response.getAttachmentType()).isEqualTo("visual");
        assertThat(response.getVisualKind()).isEqualTo("table");
        assertThat(response.getAttachmentTitle()).isEqualTo("Adoption of AI Agent Tools");
        assertThat(response.getAttachmentContent()).contains("18%");
    }
}
