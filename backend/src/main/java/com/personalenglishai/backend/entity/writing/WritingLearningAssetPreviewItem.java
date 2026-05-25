package com.personalenglishai.backend.entity.writing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WritingLearningAssetPreviewItem {
    private Long id;
    private String itemUid;
    private String runUid;
    private Long documentId;
    private Long userId;
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
    private BigDecimal confidence;
    private BigDecimal learningValueScore;
    private String promotionStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemUid() { return itemUid; }
    public void setItemUid(String itemUid) { this.itemUid = itemUid; }
    public String getRunUid() { return runUid; }
    public void setRunUid(String runUid) { this.runUid = runUid; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public BigDecimal getLearningValueScore() { return learningValueScore; }
    public void setLearningValueScore(BigDecimal learningValueScore) { this.learningValueScore = learningValueScore; }
    public String getPromotionStatus() { return promotionStatus; }
    public void setPromotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
