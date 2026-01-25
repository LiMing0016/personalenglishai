package com.personalenglishai.backend.dto;

/**
 * 注册请求
 */
public class RegisterRequest {
    private String username; // 用户名，3-20个字符
    private String email; // 邮箱
    private String password; // 密码，6-20个字符

    public RegisterRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

