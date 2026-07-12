package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.CreateVocabularyThemeRequest;
import com.personalenglishai.backend.dto.vocabulary.UpdateVocabularyThemeRequest;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyTheme;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyThemeServiceTest {
    @Mock VocabularyThemeMapper themes;
    @Mock UserVocabularyPreferenceMapper preferences;

    private VocabularyThemeService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyThemeService(themes, preferences);
    }

    @Test
    void resolvesAllFixedSystemThemesWithVersionOneContentDefaults() {
        assertEquals("theme_system_basic", service.resolve(7L, null, "basic").themeUid());

        ResolvedVocabularyTheme basic = service.resolve(7L, "theme_system_basic", null);
        ResolvedVocabularyTheme exam = service.resolve(7L, "theme_system_exam", null);
        ResolvedVocabularyTheme reading = service.resolve(7L, "theme_system_reading", null);

        assertEquals("basic-markdown-v1", basic.promptStrategyKey());
        assertEquals(1, basic.version());
        assertEquals(1, basic.contentFormatVersion());
        assertEquals("exam-markdown-v1", exam.promptStrategyKey());
        assertEquals(1, exam.version());
        assertEquals(1, exam.contentFormatVersion());
        assertEquals("reading-markdown-v1", reading.promptStrategyKey());
        assertEquals(1, reading.version());
        assertEquals(1, reading.contentFormatVersion());
    }

    @Test
    void rejectsSystemThemeMutationAndCrossUserOrDisabledSelection() {
        UpdateVocabularyThemeRequest update = new UpdateVocabularyThemeRequest("Updated", "Updated purpose");
        assertThrows(BizException.class, () -> service.update(7L, "theme_system_basic", update));
        assertThrows(BizException.class, () -> service.resolve(7L, "theme_user_1", null));

        VocabularyTheme disabled = userTheme("theme_user_disabled", "Disabled", 1, "disabled");
        when(themes.findOwnedByUid(7L, "theme_user_disabled")).thenReturn(disabled);
        assertThrows(BizException.class, () -> service.resolve(7L, "theme_user_disabled", null));
    }

    @Test
    void createsCustomThemeWithInitialImmutableRevision() {
        CreateVocabularyThemeRequest create = new CreateVocabularyThemeRequest("My study theme", "Focus on collocations");

        var created = service.create(7L, create);

        assertEquals("custom-markdown-v1", created.promptStrategyKey());
        assertEquals(1, created.version());
        ArgumentCaptor<VocabularyTheme> themeCaptor = ArgumentCaptor.forClass(VocabularyTheme.class);
        ArgumentCaptor<VocabularyThemeRevision> revisionCaptor = ArgumentCaptor.forClass(VocabularyThemeRevision.class);
        verify(themes).insertTheme(themeCaptor.capture());
        verify(themes).insertRevision(revisionCaptor.capture());
        assertTrue(themeCaptor.getValue().getThemeUid().startsWith("theme_"));
        assertTrue(revisionCaptor.getValue().getRevisionUid().startsWith("theme_rev_"));
        assertEquals(1, revisionCaptor.getValue().getVersion());
        assertEquals("custom-markdown-v1", revisionCaptor.getValue().getPromptStrategyKey());
        assertEquals(1, revisionCaptor.getValue().getContentFormatVersion());
    }

    @Test
    void rejectsDuplicateActiveUserThemeNames() {
        when(themes.findVisibleThemes(7L)).thenReturn(List.of(userTheme("theme_user_1", "My study theme", 1, "active")));

        assertThrows(BizException.class,
                () -> service.create(7L, new CreateVocabularyThemeRequest("My study theme", "Focus on collocations")));

        verify(themes, never()).insertTheme(any());
    }

    @Test
    void updatesByAppendingRevisionAndGuardingCurrentVersion() {
        VocabularyTheme theme = userTheme("theme_user_1", "Original", 1, "active");
        VocabularyThemeRevision firstRevision = revision("theme_user_1", 1, "Original", "Original purpose");
        when(themes.findOwnedByUid(7L, "theme_user_1")).thenReturn(theme);
        when(themes.findCurrentRevision("theme_user_1")).thenReturn(firstRevision);
        when(themes.advanceVersion(7L, "theme_user_1", 1, 2, "Updated")).thenReturn(1);

        var updated = service.update(7L, "theme_user_1", new UpdateVocabularyThemeRequest("Updated", "Updated purpose"));

        assertEquals(2, updated.version());
        assertEquals("Original", firstRevision.getNameSnapshot());
        ArgumentCaptor<VocabularyThemeRevision> revisionCaptor = ArgumentCaptor.forClass(VocabularyThemeRevision.class);
        verify(themes).insertRevision(revisionCaptor.capture());
        assertEquals(2, revisionCaptor.getValue().getVersion());
        assertEquals("Updated", revisionCaptor.getValue().getNameSnapshot());
        assertEquals("custom-markdown-v1", revisionCaptor.getValue().getPromptStrategyKey());
        InOrder calls = inOrder(themes);
        calls.verify(themes).advanceVersion(7L, "theme_user_1", 1, 2, "Updated");
        calls.verify(themes).insertRevision(any(VocabularyThemeRevision.class));
    }

    @Test
    void rejectsConcurrentUpdateBeforeWritingDuplicateRevision() {
        VocabularyTheme theme = userTheme("theme_user_1", "Original", 1, "active");
        when(themes.findOwnedByUid(7L, "theme_user_1")).thenReturn(theme);
        when(themes.findCurrentRevision("theme_user_1"))
                .thenReturn(revision("theme_user_1", 1, "Original", "Original purpose"));
        when(themes.advanceVersion(7L, "theme_user_1", 1, 2, "Updated")).thenReturn(0);

        BizException exception = assertThrows(BizException.class,
                () -> service.update(7L, "theme_user_1", new UpdateVocabularyThemeRequest("Updated", "Updated purpose")));

        assertEquals(ErrorCode.VOCABULARY_THEME_CONFLICT, exception.getErrorCode());
        verify(themes, never()).insertRevision(any(VocabularyThemeRevision.class));
    }

    @Test
    void translatesDuplicateRevisionRaceToThemeConflict() {
        VocabularyTheme theme = userTheme("theme_user_1", "Original", 1, "active");
        when(themes.findOwnedByUid(7L, "theme_user_1")).thenReturn(theme);
        when(themes.findCurrentRevision("theme_user_1"))
                .thenReturn(revision("theme_user_1", 1, "Original", "Original purpose"));
        when(themes.advanceVersion(7L, "theme_user_1", 1, 2, "Updated")).thenReturn(1);
        when(themes.insertRevision(any(VocabularyThemeRevision.class)))
                .thenThrow(new DuplicateKeyException("duplicate revision"));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(7L, "theme_user_1", new UpdateVocabularyThemeRequest("Updated", "Updated purpose")));

        assertEquals(ErrorCode.VOCABULARY_THEME_CONFLICT, exception.getErrorCode());
    }

    @Test
    void translatesCreateDuplicateKeyRaceToThemeConflict() {
        when(themes.insertTheme(any(VocabularyTheme.class)))
                .thenThrow(new DuplicateKeyException("duplicate theme name"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(7L, new CreateVocabularyThemeRequest("My study theme", "Focus on collocations")));

        assertEquals(ErrorCode.VOCABULARY_THEME_CONFLICT, exception.getErrorCode());
    }

    @Test
    void translatesCopyDuplicateKeyRaceToThemeConflict() {
        when(themes.insertTheme(any(VocabularyTheme.class)))
                .thenThrow(new DuplicateKeyException("duplicate theme name"));

        BizException exception = assertThrows(BizException.class,
                () -> service.copy(7L, "theme_system_exam"));

        assertEquals(ErrorCode.VOCABULARY_THEME_CONFLICT, exception.getErrorCode());
    }

    @Test
    void rejectsInconsistentCurrentRevisionBeforeWritingANewVersion() {
        when(themes.findOwnedByUid(7L, "theme_user_1"))
                .thenReturn(userTheme("theme_user_1", "Original", 2, "active"));
        when(themes.findCurrentRevision("theme_user_1"))
                .thenReturn(revision("theme_user_1", 1, "Original", "Original purpose"));

        assertThrows(BizException.class,
                () -> service.update(7L, "theme_user_1", new UpdateVocabularyThemeRequest("Updated", "Updated purpose")));

        verify(themes, never()).insertRevision(any(VocabularyThemeRevision.class));
        verify(themes, never()).advanceVersion(eq(7L), eq("theme_user_1"), eq(2), eq(3), eq("Updated"));
    }

    @Test
    void copiesSystemThemeIntoEditableUserTheme() {
        var copied = service.copy(7L, "theme_system_exam");

        assertEquals("user", copied.ownerType());
        assertEquals("custom-markdown-v1", copied.promptStrategyKey());
        verify(themes).insertTheme(any(VocabularyTheme.class));
        verify(themes).insertRevision(any(VocabularyThemeRevision.class));
    }

    @Test
    void catalogFallsBackToBasicAndKeepsRecentThemeOrdering() {
        UserVocabularyPreference preference = new UserVocabularyPreference();
        preference.setDefaultThemeUid("theme_user_disabled");
        VocabularyTheme user = userTheme("theme_user_1", "My study theme", 2, "active");
        when(preferences.findPreferenceByUser(7L)).thenReturn(preference);
        when(themes.findVisibleThemes(7L)).thenReturn(List.of(user));
        when(themes.findCurrentRevision("theme_user_1")).thenReturn(revision("theme_user_1", 2, "My study theme", "Focus"));
        when(themes.findRecentThemeUids(7L, 10)).thenReturn(List.of("theme_user_1", "theme_system_exam"));

        var catalog = service.catalog(7L);

        assertEquals(List.of("theme_system_basic", "theme_system_exam", "theme_system_reading"),
                catalog.systemThemes().stream().map(item -> item.themeUid()).toList());
        assertEquals("theme_system_basic", catalog.defaultThemeUid());
        assertEquals(List.of("theme_user_1", "theme_system_exam"), catalog.recentThemeUids());
        assertTrue(catalog.userThemes().get(0).recent());
        assertTrue(catalog.systemThemes().get(1).recent());
        assertTrue(catalog.systemThemes().get(0).defaultTheme());
    }

    @Test
    void defaultsOnlyToAccessibleActiveThemesAndSoftDeletesOwnedThemes() {
        VocabularyTheme user = userTheme("theme_user_1", "My study theme", 1, "active");
        when(themes.findOwnedByUid(7L, "theme_user_1")).thenReturn(user);
        when(themes.findCurrentRevision("theme_user_1")).thenReturn(revision("theme_user_1", 1, "My study theme", "Focus"));
        when(themes.softDelete(7L, "theme_user_1")).thenReturn(1);

        service.setDefault(7L, "theme_user_1");
        service.delete(7L, "theme_user_1");

        verify(preferences).setDefaultTheme(7L, "theme_user_1");
        verify(themes).softDelete(7L, "theme_user_1");
    }

    private VocabularyTheme userTheme(String uid, String name, int version, String status) {
        VocabularyTheme theme = new VocabularyTheme();
        theme.setThemeUid(uid);
        theme.setOwnerType("user");
        theme.setUserId(7L);
        theme.setName(name);
        theme.setCurrentVersion(version);
        theme.setStatus(status);
        return theme;
    }

    private VocabularyThemeRevision revision(String themeUid, int version, String name, String purpose) {
        VocabularyThemeRevision revision = new VocabularyThemeRevision();
        revision.setThemeUid(themeUid);
        revision.setVersion(version);
        revision.setNameSnapshot(name);
        revision.setPurpose(purpose);
        revision.setPromptStrategyKey("custom-markdown-v1");
        revision.setContentFormatVersion(1);
        return revision;
    }
}
