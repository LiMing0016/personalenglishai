package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class VocabularyCardSource {
    private Long id;
    private String sourceUid;
    private String cardUid;
    private Long userId;
    private String sourceType;
    private String sourceRef;
    private String sourceTitle;
    private String sourceUrl;
    private String contextText;
    private String rawTerm;
    private String idempotencyKey;
    private LocalDateTime capturedAt;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer sourceCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceUid() { return sourceUid; }
    public void setSourceUid(String sourceUid) { this.sourceUid = sourceUid; }
    public String getCardUid() { return cardUid; }
    public void setCardUid(String cardUid) { this.cardUid = cardUid; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getContextText() { return contextText; }
    public void setContextText(String contextText) { this.contextText = contextText; }
    public String getRawTerm() { return rawTerm; }
    public void setRawTerm(String rawTerm) { this.rawTerm = rawTerm; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getSourceCount() { return sourceCount; }
    public void setSourceCount(Integer sourceCount) { this.sourceCount = sourceCount; }
}
