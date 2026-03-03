package com.personalenglishai.backend.service.sms.impl;

import com.personalenglishai.backend.service.sms.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 日志模拟短信发送（开发阶段使用）。
 * 生产环境替换为真实短信服务商实现。
 */
@Service
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "false", matchIfMissing = true)
public class LogSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(LogSmsService.class);

    @Override
    public void send(String phone, String content) {
        log.info("""
                ========== SMS Mock ==========
                To:      {}
                Content: {}
                ===============================""",
                phone, content);
    }
}
