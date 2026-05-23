package com.personalenglishai.backend.controller.dto.assistant;

public class AssistantArchiveSettingsResponse {
    private String archiveDir;
    private String defaultArchiveDir;
    private boolean custom;

    public AssistantArchiveSettingsResponse(String archiveDir, String defaultArchiveDir, boolean custom) {
        this.archiveDir = archiveDir;
        this.defaultArchiveDir = defaultArchiveDir;
        this.custom = custom;
    }

    public String getArchiveDir() {
        return archiveDir;
    }

    public String getDefaultArchiveDir() {
        return defaultArchiveDir;
    }

    public boolean isCustom() {
        return custom;
    }
}
