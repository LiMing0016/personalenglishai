package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VocabularyGenerationJobMapper {
    int insertJob(VocabularyGenerationJob job);

    List<VocabularyGenerationJob> selectClaimable(@Param("limit") int limit);

    VocabularyGenerationJob findLatestByCard(@Param("cardUid") String cardUid);

    int markRunning(@Param("jobUid") String jobUid);

    int markSucceeded(@Param("jobUid") String jobUid, @Param("revisionUid") String revisionUid);

    int markFailed(
            @Param("jobUid") String jobUid,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("terminal") boolean terminal);

    int cancel(@Param("jobUid") String jobUid);

    int cancelPendingForCard(@Param("cardUid") String cardUid);
}
