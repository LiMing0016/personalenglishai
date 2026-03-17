package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.RewriteApplyRequest;
import com.personalenglishai.backend.dto.writing.RewriteApplyResponse;
import com.personalenglishai.backend.dto.writing.TrustedRewriteSegmentDto;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrustedRewriteServiceImpl")
class TrustedRewriteServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private GrammarCheckService grammarCheckService;

    private TrustedRewriteServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new TrustedRewriteServiceImpl(redisTemplate, objectMapper, grammarCheckService);
    }

    @Test
    @DisplayName("advanced 替换且 Lite 无硬错误时应登记 trusted rewrite")
    void shouldTrustAdvancedRewriteWhenLiteHasNoHardErrors() {
        RewriteApplyRequest request = new RewriteApplyRequest();
        request.setDocId("doc-1");
        request.setEssay("The old sentence. Another sentence.");
        request.setStart(0);
        request.setEnd("The old sentence.".length());
        request.setOriginal("The old sentence.");
        request.setReplacement("The polished sentence.");
        request.setTier("advanced");

        when(valueOperations.get("rewrite:trusted:7:doc-1")).thenReturn(null);
        when(grammarCheckService.check("The polished sentence.", "lite")).thenReturn(List.of());

        RewriteApplyResponse response = service.applyTrustedRewrite(7L, request);

        assertThat(response.isTrusted()).isTrue();
        assertThat(response.getHardErrorCount()).isZero();
        assertThat(response.getRecord()).isNotNull();
        assertThat(response.getRecord().getTier()).isEqualTo("advanced");
        verify(valueOperations).set(eq("rewrite:trusted:7:doc-1"), anyString(), any());
    }

    @Test
    @DisplayName("basic 替换不应登记 trusted rewrite")
    void shouldIgnoreBasicRewrite() {
        RewriteApplyRequest request = new RewriteApplyRequest();
        request.setDocId("doc-1");
        request.setEssay("The old sentence.");
        request.setStart(0);
        request.setEnd("The old sentence.".length());
        request.setOriginal("The old sentence.");
        request.setReplacement("The polished sentence.");
        request.setTier("basic");

        RewriteApplyResponse response = service.applyTrustedRewrite(7L, request);

        assertThat(response.isTrusted()).isFalse();
        verify(grammarCheckService, never()).check(anyString(), anyString());
    }

    @Test
    @DisplayName("trusted rewrite 只应 suppress Trinka suggestion，不 suppress 硬错误")
    void shouldSuppressOnlyTrustedTrinkaSuggestions() throws Exception {
        TrustedRewriteSegmentDto record = new TrustedRewriteSegmentDto();
        record.setDocId("doc-1");
        record.setSentenceText("The polished sentence.");
        record.setNormalizedTextHash(hashNormalized("The polished sentence."));
        record.setLeftContext("");
        record.setRightContext(" Another sentence.");
        record.setTier("perfect");
        record.setSource("rewrite");
        record.setUpdatedAt(System.currentTimeMillis());

        when(valueOperations.get("rewrite:trusted:7:doc-1"))
                .thenReturn(objectMapper.writeValueAsString(List.of(record)));

        String text = "The polished sentence. Another sentence.";
        int start = text.indexOf("polished");
        int end = start + "polished".length();

        WritingEvaluateResponse.ErrorDto suggestion = buildError("trinka", "suggestion", start, end);
        WritingEvaluateResponse.ErrorDto error = buildError("trinka", "error", start, end);
        WritingEvaluateResponse.ErrorDto otherEngineSuggestion = buildError("lt", "suggestion", start, end);

        List<WritingEvaluateResponse.ErrorDto> filtered = service.filterTrustedTrinkaSuggestions(
                7L,
                "doc-1",
                text,
                List.of(suggestion, error, otherEngineSuggestion)
        );

        assertThat(filtered).containsExactly(error, otherEngineSuggestion);
    }

    private WritingEvaluateResponse.ErrorDto buildError(String engine, String category, int start, int end) {
        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId(engine + "-" + category);
        dto.setEngine(engine);
        dto.setCategory(category);
        dto.setType("syntax");
        dto.setSeverity("minor");
        dto.setOriginal("polished");
        dto.setSuggestion("better");
        dto.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        return dto;
    }

    private String hashNormalized(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(text.trim().replaceAll("\\s+", " ").toLowerCase().getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
