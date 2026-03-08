package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.WritingStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingStageMapper {

    List<WritingStage> selectAllActive();

    WritingStage selectById(@Param("id") Integer id);

    WritingStage selectByCode(@Param("code") String code);
}
