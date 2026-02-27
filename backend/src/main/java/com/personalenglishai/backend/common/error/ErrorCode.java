package com.personalenglishai.backend.common.error;

/**
 * 错误码枚举
 */
public enum ErrorCode {
    COMMON_VALIDATION_ERROR("400001", "参数验证失败"),
    COMMON_SYSTEM_ERROR("500000", "系统内部错误"),
    AUTH_EMAIL_EXISTS("409001", "邮箱已被注册"),
    AUTH_LOGIN_FAILED("401001", "用户名或密码错误"),
    DOC_NOT_FOUND("404001", "document not found"),
    DOC_FORBIDDEN("403001", "not owner"),
    DOC_CONFLICT("409002", "revision conflict"),
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
