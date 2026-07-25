package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyRevisionMapper {
    int insertRevision(VocabularyCardRevision revision);

    VocabularyCardRevision findRevision(@Param("revisionUid") String revisionUid);

    List<VocabularyCardRevision> listRevisions(@Param("cardUid") String cardUid);
}
