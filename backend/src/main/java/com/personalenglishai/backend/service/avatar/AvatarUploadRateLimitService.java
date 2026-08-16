package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AvatarUploadRateLimitService {

    private static final String WINDOW_SECONDS = "3600";
    private static final String UPLOAD_LIMIT = "10";

    private static final RedisScript<Long> UPLOAD_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            if current >= tonumber(ARGV[2]) then
              return 1
            end

            current = redis.call('incr', KEYS[1])
            if current == 1 then
              redis.call('expire', KEYS[1], tonumber(ARGV[1]))
            end

            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public AvatarUploadRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void consume(Long userId) {
        Long result;
        try {
            result = redisTemplate.execute(
                    UPLOAD_SCRIPT,
                    List.of("user:avatar:upload:hour:" + userId),
                    WINDOW_SECONDS,
                    UPLOAD_LIMIT
            );
        } catch (RuntimeException e) {
            throw rateLimited();
        }

        if (result == null || result == 1L) {
            throw rateLimited();
        }
    }

    private BizException rateLimited() {
        return new BizException(ErrorCode.USER_AVATAR_RATE_LIMITED);
    }
}
