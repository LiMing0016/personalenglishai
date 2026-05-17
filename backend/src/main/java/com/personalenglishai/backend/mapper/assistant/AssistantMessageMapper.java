package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssistantMessageMapper {
    List<AssistantMessage> selectByConversationUid(@Param("conversationUid") String conversationUid);

    AssistantMessage findByMessageUid(@Param("messageUid") String messageUid);

    Integer selectMaxSortOrder(@Param("conversationUid") String conversationUid);

    int insert(AssistantMessage message);
}
