package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationKnowledgeChunkDto {
    private String id;
    private int chunkOrder;
    private String chunkType;
    private String content;
    private String summary;
    private List<String> sourceElementIds = new ArrayList<>();
    private List<Integer> pageNumbers = new ArrayList<>();
    private int tokenCount;
    private double qualityScore;
    private String embeddingStatus = "NOT_REQUIRED";
    private String granularity = "small";
    private int startElementOrder;
    private int endElementOrder;
    private List<String> sectionPath = new ArrayList<>();
    private String parentChunkId;
    private String prevChunkId;
    private String nextChunkId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(int chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getSourceElementIds() {
        return sourceElementIds;
    }

    public void setSourceElementIds(List<String> sourceElementIds) {
        this.sourceElementIds = sourceElementIds == null ? new ArrayList<>() : sourceElementIds;
    }

    public List<Integer> getPageNumbers() {
        return pageNumbers;
    }

    public void setPageNumbers(List<Integer> pageNumbers) {
        this.pageNumbers = pageNumbers == null ? new ArrayList<>() : pageNumbers;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getEmbeddingStatus() {
        return embeddingStatus;
    }

    public void setEmbeddingStatus(String embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }

    public int getStartElementOrder() {
        return startElementOrder;
    }

    public void setStartElementOrder(int startElementOrder) {
        this.startElementOrder = startElementOrder;
    }

    public int getEndElementOrder() {
        return endElementOrder;
    }

    public void setEndElementOrder(int endElementOrder) {
        this.endElementOrder = endElementOrder;
    }

    public List<String> getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(List<String> sectionPath) {
        this.sectionPath = sectionPath == null ? new ArrayList<>() : sectionPath;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public void setParentChunkId(String parentChunkId) {
        this.parentChunkId = parentChunkId;
    }

    public String getPrevChunkId() {
        return prevChunkId;
    }

    public void setPrevChunkId(String prevChunkId) {
        this.prevChunkId = prevChunkId;
    }

    public String getNextChunkId() {
        return nextChunkId;
    }

    public void setNextChunkId(String nextChunkId) {
        this.nextChunkId = nextChunkId;
    }
}
