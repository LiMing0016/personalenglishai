package com.personalenglishai.backend.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.admin.AdminAuditLog;
import com.personalenglishai.backend.mapper.admin.AdminAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final AdminAuditLogMapper adminAuditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditLogMapper adminAuditLogMapper, ObjectMapper objectMapper) {
        this.adminAuditLogMapper = adminAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void audit(Long adminUserId, String action, String resourceType, String resourceId,
                      Long targetUserId, Object before, Object after, HttpServletRequest request) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminUserId(adminUserId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setTargetUserId(targetUserId);
        log.setBeforeJson(toJsonQuietly(before));
        log.setAfterJson(toJsonQuietly(after));
        log.setIp(resolveClientIp(request));
        log.setUserAgent(request != null ? trim(request.getHeader("User-Agent"), 512) : null);
        adminAuditLogMapper.insert(log);
    }

    private String toJsonQuietly(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0], 64);
        }
        return trim(request.getRemoteAddr(), 64);
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
