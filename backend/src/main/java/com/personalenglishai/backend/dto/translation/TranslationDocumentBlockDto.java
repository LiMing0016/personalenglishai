package com.personalenglishai.backend.dto.translation;

public class TranslationDocumentBlockDto {
    private String id;
    private String type;
    private int order;
    private int pageNumber;
    private String text;
    private Double confidence;

    public TranslationDocumentBlockDto() {
    }

    public TranslationDocumentBlockDto(String id, String type, int order, int pageNumber, String text, Double confidence) {
        this.id = id;
        this.type = type;
        this.order = order;
        this.pageNumber = pageNumber;
        this.text = text;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
