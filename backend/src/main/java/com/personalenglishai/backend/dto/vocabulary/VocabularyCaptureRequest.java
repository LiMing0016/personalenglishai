package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;
import java.util.Map;

public record VocabularyCaptureRequest(
        String clientRequestId,
        List<String> terms,
        String language,
        String templateKey,
        Source source) {

    public record Source(
            String type,
            String sourceRef,
            String sourceTitle,
            String sourceUrl,
            String contextText,
            Map<String, Object> metadata) {
    }

    public static VocabularyCaptureRequest manual(
            String requestId,
            List<String> terms,
            String language,
            String templateKey) {
        return new VocabularyCaptureRequest(
                requestId,
                terms,
                language,
                templateKey,
                new Source("manual", null, "手动输入", null, null, Map.of()));
    }
}
