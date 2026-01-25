package com.personalenglishai.backend.service;

import com.personalenglishai.backend.dto.UpdateStageRequest;
import com.personalenglishai.backend.entity.UserProfile;
import com.personalenglishai.backend.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户档案服务
 */
@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 更新用户学段
     * 如果 studyStage 为空，设置为普通模式（ai_mode=0）
     * 如果 studyStage 不为空，设置为学段模式（ai_mode=1）
     */
    @Transactional
    public void updateStudyStage(Long userId, UpdateStageRequest request) {
        String studyStage = request.getStudyStage();
        Integer aiMode;

        // 判断学段是否为空
        if (studyStage == null || studyStage.trim().isEmpty()) {
            // 学段为空，返回普通模式
            studyStage = null;
            aiMode = 0;
        } else {
            // 学段不为空，设置为学段模式
            aiMode = 1;
        }

        // 查询用户档案是否存在
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            // 不存在则创建
            profile = new UserProfile(userId, studyStage, aiMode);
            userProfileMapper.insert(profile);
        } else {
            // 存在则更新
            userProfileMapper.updateStageAndAiMode(userId, studyStage, aiMode);
        }
    }

    /**
     * 根据用户ID获取用户档案
     */
    public UserProfile getUserProfile(Long userId) {
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            // 如果不存在，返回默认配置（普通模式）
            return new UserProfile(userId, null, 0);
        }
        return profile;
    }
}


