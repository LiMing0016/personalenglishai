package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;

public class BindHandwritingImportRequest {

    @NotBlank(message = "文档ID不能为空")
    private String docId;

    private String sourceType;

    @NotBlank(message = "图片地址不能为空")
    private String imageUrl;

    @NotBlank(message = "识别文本不能为空")
    private String recognizedText;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRecognizedText() {
        return recognizedText;
    }

    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
    }
}
