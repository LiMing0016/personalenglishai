package com.personalenglishai.backend.dto.admin;

import java.time.LocalDateTime;
import java.util.Map;

public class AdminDataCleaningJobResponse {
    private Long id;
    private String jobUid;
    private String sourceUid;
    private String jobType;
    private String status;
    private Integer progressTotal;
    private Integer progressDone;
    private Map<String, Object> result;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobUid() { return jobUid; }
    public void setJobUid(String jobUid) { this.jobUid = jobUid; }
    public String getSourceUid() { return sourceUid; }
    public void setSourceUid(String sourceUid) { this.sourceUid = sourceUid; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgressTotal() { return progressTotal; }
    public void setProgressTotal(Integer progressTotal) { this.progressTotal = progressTotal; }
    public Integer getProgressDone() { return progressDone; }
    public void setProgressDone(Integer progressDone) { this.progressDone = progressDone; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
