package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentStudyNoteDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentUserBookmarkDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentWorkspaceStateDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TranslationDocumentBookmarkExportService {
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final TranslationDocumentKnowledgeStore knowledgeStore;
    private final TranslationDocumentFileStorage fileStorage;

    public TranslationDocumentBookmarkExportService(
            TranslationDocumentKnowledgeStore knowledgeStore,
            TranslationDocumentFileStorage fileStorage) {
        this.knowledgeStore = knowledgeStore;
        this.fileStorage = fileStorage;
    }

    public ExportedTranslationDocumentFile exportWithBookmarks(String documentId) {
        TranslationDocumentParseResponse response = knowledgeStore.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档知识快照不存在"));
        StoredTranslationDocumentFile storedFile = fileStorage.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档原文件不存在"));
        if (!Files.exists(storedFile.getPath())) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档原文件不存在");
        }

        try (PDDocument document = PDDocument.load(storedFile.getPath().toFile());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            addDocumentOutline(document, outline, response.getOutline());
            addWorkspaceOutline(document, outline, response.getWorkspaceState());
            outline.openNode();
            document.save(output);
            return new ExportedTranslationDocumentFile(exportFileName(storedFile.getFileName()), PDF_CONTENT_TYPE, output.toByteArray());
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "生成带书签 PDF 失败");
        }
    }

    private void addDocumentOutline(
            PDDocument document,
            PDDocumentOutline root,
            List<TranslationDocumentOutlineItemDto> outlineItems) {
        Deque<Integer> levels = new ArrayDeque<>();
        Deque<PDOutlineItem> parents = new ArrayDeque<>();
        for (TranslationDocumentOutlineItemDto item : outlineItems) {
            if (item == null || isBlank(item.getTitle()) || item.getPageNumber() < 1) {
                continue;
            }
            int level = clampLevel(item.getLevel());
            while (!levels.isEmpty() && levels.peekLast() >= level) {
                levels.removeLast();
                parents.removeLast();
            }
            PDOutlineItem outlineItem = outlineItem(document, item.getTitle(), item.getPageNumber());
            if (parents.isEmpty()) {
                root.addLast(outlineItem);
            } else {
                parents.peekLast().addLast(outlineItem);
                parents.peekLast().openNode();
            }
            levels.addLast(level);
            parents.addLast(outlineItem);
        }
    }

    private void addWorkspaceOutline(
            PDDocument document,
            PDDocumentOutline root,
            TranslationDocumentWorkspaceStateDto state) {
        if (state == null || (state.getUserBookmarks().isEmpty() && state.getStudyNotes().isEmpty())) {
            return;
        }
        int rootPage = resolveWorkspaceRootPage(state);
        PDOutlineItem workspaceRoot = outlineItem(document, "我的学习书签", rootPage);
        root.addLast(workspaceRoot);
        workspaceRoot.openNode();

        Map<String, PDOutlineItem> bookmarkNodes = new LinkedHashMap<>();
        state.getUserBookmarks().stream()
                .filter(bookmark -> bookmark != null && !isBlank(bookmark.getTitle()))
                .sorted(Comparator
                        .comparingInt((TranslationDocumentUserBookmarkDto bookmark) -> safePage(bookmark.getPageNumber()))
                        .thenComparingInt(TranslationDocumentUserBookmarkDto::getOrder))
                .forEach(bookmark -> {
                    PDOutlineItem bookmarkNode = outlineItem(document, bookmark.getTitle(), bookmark.getPageNumber());
                    workspaceRoot.addLast(bookmarkNode);
                    bookmarkNodes.put(bookmark.getId(), bookmarkNode);
                });

        state.getStudyNotes().stream()
                .filter(note -> note != null && !isBlank(note.getTitle()))
                .sorted(Comparator.comparingInt(note -> safePage(note.getPageNumber())))
                .forEach(note -> {
                    PDOutlineItem noteNode = outlineItem(document, "笔记：" + note.getTitle(), note.getPageNumber());
                    PDOutlineItem parent = bookmarkNodes.get(note.getBookmarkId());
                    if (parent == null) {
                        workspaceRoot.addLast(noteNode);
                    } else {
                        parent.addLast(noteNode);
                        parent.openNode();
                    }
                });
    }

    private int resolveWorkspaceRootPage(TranslationDocumentWorkspaceStateDto state) {
        if (!state.getUserBookmarks().isEmpty()) {
            return safePage(state.getUserBookmarks().get(0).getPageNumber());
        }
        if (!state.getStudyNotes().isEmpty()) {
            return safePage(state.getStudyNotes().get(0).getPageNumber());
        }
        return state.getCurrentPage() == null ? 1 : safePage(state.getCurrentPage());
    }

    private PDOutlineItem outlineItem(PDDocument document, String title, int pageNumber) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title.strip());
        item.setDestination(destination(document, pageNumber));
        return item;
    }

    private PDPageXYZDestination destination(PDDocument document, int pageNumber) {
        int pageIndex = Math.max(0, Math.min(document.getNumberOfPages() - 1, pageNumber - 1));
        PDPage page = document.getPage(pageIndex);
        PDPageXYZDestination destination = new PDPageXYZDestination();
        destination.setPage(page);
        return destination;
    }

    private int safePage(int pageNumber) {
        return Math.max(1, pageNumber);
    }

    private int clampLevel(int level) {
        return Math.max(1, Math.min(6, level));
    }

    private String exportFileName(String fileName) {
        String normalized = isBlank(fileName) ? "document.pdf" : fileName.strip();
        int dotIndex = normalized.lastIndexOf('.');
        String baseName = dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
        return baseName + "-bookmarks.pdf";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
