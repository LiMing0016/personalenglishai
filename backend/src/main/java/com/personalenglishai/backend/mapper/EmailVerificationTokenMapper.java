package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.EmailVerificationToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailVerificationTokenMapper {

    int insert(EmailVerificationToken token);

    EmailVerificationToken findByToken(@Param("token") String token);

    int markUsed(@Param("token") String token);

    /**
     * 使该用户指定前缀的未使用 token 失效（重新发送时清理旧 token）。
     * tokenPrefix 为 null 或空串时匹配不以 "rst-" 开头的邮箱验证 token；
     * tokenPrefix = "rst-" 时匹配密码重置 token。
     */
    int invalidateByUserId(@Param("userId") Long userId, @Param("tokenPrefix") String tokenPrefix);
}
