package com.personalenglishai.backend.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单熔断器：记录连续失败次数，达到阈值后快速失败
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final int failureThreshold;
    private final long windowMs;
    private final long recoveryMs;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong circuitOpenTime = new AtomicLong(0);

    public CircuitBreaker(int failureThreshold, long windowMs, long recoveryMs) {
        this.failureThreshold = failureThreshold;
        this.windowMs = windowMs;
        this.recoveryMs = recoveryMs;
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        long now = System.currentTimeMillis();
        long lastFailure = lastFailureTime.get();
        
        // 如果距离上次失败超过时间窗口，重置计数
        if (now - lastFailure > windowMs) {
            failureCount.set(0);
        }
        
        int count = failureCount.incrementAndGet();
        lastFailureTime.set(now);
        
        // 如果达到阈值，打开熔断器
        if (count >= failureThreshold) {
            circuitOpenTime.compareAndSet(0, now);
            log.warn("Circuit breaker opened: failureCount={} threshold={}", count, failureThreshold);
        }
    }

    /**
     * 记录成功，重置失败计数
     */
    public void recordSuccess() {
        failureCount.set(0);
        lastFailureTime.set(0);
        circuitOpenTime.set(0);
    }

    /**
     * 检查是否应该快速失败（熔断器打开）
     */
    public boolean shouldFastFail() {
        long openTime = circuitOpenTime.get();
        if (openTime == 0) {
            return false; // 熔断器未打开
        }

        long now = System.currentTimeMillis();
        if (now - openTime < recoveryMs) {
            return true; // 仍在恢复期内，快速失败
        }

        // 恢复期已过，尝试半开状态（重置计数，允许一次尝试）
        circuitOpenTime.set(0);
        failureCount.set(0);
        log.info("Circuit breaker half-open: allowing retry");
        return false;
    }

    /**
     * 获取当前失败次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 检查熔断器是否打开
     */
    public boolean isOpen() {
        return circuitOpenTime.get() > 0 && 
               (System.currentTimeMillis() - circuitOpenTime.get()) < recoveryMs;
    }
}
