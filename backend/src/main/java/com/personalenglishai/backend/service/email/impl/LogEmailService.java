package com.personalenglishai.backend.service.email.impl;

import com.personalenglishai.backend.service.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 日志模拟邮件发送（开发阶段使用）。
 * 生产环境替换为 SmtpEmailService。
 */
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LogEmailService.class);
    private static final String FROM = "noreply@personalenglishai.com";

    @Override
    public void send(String to, String subject, String html) {
        log.info("""
                ========== 📧 模拟邮件 ==========
                From:    {}
                To:      {}
                Subject: {}
                Body:
                {}
                ===================================""",
                FROM, to, subject, html);
    }
}
