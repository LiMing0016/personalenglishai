package com.personalenglishai.backend.service.email;

/**
 * 邮件发送服务接口。
 * 当前实现为日志模拟，接入 SMTP 后替换实现即可。
 */
public interface EmailService {

    /**
     * 发送邮件
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 正文
     */
    void send(String to, String subject, String html);
}
