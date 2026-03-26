package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;

import java.util.List;
import java.util.Map;

interface ScoreExamPolicy {

    boolean supports(String stage, String mode);

    WritingExamPolicyService.ExamPolicyResult evaluate(
            String stage,
            String mode,
            String taskType,
            WritingEvaluateRequest request,
            Map<String, Integer> scoreByDimension,
            List<WritingEvaluateResponse.ErrorDto> errors
    );
}
