package com.personalenglishai.backend.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SendSmsCodeRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "用途不能为空")
    @Pattern(regexp = "^(login|register)$", message = "用途只能为 login 或 register")
    private String purpose;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
}
