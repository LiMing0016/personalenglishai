package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.dto.LoginRequest;
import com.personalenglishai.backend.dto.LoginResponse;
import com.personalenglishai.backend.dto.RegisterRequest;
import com.personalenglishai.backend.dto.RegisterResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.service.UserService;
import com.personalenglishai.backend.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.register(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );

            RegisterResponse response = new RegisterResponse(
                    user.getId(),
                    user.getNickname(),
                    user.getEmail()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            // 用户名或邮箱已存在，返回 409
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            // 参数验证失败
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            // 其他异常
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("Login request received for username: {}", request.getUsername());
        try {
            // 参数验证
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                logger.warn("Login failed: username is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                logger.warn("Login failed: password is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // 根据username（对应nickname）查找用户
            logger.debug("Looking up user by nickname: {}", request.getUsername());
            User user = userService.findByNickname(request.getUsername());
            if (user == null) {
                logger.warn("Login failed: user not found for nickname: {}", request.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            logger.debug("User found: id={}, nickname={}", user.getId(), user.getNickname());

            // 验证密码
            logger.debug("Verifying password for user: {}", user.getId());
            if (!userService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
                logger.warn("Login failed: password mismatch for user: {}", user.getId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            logger.debug("Password verified successfully for user: {}", user.getId());

            // 生成 JWT Token
            logger.debug("Generating JWT token for user: id={}, nickname={}", user.getId(), user.getNickname());
            String token = jwtUtil.generateToken(user.getId(), user.getNickname());
            logger.info("JWT token generated successfully for user: {}", user.getId());

            LoginResponse response = new LoginResponse(token);
            logger.info("Login successful for user: {}", user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Login failed with exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

