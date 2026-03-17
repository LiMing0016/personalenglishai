package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrammarCheckServiceImpl - Trinka pipeline experiment")
class GrammarCheckServiceImplTest {

    @Mock private LanguageToolService languageToolService;
    @Mock private SaplingService saplingService;
    @Mock private TextGearsService textGearsService;
    @Mock private TrinkaService trinkaService;

    @Test
    @DisplayName("grammar-check 默认应以 lite/basic 调用 Trinka")
    void checkShouldUseBasicPipelineForTrinka() {
        GrammarCheckServiceImpl service = new GrammarCheckServiceImpl(
                languageToolService,
                saplingService,
                textGearsService,
                trinkaService
        );
        WritingEvaluateResponse.ErrorDto trinkaError = buildError("trinka", 0, 5);

        when(languageToolService.check(anyString())).thenReturn(List.of());
        when(saplingService.check(anyString())).thenReturn(List.of());
        when(textGearsService.check(anyString())).thenReturn(List.of());
        when(trinkaService.check(anyString(), eq("basic"))).thenReturn(List.of(trinkaError));

        List<WritingEvaluateResponse.ErrorDto> result = service.check("Sample paragraph.");

        verify(trinkaService, times(1)).check("Sample paragraph.", "basic");
        verify(trinkaService, never()).check("Sample paragraph.", "advanced");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEngine()).isEqualTo("trinka");
    }

    @Test
    @DisplayName("grammar-check 传入 power 时应切换到 advanced")
    void checkShouldUseAdvancedPipelineWhenModeIsPower() {
        GrammarCheckServiceImpl service = new GrammarCheckServiceImpl(
                languageToolService,
                saplingService,
                textGearsService,
                trinkaService
        );
        WritingEvaluateResponse.ErrorDto trinkaError = buildError("trinka", 0, 5);
        when(languageToolService.check(anyString())).thenReturn(List.of());
        when(saplingService.check(anyString())).thenReturn(List.of());
        when(textGearsService.check(anyString())).thenReturn(List.of());
        when(trinkaService.check(anyString(), eq("advanced"))).thenReturn(List.of(trinkaError));
        List<WritingEvaluateResponse.ErrorDto> result = service.check("Sample paragraph.", "power");
        verify(trinkaService, times(1)).check("Sample paragraph.", "advanced");
        verify(trinkaService, never()).check("Sample paragraph.", "basic");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEngine()).isEqualTo("trinka");
    }
    @Test
    @DisplayName("Trinka 缓存键应包含 pipeline")
    void trinkaCacheKeyShouldIncludePipeline() {
        GrammarCheckServiceImpl service = new GrammarCheckServiceImpl(
                languageToolService,
                saplingService,
                textGearsService,
                trinkaService
        );
        String text = "Sample paragraph.";

        when(trinkaService.check(text, "basic")).thenReturn(List.of(buildError("trinka", 0, 6)));
        when(trinkaService.check(text, "advanced")).thenReturn(List.of(buildError("trinka", 0, 6)));

        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> basicFirst =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(service, "checkTrinkaWithCache", text, "basic");
        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> basicSecond =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(service, "checkTrinkaWithCache", text, "basic");
        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> advancedFirst =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(service, "checkTrinkaWithCache", text, "advanced");
        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> advancedSecond =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(service, "checkTrinkaWithCache", text, "advanced");

        verify(trinkaService, times(1)).check(text, "basic");
        verify(trinkaService, times(1)).check(text, "advanced");
        assertThat(basicFirst).hasSize(1);
        assertThat(basicSecond).hasSize(1);
        assertThat(advancedFirst).hasSize(1);
        assertThat(advancedSecond).hasSize(1);
    }

    @Test
    @DisplayName("Clarity 顶层分类应降级为 suggestion")
    void checkShouldDowngradeClarityToSuggestion() {
        GrammarCheckServiceImpl service = new GrammarCheckServiceImpl(
                languageToolService,
                saplingService,
                textGearsService,
                trinkaService
        );
        WritingEvaluateResponse.ErrorDto trinkaError = buildTrinkaError("Clarity", 2, "error", "major");

        when(languageToolService.check(anyString())).thenReturn(List.of());
        when(saplingService.check(anyString())).thenReturn(List.of());
        when(textGearsService.check(anyString())).thenReturn(List.of());
        when(trinkaService.check(anyString(), eq("basic"))).thenReturn(List.of(trinkaError));

        List<WritingEvaluateResponse.ErrorDto> result = service.check("Sample paragraph.");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("suggestion");
        assertThat(result.get(0).getSeverity()).isEqualTo("minor");
        assertThat(result.get(0).getRawEngineMeta().getTopCategoryName()).isEqualTo("Clarity");
    }

    @Test
    @DisplayName("Correctness 顶层分类应保留为 error")
    void checkShouldKeepCorrectnessAsError() {
        GrammarCheckServiceImpl service = new GrammarCheckServiceImpl(
                languageToolService,
                saplingService,
                textGearsService,
                trinkaService
        );
        WritingEvaluateResponse.ErrorDto trinkaError = buildTrinkaError("Correctness", 1, "error", "major");

        when(languageToolService.check(anyString())).thenReturn(List.of());
        when(saplingService.check(anyString())).thenReturn(List.of());
        when(textGearsService.check(anyString())).thenReturn(List.of());
        when(trinkaService.check(anyString(), eq("basic"))).thenReturn(List.of(trinkaError));

        List<WritingEvaluateResponse.ErrorDto> result = service.check("Sample paragraph.");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("error");
        assertThat(result.get(0).getSeverity()).isEqualTo("major");
        assertThat(result.get(0).getRawEngineMeta().getTopCategoryName()).isEqualTo("Correctness");
    }
    private WritingEvaluateResponse.ErrorDto buildError(String engine, int start, int end) {
        WritingEvaluateResponse.ErrorDto error = new WritingEvaluateResponse.ErrorDto();
        error.setEngine(engine);
        error.setType("syntax");
        error.setCategory("error");
        error.setSeverity("major");
        error.setOriginal("error");
        error.setSuggestion("fix");
        error.setReason("reason");
        error.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        return error;
    }

    private WritingEvaluateResponse.ErrorDto buildTrinkaError(String topCategoryName, Integer topCategoryId, String category, String severity) {
        WritingEvaluateResponse.ErrorDto error = buildError("trinka", 0, 5);
        error.setCategory(category);
        error.setSeverity(severity);
        WritingEvaluateResponse.RawEngineMetaDto raw = new WritingEvaluateResponse.RawEngineMetaDto();
        raw.setType(1);
        raw.setTopCategoryId(topCategoryId);
        raw.setTopCategoryName(topCategoryName);
        raw.setCriticalError(false);
        raw.setPipeline("basic");
        error.setRawEngineMeta(raw);
        return error;
    }
}



