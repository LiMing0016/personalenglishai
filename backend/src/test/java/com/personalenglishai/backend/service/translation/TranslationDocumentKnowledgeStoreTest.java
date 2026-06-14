package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationKnowledgeChunkDto;
import com.personalenglishai.backend.entity.translation.TranslationDocumentAssetRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentElementRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentParseSnapshotRecord;
import com.personalenglishai.backend.entity.translation.TranslationKnowledgeChunkRecord;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentKnowledgeMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentKnowledgeStoreTest {

    @Test
    void savesKnowledgeSnapshotRowsAndRestoresResponseByDocumentId() {
        FakeKnowledgeMapper mapper = new FakeKnowledgeMapper();
        TranslationDocumentKnowledgeStore store = new TranslationDocumentKnowledgeStore(new ObjectMapper(), mapper);
        TranslationDocumentParseResponse response = response();

        store.save(response);

        assertThat(mapper.deletedDocumentIds).containsExactly("translation-001");
        assertThat(mapper.snapshot).isNotNull();
        assertThat(mapper.snapshot.getDocumentId()).isEqualTo("translation-001");
        assertThat(mapper.snapshot.getResponseJson()).contains("\"documentId\":\"translation-001\"");
        assertThat(mapper.snapshot.getDiagnosisJson()).contains("textCoverageRatio");
        assertThat(mapper.snapshot.getQualityJson()).contains("documentQualityScore");
        assertThat(mapper.snapshot.getLanguageProfileJson()).contains("primaryLanguage");
        assertThat(mapper.snapshot.getParseJobJson()).contains("documentId");
        assertThat(mapper.elements).hasSize(1);
        assertThat(mapper.chunks).hasSize(1);
        assertThat(mapper.assets).hasSize(1);
        assertThat(mapper.chunks.get(0).getSourceElementIdsJson()).contains("p1");

        Optional<TranslationDocumentParseResponse> restored = store.findByDocumentId("translation-001");

        assertThat(restored).isPresent();
        assertThat(restored.get().getDocumentId()).isEqualTo("translation-001");
        assertThat(restored.get().getElements()).hasSize(1);
        assertThat(restored.get().getKnowledgeChunks()).hasSize(1);
        assertThat(restored.get().getAssets()).hasSize(1);
    }

    @Test
    void exposesReusableChunksForAgentContext() {
        FakeKnowledgeMapper mapper = new FakeKnowledgeMapper();
        TranslationDocumentKnowledgeStore store = new TranslationDocumentKnowledgeStore(new ObjectMapper(), mapper);
        store.save(response());

        List<TranslationKnowledgeChunkRecord> chunks = store.findChunksByDocumentId("translation-001");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkId()).isEqualTo("translation-001-c1");
        assertThat(chunks.get(0).getContent()).contains("source document");
    }

    @Test
    void replacesPreviousSnapshotRowsWhenSavingSameDocumentIdAgain() {
        FakeKnowledgeMapper mapper = new FakeKnowledgeMapper();
        TranslationDocumentKnowledgeStore store = new TranslationDocumentKnowledgeStore(new ObjectMapper(), mapper);
        TranslationDocumentParseResponse first = response();
        TranslationDocumentParseResponse second = response();
        second.setFileName("updated-reading.md");
        second.setBlocks(List.of());
        second.setElements(List.of());
        second.setKnowledgeChunks(List.of());
        second.setAssets(List.of());

        store.save(first);
        store.save(second);

        assertThat(mapper.deletedDocumentIds).containsExactly("translation-001", "translation-001");
        assertThat(mapper.snapshot.getFileName()).isEqualTo("updated-reading.md");
        assertThat(mapper.elements).isEmpty();
        assertThat(mapper.chunks).isEmpty();
        assertThat(mapper.assets).isEmpty();
    }

    private static TranslationDocumentParseResponse response() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("translation-001");
        response.setFileName("reading.md");
        response.setSourceType("MD");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setProvider("markdown");
        response.setParseMode("standard");

        TranslationDocumentElementDto element = new TranslationDocumentElementDto();
        element.setId("p1");
        element.setType("paragraph");
        element.setOrder(1);
        element.setPageNumber(1);
        element.setText("AI answers should stay grounded in the source document.");
        element.setProvider("markdown");
        element.setRecognitionStatus("READY");
        element.setQualityScore(0.92);
        response.setElements(List.of(element));

        TranslationKnowledgeChunkDto chunk = new TranslationKnowledgeChunkDto();
        chunk.setId("translation-001-c1");
        chunk.setChunkOrder(1);
        chunk.setChunkType("paragraph");
        chunk.setContent(element.getText());
        chunk.setSummary("grounded source document");
        chunk.setSourceElementIds(List.of("p1"));
        chunk.setPageNumbers(List.of(1));
        chunk.setTokenCount(9);
        chunk.setQualityScore(0.92);
        chunk.setSectionPath(List.of("Chapter 1"));
        chunk.setStartElementOrder(1);
        chunk.setEndElementOrder(1);
        response.setKnowledgeChunks(List.of(chunk));

        TranslationDocumentAssetDto asset = new TranslationDocumentAssetDto();
        asset.setId("translation-001-a1");
        asset.setAssetType("page_snapshot");
        asset.setPageNumber(1);
        asset.setRecognitionStatus("READY");
        response.setAssets(List.of(asset));

        return response;
    }

    private static final class FakeKnowledgeMapper implements TranslationDocumentKnowledgeMapper {
        private final List<String> deletedDocumentIds = new ArrayList<>();
        private TranslationDocumentParseSnapshotRecord snapshot;
        private final List<TranslationDocumentElementRecord> elements = new ArrayList<>();
        private final List<TranslationKnowledgeChunkRecord> chunks = new ArrayList<>();
        private final List<TranslationDocumentAssetRecord> assets = new ArrayList<>();

        @Override
        public int deleteByDocumentId(String documentId) {
            deletedDocumentIds.add(documentId);
            snapshot = null;
            elements.clear();
            chunks.clear();
            assets.clear();
            return 1;
        }

        @Override
        public int insertSnapshot(TranslationDocumentParseSnapshotRecord record) {
            snapshot = record;
            return 1;
        }

        @Override
        public int insertElement(TranslationDocumentElementRecord record) {
            elements.add(record);
            return 1;
        }

        @Override
        public int insertChunk(TranslationKnowledgeChunkRecord record) {
            chunks.add(record);
            return 1;
        }

        @Override
        public int insertAsset(TranslationDocumentAssetRecord record) {
            assets.add(record);
            return 1;
        }

        @Override
        public TranslationDocumentParseSnapshotRecord findSnapshotByDocumentId(String documentId) {
            return snapshot != null && snapshot.getDocumentId().equals(documentId) ? snapshot : null;
        }

        @Override
        public List<TranslationKnowledgeChunkRecord> selectChunksByDocumentId(String documentId) {
            return chunks.stream().filter(chunk -> chunk.getDocumentId().equals(documentId)).toList();
        }
    }
}
