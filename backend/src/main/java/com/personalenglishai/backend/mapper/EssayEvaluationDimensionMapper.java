package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.EssayEvaluationDimension;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EssayEvaluationDimensionMapper {

    int insertBatch(@Param("items") List<EssayEvaluationDimension> items);
}
