package com.personalenglishai.backend.service.email.impl;

import com.personalenglishai.backend.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Real SMTP email sender.
 * Activated when app.mail.enabled=true.
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.mail.from:noreply@personalenglishai.com}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[Email] sent to={} subject={}", to, subject);
        } catch (MessagingException e) {
            // 仅记录错误，不抛异常——避免邮件发送失败阻塞注册等主流程
            log.error("[Email] failed to send to={} subject={}", to, subject, e);
        }
    }
}
