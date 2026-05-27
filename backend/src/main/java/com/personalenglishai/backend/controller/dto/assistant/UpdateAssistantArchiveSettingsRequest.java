package com.personalenglishai.backend.controller.dto.assistant;

import jakarta.validation.constraints.Size;

public class UpdateAssistantArchiveSettingsRequest {
    @Size(max = 1000)
    private String archiveDir;

    public String getArchiveDir() {
        return archiveDir;
    }

    public void setArchiveDir(String archiveDir) {
        this.archiveDir = archiveDir;
    }
}
