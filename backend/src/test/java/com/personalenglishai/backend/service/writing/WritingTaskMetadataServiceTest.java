package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.entity.WritingTaskMetadata;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import com.personalenglishai.backend.mapper.WritingTaskMetadataMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingTaskMetadataServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private WritingMetadataMapper writingMetadataMapper;

    @Mock
    private WritingTaskMetadataMapper writingTaskMetadataMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WritingTaskMetadataService writingTaskMetadataService;

    @Test
    @DisplayName("ensureForDocument returns existing metadata when already generated")
    void ensureForDocument_returnsExisting() {
        Document doc = document(10L, 1L);
        WritingTaskMetadata existing = new WritingTaskMetadata();
        existing.setDocumentId(10L);
        existing.setCentralTask("已有中心任务");
        existing.setMetadataVersion("writing-task-metadata@test-v1");

        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc-1", "1", "default")).thenReturn(doc);
        when(writingTaskMetadataMapper.selectByDocumentId(10L)).thenReturn(existing);

        WritingTaskMetadata result = writingTaskMetadataService.ensureForDocument("1", "default", "doc-1", 1L);

        assertThat(result.getCentralTask()).isEqualTo("已有中心任务");
        verify(writingTaskMetadataMapper, never()).insert(any(WritingTaskMetadata.class));
    }

    @Test
    @DisplayName("ensureForDocument generates and stores task metadata from document and writing metadata")
    void ensureForDocument_generatesAndStores() {
        Document doc = document(10L, 1L);
        doc.setTaskPrompt("Topic: Is Online Learning Better Than Traditional Learning?");
        doc.setTitle("考试写作");

        WritingMetadata writingMetadata = new WritingMetadata();
        writingMetadata.setDocumentId(10L);
        writingMetadata.setMode("exam");
        writingMetadata.setStudyStage("ielts");
        writingMetadata.setGenre("argumentative");
        writingMetadata.setPromptText("根据题目讨论 online learning 是否优于 traditional learning。");

        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc-1", "1", "default")).thenReturn(doc);
        when(writingTaskMetadataMapper.selectByDocumentId(10L)).thenReturn(null);
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(writingMetadata);

        WritingTaskMetadata result = writingTaskMetadataService.ensureForDocument("1", "default", "doc-1", 1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getDocumentId()).isEqualTo(10L);
        assertThat(result.getStudyStage()).isEqualTo("ielts");
        assertThat(result.getTaskType()).isEqualTo("argumentative");
        assertThat(result.getCentralTask()).contains("online learning");
        assertThat(result.getCentralTask()).contains("traditional learning");
        assertThat(result.getMustAnswerPointsJson()).contains("明确回答题目核心问题");
        assertThat(result.getWritingFocusJson()).contains("观点");
        assertThat(result.getRiskPointsJson()).contains("偏题");
        assertThat(result.getRecommendedStructureJson()).contains("intro");
        assertThat(result.getRubricFocusJson()).contains("task_response");
        assertThat(result.getMetadataVersion()).isEqualTo("writing-task-metadata@test-v1");
        assertThat(result.getRubricSource()).isEqualTo("writing-coach-stage-policy@test-v1");

        ArgumentCaptor<WritingTaskMetadata> captor = ArgumentCaptor.forClass(WritingTaskMetadata.class);
        verify(writingTaskMetadataMapper).insert(captor.capture());
        assertThat(captor.getValue().getPromptText()).contains("online learning");
    }

    @Test
    @DisplayName("ensureForDocument rejects non-owner")
    void ensureForDocument_rejectsNonOwner() {
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc-1", "1", "default"))
                .thenReturn(document(10L, 2L));

        assertThatThrownBy(() -> writingTaskMetadataService.ensureForDocument("1", "default", "doc-1", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);
    }

    private Document document(Long id, Long ownerUserId) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerUserId(ownerUserId);
        doc.setPublicId("doc-1");
        doc.setTitle("作文");
        return doc;
    }
}
