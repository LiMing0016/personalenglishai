package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandwritingRecognitionServiceImplTest {

    @Mock
    private OpenAiClient openAiClient;

    @Test
    void recognizeShouldUseCurrentAiProviderAndParseStructuredResponse() {
        HandwritingRecognitionServiceImpl service = new HandwritingRecognitionServiceImpl(
                openAiClient,
                new ObjectMapper()
        );

        when(openAiClient.callVisionWithProvider(eq("openai"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("""
                        {
                          "recognizedText": "Line 1\\nLine 2",
                          "normalizedText": "Line 1\\n\\nLine 2",
                          "confidence": 0.82
                        }
                        """);

        RecognizeHandwritingImageRequest request = new RecognizeHandwritingImageRequest();
        request.setImageBase64("data:image/png;base64,abc");
        request.setAiProvider("openai");

        var response = service.recognize(request);

        assertThat(response.getImageUrl()).isEqualTo("data:image/png;base64,abc");
        assertThat(response.getRecognizedText()).isEqualTo("Line 1\nLine 2");
        assertThat(response.getNormalizedText()).isEqualTo("Line 1\n\nLine 2");
        assertThat(response.getConfidence()).isEqualByComparingTo("0.82");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageDataUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callVisionWithProvider(
                eq("openai"),
                systemPromptCaptor.capture(),
                userPromptCaptor.capture(),
                imageDataUrlCaptor.capture(),
                nullable(String.class)
        );
        assertThat(systemPromptCaptor.getValue())
                .contains("只识别图片中的手写英文作文正文")
                .contains("保留原文自然段和换行")
                .contains("不要输出解释、分析、markdown 或代码块");
        assertThat(userPromptCaptor.getValue())
                .contains("请识别这张图片中的手写英文作文正文")
                .contains("只输出 JSON")
                .doesNotContain("data:image/png;base64,abc");
        assertThat(imageDataUrlCaptor.getValue()).isEqualTo("data:image/png;base64,abc");
    }

    @Test
    void recognizeShouldFailClosedOnMalformedUpstreamOutput() {
        HandwritingRecognitionServiceImpl service = new HandwritingRecognitionServiceImpl(
                openAiClient,
                new ObjectMapper()
        );

        when(openAiClient.callVisionWithProvider(eq("openai"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("not json, just upstream error text");

        RecognizeHandwritingImageRequest request = new RecognizeHandwritingImageRequest();
        request.setImageBase64("data:image/png;base64,abc");
        request.setAiProvider("openai");

        var response = service.recognize(request);

        assertThat(response.getImageUrl()).isEqualTo("data:image/png;base64,abc");
        assertThat(response.getRecognizedText()).isNull();
        assertThat(response.getNormalizedText()).isNull();
        assertThat(response.getConfidence()).isNull();
        verify(openAiClient).callVisionWithProvider(eq("openai"), anyString(), anyString(), anyString(), anyString());
    }
}
