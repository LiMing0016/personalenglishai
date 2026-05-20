package com.personalenglishai.backend.mapper.learning;

import com.personalenglishai.backend.entity.learning.LearningRawCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningRawCandidateMapper {
    int insertOrUpdateOccurrence(LearningRawCandidate candidate);

    LearningRawCandidate findByCandidateUid(@Param("candidateUid") String candidateUid);

    LearningRawCandidate findByDedupeKey(
            @Param("userId") Long userId,
            @Param("candidateType") String candidateType,
            @Param("normalizedText") String normalizedText,
            @Param("extractorType") String extractorType
    );

    List<LearningRawCandidate> selectByMessageUid(@Param("messageUid") String messageUid);

    List<LearningRawCandidate> selectByMessageUidAndExtractor(
            @Param("messageUid") String messageUid,
            @Param("extractorType") String extractorType
    );

    List<LearningRawCandidate> selectByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    int updateComparisonStatus(
            @Param("candidateUid") String candidateUid,
            @Param("comparisonStatus") String comparisonStatus
    );
}
