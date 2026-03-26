package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WritingExamPolicyService {

    private final ScoreExamPolicyRegistry policyRegistry;

    public WritingExamPolicyService(ScoreExamPolicyRegistry policyRegistry) {
        this.policyRegistry = policyRegistry;
    }

    public ExamPolicyResult evaluate(
            String effectiveStage,
            String mode,
            String taskType,
            WritingEvaluateRequest request,
            Map<String, Integer> scoreByDimension,
            List<WritingEvaluateResponse.ErrorDto> errors
    ) {
        return policyRegistry.evaluate(effectiveStage, mode, taskType, request, scoreByDimension, errors);
    }

    public record ExamPolicyResult(
            String policyKey,
            int rawOverall,
            int finalOverall,
            Integer capScore,
            int deductionTotal,
            Map<String, Boolean> flags,
            List<String> reasons,
            DirectionAssessment directionAssessment
    ) {}

    public record DirectionAssessment(
            String relevance,
            String taskCompletion,
            String coverage,
            String maxBand,
            Integer capScore,
            List<String> reasons
    ) {}
}
