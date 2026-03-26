package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ScoreExamPolicyRegistry {

    private final List<ScoreExamPolicy> policies;

    public ScoreExamPolicyRegistry(List<ScoreExamPolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public WritingExamPolicyService.ExamPolicyResult evaluate(
            String stage,
            String mode,
            String taskType,
            WritingEvaluateRequest request,
            Map<String, Integer> scoreByDimension,
            List<WritingEvaluateResponse.ErrorDto> errors
    ) {
        for (ScoreExamPolicy policy : policies) {
            if (policy.supports(stage, mode)) {
                return policy.evaluate(stage, mode, taskType, request, scoreByDimension, errors);
            }
        }
        int rawOverall = average(scoreByDimension);
        return new WritingExamPolicyService.ExamPolicyResult(
                null,
                rawOverall,
                rawOverall,
                null,
                0,
                Map.of(),
                List.of(),
                null
        );
    }

    private int average(Map<String, Integer> scoreByDimension) {
        if (scoreByDimension == null || scoreByDimension.isEmpty()) {
            return 60;
        }
        int total = 0;
        for (Integer value : scoreByDimension.values()) {
            total += value == null ? 60 : value;
        }
        return Math.round(total / (float) scoreByDimension.size());
    }
}
