package com.personalenglishai.backend.mapper.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VocabularyCardMapper {
    VocabularyCard findByIdentityIncludingDeleted(
            @Param("userId") Long userId,
            @Param("language") String language,
            @Param("normalizedTerm") String normalizedTerm);

    int insert(VocabularyCard card);

    VocabularyCard findByUidIncludingDeleted(@Param("cardUid") String cardUid);

    VocabularyCard findByUidForUpdate(@Param("cardUid") String cardUid);

    int restoreAndTouch(
            @Param("userId") Long userId,
            @Param("cardUid") String cardUid,
            @Param("displayTerm") String displayTerm,
            @Param("status") String status,
            @Param("capturedAt") LocalDateTime capturedAt);

    int touch(
            @Param("userId") Long userId,
            @Param("cardUid") String cardUid,
            @Param("capturedAt") LocalDateTime capturedAt);

    int markNeedsReview(
            @Param("userId") Long userId,
            @Param("cardUid") String cardUid);

    VocabularyCard findOwnedByUid(@Param("userId") Long userId, @Param("cardUid") String cardUid);

    List<VocabularyCard> listByUser(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("sourceType") String sourceType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByUser(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("sourceType") String sourceType);

    int updateActiveRevision(
            @Param("userId") Long userId,
            @Param("cardUid") String cardUid,
            @Param("baseRevisionUid") String baseRevisionUid,
            @Param("revisionUid") String revisionUid,
            @Param("status") String status,
            @Param("templateKey") String templateKey,
            @Param("templateVersion") int templateVersion);

    int markConflictCandidate(@Param("cardUid") String cardUid);

    int markGenerationFailed(@Param("cardUid") String cardUid, @Param("terminal") boolean terminal);

    int softDelete(@Param("userId") Long userId, @Param("cardUid") String cardUid);
}
