package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyImageRecognitionResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.subscription.AiUsageContext;
import com.personalenglishai.backend.service.subscription.AiUsageContextHolder;
import com.personalenglishai.backend.service.subscription.AiUsageRecorder;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public final class VocabularyImageRecognitionService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final String FEATURE_KEY = "vocabulary.image_recognition";
    private static final String DICTIONARY_LANGUAGE = "en";
    private static final String DICTIONARY_WARNING = "DICTIONARY_VERIFICATION_UNAVAILABLE";
    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png"),
            "image/webp", Set.of(".webp"));

    private final VocabularyImageRecognitionPythonClient client;
    private final VocabularyDictionaryEnricher dictionary;
    private final SubscriptionService subscriptionService;
    private final AiUsageRecorder usageRecorder;

    public VocabularyImageRecognitionService(
            VocabularyImageRecognitionPythonClient client,
            VocabularyDictionaryEnricher dictionary,
            SubscriptionService subscriptionService,
            AiUsageRecorder usageRecorder) {
        this.client = client;
        this.dictionary = dictionary;
        this.subscriptionService = subscriptionService;
        this.usageRecorder = usageRecorder;
    }

    public VocabularyImageRecognitionResponse recognize(Long userId, MultipartFile file) {
        validate(userId, file);
        subscriptionService.assertAiTokenQuotaAvailable(userId);
        String traceId = "vocab-image-" + UUID.randomUUID().toString().replace("-", "");
        return AiUsageContextHolder.call(
                new AiUsageContext(userId, FEATURE_KEY, traceId),
                () -> recognizeInContext(traceId, file));
    }

    private VocabularyImageRecognitionResponse recognizeInContext(String traceId, MultipartFile file) {
        VocabularyImageRecognitionPythonResponse response;
        try {
            response = client.recognize(traceId, file);
        } catch (VocabularyImageRecognitionException exception) {
            throw mapFailure(exception);
        }
        recordUsage(traceId, response.generation());
        return enrich(response);
    }

    private void recordUsage(String traceId, VocabularyImageRecognitionPythonResponse.Generation generation) {
        VocabularyImageRecognitionPythonResponse.Usage usage = generation.usage();
        if (usage == null) {
            return;
        }
        usageRecorder.recordCurrentContext(
                generation.provider(),
                generation.model(),
                traceId,
                usage.inputTokens(),
                null,
                usage.outputTokens(),
                null,
                null);
    }

    private VocabularyImageRecognitionResponse enrich(VocabularyImageRecognitionPythonResponse response) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>(response.warnings());
        List<VocabularyImageRecognitionResponse.Item> items = new ArrayList<>(response.items().size());
        boolean dictionaryAvailable = true;
        for (VocabularyImageRecognitionPythonResponse.Item item : response.items()) {
            EnrichedItem enriched = enrichItem(item, dictionaryAvailable);
            items.add(enriched.item());
            if (!enriched.dictionaryAvailable()) {
                dictionaryAvailable = false;
                warnings.add(DICTIONARY_WARNING);
            }
        }
        return new VocabularyImageRecognitionResponse(
                response.contractVersion(), response.traceId(), response.rawText(), List.copyOf(warnings), items);
    }

    private EnrichedItem enrichItem(
            VocabularyImageRecognitionPythonResponse.Item item, boolean dictionaryAvailable) {
        if (!"suspected_typo".equals(item.status())) {
            return new EnrichedItem(toPublicItem(item, item.status(), List.of()), dictionaryAvailable);
        }
        if (!dictionaryAvailable) {
            return new EnrichedItem(toPublicItem(item, item.status(), unverified(item.suggestions())), false);
        }

        try {
            DictionaryLookupResponse original = dictionary.lookupWithoutUserState(
                    item.normalizedTerm(), DICTIONARY_LANGUAGE);
            if (original != null) {
                return new EnrichedItem(toPublicItem(item, "accepted", List.of()), true);
            }

            List<VocabularyImageRecognitionResponse.Suggestion> verified = new ArrayList<>();
            List<VocabularyImageRecognitionResponse.Suggestion> unverified = new ArrayList<>();
            for (String suggestion : item.suggestions()) {
                boolean hit = dictionary.lookupWithoutUserState(suggestion, DICTIONARY_LANGUAGE) != null;
                (hit ? verified : unverified).add(
                        new VocabularyImageRecognitionResponse.Suggestion(suggestion, hit));
            }
            verified.addAll(unverified);
            return new EnrichedItem(toPublicItem(item, item.status(), verified), true);
        } catch (DictionaryLookupException exception) {
            return new EnrichedItem(toPublicItem(item, item.status(), unverified(item.suggestions())), false);
        }
    }

    private List<VocabularyImageRecognitionResponse.Suggestion> unverified(List<String> suggestions) {
        return suggestions.stream()
                .map(term -> new VocabularyImageRecognitionResponse.Suggestion(term, false))
                .toList();
    }

    private VocabularyImageRecognitionResponse.Item toPublicItem(
            VocabularyImageRecognitionPythonResponse.Item item,
            String status,
            List<VocabularyImageRecognitionResponse.Suggestion> suggestions) {
        return new VocabularyImageRecognitionResponse.Item(
                item.itemId(),
                item.observedText(),
                item.normalizedTerm(),
                status,
                suggestions,
                item.contextText(),
                item.confidence());
    }

    private void validate(Long userId, MultipartFile file) {
        if (userId == null || userId <= 0 || file == null || file.isEmpty()
                || file.getSize() > MAX_IMAGE_BYTES || !hasSupportedType(file)) {
            throw new BizException(ErrorCode.VOCABULARY_IMAGE_INVALID);
        }
    }

    private boolean hasSupportedType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (contentType == null || filename == null) {
            return false;
        }
        Set<String> extensions = EXTENSIONS_BY_CONTENT_TYPE.get(contentType.toLowerCase(Locale.ROOT));
        if (extensions == null) {
            return false;
        }
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(basename::endsWith);
    }

    private BizException mapFailure(VocabularyImageRecognitionException exception) {
        return switch (exception.code()) {
            case "PYTHON_IMAGE_REQUEST_REJECTED" -> new BizException(ErrorCode.VOCABULARY_IMAGE_INVALID);
            case "PYTHON_IMAGE_OUTPUT_INVALID" -> new BizException(ErrorCode.VOCABULARY_IMAGE_OUTPUT_INVALID);
            case "PYTHON_IMAGE_TIMEOUT" -> new BizException(ErrorCode.VOCABULARY_IMAGE_TIMEOUT);
            default -> new BizException(ErrorCode.VOCABULARY_IMAGE_UNAVAILABLE);
        };
    }

    private record EnrichedItem(
            VocabularyImageRecognitionResponse.Item item,
            boolean dictionaryAvailable) {
    }
}
