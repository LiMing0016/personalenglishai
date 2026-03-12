package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.mapper.admin.AdminAuditLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminAuditLogService {
    private final AdminAuditLogMapper adminAuditLogMapper;

    public AdminAuditLogService(AdminAuditLogMapper adminAuditLogMapper) {
        this.adminAuditLogMapper = adminAuditLogMapper;
    }

    public AdminPageResponse<Map<String, Object>> list(Long adminUserId, String action, String resourceType,
                                                       Long targetUserId, String from, String to,
                                                       int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Map<String, Object>> items = adminAuditLogMapper.search(adminUserId, action, resourceType, targetUserId,
                from, to, offset, normalizedSize);
        long total = adminAuditLogMapper.countSearch(adminUserId, action, resourceType, targetUserId, from, to);
        return new AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }
}
