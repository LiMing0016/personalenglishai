package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.mapper.WritingPromptSheetMapper;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WritingPromptSheetServiceImpl implements WritingPromptSheetService {

    private static final DateTimeFormatter PAPER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final WritingPromptSheetMapper writingPromptSheetMapper;
    private final ObjectMapper objectMapper;

    public WritingPromptSheetServiceImpl(WritingPromptSheetMapper writingPromptSheetMapper, ObjectMapper objectMapper) {
        this.writingPromptSheetMapper = writingPromptSheetMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public WritingPromptSheet createGeneratedPromptSheet(GenerateExamPromptRequest request, GenerateExamPromptResponse response) {
        WritingPromptSheet entity = new WritingPromptSheet();
        entity.setPaper(generatePaperCode());
        entity.setStudyStage(trimToNull(request.getStudyStage()));
        entity.setSourceType(firstNonBlank(response.getSourceType(), "ai_generated"));
        entity.setTaskType(trimToNull(response.getTaskType()));
        entity.setPromptType(trimToNull(response.getPromptType()));
        entity.setTopicTitle(firstNonBlank(response.getTopic(), response.getPromptText()));
        entity.setDirections(trimToNull(response.getDirections()));
        entity.setPromptText(firstNonBlank(response.getPromptText(), request.getTopic()));
        entity.setRequirementsText(trimToNull(response.getRequirements()));
        entity.setGenre(trimToNull(response.getGenre()));
        entity.setWordCountMin(response.getMinWords());
        entity.setWordCountMax(response.getRecommendedMaxWords());
        entity.setAttachmentType(firstNonBlank(response.getAttachmentType(), "none"));
        entity.setAttachmentPayloadJson(writeJson(buildAttachmentPayload(response)));
        entity.setStructuredPayloadJson(writeJson(buildStructuredPayload(response)));
        entity.setStatus("active");
        writingPromptSheetMapper.insert(entity);
        return entity;
    }

    @Override
    public void bindDocument(Long promptSheetId, Long documentId) {
        if (promptSheetId == null || documentId == null) {
            return;
        }
        writingPromptSheetMapper.updateDocumentId(promptSheetId, documentId);
    }

    private Map<String, Object> buildAttachmentPayload(GenerateExamPromptResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (trimToNull(response.getAttachmentTitle()) != null) {
            payload.put("title", response.getAttachmentTitle().trim());
        }
        if (trimToNull(response.getAttachmentContent()) != null) {
            payload.put("content", response.getAttachmentContent().trim());
        }
        if (trimToNull(response.getMaterialText()) != null) {
            payload.put("materialText", response.getMaterialText().trim());
        }
        if (trimToNull(response.getAttachmentImageUrl()) != null) {
            payload.put("imageUrl", response.getAttachmentImageUrl().trim());
        }
        return payload.isEmpty() ? null : payload;
    }

    private Map<String, Object> buildStructuredPayload(GenerateExamPromptResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (response.getChartSpec() != null) {
            Map<String, Object> chartSpec = new LinkedHashMap<>();
            putIfNotBlank(chartSpec, "title", response.getChartSpec().getTitle());
            putIfNotBlank(chartSpec, "displayType", response.getChartSpec().getDisplayType());
            if (response.getChartSpec().getColumns() != null && !response.getChartSpec().getColumns().isEmpty()) {
                chartSpec.put("columns", response.getChartSpec().getColumns());
            }
            if (response.getChartSpec().getRows() != null && !response.getChartSpec().getRows().isEmpty()) {
                chartSpec.put("rows", response.getChartSpec().getRows());
            }
            putIfNotBlank(chartSpec, "summary", response.getChartSpec().getSummary());
            if (!chartSpec.isEmpty()) {
                payload.put("chartSpec", chartSpec);
            }
        }
        if (response.getComicScenes() != null && !response.getComicScenes().isEmpty()) {
            List<Map<String, Object>> comicScenes = response.getComicScenes().stream()
                    .map(scene -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        putIfNotBlank(item, "title", scene.getTitle());
                        putIfNotBlank(item, "description", scene.getDescription());
                        putIfNotBlank(item, "dialogue", scene.getDialogue());
                        return item;
                    })
                    .filter(item -> !item.isEmpty())
                    .toList();
            if (!comicScenes.isEmpty()) {
                payload.put("comicScenes", comicScenes);
            }
        }
        return payload.isEmpty() ? null : payload;
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            map.put(key, normalized);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize prompt sheet payload", e);
        }
    }

    private String generatePaperCode() {
        String date = LocalDate.now().format(PAPER_DATE_FORMAT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ai-" + date + "-" + suffix;
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
