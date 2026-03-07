package com.personalenglishai.backend.common.error;

/**
 * 错误码枚举
 */
public enum ErrorCode {
    COMMON_VALIDATION_ERROR("400001", "参数验证失败"),
    COMMON_SYSTEM_ERROR("500000", "系统内部错误"),
    AUTH_EMAIL_EXISTS("409001", "邮箱已被注册"),
    AUTH_LOGIN_FAILED("401001", "用户名或密码错误"),
    AUTH_LOGIN_RATE_LIMITED("429001", "登录尝试次数过多，请 5 分钟后再试"),
    AUTH_REFRESH_INVALID("401002", "登录已过期，请重新登录"),
    AUTH_RESET_TOKEN_INVALID("400020", "重置链接无效或已被使用"),
    AUTH_RESET_TOKEN_EXPIRED("400021", "重置链接已过期，请重新申请"),
    DOC_NOT_FOUND("404001", "document not found"),
    DOC_FORBIDDEN("403001", "not owner"),
    DOC_CONFLICT("409002", "revision conflict"),
    AUTH_PHONE_EXISTS("409003", "该手机号已被注册"),
    AUTH_SMS_RATE_LIMITED("429002", "验证码发送过于频繁，请稍后再试"),
    AUTH_SMS_CODE_INVALID("400030", "验证码无效或已过期"),
    AUTH_PHONE_NOT_FOUND("401003", "该手机号未注册"),
    AUTH_PHONE_NO_PASSWORD("401004", "该账号未设置密码，请使用验证码登录"),
    AUTH_CURRENT_PASSWORD_WRONG("401005", "当前密码错误"),
    AUTH_CAPTCHA_INVALID("400040", "验证码无效或已过期，请重新验证"),
    ESSAY_TOO_SHORT("400010", "作文太短，至少需要 20 个词"),
    ESSAY_TOO_LONG("400011", "作文太长，最多支持 500 个词"),
    ;

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
