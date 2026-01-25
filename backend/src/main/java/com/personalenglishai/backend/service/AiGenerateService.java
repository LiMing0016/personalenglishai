package com.personalenglishai.backend.service;

import com.personalenglishai.backend.entity.UserProfile;
import org.springframework.stereotype.Service;

/**
 * AI 生成服务
 * 注意：这是模拟实现，实际应该调用真实的 AI API
 */
@Service
public class AiGenerateService {

    private final UserProfileService userProfileService;

    public AiGenerateService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * 根据用户配置生成 AI 内容
     */
    public String generate(Long userId, String prompt) {
        UserProfile profile = userProfileService.getUserProfile(userId);
        Integer aiMode = profile.getAiMode();

        if (aiMode == null || aiMode == 0) {
            // 普通模式：不做学段假设，给出通用 GPT 输出
            return generateGeneralResponse(prompt);
        } else {
            // 学段模式：按学段生成反馈
            String studyStage = profile.getStudyStage();
            return generateStageBasedResponse(prompt, studyStage);
        }
    }

    /**
     * 生成通用响应（普通模式）
     */
    private String generateGeneralResponse(String prompt) {
        // 模拟通用 AI 响应
        return String.format("[通用模式] 针对您的输入：\"%s\"，我为您提供以下建议和分析。", prompt);
    }

    /**
     * 生成学段相关响应（学段模式）
     */
    private String generateStageBasedResponse(String prompt, String studyStage) {
        // 模拟按学段生成的 AI 响应
        String stageName = studyStage != null ? studyStage : "未知学段";
        return String.format("[%s模式] 针对您的输入：\"%s\"，我为您提供符合%s考试要求的专业反馈和建议。", 
                stageName, prompt, stageName);
    }
}


