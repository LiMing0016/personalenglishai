package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.EmailVerificationToken;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.EmailVerificationTokenMapper;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final long TOKEN_EXPIRE_HOURS = 24;

    private final EmailVerificationTokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final String baseUrl;

    public EmailVerificationService(EmailVerificationTokenMapper tokenMapper,
                                    UserMapper userMapper,
                                    EmailService emailService,
                                    @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
        this.emailService = emailService;
        this.baseUrl = baseUrl;
    }

    /**
     * 为用户创建验证 token 并发送验证邮件
     */
    @Transactional
    public void sendVerification(Long userId, String email) {
        // 仅使旧的邮箱验证 token 失效（不影响密码重置 token）
        tokenMapper.invalidateByUserId(userId, null);

        // 生成新 token
        String token = UUID.randomUUID().toString();
        EmailVerificationToken record = new EmailVerificationToken();
        record.setUserId(userId);
        record.setToken(token);
        record.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRE_HOURS));
        tokenMapper.insert(record);

        // 发送邮件
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        String html = buildVerificationHtml(verifyUrl);
        emailService.send(email, "验证你的 Personal English AI 邮箱", html);
    }

    /**
     * 重新发送验证邮件
     */
    public void resendVerification(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userMapper.findByEmail(normalizedEmail);
        if (user == null) {
            // 不暴露邮箱是否存在
            return;
        }
        if (user.isEmailVerified()) {
            return;
        }
        sendVerification(user.getId(), user.getEmail());
    }

    /**
     * 验证 token，返回状态：VERIFIED / EXPIRED / INVALID
     */
    @Transactional
    public String verify(String token) {
        if (token == null || token.isBlank()) {
            return "INVALID";
        }

        EmailVerificationToken record = tokenMapper.findByToken(token);
        if (record == null) {
            return "INVALID";
        }
        if (record.isUsed()) {
            return "INVALID";
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }

        // 标记 token 已使用
        tokenMapper.markUsed(token);
        // 更新用户验证状态
        userMapper.updateEmailVerified(record.getUserId(), true);

        return "VERIFIED";
    }

    private String buildVerificationHtml(String verifyUrl) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px;">
                  <h2 style="color:#1a1a2e;">验证你的邮箱</h2>
                  <p style="color:#444;line-height:1.6;">
                    感谢注册 Personal English AI！请点击下方按钮验证你的邮箱地址：
                  </p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 32px;
                            background:linear-gradient(90deg,#35c0ff,#6f6bff);
                            color:#fff;text-decoration:none;border-radius:8px;
                            font-weight:600;margin:24px 0;">
                    验证邮箱
                  </a>
                  <p style="color:#888;font-size:13px;">
                    如果按钮无法点击，请复制以下链接到浏览器：<br/>
                    <span style="color:#35c0ff;">%s</span>
                  </p>
                  <p style="color:#aaa;font-size:12px;margin-top:32px;">
                    此链接 24 小时内有效。如非本人操作，请忽略此邮件。
                  </p>
                </div>
                """.formatted(verifyUrl, verifyUrl);
    }
}
