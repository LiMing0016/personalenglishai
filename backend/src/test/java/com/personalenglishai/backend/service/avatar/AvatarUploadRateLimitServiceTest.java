package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
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
class AvatarUploadRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void consumesHourlyUserLimitWithAtomicRedisScript() {
        when(redisTemplate.execute(
                any(RedisScript.class), any(List.class), eq("3600"), eq("10")))
                .thenReturn(0L);
        AvatarUploadRateLimitService service = new AvatarUploadRateLimitService(redisTemplate);

        service.consume(42L);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(RedisScript.class), keysCaptor.capture(), eq("3600"), eq("10"));
        assertThat(keysCaptor.getValue())
                .containsExactly("user:avatar:upload:hour:42");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsWhenHourlyLimitIsReached() {
        when(redisTemplate.execute(
                any(RedisScript.class), any(List.class), eq("3600"), eq("10")))
                .thenReturn(1L);
        AvatarUploadRateLimitService service = new AvatarUploadRateLimitService(redisTemplate);

        assertRateLimited(service);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failsClosedWhenRedisReturnsNoResult() {
        when(redisTemplate.execute(
                any(RedisScript.class), any(List.class), eq("3600"), eq("10")))
                .thenReturn(null);
        AvatarUploadRateLimitService service = new AvatarUploadRateLimitService(redisTemplate);

        assertRateLimited(service);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failsClosedWhenRedisIsUnavailable() {
        when(redisTemplate.execute(
                any(RedisScript.class), any(List.class), eq("3600"), eq("10")))
                .thenThrow(new IllegalStateException("redis unavailable"));
        AvatarUploadRateLimitService service = new AvatarUploadRateLimitService(redisTemplate);

        assertRateLimited(service);
    }

    private void assertRateLimited(AvatarUploadRateLimitService service) {
        assertThatThrownBy(() -> service.consume(42L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.USER_AVATAR_RATE_LIMITED));
    }
}
