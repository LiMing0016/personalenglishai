package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularySourceMapper {
    int insertSource(VocabularyCardSource source);

    VocabularyCardSource findSourceByIdempotencyKey(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey);

    VocabularyCardSource findBySourceUid(@Param("sourceUid") String sourceUid);

    List<VocabularyCardSource> listSources(@Param("cardUid") String cardUid);

    List<VocabularyCardSource> listDistinctSourceTypesByCardUids(
            @Param("userId") Long userId,
            @Param("cardUids") List<String> cardUids);
}
