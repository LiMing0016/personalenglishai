package com.personalenglishai.backend.mapper.translation;

import com.personalenglishai.backend.entity.translation.TranslationDocumentFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TranslationDocumentFileMapper {
    int upsert(TranslationDocumentFileRecord record);

    TranslationDocumentFileRecord findByDocumentId(@Param("documentId") String documentId);
}
