package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.entity.translation.TranslationDocumentAssetRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentElementRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentFileRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentParseSnapshotRecord;
import com.personalenglishai.backend.entity.translation.TranslationKnowledgeChunkRecord;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentFileMapper;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentKnowledgeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentImportPersistenceTest {

    @TempDir
    Path storageRoot;

    @Test
    void pdfImportPersistsOriginalFileAndSavesSnapshotWithStableFileUrl() throws Exception {
        FakeFileMapper fileMapper = new FakeFileMapper();
        TranslationDocumentFileStorage fileStorage = new TranslationDocumentFileStorage(storageRoot, fileMapper);
        FakeKnowledgeMapper knowledgeMapper = new FakeKnowledgeMapper();
        TranslationDocumentKnowledgeStore knowledgeStore = new TranslationDocumentKnowledgeStore(new ObjectMapper(), knowledgeMapper);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(new FixedPdfProvider()));
        TranslationDocumentImportService service = new TranslationDocumentImportService(
                List.of(),
                orchestrator,
                knowledgeStore,
                fileStorage
        );
        byte[] bytes = "%PDF-1.7\nsource".getBytes(StandardCharsets.UTF_8);

        TranslationDocumentParseResponse response = service.importDocument(new UploadedTranslationDocument(
                "source.pdf",
                "application/pdf",
                bytes,
                "immersive",
                DocumentParseMode.STANDARD
        ));

        assertThat(response.getDocumentId()).isEqualTo("stable-doc");
        assertThat(response.getFileUrl()).isEqualTo("/api/translation/documents/stable-doc/file");
        assertThat(response.isFilePersisted()).isTrue();
        assertThat(response.getStorageProvider()).isEqualTo("local");
        assertThat(Files.readAllBytes(storageRoot.resolve(fileMapper.record.getStorageKey()))).isEqualTo(bytes);
        assertThat(knowledgeMapper.snapshot.getResponseJson())
                .contains("\"fileUrl\":\"/api/translation/documents/stable-doc/file\"");
    }

    private static final class FixedPdfProvider implements DocumentParseProvider {
        @Override
        public boolean supports(DocumentParseRequest request) {
            return true;
        }

        @Override
        public DocumentParseProviderType providerType() {
            return DocumentParseProviderType.LOCAL_PDFBOX;
        }

        @Override
        public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
            TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
            response.setDocumentId("stable-doc");
            response.setFileName(request.originalFilename());
            response.setSourceType("PDF");
            response.setParseStatus("SUCCEEDED");
            response.setOcrStatus("NOT_REQUIRED");
            response.setPageCount(1);
            response.setBlocks(List.of(new TranslationDocumentBlockDto(
                    "p1-b1",
                    "paragraph",
                    1,
                    1,
                    "A stable PDF file URL should survive page refresh.",
                    null
            )));
            return response;
        }
    }

    private static final class FakeFileMapper implements TranslationDocumentFileMapper {
        private TranslationDocumentFileRecord record;

        @Override
        public int upsert(TranslationDocumentFileRecord record) {
            this.record = record;
            return 1;
        }

        @Override
        public TranslationDocumentFileRecord findByDocumentId(String documentId) {
            return record != null && record.getDocumentId().equals(documentId) ? record : null;
        }
    }

    private static final class FakeKnowledgeMapper implements TranslationDocumentKnowledgeMapper {
        private TranslationDocumentParseSnapshotRecord snapshot;

        @Override
        public int deleteByDocumentId(String documentId) {
            snapshot = null;
            return 1;
        }

        @Override
        public int insertSnapshot(TranslationDocumentParseSnapshotRecord record) {
            snapshot = record;
            return 1;
        }

        @Override
        public int insertElement(TranslationDocumentElementRecord record) {
            return 1;
        }

        @Override
        public int insertChunk(TranslationKnowledgeChunkRecord record) {
            return 1;
        }

        @Override
        public int insertAsset(TranslationDocumentAssetRecord record) {
            return 1;
        }

        @Override
        public TranslationDocumentParseSnapshotRecord findSnapshotByDocumentId(String documentId) {
            return snapshot;
        }

        @Override
        public List<TranslationKnowledgeChunkRecord> selectChunksByDocumentId(String documentId) {
            return List.of();
        }
    }
}
