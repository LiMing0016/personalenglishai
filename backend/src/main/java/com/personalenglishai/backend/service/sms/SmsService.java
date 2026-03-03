package com.personalenglishai.backend.service.sms;

/**
 * 短信发送服务接口。
 * 当前实现为日志模拟，接入短信服务商后替换实现即可。
 */
public interface SmsService {

    /**
     * 发送短信
     *
     * @param phone   手机号
     * @param content 短信内容
     */
    void send(String phone, String content);
}
