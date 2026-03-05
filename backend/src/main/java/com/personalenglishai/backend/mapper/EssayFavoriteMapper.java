package com.personalenglishai.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EssayFavoriteMapper {

    int insert(@Param("userId") Long userId, @Param("evaluationId") Long evaluationId);

    int deleteByUserAndEval(@Param("userId") Long userId, @Param("evaluationId") Long evaluationId);

    boolean existsByUserAndEval(@Param("userId") Long userId, @Param("evaluationId") Long evaluationId);

    List<Long> selectEvalIdsByUserId(@Param("userId") Long userId);
}
