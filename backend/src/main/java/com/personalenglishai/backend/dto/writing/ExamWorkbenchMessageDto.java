package com.personalenglishai.backend.dto.writing;

public class ExamWorkbenchMessageDto {

    private String role;
    private String kind;
    private String text;
    private String assetType;
    private String assetSummary;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetSummary() {
        return assetSummary;
    }

    public void setAssetSummary(String assetSummary) {
        this.assetSummary = assetSummary;
    }
}
