package com.personalenglishai.backend.entity.translation;

import java.time.LocalDateTime;

public class TranslationDocumentElementRecord {
    private Long id;
    private String documentId;
    private String elementId;
    private String elementType;
    private Integer elementOrder;
    private Integer pageNumber;
    private String text;
    private String bbox;
    private String provider;
    private Double confidence;
    private String recognitionStatus;
    private Double qualityScore;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }
    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public Integer getElementOrder() { return elementOrder; }
    public void setElementOrder(Integer elementOrder) { this.elementOrder = elementOrder; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getBbox() { return bbox; }
    public void setBbox(String bbox) { this.bbox = bbox; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getRecognitionStatus() { return recognitionStatus; }
    public void setRecognitionStatus(String recognitionStatus) { this.recognitionStatus = recognitionStatus; }
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
