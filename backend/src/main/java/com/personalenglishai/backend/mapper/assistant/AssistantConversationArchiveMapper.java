package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantConversationArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssistantConversationArchiveMapper {
    int insert(AssistantConversationArchive archive);

    AssistantConversationArchive findLatestActive(
            @Param("userId") Long userId,
            @Param("conversationUid") String conversationUid);

    int markRestored(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
