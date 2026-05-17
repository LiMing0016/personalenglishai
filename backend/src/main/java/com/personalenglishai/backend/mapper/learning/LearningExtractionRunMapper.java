package com.personalenglishai.backend.mapper.learning;

import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningExtractionRunMapper {
    int insert(LearningExtractionRun run);

    LearningExtractionRun findByRunUid(@Param("runUid") String runUid);

    LearningExtractionRun findByMessageAndExtractor(
            @Param("messageUid") String messageUid,
            @Param("extractorType") String extractorType
    );

    List<LearningExtractionRun> selectPendingByExtractor(
            @Param("extractorType") String extractorType,
            @Param("limit") Integer limit
    );

    List<LearningExtractionRun> selectPendingByExtractorAndUserCreatedRange(
            @Param("extractorType") String extractorType,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") Integer limit
    );

    int markProcessing(@Param("runUid") String runUid);

    int updateCompleted(
            @Param("runUid") String runUid,
            @Param("model") String model,
            @Param("inputTokenCount") Long inputTokenCount,
            @Param("outputTokenCount") Long outputTokenCount,
            @Param("resultJson") String resultJson
    );

    int updateFailed(
            @Param("runUid") String runUid,
            @Param("errorMessage") String errorMessage
    );
}
