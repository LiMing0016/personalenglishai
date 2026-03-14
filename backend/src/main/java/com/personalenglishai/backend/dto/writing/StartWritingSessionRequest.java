package com.personalenglishai.backend.dto.writing;

/**
 * POST /api/writing/start-session request body.
 */
public class StartWritingSessionRequest {

    private String mode = "free";

    private String taskPrompt;

    private String title;

    /** true for saving draft from exam setup, false for正式开始 */
    private Boolean draft = false;

    /** 写作元数据字段（可选） */
    private String studyStage;
    private String sourceType;
    private String titleSnapshot;
    private String topicTitle;
    private String promptText;
    private String genre;
    private String examType;
    private String taskType;
    private Integer minWords;
    private Integer recommendedMaxWords;
    private Integer maxScore;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public void setTaskPrompt(String taskPrompt) {
        this.taskPrompt = taskPrompt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getDraft() {
        return draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public void setTitleSnapshot(String titleSnapshot) {
        this.titleSnapshot = titleSnapshot;
    }

    public String getTopicTitle() {
        return topicTitle;
    }

    public void setTopicTitle(String topicTitle) {
        this.topicTitle = topicTitle;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
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

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }
}
