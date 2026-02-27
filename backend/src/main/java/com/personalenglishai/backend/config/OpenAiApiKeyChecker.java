package com.personalenglishai.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 启动时检查并打印 OPENAI_API_KEY 的脱敏状态
 */
@Component
public class OpenAiApiKeyChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiApiKeyChecker.class);

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        boolean present = apiKey != null && !apiKey.isBlank();
        int length = present ? apiKey.length() : 0;
        String prefix = present && apiKey.length() >= 6 ? apiKey.substring(0, 6) : "";
        String sha256Prefix = present ? calculateSha256Prefix(apiKey) : "";

        log.info("OPENAI_API_KEY check: present={} length={} prefix={} sha256={}",
                present, length, prefix, sha256Prefix);
    }

    /**
     * 计算 API Key 的 SHA256 前 8 位（用于区分不同 key）
     */
    private String calculateSha256Prefix(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) { // 取前 4 个字节 = 8 个十六进制字符
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to calculate SHA256 for API key", e);
            return "unknown";
        }
    }
}
