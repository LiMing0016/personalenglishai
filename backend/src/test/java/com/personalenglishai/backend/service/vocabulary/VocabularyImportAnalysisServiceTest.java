package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.VocabularyImportAnalysisResponse;
import com.personalenglishai.backend.service.subscription.AiUsageRecorder;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VocabularyImportAnalysisServiceTest {

    private final VocabularyImportAnalysisPythonClient client = mock(VocabularyImportAnalysisPythonClient.class);
    private final VocabularyDictionaryEnricher dictionary = mock(VocabularyDictionaryEnricher.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AiUsageRecorder usageRecorder = mock(AiUsageRecorder.class);
    private VocabularyImportAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyImportAnalysisService(client, dictionary, subscriptionService, usageRecorder);
    }

    @Test
    void rejects_mismatched_fingerprint_before_quota_or_python_call() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.analyze(7L, "package", null, "b".repeat(64)));

        assertEquals(ErrorCode.VOCABULARY_IMPORT_FINGERPRINT_MISMATCH, error.getErrorCode());
        verifyNoInteractions(subscriptionService, client, usageRecorder);
    }

    @Test
    void text_only_success_preserves_verified_fingerprint_and_evidence() {
        String fingerprint = VocabularyImportFingerprint.calculate("package", null);
        when(client.analyze(anyString(), eq("package"), eq(null), eq(fingerprint)))
                .thenReturn(response(fingerprint));

        VocabularyImportAnalysisResponse result = service.analyze(
                7L, "package", null, fingerprint);

        assertEquals(fingerprint, result.inputFingerprint());
        assertEquals("text", result.items().get(0).evidence());
        verify(subscriptionService).assertAiTokenQuotaAvailable(7L);
        verify(client).analyze(anyString(), eq("package"), eq(null), eq(fingerprint));
    }

    @Test
    void combined_input_hashes_raw_file_bytes_and_maps_timeout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "words.png", "image/png", "image-data".getBytes(StandardCharsets.UTF_8));
        String fingerprint = VocabularyImportFingerprint.calculate("package", file.getBytes());
        doThrow(new VocabularyImportAnalysisException(
                "PYTHON_IMPORT_TIMEOUT", true, "private timeout"))
                .when(client).analyze(anyString(), eq("package"), eq(file), eq(fingerprint));

        BizException error = assertThrows(
                BizException.class,
                () -> service.analyze(7L, "package", file, fingerprint));

        assertEquals(ErrorCode.VOCABULARY_IMPORT_TIMEOUT, error.getErrorCode());
    }

    private VocabularyImportAnalysisPythonResponse response(String fingerprint) {
        return new VocabularyImportAnalysisPythonResponse(
                1,
                "trace_123",
                fingerprint,
                "package",
                List.of(),
                List.of(new VocabularyImportAnalysisPythonResponse.Item(
                        "item_1",
                        "package",
                        "package",
                        "accepted",
                        List.of(),
                        null,
                        0.98,
                        "text")),
                new VocabularyImportAnalysisPythonResponse.Generation(
                        "openai",
                        "test-model",
                        "vocabulary-import-analysis-v1",
                        1,
                        "trace_123",
                        null));
    }
}
