package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.WritingTaskMetadata;

import java.util.List;
import java.util.Map;

public class WritingTaskMetadataResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private String documentId;
    private String studyStage;
    private String assistantMode;
    private String promptText;
    private String taskType;
    private String centralTask;
    private List<String> mustAnswerPoints;
    private List<String> writingFocus;
    private List<String> riskPoints;
    private Map<String, String> recommendedStructure;
    private List<String> rubricFocus;
    private String metadataVersion;
    private String rubricSource;

    public static WritingTaskMetadataResponse from(WritingTaskMetadata metadata) {
        WritingTaskMetadataResponse response = new WritingTaskMetadataResponse();
        response.setDocumentId(metadata.getDocumentPublicId());
        response.setStudyStage(metadata.getStudyStage());
        response.setAssistantMode(metadata.getAssistantMode());
        response.setPromptText(metadata.getPromptText());
        response.setTaskType(metadata.getTaskType());
        response.setCentralTask(metadata.getCentralTask());
        response.setMustAnswerPoints(readList(metadata.getMustAnswerPointsJson()));
        response.setWritingFocus(readList(metadata.getWritingFocusJson()));
        response.setRiskPoints(readList(metadata.getRiskPointsJson()));
        response.setRecommendedStructure(readMap(metadata.getRecommendedStructureJson()));
        response.setRubricFocus(readList(metadata.getRubricFocusJson()));
        response.setMetadataVersion(metadata.getMetadataVersion());
        response.setRubricSource(metadata.getRubricSource());
        return response;
    }

    private static List<String> readList(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, String> readMap(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : OBJECT_MAPPER.readValue(json, STRING_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }
    public String getAssistantMode() { return assistantMode; }
    public void setAssistantMode(String assistantMode) { this.assistantMode = assistantMode; }
    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getCentralTask() { return centralTask; }
    public void setCentralTask(String centralTask) { this.centralTask = centralTask; }
    public List<String> getMustAnswerPoints() { return mustAnswerPoints; }
    public void setMustAnswerPoints(List<String> mustAnswerPoints) { this.mustAnswerPoints = mustAnswerPoints; }
    public List<String> getWritingFocus() { return writingFocus; }
    public void setWritingFocus(List<String> writingFocus) { this.writingFocus = writingFocus; }
    public List<String> getRiskPoints() { return riskPoints; }
    public void setRiskPoints(List<String> riskPoints) { this.riskPoints = riskPoints; }
    public Map<String, String> getRecommendedStructure() { return recommendedStructure; }
    public void setRecommendedStructure(Map<String, String> recommendedStructure) { this.recommendedStructure = recommendedStructure; }
    public List<String> getRubricFocus() { return rubricFocus; }
    public void setRubricFocus(List<String> rubricFocus) { this.rubricFocus = rubricFocus; }
    public String getMetadataVersion() { return metadataVersion; }
    public void setMetadataVersion(String metadataVersion) { this.metadataVersion = metadataVersion; }
    public String getRubricSource() { return rubricSource; }
    public void setRubricSource(String rubricSource) { this.rubricSource = rubricSource; }
}
