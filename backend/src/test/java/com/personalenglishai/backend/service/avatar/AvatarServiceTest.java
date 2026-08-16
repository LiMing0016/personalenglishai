package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.AvatarUploadResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AvatarUploadRateLimitService rateLimitService;
    @Mock
    private AvatarImageNormalizer imageNormalizer;
    @Mock
    private AvatarFileStorage fileStorage;

    private AvatarService service;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new AvatarService(
                userMapper, rateLimitService, imageNormalizer, fileStorage);
        file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});
    }

    @Test
    void rejectsMissingUserBeforeConsumingRateLimit() {
        when(userMapper.findById(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.upload(42L, file))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(rateLimitService, never()).consume(42L);
        verify(imageNormalizer, never()).normalize(file);
    }

    @Test
    void updatesDatabaseThenCleansPreviousOwnedAvatar() {
        User user = user("/uploads/avatars/42/old.png");
        AvatarImageNormalizer.NormalizedAvatar normalized =
                new AvatarImageNormalizer.NormalizedAvatar(new byte[] {8, 9}, 64, 64);
        AvatarFileStorage.StoredAvatar stored = stored();
        when(userMapper.findById(42L)).thenReturn(user);
        when(imageNormalizer.normalize(file)).thenReturn(normalized);
        when(fileStorage.store(42L, normalized.bytes())).thenReturn(stored);
        when(userMapper.updateAvatarUrl(42L, stored.avatarUrl())).thenReturn(1);

        AvatarUploadResponse response = service.upload(42L, file);

        assertThat(response.avatarUrl()).isEqualTo(stored.avatarUrl());
        InOrder order = inOrder(
                userMapper, rateLimitService, imageNormalizer, fileStorage);
        order.verify(userMapper).findById(42L);
        order.verify(rateLimitService).consume(42L);
        order.verify(imageNormalizer).normalize(file);
        order.verify(fileStorage).store(42L, normalized.bytes());
        order.verify(userMapper).updateAvatarUrl(42L, stored.avatarUrl());
        order.verify(fileStorage).deletePreviousIfOwned(
                42L, user.getAvatarUrl(), stored);
    }

    @Test
    void storageFailureDoesNotUpdateDatabase() {
        when(userMapper.findById(42L)).thenReturn(user(null));
        AvatarImageNormalizer.NormalizedAvatar normalized =
                new AvatarImageNormalizer.NormalizedAvatar(new byte[] {8}, 1, 1);
        when(imageNormalizer.normalize(file)).thenReturn(normalized);
        when(fileStorage.store(42L, normalized.bytes()))
                .thenThrow(new BizException(ErrorCode.COMMON_SYSTEM_ERROR));

        assertThatThrownBy(() -> service.upload(42L, file))
                .isInstanceOf(BizException.class);

        verify(userMapper, never()).updateAvatarUrl(42L, "/uploads/avatars/42/new.png");
    }

    @Test
    void zeroRowDatabaseUpdateRollsBackNewFile() {
        AvatarFileStorage.StoredAvatar stored = arrangeStoredAvatar();
        when(userMapper.updateAvatarUrl(42L, stored.avatarUrl())).thenReturn(0);

        assertThatThrownBy(() -> service.upload(42L, file))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_SYSTEM_ERROR));

        verify(fileStorage).deleteNewFile(stored);
        verify(fileStorage, never()).deletePreviousIfOwned(
                42L, "/uploads/avatars/42/old.png", stored);
    }

    @Test
    void databaseExceptionRollsBackNewFileAndPreservesCauseType() {
        AvatarFileStorage.StoredAvatar stored = arrangeStoredAvatar();
        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        when(userMapper.updateAvatarUrl(42L, stored.avatarUrl())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.upload(42L, file))
                .isSameAs(databaseFailure);

        verify(fileStorage).deleteNewFile(stored);
        verify(fileStorage, never()).deletePreviousIfOwned(
                42L, "/uploads/avatars/42/old.png", stored);
    }

    private AvatarFileStorage.StoredAvatar arrangeStoredAvatar() {
        User user = user("/uploads/avatars/42/old.png");
        AvatarImageNormalizer.NormalizedAvatar normalized =
                new AvatarImageNormalizer.NormalizedAvatar(new byte[] {8, 9}, 64, 64);
        AvatarFileStorage.StoredAvatar stored = stored();
        when(userMapper.findById(42L)).thenReturn(user);
        when(imageNormalizer.normalize(file)).thenReturn(normalized);
        when(fileStorage.store(42L, normalized.bytes())).thenReturn(stored);
        return stored;
    }

    private User user(String avatarUrl) {
        User user = new User();
        user.setId(42L);
        user.setAvatarUrl(avatarUrl);
        return user;
    }

    private AvatarFileStorage.StoredAvatar stored() {
        return new AvatarFileStorage.StoredAvatar(
                "/uploads/avatars/42/new.png",
                Path.of("uploads", "avatars", "42", "new.png").toAbsolutePath());
    }
}
