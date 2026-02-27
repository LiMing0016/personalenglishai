package com.personalenglishai.backend.service.auth.impl;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.auth.dto.LoginResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long jwtExpireSeconds;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                           @Value("${jwt.expireSeconds:86400}") long jwtExpireSeconds) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    @Override
    @Transactional
    public Long register(String email, String password, String nickname) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String nick = nickname == null ? "" : nickname.trim();

        if (userMapper.findByEmail(normalizedEmail) != null) {
            throw new BizException(ErrorCode.AUTH_EMAIL_EXISTS);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setNickname(nick);
        user.setPasswordHash(passwordHash);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.AUTH_EMAIL_EXISTS);
        }
        return user.getId();
    }

    @Override
    public LoginResponse login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userMapper.findByEmail(normalizedEmail);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getNickname());
        return new LoginResponse(token, jwtExpireSeconds);
    }
}
