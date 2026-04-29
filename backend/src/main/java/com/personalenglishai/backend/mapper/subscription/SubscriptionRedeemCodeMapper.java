package com.personalenglishai.backend.mapper.subscription;

import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemCode;
import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SubscriptionRedeemCodeMapper {
    int insertCode(SubscriptionRedeemCode code);

    SubscriptionRedeemCode findByCodeHash(@Param("codeHash") String codeHash);

    int markRedeemed(@Param("id") Long id,
                     @Param("redeemedByUserId") Long redeemedByUserId,
                     @Param("redeemedAt") LocalDateTime redeemedAt);

    int insertEvent(SubscriptionRedeemEvent event);
}
