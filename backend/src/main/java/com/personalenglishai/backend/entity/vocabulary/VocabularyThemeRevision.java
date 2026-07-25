package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class VocabularyThemeRevision {
    private Long id;
    private String revisionUid;
    private String themeUid;
    private Integer version;
    private String nameSnapshot;
    private String purpose;
    private String promptStrategyKey;
    private Integer contentFormatVersion;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRevisionUid() { return revisionUid; }
    public void setRevisionUid(String revisionUid) { this.revisionUid = revisionUid; }
    public String getThemeUid() { return themeUid; }
    public void setThemeUid(String themeUid) { this.themeUid = themeUid; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getNameSnapshot() { return nameSnapshot; }
    public void setNameSnapshot(String nameSnapshot) { this.nameSnapshot = nameSnapshot; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getPromptStrategyKey() { return promptStrategyKey; }
    public void setPromptStrategyKey(String promptStrategyKey) { this.promptStrategyKey = promptStrategyKey; }
    public Integer getContentFormatVersion() { return contentFormatVersion; }
    public void setContentFormatVersion(Integer contentFormatVersion) { this.contentFormatVersion = contentFormatVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
