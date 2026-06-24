package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningCanvasOrganizeServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    @Test
    void createModeAsksModelForVocabularyMarkdown() {
        LearningCanvasOrganizeService service = new LearningCanvasOrganizeService(openAiClient);
        LearningCanvasOrganizeRequest request = new LearningCanvasOrganizeRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setSelectedText("nuanced");
        request.setContextText("A nuanced answer considers different sides.");
        request.setMode("create");

        when(openAiClient.callWithProvider(eq(null), anyString(), anyString(), eq("learning-canvas-organize"), eq(0.2), eq(1200)))
                .thenReturn("# nuanced\n\n**词性：** adjective");

        var response = service.organize(request);

        assertThat(response.getCandidateMarkdown()).startsWith("# nuanced");
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(eq(null), anyString(), userPrompt.capture(), eq("learning-canvas-organize"), eq(0.2), eq(1200));
        assertThat(userPrompt.getValue()).contains("nuanced");
        assertThat(userPrompt.getValue()).contains("默认单词卡模板");
    }

    @Test
    void formatModePreservesUserMarkdownInstruction() {
        LearningCanvasOrganizeService service = new LearningCanvasOrganizeService(openAiClient);
        LearningCanvasOrganizeRequest request = new LearningCanvasOrganizeRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setCurrentMarkdown("# nuanced\nmy own note");
        request.setMode("format");

        when(openAiClient.callWithProvider(eq(null), anyString(), anyString(), eq("learning-canvas-organize"), eq(0.2), eq(1200)))
                .thenReturn("# nuanced\n\n## 我的笔记\nmy own note");

        service.organize(request);

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(eq(null), anyString(), userPrompt.capture(), eq("learning-canvas-organize"), eq(0.2), eq(1200));
        assertThat(userPrompt.getValue()).contains("尽量保留用户原意");
        assertThat(userPrompt.getValue()).contains("my own note");
    }
}
