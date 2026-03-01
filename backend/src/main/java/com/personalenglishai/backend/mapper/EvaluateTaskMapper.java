package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.EvaluateTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvaluateTaskMapper {

    void insert(EvaluateTask task);

    EvaluateTask selectByRequestId(@Param("requestId") String requestId);

    void updateStatus(@Param("requestId") String requestId,
                      @Param("status") String status,
                      @Param("error") String error,
                      @Param("resultJson") String resultJson,
                      @Param("completedAt") Long completedAt);

    void deleteExpired(@Param("cutoffMs") long cutoffMs);
}
