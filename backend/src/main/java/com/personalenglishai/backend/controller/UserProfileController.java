package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.dto.UpdateStageRequest;
import com.personalenglishai.backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户档案控制器
 */
@RestController
@RequestMapping("/api/users/me/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * 更新学段
     * PATCH /api/users/me/profile/stage
     */
    @PatchMapping("/stage")
    public ResponseEntity<Void> updateStage(@RequestBody UpdateStageRequest request,
                                           @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        userProfileService.updateStudyStage(userId, request);
        return ResponseEntity.noContent().build();
    }
}


