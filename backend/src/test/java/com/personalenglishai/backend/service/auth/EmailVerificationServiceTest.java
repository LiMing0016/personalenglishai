package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.EmailVerificationTokenMapper;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.email.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenMapper tokenMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private EmailVerificationRateLimitService rateLimitService;

    @Test
    @DisplayName("重发验证邮件先限流再查询用户")
    void resendVerification_checksRateLimitBeforeUserLookup() {
        User user = user(false);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        EmailVerificationService service = service();

        service.resendVerification(" U1@Example.com ", "203.0.113.8");

        InOrder order = inOrder(rateLimitService, userMapper);
        order.verify(rateLimitService).checkAndConsumeResend("u1@example.com", "203.0.113.8");
        order.verify(userMapper).findByEmail("u1@example.com");
        verify(emailService).send(eq("u1@example.com"), any(), any());
    }

    @Test
    @DisplayName("不存在邮箱重发验证邮件不暴露状态且不发送邮件")
    void resendVerification_unknownEmail_returnsWithoutSending() {
        EmailVerificationService service = service();

        service.resendVerification("missing@example.com", "203.0.113.8");

        verify(rateLimitService).checkAndConsumeResend("missing@example.com", "203.0.113.8");
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("已验证邮箱重发验证邮件不发送邮件")
    void resendVerification_verifiedEmail_returnsWithoutSending() {
        User user = user(true);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        EmailVerificationService service = service();

        service.resendVerification("u1@example.com", "203.0.113.8");

        verify(rateLimitService).checkAndConsumeResend("u1@example.com", "203.0.113.8");
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("注册创建用户前可预先消费 IP 发信限流")
    void checkRegisterSendAllowed_consumesIpRateLimit() {
        EmailVerificationService service = service();

        service.checkRegisterSendAllowed("203.0.113.8");

        verify(rateLimitService).checkAndConsumeRegisterSend("203.0.113.8");
    }

    private EmailVerificationService service() {
        return new EmailVerificationService(tokenMapper, userMapper, emailService, rateLimitService, "https://www.personalenglishai.com");
    }

    private User user(boolean verified) {
        User user = new User();
        user.setId(1L);
        user.setEmail("u1@example.com");
        user.setEmailVerified(verified);
        return user;
    }
}
