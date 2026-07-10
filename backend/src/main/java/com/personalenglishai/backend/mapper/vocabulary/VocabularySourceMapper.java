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

    List<VocabularyCardSource> listSources(@Param("cardUid") String cardUid);
}
