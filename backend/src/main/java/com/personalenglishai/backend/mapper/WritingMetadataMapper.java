package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.WritingMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WritingMetadataMapper {

    int insert(WritingMetadata metadata);

    int updateByDocumentId(WritingMetadata metadata);

    WritingMetadata selectByDocumentId(@Param("documentId") Long documentId);
}