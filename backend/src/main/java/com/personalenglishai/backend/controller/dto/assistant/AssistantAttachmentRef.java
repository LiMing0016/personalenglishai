package com.personalenglishai.backend.controller.dto.assistant;

public class AssistantAttachmentRef {
    private String attachmentId;
    private String provider;
    private String openaiFileId;
    private String storageKey;
    private String url;
    private String name;
    private String mimeType;
    private Long sizeBytes;
    private String kind;
    private Processing processing;
    private ModelInput modelInput;

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getOpenaiFileId() {
        return openaiFileId;
    }

    public void setOpenaiFileId(String openaiFileId) {
        this.openaiFileId = openaiFileId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Processing getProcessing() {
        return processing;
    }

    public void setProcessing(Processing processing) {
        this.processing = processing;
    }

    public ModelInput getModelInput() {
        return modelInput;
    }

    public void setModelInput(ModelInput modelInput) {
        this.modelInput = modelInput;
    }

    public static class Processing {
        private String status;
        private String errorCode;
        private Boolean extractedTextAvailable;
        private String extractedText;
        private Integer pageCount;
        private String checksum;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public Boolean getExtractedTextAvailable() {
            return extractedTextAvailable;
        }

        public void setExtractedTextAvailable(Boolean extractedTextAvailable) {
            this.extractedTextAvailable = extractedTextAvailable;
        }

        public String getExtractedText() {
            return extractedText;
        }

        public void setExtractedText(String extractedText) {
            this.extractedText = extractedText;
        }

        public Integer getPageCount() {
            return pageCount;
        }

        public void setPageCount(Integer pageCount) {
            this.pageCount = pageCount;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }
    }

    public static class ModelInput {
        private String preferredPart;
        private String imageDetail;

        public String getPreferredPart() {
            return preferredPart;
        }

        public void setPreferredPart(String preferredPart) {
            this.preferredPart = preferredPart;
        }

        public String getImageDetail() {
            return imageDetail;
        }

        public void setImageDetail(String imageDetail) {
            this.imageDetail = imageDetail;
        }
    }
}
