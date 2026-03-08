package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.EssayPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EssayPromptMapper {

    List<EssayPrompt> selectActiveByStageId(@Param("stageId") Integer stageId);

    List<EssayPrompt> searchActiveByStageId(@Param("stageId") Integer stageId,
                                            @Param("keyword") String keyword,
                                            @Param("offset") Integer offset,
                                            @Param("limit") Integer limit);

    long countSearchActiveByStageId(@Param("stageId") Integer stageId,
                                    @Param("keyword") String keyword);

    EssayPrompt selectByPaper(@Param("stageId") Integer stageId,
                              @Param("paper") String paper);
}
