package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户档案 Mapper
 */
@Mapper
public interface UserProfileMapper {

    /**
     * 根据用户ID查询用户档案
     */
    UserProfile findByUserId(@Param("userId") Long userId);

    /**
     * 插入用户档案
     */
    int insert(UserProfile userProfile);

    /**
     * 更新用户档案的学段和AI模式
     */
    int updateStageAndAiMode(@Param("userId") Long userId,
                             @Param("studyStage") String studyStage,
                             @Param("aiMode") Integer aiMode);
}


