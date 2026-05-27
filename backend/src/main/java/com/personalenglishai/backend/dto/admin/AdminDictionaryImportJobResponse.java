package com.personalenglishai.backend.dto.admin;

import java.time.LocalDateTime;
import java.util.Map;

public class AdminDictionaryImportJobResponse {
    private Long id;
    private String importJobUid;
    private String dictionaryUid;
    private String sourceUid;
    private String status;
    private Integer importLimit;
    private Integer processedEntries;
    private Integer importedEntries;
    private Integer failedEntries;
    private Integer importedExamples;
    private Integer importedPhrases;
    private String errorMessage;
    private Map<String, Object> result;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getImportJobUid() { return importJobUid; }
    public void setImportJobUid(String importJobUid) { this.importJobUid = importJobUid; }
    public String getDictionaryUid() { return dictionaryUid; }
    public void setDictionaryUid(String dictionaryUid) { this.dictionaryUid = dictionaryUid; }
    public String getSourceUid() { return sourceUid; }
    public void setSourceUid(String sourceUid) { this.sourceUid = sourceUid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getImportLimit() { return importLimit; }
    public void setImportLimit(Integer importLimit) { this.importLimit = importLimit; }
    public Integer getProcessedEntries() { return processedEntries; }
    public void setProcessedEntries(Integer processedEntries) { this.processedEntries = processedEntries; }
    public Integer getImportedEntries() { return importedEntries; }
    public void setImportedEntries(Integer importedEntries) { this.importedEntries = importedEntries; }
    public Integer getFailedEntries() { return failedEntries; }
    public void setFailedEntries(Integer failedEntries) { this.failedEntries = failedEntries; }
    public Integer getImportedExamples() { return importedExamples; }
    public void setImportedExamples(Integer importedExamples) { this.importedExamples = importedExamples; }
    public Integer getImportedPhrases() { return importedPhrases; }
    public void setImportedPhrases(Integer importedPhrases) { this.importedPhrases = importedPhrases; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
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
