package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationSourceCitationDto {
    private String documentId;
    private String chunkId;
    private Integer pageNumber;
    private String elementId;
    private String bbox;
    private String quote;
    private List<String> sectionPath = new ArrayList<>();
    private double score;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public String getBbox() {
        return bbox;
    }

    public void setBbox(String bbox) {
        this.bbox = bbox;
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }

    public List<String> getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(List<String> sectionPath) {
        this.sectionPath = sectionPath == null ? new ArrayList<>() : sectionPath;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
