package com.personalenglishai.backend.controller.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerRequest;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentWorkspaceStateDto;
import com.personalenglishai.backend.service.translation.DocumentParseMode;
import com.personalenglishai.backend.service.translation.ExportedTranslationDocumentFile;
import com.personalenglishai.backend.service.translation.StoredTranslationDocumentFile;
import com.personalenglishai.backend.service.translation.TranslationDocumentBookmarkExportService;
import com.personalenglishai.backend.service.translation.TranslationDocumentFileStorage;
import com.personalenglishai.backend.service.translation.TranslationDocumentKnowledgeStore;
import com.personalenglishai.backend.service.translation.TranslationDocumentImportService;
import com.personalenglishai.backend.service.translation.TranslationDocumentParseService;
import com.personalenglishai.backend.service.translation.TranslationDocumentSourceAnswerService;
import com.personalenglishai.backend.service.translation.TranslationDocumentWorkspaceStateService;
import com.personalenglishai.backend.service.translation.UploadedTranslationDocument;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/translation/documents")
public class TranslationDocumentController {
    private final TranslationDocumentParseService parseService;
    private final TranslationDocumentImportService importService;
    private final TranslationDocumentKnowledgeStore knowledgeStore;
    private final TranslationDocumentFileStorage fileStorage;
    private final TranslationDocumentSourceAnswerService sourceAnswerService;
    private final TranslationDocumentWorkspaceStateService workspaceStateService;
    private final TranslationDocumentBookmarkExportService bookmarkExportService;

    public TranslationDocumentController(
            TranslationDocumentParseService parseService,
            TranslationDocumentImportService importService,
            TranslationDocumentKnowledgeStore knowledgeStore,
            TranslationDocumentFileStorage fileStorage,
            TranslationDocumentSourceAnswerService sourceAnswerService,
            TranslationDocumentWorkspaceStateService workspaceStateService,
            TranslationDocumentBookmarkExportService bookmarkExportService) {
        this.parseService = parseService;
        this.importService = importService;
        this.knowledgeStore = knowledgeStore;
        this.fileStorage = fileStorage;
        this.sourceAnswerService = sourceAnswerService;
        this.workspaceStateService = workspaceStateService;
        this.bookmarkExportService = bookmarkExportService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranslationDocumentParseResponse parse(@RequestParam("file") MultipartFile file) {
        try {
            return parseService.parsePdf(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "读取上传文件失败");
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranslationDocumentParseResponse importDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", required = false, defaultValue = "immersive") String mode,
            @RequestParam(value = "parseMode", required = false, defaultValue = "standard") String parseMode) {
        try {
            return importService.importDocument(new UploadedTranslationDocument(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes(),
                    mode,
                    DocumentParseMode.fromWireName(parseMode)
            ));
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "读取上传文件失败");
        }
    }

    @GetMapping("/{documentId}/knowledge")
    public TranslationDocumentParseResponse getKnowledge(@PathVariable String documentId) {
        return knowledgeStore.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档知识快照不存在"));
    }

    @PostMapping("/{documentId}/agent-answer")
    public TranslationDocumentAgentAnswerResponse answerWithSourceChunks(
            @PathVariable String documentId,
            @RequestBody TranslationDocumentAgentAnswerRequest request) {
        return sourceAnswerService.answer(documentId, request);
    }

    @PutMapping("/{documentId}/workspace-state")
    public TranslationDocumentWorkspaceStateDto saveWorkspaceState(
            @PathVariable String documentId,
            @RequestBody TranslationDocumentWorkspaceStateDto request) {
        return workspaceStateService.save(documentId, request);
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> getOriginalFile(@PathVariable String documentId) {
        StoredTranslationDocumentFile storedFile = fileStorage.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档原文件不存在"));
        if (!Files.exists(storedFile.getPath())) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档原文件不存在");
        }

        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            contentType = MediaType.parseMediaType(storedFile.getContentType());
        } catch (RuntimeException ignored) {
            // Keep application/octet-stream for invalid legacy content types.
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(storedFile.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
                .body(new FileSystemResource(storedFile.getPath()));
    }

    @GetMapping("/{documentId}/file-with-bookmarks")
    public ResponseEntity<byte[]> getFileWithBookmarks(@PathVariable String documentId) {
        ExportedTranslationDocumentFile exported = bookmarkExportService.exportWithBookmarks(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(exported.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exported.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(exported.bytes());
    }
}
