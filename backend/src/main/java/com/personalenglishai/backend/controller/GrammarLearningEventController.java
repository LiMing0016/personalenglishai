package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchRequest;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchResult;
import com.personalenglishai.backend.service.learning.GrammarLearningEventService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-events/grammar")
public class GrammarLearningEventController {
    private final GrammarLearningEventService grammarLearningEventService;

    public GrammarLearningEventController(GrammarLearningEventService grammarLearningEventService) {
        this.grammarLearningEventService = grammarLearningEventService;
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<GrammarLearningEventBatchResult>> acceptBatch(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody GrammarLearningEventBatchRequest request
    ) {
        GrammarLearningEventBatchResult result = grammarLearningEventService.acceptBatch(userId, request);
        ApiResponse<GrammarLearningEventBatchResult> body = ApiResponse.success(result);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }
}
