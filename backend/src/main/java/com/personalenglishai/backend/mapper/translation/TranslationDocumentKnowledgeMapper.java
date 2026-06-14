package com.personalenglishai.backend.mapper.translation;

import com.personalenglishai.backend.entity.translation.TranslationDocumentAssetRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentElementRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentParseSnapshotRecord;
import com.personalenglishai.backend.entity.translation.TranslationKnowledgeChunkRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TranslationDocumentKnowledgeMapper {
    int deleteByDocumentId(@Param("documentId") String documentId);

    int insertSnapshot(TranslationDocumentParseSnapshotRecord record);

    int insertElement(TranslationDocumentElementRecord record);

    int insertChunk(TranslationKnowledgeChunkRecord record);

    int insertAsset(TranslationDocumentAssetRecord record);

    TranslationDocumentParseSnapshotRecord findSnapshotByDocumentId(@Param("documentId") String documentId);

    List<TranslationKnowledgeChunkRecord> selectChunksByDocumentId(@Param("documentId") String documentId);
}
