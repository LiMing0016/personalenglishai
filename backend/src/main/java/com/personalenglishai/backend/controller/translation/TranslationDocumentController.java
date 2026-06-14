package com.personalenglishai.backend.controller.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.service.translation.DocumentParseMode;
import com.personalenglishai.backend.service.translation.TranslationDocumentKnowledgeStore;
import com.personalenglishai.backend.service.translation.TranslationDocumentImportService;
import com.personalenglishai.backend.service.translation.TranslationDocumentParseService;
import com.personalenglishai.backend.service.translation.UploadedTranslationDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/translation/documents")
public class TranslationDocumentController {
    private final TranslationDocumentParseService parseService;
    private final TranslationDocumentImportService importService;
    private final TranslationDocumentKnowledgeStore knowledgeStore;

    public TranslationDocumentController(
            TranslationDocumentParseService parseService,
            TranslationDocumentImportService importService,
            TranslationDocumentKnowledgeStore knowledgeStore) {
        this.parseService = parseService;
        this.importService = importService;
        this.knowledgeStore = knowledgeStore;
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
}
