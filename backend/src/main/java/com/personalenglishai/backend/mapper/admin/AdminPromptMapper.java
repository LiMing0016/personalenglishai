package com.personalenglishai.backend.mapper.admin;

import com.personalenglishai.backend.entity.EssayPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminPromptMapper {
    List<Map<String, Object>> search(@Param("stageId") Integer stageId,
                                     @Param("keyword") String keyword,
                                     @Param("examYear") Integer examYear,
                                     @Param("task") String task,
                                     @Param("active") Boolean active,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    long countSearch(@Param("stageId") Integer stageId,
                     @Param("keyword") String keyword,
                     @Param("examYear") Integer examYear,
                     @Param("task") String task,
                     @Param("active") Boolean active);
    EssayPrompt selectById(@Param("id") Long id);
    int insert(EssayPrompt prompt);
    int update(EssayPrompt prompt);
    int updateActive(@Param("id") Long id, @Param("isActive") Integer isActive);
}
