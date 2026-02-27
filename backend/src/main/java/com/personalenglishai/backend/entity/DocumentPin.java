package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

/**
 * 文档固定引用预留实体
 */
public class DocumentPin {

    private Long id;
    private Long documentId;
    private String pinId;
    private String type;
    private String payload;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getPinId() { return pinId; }
    public void setPinId(String pinId) { this.pinId = pinId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
