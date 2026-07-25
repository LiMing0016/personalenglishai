package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class VocabularyCardRevision {
    private Long id;
    private String revisionUid;
    private String cardUid;
    private String baseRevisionUid;
    private String authorType;
    private String templateKey;
    private Integer templateVersion;
    private String themeUid;
    private Integer themeVersion;
    private String contentJson;
    private String coreJson;
    private String cardBlocksJson;
    private Integer cardBlocksSchemaVersion;
    private String contentMarkdown;
    private Integer contentFormatVersion;
    private String generationMetadataJson;
    private String changeSummary;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRevisionUid() { return revisionUid; }
    public void setRevisionUid(String revisionUid) { this.revisionUid = revisionUid; }
    public String getCardUid() { return cardUid; }
    public void setCardUid(String cardUid) { this.cardUid = cardUid; }
    public String getBaseRevisionUid() { return baseRevisionUid; }
    public void setBaseRevisionUid(String baseRevisionUid) { this.baseRevisionUid = baseRevisionUid; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public String getThemeUid() { return themeUid; }
    public void setThemeUid(String themeUid) { this.themeUid = themeUid; }
    public Integer getThemeVersion() { return themeVersion; }
    public void setThemeVersion(Integer themeVersion) { this.themeVersion = themeVersion; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getCoreJson() { return coreJson; }
    public void setCoreJson(String coreJson) { this.coreJson = coreJson; }
    public String getCardBlocksJson() { return cardBlocksJson; }
    public void setCardBlocksJson(String cardBlocksJson) { this.cardBlocksJson = cardBlocksJson; }
    public Integer getCardBlocksSchemaVersion() { return cardBlocksSchemaVersion; }
    public void setCardBlocksSchemaVersion(Integer cardBlocksSchemaVersion) {
        this.cardBlocksSchemaVersion = cardBlocksSchemaVersion;
    }
    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }
    public Integer getContentFormatVersion() { return contentFormatVersion; }
    public void setContentFormatVersion(Integer contentFormatVersion) { this.contentFormatVersion = contentFormatVersion; }
    public String getGenerationMetadataJson() { return generationMetadataJson; }
    public void setGenerationMetadataJson(String generationMetadataJson) { this.generationMetadataJson = generationMetadataJson; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
