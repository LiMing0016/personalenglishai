package com.personalenglishai.backend.controller.auth.v1;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.controller.auth.dto.LoginResponse;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.service.auth.EmailVerificationService;
import com.personalenglishai.backend.service.auth.PasswordResetService;
import com.personalenglishai.backend.service.auth.SmsVerificationService;
import com.personalenglishai.backend.service.captcha.CaptchaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthControllerV1.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerV1Test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private SmsVerificationService smsVerificationService;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 and triggers verification email")
        void register_success() throws Exception {
            when(authService.register("u1@example.com", "Abcd1234", "Catalina")).thenReturn(100L);
            doNothing().when(emailVerificationService).sendVerification(100L, "u1@example.com");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"u1@example.com","password":"Abcd1234","nickname":"Catalina"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.userId").value(100));

            verify(emailVerificationService).sendVerification(100L, "u1@example.com");
        }

        @Test
        @DisplayName("returns 400 for weak password")
        void register_weakPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"u1@example.com","password":"12345678","nickname":"Catalina"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400001"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("returns token and sets refresh cookie")
        void login_success() throws Exception {
            when(captchaService.validateToken("cap-ok")).thenReturn(true);
            when(authService.login("u1@example.com", "Abcd1234"))
                    .thenReturn(buildLoginResponse("access-1", "refresh-1"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"u1@example.com","password":"Abcd1234","captchaToken":"cap-ok"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("access-1"))
                    .andExpect(cookie().exists("refresh_token"))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=refresh-1")));
        }

        @Test
        @DisplayName("returns 401 for invalid credentials")
        void login_badCredentials() throws Exception {
            when(captchaService.validateToken("cap-ok")).thenReturn(true);
            when(authService.login("u1@example.com", "wrongPass"))
                    .thenThrow(new BizException(ErrorCode.AUTH_LOGIN_FAILED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"u1@example.com","password":"wrongPass","captchaToken":"cap-ok"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("401001"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("reads refresh cookie and returns new token")
        void refresh_success() throws Exception {
            when(authService.refresh("refresh-abc"))
                    .thenReturn(buildLoginResponse("new-access", "new-refresh"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-abc")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("new-access"))
                    .andExpect(cookie().exists("refresh_token"))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=new-refresh")));
        }

        @Test
        @DisplayName("returns 401 when refresh token is invalid")
        void refresh_invalid() throws Exception {
            when(authService.refresh("")).thenThrow(new BizException(ErrorCode.AUTH_REFRESH_INVALID));

            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("401002"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("clears refresh cookie")
        void logout_success() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("refresh_token", ""))
                    .andExpect(cookie().maxAge("refresh_token", 0));
        }
    }

    @Nested
    @DisplayName("password reset and verify-email endpoints")
    class EmailAndPassword {

        @Test
        @DisplayName("verify-email returns status")
        void verifyEmail_success() throws Exception {
            when(emailVerificationService.verify("tok-1")).thenReturn("VERIFIED");

            mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "tok-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("VERIFIED"));
        }

        @Test
        @DisplayName("forgot-password always returns 200")
        void forgotPassword_success() throws Exception {
            doNothing().when(passwordResetService).requestReset("u1@example.com");

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"u1@example.com"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("validate reset token returns status")
        void validateResetToken_success() throws Exception {
            when(passwordResetService.validateToken("rst-1")).thenReturn("VALID");

            mockMvc.perform(get("/api/v1/auth/reset-password/validate").param("token", "rst-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("VALID"));
        }

        @Test
        @DisplayName("reset password returns 200")
        void resetPassword_success() throws Exception {
            doNothing().when(passwordResetService).resetPassword("rst-1", "Abcd1234");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"rst-1","password":"Abcd1234"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("reset password returns 400 for weak password")
        void resetPassword_weakPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"rst-1","password":"12345678"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400001"));
        }
    }

    @Nested
    @DisplayName("sms and phone auth endpoints")
    class SmsAndPhone {

        @Test
        @DisplayName("sms send returns 200")
        void sendSms_success() throws Exception {
            doNothing().when(smsVerificationService).sendCode("13812345678", "login");

            mockMvc.perform(post("/api/v1/auth/sms/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13812345678","purpose":"login"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("sms send returns 429 when rate limited")
        void sendSms_rateLimited() throws Exception {
            doThrow(new BizException(ErrorCode.AUTH_SMS_RATE_LIMITED))
                    .when(smsVerificationService).sendCode("13812345678", "login");

            mockMvc.perform(post("/api/v1/auth/sms/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13812345678","purpose":"login"}
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("429002"));
        }

        @Test
        @DisplayName("phone login otp mode verifies code first")
        void phoneLogin_otp() throws Exception {
            doNothing().when(smsVerificationService).verifyCode("13812345678", "123456", "login");
            when(authService.loginByPhone("13812345678"))
                    .thenReturn(buildLoginResponse("access-otp", "refresh-otp"));

            mockMvc.perform(post("/api/v1/auth/phone/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13812345678","mode":"otp","code":"123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("access-otp"))
                    .andExpect(cookie().exists("refresh_token"));

            verify(smsVerificationService).verifyCode("13812345678", "123456", "login");
            verify(authService).loginByPhone("13812345678");
        }

        @Test
        @DisplayName("phone login password mode uses phone password auth")
        void phoneLogin_password() throws Exception {
            when(authService.loginByPhonePassword("13812345678", "Abcd1234"))
                    .thenReturn(buildLoginResponse("access-pwd", "refresh-pwd"));

            mockMvc.perform(post("/api/v1/auth/phone/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13812345678","mode":"password","password":"Abcd1234"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("access-pwd"));

            verify(authService).loginByPhonePassword("13812345678", "Abcd1234");
        }

        @Test
        @DisplayName("phone register returns 201 and sets refresh cookie")
        void phoneRegister_success() throws Exception {
            doNothing().when(smsVerificationService).verifyCode("13812345678", "123456", "register");
            when(authService.registerByPhone("13812345678", "Catalina"))
                    .thenReturn(buildLoginResponse("access-reg", "refresh-reg"));

            mockMvc.perform(post("/api/v1/auth/phone/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13812345678","code":"123456","nickname":"Catalina"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.token").value("access-reg"))
                    .andExpect(cookie().exists("refresh_token"));
        }
    }

    private LoginResponse buildLoginResponse(String accessToken, String refreshToken) {
        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setExpiresIn(3600);
        response.setRefreshToken(refreshToken);
        response.setRefreshTokenMaxAge(604800);
        return response;
    }
}



