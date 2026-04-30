package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AssistantShareMapper {
    int insert(AssistantShare share);

    AssistantShare findActiveByToken(@Param("shareToken") String shareToken);

    AssistantShare findActiveByConversationOwned(
            @Param("ownerUserId") Long ownerUserId,
            @Param("conversationUid") String conversationUid);

    int revokeOwned(
            @Param("ownerUserId") Long ownerUserId,
            @Param("shareToken") String shareToken,
            @Param("revokedAt") LocalDateTime revokedAt);
}
