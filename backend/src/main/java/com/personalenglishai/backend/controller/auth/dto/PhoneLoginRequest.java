package com.personalenglishai.backend.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PhoneLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "登录模式不能为空")
    @Pattern(regexp = "^(otp|password)$", message = "模式只能为 otp 或 password")
    private String mode;

    private String code;
    private String password;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
