package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptSheetChatResponse {

    private String reply;
    private String action;
    private Boolean needsCanvasUpdate;
    private Boolean needsConfirmation;
    private String canvasInstruction;
    private Patch patch;
    private GenerateExamPromptResponse promptSheet;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Boolean getNeedsCanvasUpdate() {
        return needsCanvasUpdate;
    }

    public void setNeedsCanvasUpdate(Boolean needsCanvasUpdate) {
        this.needsCanvasUpdate = needsCanvasUpdate;
    }

    public Boolean getNeedsConfirmation() {
        return needsConfirmation;
    }

    public void setNeedsConfirmation(Boolean needsConfirmation) {
        this.needsConfirmation = needsConfirmation;
    }

    public String getCanvasInstruction() {
        return canvasInstruction;
    }

    public void setCanvasInstruction(String canvasInstruction) {
        this.canvasInstruction = canvasInstruction;
    }

    public Patch getPatch() {
        return patch;
    }

    public void setPatch(Patch patch) {
        this.patch = patch;
    }

    public GenerateExamPromptResponse getPromptSheet() {
        return promptSheet;
    }

    public void setPromptSheet(GenerateExamPromptResponse promptSheet) {
        this.promptSheet = promptSheet;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Patch {
        private String taskType;
        private String promptType;
        private String genre;
        private String wordRange;
        private String requirements;
        private String topic;

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getPromptType() {
            return promptType;
        }

        public void setPromptType(String promptType) {
            this.promptType = promptType;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public String getWordRange() {
            return wordRange;
        }

        public void setWordRange(String wordRange) {
            this.wordRange = wordRange;
        }

        public String getRequirements() {
            return requirements;
        }

        public void setRequirements(String requirements) {
            this.requirements = requirements;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }
    }
}
