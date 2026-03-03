package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.SmsVerificationCode;
import com.personalenglishai.backend.mapper.SmsVerificationCodeMapper;
import com.personalenglishai.backend.service.sms.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SmsVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);

    private static final int EXPIRE_MINUTES = 5;
    private static final int RATE_LIMIT_COUNT = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final SmsVerificationCodeMapper codeMapper;
    private final SmsService smsService;
    private final SecureRandom random = new SecureRandom();

    /** phone -> failed attempt count (reset on new code send or successful verify) */
    private final ConcurrentHashMap<String, AtomicInteger> verifyAttempts = new ConcurrentHashMap<>();

    public SmsVerificationService(SmsVerificationCodeMapper codeMapper, SmsService smsService) {
        this.codeMapper = codeMapper;
        this.smsService = smsService;
    }

    @Transactional
    public void sendCode(String phone, String purpose) {
        // 60 秒冷却
        int recentInCooldown = codeMapper.countRecent(phone, 1);
        if (recentInCooldown > 0) {
            throw new BizException(ErrorCode.AUTH_SMS_RATE_LIMITED);
        }

        // 每小时最多 3 次
        int recentCount = codeMapper.countRecent(phone, RATE_LIMIT_WINDOW_MINUTES);
        if (recentCount >= RATE_LIMIT_COUNT) {
            throw new BizException(ErrorCode.AUTH_SMS_RATE_LIMITED);
        }

        codeMapper.invalidateByPhone(phone, purpose);

        // 发送新验证码时重置失败计数
        verifyAttempts.remove(phone);

        String code = generateCode();

        SmsVerificationCode record = new SmsVerificationCode();
        record.setPhone(phone);
        record.setCode(code);
        record.setPurpose(purpose);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        codeMapper.insert(record);

        smsService.send(phone, "您的验证码是：" + code + "，" + EXPIRE_MINUTES + "分钟内有效。");
    }

    @Transactional
    public void verifyCode(String phone, String code, String purpose) {
        // 检查失败次数
        AtomicInteger attempts = verifyAttempts.computeIfAbsent(phone, k -> new AtomicInteger(0));
        if (attempts.get() >= MAX_VERIFY_ATTEMPTS) {
            // 超过最大尝试次数，作废验证码
            codeMapper.invalidateByPhone(phone, purpose);
            verifyAttempts.remove(phone);
            log.warn("[SMS] max verify attempts exceeded, code invalidated: phone={}", phone);
            throw new BizException(ErrorCode.AUTH_SMS_CODE_INVALID);
        }

        SmsVerificationCode record = codeMapper.findLatest(phone, purpose);

        if (record == null) {
            attempts.incrementAndGet();
            throw new BizException(ErrorCode.AUTH_SMS_CODE_INVALID);
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            attempts.incrementAndGet();
            throw new BizException(ErrorCode.AUTH_SMS_CODE_INVALID);
        }

        // 恒定时间比较，防止时序攻击
        if (!constantTimeEquals(record.getCode(), code)) {
            attempts.incrementAndGet();
            throw new BizException(ErrorCode.AUTH_SMS_CODE_INVALID);
        }

        codeMapper.markUsed(record.getId());
        verifyAttempts.remove(phone);
    }

    private String generateCode() {
        int num = random.nextInt(900_000) + 100_000;
        return String.valueOf(num);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
