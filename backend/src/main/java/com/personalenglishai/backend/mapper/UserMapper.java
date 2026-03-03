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
     * 根据主键查询用户（用于 /api/users/me/profile 等）
     */
    User findById(@Param("id") Long id);

    /**
     * 根据昵称查询用户（登录时username对应nickname）
     */
    User findByNickname(@Param("nickname") String nickname);

    /**
     * 插入新用户
     */
    int insert(User user);

    /**
     * 更新邮箱验证状态
     */
    int updateEmailVerified(@Param("id") Long id, @Param("emailVerified") boolean emailVerified);

    /**
     * 更新密码
     */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 根据手机号查询用户
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 更新手机验证状态
     */
    int updatePhoneVerified(@Param("id") Long id, @Param("phoneVerified") boolean phoneVerified);

    /**
     * 更新昵称
     */
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);
}

