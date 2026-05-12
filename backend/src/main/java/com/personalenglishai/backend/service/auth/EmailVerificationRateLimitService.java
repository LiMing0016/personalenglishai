package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class EmailVerificationRateLimitService {

    private static final String RESEND_COOLDOWN_SECONDS = "60";
    private static final String HOUR_WINDOW_SECONDS = "3600";
    private static final String EMAIL_HOUR_LIMIT = "5";
    private static final String IP_HOUR_LIMIT = "20";

    private static final RedisScript<Long> RESEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('exists', KEYS[1]) == 1 then
              return 1
            end

            local emailCount = tonumber(redis.call('get', KEYS[2]) or '0')
            if emailCount >= tonumber(ARGV[3]) then
              return 1
            end

            local ipCount = tonumber(redis.call('get', KEYS[3]) or '0')
            if ipCount >= tonumber(ARGV[4]) then
              return 1
            end

            redis.call('set', KEYS[1], '1', 'EX', tonumber(ARGV[1]))

            emailCount = redis.call('incr', KEYS[2])
            if emailCount == 1 then
              redis.call('expire', KEYS[2], tonumber(ARGV[2]))
            end

            ipCount = redis.call('incr', KEYS[3])
            if ipCount == 1 then
              redis.call('expire', KEYS[3], tonumber(ARGV[2]))
            end

            return 0
            """, Long.class);

    private static final RedisScript<Long> REGISTER_SEND_SCRIPT = new DefaultRedisScript<>("""
            local ipCount = tonumber(redis.call('get', KEYS[1]) or '0')
            if ipCount >= tonumber(ARGV[2]) then
              return 1
            end

            ipCount = redis.call('incr', KEYS[1])
            if ipCount == 1 then
              redis.call('expire', KEYS[1], tonumber(ARGV[1]))
            end

            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public EmailVerificationRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkAndConsumeResend(String email, String ip) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedIp = normalizeIp(ip);
        Long result = redisTemplate.execute(
                RESEND_SCRIPT,
                List.of(
                        "auth:email:verify:resend:cooldown:" + normalizedEmail,
                        "auth:email:verify:resend:hour:" + normalizedEmail,
                        "auth:email:verify:resend:ip:" + normalizedIp
                ),
                RESEND_COOLDOWN_SECONDS,
                HOUR_WINDOW_SECONDS,
                EMAIL_HOUR_LIMIT,
                IP_HOUR_LIMIT
        );
        rejectIfLimited(result);
    }

    public void checkAndConsumeRegisterSend(String ip) {
        String normalizedIp = normalizeIp(ip);
        Long result = redisTemplate.execute(
                REGISTER_SEND_SCRIPT,
                List.of("auth:email:verify:register:ip:" + normalizedIp),
                HOUR_WINDOW_SECONDS,
                IP_HOUR_LIMIT
        );
        rejectIfLimited(result);
    }

    private void rejectIfLimited(Long result) {
        if (result == null || result == 1L) {
            throw new BizException(ErrorCode.AUTH_EMAIL_RESEND_RATE_LIMITED);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip.trim();
    }
}
