package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.WritingTaskMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WritingTaskMetadataMapper {
    WritingTaskMetadata selectByDocumentId(@Param("documentId") Long documentId);

    int insert(WritingTaskMetadata metadata);
}
