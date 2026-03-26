package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.service.document.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultScorePromptContextResolverTest {

    @Mock
    private DocumentService documentService;

    @Test
    @DisplayName("docId 存在时应优先使用文档元数据")
    void shouldPreferDocumentMetadataWhenDocIdExists() {
        DefaultScorePromptContextResolver resolver = new DefaultScorePromptContextResolver(documentService);

        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setUserId(7L);
        request.setDocumentId("doc_abc");
        request.setMode("free");
        request.setStudyStage("highschool");
        request.setTaskType("task1");
        request.setTaskPrompt("request prompt");
        request.setTopicTitle("request title");
        request.setMinWords(80);
        request.setRecommendedMaxWords(120);
        request.setMaxScore(15);

        WritingSessionMetadataResponse metadata = new WritingSessionMetadataResponse();
        metadata.setDocumentId("doc_abc");
        metadata.setMode("exam");
        metadata.setStudyStage("postgrad");
        metadata.setPromptText("metadata prompt");
        metadata.setTopicTitle("metadata title");
        metadata.setTaskType("task2");
        metadata.setMinWords(160);
        metadata.setRecommendedMaxWords(200);
        metadata.setMaxScore(25);

        when(documentService.getSessionMetadataByDocId("7", "default", "doc_abc", 7L))
                .thenReturn(metadata);

        ScorePromptContext context = resolver.resolve(request, "gpt-4o", "score-v1", "postgrad-exam-v1", "rubric-hash");

        assertThat(context.docId()).isEqualTo("doc_abc");
        assertThat(context.mode()).isEqualTo("exam");
        assertThat(context.studyStage()).isEqualTo("postgrad");
        assertThat(context.taskType()).isEqualTo("task2");
        assertThat(context.taskPrompt()).isEqualTo("metadata prompt");
        assertThat(context.topicTitle()).isEqualTo("metadata title");
        assertThat(context.minWords()).isEqualTo(160);
        assertThat(context.recommendedMaxWords()).isEqualTo(200);
        assertThat(context.maxScore()).isEqualTo(25);
    }
}
