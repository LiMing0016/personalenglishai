package com.personalenglishai.backend.mapper.assistant;

import com.personalenglishai.backend.entity.assistant.AssistantArchiveSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssistantArchiveSettingMapper {
    AssistantArchiveSetting findByUserId(@Param("userId") Long userId);

    int upsert(AssistantArchiveSetting setting);
}
