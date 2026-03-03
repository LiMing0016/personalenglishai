package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.MeProfileResponse;
import com.personalenglishai.backend.controller.dto.UpdateNicknameRequest;
import com.personalenglishai.backend.dto.UpdateStageRequest;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.entity.UserProfile;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.UserProfileService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

/**
 * 用户档案控制器
 * GET /api/users/me/profile 需 JWT；userId 来自鉴权链路。
 */
@RestController
@RequestMapping("/api/users/me/profile")
public class UserProfileController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserProfileService userProfileService;
    private final UserMapper userMapper;

    public UserProfileController(UserProfileService userProfileService, UserMapper userMapper) {
        this.userProfileService = userProfileService;
        this.userMapper = userMapper;
    }

    /**
     * 获取当前用户档案（需 JWT）
     * GET /api/users/me/profile
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MeProfileResponse>> getProfile(
            @RequestAttribute("userId") Long userId) {
        User user = userMapper.findById(userId);
        MeProfileResponse data = user == null
                ? new MeProfileResponse(userId, null, null)
                : new MeProfileResponse(user.getId(), user.getEmail(), user.getNickname());

        if (user != null) {
            data.setEmailVerified(user.isEmailVerified());
            data.setPhone(maskPhone(user.getPhone()));
            data.setPhoneVerified(user.isPhoneVerified());
            data.setAvatarUrl(user.getAvatarUrl());
            data.setRegisterSource(user.getRegisterSource());
            data.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FMT) : null);
        }

        UserProfile profile = userProfileService.getUserProfile(userId);
        data.setStudyStage(profile.getStudyStage());
        data.setAiMode(profile.getAiMode());

        ApiResponse<MeProfileResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 修改昵称
     * PATCH /api/users/me/profile/nickname
     */
    @PatchMapping("/nickname")
    public ResponseEntity<Void> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            @RequestAttribute("userId") Long userId) {
        userMapper.updateNickname(userId, request.getNickname().trim());
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新学段
     * PATCH /api/users/me/profile/stage
     */
    @PatchMapping("/stage")
    public ResponseEntity<Void> updateStage(
            @RequestBody UpdateStageRequest request,
            @RequestAttribute("userId") Long userId) {
        userProfileService.updateStudyStage(userId, request);
        return ResponseEntity.noContent().build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
