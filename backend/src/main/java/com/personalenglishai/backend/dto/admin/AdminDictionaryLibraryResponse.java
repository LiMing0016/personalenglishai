package com.personalenglishai.backend.dto.admin;

import java.time.LocalDateTime;
import java.util.Map;

public class AdminDictionaryLibraryResponse {
    private Long id;
    private String dictionaryUid;
    private String sourceUid;
    private String dictionaryCode;
    private String displayName;
    private String description;
    private String format;
    private String engineVersion;
    private String requiredEngineVersion;
    private String encoding;
    private Long entryCount;
    private Long resourceCount;
    private String mdxFileName;
    private String mddFileName;
    private String coverImagePath;
    private Long mdxSizeBytes;
    private Long mddSizeBytes;
    private Long examplesCount;
    private String licenseStatus;
    private String storageType;
    private boolean enabled;
    private Integer sortOrder;
    private String status;
    private Map<String, Object> metadata;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDictionaryUid() { return dictionaryUid; }
    public void setDictionaryUid(String dictionaryUid) { this.dictionaryUid = dictionaryUid; }
    public String getSourceUid() { return sourceUid; }
    public void setSourceUid(String sourceUid) { this.sourceUid = sourceUid; }
    public String getDictionaryCode() { return dictionaryCode; }
    public void setDictionaryCode(String dictionaryCode) { this.dictionaryCode = dictionaryCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getRequiredEngineVersion() { return requiredEngineVersion; }
    public void setRequiredEngineVersion(String requiredEngineVersion) { this.requiredEngineVersion = requiredEngineVersion; }
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
    public Long getEntryCount() { return entryCount; }
    public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
    public Long getResourceCount() { return resourceCount; }
    public void setResourceCount(Long resourceCount) { this.resourceCount = resourceCount; }
    public String getMdxFileName() { return mdxFileName; }
    public void setMdxFileName(String mdxFileName) { this.mdxFileName = mdxFileName; }
    public String getMddFileName() { return mddFileName; }
    public void setMddFileName(String mddFileName) { this.mddFileName = mddFileName; }
    public String getCoverImagePath() { return coverImagePath; }
    public void setCoverImagePath(String coverImagePath) { this.coverImagePath = coverImagePath; }
    public Long getMdxSizeBytes() { return mdxSizeBytes; }
    public void setMdxSizeBytes(Long mdxSizeBytes) { this.mdxSizeBytes = mdxSizeBytes; }
    public Long getMddSizeBytes() { return mddSizeBytes; }
    public void setMddSizeBytes(Long mddSizeBytes) { this.mddSizeBytes = mddSizeBytes; }
    public Long getExamplesCount() { return examplesCount; }
    public void setExamplesCount(Long examplesCount) { this.examplesCount = examplesCount; }
    public String getLicenseStatus() { return licenseStatus; }
    public void setLicenseStatus(String licenseStatus) { this.licenseStatus = licenseStatus; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
