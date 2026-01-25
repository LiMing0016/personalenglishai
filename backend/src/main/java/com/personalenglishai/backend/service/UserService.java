package com.personalenglishai.backend.service;

import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 用户服务
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // 邮箱格式正则
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 注册新用户
     */
    @Transactional
    public User register(String username, String email, String password) {
        // 参数验证
        validateRegisterRequest(username, email, password);

        // 检查邮箱是否已存在
        if (userMapper.findByEmail(email) != null) {
            throw new IllegalStateException("邮箱已被注册");
        }

        // 密码加密（使用 BCrypt）
        String hashedPassword = passwordEncoder.encode(password);

        // 创建用户：username 映射为 nickname
        User user = new User();
        user.setEmail(email);
        user.setNickname(username);
        user.setPasswordHash(hashedPassword);
        userMapper.insert(user);

        return user;
    }

    /**
     * 验证注册请求参数
     */
    private void validateRegisterRequest(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw new IllegalArgumentException("用户名长度必须在3-20个字符之间");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new IllegalArgumentException("密码长度必须在6-20个字符之间");
        }
    }


    /**
     * 根据邮箱查找用户
     */
    public User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    /**
     * 根据昵称查找用户（登录时username对应nickname）
     */
    public User findByNickname(String nickname) {
        return userMapper.findByNickname(nickname);
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}

