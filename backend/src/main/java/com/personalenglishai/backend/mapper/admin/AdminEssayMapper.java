package com.personalenglishai.backend.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminEssayMapper {
    List<Map<String, Object>> searchEssays(@Param("userId") Long userId,
                                           @Param("keyword") String keyword,
                                           @Param("mode") String mode,
                                           @Param("studyStage") String studyStage,
                                           @Param("scoreMin") Integer scoreMin,
                                           @Param("scoreMax") Integer scoreMax,
                                           @Param("favorited") Boolean favorited,
                                           @Param("archived") Boolean archived,
                                           @Param("createdFrom") String createdFrom,
                                           @Param("createdTo") String createdTo,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);
    long countEssays(@Param("userId") Long userId,
                     @Param("keyword") String keyword,
                     @Param("mode") String mode,
                     @Param("studyStage") String studyStage,
                     @Param("scoreMin") Integer scoreMin,
                     @Param("scoreMax") Integer scoreMax,
                     @Param("favorited") Boolean favorited,
                     @Param("archived") Boolean archived,
                     @Param("createdFrom") String createdFrom,
                     @Param("createdTo") String createdTo);
}
