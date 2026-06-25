package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import com.personalenglishai.backend.service.assistant.PythonAssistantClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningCanvasOrganizeServiceTest {

    @Mock
    private PythonAssistantClient pythonAssistantClient;

    @Test
    void delegatesLearningAssetCopilotToPythonOrchestrator() {
        LearningCanvasOrganizeService service = new LearningCanvasOrganizeService(pythonAssistantClient);
        LearningCanvasOrganizeRequest request = new LearningCanvasOrganizeRequest();
        request.setType("grammar");
        request.setTitle("a window of opportunity");
        request.setSelectedText("a window of opportunity");
        request.setCurrentMarkdown("# a window of opportunity");
        request.setAction("expand");
        request.setInstruction("补充一个自然例句");

        LearningCanvasOrganizeResponse upstream = new LearningCanvasOrganizeResponse();
        upstream.setCandidateMarkdown("# a window of opportunity\n\n**类型：** 语法笔记");
        when(pythonAssistantClient.organizeLearningAsset(request)).thenReturn(upstream);

        LearningCanvasOrganizeResponse response = service.organize(request);

        assertThat(response.getCandidateMarkdown()).contains("语法笔记");
        ArgumentCaptor<LearningCanvasOrganizeRequest> requestCaptor = ArgumentCaptor.forClass(LearningCanvasOrganizeRequest.class);
        verify(pythonAssistantClient).organizeLearningAsset(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAction()).isEqualTo("expand");
        assertThat(requestCaptor.getValue().getInstruction()).isEqualTo("补充一个自然例句");
    }

    @Test
    void serviceDoesNotOwnAgentPromptOrOpenAiCall() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeService.java"));

        assertThat(source).contains("PythonAssistantClient");
        assertThat(source).doesNotContain("OpenAiClient");
        assertThat(source).doesNotContain("callWithProvider");
        assertThat(source).doesNotContain("你是学习资产画布");
        assertThat(source).doesNotContain("默认单词卡模板");
        assertThat(source).doesNotContain("默认语法笔记模板");
    }
}
