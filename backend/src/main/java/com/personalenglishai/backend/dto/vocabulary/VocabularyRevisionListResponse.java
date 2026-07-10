package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyRevisionListResponse(
        String currentRevisionUid,
        String candidateRevisionUid,
        String conflictStatus,
        List<VocabularyRevisionResponse> items) {
}
