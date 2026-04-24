package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.DocumentScoreSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentScoreSummaryMapper {

    int insert(DocumentScoreSummary summary);

    int updateByDocumentId(DocumentScoreSummary summary);

    DocumentScoreSummary selectByDocumentId(@Param("documentId") Long documentId);

    List<Map<String, Object>> selectDashboardAssetRowsByUserIdAndMode(
            @Param("userId") Long userId,
            @Param("mode") String mode
    );
}
