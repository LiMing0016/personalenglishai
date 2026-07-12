package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.CreateVocabularyThemeRequest;
import com.personalenglishai.backend.dto.vocabulary.UpdateVocabularyThemeRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyThemeCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyThemeResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyTheme;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyThemeService {
    private static final String SYSTEM_OWNER = "system";
    private static final String USER_OWNER = "user";
    private static final String ACTIVE_STATUS = "active";
    private static final String DISABLED_STATUS = "disabled";
    private static final String CUSTOM_PROMPT_STRATEGY = "custom-markdown-v1";
    private static final int CONTENT_FORMAT_VERSION = 1;
    private static final int RECENT_LIMIT = 10;

    private static final List<ResolvedVocabularyTheme> SYSTEM_THEMES = List.of(
            new ResolvedVocabularyTheme(
                    "theme_system_basic", 1, "Basic", "Everyday definitions and learning tips.",
                    "basic-markdown-v1", CONTENT_FORMAT_VERSION, "basic"),
            new ResolvedVocabularyTheme(
                    "theme_system_exam", 1, "Exam", "Exam meanings, collocations, and common mistakes.",
                    "exam-markdown-v1", CONTENT_FORMAT_VERSION, "exam"),
            new ResolvedVocabularyTheme(
                    "theme_system_reading", 1, "Reading", "Contextual meanings and reading comprehension.",
                    "reading-markdown-v1", CONTENT_FORMAT_VERSION, "reading"));

    private final VocabularyThemeMapper themes;
    private final UserVocabularyPreferenceMapper preferences;

    public VocabularyThemeService(
            VocabularyThemeMapper themes,
            UserVocabularyPreferenceMapper preferences) {
        this.themes = themes;
        this.preferences = preferences;
    }

    public VocabularyThemeCatalogResponse catalog(Long userId) {
        List<VocabularyTheme> visibleThemes = visibleThemes(userId);
        String defaultThemeUid = resolveDefaultThemeUid(userId);
        List<String> recentThemeUids = visibleRecentThemeUids(userId, visibleThemes);
        Set<String> recentThemeUidSet = new HashSet<>(recentThemeUids);

        List<VocabularyThemeResponse> systemThemes = SYSTEM_THEMES.stream()
                .map(theme -> systemResponse(theme, defaultThemeUid, recentThemeUidSet))
                .toList();
        List<VocabularyThemeResponse> userThemes = visibleThemes.stream()
                .filter(theme -> USER_OWNER.equals(theme.getOwnerType()))
                .map(theme -> userResponse(theme, defaultThemeUid, recentThemeUidSet))
                .toList();

        return new VocabularyThemeCatalogResponse(systemThemes, userThemes, defaultThemeUid, recentThemeUids);
    }

    @Transactional
    public VocabularyThemeResponse create(Long userId, CreateVocabularyThemeRequest request) {
        assertNameAvailable(userId, request.name(), null);
        VocabularyTheme theme = new VocabularyTheme();
        theme.setThemeUid(newUid("theme_"));
        theme.setOwnerType(USER_OWNER);
        theme.setUserId(userId);
        theme.setName(request.name());
        theme.setStatus(ACTIVE_STATUS);
        theme.setCurrentVersion(1);
        VocabularyThemeRevision revision = revision(
                theme.getThemeUid(), 1, request.name(), request.purpose(), CUSTOM_PROMPT_STRATEGY);
        try {
            themes.insertTheme(theme);
            themes.insertRevision(revision);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_CONFLICT, "theme name already exists");
        }
        return response(theme, revision, false, false);
    }

    @Transactional
    public VocabularyThemeResponse update(Long userId, String themeUid, UpdateVocabularyThemeRequest request) {
        VocabularyTheme theme = requireActiveOwnedTheme(userId, themeUid);
        assertNameAvailable(userId, request.name(), themeUid);
        VocabularyThemeRevision current = requireCurrentRevision(themeUid);
        if (current.getVersion() != theme.getCurrentVersion()) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_CONFLICT);
        }
        int nextVersion = theme.getCurrentVersion() + 1;
        VocabularyThemeRevision revision = revision(
                themeUid, nextVersion, request.name(), request.purpose(), CUSTOM_PROMPT_STRATEGY);
        try {
            if (themes.advanceVersion(userId, themeUid, theme.getCurrentVersion(), nextVersion, request.name()) != 1) {
                throw new BizException(ErrorCode.VOCABULARY_THEME_CONFLICT);
            }
            themes.insertRevision(revision);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_CONFLICT);
        }
        theme.setName(request.name());
        theme.setCurrentVersion(nextVersion);
        return response(theme, revision, false, false);
    }

    @Transactional
    public VocabularyThemeResponse copy(Long userId, String themeUid) {
        ResolvedVocabularyTheme source = resolve(userId, themeUid, null);
        String name = nextCopyName(userId, source.name());
        return create(userId, new CreateVocabularyThemeRequest(name, source.purpose()));
    }

    public void setDefault(Long userId, String themeUid) {
        resolve(userId, themeUid, null);
        preferences.setDefaultTheme(userId, themeUid);
    }

    public void disable(Long userId, String themeUid) {
        requireActiveOwnedTheme(userId, themeUid);
        if (themes.setStatus(userId, themeUid, DISABLED_STATUS) != 1) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_NOT_FOUND);
        }
    }

    public void delete(Long userId, String themeUid) {
        requireOwnedTheme(userId, themeUid);
        if (themes.softDelete(userId, themeUid) != 1) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_NOT_FOUND);
        }
    }

    public ResolvedVocabularyTheme resolve(Long userId, String themeUid, String legacyTemplateKey) {
        if (themeUid == null || themeUid.isBlank()) {
            if (legacyTemplateKey != null && !legacyTemplateKey.isBlank()) {
                return systemThemeForLegacyKey(legacyTemplateKey);
            }
            return resolveDefault(userId);
        }

        ResolvedVocabularyTheme systemTheme = systemTheme(themeUid);
        if (systemTheme != null) {
            return systemTheme;
        }

        VocabularyTheme theme = requireActiveOwnedTheme(userId, themeUid);
        VocabularyThemeRevision revision = requireCurrentRevision(themeUid);
        return new ResolvedVocabularyTheme(
                themeUid,
                theme.getCurrentVersion(),
                revision.getNameSnapshot(),
                revision.getPurpose(),
                revision.getPromptStrategyKey(),
                revision.getContentFormatVersion(),
                "basic");
    }

    private ResolvedVocabularyTheme resolveDefault(Long userId) {
        UserVocabularyPreference preference = preferences.findPreferenceByUser(userId);
        String defaultThemeUid = preference == null ? null : preference.getDefaultThemeUid();
        if (defaultThemeUid != null && !defaultThemeUid.isBlank()) {
            try {
                return resolve(userId, defaultThemeUid, null);
            } catch (BizException ignored) {
                // Disabled, deleted, or inaccessible defaults use the stable system fallback.
            }
        }
        return systemTheme("theme_system_basic");
    }

    private String resolveDefaultThemeUid(Long userId) {
        return resolveDefault(userId).themeUid();
    }

    private List<String> visibleRecentThemeUids(Long userId, List<VocabularyTheme> visibleThemes) {
        Set<String> visibleThemeUids = new HashSet<>();
        SYSTEM_THEMES.forEach(theme -> visibleThemeUids.add(theme.themeUid()));
        visibleThemes.forEach(theme -> visibleThemeUids.add(theme.getThemeUid()));
        List<String> recent = themes.findRecentThemeUids(userId, RECENT_LIMIT);
        if (recent == null) {
            return List.of();
        }
        return recent.stream().filter(visibleThemeUids::contains).toList();
    }

    private VocabularyThemeResponse systemResponse(
            ResolvedVocabularyTheme theme, String defaultThemeUid, Set<String> recentThemeUids) {
        return new VocabularyThemeResponse(
                theme.themeUid(), SYSTEM_OWNER, theme.name(), theme.purpose(), theme.version(), ACTIVE_STATUS,
                true, theme.themeUid().equals(defaultThemeUid), recentThemeUids.contains(theme.themeUid()),
                theme.promptStrategyKey());
    }

    private VocabularyThemeResponse userResponse(
            VocabularyTheme theme, String defaultThemeUid, Set<String> recentThemeUids) {
        VocabularyThemeRevision revision = requireCurrentRevision(theme.getThemeUid());
        return response(theme, revision, theme.getThemeUid().equals(defaultThemeUid),
                recentThemeUids.contains(theme.getThemeUid()));
    }

    private VocabularyThemeResponse response(
            VocabularyTheme theme, VocabularyThemeRevision revision, boolean defaultTheme, boolean recent) {
        return new VocabularyThemeResponse(
                theme.getThemeUid(), theme.getOwnerType(), revision.getNameSnapshot(), revision.getPurpose(),
                revision.getVersion(), theme.getStatus(), false, defaultTheme, recent, revision.getPromptStrategyKey());
    }

    private VocabularyTheme requireOwnedTheme(Long userId, String themeUid) {
        if (systemTheme(themeUid) != null) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_FORBIDDEN);
        }
        VocabularyTheme theme = themes.findOwnedByUid(userId, themeUid);
        if (theme == null) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_NOT_FOUND);
        }
        return theme;
    }

    private VocabularyTheme requireActiveOwnedTheme(Long userId, String themeUid) {
        VocabularyTheme theme = requireOwnedTheme(userId, themeUid);
        if (!ACTIVE_STATUS.equals(theme.getStatus())) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_NOT_FOUND);
        }
        return theme;
    }

    private VocabularyThemeRevision requireCurrentRevision(String themeUid) {
        VocabularyThemeRevision revision = themes.findCurrentRevision(themeUid);
        if (revision == null) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_NOT_FOUND);
        }
        return revision;
    }

    private void assertNameAvailable(Long userId, String name, String ignoredThemeUid) {
        boolean duplicate = visibleThemes(userId).stream()
                .anyMatch(theme -> USER_OWNER.equals(theme.getOwnerType())
                        && name.equals(theme.getName())
                        && !theme.getThemeUid().equals(ignoredThemeUid));
        if (duplicate) {
            throw new BizException(ErrorCode.VOCABULARY_THEME_CONFLICT, "theme name already exists");
        }
    }

    private String nextCopyName(Long userId, String sourceName) {
        Set<String> names = new HashSet<>();
        visibleThemes(userId).stream()
                .filter(theme -> USER_OWNER.equals(theme.getOwnerType()))
                .map(VocabularyTheme::getName)
                .forEach(names::add);
        for (int copyNumber = 1; ; copyNumber++) {
            String suffix = copyNumber == 1 ? " copy" : " copy " + copyNumber;
            String candidate = truncate(sourceName, 80 - suffix.length()) + suffix;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
    }

    private VocabularyThemeRevision revision(
            String themeUid, int version, String name, String purpose, String promptStrategyKey) {
        VocabularyThemeRevision revision = new VocabularyThemeRevision();
        revision.setRevisionUid(newUid("theme_rev_"));
        revision.setThemeUid(themeUid);
        revision.setVersion(version);
        revision.setNameSnapshot(name);
        revision.setPurpose(purpose);
        revision.setPromptStrategyKey(promptStrategyKey);
        revision.setContentFormatVersion(CONTENT_FORMAT_VERSION);
        return revision;
    }

    private List<VocabularyTheme> visibleThemes(Long userId) {
        List<VocabularyTheme> visible = themes.findVisibleThemes(userId);
        return visible == null ? List.of() : new ArrayList<>(visible);
    }

    private ResolvedVocabularyTheme systemThemeForLegacyKey(String legacyTemplateKey) {
        return SYSTEM_THEMES.stream()
                .filter(theme -> theme.legacyTemplateKey().equals(legacyTemplateKey))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "unsupported legacy template"));
    }

    private ResolvedVocabularyTheme systemTheme(String themeUid) {
        return SYSTEM_THEMES.stream()
                .filter(theme -> theme.themeUid().equals(themeUid))
                .findFirst()
                .orElse(null);
    }

    private String newUid(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
