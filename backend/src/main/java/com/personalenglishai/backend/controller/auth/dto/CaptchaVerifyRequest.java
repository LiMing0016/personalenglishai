package com.personalenglishai.backend.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class CaptchaVerifyRequest {
    @NotBlank(message = "captchaId 不能为空")
    private String captchaId;

    private int x;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
}
