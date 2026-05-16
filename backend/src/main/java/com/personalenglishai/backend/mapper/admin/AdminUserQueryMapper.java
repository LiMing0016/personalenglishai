package com.personalenglishai.backend.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminUserQueryMapper {
    List<Map<String, Object>> searchUsers(@Param("userId") Long userId,
                                          @Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("role") String role,
                                          @Param("registerSource") String registerSource,
                                          @Param("adminRole") String adminRole,
                                          @Param("studyStage") String studyStage,
                                          @Param("lastActiveFrom") String lastActiveFrom,
                                          @Param("lastActiveTo") String lastActiveTo,
                                          @Param("createdFrom") String createdFrom,
                                          @Param("createdTo") String createdTo,
                                          @Param("planCode") String planCode,
                                          @Param("subscriptionStatus") String subscriptionStatus,
                                          @Param("overLimit") Boolean overLimit,
                                          @Param("now") java.time.LocalDateTime now,
                                          @Param("usageMonth") String usageMonth,
                                          @Param("usageDate") java.time.LocalDate usageDate,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);
    long countUsers(@Param("userId") Long userId,
                    @Param("keyword") String keyword,
                    @Param("status") String status,
                    @Param("role") String role,
                    @Param("registerSource") String registerSource,
                    @Param("adminRole") String adminRole,
                    @Param("studyStage") String studyStage,
                    @Param("lastActiveFrom") String lastActiveFrom,
                    @Param("lastActiveTo") String lastActiveTo,
                    @Param("createdFrom") String createdFrom,
                    @Param("createdTo") String createdTo,
                    @Param("planCode") String planCode,
                    @Param("subscriptionStatus") String subscriptionStatus,
                    @Param("overLimit") Boolean overLimit,
                    @Param("now") java.time.LocalDateTime now,
                    @Param("usageMonth") String usageMonth,
                    @Param("usageDate") java.time.LocalDate usageDate);

    Map<String, Object> selectUserSubscriptionSnapshot(@Param("userId") Long userId,
                                                       @Param("now") java.time.LocalDateTime now,
                                                       @Param("usageMonth") String usageMonth,
                                                       @Param("usageDate") java.time.LocalDate usageDate);
}
