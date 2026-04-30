package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantProjectMapper {
    List<AssistantProject> selectActiveByUserId(@Param("userId") Long userId);

    AssistantProject findOwnedActiveById(@Param("userId") Long userId, @Param("id") Long id);

    int insert(AssistantProject project);

    int updateOwned(AssistantProject project);

    int softDeleteOwned(@Param("userId") Long userId, @Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
