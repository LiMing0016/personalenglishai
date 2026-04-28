package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSheetChartImageServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void renderChartSpecShouldCreatePngAndExposeAttachmentImageUrl() throws Exception {
        PromptSheetChartImageService service = new PromptSheetChartImageService(uploadRoot, "/uploads");
        GenerateExamPromptResponse response = chartPromptSheetResponse();

        service.attachChartImage(response);

        assertThat(response.getAttachmentType()).isEqualTo("visual");
        assertThat(response.getVisualKind()).isEqualTo("image");
        assertThat(response.getAttachmentImageUrl()).startsWith("/uploads/prompt-sheets/charts/");
        assertThat(response.getAttachmentImageUrl()).endsWith(".png");

        Path imagePath = uploadRoot.resolve(response.getAttachmentImageUrl().replaceFirst("^/uploads/", ""));
        assertThat(imagePath).exists();
        assertThat(Files.readAllBytes(imagePath)).startsWith(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47
        });
    }

    private GenerateExamPromptResponse chartPromptSheetResponse() {
        GenerateExamPromptResponse response = new GenerateExamPromptResponse();
        response.setPromptType("chart");
        response.setTopic("GDP 总量与增速（2014-2023）");
        response.setAttachmentType("visual");
        response.setVisualKind("chart");
        response.setAttachmentTitle("GDP 总量与增速（2014-2023）折线图");

        GenerateExamPromptResponse.ChartSpec chartSpec = new GenerateExamPromptResponse.ChartSpec();
        chartSpec.setTitle("中国近 10 年 GDP 增长情况");
        chartSpec.setDisplayType("chart");
        chartSpec.setColumns(List.of("Year", "GDP (trillion yuan)", "Growth Rate (%)"));
        chartSpec.setRows(List.of(
                List.of("2014", "63.6", "7.3%"),
                List.of("2015", "67.7", "6.9%"),
                List.of("2016", "74.0", "6.7%"),
                List.of("2017", "82.1", "6.9%"),
                List.of("2018", "90.0", "6.7%"),
                List.of("2019", "99.1", "6.0%"),
                List.of("2020", "101.6", "2.2%"),
                List.of("2021", "114.4", "8.4%"),
                List.of("2022", "120.5", "3.0%"),
                List.of("2023", "126.1", "5.2%")
        ));
        chartSpec.setSummary("GDP rose overall while the growth rate fluctuated.");
        response.setChartSpec(chartSpec);
        return response;
    }
}
