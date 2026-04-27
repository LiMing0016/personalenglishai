package com.personalenglishai.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GrammarLearningEvent {
    private String eventId;
    private Long userId;
    private String conversationId;
    private String messageId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String studyStage;
    private String assistantMode;
    private String sourceAgent;
    private String taskType;
    private String contentOrigin;
    private Boolean profileEligible;
    private BigDecimal confidence;
    private String schemaVersion;
    private String skillVersion;
    private String taxonomyVersion;
    private String promptVersion;
    private String modelVersion;
    private String grammarQuestionType;
    private String grammarErrorType;
    private String styleIssueType;
    private String severity;
    private String sentenceHash;
    private String payloadJson;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getAssistantMode() {
        return assistantMode;
    }

    public void setAssistantMode(String assistantMode) {
        this.assistantMode = assistantMode;
    }

    public String getSourceAgent() {
        return sourceAgent;
    }

    public void setSourceAgent(String sourceAgent) {
        this.sourceAgent = sourceAgent;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getContentOrigin() {
        return contentOrigin;
    }

    public void setContentOrigin(String contentOrigin) {
        this.contentOrigin = contentOrigin;
    }

    public Boolean getProfileEligible() {
        return profileEligible;
    }

    public void setProfileEligible(Boolean profileEligible) {
        this.profileEligible = profileEligible;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion;
    }

    public String getTaxonomyVersion() {
        return taxonomyVersion;
    }

    public void setTaxonomyVersion(String taxonomyVersion) {
        this.taxonomyVersion = taxonomyVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getGrammarQuestionType() {
        return grammarQuestionType;
    }

    public void setGrammarQuestionType(String grammarQuestionType) {
        this.grammarQuestionType = grammarQuestionType;
    }

    public String getGrammarErrorType() {
        return grammarErrorType;
    }

    public void setGrammarErrorType(String grammarErrorType) {
        this.grammarErrorType = grammarErrorType;
    }

    public String getStyleIssueType() {
        return styleIssueType;
    }

    public void setStyleIssueType(String styleIssueType) {
        this.styleIssueType = styleIssueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSentenceHash() {
        return sentenceHash;
    }

    public void setSentenceHash(String sentenceHash) {
        this.sentenceHash = sentenceHash;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
