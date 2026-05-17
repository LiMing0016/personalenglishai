package com.personalenglishai.backend.mapper.learning;

import com.personalenglishai.backend.entity.learning.LearningEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningEvidenceMapper {
    int insert(LearningEvidence evidence);

    LearningEvidence findByEvidenceUid(@Param("evidenceUid") String evidenceUid);

    LearningEvidence findByCandidateUid(@Param("candidateUid") String candidateUid);

    List<LearningEvidence> selectPendingByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") Integer limit
    );

    int updateStatus(
            @Param("evidenceUid") String evidenceUid,
            @Param("status") String status
    );
}
