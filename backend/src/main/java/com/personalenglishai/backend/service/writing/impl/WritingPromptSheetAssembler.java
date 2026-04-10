package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WritingPromptSheetAssembler {

    public void populate(GenerateExamPromptRequest request, GenerateExamPromptResponse response) {
        response.setPart(firstNonBlank(response.getPart(), "Part B"));
        response.setDirections(firstNonBlank(response.getDirections(), "Directions:"));

        String promptType = normalizePromptType(response.getPromptType());
        switch (promptType) {
            case "material" -> {
                response.setAttachmentType("material");
                response.setAttachmentContent(firstNonBlank(response.getAttachmentContent(), trimToNull(response.getMaterialText())));
            }
            case "chart" -> {
                response.setAttachmentType("visual");
                if (response.getChartSpec() != null) {
                    response.setVisualKind(firstNonBlank(response.getVisualKind(), normalizeVisualKind(response.getChartSpec().getDisplayType())));
                    response.setAttachmentTitle(firstNonBlank(response.getAttachmentTitle(), trimToNull(response.getChartSpec().getTitle())));
                    response.setAttachmentContent(firstNonBlank(response.getAttachmentContent(), summarizeChartSpec(response.getChartSpec())));
                } else {
                    response.setVisualKind(firstNonBlank(response.getVisualKind(), "chart"));
                }
            }
            case "comic" -> {
                response.setAttachmentType("visual");
                response.setVisualKind(firstNonBlank(response.getVisualKind(), "comic"));
                response.setAttachmentContent(firstNonBlank(response.getAttachmentContent(), summarizeComicScenes(response.getComicScenes())));
            }
            default -> response.setAttachmentType(firstNonBlank(response.getAttachmentType(), "none"));
        }

        if (response.getPromptText() == null) {
            response.setPromptText(trimToNull(request.getTopic()));
        }
    }

    private String summarizeChartSpec(GenerateExamPromptResponse.ChartSpec chartSpec) {
        List<String> lines = new ArrayList<>();
        if (trimToNull(chartSpec.getTitle()) != null) {
            lines.add(chartSpec.getTitle().trim());
        }
        if (chartSpec.getColumns() != null && !chartSpec.getColumns().isEmpty()) {
            lines.add(String.join(" | ", chartSpec.getColumns()));
        }
        if (chartSpec.getRows() != null) {
            for (List<String> row : chartSpec.getRows()) {
                lines.add(String.join(" | ", row));
            }
        }
        if (trimToNull(chartSpec.getSummary()) != null) {
            lines.add(chartSpec.getSummary().trim());
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private String summarizeComicScenes(List<GenerateExamPromptResponse.ComicScene> comicScenes) {
        if (comicScenes == null || comicScenes.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < comicScenes.size(); i++) {
            GenerateExamPromptResponse.ComicScene scene = comicScenes.get(i);
            String title = firstNonBlank(trimToNull(scene.getTitle()), "Scene " + (i + 1));
            if (trimToNull(scene.getDescription()) != null) {
                lines.add(title + ": " + scene.getDescription().trim());
            }
            if (trimToNull(scene.getDialogue()) != null) {
                lines.add("Dialogue: " + scene.getDialogue().trim());
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private String normalizePromptType(String promptType) {
        String normalized = trimToNull(promptType);
        return normalized == null ? "general" : normalized.toLowerCase();
    }

    private String normalizeVisualKind(String displayType) {
        String normalized = trimToNull(displayType);
        if (normalized == null) {
            return "chart";
        }
        return "table".equalsIgnoreCase(normalized) ? "table" : "chart";
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = trimToNull(primary);
        return normalizedPrimary != null ? normalizedPrimary : trimToNull(fallback);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
