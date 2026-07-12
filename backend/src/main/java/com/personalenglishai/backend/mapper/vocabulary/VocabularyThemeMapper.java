package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyTheme;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyThemeMapper {
    List<VocabularyTheme> findVisibleThemes(@Param("userId") Long userId);

    VocabularyTheme findOwnedByUid(@Param("userId") Long userId, @Param("themeUid") String themeUid);

    VocabularyThemeRevision findCurrentRevision(@Param("themeUid") String themeUid);

    VocabularyThemeRevision findRevision(@Param("themeUid") String themeUid, @Param("version") int version);

    int insertTheme(VocabularyTheme theme);

    int insertRevision(VocabularyThemeRevision revision);

    int advanceVersion(
            @Param("userId") Long userId,
            @Param("themeUid") String themeUid,
            @Param("expectedVersion") int expectedVersion,
            @Param("nextVersion") int nextVersion,
            @Param("name") String name);

    int setStatus(@Param("userId") Long userId, @Param("themeUid") String themeUid,
                  @Param("status") String status);

    int softDelete(@Param("userId") Long userId, @Param("themeUid") String themeUid);

    int recordRecentUse(@Param("userId") Long userId, @Param("themeUid") String themeUid);

    List<String> findRecentThemeUids(@Param("userId") Long userId, @Param("limit") int limit);
}
