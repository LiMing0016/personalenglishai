package com.personalenglishai.backend.ai.client;

/**
 * 用于 dev/local 调试：模拟上游 429，走统一失败日志逻辑。
 * rootCauseClass=TooManyRequests, rootCauseMsg=429 Too Many Requests (debug simulated)
 */
public class TooManyRequests extends RuntimeException {

    public TooManyRequests(String message) {
        super(message);
    }
}
