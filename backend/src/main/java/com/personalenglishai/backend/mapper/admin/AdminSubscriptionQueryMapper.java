package com.personalenglishai.backend.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminSubscriptionQueryMapper {
    List<Map<String, Object>> searchSubscriptions(@Param("keyword") String keyword,
                                                  @Param("planCode") String planCode,
                                                  @Param("subscriptionStatus") String subscriptionStatus,
                                                  @Param("overLimit") Boolean overLimit,
                                                  @Param("expiresFrom") String expiresFrom,
                                                  @Param("expiresTo") String expiresTo,
                                                  @Param("usageMonth") String usageMonth,
                                                  @Param("usageDate") LocalDate usageDate,
                                                  @Param("now") LocalDateTime now,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    long countSubscriptions(@Param("keyword") String keyword,
                            @Param("planCode") String planCode,
                            @Param("subscriptionStatus") String subscriptionStatus,
                            @Param("overLimit") Boolean overLimit,
                            @Param("expiresFrom") String expiresFrom,
                            @Param("expiresTo") String expiresTo,
                            @Param("usageMonth") String usageMonth,
                            @Param("usageDate") LocalDate usageDate,
                            @Param("now") LocalDateTime now);

    Map<String, Object> selectOverview(@Param("today") LocalDate today,
                                       @Param("usageMonth") String usageMonth,
                                       @Param("now") LocalDateTime now);

    List<Map<String, Object>> selectPlanDistribution(@Param("now") LocalDateTime now);

    List<Map<String, Object>> selectDailyStats(@Param("dateFrom") LocalDate dateFrom,
                                               @Param("dateTo") LocalDate dateTo);
}
