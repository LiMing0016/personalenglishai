package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.UserAbilityProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAbilityProfileMapper {

    UserAbilityProfile selectByUserId(@Param("userId") Long userId);
}
