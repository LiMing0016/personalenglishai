package com.personalenglishai.backend.service;

import com.personalenglishai.backend.dto.UpdateStageRequest;
import com.personalenglishai.backend.entity.UserProfile;
import com.personalenglishai.backend.mapper.UserProfileMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    @DisplayName("insert profile with aiMode=1 when stage is non-empty and profile does not exist")
    void updateStudyStage_insertNonEmpty() {
        UpdateStageRequest req = new UpdateStageRequest();
        req.setStudyStage("cet4");
        when(userProfileMapper.findByUserId(1L)).thenReturn(null);

        userProfileService.updateStudyStage(1L, req);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).insert(captor.capture());
        UserProfile inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getStudyStage()).isEqualTo("cet4");
        assertThat(inserted.getAiMode()).isEqualTo(1);
        verify(userProfileMapper, never()).updateStageAndAiMode(1L, "cet4", 1);
    }

    @Test
    @DisplayName("update profile to aiMode=0 when stage is blank and profile exists")
    void updateStudyStage_updateBlank() {
        UpdateStageRequest req = new UpdateStageRequest();
        req.setStudyStage("   ");
        when(userProfileMapper.findByUserId(2L)).thenReturn(new UserProfile(2L, "ielts", 1));

        userProfileService.updateStudyStage(2L, req);

        verify(userProfileMapper).updateStageAndAiMode(2L, null, 0);
        verify(userProfileMapper, never()).insert(org.mockito.ArgumentMatchers.any(UserProfile.class));
    }

    @Test
    @DisplayName("returns default profile when row is missing")
    void getUserProfile_defaultWhenMissing() {
        when(userProfileMapper.findByUserId(3L)).thenReturn(null);

        UserProfile profile = userProfileService.getUserProfile(3L);

        assertThat(profile.getUserId()).isEqualTo(3L);
        assertThat(profile.getStudyStage()).isNull();
        assertThat(profile.getAiMode()).isEqualTo(0);
    }

    @Test
    @DisplayName("returns mapper profile when row exists")
    void getUserProfile_existing() {
        UserProfile existing = new UserProfile(4L, "toefl", 1);
        when(userProfileMapper.findByUserId(4L)).thenReturn(existing);

        UserProfile profile = userProfileService.getUserProfile(4L);

        assertThat(profile).isSameAs(existing);
    }
}
