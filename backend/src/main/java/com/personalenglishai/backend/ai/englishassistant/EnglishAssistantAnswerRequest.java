package com.personalenglishai.backend.ai.englishassistant;

public class EnglishAssistantAnswerRequest {

    private String conversationId;
    private String scope;
    private String taskType;
    private boolean useDraftContext;
    private String message;
    private String assignmentText;
    private String selectedText;
    private String draftText;
    private String assistantOutputText;
    private String rubricKey;
    private String rubricSummary;
    private String recentTurnsText;
    private String summaryText;
    private String trimmedContextMode;
    private String artifactChain;
    private String previousResponseId;
    private String promptCacheKey;
    private Long userId;
    private String traceId;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public boolean getUseDraftContext() {
        return useDraftContext;
    }

    public void setUseDraftContext(boolean useDraftContext) {
        this.useDraftContext = useDraftContext;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAssignmentText() {
        return assignmentText;
    }

    public void setAssignmentText(String assignmentText) {
        this.assignmentText = assignmentText;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public String getDraftText() {
        return draftText;
    }

    public void setDraftText(String draftText) {
        this.draftText = draftText;
    }

    public String getAssistantOutputText() {
        return assistantOutputText;
    }

    public void setAssistantOutputText(String assistantOutputText) {
        this.assistantOutputText = assistantOutputText;
    }

    public String getRubricKey() {
        return rubricKey;
    }

    public void setRubricKey(String rubricKey) {
        this.rubricKey = rubricKey;
    }

    public String getRubricSummary() {
        return rubricSummary;
    }

    public void setRubricSummary(String rubricSummary) {
        this.rubricSummary = rubricSummary;
    }

    public String getRecentTurnsText() {
        return recentTurnsText;
    }

    public void setRecentTurnsText(String recentTurnsText) {
        this.recentTurnsText = recentTurnsText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getTrimmedContextMode() {
        return trimmedContextMode;
    }

    public void setTrimmedContextMode(String trimmedContextMode) {
        this.trimmedContextMode = trimmedContextMode;
    }

    public String getArtifactChain() {
        return artifactChain;
    }

    public void setArtifactChain(String artifactChain) {
        this.artifactChain = artifactChain;
    }

    public String getPreviousResponseId() {
        return previousResponseId;
    }

    public void setPreviousResponseId(String previousResponseId) {
        this.previousResponseId = previousResponseId;
    }

    public String getPromptCacheKey() {
        return promptCacheKey;
    }

    public void setPromptCacheKey(String promptCacheKey) {
        this.promptCacheKey = promptCacheKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
