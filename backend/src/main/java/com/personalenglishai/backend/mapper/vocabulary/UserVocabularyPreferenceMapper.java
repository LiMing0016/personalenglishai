package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserVocabularyPreferenceMapper {
    UserVocabularyPreference findPreferenceByUser(@Param("userId") Long userId);

    int upsertDefaultTemplate(@Param("userId") Long userId, @Param("templateKey") String templateKey);
}
