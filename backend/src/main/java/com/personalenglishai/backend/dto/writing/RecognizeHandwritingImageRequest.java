package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;

public class RecognizeHandwritingImageRequest {

    @NotBlank(message = "图片内容不能为空")
    private String imageBase64;

    private String aiProvider;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }
}
