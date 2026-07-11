package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardDetailResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardSummaryResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyConflictResponse;
import com.personalenglishai.backend.service.vocabulary.VocabularyRevisionConflictException;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.vocabulary.VocabularyCaptureService;
import com.personalenglishai.backend.service.vocabulary.VocabularyCardService;
import com.personalenglishai.backend.service.vocabulary.VocabularyTemplateRegistry;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VocabularyController.class)
@AutoConfigureMockMvc(addFilters = false)
class VocabularyControllerTest {
    @Resource MockMvc mockMvc;
    @MockBean VocabularyCaptureService captureService;
    @MockBean VocabularyCardService cardService;
    @MockBean VocabularyTemplateRegistry templateRegistry;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtInterceptor jwtInterceptor;

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
    void regenerateAcceptsValidatedTemplateAndRejectsUnsupportedTemplate() throws Exception {
        mockMvc.perform(post("/api/vocabulary/cards/card_1/regenerate")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"templateKey\":\"exam\"}"))
                .andExpect(status().isOk());
        verify(cardService).regenerate(7L, "card_1", "exam");

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
                        "rev_current", "rev_candidate", content, content, "needs_review")));

        mockMvc.perform(put("/api/vocabulary/cards/card_1")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("""
                                {"baseRevisionUid":"rev_current","content":{"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":"edited"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409030"))
                .andExpect(jsonPath("$.data.currentRevisionUid").value("rev_current"))
                .andExpect(jsonPath("$.data.candidateRevisionUid").value("rev_candidate"));
    }
}
