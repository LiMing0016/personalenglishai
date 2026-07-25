package com.personalenglishai.backend.dto.vocabulary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record VocabularyProductEventBatchRequest(
        @NotEmpty @Size(max = 50) List<@Valid Event> events) {

    public record Event(
            @NotBlank @Size(max = 128) String eventUid,
            @NotBlank @Size(max = 64) String eventName,
            @Size(max = 128) String traceId,
            @NotBlank @Size(max = 128) String sessionId,
            @Size(max = 64) String cardUid,
            @NotNull LocalDateTime occurredAt,
            Map<String, Object> properties) {
    }
}
