package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.AvatarUploadResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {

    private final UserMapper userMapper;
    private final AvatarUploadRateLimitService rateLimitService;
    private final AvatarImageNormalizer imageNormalizer;
    private final AvatarFileStorage fileStorage;

    public AvatarService(
            UserMapper userMapper,
            AvatarUploadRateLimitService rateLimitService,
            AvatarImageNormalizer imageNormalizer,
            AvatarFileStorage fileStorage) {
        this.userMapper = userMapper;
        this.rateLimitService = rateLimitService;
        this.imageNormalizer = imageNormalizer;
        this.fileStorage = fileStorage;
    }

    public AvatarUploadResponse upload(Long userId, MultipartFile file) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        rateLimitService.consume(userId);
        AvatarImageNormalizer.NormalizedAvatar normalized = imageNormalizer.normalize(file);
        AvatarFileStorage.StoredAvatar stored =
                fileStorage.store(userId, normalized.bytes());

        int updated;
        try {
            updated = userMapper.updateAvatarUrl(userId, stored.avatarUrl());
        } catch (RuntimeException e) {
            fileStorage.deleteNewFile(stored);
            throw e;
        }

        if (updated != 1) {
            fileStorage.deleteNewFile(stored);
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "头像资料更新失败");
        }

        fileStorage.deletePreviousIfOwned(userId, user.getAvatarUrl(), stored);
        return new AvatarUploadResponse(stored.avatarUrl());
    }
}
