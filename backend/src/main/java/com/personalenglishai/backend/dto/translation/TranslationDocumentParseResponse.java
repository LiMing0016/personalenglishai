package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationDocumentParseResponse {
    private String documentId;
    private String fileName;
    private String sourceType;
    private String parseStatus;
    private String ocrStatus;
    private int pageCount;
    private int blockCount;
    private List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String provider;
    private String parseMode;
    private boolean fallbackUsed;
    private long elapsedMs;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    public List<TranslationDocumentBlockDto> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<TranslationDocumentBlockDto> blocks) {
        this.blocks = blocks == null ? new ArrayList<>() : blocks;
        this.blockCount = this.blocks.size();
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getParseMode() {
        return parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
