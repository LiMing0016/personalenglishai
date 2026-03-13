package com.personalenglishai.backend.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminUserQueryMapper {
    List<Map<String, Object>> searchUsers(@Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("registerSource") String registerSource,
                                          @Param("adminRole") String adminRole,
                                          @Param("studyStage") String studyStage,
                                          @Param("lastActiveFrom") String lastActiveFrom,
                                          @Param("lastActiveTo") String lastActiveTo,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);
    long countUsers(@Param("keyword") String keyword,
                    @Param("status") String status,
                    @Param("registerSource") String registerSource,
                    @Param("adminRole") String adminRole,
                    @Param("studyStage") String studyStage,
                    @Param("lastActiveFrom") String lastActiveFrom,
                    @Param("lastActiveTo") String lastActiveTo);
}
