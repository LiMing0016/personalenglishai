package com.personalenglishai.backend.controller.dto.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AssistantRequest {
    @Size(max = 64)
    private String appConversationId;

    @NotBlank
    @Size(max = 128)
    private String clientMessageId;

    @Size(max = 128)
    private String idempotencyKey;

    @Size(max = 32)
    private String agentMode;

    @NotBlank
    @Size(max = 32)
    private String mode;

    @NotBlank
    @Size(max = 32)
    private String intent;

    @Size(max = 64)
    private String scope;

    @Valid
    private Message message = new Message();

    @Valid
    private Selection selection;

    @Valid
    @Size(max = 5)
    private List<AssistantAttachmentRef> attachments = List.of();

    @Valid
    private StudyContext studyContext;

    @Valid
    private ClientMeta clientMeta;

    @Valid
    private Interaction interaction;

    @Valid
    @Size(max = 20)
    private List<ConversationHistoryMessage> conversationHistory = List.of();

    public String getAppConversationId() {
        return appConversationId;
    }

    public void setAppConversationId(String appConversationId) {
        this.appConversationId = appConversationId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(String agentMode) {
        this.agentMode = agentMode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Selection getSelection() {
        return selection;
    }

    public void setSelection(Selection selection) {
        this.selection = selection;
    }

    public List<AssistantAttachmentRef> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AssistantAttachmentRef> attachments) {
        this.attachments = attachments == null ? List.of() : attachments;
    }

    public StudyContext getStudyContext() {
        return studyContext;
    }

    public void setStudyContext(StudyContext studyContext) {
        this.studyContext = studyContext;
    }

    public ClientMeta getClientMeta() {
        return clientMeta;
    }

    public void setClientMeta(ClientMeta clientMeta) {
        this.clientMeta = clientMeta;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public void setInteraction(Interaction interaction) {
        this.interaction = interaction;
    }

    public List<ConversationHistoryMessage> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<ConversationHistoryMessage> conversationHistory) {
        this.conversationHistory = conversationHistory == null ? List.of() : conversationHistory;
    }

    public static class Message {
        @Size(max = 8000)
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class Selection {
        @Size(max = 8000)
        private String text;

        @Size(max = 64)
        private String source;

        @Size(max = 128)
        private String sourceId;

        @Size(max = 128)
        private String messageId;

        @Size(max = 128)
        private String documentId;

        private Range range;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public Range getRange() {
            return range;
        }

        public void setRange(Range range) {
            this.range = range;
        }
    }

    public static class Range {
        private Integer start;
        private Integer end;

        public Integer getStart() {
            return start;
        }

        public void setStart(Integer start) {
            this.start = start;
        }

        public Integer getEnd() {
            return end;
        }

        public void setEnd(Integer end) {
            this.end = end;
        }
    }

    public static class StudyContext {
        private String studyStage;
        private String cefrLevel;
        private String targetExam;
        private String locale;
        private String responseLanguage;

        public String getStudyStage() {
            return studyStage;
        }

        public void setStudyStage(String studyStage) {
            this.studyStage = studyStage;
        }

        public String getCefrLevel() {
            return cefrLevel;
        }

        public void setCefrLevel(String cefrLevel) {
            this.cefrLevel = cefrLevel;
        }

        public String getTargetExam() {
            return targetExam;
        }

        public void setTargetExam(String targetExam) {
            this.targetExam = targetExam;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public String getResponseLanguage() {
            return responseLanguage;
        }

        public void setResponseLanguage(String responseLanguage) {
            this.responseLanguage = responseLanguage;
        }
    }

    public static class ClientMeta {
        private String sourcePage;
        private String timezone;
        private String userAgent;

        public String getSourcePage() {
            return sourcePage;
        }

        public void setSourcePage(String sourcePage) {
            this.sourcePage = sourcePage;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Interaction {
        @Size(max = 32)
        private String source;

        @Size(max = 64)
        private String uiIntent;

        @Size(max = 128)
        private String activeActivityId;

        @Size(max = 128)
        private String actionId;

        @Valid
        private InteractionContext context;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getUiIntent() {
            return uiIntent;
        }

        public void setUiIntent(String uiIntent) {
            this.uiIntent = uiIntent;
        }

        public String getActiveActivityId() {
            return activeActivityId;
        }

        public void setActiveActivityId(String activeActivityId) {
            this.activeActivityId = activeActivityId;
        }

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public InteractionContext getContext() {
            return context;
        }

        public void setContext(InteractionContext context) {
            this.context = context;
        }
    }

    public static class InteractionContext {
        @Size(max = 64)
        private String exerciseType;

        @Size(max = 256)
        private String topic;

        @Size(max = 32)
        private String difficulty;

        public String getExerciseType() {
            return exerciseType;
        }

        public void setExerciseType(String exerciseType) {
            this.exerciseType = exerciseType;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }
    }

    public static class ConversationHistoryMessage {
        @NotBlank
        @Size(max = 16)
        private String role;

        @NotBlank
        @Size(max = 4000)
        private String content;

        public ConversationHistoryMessage() {
        }

        public ConversationHistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
