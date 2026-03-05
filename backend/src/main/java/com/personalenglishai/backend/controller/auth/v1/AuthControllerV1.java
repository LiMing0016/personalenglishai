package com.personalenglishai.backend.controller.auth.v1;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.auth.dto.*;
import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.service.auth.EmailVerificationService;
import com.personalenglishai.backend.service.auth.PasswordResetService;
import com.personalenglishai.backend.service.auth.SmsVerificationService;
import com.personalenglishai.backend.service.captcha.CaptchaService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 v1
 * POST /api/v1/auth/register, POST /api/v1/auth/login,
 * POST /api/v1/auth/refresh, POST /api/v1/auth/logout
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthControllerV1 {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SmsVerificationService smsVerificationService;
    private final CaptchaService captchaService;
    private final boolean cookieSecure;

    public AuthControllerV1(AuthService authService,
                            EmailVerificationService emailVerificationService,
                            PasswordResetService passwordResetService,
                            SmsVerificationService smsVerificationService,
                            CaptchaService captchaService,
                            @org.springframework.beans.factory.annotation.Value("${app.cookie.secure:false}") boolean cookieSecure) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.smsVerificationService = smsVerificationService;
        this.captchaService = captchaService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        Long userId = authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        audit.info("[REGISTER] email={} userId={} ip={}", request.getEmail(), userId, resolveClientIp(httpRequest));
        // 注册成功后发送验证邮件
        emailVerificationService.sendVerification(userId, request.getEmail());
        RegisterResponse data = new RegisterResponse(userId);
        ApiResponse<RegisterResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * 获取滑动验证码
     */
    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaResponse>> getCaptcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        CaptchaResponse data = new CaptchaResponse(result.captchaId(), result.bgImage(), result.pieceImage());
        ApiResponse<CaptchaResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 验证滑动验证码
     */
    @PostMapping("/captcha/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyCaptcha(
            @Valid @RequestBody CaptchaVerifyRequest request) {
        String token = captchaService.verify(request.getCaptchaId(), request.getX());
        boolean verified = token != null;
        Map<String, Object> data = verified
                ? Map.of("verified", true, "captchaToken", token)
                : Map.of("verified", false);
        ApiResponse<Map<String, Object>> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 用户登录 — access token 通过 body 返回，refresh token 通过 httpOnly cookie 返回
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String ip = resolveClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        String email = request.getEmail();

        // 验证滑动验证码 token
        if (!captchaService.validateToken(request.getCaptchaToken())) {
            audit.warn("[LOGIN_FAIL] email={} ip={} reason=invalid_captcha", email, ip);
            throw new BizException(ErrorCode.AUTH_CAPTCHA_INVALID);
        }

        try {
            LoginResponse data = authService.login(email, request.getPassword());
            setRefreshCookie(response, data.getRefreshToken(), (int) data.getRefreshTokenMaxAge());
            audit.info("[LOGIN_OK] email={} ip={} ua={}", email, ip, ua);
            ApiResponse<LoginResponse> body = ApiResponse.success(data);
            body.setTraceId(MDC.get("traceId"));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            audit.warn("[LOGIN_FAIL] email={} ip={} ua={} reason={}", email, ip, ua, e.getMessage());
            throw e;
        }
    }

    /**
     * 刷新 token — 从 cookie 中读取 refresh token，返回新 access + refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        LoginResponse data = authService.refresh(refreshToken);
        setRefreshCookie(response, data.getRefreshToken(), (int) data.getRefreshTokenMaxAge());
        ApiResponse<LoginResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 退出登录 — 清除 refresh cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        audit.info("[LOGOUT] ip={}", resolveClientIp(httpRequest));
        setRefreshCookie(response, "", 0);
        ApiResponse<Void> body = ApiResponse.success();
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 邮箱验证 — GET /api/v1/auth/verify-email?token=xxx
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(@RequestParam String token) {
        String status = emailVerificationService.verify(token);
        Map<String, String> data = Map.of("status", status);
        ApiResponse<Map<String, String>> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 重新发送验证邮件 — POST /api/v1/auth/resend-verification
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.getOrDefault("email", "");
        emailVerificationService.resendVerification(email);
        // 不论邮箱是否存在都返回成功（防枚举）
        ApiResponse<Void> body = ApiResponse.success();
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 请求密码重置 — POST /api/v1/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.getOrDefault("email", "");
        passwordResetService.requestReset(email);
        // 不论邮箱是否存在都返回成功（防枚举）
        ApiResponse<Void> body = ApiResponse.success();
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 验证重置 token — GET /api/v1/auth/reset-password/validate?token=xxx
     */
    @GetMapping("/reset-password/validate")
    public ResponseEntity<ApiResponse<Map<String, String>>> validateResetToken(@RequestParam String token) {
        String status = passwordResetService.validateToken(token);
        Map<String, String> data = Map.of("status", status);
        ApiResponse<Map<String, String>> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 执行密码重置 — POST /api/v1/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        audit.info("[PASSWORD_RESET] ip={}", resolveClientIp(httpRequest));
        ApiResponse<Void> body = ApiResponse.success();
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 发送短信验证码 — POST /api/v1/auth/sms/send
     */
    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<Void>> sendSmsCode(
            @Valid @RequestBody SendSmsCodeRequest request,
            HttpServletRequest httpRequest) {
        smsVerificationService.sendCode(request.getPhone(), request.getPurpose());
        audit.info("[SMS_SEND] phone={} purpose={} ip={}", request.getPhone(), request.getPurpose(), resolveClientIp(httpRequest));
        ApiResponse<Void> body = ApiResponse.success();
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    /**
     * 手机登录 — POST /api/v1/auth/phone/login
     * mode=otp: 先验证码校验，再获取 token
     * mode=password: 密码校验
     */
    @PostMapping("/phone/login")
    public ResponseEntity<ApiResponse<LoginResponse>> phoneLogin(
            @Valid @RequestBody PhoneLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String ip = resolveClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        String phone = request.getPhone();

        try {
            LoginResponse data;
            if ("otp".equals(request.getMode())) {
                smsVerificationService.verifyCode(phone, request.getCode(), "login");
                data = authService.loginByPhone(phone);
            } else {
                data = authService.loginByPhonePassword(phone, request.getPassword());
            }
            setRefreshCookie(response, data.getRefreshToken(), (int) data.getRefreshTokenMaxAge());
            audit.info("[PHONE_LOGIN_OK] phone={} mode={} ip={} ua={}", phone, request.getMode(), ip, ua);
            ApiResponse<LoginResponse> body = ApiResponse.success(data);
            body.setTraceId(MDC.get("traceId"));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            audit.warn("[PHONE_LOGIN_FAIL] phone={} mode={} ip={} ua={} reason={}", phone, request.getMode(), ip, ua, e.getMessage());
            throw e;
        }
    }

    /**
     * 手机注册 — POST /api/v1/auth/phone/register
     * 注册成功后直接返回 JWT（自动登录）
     */
    @PostMapping("/phone/register")
    public ResponseEntity<ApiResponse<LoginResponse>> phoneRegister(
            @Valid @RequestBody PhoneRegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        // 先验证短信验证码
        smsVerificationService.verifyCode(request.getPhone(), request.getCode(), "register");
        LoginResponse data = authService.registerByPhone(request.getPhone(), request.getNickname());
        setRefreshCookie(response, data.getRefreshToken(), (int) data.getRefreshTokenMaxAge());
        audit.info("[PHONE_REGISTER] phone={} ip={}", request.getPhone(), resolveClientIp(httpRequest));
        ApiResponse<LoginResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private void setRefreshCookie(HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/v1/auth/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", cookieSecure ? "Strict" : "Lax");
        response.addCookie(cookie);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return "";
    }

    /**
     * 解析客户端真实 IP（支持反向代理 X-Forwarded-For）
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 取第一个 IP（最左侧为客户端真实 IP）
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
