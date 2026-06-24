package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.entity.translation.TranslationDocumentFileRecord;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentFileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentFileStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void savesOriginalPdfOutsideDatabaseAndReturnsStableFileUrl() throws Exception {
        FakeFileMapper mapper = new FakeFileMapper();
        TranslationDocumentFileStorage storage = new TranslationDocumentFileStorage(storageRoot, mapper);
        byte[] bytes = "%PDF-1.7\nbook content".getBytes(StandardCharsets.UTF_8);

        StoredTranslationDocumentFile stored = storage.save("doc-001", new UploadedTranslationDocument(
                "Computer Networking.pdf",
                "application/pdf",
                bytes,
                "immersive"
        ));

        assertThat(stored.getDocumentId()).isEqualTo("doc-001");
        assertThat(stored.getFileUrl()).isEqualTo("/api/translation/documents/doc-001/file");
        assertThat(stored.getStorageProvider()).isEqualTo("local");
        assertThat(stored.getFileSize()).isEqualTo(bytes.length);
        assertThat(stored.getSha256()).hasSize(64);
        assertThat(Files.readAllBytes(storageRoot.resolve(stored.getStorageKey()))).isEqualTo(bytes);

        assertThat(mapper.record).isNotNull();
        assertThat(mapper.record.getDocumentId()).isEqualTo("doc-001");
        assertThat(mapper.record.getStorageKey()).isEqualTo(stored.getStorageKey());
        assertThat(mapper.record.getSha256()).isEqualTo(stored.getSha256());
        assertThat(mapper.record.getFileSize()).isEqualTo(bytes.length);
    }

    @Test
    void loadsStoredFileMetadataAndReadableResourceByDocumentId() throws Exception {
        FakeFileMapper mapper = new FakeFileMapper();
        TranslationDocumentFileStorage storage = new TranslationDocumentFileStorage(storageRoot, mapper);
        byte[] bytes = "%PDF-1.7\nbook content".getBytes(StandardCharsets.UTF_8);
        storage.save("doc-002", new UploadedTranslationDocument(
                "book.pdf",
                "application/pdf",
                bytes,
                "immersive"
        ));

        StoredTranslationDocumentFile stored = storage.findByDocumentId("doc-002").orElseThrow();

        assertThat(stored.getFileName()).isEqualTo("book.pdf");
        assertThat(stored.getContentType()).isEqualTo("application/pdf");
        assertThat(stored.getFileUrl()).isEqualTo("/api/translation/documents/doc-002/file");
        assertThat(Files.readAllBytes(stored.getPath())).isEqualTo(bytes);
    }

    @Test
    void sanitizesUploadedFileNameBeforeBuildingStoragePath() {
        FakeFileMapper mapper = new FakeFileMapper();
        TranslationDocumentFileStorage storage = new TranslationDocumentFileStorage(storageRoot, mapper);

        StoredTranslationDocumentFile stored = storage.save("doc-003", new UploadedTranslationDocument(
                "C:\\Users\\Catalina\\Downloads\\book:chapter?.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                "immersive"
        ));

        assertThat(stored.getFileName()).isEqualTo("book_chapter_.pdf");
        assertThat(stored.getStorageKey()).startsWith("doc-003/");
        assertThat(stored.getPath()).startsWith(storageRoot.toAbsolutePath().normalize());
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
}
