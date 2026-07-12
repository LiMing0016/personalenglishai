package com.personalenglishai.backend.dto.vocabulary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record VocabularyCaptureRequest(
        @NotBlank @Size(max = 128) String clientRequestId,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 255) String> terms,
        @Size(max = 16) String language,
        String themeUid,
        @Pattern(regexp = "basic|exam|reading") String templateKey,
        @Valid Source source) {

    public record Source(
            @NotBlank @Pattern(regexp = "manual|dictionary") String type,
            @Size(max = 128) String sourceRef,
            @Size(max = 255) String sourceTitle,
            @Size(max = 1024) String sourceUrl,
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
                null,
                templateKey,
                new Source("manual", null, "手动输入", null, null, Map.of()));
    }
}
