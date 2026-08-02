package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardDetailResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardResolutionResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardSummaryResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyConflictResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyThemeCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyThemeResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyImageRecognitionResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyImportAnalysisResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyProductEventBatchResponse;
import com.personalenglishai.backend.service.vocabulary.VocabularyRevisionConflictException;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.vocabulary.VocabularyCaptureService;
import com.personalenglishai.backend.service.vocabulary.VocabularyCardService;
import com.personalenglishai.backend.service.vocabulary.VocabularyThemeService;
import com.personalenglishai.backend.service.vocabulary.VocabularyTemplateRegistry;
import com.personalenglishai.backend.service.vocabulary.VocabularyImageRecognitionService;
import com.personalenglishai.backend.service.vocabulary.VocabularyImportAnalysisService;
import com.personalenglishai.backend.service.vocabulary.VocabularyImportFingerprint;
import com.personalenglishai.backend.service.vocabulary.VocabularyProductEventService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(VocabularyController.class)
@AutoConfigureMockMvc(addFilters = false)
class VocabularyControllerTest {
    @Resource MockMvc mockMvc;
    @MockBean VocabularyCaptureService captureService;
    @MockBean VocabularyCardService cardService;
    @MockBean VocabularyThemeService themeService;
    @MockBean VocabularyImageRecognitionService imageRecognitionService;
    @MockBean VocabularyImportAnalysisService importAnalysisService;
    @MockBean VocabularyProductEventService productEventService;
    @MockBean VocabularyTemplateRegistry templateRegistry;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtInterceptor jwtInterceptor;

    @Test
    void analyzes_text_import_and_returns_verified_fingerprint() throws Exception {
        String fingerprint = VocabularyImportFingerprint.calculate("package", null);
        when(importAnalysisService.analyze(7L, "package", null, fingerprint))
                .thenReturn(new VocabularyImportAnalysisResponse(
                        1,
                        "trace_123",
                        fingerprint,
                        "package",
                        List.of(),
                        List.of(new VocabularyImportAnalysisResponse.Item(
                                "item_1", "package", "package", "accepted", List.of(), null, 0.98, "text")),
                        new VocabularyImportAnalysisResponse.Generation(
                                "openai", "test-model", "vocabulary-import-analysis-v1", 1, "trace_123", null)));

        mockMvc.perform(multipart("/api/vocabulary/import-analyses")
                        .param("text", "package")
                        .param("inputFingerprint", fingerprint)
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputFingerprint").value(fingerprint))
                .andExpect(jsonPath("$.data.items[0].evidence").value("text"));

        verify(importAnalysisService).analyze(7L, "package", null, fingerprint);
    }

    @Test
    void maps_import_fingerprint_mismatch_to_stable_bad_request() throws Exception {
        String fingerprint = "b".repeat(64);
        doThrow(new BizException(ErrorCode.VOCABULARY_IMPORT_FINGERPRINT_MISMATCH))
                .when(importAnalysisService).analyze(7L, "package", null, fingerprint);

        mockMvc.perform(multipart("/api/vocabulary/import-analyses")
                        .param("text", "package")
                        .param("inputFingerprint", fingerprint)
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400054"));
    }

    @Test
    void acceptsAuthenticatedVocabularyProductEventBatch() throws Exception {
        when(productEventService.acceptBatch(eq(7L), any()))
                .thenReturn(new VocabularyProductEventBatchResponse(1, 1));

        mockMvc.perform(post("/api/vocabulary/product-events/batch")
                        .requestAttr("userId", 7L)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"events":[
                                  {"eventUid":"event-1","eventName":"vocabulary_learning_started",
                                   "sessionId":"session-1","cardUid":"card_1",
                                   "occurredAt":"2026-07-21T04:30:00","properties":{}},
                                  {"eventUid":"event-2","eventName":"vocabulary_learning_started",
                                   "sessionId":"session-1","cardUid":"card_2",
                                   "occurredAt":"2026-07-21T04:30:01","properties":{}}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.duplicate").value(1));

        verify(productEventService).acceptBatch(eq(7L), argThat(request -> request.events().size() == 2));
    }

    @Test
    void rejectsUnauthenticatedVocabularyProductEventBatch() throws Exception {
        mockMvc.perform(post("/api/vocabulary/product-events/batch")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"events":[{"eventUid":"event-1",
                                  "eventName":"vocabulary_learning_started","sessionId":"session-1",
                                  "occurredAt":"2026-07-21T04:30:00","properties":{}}]}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productEventService);
    }

    @Test
    void recognizesVocabularyImageByDelegatingMultipartToService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "words.png", "image/png", new byte[] {1, 2, 3});
        var response = new VocabularyImageRecognitionResponse(
                1,
                "vocab-image-0123456789abcdef0123456789abcdef",
                "private OCR review text",
                List.of(),
                List.of(new VocabularyImageRecognitionResponse.Item(
                        "item-1", "colour", "colour", "accepted", List.of(), "context", 0.95)),
                new VocabularyImageRecognitionResponse.Generation(
                        "openai",
                        "gpt-image",
                        "vocabulary-image-recognition-v1",
                        1,
                        "vocab-image-0123456789abcdef0123456789abcdef",
                        new VocabularyImageRecognitionResponse.Usage(11, 5)));
        when(imageRecognitionService.recognize(eq(7L), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .file(file)
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawText").value("private OCR review text"))
                .andExpect(jsonPath("$.data.items[0].status").value("accepted"))
                .andExpect(jsonPath("$.data.generation.provider").value("openai"))
                .andExpect(jsonPath("$.data.generation.usage.inputTokens").value(11));

        verify(imageRecognitionService).recognize(7L, file);
    }

    @Test
    void rejectsAnonymousVocabularyImageWithoutCallingService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "words.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/vocabulary/image-recognitions").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));

