package com.personalenglishai.backend.mapper.subscription;

import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiTokenUsageMapper {
    int insertIgnoreEvent(AiTokenUsageEvent event);

    int upsertMonthlyUsage(@Param("userId") Long userId,
                           @Param("usageMonth") String usageMonth,
                           @Param("tokenDelta") Long tokenDelta);

    Long selectMonthlyTokenUsed(@Param("userId") Long userId,
                                @Param("usageMonth") String usageMonth);
}
