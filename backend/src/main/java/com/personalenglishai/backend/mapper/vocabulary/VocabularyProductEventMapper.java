package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyProductEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VocabularyProductEventMapper {
    int insertIgnore(VocabularyProductEvent event);
}
