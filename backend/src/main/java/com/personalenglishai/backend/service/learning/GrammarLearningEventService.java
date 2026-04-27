package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchRequest;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchResult;

public interface GrammarLearningEventService {
    GrammarLearningEventBatchResult acceptBatch(Long authenticatedUserId, GrammarLearningEventBatchRequest request);
}
