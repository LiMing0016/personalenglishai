package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.GrammarLearningEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GrammarLearningEventMapper {
    int insertIgnore(GrammarLearningEvent event);
}
