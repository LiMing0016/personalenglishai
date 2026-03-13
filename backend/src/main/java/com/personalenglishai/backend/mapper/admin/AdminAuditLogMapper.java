package com.personalenglishai.backend.mapper.admin;

import com.personalenglishai.backend.entity.admin.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminAuditLogMapper {
    int insert(AdminAuditLog log);
    List<Map<String, Object>> search(@Param("adminUserId") Long adminUserId,
                                     @Param("action") String action,
                                     @Param("resourceType") String resourceType,
                                     @Param("targetUserId") Long targetUserId,
                                     @Param("from") String from,
                                     @Param("to") String to,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    long countSearch(@Param("adminUserId") Long adminUserId,
                     @Param("action") String action,
                     @Param("resourceType") String resourceType,
                     @Param("targetUserId") Long targetUserId,
                     @Param("from") String from,
                     @Param("to") String to);
}
