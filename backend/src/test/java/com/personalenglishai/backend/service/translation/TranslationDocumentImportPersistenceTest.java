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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(new FixedPdfProvider()), true, false);
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

    @Test
    void pdfImportParsesFirstTenPagesThenContinuesInBackground() throws Exception {
        FakeFileMapper fileMapper = new FakeFileMapper();
        TranslationDocumentFileStorage fileStorage = new TranslationDocumentFileStorage(storageRoot, fileMapper);
        FakeKnowledgeMapper knowledgeMapper = new FakeKnowledgeMapper();
        TranslationDocumentKnowledgeStore knowledgeStore = new TranslationDocumentKnowledgeStore(new ObjectMapper(), knowledgeMapper);
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        List<Runnable> backgroundTasks = new ArrayList<>();
        TranslationDocumentImportService service = new TranslationDocumentImportService(
                List.of(),
                orchestrator,
                knowledgeStore,
                fileStorage,
                backgroundTasks::add,
                10,
                10
        );
        byte[] bytes = createPdfWithPages(12);

        TranslationDocumentParseResponse response = service.importDocument(new UploadedTranslationDocument(
                "book.pdf",
                "application/pdf",
                bytes,
                "immersive",
                DocumentParseMode.STANDARD,
                DocumentParseProviderPreference.PADDLE_OCR
        ));

        assertThat(orchestrator.requests).hasSize(1);
        assertThat(orchestrator.requests.get(0).pageStart()).isEqualTo(1);
        assertThat(orchestrator.requests.get(0).pageEnd()).isNull();
        assertThat(orchestrator.requests.get(0).maxPages()).isEqualTo(10);
        assertThat(response.getPageCount()).isEqualTo(12);
        assertThat(response.getOcrStatus()).isEqualTo("PARTIAL");
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("前 10 页"));
        assertThat(backgroundTasks).hasSize(1);

        backgroundTasks.get(0).run();

        assertThat(orchestrator.requests).hasSize(2);
        assertThat(orchestrator.requests.get(1).pageStart()).isEqualTo(11);
        assertThat(orchestrator.requests.get(1).pageEnd()).isEqualTo(12);
        assertThat(orchestrator.requests.get(1).maxPages()).isEqualTo(10);
        assertThat(knowledgeMapper.snapshot.getResponseJson())
                .contains("\"ocrStatus\":\"SUCCEEDED\"")
                .contains("page 11")
                .contains("page 12");
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

    private static final class CapturingOrchestrator extends DocumentParseOrchestrator {
        private final List<DocumentParseRequest> requests = new ArrayList<>();

        private CapturingOrchestrator() {
            super(List.of());
        }

        @Override
        public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
            requests.add(request);
            int pageStart = request.pageStart() == null ? 1 : request.pageStart();
            int pageEnd = request.pageEnd() == null
                    ? Math.min(10, pageStart + Math.max(1, request.maxPages() == null ? 10 : request.maxPages()) - 1)
                    : request.pageEnd();
            TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
            response.setDocumentId("stable-doc");
            response.setFileName(request.originalFilename());
            response.setSourceType("PDF");
            response.setParseStatus("SUCCEEDED");
            response.setOcrStatus("SUCCEEDED");
            response.setPageCount(Math.max(pageEnd, 1));
            List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
            for (int page = pageStart; page <= pageEnd; page++) {
                blocks.add(new TranslationDocumentBlockDto(
                        "p" + page + "-b1",
                        "paragraph",
                        page - pageStart + 1,
                        page,
                        "OCR text from page " + page,
                        null
                ));
            }
            response.setBlocks(blocks);
            return response;
        }
    }

    private static byte[] createPdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
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
