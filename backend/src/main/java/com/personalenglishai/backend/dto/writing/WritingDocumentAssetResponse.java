package com.personalenglishai.backend.dto.writing;

import java.time.LocalDateTime;
import java.util.List;

public class WritingDocumentAssetResponse {
    private String docId;
    private String title;
    private String taskPrompt;
    private String content;
    private Integer latestRevision;
    private Integer latestScore;
    private Integer submitCount;
    private boolean archived;
    private List<EvaluationItem> evaluations = List.of();
    private List<CoachConversationItem> coachConversations = List.of();
    private LearningAssetPreview learningAssetPreview = LearningAssetPreview.none();
    private String markdown;
    private LocalDateTime generatedAt;
    private boolean stale;

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaskPrompt() { return taskPrompt; }
    public void setTaskPrompt(String taskPrompt) { this.taskPrompt = taskPrompt; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLatestRevision() { return latestRevision; }
    public void setLatestRevision(Integer latestRevision) { this.latestRevision = latestRevision; }
    public Integer getLatestScore() { return latestScore; }
    public void setLatestScore(Integer latestScore) { this.latestScore = latestScore; }
    public Integer getSubmitCount() { return submitCount; }
    public void setSubmitCount(Integer submitCount) { this.submitCount = submitCount; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public List<EvaluationItem> getEvaluations() { return evaluations; }
    public void setEvaluations(List<EvaluationItem> evaluations) { this.evaluations = evaluations == null ? List.of() : evaluations; }
    public List<CoachConversationItem> getCoachConversations() { return coachConversations; }
    public void setCoachConversations(List<CoachConversationItem> coachConversations) { this.coachConversations = coachConversations == null ? List.of() : coachConversations; }
    public LearningAssetPreview getLearningAssetPreview() { return learningAssetPreview; }
    public void setLearningAssetPreview(LearningAssetPreview learningAssetPreview) { this.learningAssetPreview = learningAssetPreview == null ? LearningAssetPreview.none() : learningAssetPreview; }
    public String getMarkdown() { return markdown; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }

    public static class EvaluationItem {
        private Long id;
        private Integer overallScore;
        private String band;
        private Integer structureScore;
        private Integer vocabularyScore;
        private Integer grammarScore;
        private Integer expressionScore;
        private Integer totalErrorCount;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getOverallScore() { return overallScore; }
        public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }
        public String getBand() { return band; }
        public void setBand(String band) { this.band = band; }
        public Integer getStructureScore() { return structureScore; }
        public void setStructureScore(Integer structureScore) { this.structureScore = structureScore; }
        public Integer getVocabularyScore() { return vocabularyScore; }
        public void setVocabularyScore(Integer vocabularyScore) { this.vocabularyScore = vocabularyScore; }
        public Integer getGrammarScore() { return grammarScore; }
        public void setGrammarScore(Integer grammarScore) { this.grammarScore = grammarScore; }
        public Integer getExpressionScore() { return expressionScore; }
        public void setExpressionScore(Integer expressionScore) { this.expressionScore = expressionScore; }
        public Integer getTotalErrorCount() { return totalErrorCount; }
        public void setTotalErrorCount(Integer totalErrorCount) { this.totalErrorCount = totalErrorCount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class CoachConversationItem {
        private String id;
        private String title;
        private Integer messageCount;
        private LocalDateTime updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getMessageCount() { return messageCount; }
        public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class LearningAssetPreview {
        private String status = "none";
        private String model;
        private String summary;
        private String errorMessage;
        private LocalDateTime generatedAt;
        private List<LearningAssetPreviewItem> items = List.of();

        public static LearningAssetPreview none() {
            LearningAssetPreview preview = new LearningAssetPreview();
            preview.setStatus("none");
            return preview;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        public List<LearningAssetPreviewItem> getItems() { return items; }
        public void setItems(List<LearningAssetPreviewItem> items) { this.items = items == null ? List.of() : items; }
    }

    public static class LearningAssetPreviewItem {
        private String id;
        private String assetType;
        private String sourceType;
        private String displayText;
        private String originalText;
        private String recommendedText;
        private String meaningZh;
        private String explanation;
        private String valueReasonForUser;
        private String howToReuse;
        private String reviewPrompt;
        private String sourceQuestion;
        private String sourceExcerpt;
        private Double confidence;
        private Double learningValueScore;
        private String promotionStatus;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getDisplayText() { return displayText; }
        public void setDisplayText(String displayText) { this.displayText = displayText; }
        public String getOriginalText() { return originalText; }
        public void setOriginalText(String originalText) { this.originalText = originalText; }
        public String getRecommendedText() { return recommendedText; }
        public void setRecommendedText(String recommendedText) { this.recommendedText = recommendedText; }
        public String getMeaningZh() { return meaningZh; }
        public void setMeaningZh(String meaningZh) { this.meaningZh = meaningZh; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public String getValueReasonForUser() { return valueReasonForUser; }
        public void setValueReasonForUser(String valueReasonForUser) { this.valueReasonForUser = valueReasonForUser; }
        public String getHowToReuse() { return howToReuse; }
        public void setHowToReuse(String howToReuse) { this.howToReuse = howToReuse; }
        public String getReviewPrompt() { return reviewPrompt; }
        public void setReviewPrompt(String reviewPrompt) { this.reviewPrompt = reviewPrompt; }
        public String getSourceQuestion() { return sourceQuestion; }
        public void setSourceQuestion(String sourceQuestion) { this.sourceQuestion = sourceQuestion; }
        public String getSourceExcerpt() { return sourceExcerpt; }
        public void setSourceExcerpt(String sourceExcerpt) { this.sourceExcerpt = sourceExcerpt; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public Double getLearningValueScore() { return learningValueScore; }
        public void setLearningValueScore(Double learningValueScore) { this.learningValueScore = learningValueScore; }
        public String getPromotionStatus() { return promotionStatus; }
        public void setPromotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; }
    }
}
