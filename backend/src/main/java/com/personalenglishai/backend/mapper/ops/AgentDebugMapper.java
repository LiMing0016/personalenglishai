package com.personalenglishai.backend.mapper.ops;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentDebugMapper {
    void insertRun(Map<String, Object> row);

    void insertStep(Map<String, Object> row);

    void insertPromptSnapshot(Map<String, Object> row);

    List<Map<String, Object>> searchRuns(@Param("status") String status,
                                         @Param("intent") String intent,
                                         @Param("targetAgent") String targetAgent,
                                         @Param("model") String model,
                                         @Param("userId") Long userId,
                                         @Param("conversationId") String conversationId,
                                         @Param("createdFrom") String createdFrom,
                                         @Param("createdTo") String createdTo,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countRuns(@Param("status") String status,
                   @Param("intent") String intent,
                   @Param("targetAgent") String targetAgent,
                   @Param("model") String model,
                   @Param("userId") Long userId,
                   @Param("conversationId") String conversationId,
                   @Param("createdFrom") String createdFrom,
                   @Param("createdTo") String createdTo);

    Map<String, Object> findRunByRunId(@Param("runId") String runId);

    List<Map<String, Object>> listSteps(@Param("runId") String runId);

    List<Map<String, Object>> listPromptSnapshots(@Param("runId") String runId);

    List<Map<String, Object>> searchPromptSnapshots(@Param("promptKey") String promptKey,
                                                    @Param("promptHash") String promptHash,
                                                    @Param("agentName") String agentName,
                                                    @Param("model") String model,
                                                    @Param("createdFrom") String createdFrom,
                                                    @Param("createdTo") String createdTo,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    long countPromptSnapshots(@Param("promptKey") String promptKey,
                              @Param("promptHash") String promptHash,
                              @Param("agentName") String agentName,
                              @Param("model") String model,
                              @Param("createdFrom") String createdFrom,
                              @Param("createdTo") String createdTo);
}
