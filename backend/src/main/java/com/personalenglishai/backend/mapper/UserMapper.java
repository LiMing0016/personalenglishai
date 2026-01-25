package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据邮箱查询用户
     */
    User findByEmail(@Param("email") String email);

    /**
     * 根据昵称查询用户（登录时username对应nickname）
     */
    User findByNickname(@Param("nickname") String nickname);

    /**
     * 插入新用户
     */
    int insert(User user);
}

