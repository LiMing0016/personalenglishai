package com.personalenglishai.backend.service.translation;

public record DocumentParseRequest(
        String originalFilename,
        String contentType,
        byte[] bytes,
        String fileType,
        DocumentParseMode parseMode,
        String agentMode
) {
}
