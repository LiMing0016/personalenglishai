package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateExamPromptResponse {

    private String promptType;
    private String paper;
    private Long promptSheetId;
    private String topic;
    private String promptText;
    private String requirements;
    private String part;
    private String questionNo;
    private String directions;
    private String genre;
    private String wordRange;
    private Integer maxScore;
    private String sourceType;
    private String taskType;
    private Integer minWords;
    private Integer recommendedMaxWords;
    private String attachmentType;
    private String attachmentTitle;
    private String attachmentContent;
    private String attachmentImageUrl;
    private String visualKind;
    private String materialText;
    private ChartSpec chartSpec;
    private List<ComicScene> comicScenes = new ArrayList<>();

    public String getPromptType() {
        return promptType;
    }

    public void setPromptType(String promptType) {
        this.promptType = promptType;
    }

    public String getPaper() {
        return paper;
    }

    public void setPaper(String paper) {
        this.paper = paper;
    }

    public Long getPromptSheetId() {
        return promptSheetId;
    }

    public void setPromptSheetId(Long promptSheetId) {
        this.promptSheetId = promptSheetId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getQuestionNo() {
        return questionNo;
    }

    public void setQuestionNo(String questionNo) {
        this.questionNo = questionNo;
    }

    public String getDirections() {
        return directions;
    }

    public void setDirections(String directions) {
        this.directions = directions;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getWordRange() {
        return wordRange;
    }

    public void setWordRange(String wordRange) {
        this.wordRange = wordRange;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getMinWords() {
        return minWords;
    }

    public void setMinWords(Integer minWords) {
        this.minWords = minWords;
    }

    public Integer getRecommendedMaxWords() {
        return recommendedMaxWords;
    }

    public void setRecommendedMaxWords(Integer recommendedMaxWords) {
        this.recommendedMaxWords = recommendedMaxWords;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }

    public String getAttachmentTitle() {
        return attachmentTitle;
    }

    public void setAttachmentTitle(String attachmentTitle) {
        this.attachmentTitle = attachmentTitle;
    }

    public String getAttachmentContent() {
        return attachmentContent;
    }

    public void setAttachmentContent(String attachmentContent) {
        this.attachmentContent = attachmentContent;
    }

    public String getAttachmentImageUrl() {
        return attachmentImageUrl;
    }

    public void setAttachmentImageUrl(String attachmentImageUrl) {
        this.attachmentImageUrl = attachmentImageUrl;
    }

    public String getVisualKind() {
        return visualKind;
    }

    public void setVisualKind(String visualKind) {
        this.visualKind = visualKind;
    }

    public String getMaterialText() {
        return materialText;
    }

    public void setMaterialText(String materialText) {
        this.materialText = materialText;
    }

    public ChartSpec getChartSpec() {
        return chartSpec;
    }

    public void setChartSpec(ChartSpec chartSpec) {
        this.chartSpec = chartSpec;
    }

    public List<ComicScene> getComicScenes() {
        return comicScenes;
    }

    public void setComicScenes(List<ComicScene> comicScenes) {
        this.comicScenes = comicScenes == null ? new ArrayList<>() : comicScenes;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChartSpec {
        private String title;
        private String displayType;
        private List<String> columns = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
        private String summary;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDisplayType() {
            return displayType;
        }

        public void setDisplayType(String displayType) {
            this.displayType = displayType;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns == null ? new ArrayList<>() : columns;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public void setRows(List<List<String>> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComicScene {
        private String title;
        private String description;
        private String dialogue;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDialogue() {
            return dialogue;
        }

        public void setDialogue(String dialogue) {
            this.dialogue = dialogue;
        }
    }
}
