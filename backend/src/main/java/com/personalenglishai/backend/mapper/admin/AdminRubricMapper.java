package com.personalenglishai.backend.mapper.admin;

import com.personalenglishai.backend.entity.RubricDimension;
import com.personalenglishai.backend.entity.RubricLevel;
import com.personalenglishai.backend.entity.RubricVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminRubricMapper {
    List<Map<String, Object>> searchVersions(@Param("stage") String stage,
                                             @Param("mode") String mode,
                                             @Param("active") Boolean active,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);
    long countVersions(@Param("stage") String stage,
                       @Param("mode") String mode,
                       @Param("active") Boolean active);
    RubricVersion selectVersionById(@Param("id") Long id);
    List<RubricDimension> selectDimensionsByVersionId(@Param("rubricVersionId") Long rubricVersionId);
    List<RubricLevel> selectLevelsByVersionId(@Param("rubricVersionId") Long rubricVersionId);
    List<String> selectModesByVersionId(@Param("rubricVersionId") Long rubricVersionId);
    int insertVersion(RubricVersion version);
    int insertDimension(RubricDimension dimension);
    int insertLevel(RubricLevel level);
    int deleteDimensionsByVersionId(@Param("rubricVersionId") Long rubricVersionId);
    int deleteLevelsByVersionId(@Param("rubricVersionId") Long rubricVersionId);
    int updateVersionMeta(RubricVersion version);
    int deactivateByStage(@Param("stage") String stage);
    int activateById(@Param("id") Long id);
}
