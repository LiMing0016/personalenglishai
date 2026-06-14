package com.personalenglishai.backend.entity.translation;

import java.time.LocalDateTime;

public class TranslationKnowledgeChunkRecord {
    private Long id;
    private String documentId;
    private String chunkId;
    private Integer chunkOrder;
    private String chunkType;
    private String content;
    private String summary;
    private String sourceElementIdsJson;
    private String pageNumbersJson;
    private Integer firstPageNumber;
    private Integer tokenCount;
    private Double qualityScore;
    private String embeddingStatus;
    private String granularity;
    private Integer startElementOrder;
    private Integer endElementOrder;
    private String sectionPathJson;
    private String parentChunkId;
    private String prevChunkId;
    private String nextChunkId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public Integer getChunkOrder() { return chunkOrder; }
    public void setChunkOrder(Integer chunkOrder) { this.chunkOrder = chunkOrder; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSourceElementIdsJson() { return sourceElementIdsJson; }
    public void setSourceElementIdsJson(String sourceElementIdsJson) { this.sourceElementIdsJson = sourceElementIdsJson; }
    public String getPageNumbersJson() { return pageNumbersJson; }
    public void setPageNumbersJson(String pageNumbersJson) { this.pageNumbersJson = pageNumbersJson; }
    public Integer getFirstPageNumber() { return firstPageNumber; }
    public void setFirstPageNumber(Integer firstPageNumber) { this.firstPageNumber = firstPageNumber; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    public String getEmbeddingStatus() { return embeddingStatus; }
    public void setEmbeddingStatus(String embeddingStatus) { this.embeddingStatus = embeddingStatus; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public Integer getStartElementOrder() { return startElementOrder; }
    public void setStartElementOrder(Integer startElementOrder) { this.startElementOrder = startElementOrder; }
    public Integer getEndElementOrder() { return endElementOrder; }
    public void setEndElementOrder(Integer endElementOrder) { this.endElementOrder = endElementOrder; }
    public String getSectionPathJson() { return sectionPathJson; }
    public void setSectionPathJson(String sectionPathJson) { this.sectionPathJson = sectionPathJson; }
    public String getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(String parentChunkId) { this.parentChunkId = parentChunkId; }
    public String getPrevChunkId() { return prevChunkId; }
    public void setPrevChunkId(String prevChunkId) { this.prevChunkId = prevChunkId; }
    public String getNextChunkId() { return nextChunkId; }
    public void setNextChunkId(String nextChunkId) { this.nextChunkId = nextChunkId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
