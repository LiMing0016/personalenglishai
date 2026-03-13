package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class DocumentScoreSummary {

    private Long documentId;
    private Long userId;
    private Long firstEvaluationId;
    private Long latestEvaluationId;
    private Long bestEvaluationId;
    private Integer firstOverallScore;
    private Integer latestOverallScore;
    private Integer bestOverallScore;
    private String latestBandLabel;
    private Integer latestWordCount;
    private Integer latestTotalErrorCount;
    private Integer latestMajorErrorCount;
    private Integer latestMinorErrorCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFirstEvaluationId() { return firstEvaluationId; }
    public void setFirstEvaluationId(Long firstEvaluationId) { this.firstEvaluationId = firstEvaluationId; }

    public Long getLatestEvaluationId() { return latestEvaluationId; }
    public void setLatestEvaluationId(Long latestEvaluationId) { this.latestEvaluationId = latestEvaluationId; }

    public Long getBestEvaluationId() { return bestEvaluationId; }
    public void setBestEvaluationId(Long bestEvaluationId) { this.bestEvaluationId = bestEvaluationId; }

    public Integer getFirstOverallScore() { return firstOverallScore; }
    public void setFirstOverallScore(Integer firstOverallScore) { this.firstOverallScore = firstOverallScore; }

    public Integer getLatestOverallScore() { return latestOverallScore; }
    public void setLatestOverallScore(Integer latestOverallScore) { this.latestOverallScore = latestOverallScore; }

    public Integer getBestOverallScore() { return bestOverallScore; }
    public void setBestOverallScore(Integer bestOverallScore) { this.bestOverallScore = bestOverallScore; }

    public String getLatestBandLabel() { return latestBandLabel; }
    public void setLatestBandLabel(String latestBandLabel) { this.latestBandLabel = latestBandLabel; }

    public Integer getLatestWordCount() { return latestWordCount; }
    public void setLatestWordCount(Integer latestWordCount) { this.latestWordCount = latestWordCount; }

    public Integer getLatestTotalErrorCount() { return latestTotalErrorCount; }
    public void setLatestTotalErrorCount(Integer latestTotalErrorCount) { this.latestTotalErrorCount = latestTotalErrorCount; }

    public Integer getLatestMajorErrorCount() { return latestMajorErrorCount; }
    public void setLatestMajorErrorCount(Integer latestMajorErrorCount) { this.latestMajorErrorCount = latestMajorErrorCount; }

    public Integer getLatestMinorErrorCount() { return latestMinorErrorCount; }
    public void setLatestMinorErrorCount(Integer latestMinorErrorCount) { this.latestMinorErrorCount = latestMinorErrorCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
