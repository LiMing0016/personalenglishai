package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantConversationMapper {
    List<AssistantConversation> selectByUser(
            @Param("userId") Long userId,
            @Param("archived") Boolean archived,
            @Param("projectId") Long projectId);

    AssistantConversation findOwnedActiveByUid(@Param("userId") Long userId, @Param("conversationUid") String conversationUid);

    AssistantConversation findActiveByUid(@Param("conversationUid") String conversationUid);

    int insert(AssistantConversation conversation);

    int updateTitleSummaryOwned(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid,
            @Param("title") String title,
            @Param("summary") String summary);

    int setArchivedAtOwned(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid,
            @Param("archivedAt") LocalDateTime archivedAt);

    int setPinnedOwned(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid,
            @Param("pinned") boolean pinned);

    int moveOwned(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid,
            @Param("projectId") Long projectId);

    int softDeleteOwned(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid,
            @Param("deletedAt") LocalDateTime deletedAt);
}
