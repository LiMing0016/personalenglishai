package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.WritingPromptSheet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WritingPromptSheetMapper {

    int insert(WritingPromptSheet entity);

    WritingPromptSheet selectById(@Param("id") Long id);

    int updateDocumentId(@Param("id") Long id, @Param("documentId") Long documentId);
}
