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

    Double averageScoreByUserId(@Param("userId") Long userId);

    Integer bestScoreByUserId(@Param("userId") Long userId);

    long countDistinctDaysByUserId(@Param("userId") Long userId);

    List<EssayEvaluation> selectByDocumentId(
            @Param("documentId") Long documentId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<java.util.Map<String, Object>> selectDashboardSubmissionRows(
            @Param("userId") Long userId,
            @Param("mode") String mode,
            @Param("startAt") java.time.LocalDateTime startAt,
            @Param("endExclusive") java.time.LocalDateTime endExclusive);

    long countByDocumentId(@Param("documentId") Long documentId);

    /** 聚合用户维度平均分和错误总数 */
    java.util.Map<String, Object> selectAggregatedStatsByUserId(@Param("userId") Long userId);
}
