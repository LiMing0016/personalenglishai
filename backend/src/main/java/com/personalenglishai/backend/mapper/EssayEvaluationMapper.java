package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.EssayEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EssayEvaluationMapper {

    void insert(EssayEvaluation evaluation);

    List<EssayEvaluation> selectByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByUserId(@Param("userId") Long userId);

    EssayEvaluation selectById(@Param("id") Long id);
}
