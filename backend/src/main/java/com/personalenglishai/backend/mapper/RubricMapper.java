package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.dto.rubric.RubricRuleRow;
import com.personalenglishai.backend.entity.RubricDimension;
import com.personalenglishai.backend.entity.RubricLevel;
import com.personalenglishai.backend.entity.RubricVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RubricMapper {

    RubricVersion selectActiveVersionByStage(@Param("stage") String stage);

    List<RubricDimension> selectDimensionsByVersionAndMode(
            @Param("rubricVersionId") Long rubricVersionId,
            @Param("mode") String mode
    );

    List<RubricLevel> selectLevelsByVersionAndMode(
            @Param("rubricVersionId") Long rubricVersionId,
            @Param("mode") String mode
    );

    List<RubricRuleRow> selectActiveRubricRows(
            @Param("stage") String stage,
            @Param("mode") String mode,
            @Param("rubricKey") String rubricKey
    );
}
