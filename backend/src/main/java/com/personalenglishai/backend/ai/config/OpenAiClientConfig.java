package com.personalenglishai.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 客户端配置：超时、重试、熔断参数
 */
@Configuration
@ConfigurationProperties(prefix = "openai.client")
public class OpenAiClientConfig {

    /**
     * API 基础 URL，默认 https://api.openai.com
     */
    private String baseUrl = "https://api.openai.com";

    /**
     * 连接超时（毫秒），默认 5 秒
     */
    private int connectTimeoutMs = 5000;

    /**
     * 响应超时（毫秒），默认 30 秒
     */
    private int responseTimeoutMs = 30000;

    /**
     * 总体超时（毫秒），默认 35 秒
     */
    private int overallTimeoutMs = 35000;

    /**
     * 最大重试次数，默认 2 次（总共最多 3 次尝试）
     */
    private int maxRetries = 2;

    /**
     * 初始退避时间（毫秒），默认 200ms
     */
    private int initialBackoffMs = 200;

    /**
     * 最大退避时间（毫秒），默认 400ms
     */
    private int maxBackoffMs = 400;

    /**
     * 熔断阈值：连续失败次数，默认 5 次
     */
    private int circuitBreakerFailureThreshold = 5;

    /**
     * 熔断时间窗口（毫秒），默认 60 秒（1 分钟）
     */
    private long circuitBreakerWindowMs = 60000;

    /**
     * 熔断恢复时间（毫秒），默认 30 秒
     */
    private long circuitBreakerRecoveryMs = 30000;

    /**
     * 代理是否启用，默认 false
     */
    private boolean proxyEnabled = false;

    /**
     * 代理 URL，例如 http://127.0.0.1:59527
     */
    private String proxyUrl;

    /**
     * 代理主机（如果 proxyUrl 未设置，可使用 proxyHost + proxyPort）
     */
    private String proxyHost;

    /**
     * 代理端口（如果 proxyUrl 未设置，可使用 proxyHost + proxyPort）
     */
    private Integer proxyPort;

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public void setResponseTimeoutMs(int responseTimeoutMs) {
        this.responseTimeoutMs = responseTimeoutMs;
    }

    public int getOverallTimeoutMs() {
        return overallTimeoutMs;
    }

    public void setOverallTimeoutMs(int overallTimeoutMs) {
        this.overallTimeoutMs = overallTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(int initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public int getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(int maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public long getCircuitBreakerWindowMs() {
        return circuitBreakerWindowMs;
    }

    public void setCircuitBreakerWindowMs(long circuitBreakerWindowMs) {
        this.circuitBreakerWindowMs = circuitBreakerWindowMs;
    }

    public long getCircuitBreakerRecoveryMs() {
        return circuitBreakerRecoveryMs;
    }

    public void setCircuitBreakerRecoveryMs(long circuitBreakerRecoveryMs) {
        this.circuitBreakerRecoveryMs = circuitBreakerRecoveryMs;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
}
