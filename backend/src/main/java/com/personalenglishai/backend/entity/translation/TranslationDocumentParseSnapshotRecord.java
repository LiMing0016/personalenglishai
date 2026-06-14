package com.personalenglishai.backend.entity.translation;

import java.time.LocalDateTime;

public class TranslationDocumentParseSnapshotRecord {
    private Long id;
    private String documentId;
    private String fileName;
    private String sourceType;
    private String parseStatus;
    private String ocrStatus;
    private String provider;
    private String parseMode;
    private Boolean fallbackUsed;
    private Integer pageCount;
    private Integer blockCount;
    private String responseJson;
    private String diagnosisJson;
    private String qualityJson;
    private String languageProfileJson;
    private String parseJobJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getParseMode() { return parseMode; }
    public void setParseMode(String parseMode) { this.parseMode = parseMode; }
    public Boolean getFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(Boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Integer getBlockCount() { return blockCount; }
    public void setBlockCount(Integer blockCount) { this.blockCount = blockCount; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public String getDiagnosisJson() { return diagnosisJson; }
    public void setDiagnosisJson(String diagnosisJson) { this.diagnosisJson = diagnosisJson; }
    public String getQualityJson() { return qualityJson; }
    public void setQualityJson(String qualityJson) { this.qualityJson = qualityJson; }
    public String getLanguageProfileJson() { return languageProfileJson; }
    public void setLanguageProfileJson(String languageProfileJson) { this.languageProfileJson = languageProfileJson; }
    public String getParseJobJson() { return parseJobJson; }
    public void setParseJobJson(String parseJobJson) { this.parseJobJson = parseJobJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
