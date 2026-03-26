package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.service.document.DocumentService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class DefaultScorePromptContextResolver {

    private final DocumentService documentService;

    public DefaultScorePromptContextResolver(DocumentService documentService) {
        this.documentService = documentService;
    }

    public ScorePromptContext resolve(WritingEvaluateRequest request,
                                      String model,
                                      String promptVersion,
                                      String rubricKey,
                                      String renderedRubricHash) {
        WritingSessionMetadataResponse metadata = loadMetadata(request);
        String mode = firstNonBlank(metadata == null ? null : metadata.getMode(), request.getMode(), "free");
        String stage = firstNonBlank(metadata == null ? null : metadata.getStudyStage(), request.getStudyStage(), "highschool");
        String taskType = firstNonBlank(metadata == null ? null : metadata.getTaskType(), request.getTaskType(), "unknown");
        String taskPrompt = firstNonBlank(metadata == null ? null : metadata.getPromptText(), request.getTaskPrompt(), null);
        String topicTitle = firstNonBlank(metadata == null ? null : metadata.getTopicTitle(), request.getTopicTitle(), null);
        Integer minWords = firstNonNull(metadata == null ? null : metadata.getMinWords(), request.getMinWords());
        Integer recommendedMaxWords = firstNonNull(metadata == null ? null : metadata.getRecommendedMaxWords(), request.getRecommendedMaxWords());
        Integer maxScore = firstNonNull(metadata == null ? null : metadata.getMaxScore(), request.getMaxScore());
        return new ScorePromptContext(
                trimToNull(request.getDocumentId()),
                firstNonBlank(model, "gpt-4o"),
                firstNonBlank(promptVersion, "score-v1"),
                firstNonBlank(rubricKey, "unknown"),
                stage,
                mode,
                taskType,
                sha256(taskPrompt),
                firstNonBlank(renderedRubricHash, sha256("")),
                taskPrompt,
                topicTitle,
                minWords,
                recommendedMaxWords,
                maxScore
        );
    }

    private WritingSessionMetadataResponse loadMetadata(WritingEvaluateRequest request) {
        String docId = trimToNull(request.getDocumentId());
        Long userId = request.getUserId();
        if (docId == null || userId == null) {
            return null;
        }
        return documentService.getSessionMetadataByDocId(String.valueOf(userId), "default", docId, userId);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sha256(String input) {
        String safe = input == null ? "" : input;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha-256 unavailable", e);
        }
    }
}
