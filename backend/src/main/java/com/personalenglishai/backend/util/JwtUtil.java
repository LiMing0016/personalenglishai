package com.personalenglishai.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenMs;
    private final long refreshTokenMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.accessTokenSeconds:1800}") long accessTokenSeconds,
                   @Value("${jwt.refreshTokenSeconds:259200}") long refreshTokenSeconds) {
        // 校验密钥长度（至少32字节）
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret key must be at least 32 bytes long");
        }

        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenMs = accessTokenSeconds * 1000;
        this.refreshTokenMs = refreshTokenSeconds * 1000;
    }

    public long getAccessTokenSeconds() {
        return accessTokenMs / 1000;
    }

    public long getRefreshTokenSeconds() {
        return refreshTokenMs / 1000;
    }

    /**
     * 生成 Access Token（短期）
     */
    public String generateAccessToken(Long userId, String nickname, int tokenVersion) {
        return buildToken(userId, nickname, "access", tokenVersion, accessTokenMs);
    }

    /**
     * 生成 Refresh Token（长期）
     */
    public String generateRefreshToken(Long userId, String nickname, int tokenVersion) {
        return buildToken(userId, nickname, "refresh", tokenVersion, refreshTokenMs);
    }

    /**
     * 兼容旧调用（等同于 generateAccessToken，tokenVersion=0）
     */
    public String generateToken(Long userId, String nickname) {
        return generateAccessToken(userId, nickname, 0);
    }

    private String buildToken(Long userId, String nickname, String tokenType, int tokenVersion, long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("nickname", nickname);
        claims.put("type", tokenType);
        claims.put("tv", tokenVersion);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从 Token 中获取 Claims
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取 UserId
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中获取 Nickname
     */
    public String getNicknameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("nickname", String.class);
    }

    /**
     * 获取 token 类型（"access" 或 "refresh"）
     */
    public String getTokenType(String token) {
        Claims claims = getClaimsFromToken(token);
        String type = claims.get("type", String.class);
        return type != null ? type : "access";
    }

    /**
     * 获取 token 中的 tokenVersion（用于密码重置后失效旧 token）
     */
    public int getTokenVersion(String token) {
        Claims claims = getClaimsFromToken(token);
        Integer tv = claims.get("tv", Integer.class);
        return tv != null ? tv : 0;
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}

