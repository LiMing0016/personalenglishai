package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class VocabularyCaptureService {
    private static final Logger log = LoggerFactory.getLogger(VocabularyCaptureService.class);
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of("manual", "dictionary");

    private final VocabularyCaptureItemService itemService;
    private final VocabularyTermNormalizer termNormalizer;

    public VocabularyCaptureService(
            VocabularyCaptureItemService itemService,
            VocabularyTermNormalizer termNormalizer) {
        this.itemService = itemService;
        this.termNormalizer = termNormalizer;
    }

    @Transactional
    public VocabularyCaptureResponse capture(Long userId, VocabularyCaptureRequest request) {
        validate(userId, request);

        List<VocabularyCaptureResponse.Item> items = new ArrayList<>(request.terms().size());
        for (int index = 0; index < request.terms().size(); index++) {
            try {
                items.add(itemService.captureOne(userId, request, index));
            } catch (RuntimeException exception) {
                log.warn(
                        "Vocabulary capture item rejected requestId={} index={} errorType={}",
                        request.clientRequestId(),
                        index,
                        exception.getClass().getSimpleName());
                items.add(new VocabularyCaptureResponse.Item(
                        request.terms().get(index), null, "rejected", "failed"));
            }
        }
        return new VocabularyCaptureResponse(items);
    }

    @Transactional
    public VocabularyCaptureResponse captureDictionaryFavorite(
            Long userId,
            String word,
            String language,
            String contextText) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        String normalizedTerm = termNormalizer.normalize(word);
        String requestId = dictionaryFavoriteRequestId(userId, normalizedTerm);
        VocabularyCaptureRequest request = new VocabularyCaptureRequest(
                requestId,
                List.of(word),
                canonicalLanguage(language),
                null,
                new VocabularyCaptureRequest.Source(
                        "dictionary",
                        "dictionary:" + normalizedTerm,
                        "词典收藏",
                        null,
                        contextText,
                        Map.of()));
        validate(userId, request);
        VocabularyCaptureResponse.Item item = itemService.captureOne(userId, request, 0);
        return new VocabularyCaptureResponse(List.of(item));
    }

    private void validate(Long userId, VocabularyCaptureRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request == null || request.clientRequestId() == null || request.clientRequestId().isBlank()) {
            throw new IllegalArgumentException("clientRequestId is required");
        }
        if (request.terms() == null || request.terms().isEmpty() || request.terms().size() > 100) {
            throw new IllegalArgumentException("terms must contain 1 to 100 items");
        }
        String sourceType = request.source() == null ? "manual" : request.source().type();
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("unsupported source type");
        }
    }

    private String canonicalLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("en-gb") || normalized.equals("en-us") ? "en" : normalized;
    }

    private String dictionaryFavoriteRequestId(Long userId, String normalizedTerm) {
        String value = userId + ":" + normalizedTerm;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "dictionary-favorite-" + userId + "-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
