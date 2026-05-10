package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("重发验证邮件使用邮箱和 IP 维度 Redis Lua 限流")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void checkAndConsumeResend_usesRedisLuaKeysAndArguments() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("60"), eq("3600"), eq("5"), eq("20")))
                .thenReturn(0L);
        EmailVerificationRateLimitService service = new EmailVerificationRateLimitService(redisTemplate);

        service.checkAndConsumeResend(" U1@Example.com ", "203.0.113.8");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), eq("60"), eq("3600"), eq("5"), eq("20"));
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:email:verify:resend:cooldown:u1@example.com",
                "auth:email:verify:resend:hour:u1@example.com",
                "auth:email:verify:resend:ip:203.0.113.8"
        );
    }

    @Test
    @DisplayName("注册首次发信只消费 IP 维度 Redis Lua 限流")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void checkAndConsumeRegisterSend_usesIpOnlyLimit() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("3600"), eq("20")))
                .thenReturn(0L);
        EmailVerificationRateLimitService service = new EmailVerificationRateLimitService(redisTemplate);

        service.checkAndConsumeRegisterSend("203.0.113.8");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), eq("3600"), eq("20"));
        assertThat(keysCaptor.getValue()).containsExactly("auth:email:verify:register:ip:203.0.113.8");
    }

    @Test
    @DisplayName("Redis Lua 返回限流时抛出发送频率过高错误")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void checkAndConsumeResend_whenLimited_throwsBizException() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("60"), eq("3600"), eq("5"), eq("20")))
                .thenReturn(1L);
        EmailVerificationRateLimitService service = new EmailVerificationRateLimitService(redisTemplate);

        assertThatThrownBy(() -> service.checkAndConsumeResend("u1@example.com", "203.0.113.8"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_RESEND_RATE_LIMITED);
    }
}
