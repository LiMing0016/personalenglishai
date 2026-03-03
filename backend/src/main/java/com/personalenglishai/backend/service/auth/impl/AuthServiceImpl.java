package com.personalenglishai.backend.service.auth.impl;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.auth.dto.LoginResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.service.auth.LoginAttemptService;
import com.personalenglishai.backend.util.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                           LoginAttemptService loginAttemptService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
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

        if (loginAttemptService.isBlocked(normalizedEmail)) {
            throw new BizException(ErrorCode.AUTH_LOGIN_RATE_LIMITED);
        }

        User user = userMapper.findByEmail(normalizedEmail);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(normalizedEmail);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        loginAttemptService.clearAttempts(normalizedEmail);
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse loginByPhone(String phone) {
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_PHONE_NOT_FOUND);
        }
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse loginByPhonePassword(String phone, String password) {
        if (loginAttemptService.isBlocked(phone)) {
            throw new BizException(ErrorCode.AUTH_LOGIN_RATE_LIMITED);
        }

        User user = userMapper.findByPhone(phone);
        if (user == null) {
            loginAttemptService.recordFailure(phone);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BizException(ErrorCode.AUTH_PHONE_NO_PASSWORD);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(phone);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        loginAttemptService.clearAttempts(phone);
        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse registerByPhone(String phone, String nickname) {
        String nick = nickname == null ? "" : nickname.trim();

        if (userMapper.findByPhone(phone) != null) {
            throw new BizException(ErrorCode.AUTH_PHONE_EXISTS);
        }

        User user = new User();
        user.setPhone(phone);
        user.setPhoneVerified(true);
        user.setNickname(nick);
        user.setRegisterSource("phone");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.AUTH_PHONE_EXISTS);
        }

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        int tokenVersion = jwtUtil.getTokenVersion(refreshToken);

        // 校验 tokenVersion：密码重置后 version 会自增，旧 token 失效
        User user = userMapper.findById(userId);
        if (user == null || user.getTokenVersion() != tokenVersion) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BizException(ErrorCode.AUTH_PHONE_NO_PASSWORD);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTH_CURRENT_PASSWORD_WRONG);
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getNickname(), user.getTokenVersion());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getNickname(), user.getTokenVersion());
        return new LoginResponse(accessToken, refreshToken,
                jwtUtil.getAccessTokenSeconds(), jwtUtil.getRefreshTokenSeconds());
    }
}
