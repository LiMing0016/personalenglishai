package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyImportAnalysisResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.subscription.AiUsageContext;
import com.personalenglishai.backend.service.subscription.AiUsageContextHolder;
import com.personalenglishai.backend.service.subscription.AiUsageRecorder;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
public final class VocabularyImportAnalysisService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final String FEATURE_KEY = "vocabulary.import_analysis";
    private static final String DICTIONARY_WARNING = "DICTIONARY_VERIFICATION_UNAVAILABLE";
    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png"),
            "image/webp", Set.of(".webp"));

    private final VocabularyImportAnalysisPythonClient client;
    private final VocabularyDictionaryEnricher dictionary;
    private final SubscriptionService subscriptionService;
    private final AiUsageRecorder usageRecorder;

    public VocabularyImportAnalysisService(
            VocabularyImportAnalysisPythonClient client,
            VocabularyDictionaryEnricher dictionary,
            SubscriptionService subscriptionService,
            AiUsageRecorder usageRecorder) {
        this.client = client;
        this.dictionary = dictionary;
        this.subscriptionService = subscriptionService;
        this.usageRecorder = usageRecorder;
    }

    public VocabularyImportAnalysisResponse analyze(
            Long userId,
            String text,
            MultipartFile file,
            String suppliedFingerprint) {
        validate(userId, text, file, suppliedFingerprint);
        byte[] imageBytes = readBytes(file);
        String verifiedFingerprint = VocabularyImportFingerprint.calculate(text, imageBytes);
        if (!constantTimeEquals(verifiedFingerprint, suppliedFingerprint)) {
            throw new BizException(ErrorCode.VOCABULARY_IMPORT_FINGERPRINT_MISMATCH);
        }

        subscriptionService.assertAiTokenQuotaAvailable(userId);
        String traceId = "vocab-import-" + UUID.randomUUID().toString().replace("-", "");
        return AiUsageContextHolder.call(
                new AiUsageContext(userId, FEATURE_KEY, traceId),
                () -> analyzeInContext(traceId, text, file, verifiedFingerprint));
    }

    private VocabularyImportAnalysisResponse analyzeInContext(
            String traceId,
            String text,
            MultipartFile file,
            String verifiedFingerprint) {
        VocabularyImportAnalysisPythonResponse response;
        try {
            response = client.analyze(traceId, text, file, verifiedFingerprint);
        } catch (VocabularyImportAnalysisException exception) {
            throw mapFailure(exception);
        }
        recordUsage(traceId, response.generation());
        return enrich(response, verifiedFingerprint);
    }

    private void recordUsage(String traceId, VocabularyImportAnalysisPythonResponse.Generation generation) {
        VocabularyImportAnalysisPythonResponse.Usage usage = generation.usage();
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

    private VocabularyImportAnalysisResponse enrich(
            VocabularyImportAnalysisPythonResponse response,
            String verifiedFingerprint) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>(response.warnings());
        List<VocabularyImportAnalysisResponse.Item> items;
        try {
            items = new ArrayList<>(response.items().size());
            for (VocabularyImportAnalysisPythonResponse.Item item : response.items()) {
                items.add(enrichItem(item));
            }
        } catch (DictionaryLookupException exception) {
            warnings.add(DICTIONARY_WARNING);
            items = response.items().stream().map(this::toOriginalPublicItem).toList();
        }
        return new VocabularyImportAnalysisResponse(
                response.contractVersion(),
                response.traceId(),
                verifiedFingerprint,
                response.rawText(),
                List.copyOf(warnings),
                items,
                toPublicGeneration(response.generation()));
    }

    private VocabularyImportAnalysisResponse.Item enrichItem(
            VocabularyImportAnalysisPythonResponse.Item item) {
        if (!"suspected_typo".equals(item.status())) {
            return toOriginalPublicItem(item);
        }
        DictionaryLookupResponse original = dictionary.lookupWithoutUserState(item.normalizedTerm(), "en");
        if (original != null) {
            return toPublicItem(item, "accepted", List.of());
        }
        List<VocabularyImportAnalysisResponse.Suggestion> verified = new ArrayList<>();
        List<VocabularyImportAnalysisResponse.Suggestion> unverified = new ArrayList<>();
        for (String suggestion : item.suggestions()) {
            boolean hit = dictionary.lookupWithoutUserState(suggestion, "en") != null;
            (hit ? verified : unverified).add(new VocabularyImportAnalysisResponse.Suggestion(suggestion, hit));
        }
        verified.addAll(unverified);
        return toPublicItem(item, item.status(), verified);
    }

    private VocabularyImportAnalysisResponse.Item toOriginalPublicItem(
            VocabularyImportAnalysisPythonResponse.Item item) {
        List<VocabularyImportAnalysisResponse.Suggestion> suggestions = "suspected_typo".equals(item.status())
                ? item.suggestions().stream()
                        .map(term -> new VocabularyImportAnalysisResponse.Suggestion(term, false))
                        .toList()
                : List.of();
        return toPublicItem(item, item.status(), suggestions);
    }

    private VocabularyImportAnalysisResponse.Item toPublicItem(
            VocabularyImportAnalysisPythonResponse.Item item,
            String status,
            List<VocabularyImportAnalysisResponse.Suggestion> suggestions) {
        return new VocabularyImportAnalysisResponse.Item(
                item.itemId(),
                item.observedText(),
                item.normalizedTerm(),
                status,
                suggestions,
                item.contextText(),
                item.confidence(),
                item.evidence());
    }

    private VocabularyImportAnalysisResponse.Generation toPublicGeneration(
            VocabularyImportAnalysisPythonResponse.Generation generation) {
        VocabularyImportAnalysisPythonResponse.Usage usage = generation.usage();
        return new VocabularyImportAnalysisResponse.Generation(
                generation.provider(),
                generation.model(),
                generation.promptVersion(),
                generation.modelCallCount(),
                generation.traceId(),
                usage == null
                        ? null
                        : new VocabularyImportAnalysisResponse.Usage(
                                usage.inputTokens(), usage.outputTokens()));
    }

    private void validate(Long userId, String text, MultipartFile file, String fingerprint) {
        String normalizedText = text == null ? "" : text;
        boolean hasText = !normalizedText.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (userId == null || userId <= 0 || normalizedText.length() > MAX_TEXT_LENGTH
                || (!hasText && !hasFile) || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new BizException(ErrorCode.VOCABULARY_IMPORT_INVALID);
        }
        if (file != null && (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES || !hasSupportedType(file))) {
            throw new BizException(ErrorCode.VOCABULARY_IMPORT_INVALID);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BizException(ErrorCode.VOCABULARY_IMPORT_INVALID);
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

    private boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }

    private BizException mapFailure(VocabularyImportAnalysisException exception) {
        return switch (exception.code()) {
            case "PYTHON_IMPORT_REQUEST_REJECTED" -> new BizException(ErrorCode.VOCABULARY_IMPORT_INVALID);
            case "PYTHON_IMPORT_OUTPUT_INVALID" -> new BizException(ErrorCode.VOCABULARY_IMPORT_OUTPUT_INVALID);
            case "PYTHON_IMPORT_TIMEOUT" -> new BizException(ErrorCode.VOCABULARY_IMPORT_TIMEOUT);
            default -> new BizException(ErrorCode.VOCABULARY_IMPORT_UNAVAILABLE);
        };
    }
}
