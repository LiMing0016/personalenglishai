package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TranslationDocumentParseService {
    private static final int MIN_EXTRACTED_TEXT_CHARS = 20;
    private static final int MIN_USABLE_TEXT_CHARS = 80;
    private static final double MIN_TEXT_LAYER_PAGE_COVERAGE = 0.03;
    private final TranslationOcrService ocrService;

    @Autowired
    public TranslationDocumentParseService(TranslationOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public TranslationDocumentParseResponse parsePdf(String originalFilename, byte[] pdfBytes) {
        validatePdfUpload(originalFilename, pdfBytes);

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
            int order = 1;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                List<String> pageBlocks = splitIntoLearningBlocks(stripper.getText(document));
                for (String text : pageBlocks) {
                    blocks.add(new TranslationDocumentBlockDto(
                            "p" + page + "-b" + order,
                            inferBlockType(text),
                            order,
                            page,
                            text,
                            null
                    ));
                    order++;
                }
            }

            TranslationDocumentParseResponse response = baseResponse(originalFilename, document.getNumberOfPages());
            response.setBlocks(blocks);
            if (hasUsableTextLayer(blocks, document.getNumberOfPages())) {
                response.setParseStatus("SUCCEEDED");
                response.setOcrStatus("NOT_REQUIRED");
            } else {
                applyOcrFallback(response, pdfBytes);
            }
            return TranslationDocumentKnowledgePipeline.enrich(response);
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "PDF 解析失败，请确认文件未损坏");
        }
    }

    List<String> splitIntoLearningBlocks(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }

        String normalized = pageText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ');
        String[] lines = normalized.split("\n");
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                flushBlock(blocks, current);
                continue;
            }
            if (isPageNumberLine(line)) {
                continue;
            }
            if (shouldStartNewBlock(current, line)) {
                flushBlock(blocks, current);
            }
            appendLine(current, line);
        }
        flushBlock(blocks, current);
        return blocks;
    }

    private void applyOcrFallback(TranslationDocumentParseResponse response, byte[] pdfBytes) {
        TranslationOcrResult ocrResult = ocrService.recognizePdf(pdfBytes);
        response.setRawOcrResponse(ocrResult.getRawResponse());
        if (ocrResult.isSucceeded()) {
            List<TranslationDocumentBlockDto> ocrBlocks = buildBlocksFromOcr(ocrResult);
            if (hasEnoughText(ocrBlocks)) {
                response.setParseStatus("SUCCEEDED");
                response.setOcrStatus("SUCCEEDED");
                response.setProvider("paddle_ocr");
                response.setParseMode("ocr_fallback");
                response.setFallbackUsed(true);
                response.setBlocks(ocrBlocks);
                response.setElements(buildElementsFromOcr(ocrResult));
                response.setWarnings(ocrSuccessWarnings(ocrResult));
                return;
            }
        }

        response.setParseStatus("NEEDS_OCR");
        response.setOcrStatus("REQUIRED");
        response.setBlocks(List.of());
        response.setElements(List.of());
        String message = ocrResult.getMessage() == null ? "PDF 文本层为空、过少或质量较低，需要 OCR 后再进入精读解析。" : ocrResult.getMessage();
        response.setWarnings(List.of("PDF 文本层为空、过少或质量较低，需要 OCR 后再进入精读解析。", message));
    }

    private List<TranslationDocumentBlockDto> buildBlocksFromOcr(TranslationOcrResult ocrResult) {
        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        int order = 1;
        for (TranslationOcrPageText page : ocrResult.getPages()) {
            for (String text : splitIntoLearningBlocks(page.getText())) {
                blocks.add(new TranslationDocumentBlockDto(
                        "p" + page.getPageNumber() + "-ocr-b" + order,
                        inferBlockType(text),
                        order,
                        page.getPageNumber(),
                        text,
                        null
                ));
                order++;
            }
        }
        return blocks;
    }

    private List<TranslationDocumentElementDto> buildElementsFromOcr(TranslationOcrResult ocrResult) {
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        int order = 1;
        for (TranslationOcrPageText page : ocrResult.getPages()) {
            if (page.getElements().isEmpty()) {
                continue;
            }
            for (TranslationOcrElement ocrElement : page.getElements()) {
                String text = ocrElement.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                TranslationDocumentElementDto element = new TranslationDocumentElementDto();
                element.setId("p" + page.getPageNumber() + "-ocr-e" + order);
                element.setType(normalizeOcrElementType(ocrElement.getType()));
                element.setOrder(order);
                element.setPageNumber(page.getPageNumber());
                element.setText(text);
                element.setBbox(ocrElement.getBbox());
                element.setProvider(nonBlank(ocrElement.getSource(), "paddle_ocr"));
                element.setConfidence(ocrElement.getConfidence());
                element.setRecognitionStatus("READY");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("source", nonBlank(ocrElement.getSource(), "paddle_ocr"));
                metadata.put("rawType", ocrElement.getRawType());
                metadata.put("warnings", ocrElement.getWarnings());
                metadata.put("pageWidth", page.getWidth());
                metadata.put("pageHeight", page.getHeight());
                if (order == 1 && ocrResult.getRawResponse() != null && !ocrResult.getRawResponse().isBlank()) {
                    metadata.put("rawOcrResponse", ocrResult.getRawResponse());
                }
                element.setMetadata(metadata);
                elements.add(element);
                order++;
            }
        }
        return elements;
    }

    private List<String> ocrSuccessWarnings(TranslationOcrResult ocrResult) {
        List<String> warnings = new ArrayList<>();
        warnings.add("PDF 文本层为空或质量较低，已使用 OCR 结果生成精读材料。");
        for (TranslationOcrPageText page : ocrResult.getPages()) {
            warnings.addAll(page.getWarnings());
            for (TranslationOcrElement element : page.getElements()) {
                warnings.addAll(element.getWarnings());
            }
        }
        return warnings.stream()
                .filter(warning -> warning != null && !warning.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeOcrElementType(String type) {
        if (type == null || type.isBlank() || "text".equals(type)) {
            return "paragraph";
        }
        return type;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void validatePdfUpload(String originalFilename, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "PDF 文件不能为空");
        }
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".pdf")) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "仅支持 PDF 文件解析");
        }
    }

    private TranslationDocumentParseResponse baseResponse(String originalFilename, int pageCount) {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId(UUID.randomUUID().toString());
        response.setFileName(originalFilename);
        response.setSourceType("PDF");
        response.setPageCount(pageCount);
        return response;
    }

    private boolean hasEnoughText(List<TranslationDocumentBlockDto> blocks) {
        int textLength = blocks.stream()
                .map(TranslationDocumentBlockDto::getText)
                .filter(text -> text != null)
                .mapToInt(text -> text.replaceAll("\\s+", "").length())
                .sum();
        return textLength >= MIN_EXTRACTED_TEXT_CHARS;
    }

    private boolean hasUsableTextLayer(List<TranslationDocumentBlockDto> blocks, int pageCount) {
        if (!hasEnoughText(blocks)) {
            return false;
        }
        String text = blocks.stream()
                .map(TranslationDocumentBlockDto::getText)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);
        int compactLength = text.replaceAll("\\s+", "").length();
        if (compactLength < MIN_USABLE_TEXT_CHARS) {
            return false;
        }
        if (hasPromotionalNoise(text) || hasGarbledText(text)) {
            return false;
        }
        long activePages = blocks.stream()
                .map(TranslationDocumentBlockDto::getPageNumber)
                .filter(page -> page != null && page > 0)
                .distinct()
                .count();
        if (pageCount >= 20 && activePages > 0) {
            double coverage = activePages / (double) pageCount;
            return coverage >= MIN_TEXT_LAYER_PAGE_COVERAGE || compactLength >= 4000;
        }
        return true;
    }

    private boolean hasPromotionalNoise(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return false;
        }
        String[] markers = {
                "微信公众号", "关注微信", "qq群", "qq:", "qq：", "持续更新",
                "订阅", "扫码", "免费资料", "发普", "37药", "378327010"
        };
        int hits = 0;
        for (String marker : markers) {
            if (normalized.contains(marker.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits >= 2;
    }

    private boolean hasGarbledText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        long replacementChars = text.chars().filter(ch -> ch == '\uFFFD').count();
        if (replacementChars >= 2) {
            return true;
        }
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return true;
        }
        long controlOrUnknown = compact.chars()
                .filter(ch -> Character.isISOControl(ch) || ch == '\uFFFD')
                .count();
        return controlOrUnknown / (double) compact.length() > 0.01;
    }

    private void appendLine(StringBuilder current, String line) {
        if (current.isEmpty()) {
            current.append(line);
            return;
        }
        if (current.charAt(current.length() - 1) == '-') {
            current.deleteCharAt(current.length() - 1);
            current.append(line);
            return;
        }
        current.append(' ').append(line);
    }

    private boolean shouldStartNewBlock(StringBuilder current, String nextLine) {
        if (current.isEmpty()) {
            return false;
        }
        String text = current.toString().strip();
        if (text.matches(".*[.!?。！？]$")) {
            return true;
        }
        return inferBlockType(text).equals("heading") && Character.isUpperCase(nextLine.codePointAt(0));
    }

    private void flushBlock(List<String> blocks, StringBuilder current) {
        String text = current.toString().strip().replaceAll("\\s{2,}", " ");
        if (!text.isBlank()) {
            blocks.add(text);
        }
        current.setLength(0);
    }

    private boolean isPageNumberLine(String line) {
        return line.matches("(?i)^(page\\s*)?\\d{1,4}(\\s*/\\s*\\d{1,4})?$");
    }

    private String inferBlockType(String text) {
        if (text.length() <= 90 && !text.matches(".*[.!?。！？]$")) {
            return "heading";
        }
        return "paragraph";
    }
}
