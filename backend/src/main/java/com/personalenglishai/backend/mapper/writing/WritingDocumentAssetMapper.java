package com.personalenglishai.backend.mapper.writing;

import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.writing.WritingDocumentAssetSnapshot;
import com.personalenglishai.backend.entity.writing.WritingLearningAssetPreviewItem;
import com.personalenglishai.backend.entity.writing.WritingLearningAssetPreviewRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingDocumentAssetMapper {
    int upsertConversationLink(
            @Param("userId") Long userId,
            @Param("documentId") Long documentId,
            @Param("conversationUid") String conversationUid);

    List<AssistantConversation> selectLinkedConversations(
            @Param("userId") Long userId,
            @Param("documentId") Long documentId);

    List<AssistantConversation> selectRecoverableCoachConversations(
            @Param("userId") Long userId,
            @Param("probes") List<String> probes);

    WritingDocumentAssetSnapshot findSnapshot(
            @Param("userId") Long userId,
            @Param("documentId") Long documentId);

    int upsertSnapshot(WritingDocumentAssetSnapshot snapshot);

    WritingLearningAssetPreviewRun findLatestLearningAssetPreviewRun(
            @Param("userId") Long userId,
            @Param("documentId") Long documentId);

    List<WritingLearningAssetPreviewItem> selectLearningAssetPreviewItems(@Param("runUid") String runUid);

    int insertLearningAssetPreviewRun(WritingLearningAssetPreviewRun run);

    int deleteLearningAssetPreviewItems(@Param("runUid") String runUid);

    int insertLearningAssetPreviewItems(
            @Param("runUid") String runUid,
            @Param("items") List<WritingLearningAssetPreviewItem> items);

    default int replaceLearningAssetPreviewItems(String runUid, List<WritingLearningAssetPreviewItem> items) {
        int affected = deleteLearningAssetPreviewItems(runUid);
        if (items == null || items.isEmpty()) {
            return affected;
        }
        return affected + insertLearningAssetPreviewItems(runUid, items);
    }
}
