package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentStudyNoteDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentUserBookmarkDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentWorkspaceStateDto;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentFileMapper;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentKnowledgeMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentBookmarkExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsPdfWithOriginalOutlineUserBookmarksAndNotes() throws Exception {
        byte[] sourcePdf = twoPagePdf();
        Path pdfPath = tempDir.resolve("source.pdf");
        java.nio.file.Files.write(pdfPath, sourcePdf);

        TranslationDocumentParseResponse response = responseWithWorkspaceState();
        TranslationDocumentBookmarkExportService service = new TranslationDocumentBookmarkExportService(
                new FakeKnowledgeStore(response),
                new FakeFileStorage(pdfPath, sourcePdf.length)
        );

        ExportedTranslationDocumentFile exported = service.exportWithBookmarks("doc-001");

        assertThat(exported.fileName()).isEqualTo("source-bookmarks.pdf");
        try (PDDocument document = PDDocument.load(exported.bytes())) {
            List<String> titles = outlineTitles(document.getDocumentCatalog().getDocumentOutline());
            assertThat(titles).contains(
                    "第1章 绪论",
                    "我的学习书签",
                    "复杂度公式",
                    "笔记：O(n^2) 推导"
            );
        }
    }

    private static TranslationDocumentParseResponse responseWithWorkspaceState() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-001");
        response.setFileName("source.pdf");
        response.setPageCount(2);

        TranslationDocumentOutlineItemDto outlineItem = new TranslationDocumentOutlineItemDto();
        outlineItem.setId("outline-1");
        outlineItem.setTitle("第1章 绪论");
        outlineItem.setLevel(1);
        outlineItem.setPageNumber(1);
        response.setOutline(List.of(outlineItem));

        TranslationDocumentUserBookmarkDto bookmark = new TranslationDocumentUserBookmarkDto();
        bookmark.setId("bookmark-1");
        bookmark.setTitle("复杂度公式");
        bookmark.setPageNumber(2);
        bookmark.setLevel(2);

        TranslationDocumentStudyNoteDto note = new TranslationDocumentStudyNoteDto();
        note.setId("note-1");
        note.setDocumentId("doc-001");
        note.setBookmarkId("bookmark-1");
        note.setTitle("O(n^2) 推导");
        note.setPageNumber(2);
        note.setStatus("saved");
        note.setSource("manual");

        TranslationDocumentWorkspaceStateDto workspaceState = new TranslationDocumentWorkspaceStateDto();
        workspaceState.setUserBookmarks(List.of(bookmark));
        workspaceState.setStudyNotes(List.of(note));
        response.setWorkspaceState(workspaceState);
        return response;
    }

    private static byte[] twoPagePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static List<String> outlineTitles(PDDocumentOutline outline) {
        List<String> titles = new ArrayList<>();
        if (outline == null) {
            return titles;
        }
        collectTitles(outline.getFirstChild(), titles);
        return titles;
    }

    private static void collectTitles(PDOutlineItem item, List<String> titles) {
        PDOutlineItem current = item;
        while (current != null) {
            titles.add(current.getTitle());
            if (current.hasChildren()) {
                collectTitles(current.getFirstChild(), titles);
            }
            current = current.getNextSibling();
        }
    }

    private static final class FakeKnowledgeStore extends TranslationDocumentKnowledgeStore {
        private final TranslationDocumentParseResponse response;

        private FakeKnowledgeStore(TranslationDocumentParseResponse response) {
            super(new ObjectMapper(), nullMapper());
            this.response = response;
        }

        @Override
        public Optional<TranslationDocumentParseResponse> findByDocumentId(String documentId) {
            return response.getDocumentId().equals(documentId) ? Optional.of(response) : Optional.empty();
        }
    }

    private static final class FakeFileStorage extends TranslationDocumentFileStorage {
        private final Path pdfPath;
        private final long fileSize;

        private FakeFileStorage(Path pdfPath, long fileSize) {
            super(Path.of("."), null);
            this.pdfPath = pdfPath;
            this.fileSize = fileSize;
        }

        @Override
        public Optional<StoredTranslationDocumentFile> findByDocumentId(String documentId) {
            return Optional.of(new StoredTranslationDocumentFile(
                    documentId,
                    "source.pdf",
                    "application/pdf",
                    fileSize,
                    "f".repeat(64),
                    "local",
                    documentId + "/source.pdf",
                    "/api/translation/documents/" + documentId + "/file",
                    pdfPath
            ));
        }
    }

    private static TranslationDocumentKnowledgeMapper nullMapper() {
        return null;
    }
}
