package com.personalenglishai.backend.mapper.subscription;

import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubscriptionPlanMapper {
    List<SubscriptionPlan> selectActivePlans();

    SubscriptionPlan findByPlanCode(@Param("planCode") String planCode);

    int updateQuotaRule(@Param("planCode") String planCode,
                        @Param("dailyTokenLimit") Long dailyTokenLimit,
                        @Param("monthlyTokenLimit") Long monthlyTokenLimit);
}
