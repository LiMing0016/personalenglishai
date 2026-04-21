package com.personalenglishai.backend.dto.writing;

import java.util.ArrayList;
import java.util.List;

public class GenerateExamDialogueTurnResponse {

    private String assistantReply;
    private List<AssistantReplyBlock> assistantReplyBlocks = new ArrayList<>();
    private String previewStatus;
    private List<String> missingFields = new ArrayList<>();
    private GenerateExamPromptResponse promptSheetDraft;

    public String getAssistantReply() {
        return assistantReply;
    }

    public void setAssistantReply(String assistantReply) {
        this.assistantReply = assistantReply;
    }

    public List<AssistantReplyBlock> getAssistantReplyBlocks() {
        return assistantReplyBlocks;
    }

    public void setAssistantReplyBlocks(List<AssistantReplyBlock> assistantReplyBlocks) {
        this.assistantReplyBlocks = assistantReplyBlocks;
    }

    public String getPreviewStatus() {
        return previewStatus;
    }

    public void setPreviewStatus(String previewStatus) {
        this.previewStatus = previewStatus;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public GenerateExamPromptResponse getPromptSheetDraft() {
        return promptSheetDraft;
    }

    public void setPromptSheetDraft(GenerateExamPromptResponse promptSheetDraft) {
        this.promptSheetDraft = promptSheetDraft;
    }

    public static class AssistantReplyBlock {
        private String kind;
        private String text;

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
    }
}
