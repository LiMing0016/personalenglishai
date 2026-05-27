package com.personalenglishai.backend.controller.dto.assistant;

public class ChatKitSessionResponse {
    private String clientSecret;
    private String sessionId;
    private Long expiresAt;

    public ChatKitSessionResponse() {
    }

    public ChatKitSessionResponse(String clientSecret, String sessionId, Long expiresAt) {
        this.clientSecret = clientSecret;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
