package com.personalenglishai.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateNicknameRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 32, message = "昵称长度 1-32 个字符")
    private String nickname;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
