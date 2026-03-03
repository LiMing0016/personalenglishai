package com.personalenglishai.backend.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录尝试限流服务（内存方案，单实例适用）。
 * <p>
 * 规则：同一邮箱在 {@code WINDOW_MS} 内连续失败 {@code MAX_ATTEMPTS} 次后锁定，
 * 锁定期间拒绝登录尝试。登录成功后清除记录。
 * <p>
 * 生产多实例部署时可替换为 Redis 实现。
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 5 * 60 * 1000L; // 5 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * 判断该邮箱是否已被锁定（超过最大失败次数且仍在窗口期内）。
     */
    public boolean isBlocked(String email) {
        AttemptRecord record = attempts.get(email);
        if (record == null) {
            return false;
        }
        if (isExpired(record)) {
            attempts.remove(email);
            return false;
        }
        return record.count >= MAX_ATTEMPTS;
    }

    /**
     * 记录一次登录失败。
     */
    public void recordFailure(String email) {
        long now = System.currentTimeMillis();
        attempts.compute(email, (key, existing) -> {
            if (existing == null || isExpired(existing)) {
                return new AttemptRecord(1, now);
            }
            return new AttemptRecord(existing.count + 1, existing.firstAttemptMs);
        });
        AttemptRecord record = attempts.get(email);
        if (record != null && record.count >= MAX_ATTEMPTS) {
            log.warn("[LoginAttempt] account locked: email={}, failures={}", email, record.count);
        }
    }

    /**
     * 登录成功后清除失败记录。
     */
    public void clearAttempts(String email) {
        attempts.remove(email);
    }

    /**
     * 获取剩余可尝试次数（用于前端提示，可选）。
     */
    public int remainingAttempts(String email) {
        AttemptRecord record = attempts.get(email);
        if (record == null || isExpired(record)) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - record.count);
    }

    private boolean isExpired(AttemptRecord record) {
        return System.currentTimeMillis() - record.firstAttemptMs > WINDOW_MS;
    }

    private static class AttemptRecord {
        final int count;
        final long firstAttemptMs;

        AttemptRecord(int count, long firstAttemptMs) {
            this.count = count;
            this.firstAttemptMs = firstAttemptMs;
        }
    }
}
