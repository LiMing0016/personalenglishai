package com.personalenglishai.backend.mapper.subscription;

import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiTokenUsageMapper {
    int insertIgnoreEvent(AiTokenUsageEvent event);

    int upsertMonthlyUsage(@Param("userId") Long userId,
                           @Param("usageMonth") String usageMonth,
                           @Param("tokenDelta") Long tokenDelta);

    Long selectMonthlyTokenUsed(@Param("userId") Long userId,
                                @Param("usageMonth") String usageMonth);

    int upsertDailyUsage(@Param("userId") Long userId,
                         @Param("usageDate") LocalDate usageDate,
                         @Param("tokenDelta") Long tokenDelta);

    Long selectDailyTokenUsed(@Param("userId") Long userId,
                              @Param("usageDate") LocalDate usageDate);

    List<AiTokenUsageEvent> selectEventsByUserAndOccurredAt(
            @Param("userId") Long userId,
            @Param("fromUtc") LocalDateTime fromUtc,
            @Param("toUtcExclusive") LocalDateTime toUtcExclusive);
}