        verifyNoInteractions(imageRecognitionService);
    }

    @Test
    void rejectsDuplicateVocabularyImageFilePartsWithoutCallingService() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
                "file", "first.png", "image/png", new byte[] {1});
        MockMultipartFile second = new MockMultipartFile(
                "file", "second.png", "image/png", new byte[] {2});

        mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .file(first)
                        .file(second)
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400052"));

        verifyNoInteractions(imageRecognitionService);
    }

    @Test
    void rejectsMissingVocabularyImageFilePartWithStableBadRequest() throws Exception {
                mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400001"))
                .andExpect(jsonPath("$.message").value("参数验证失败"));

        verifyNoInteractions(imageRecognitionService);
    }

    @Test
    void mapsEmptyAndInvalidVocabularyImagesToBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "words.png", "image/png", new byte[0]);
        MockMultipartFile invalid = new MockMultipartFile("file", "words.gif", "image/gif", new byte[] {1});
        doThrow(new BizException(ErrorCode.VOCABULARY_IMAGE_INVALID))
                .when(imageRecognitionService).recognize(eq(7L), any());

        mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .file(empty)
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400052"));
        mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .file(invalid)
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400052"));
    }

    @Test
    void mapsVocabularyImageQuotaAndUpstreamFailuresToStableStatusesWithoutPrivateBody() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "private-path.png", "image/png", "private file body".getBytes());

        assertImageError(file, ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED, 429, "429010");
        assertImageError(file, ErrorCode.VOCABULARY_IMAGE_OUTPUT_INVALID, 502, "502050");
        assertImageError(file, ErrorCode.VOCABULARY_IMAGE_UNAVAILABLE, 503, "503050");
        assertImageError(file, ErrorCode.VOCABULARY_IMAGE_TIMEOUT, 504, "504050");
    }

    private void assertImageError(
            MockMultipartFile file, ErrorCode errorCode, int statusCode, String responseCode) throws Exception {
        org.mockito.Mockito.reset(imageRecognitionService);
        doThrow(new BizException(errorCode)).when(imageRecognitionService).recognize(eq(7L), any());

        String responseBody = mockMvc.perform(multipart("/api/vocabulary/image-recognitions")
                        .file(file)
                        .requestAttr("userId", 7L))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(responseCode))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains("private file body"));
        assertFalse(responseBody.contains("private-path.png"));
    }

    @Test
    void capturesManualTerms() throws Exception {
        when(captureService.capture(eq(7L), any())).thenReturn(new VocabularyCaptureResponse(List.of(
                new VocabularyCaptureResponse.Item("innovative", "card_1", "created", "generating"))));

        mockMvc.perform(post("/api/vocabulary/captures")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {"clientRequestId":"req-1","terms":["innovative"],"language":"en","templateKey":"basic","source":{"type":"manual"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cardUid").value("card_1"));
    }

    @Test
    void acceptsOcrCaptureEnvelopeWithIndexedSources() throws Exception {
        when(captureService.capture(eq(7L), any())).thenReturn(new VocabularyCaptureResponse(List.of(
                new VocabularyCaptureResponse.Item("receive", "card_ocr", "created", "generating"))));

        mockMvc.perform(post("/api/vocabulary/captures")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "clientRequestId":"req-ocr",
                                  "terms":["receive"],
                                  "language":"en",
                                  "templateKey":"basic",
                                  "source":{"type":"ocr_image","metadata":{"recognitionTraceId":"trace-1","fileName":"words.png","provider":"openai","model":"vision-model","promptVersion":"vocabulary-image-recognition-v1"}},
                                  "itemSources":[{"contextText":"I receive it","metadata":{"observedText":"recieve","resolution":"suggestion_applied"}}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cardUid").value("card_ocr"));

        verify(captureService).capture(eq(7L), argThat(request ->
                request.itemSources().size() == 1
                        && "recieve".equals(request.itemSources().get(0).metadata().get("observedText"))));
    }

    @Test
    void rejectsInvalidCaptureEnvelopeBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/vocabulary/captures")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {"clientRequestId":" ","terms":[],"language":"en","source":{"type":"assistant"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400001"));

        verifyNoInteractions(captureService);
    }

    @Test
    void exposesTemplateCatalog() throws Exception {
        var catalog = new VocabularyTemplateCatalogResponse(
                List.of(new VocabularyTemplateResponse("basic", 1, "Basic", List.of("term"))),
                "basic");
        when(cardService.templateCatalog(7L)).thenReturn(catalog);

        mockMvc.perform(get("/api/vocabulary/templates").requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTemplateKey").value("basic"))
                .andExpect(jsonPath("$.data.items[0].key").value("basic"));
    }

    @Test
    void exposesThemeCatalogAndCrudEndpoints() throws Exception {
        var theme = new VocabularyThemeResponse(
                "theme_user_1", "user", "My theme", "Focus", 1, "active",
                false, true, false, "custom-markdown-v1");
        when(themeService.catalog(7L)).thenReturn(new VocabularyThemeCatalogResponse(
                List.of(new VocabularyThemeResponse("theme_system_basic", "system", "Basic", "", 1,
                        "active", true, true, false, "basic-markdown-v1")),
                List.of(theme), "theme_system_basic", List.of()));
        when(themeService.create(eq(7L), any())).thenReturn(theme);
        when(themeService.update(eq(7L), eq("theme_user_1"), any())).thenReturn(theme);
        when(themeService.copy(7L, "theme_system_basic")).thenReturn(theme);

        mockMvc.perform(get("/api/vocabulary/themes").requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemThemes[0].themeUid").value("theme_system_basic"));

        mockMvc.perform(post("/api/vocabulary/themes")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"name\":\"My theme\",\"purpose\":\"Focus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promptStrategyKey").value("custom-markdown-v1"));

        mockMvc.perform(put("/api/vocabulary/themes/theme_user_1")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"name\":\"My theme\",\"purpose\":\"Focus\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vocabulary/themes/theme_system_basic/copy").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vocabulary/themes/theme_user_1/default").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vocabulary/themes/theme_user_1/disable").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/vocabulary/themes/theme_user_1").requestAttr("userId", 7L))
                .andExpect(status().isOk());

        verify(themeService).setDefault(7L, "theme_user_1");
        verify(themeService).disable(7L, "theme_user_1");
        verify(themeService).delete(7L, "theme_user_1");
    }

    @Test
    void rejectsAnonymousThemeRequests() throws Exception {
        mockMvc.perform(get("/api/vocabulary/themes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(post("/api/vocabulary/themes")
                        .contentType("application/json")
                        .content("{\"name\":\"My theme\",\"purpose\":\"Focus\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(put("/api/vocabulary/themes/theme_user_1")
                        .contentType("application/json")
                        .content("{\"name\":\"My theme\",\"purpose\":\"Focus\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(post("/api/vocabulary/themes/theme_system_basic/copy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(post("/api/vocabulary/themes/theme_user_1/default"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(post("/api/vocabulary/themes/theme_user_1/disable"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
        mockMvc.perform(delete("/api/vocabulary/themes/theme_user_1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"));
    }

    @Test
    void rejectsInvalidThemeCreateAndUpdateBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/vocabulary/themes")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400001"));
        mockMvc.perform(put("/api/vocabulary/themes/theme_user_1")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400001"));

        verifyNoInteractions(themeService);
    }

    @Test
    void listsOwnedCardsWithFilters() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);
        var item = new VocabularyCardSummaryResponse(
                "card_1", "Innovative", "innovative", "basic", "ready", "rev_1",
                List.of("manual"), now, now);
        when(cardService.list(7L, "inno", "ready", "manual", "az", 2, 25))
                .thenReturn(new AdminPageResponse<>(List.of(item), 1, 2, 25));

        mockMvc.perform(get("/api/vocabulary/cards")
                        .requestAttr("userId", 7L)
                        .param("keyword", "inno")
                        .param("status", "ready")
                        .param("sourceType", "manual")
                        .param("sort", "az")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].cardUid").value("card_1"))
                .andExpect(jsonPath("$.data.items[0].sourceTypes[0]").value("manual"));

        verify(cardService).list(7L, "inno", "ready", "manual", "az", 2, 25);
    }

    @Test
    void listsOcrImageCardsWithExistingSourceFilter() throws Exception {
        when(cardService.list(7L, null, null, "ocr_image", "recent", 1, 20))
                .thenReturn(new AdminPageResponse<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/vocabulary/cards")
                        .requestAttr("userId", 7L)
                        .param("sourceType", "ocr_image"))
                .andExpect(status().isOk());

        verify(cardService).list(7L, null, null, "ocr_image", "recent", 1, 20);
    }

    @Test
    void resolvesOwnedCardByTermAndLanguage() throws Exception {
        when(cardService.resolve(7L, "Wonder", "en"))
                .thenReturn(VocabularyCardResolutionResponse.found("card_wonder"));

        mockMvc.perform(get("/api/vocabulary/cards/resolve")
                        .requestAttr("userId", 7L)
                        .param("term", "Wonder")
                        .param("language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.cardUid").value("card_wonder"));

        verify(cardService).resolve(7L, "Wonder", "en");
    }

    @Test
    void resolvesMissingCardWithoutTurningAbsenceIntoAnError() throws Exception {
        when(cardService.resolve(7L, "absent", "en"))
                .thenReturn(VocabularyCardResolutionResponse.notFound());

        mockMvc.perform(get("/api/vocabulary/cards/resolve")
                        .requestAttr("userId", 7L)
                        .param("term", "absent")
                        .param("language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(false))
                .andExpect(jsonPath("$.data.cardUid").doesNotExist());
    }

    @Test
    void exposesOwnedCardDetail() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);
        var content = JsonNodeFactory.instance.objectNode().put("term", "innovative");
        var detail = new VocabularyCardDetailResponse(
                "card_1", "Innovative", "innovative", "en", "basic", 1, "ready", "rev_1",
                List.of("manual"), content, List.of(), "succeeded", null, now, now, now);
        when(cardService.getDetail(7L, "card_1")).thenReturn(detail);

        mockMvc.perform(get("/api/vocabulary/cards/card_1").requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardUid").value("card_1"))
                .andExpect(jsonPath("$.data.sourceTypes[0]").value("manual"))
                .andExpect(jsonPath("$.data.content.term").value("innovative"));
    }

    @Test
    void mapsMissingOrForeignCardToNotFound() throws Exception {
        when(cardService.getDetail(7L, "card_1"))
                .thenThrow(new BizException(ErrorCode.VOCABULARY_CARD_NOT_FOUND));

        mockMvc.perform(get("/api/vocabulary/cards/card_1").requestAttr("userId", 7L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404050"));
    }

    @Test
    void mapsStoredJsonFailureToGenericErrorWithoutRawJsonLeakage() throws Exception {
        String rawJson = "{\"raw-secret\":\"private-value\"";
        when(cardService.getDetail(7L, "card_1"))
                .thenThrow(new IllegalStateException("invalid stored content_json"));

        String responseBody = mockMvc.perform(
                        get("/api/vocabulary/cards/card_1").requestAttr("userId", 7L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500000"))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains("raw-secret"));
        assertFalse(responseBody.contains("private-value"));
    }

    @Test
    void rejectsAnonymousVocabularyRequests() throws Exception {
        mockMvc.perform(post("/api/vocabulary/captures")
                        .contentType("application/json")
                        .content("""
                                {"clientRequestId":"req-1","terms":["innovative"],"language":"en","source":{"type":"manual"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        mockMvc.perform(get("/api/vocabulary/templates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        mockMvc.perform(get("/api/vocabulary/cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        mockMvc.perform(get("/api/vocabulary/cards/resolve")
                        .param("term", "wonder")
                        .param("language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        mockMvc.perform(get("/api/vocabulary/cards/card_1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401001"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void exposesCardMutationEndpointsAndRevisionHistory() throws Exception {
        mockMvc.perform(put("/api/vocabulary/cards/card_1")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {"baseRevisionUid":"rev_1","content":{"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":"edited"}}
                                """))
                .andExpect(status().isOk());
        verify(cardService).update(eq(7L), eq("card_1"), any());

        mockMvc.perform(delete("/api/vocabulary/cards/card_1").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        verify(cardService).delete(7L, "card_1");

        mockMvc.perform(get("/api/vocabulary/cards/card_1/revisions").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        verify(cardService).revisions(7L, "card_1");

        mockMvc.perform(post("/api/vocabulary/cards/card_1/regenerate").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        verify(cardService).regenerate(eq(7L), eq("card_1"), isNull());

        mockMvc.perform(post("/api/vocabulary/cards/card_1/retry").requestAttr("userId", 7L))
                .andExpect(status().isOk());
        verify(cardService).retry(7L, "card_1");

        mockMvc.perform(post("/api/vocabulary/cards/card_1/conflicts/rev_candidate/resolve")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"choice\":\"keep_current\"}"))
                .andExpect(status().isOk());
        verify(cardService).resolveConflict(eq(7L), eq("card_1"), eq("rev_candidate"), any());
    }

    @Test
    void acceptsStructuredCardBlocksOnVocabularyCardUpdate() throws Exception {
        mockMvc.perform(put("/api/vocabulary/cards/card_1")
                        .requestAttr("userId", 7L)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "baseRevisionUid":"rev_1",
                                  "cardBlocks":{
                                    "schemaVersion":1,
                                    "blocks":[{
                                      "id":"block_note_01","type":"note","title":"我的笔记",
                                      "meaningRefs":[],"format":"markdown","content":"## 重点",
                                      "source":"user","sourceRef":null,"sortOrder":10,
                                      "userEdited":true,"locked":true
                                    }]
                                  },
                                  "changeSummary":"补充笔记"
                                }
                                """))
                .andExpect(status().isOk());

        verify(cardService).update(eq(7L), eq("card_1"), argThat(request ->
                request.cardBlocks() != null
                        && "note".equals(request.cardBlocks().path("blocks").get(0).path("type").asText())));
    }

    @Test
    void regenerateAcceptsValidatedTemplateAndRejectsUnsupportedTemplate() throws Exception {
        mockMvc.perform(post("/api/vocabulary/cards/card_1/regenerate")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"templateKey\":\"exam\"}"))
                .andExpect(status().isOk());
        verify(cardService).regenerate(eq(7L), eq("card_1"), argThat(request ->
                "exam".equals(request.templateKey())
                        && request.themeUid() == null
                        && request.useLatestThemeVersion() == null));

        mockMvc.perform(post("/api/vocabulary/cards/card_1/regenerate")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"templateKey\":\"unsupported\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    @Test
    void returnsConflictSummaryForStaleVocabularyRevision() throws Exception {
        var content = JsonNodeFactory.instance.objectNode().put("term", "innovative");
        when(cardService.update(eq(7L), eq("card_1"), any()))
                .thenThrow(new VocabularyRevisionConflictException(new VocabularyConflictResponse(
                        "rev_current", "rev_candidate", content, content, 1, null, "needs_review")));

        mockMvc.perform(put("/api/vocabulary/cards/card_1")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {"baseRevisionUid":"rev_current","content":{"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":"edited"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409030"))
                .andExpect(jsonPath("$.data.currentRevisionUid").value("rev_current"))
                .andExpect(jsonPath("$.data.candidateRevisionUid").value("rev_candidate"))
                .andExpect(jsonPath("$.data.currentContentFormatVersion").value(1))
                .andExpect(jsonPath("$.data.candidateContentFormatVersion").isEmpty());
    }
}
