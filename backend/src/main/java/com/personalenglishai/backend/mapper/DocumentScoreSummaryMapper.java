package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.DocumentScoreSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentScoreSummaryMapper {

    int insert(DocumentScoreSummary summary);

    int updateByDocumentId(DocumentScoreSummary summary);

    DocumentScoreSummary selectByDocumentId(@Param("documentId") Long documentId);
}
