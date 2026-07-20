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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyImageRecognitionServiceTest {
    private static final long USER_ID = 7L;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    @Mock VocabularyImageRecognitionPythonClient client;
    @Mock VocabularyDictionaryEnricher dictionary;
    @Mock SubscriptionService subscriptionService;
    @Mock AiUsageRecorder usageRecorder;

    private VocabularyImageRecognitionService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyImageRecognitionService(client, dictionary, subscriptionService, usageRecorder);
    }

    @Test
    void rejects_missing_user_and_empty_file_before_dependencies() {
        assertInvalid(() -> service.recognize(null, png("words.png", 20)));
        assertInvalid(() -> service.recognize(USER_ID, null));
        assertInvalid(() -> service.recognize(USER_ID, png("words.png", 0)));

        verifyNoInteractions(subscriptionService, client, usageRecorder, dictionary);
    }

    @Test
    void accepts_file_at_exactly_ten_mib() {
        stubAcceptedResponse("private review text");

        VocabularyImageRecognitionResponse response = service.recognize(
                USER_ID, png("words.png", MAX_IMAGE_BYTES));

        assertEquals("private review text", response.rawText());
        verify(client).recognize(anyString(), any(MultipartFile.class));
    }

    @Test
    void rejects_file_larger_than_ten_mib_before_quota_check() {
        assertInvalid(() -> service.recognize(USER_ID, png("words.png", MAX_IMAGE_BYTES + 1)));

        verifyNoInteractions(subscriptionService, client, usageRecorder, dictionary);
    }

    @ParameterizedTest
    @MethodSource("allowedImageTypes")
    void accepts_supported_matching_mime_and_extension(String filename, String contentType) {
        stubAcceptedResponse("");

        service.recognize(USER_ID, image(filename, contentType, 20));

        verify(client).recognize(anyString(), any(MultipartFile.class));
    }

    static Stream<Arguments> allowedImageTypes() {
        return Stream.of(
                Arguments.of("words.jpg", "image/jpeg"),
                Arguments.of("words.JPEG", "image/jpeg"),
                Arguments.of("words.png", "image/png"),
                Arguments.of("words.WEBP", "image/webp"));
    }

    @ParameterizedTest
    @MethodSource("invalidImageTypes")
    void rejects_unsupported_or_mismatched_mime_and_extension(String filename, String contentType) {
        assertInvalid(() -> service.recognize(USER_ID, image(filename, contentType, 20)));

        verifyNoInteractions(subscriptionService, client, usageRecorder, dictionary);
    }

    static Stream<Arguments> invalidImageTypes() {
        return Stream.of(
                Arguments.of("words.jpg", "image/png"),
                Arguments.of("words.png", "image/jpeg"),
                Arguments.of("words.gif", "image/gif"),
                Arguments.of("words", "image/png"),
                Arguments.of("words.png", null));
    }

    @Test
    void quota_rejection_happens_before_trace_context_and_python_call() {
        BizException quota = new BizException(ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED);
        org.mockito.Mockito.doThrow(quota).when(subscriptionService).assertAiTokenQuotaAvailable(USER_ID);

        assertThatThrownBy(() -> service.recognize(USER_ID, png("words.png", 20)))
                .isSameAs(quota);

        assertNull(AiUsageContextHolder.current());
        verifyNoInteractions(client, usageRecorder, dictionary);
    }

    @Test
    void invokes_python_once_in_usage_context_and_records_usage_before_dictionary() {
        when(client.recognize(anyString(), any())).thenAnswer(invocation -> {
            String traceId = invocation.getArgument(0);
            assertThat(traceId).matches("vocab-image-[0-9a-f]{32}");
            assertEquals(new AiUsageContext(USER_ID, "vocabulary.image_recognition", traceId),
                    AiUsageContextHolder.current());
            return typoResponse(traceId, "private review text", List.of(), "teh", List.of("the"));
        });
        when(dictionary.lookupWithoutUserState("teh", "en")).thenReturn(null);
        when(dictionary.lookupWithoutUserState("the", "en")).thenReturn(dictionaryHit("the"));

        VocabularyImageRecognitionResponse response = service.recognize(USER_ID, png("words.png", 20));

        assertNull(AiUsageContextHolder.current());
        assertEquals("private review text", response.rawText());
        InOrder order = inOrder(subscriptionService, client, usageRecorder, dictionary);
        order.verify(subscriptionService).assertAiTokenQuotaAvailable(USER_ID);
        order.verify(client).recognize(anyString(), any(MultipartFile.class));
        order.verify(usageRecorder).recordCurrentContext(
                org.mockito.ArgumentMatchers.eq("openai"),
                org.mockito.ArgumentMatchers.eq("gpt-image"),
                org.mockito.ArgumentMatchers.matches("vocab-image-[0-9a-f]{32}"),
                org.mockito.ArgumentMatchers.eq(11),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
        order.verify(dictionary).lookupWithoutUserState("teh", "en");
        order.verify(dictionary).lookupWithoutUserState("the", "en");
        verify(client).recognize(anyString(), any(MultipartFile.class));
    }

    @Test
    void original_dictionary_hit_downgrades_model_typo_to_accepted() {
        stubTypoResponse("colour", List.of("color"));
        when(dictionary.lookupWithoutUserState("colour", "en")).thenReturn(dictionaryHit("colour"));

        VocabularyImageRecognitionResponse response = service.recognize(USER_ID, png("words.png", 20));

        assertEquals("accepted", response.items().get(0).status());
        assertTrue(response.items().get(0).suggestions().isEmpty());
        verify(dictionary, never()).lookupWithoutUserState("color", "en");
    }

    @Test
    void verified_suggestions_sort_first_while_preserving_relative_order() {
        stubTypoResponse("teh", List.of("teh", "the", "ten"));
        when(dictionary.lookupWithoutUserState("teh", "en"))
                .thenReturn(null)
                .thenReturn(null);
        when(dictionary.lookupWithoutUserState("the", "en")).thenReturn(dictionaryHit("the"));
        when(dictionary.lookupWithoutUserState("ten", "en")).thenReturn(dictionaryHit("ten"));

        VocabularyImageRecognitionResponse response = service.recognize(USER_ID, png("words.png", 20));

        assertThat(response.items().get(0).suggestions())
                .containsExactly(
                        new VocabularyImageRecognitionResponse.Suggestion("the", true),
                        new VocabularyImageRecognitionResponse.Suggestion("ten", true),
                        new VocabularyImageRecognitionResponse.Suggestion("teh", false));
    }

    @Test
    void dictionary_misses_preserve_model_typo_and_unverified_suggestions() {
        stubTypoResponse("teh", List.of("the"));
        when(dictionary.lookupWithoutUserState(anyString(), org.mockito.ArgumentMatchers.eq("en"))).thenReturn(null);

        VocabularyImageRecognitionResponse response = service.recognize(USER_ID, png("words.png", 20));

        assertEquals("suspected_typo", response.items().get(0).status());
        assertThat(response.items().get(0).suggestions())
                .containsExactly(new VocabularyImageRecognitionResponse.Suggestion("the", false));
        assertTrue(response.warnings().isEmpty());
    }

    @Test
    void accepted_items_never_query_dictionary() {
        stubAcceptedResponse("review text");

        service.recognize(USER_ID, png("words.png", 20));

        verifyNoInteractions(dictionary);
    }

    @Test
    void dictionary_unavailability_adds_one_warning_and_preserves_all_model_typo_states() {
        when(client.recognize(anyString(), any())).thenAnswer(invocation -> {
            String traceId = invocation.getArgument(0);
            return response(traceId, "private review text", List.of("CANDIDATE_LIMIT_REACHED"), List.of(
                    pythonItem("item-1", "teh", "teh", "suspected_typo", List.of("the")),
                    pythonItem("item-2", "recieve", "recieve", "suspected_typo", List.of("receive"))));
        });
        when(dictionary.lookupWithoutUserState("teh", "en"))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.TIMEOUT));

        VocabularyImageRecognitionResponse response = service.recognize(USER_ID, png("words.png", 20));

        assertThat(response.warnings()).containsExactly(
                "CANDIDATE_LIMIT_REACHED", "DICTIONARY_VERIFICATION_UNAVAILABLE");
        assertThat(response.items()).extracting(VocabularyImageRecognitionResponse.Item::status)
                .containsExactly("suspected_typo", "suspected_typo");
        assertThat(response.items().get(0).suggestions())
                .containsExactly(new VocabularyImageRecognitionResponse.Suggestion("the", false));
        assertThat(response.items().get(1).suggestions())
                .containsExactly(new VocabularyImageRecognitionResponse.Suggestion("receive", false));
        verify(dictionary).lookupWithoutUserState("teh", "en");
        verify(dictionary, never()).lookupWithoutUserState("recieve", "en");
        verify(dictionary, never()).lookupWithoutUserState("receive", "en");
    }

    @Test
    void public_success_keeps_raw_text_but_mapped_errors_do_not_leak_private_inputs() {
        String rawText = "private OCR body";
        stubAcceptedResponse(rawText);
        assertEquals(rawText, service.recognize(USER_ID, png("words.png", 20)).rawText());

        String upstreamSecret = rawText + " C:\\private\\words.png upstream body";
        doThrow(new VocabularyImageRecognitionException(
                "PYTHON_IMAGE_OUTPUT_INVALID", false, upstreamSecret))
                .when(client).recognize(anyString(), any());

        assertThatThrownBy(() -> service.recognize(USER_ID, png("words.png", 20)))
                .isInstanceOf(BizException.class)
                .hasMessage(ErrorCode.VOCABULARY_IMAGE_OUTPUT_INVALID.getMessage())
                .hasMessageNotContaining(rawText)
                .hasMessageNotContaining("words.png")
                .hasMessageNotContaining("upstream body");
    }

    private void stubAcceptedResponse(String rawText) {
        when(client.recognize(anyString(), any())).thenAnswer(invocation -> {
            String traceId = invocation.getArgument(0);
            return response(traceId, rawText, List.of(), List.of(
                    pythonItem("item-1", "colour", "colour", "accepted", List.of())));
        });
    }

    private void stubTypoResponse(String term, List<String> suggestions) {
        when(client.recognize(anyString(), any())).thenAnswer(invocation -> {
            String traceId = invocation.getArgument(0);
            return typoResponse(traceId, "review text", List.of(), term, suggestions);
        });
    }

    private VocabularyImageRecognitionPythonResponse typoResponse(
            String traceId, String rawText, List<String> warnings, String term, List<String> suggestions) {
        return response(traceId, rawText, warnings, List.of(
                pythonItem("item-1", term, term, "suspected_typo", suggestions)));
    }

    private VocabularyImageRecognitionPythonResponse response(
            String traceId,
            String rawText,
            List<String> warnings,
            List<VocabularyImageRecognitionPythonResponse.Item> items) {
        return new VocabularyImageRecognitionPythonResponse(
                1,
                traceId,
                rawText,
                warnings,
                items,
                new VocabularyImageRecognitionPythonResponse.Generation(
                        "openai",
                        "gpt-image",
                        "vocabulary-image-recognition-v1",
                        1,
                        traceId,
                        new VocabularyImageRecognitionPythonResponse.Usage(11, 5)));
    }

    private VocabularyImageRecognitionPythonResponse.Item pythonItem(
            String itemId,
            String observedText,
            String normalizedTerm,
            String status,
            List<String> suggestions) {
        return new VocabularyImageRecognitionPythonResponse.Item(
                itemId, observedText, normalizedTerm, status, suggestions, "context", 0.91);
    }

    private DictionaryLookupResponse dictionaryHit(String term) {
        DictionaryLookupResponse response = new DictionaryLookupResponse();
        response.setWord(term);
        response.setLanguage("en");
        return response;
    }

    private MockMultipartFile png(String filename, int size) {
        return image(filename, "image/png", size);
    }

    private MockMultipartFile image(String filename, String contentType, int size) {
        return new MockMultipartFile("file", filename, contentType, new byte[size]);
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.VOCABULARY_IMAGE_INVALID);
    }
}
