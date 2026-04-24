package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditTopicResponse {

    /** complete: 信息完整 | need_more_info: 缺失信息 | invalid: 无效输入 */
    private String status;

    private String topic;
    private String promptType;
    private String genre;
    private String wordRange;

    /** 写作要求/要点 */
    private String requirements;

    /** AI 给用户的提示信息 */
    private String message;

    private Boolean isCompleteOriginalPrompt;
    private Boolean shouldPreserveOriginalWording;
    private Boolean isExamStyleCompatible;
    private List<String> styleCompatibilityReasons;
    private List<String> missingFields;
    private Boolean requiresUserConfirmation;
    private String confirmationQuestion;
    private String targetStyle;
    private Boolean needsMoreInfo;
    private String assistantReply;
    private Boolean promptReady;
    private String readyReason;
    private String nextAction;
    private Boolean needsAttachment;
    private String attachmentType;
    private String attachmentSource;
    private Boolean attachmentReady;
    private String attachmentTitle;
    private String attachmentInstruction;
    private Map<String, Object> attachmentPayload;

    public AuditTopicResponse() {}

    public static AuditTopicResponse complete(String topic, String promptType, String genre, String wordRange, String requirements) {
        var r = new AuditTopicResponse();
        r.status = "complete";
        r.topic = topic;
        r.promptType = promptType;
        r.genre = genre;
        r.wordRange = wordRange;
        r.requirements = requirements;
        return r;
    }

    public static AuditTopicResponse needMoreInfo(String topic, String promptType, String genre, String wordRange, String requirements, String message) {
        var r = new AuditTopicResponse();
        r.status = "need_more_info";
        r.topic = topic;
        r.promptType = promptType;
        r.genre = genre;
        r.wordRange = wordRange;
        r.requirements = requirements;
        r.message = message;
        return r;
    }

    public static AuditTopicResponse invalid(String message) {
        var r = new AuditTopicResponse();
        r.status = "invalid";
        r.message = message;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getPromptType() { return promptType; }
    public void setPromptType(String promptType) { this.promptType = promptType; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getWordRange() { return wordRange; }
    public void setWordRange(String wordRange) { this.wordRange = wordRange; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getIsCompleteOriginalPrompt() { return isCompleteOriginalPrompt; }
    public void setIsCompleteOriginalPrompt(Boolean isCompleteOriginalPrompt) { this.isCompleteOriginalPrompt = isCompleteOriginalPrompt; }

    public Boolean getShouldPreserveOriginalWording() { return shouldPreserveOriginalWording; }
    public void setShouldPreserveOriginalWording(Boolean shouldPreserveOriginalWording) { this.shouldPreserveOriginalWording = shouldPreserveOriginalWording; }

    public Boolean getIsExamStyleCompatible() { return isExamStyleCompatible; }
    public void setIsExamStyleCompatible(Boolean isExamStyleCompatible) { this.isExamStyleCompatible = isExamStyleCompatible; }

    public List<String> getStyleCompatibilityReasons() { return styleCompatibilityReasons; }
    public void setStyleCompatibilityReasons(List<String> styleCompatibilityReasons) { this.styleCompatibilityReasons = styleCompatibilityReasons; }

    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }

    public Boolean getRequiresUserConfirmation() { return requiresUserConfirmation; }
    public void setRequiresUserConfirmation(Boolean requiresUserConfirmation) { this.requiresUserConfirmation = requiresUserConfirmation; }

    public String getConfirmationQuestion() { return confirmationQuestion; }
    public void setConfirmationQuestion(String confirmationQuestion) { this.confirmationQuestion = confirmationQuestion; }

    public String getTargetStyle() { return targetStyle; }
    public void setTargetStyle(String targetStyle) { this.targetStyle = targetStyle; }

    public Boolean getNeedsMoreInfo() { return needsMoreInfo; }
    public void setNeedsMoreInfo(Boolean needsMoreInfo) { this.needsMoreInfo = needsMoreInfo; }

    public String getAssistantReply() { return assistantReply; }
    public void setAssistantReply(String assistantReply) { this.assistantReply = assistantReply; }

    public Boolean getPromptReady() { return promptReady; }
    public void setPromptReady(Boolean promptReady) { this.promptReady = promptReady; }

    public String getReadyReason() { return readyReason; }
    public void setReadyReason(String readyReason) { this.readyReason = readyReason; }

    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }

    public Boolean getNeedsAttachment() { return needsAttachment; }
    public void setNeedsAttachment(Boolean needsAttachment) { this.needsAttachment = needsAttachment; }

    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }

    public String getAttachmentSource() { return attachmentSource; }
    public void setAttachmentSource(String attachmentSource) { this.attachmentSource = attachmentSource; }

    public Boolean getAttachmentReady() { return attachmentReady; }
    public void setAttachmentReady(Boolean attachmentReady) { this.attachmentReady = attachmentReady; }

    public String getAttachmentTitle() { return attachmentTitle; }
    public void setAttachmentTitle(String attachmentTitle) { this.attachmentTitle = attachmentTitle; }

    public String getAttachmentInstruction() { return attachmentInstruction; }
    public void setAttachmentInstruction(String attachmentInstruction) { this.attachmentInstruction = attachmentInstruction; }

    public Map<String, Object> getAttachmentPayload() { return attachmentPayload; }
    public void setAttachmentPayload(Map<String, Object> attachmentPayload) { this.attachmentPayload = attachmentPayload; }
}
