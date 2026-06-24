package com.personalenglishai.backend.mapper.learning;

import com.personalenglishai.backend.entity.learning.LearningNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningNoteMapper {
    int insert(LearningNote note);

    int updateForUser(LearningNote note);

    LearningNote selectByUidForUser(@Param("userId") Long userId, @Param("noteUid") String noteUid);

    List<LearningNote> selectByUserAndType(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("offset") int offset,
            @Param("size") int size);

    long countByUserAndType(@Param("userId") Long userId, @Param("type") String type);

    int softDelete(@Param("userId") Long userId, @Param("noteUid") String noteUid);
}
