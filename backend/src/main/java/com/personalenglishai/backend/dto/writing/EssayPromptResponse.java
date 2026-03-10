package com.personalenglishai.backend.dto.writing;

public class EssayPromptResponse {
    private Long id;
    private String paper;
    private String title;
    private String promptText;
    private Integer examYear;
    private String imageUrl;
    private String imageDescription;
    private String materialText;
    private String task;
    private Integer wordCountMin;
    private Integer wordCountMax;
    private Integer maxScore;
    private String source;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaper() { return paper; }
    public void setPaper(String paper) { this.paper = paper; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }

    public Integer getExamYear() { return examYear; }
    public void setExamYear(Integer examYear) { this.examYear = examYear; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }

    public String getMaterialText() { return materialText; }
    public void setMaterialText(String materialText) { this.materialText = materialText; }

    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }

    public Integer getWordCountMin() { return wordCountMin; }
    public void setWordCountMin(Integer wordCountMin) { this.wordCountMin = wordCountMin; }

    public Integer getWordCountMax() { return wordCountMax; }
    public void setWordCountMax(Integer wordCountMax) { this.wordCountMax = wordCountMax; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
