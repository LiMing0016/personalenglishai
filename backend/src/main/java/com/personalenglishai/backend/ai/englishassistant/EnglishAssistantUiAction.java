package com.personalenglishai.backend.ai.englishassistant;

public class EnglishAssistantUiAction {

    private String type;
    private String label;
    private String payloadText;

    public EnglishAssistantUiAction() {
    }

    public EnglishAssistantUiAction(String type, String label, String payloadText) {
        this.type = type;
        this.label = label;
        this.payloadText = payloadText;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public void setPayloadText(String payloadText) {
        this.payloadText = payloadText;
    }
}
