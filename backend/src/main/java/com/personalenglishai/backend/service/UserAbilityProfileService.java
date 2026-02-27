package com.personalenglishai.backend.service;

import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import org.springframework.stereotype.Service;

@Service
public class UserAbilityProfileService {

    private final UserAbilityProfileMapper userAbilityProfileMapper;

    public UserAbilityProfileService(UserAbilityProfileMapper userAbilityProfileMapper) {
        this.userAbilityProfileMapper = userAbilityProfileMapper;
    }

    public UserAbilityProfile getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return userAbilityProfileMapper.selectByUserId(userId);
    }
}
