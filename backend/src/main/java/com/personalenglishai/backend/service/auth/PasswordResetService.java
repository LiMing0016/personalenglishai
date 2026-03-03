package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.EmailVerificationToken;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.EmailVerificationTokenMapper;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 密码重置服务。
 * <p>
 * 复用 email_verification_token 表存储重置 token（token 前缀 "rst-" 区分）。
 */
@Service
public class PasswordResetService {

    private static final long TOKEN_EXPIRE_HOURS = 1;
    private static final String TOKEN_PREFIX = "rst-";

    private final EmailVerificationTokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String baseUrl;

    public PasswordResetService(EmailVerificationTokenMapper tokenMapper,
                                UserMapper userMapper,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.baseUrl = baseUrl;
    }

    /**
     * 请求重置密码 — 发送重置邮件。
     * 不论邮箱是否存在都静默返回（防枚举）。
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userMapper.findByEmail(normalizedEmail);
        if (user == null) {
            return;
        }

        // 仅使旧的重置 token 失效（不影响邮箱验证 token）
        tokenMapper.invalidateByUserId(user.getId(), TOKEN_PREFIX);

        // 生成新 token
        String token = TOKEN_PREFIX + UUID.randomUUID();
        EmailVerificationToken record = new EmailVerificationToken();
        record.setUserId(user.getId());
        record.setToken(token);
        record.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRE_HOURS));
        tokenMapper.insert(record);

        // 发送重置邮件
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String html = buildResetHtml(resetUrl);
        emailService.send(normalizedEmail, "重置你的 Personal English AI 密码", html);
    }

    /**
     * 验证重置 token 是否有效（前端打开重置页面时调用）。
     */
    public String validateToken(String token) {
        if (token == null || token.isBlank() || !token.startsWith(TOKEN_PREFIX)) {
            return "INVALID";
        }
        EmailVerificationToken record = tokenMapper.findByToken(token);
        if (record == null || record.isUsed()) {
            return "INVALID";
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }
        return "VALID";
    }

    /**
     * 执行密码重置。
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || !token.startsWith(TOKEN_PREFIX)) {
            throw new BizException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }
        EmailVerificationToken record = tokenMapper.findByToken(token);
        if (record == null || record.isUsed()) {
            throw new BizException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.AUTH_RESET_TOKEN_EXPIRED);
        }

        // 标记 token 已使用
        tokenMapper.markUsed(token);

        // 更新密码
        User user = userMapper.findById(record.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String newHash = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(user.getId(), newHash);
    }

    private String buildResetHtml(String resetUrl) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px;">
                  <h2 style="color:#1a1a2e;">重置你的密码</h2>
                  <p style="color:#444;line-height:1.6;">
                    我们收到了你的密码重置请求。请点击下方按钮设置新密码：
                  </p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 32px;
                            background:linear-gradient(90deg,#35c0ff,#6f6bff);
                            color:#fff;text-decoration:none;border-radius:8px;
                            font-weight:600;margin:24px 0;">
                    重置密码
                  </a>
                  <p style="color:#888;font-size:13px;">
                    如果按钮无法点击，请复制以下链接到浏览器：<br/>
                    <span style="color:#35c0ff;">%s</span>
                  </p>
                  <p style="color:#aaa;font-size:12px;margin-top:32px;">
                    此链接 1 小时内有效。如非本人操作，请忽略此邮件，你的密码不会被修改。
                  </p>
                </div>
                """.formatted(resetUrl, resetUrl);
    }
}
