package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranslationDocumentParseService {
    private static final int MIN_EXTRACTED_TEXT_CHARS = 20;
    private static final int MIN_USABLE_TEXT_CHARS = 80;
    private static final int MAX_TOC_SCAN_PAGES = 12;
    private static final int MIN_TOC_ITEM_COUNT = 2;
    private static final double MIN_TEXT_LAYER_PAGE_COVERAGE = 0.03;
    private static final Pattern TOC_HEADING_PATTERN = Pattern.compile(
            "^(目录|目\\s*录|contents|table\\s+of\\s+contents)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOC_ITEM_PATTERN = Pattern.compile(
            "^(.+?)(?:\\s*[.·•…]{2,}\\s*|\\s{2,}|\\s+)(\\d{1,4})\\s*$"
    );
    private static final Pattern NUMBERED_SECTION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)+)\\b.*");
    private final TranslationOcrService ocrService;

    @Autowired
    public TranslationDocumentParseService(TranslationOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public TranslationDocumentParseResponse parsePdf(String originalFilename, byte[] pdfBytes) {
        validatePdfUpload(originalFilename, pdfBytes);

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
            Map<Integer, String> pageTexts = new LinkedHashMap<>();
            int order = 1;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                pageTexts.put(page, pageText);
                List<String> pageBlocks = splitIntoLearningBlocks(pageText);
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
            List<TranslationDocumentOutlineItemDto> outline = readDocumentOutline(document);
            if (outline.isEmpty()) {
                outline = readTableOfContentsOutline(pageTexts, document.getNumberOfPages());
            }
            response.setOutline(outline);
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

    public TranslationDocumentParseResponse parsePdfWithOcr(String originalFilename, byte[] pdfBytes) {
        return parsePdfWithOcr(originalFilename, pdfBytes, DocumentParseMode.STANDARD);
    }

    public TranslationDocumentParseResponse parsePdfWithOcr(String originalFilename, byte[] pdfBytes, DocumentParseMode parseMode) {
        return parsePdfWithOcr(originalFilename, pdfBytes, TranslationOcrOptions.of(parseMode));
    }

    public TranslationDocumentParseResponse parsePdfWithOcr(String originalFilename, byte[] pdfBytes, TranslationOcrOptions options) {
        validatePdfUpload(originalFilename, pdfBytes);
        TranslationOcrResult ocrResult = ocrService.recognizePdf(pdfBytes, options);
        TranslationDocumentParseResponse response = baseResponse(originalFilename, inferOcrPageCount(ocrResult));
        applyOcrResult(response, ocrResult, false);
        return TranslationDocumentKnowledgePipeline.enrich(response);
    }

    private List<TranslationDocumentOutlineItemDto> readDocumentOutline(PDDocument document) throws IOException {
        PDDocumentOutline documentOutline = document.getDocumentCatalog().getDocumentOutline();
        if (documentOutline == null || !documentOutline.hasChildren()) {
            return List.of();
        }

        List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
        collectOutlineItems(document, documentOutline.getFirstChild(), 1, outline);
        return outline;
    }

    private void collectOutlineItems(
            PDDocument document,
            PDOutlineItem item,
            int level,
            List<TranslationDocumentOutlineItemDto> outline) throws IOException {
        PDOutlineItem current = item;
        while (current != null) {
            String title = current.getTitle() == null ? "" : current.getTitle().strip();
            int pageNumber = resolveOutlinePageNumber(document, current);
            if (!title.isBlank() && pageNumber > 0) {
                TranslationDocumentOutlineItemDto outlineItem = new TranslationDocumentOutlineItemDto();
                outlineItem.setId("pdf-outline-" + (outline.size() + 1));
                outlineItem.setTitle(title);
                outlineItem.setLevel(Math.max(1, Math.min(6, level)));
                outlineItem.setPageNumber(pageNumber);
                outlineItem.setSource("pdf_outline");
                outlineItem.setConfidence(1.0);
                outline.add(outlineItem);
            }
            if (current.hasChildren()) {
                collectOutlineItems(document, current.getFirstChild(), level + 1, outline);
            }
            current = current.getNextSibling();
        }
    }

    private int resolveOutlinePageNumber(PDDocument document, PDOutlineItem item) throws IOException {
        PDPage destinationPage = item.findDestinationPage(document);
        if (destinationPage != null) {
            int pageIndex = document.getPages().indexOf(destinationPage);
            if (pageIndex >= 0) {
                return pageIndex + 1;
            }
        }

        PDDestination destination = item.getDestination();
        if (destination == null) {
            PDAction action = item.getAction();
            if (action instanceof PDActionGoTo goTo) {
                destination = goTo.getDestination();
            }
        }
        if (destination instanceof PDPageDestination pageDestination) {
            PDPage page = pageDestination.getPage();
            if (page != null) {
                int pageIndex = document.getPages().indexOf(page);
                if (pageIndex >= 0) {
                    return pageIndex + 1;
                }
            }
            int zeroBasedPageNumber = pageDestination.retrievePageNumber();
            if (zeroBasedPageNumber >= 0) {
                return zeroBasedPageNumber + 1;
            }
        }
        return 0;
    }

    private List<TranslationDocumentOutlineItemDto> readTableOfContentsOutline(Map<Integer, String> pageTexts, int pageCount) {
        List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
        boolean tocStarted = false;
        int scanLimit = Math.min(pageCount, MAX_TOC_SCAN_PAGES);
        for (int page = 1; page <= scanLimit; page++) {
            List<String> lines = normalizeTocLines(pageTexts.get(page));
            boolean hasTocHeading = lines.stream().anyMatch(this::isTocHeadingLine);
            if (!tocStarted && !hasTocHeading) {
                continue;
            }

            List<TranslationDocumentOutlineItemDto> pageOutline = parseTableOfContentsPage(lines, pageCount, outline.size());
            if (pageOutline.isEmpty()) {
                if (tocStarted) {
                    break;
                }
                continue;
            }

            tocStarted = true;
            outline.addAll(pageOutline);
        }
        return outline.size() >= MIN_TOC_ITEM_COUNT ? outline : List.of();
    }

    private List<TranslationDocumentOutlineItemDto> parseTableOfContentsPage(
            List<String> lines,
            int pageCount,
            int outlineOffset) {
        List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
        String pendingTitle = "";
        for (String line : lines) {
            if (isTocNoiseLine(line)) {
                continue;
            }

            Matcher matcher = TOC_ITEM_PATTERN.matcher(line);
            if (!matcher.matches()) {
                if (looksLikeWrappedTocTitle(line)) {
                    pendingTitle = mergeTocTitle(pendingTitle, line);
                }
                continue;
            }

            String title = cleanTocTitle(mergeTocTitle(pendingTitle, matcher.group(1)));
            pendingTitle = "";
            int targetPage = parseTocTargetPage(matcher.group(2), pageCount);
            if (targetPage <= 0 || !isValidTocTitle(title)) {
                continue;
            }

            TranslationDocumentOutlineItemDto item = new TranslationDocumentOutlineItemDto();
            item.setId("toc-outline-" + (outlineOffset + outline.size() + 1));
            item.setTitle(title);
            item.setLevel(inferTocLevel(title));
            item.setPageNumber(targetPage);
            item.setSource("toc_page");
            item.setConfidence(0.86);
            outline.add(item);
        }
        return outline;
    }

    private List<String> normalizeTocLines(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }
        String normalized = pageText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ');
        List<String> lines = new ArrayList<>();
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.strip().replaceAll("\\s{2,}", " ");
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean isTocHeadingLine(String line) {
        return line != null && TOC_HEADING_PATTERN.matcher(line.strip()).matches();
    }

    private boolean isTocNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        String normalized = line.strip();
        if (isTocHeadingLine(normalized) || isPageNumberLine(normalized)) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("copyright")
                || lower.contains("all rights reserved")
                || lower.contains("isbn")
                || lower.contains("publisher")
                || lower.contains("press")
                || lower.contains("edition")
                || lower.contains("微信公众号")
                || lower.contains("qq群")
                || lower.contains("qq:");
    }

    private boolean looksLikeWrappedTocTitle(String line) {
        if (line == null || line.isBlank() || line.length() > 120) {
            return false;
        }
        if (isTocNoiseLine(line) || line.matches(".*\\d{1,4}\\s*$")) {
            return false;
        }
        return line.matches("(?i)^(chapter|part|unit)\\s+\\d+\\b.*")
                || line.matches("^第[一二三四五六七八九十百千0-9]+[章节篇部].*")
                || line.matches("^\\d+(?:\\.\\d+)+\\b.*")
                || Character.isUpperCase(line.codePointAt(0));
    }

    private String mergeTocTitle(String left, String right) {
        String first = left == null ? "" : left.strip();
        String second = right == null ? "" : right.strip();
        if (first.isBlank()) {
            return second;
        }
        if (second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    private String cleanTocTitle(String title) {
        if (title == null) {
            return "";
        }
        return title
                .replaceAll("[.·•…]{2,}", " ")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    private int parseTocTargetPage(String value, int pageCount) {
        try {
            int pageNumber = Integer.parseInt(value);
            return pageNumber >= 1 && pageNumber <= pageCount ? pageNumber : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isValidTocTitle(String title) {
        if (title == null || title.length() < 2 || title.length() > 160) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return !lower.contains("copyright")
                && !lower.contains("publisher")
                && !lower.contains("isbn")
                && !lower.contains("press")
                && !title.matches("^\\d+$");
    }

    private int inferTocLevel(String title) {
        String normalized = title == null ? "" : title.strip();
        Matcher numbered = NUMBERED_SECTION_PATTERN.matcher(normalized);
        if (numbered.matches()) {
            String sectionNumber = numbered.group(1);
            int dots = (int) sectionNumber.chars().filter(ch -> ch == '.').count();
            return Math.max(1, Math.min(6, dots + 1));
        }
        if (normalized.matches("(?i)^(chapter|part|unit)\\s+\\d+\\b.*")
                || normalized.matches("^第[一二三四五六七八九十百千0-9]+[章节篇部].*")) {
            return 1;
        }
        return 1;
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
        TranslationOcrResult ocrResult = ocrService.recognizePdf(pdfBytes, DocumentParseMode.STANDARD);
        applyOcrResult(response, ocrResult, true);
    }

    private void applyOcrResult(TranslationDocumentParseResponse response, TranslationOcrResult ocrResult, boolean fallback) {
        response.setRawOcrResponse(ocrResult.getRawResponse());
        if (ocrResult.isSucceeded()) {
            List<TranslationDocumentBlockDto> ocrBlocks = buildBlocksFromOcr(ocrResult);
            if (hasEnoughText(ocrBlocks)) {
                response.setParseStatus("SUCCEEDED");
                response.setOcrStatus("SUCCEEDED");
                response.setProvider("paddle_ocr");
                response.setParseMode(fallback ? "ocr_fallback" : "ocr_direct");
                response.setFallbackUsed(fallback);
                response.setBlocks(ocrBlocks);
                response.setElements(buildElementsFromOcr(ocrResult));
                response.setWarnings(ocrSuccessWarnings(ocrResult, fallback));
                return;
            }
        }

        response.setParseStatus(fallback ? "NEEDS_OCR" : "FAILED");
        response.setOcrStatus(fallback ? "REQUIRED" : "FAILED");
        response.setBlocks(List.of());
        response.setElements(List.of());
        String defaultMessage = fallback
                ? "PDF 文本层为空、过少或质量较低，需要 OCR 后再进入精读解析。"
                : "PaddleOCR 未能解析出可用文本。";
        String message = ocrResult.getMessage() == null ? defaultMessage : ocrResult.getMessage();
        response.setWarnings(List.of(defaultMessage, message));
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

    private List<String> ocrSuccessWarnings(TranslationOcrResult ocrResult, boolean fallback) {
        List<String> warnings = new ArrayList<>();
        warnings.add(fallback
                ? "PDF 文本层为空或质量较低，已使用 OCR 结果生成精读材料。"
                : "已使用 PaddleOCR 结果生成精读材料。");
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

    private int inferOcrPageCount(TranslationOcrResult ocrResult) {
        if (ocrResult == null || ocrResult.getPages().isEmpty()) {
            return 0;
        }
        return ocrResult.getPages().stream()
                .mapToInt(TranslationOcrPageText::getPageNumber)
                .max()
                .orElse(ocrResult.getPages().size());
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
