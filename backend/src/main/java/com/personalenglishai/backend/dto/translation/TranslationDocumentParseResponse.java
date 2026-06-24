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
    private String fileUrl;
    private boolean filePersisted;
    private String storageProvider;
    private long elapsedMs;
    private String rawOcrResponse;
    private List<TranslationDocumentElementDto> elements = new ArrayList<>();
    private List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
    private List<TranslationKnowledgeChunkDto> knowledgeChunks = new ArrayList<>();
    private List<TranslationDocumentAssetDto> assets = new ArrayList<>();
    private TranslationParseDiagnosisDto diagnosis = new TranslationParseDiagnosisDto();
    private TranslationDocumentQualityDto quality = new TranslationDocumentQualityDto();
    private TranslationLanguageProfileDto languageProfile = new TranslationLanguageProfileDto();
    private TranslationParseJobDto parseJob = new TranslationParseJobDto();

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

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public boolean isFilePersisted() {
        return filePersisted;
    }

    public void setFilePersisted(boolean filePersisted) {
        this.filePersisted = filePersisted;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getRawOcrResponse() {
        return rawOcrResponse;
    }

    public void setRawOcrResponse(String rawOcrResponse) {
        this.rawOcrResponse = rawOcrResponse;
    }

    public List<TranslationDocumentElementDto> getElements() {
        return elements;
    }

    public void setElements(List<TranslationDocumentElementDto> elements) {
        this.elements = elements == null ? new ArrayList<>() : elements;
    }

    public List<TranslationDocumentOutlineItemDto> getOutline() {
        return outline;
    }

    public void setOutline(List<TranslationDocumentOutlineItemDto> outline) {
        this.outline = outline == null ? new ArrayList<>() : outline;
    }

    public List<TranslationKnowledgeChunkDto> getKnowledgeChunks() {
        return knowledgeChunks;
    }

    public void setKnowledgeChunks(List<TranslationKnowledgeChunkDto> knowledgeChunks) {
        this.knowledgeChunks = knowledgeChunks == null ? new ArrayList<>() : knowledgeChunks;
    }

    public List<TranslationDocumentAssetDto> getAssets() {
        return assets;
    }

    public void setAssets(List<TranslationDocumentAssetDto> assets) {
        this.assets = assets == null ? new ArrayList<>() : assets;
    }

    public TranslationParseDiagnosisDto getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(TranslationParseDiagnosisDto diagnosis) {
        this.diagnosis = diagnosis == null ? new TranslationParseDiagnosisDto() : diagnosis;
    }

    public TranslationDocumentQualityDto getQuality() {
        return quality;
    }

    public void setQuality(TranslationDocumentQualityDto quality) {
        this.quality = quality == null ? new TranslationDocumentQualityDto() : quality;
    }

    public TranslationLanguageProfileDto getLanguageProfile() {
        return languageProfile;
    }

    public void setLanguageProfile(TranslationLanguageProfileDto languageProfile) {
        this.languageProfile = languageProfile == null ? new TranslationLanguageProfileDto() : languageProfile;
    }

    public TranslationParseJobDto getParseJob() {
        return parseJob;
    }

    public void setParseJob(TranslationParseJobDto parseJob) {
        this.parseJob = parseJob == null ? new TranslationParseJobDto() : parseJob;
    }
}
