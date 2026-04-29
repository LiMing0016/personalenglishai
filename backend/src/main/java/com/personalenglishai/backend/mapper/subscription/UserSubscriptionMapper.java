package com.personalenglishai.backend.mapper.subscription;

import com.personalenglishai.backend.entity.subscription.UserSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSubscriptionMapper {
    UserSubscription findLatestByUserId(@Param("userId") Long userId);

    int upsert(UserSubscription subscription);
}
