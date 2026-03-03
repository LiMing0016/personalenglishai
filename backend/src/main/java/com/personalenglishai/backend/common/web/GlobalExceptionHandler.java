package com.personalenglishai.backend.common.web;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数验证失败");
        log.warn("参数验证失败: {}", msg);
        return body(ErrorCode.COMMON_VALIDATION_ERROR.getCode(), msg, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        String msg = "请求体格式错误";
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException upe) {
            msg = "不允许的字段: " + upe.getPropertyName();
        }
        log.warn("请求体不可读: {} -> {}", e.getMessage(), msg);
        return body(ErrorCode.COMMON_VALIDATION_ERROR.getCode(), msg, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return body(ErrorCode.AUTH_EMAIL_EXISTS, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        log.warn("业务异常: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        HttpStatus status = resolveStatus(e.getErrorCode());
        return body(e.getErrorCode().getCode(), e.getMessage(), status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("系统异常", e);
        return body(ErrorCode.COMMON_SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Object>> body(ErrorCode ec, HttpStatus status) {
        return body(ec.getCode(), ec.getMessage(), status);
    }

    private ResponseEntity<ApiResponse<Object>> body(String code, String message, HttpStatus status) {
        ApiResponse<Object> r = ApiResponse.error(code, message);
        r.setTraceId(MDC.get("traceId"));
        return ResponseEntity.status(status).body(r);
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        String code = errorCode.getCode();
        if (code == null || code.length() < 3) {
            return HttpStatus.BAD_REQUEST;
        }
        String prefix = code.substring(0, 3);
        return switch (prefix) {
            case "400" -> HttpStatus.BAD_REQUEST;
            case "401" -> HttpStatus.UNAUTHORIZED;
            case "403" -> HttpStatus.FORBIDDEN;
            case "404" -> HttpStatus.NOT_FOUND;
            case "409" -> HttpStatus.CONFLICT;
            case "429" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
