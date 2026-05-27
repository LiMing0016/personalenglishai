package com.personalenglishai.backend.dto.admin;

import java.time.LocalDateTime;
import java.util.Map;

public class AdminDataCleaningSourceResponse {
    private Long id;
    private String sourceUid;
    private String sourceType;
    private String sourceCode;
    private String displayName;
    private String licenseStatus;
    private String mdxPath;
    private String mddPath;
    private String examplesPath;
    private String coverImagePath;
    private Map<String, Object> metadata;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceUid() { return sourceUid; }
    public void setSourceUid(String sourceUid) { this.sourceUid = sourceUid; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLicenseStatus() { return licenseStatus; }
    public void setLicenseStatus(String licenseStatus) { this.licenseStatus = licenseStatus; }
    public String getMdxPath() { return mdxPath; }
    public void setMdxPath(String mdxPath) { this.mdxPath = mdxPath; }
    public String getMddPath() { return mddPath; }
    public void setMddPath(String mddPath) { this.mddPath = mddPath; }
    public String getExamplesPath() { return examplesPath; }
    public void setExamplesPath(String examplesPath) { this.examplesPath = examplesPath; }
    public String getCoverImagePath() { return coverImagePath; }
    public void setCoverImagePath(String coverImagePath) { this.coverImagePath = coverImagePath; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
