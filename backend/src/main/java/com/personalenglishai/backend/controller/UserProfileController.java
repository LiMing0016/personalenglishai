package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.MeProfileResponse;
import com.personalenglishai.backend.dto.UpdateStageRequest;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.UserProfileService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户档案控制器
 * GET /api/users/me/profile 需 JWT；userId 来自鉴权链路。
 */
@RestController
@RequestMapping("/api/users/me/profile")
public class UserProfileController {

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
        ApiResponse<MeProfileResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
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
}

