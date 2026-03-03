package com.personalenglishai.backend.controller.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 登录响应 DTO
 * <p>
 * accessToken 通过 JSON body 返回给前端，
 * refreshToken 由 Controller 设置到 httpOnly cookie，不序列化到 body。
 */
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;

    @JsonIgnore
    private String refreshToken;

    @JsonIgnore
    private long refreshTokenMaxAge;

    public LoginResponse() {}

    public LoginResponse(String token, String refreshToken, long expiresIn, long refreshTokenMaxAge) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshTokenMaxAge = refreshTokenMaxAge;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getRefreshTokenMaxAge() { return refreshTokenMaxAge; }
    public void setRefreshTokenMaxAge(long refreshTokenMaxAge) { this.refreshTokenMaxAge = refreshTokenMaxAge; }
}
