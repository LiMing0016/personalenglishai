package com.personalenglishai.backend.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.writing.WritingDocumentAssetSnapshot;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.writing.WritingDocumentAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingDocumentAssetServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private WritingMetadataMapper writingMetadataMapper;

    @Mock
    private EssayEvaluationMapper essayEvaluationMapper;

    @Mock
    private AssistantConversationMapper assistantConversationMapper;

    @Mock
    private AssistantMessageMapper assistantMessageMapper;

    @Mock
    private WritingDocumentAssetMapper writingDocumentAssetMapper;

    @Mock
    private OpenAiClient openAiClient;

    private WritingDocumentAssetService service;

    @BeforeEach
    void setUp() {
        service = new WritingDocumentAssetService(
                documentMapper,
                writingMetadataMapper,
                essayEvaluationMapper,
                assistantConversationMapper,
                assistantMessageMapper,
                writingDocumentAssetMapper,
                openAiClient,
                new ObjectMapper());
    }

    @Test
    @DisplayName("refreshLearningAssetPreview extracts user-centered learning assets and exposes them in asset detail")
    void refreshLearningAssetPreview_extractsAndReturnsLearningAssets() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);

        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(10L);
        revision.setRevision(3);
        revision.setContent("Nowadays, online learning become more and more popular.");
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 3)).thenReturn(revision);
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(null);
        when(essayEvaluationMapper.selectByDocumentId(10L, 0, 50)).thenReturn(List.of());
        when(essayEvaluationMapper.countByDocumentId(10L)).thenReturn(0L);
        when(writingDocumentAssetMapper.selectLinkedConversations(1L, 10L)).thenReturn(List.of());
        when(writingDocumentAssetMapper.findSnapshot(1L, 10L)).thenReturn(null);

        when(openAiClient.createTextResponse(any())).thenReturn(new OpenAiResponsesTextResult(
                "resp-learning",
                """
                {"summary":"用户主要在问表达升级和语法修正。","items":[
                  {"assetType":"word","sourceType":"user_focus","displayText":"nowadays","originalText":"Nowadays","recommendedText":"Nowadays","meaningZh":"如今","explanation":"作文开头常用时间背景词。","valueReasonForUser":"用户开头句中实际使用了这个词，适合固定为开头表达。","howToReuse":"用于引出当下社会现象。","reviewPrompt":"用 nowadays 写一个社会现象开头。","sourceQuestion":"如何改开头句","sourceExcerpt":"Nowadays, online learning become...","confidence":0.91,"learningValueScore":88},
                  {"assetType":"sentence","sourceType":"user_focus","displayText":"Online learning has become increasingly popular.","originalText":"online learning become more and more popular","recommendedText":"Online learning has become increasingly popular.","meaningZh":"在线学习变得越来越流行。","explanation":"has become increasingly popular 比 become more and more popular 更自然。","valueReasonForUser":"这是用户原句的直接升级版，能解决主谓一致和表达自然度问题。","howToReuse":"替换作文开头中描述趋势的句子。","reviewPrompt":"把 become more and more popular 改成更自然的表达。","sourceQuestion":"如何改开头句","sourceExcerpt":"online learning become more and more popular","confidence":0.96,"learningValueScore":95}
                ]}
                """,
                100,
                0,
                80,
                null,
                180,
                900));

        var response = service.refreshLearningAssetPreview("1", "default", "doc_asset", 1L);

        assertThat(response.getLearningAssetPreview().getStatus()).isEqualTo("completed");
        assertThat(response.getLearningAssetPreview().getSummary()).contains("表达升级");
        assertThat(response.getLearningAssetPreview().getItems()).hasSize(2);
        assertThat(response.getLearningAssetPreview().getItems().get(0).getAssetType()).isEqualTo("word");
        assertThat(response.getLearningAssetPreview().getItems().get(1).getRecommendedText())
                .isEqualTo("Online learning has become increasingly popular.");
        verify(writingDocumentAssetMapper).insertLearningAssetPreviewRun(any());
        verify(writingDocumentAssetMapper).replaceLearningAssetPreviewItems(any(), any());
    }

    @Test
    @DisplayName("refreshSnapshot writes markdown and json with essay, evaluation and coach conversation")
    void refreshSnapshot_writesCompleteAsset() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);

        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(10L);
        revision.setRevision(3);
        revision.setContent("Online learning draft");
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 3)).thenReturn(revision);

        WritingMetadata metadata = new WritingMetadata();
        metadata.setMode("exam");
        metadata.setStudyStage("postgrad");
        metadata.setTopicTitle("Online Learning");
        metadata.setPromptText("Is online learning better than traditional learning?");
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(metadata);

        EssayEvaluation evaluation = new EssayEvaluation();
        evaluation.setId(99L);
        evaluation.setOverallScore(88);
        evaluation.setBand("A");
        evaluation.setStructureScore(18);
        evaluation.setGrammarErrorCount(2);
        evaluation.setResultJson("{\"summary\":\"good\"}");
        evaluation.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 0));
        when(essayEvaluationMapper.selectByDocumentId(10L, 0, 50)).thenReturn(List.of(evaluation));
        when(essayEvaluationMapper.countByDocumentId(10L)).thenReturn(1L);

        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-1");
        conversation.setTitle("写作教练：考试写作");
        when(writingDocumentAssetMapper.selectLinkedConversations(1L, 10L)).thenReturn(List.of(conversation));

        AssistantMessage userMessage = new AssistantMessage();
        userMessage.setRole("user");
        userMessage.setContent("[写作教练 Copilot 请求]\n\n[用户本轮问题]\nHow can I improve conclusion?");
        userMessage.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 5));
        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("教练建议：结尾需要回扣观点。");
        assistantMessage.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 6));
        when(assistantMessageMapper.selectByConversationUid("conv-1")).thenReturn(List.of(userMessage, assistantMessage));

        service.refreshSnapshot("1", "default", "doc_asset", 1L);

        ArgumentCaptor<WritingDocumentAssetSnapshot> snapshotCaptor = ArgumentCaptor.forClass(WritingDocumentAssetSnapshot.class);
        verify(writingDocumentAssetMapper).upsertSnapshot(snapshotCaptor.capture());
        WritingDocumentAssetSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getDocumentId()).isEqualTo(10L);
        assertThat(snapshot.getUserId()).isEqualTo(1L);
        assertThat(snapshot.getLatestRevision()).isEqualTo(3);
        assertThat(snapshot.getEvaluationCount()).isEqualTo(1);
        assertThat(snapshot.getCoachMessageCount()).isEqualTo(2);
        assertThat(snapshot.getMarkdownContent()).contains("# My Doc");
        assertThat(snapshot.getMarkdownContent()).contains("Online learning draft");
        assertThat(snapshot.getMarkdownContent()).contains("88");
        assertThat(snapshot.getMarkdownContent()).contains("How can I improve conclusion?");
        assertThat(snapshot.getMarkdownContent()).contains("教练建议：结尾需要回扣观点。");
        assertThat(snapshot.getSnapshotJson()).contains("[写作教练 Copilot 请求]");
    }

    @Test
    @DisplayName("linkCoachConversation rejects conversation that is not owned by current user")
    void linkCoachConversation_rejectsForeignConversation() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);
        when(assistantConversationMapper.findOwnedActiveByUid(1L, "conv-foreign")).thenReturn(null);

        assertThatThrownBy(() -> service.linkCoachConversation("1", "default", "doc_asset", 1L, "conv-foreign"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);
    }

    @Test
    @DisplayName("linkCoachConversation stores owned conversation link idempotently")
    void linkCoachConversation_success() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);
        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-1");
        when(assistantConversationMapper.findOwnedActiveByUid(1L, "conv-1")).thenReturn(conversation);

        service.linkCoachConversation("1", "default", "doc_asset", 1L, "conv-1");

        verify(writingDocumentAssetMapper).upsertConversationLink(1L, 10L, "conv-1");
    }

    @Test
    @DisplayName("getCoachConversationMarkdown exports only linked coach conversation")
    void getCoachConversationMarkdown_exportsLinkedConversation() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 3)).thenReturn(null);
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(null);
        when(essayEvaluationMapper.selectByDocumentId(10L, 0, 50)).thenReturn(List.of());
        when(essayEvaluationMapper.countByDocumentId(10L)).thenReturn(0L);

        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-1");
        conversation.setTitle("写作教练：结尾修改");
        conversation.setUpdatedAt(LocalDateTime.of(2026, 5, 23, 11, 0));
        when(writingDocumentAssetMapper.selectLinkedConversations(1L, 10L)).thenReturn(List.of(conversation));

        AssistantMessage userMessage = new AssistantMessage();
        userMessage.setRole("user");
        userMessage.setContent("[写作教练 Copilot 请求]\n\n[用户本轮问题]\nHow can I improve conclusion?");
        userMessage.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 5));
        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("教练建议：结尾需要回扣观点。");
        assistantMessage.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 6));
        when(assistantMessageMapper.selectByConversationUid("conv-1")).thenReturn(List.of(userMessage, assistantMessage));

        String markdown = service.getCoachConversationMarkdown("1", "default", "doc_asset", 1L, "conv-1");

        assertThat(markdown).contains("# 写作教练对话 - My Doc");
        assertThat(markdown).contains("写作教练：结尾修改");
        assertThat(markdown).contains("How can I improve conclusion?");
        assertThat(markdown).contains("教练建议：结尾需要回扣观点。");
        assertThat(markdown).doesNotContain("[写作教练 Copilot 请求]");
    }

    @Test
    @DisplayName("getAsset recovers historical writing coach conversations when link is missing")
    void getAsset_recoversHistoricalCoachConversationWhenLinkMissing() {
        Document doc = buildDoc();
        doc.setTitle("考研英语一小作文 2025");
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);

        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(10L);
        revision.setRevision(3);
        revision.setContent("Online learning draft");
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 3)).thenReturn(revision);
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(null);
        when(essayEvaluationMapper.selectByDocumentId(10L, 0, 50)).thenReturn(List.of());
        when(essayEvaluationMapper.countByDocumentId(10L)).thenReturn(0L);
        when(writingDocumentAssetMapper.findSnapshot(1L, 10L)).thenReturn(null);
        when(writingDocumentAssetMapper.selectLinkedConversations(1L, 10L)).thenReturn(List.of());

        AssistantConversation recovered = new AssistantConversation();
        recovered.setConversationUid("conv-recovered");
        recovered.setTitle("写作教练：考试写作");
        when(writingDocumentAssetMapper.selectRecoverableCoachConversations(1L, List.of(
                "考研英语一小作文 2025",
                "Is online learning better than traditional learning?")))
                .thenReturn(List.of(recovered));

        AssistantMessage userMessage = new AssistantMessage();
        userMessage.setRole("user");
        userMessage.setContent("[写作教练 Copilot 请求]\n\n[作文题目]\n考研英语一小作文 2025\n\n[用户本轮问题]\n帮我扩写");
        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("可以先补充原因和例子。");
        when(assistantMessageMapper.selectByConversationUid("conv-recovered")).thenReturn(List.of(userMessage, assistantMessage));

        var asset = service.getAsset("1", "default", "doc_asset", 1L);

        assertThat(asset.getCoachConversations()).hasSize(1);
        assertThat(asset.getCoachConversations().get(0).getId()).isEqualTo("conv-recovered");
        assertThat(asset.getCoachConversations().get(0).getMessageCount()).isEqualTo(2);
        verify(writingDocumentAssetMapper).upsertConversationLink(1L, 10L, "conv-recovered");
    }

    @Test
    @DisplayName("getCoachConversationMarkdown rejects conversation not linked to document")
    void getCoachConversationMarkdown_rejectsUnlinkedConversation() {
        Document doc = buildDoc();
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_asset", "1", "default")).thenReturn(doc);
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 3)).thenReturn(null);
        when(writingMetadataMapper.selectByDocumentId(10L)).thenReturn(null);
        when(essayEvaluationMapper.selectByDocumentId(10L, 0, 50)).thenReturn(List.of());
        when(essayEvaluationMapper.countByDocumentId(10L)).thenReturn(0L);
        when(writingDocumentAssetMapper.selectLinkedConversations(1L, 10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getCoachConversationMarkdown("1", "default", "doc_asset", 1L, "conv-foreign"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);
    }

    private Document buildDoc() {
        Document doc = new Document();
        doc.setId(10L);
        doc.setPublicId("doc_asset");
        doc.setOwnerUserId(1L);
        doc.setTitle("My Doc");
        doc.setTaskPrompt("Is online learning better than traditional learning?");
        doc.setLatestRevision(3);
        doc.setLatestScore(88);
        doc.setSubmitCount(1);
        doc.setStatus(2);
        doc.setUpdatedAt(LocalDateTime.of(2026, 5, 23, 10, 10));
        return doc;
    }
}
