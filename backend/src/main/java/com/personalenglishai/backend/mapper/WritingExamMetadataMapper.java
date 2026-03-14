package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.WritingExamMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WritingExamMetadataMapper {

    int insert(WritingExamMetadata metadata);

    int updateByMetadataId(WritingExamMetadata metadata);

    WritingExamMetadata selectByMetadataId(@Param("metadataId") Long metadataId);
}