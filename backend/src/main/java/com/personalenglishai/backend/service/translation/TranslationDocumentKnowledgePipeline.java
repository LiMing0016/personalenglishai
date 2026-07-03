package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentQualityDto;
import com.personalenglishai.backend.dto.translation.TranslationKnowledgeChunkDto;
import com.personalenglishai.backend.dto.translation.TranslationLanguageProfileDto;
import com.personalenglishai.backend.dto.translation.TranslationParseJobDto;
import com.personalenglishai.backend.dto.translation.TranslationParseDiagnosisDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TranslationDocumentKnowledgePipeline {
    private static final int TARGET_CHUNK_TOKENS = 180;

    private TranslationDocumentKnowledgePipeline() {
    }

    static TranslationDocumentParseResponse enrich(TranslationDocumentParseResponse response) {
        if (response == null) {
            return null;
        }

        List<TranslationDocumentBlockDto> cleanedBlocks = cleanBlocks(response.getBlocks());
        response.setBlocks(cleanedBlocks);

        String provider = resolveProvider(response);
        List<TranslationDocumentElementDto> sourceElements = response.getElements();
        List<TranslationDocumentElementDto> elements = sourceElements == null || sourceElements.isEmpty()
                ? buildElements(cleanedBlocks, provider, response.getParseStatus())
                : cleanElements(sourceElements, provider, response.getParseStatus());
        List<TranslationKnowledgeChunkDto> chunks = buildChunks(response.getDocumentId(), elements);
        TranslationParseDiagnosisDto diagnosis = diagnose(response, elements);
        TranslationDocumentQualityDto quality = scoreDocument(diagnosis, elements, chunks);
        diagnosis.setFallbackRecommended(quality.isFallbackRecommended());

        List<TranslationDocumentOutlineItemDto> explicitOutline = cleanOutline(response.getOutline());
        response.setElements(elements);
        response.setOutline(explicitOutline.isEmpty() ? buildOutline(elements) : explicitOutline);
        response.setKnowledgeChunks(chunks);
        response.setAssets(buildAssets(response, elements, diagnosis, provider));
        response.setDiagnosis(diagnosis);
        response.setQuality(quality);
        response.setLanguageProfile(detectLanguage(elements));
        response.setParseJob(buildParseJob(response, provider));
        return response;
    }

    private static List<TranslationDocumentBlockDto> cleanBlocks(List<TranslationDocumentBlockDto> blocks) {
        List<TranslationDocumentBlockDto> cleaned = new ArrayList<>();
        int order = 1;
        for (TranslationDocumentBlockDto block : blocks == null ? List.<TranslationDocumentBlockDto>of() : blocks) {
            String text = cleanText(block.getText());
            if (text.isBlank() || isPageNumberLine(text) || isLowQualityElementText(text)) {
                continue;
            }
            block.setText(text);
            block.setOrder(order++);
            cleaned.add(block);
        }
        return cleaned;
    }

    private static List<TranslationDocumentElementDto> buildElements(
            List<TranslationDocumentBlockDto> blocks,
            String provider,
            String parseStatus) {
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        for (TranslationDocumentBlockDto block : blocks) {
            TranslationDocumentElementDto element = new TranslationDocumentElementDto();
            int elementOrder = elements.size() + 1;
            element.setId(nonBlank(block.getId(), stableElementId(block.getPageNumber(), elementOrder)));
            element.setType(normalizeType(block.getType()));
            element.setOrder(elementOrder);
            element.setPageNumber(block.getPageNumber());
            element.setText(block.getText());
            element.setProvider(provider);
            element.setConfidence(block.getConfidence());
            element.setRecognitionStatus("SUCCEEDED".equals(parseStatus) ? "READY" : "NEEDS_OCR");
            element.setQualityScore(scoreElement(block.getText(), block.getPageNumber()));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", resolveElementSource(provider));
            metadata.put("originalType", block.getType());
            element.setMetadata(metadata);
            elements.add(element);
        }
        return elements;
    }

    private static List<TranslationDocumentElementDto> cleanElements(
            List<TranslationDocumentElementDto> sourceElements,
            String provider,
            String parseStatus) {
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        List<TranslationDocumentElementDto> sortedSourceElements = new ArrayList<>(
                sourceElements == null ? List.<TranslationDocumentElementDto>of() : sourceElements
        );
        sortedSourceElements.sort(Comparator
                .comparingInt(TranslationDocumentKnowledgePipeline::elementPageSortKey)
                .thenComparingInt(TranslationDocumentKnowledgePipeline::elementOrderSortKey));
        for (TranslationDocumentElementDto element : sortedSourceElements) {
            String text = cleanText(element.getText());
            if (text.isBlank() || isPageNumberLine(text) || isLowQualityElementText(text)) {
                continue;
            }
            int elementOrder = elements.size() + 1;
            String originalType = element.getType();
            if (element.getId() == null || element.getId().isBlank()) {
                element.setId(stableElementId(element.getPageNumber(), elementOrder));
            }
            element.setType(normalizeType(originalType));
            element.setOrder(elementOrder);
            element.setText(text);
            if (element.getProvider() == null || element.getProvider().isBlank()) {
                element.setProvider(provider);
            }
            if (element.getRecognitionStatus() == null || element.getRecognitionStatus().isBlank()) {
                element.setRecognitionStatus("SUCCEEDED".equals(parseStatus) ? "READY" : "NEEDS_OCR");
            }
            if (element.getQualityScore() <= 0) {
                element.setQualityScore(scoreElement(text, element.getPageNumber()));
            }
            Map<String, Object> metadata = new LinkedHashMap<>(element.getMetadata());
            metadata.putIfAbsent("source", resolveElementSource(element.getProvider()));
            metadata.putIfAbsent("originalType", originalType);
            element.setMetadata(metadata);
            elements.add(element);
        }
        return elements;
    }

    private static int elementPageSortKey(TranslationDocumentElementDto element) {
        return element != null && element.getPageNumber() > 0 ? element.getPageNumber() : Integer.MAX_VALUE;
    }

    private static int elementOrderSortKey(TranslationDocumentElementDto element) {
        return element != null && element.getOrder() > 0 ? element.getOrder() : Integer.MAX_VALUE;
    }

    private static List<TranslationKnowledgeChunkDto> buildChunks(String documentId, List<TranslationDocumentElementDto> elements) {
        List<TranslationKnowledgeChunkDto> chunks = new ArrayList<>();
        List<TranslationDocumentElementDto> buffer = new ArrayList<>();
        List<String> activeSectionPath = new ArrayList<>();
        List<String> bufferSectionPath = new ArrayList<>();
        int tokenCount = 0;

        for (TranslationDocumentElementDto element : elements) {
            int elementTokens = estimateTokens(element.getText());
            boolean heading = isHeading(element);
            boolean startsNewSection = heading && !buffer.isEmpty();
            boolean tooLarge = !buffer.isEmpty() && tokenCount + elementTokens > TARGET_CHUNK_TOKENS;
            if (startsNewSection || tooLarge) {
                chunks.add(toChunk(documentId, chunks.size() + 1, buffer, bufferSectionPath));
                buffer = new ArrayList<>();
                bufferSectionPath = new ArrayList<>();
                tokenCount = 0;
            }
            if (heading) {
                activeSectionPath = new ArrayList<>(List.of(summarizeSection(element.getText())));
            }
            if (buffer.isEmpty()) {
                bufferSectionPath = new ArrayList<>(activeSectionPath);
            }
            buffer.add(element);
            tokenCount += elementTokens;
        }

        if (!buffer.isEmpty()) {
            chunks.add(toChunk(documentId, chunks.size() + 1, buffer, bufferSectionPath));
        }
        linkNeighborChunks(chunks);
        return chunks;
    }

    private static List<TranslationDocumentOutlineItemDto> buildOutline(List<TranslationDocumentElementDto> elements) {
        List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int order = 1;
        for (TranslationDocumentElementDto element : elements) {
            if (!isOutlineCandidate(element)) {
                continue;
            }
            String title = summarizeSection(element.getText());
            String key = element.getPageNumber() + "\n" + title;
            if (title.isBlank() || seen.contains(key)) {
                continue;
            }
            seen.add(key);

            TranslationDocumentOutlineItemDto item = new TranslationDocumentOutlineItemDto();
            item.setId("outline-" + order++);
            item.setTitle(title);
            item.setLevel(inferOutlineLevel(title, element.getType()));
            item.setPageNumber(element.getPageNumber());
            item.setElementId(element.getId());
            item.setBbox(element.getBbox());
            item.setSource("rule_heading");
            item.setConfidence(element.getConfidence());
            outline.add(item);
        }
        return outline;
    }

    private static List<TranslationDocumentOutlineItemDto> cleanOutline(List<TranslationDocumentOutlineItemDto> sourceOutline) {
        List<TranslationDocumentOutlineItemDto> outline = new ArrayList<>();
        int order = 1;
        for (TranslationDocumentOutlineItemDto item : sourceOutline == null ? List.<TranslationDocumentOutlineItemDto>of() : sourceOutline) {
            if (item == null || item.getTitle() == null || item.getTitle().isBlank() || item.getPageNumber() <= 0) {
                continue;
            }
            item.setTitle(summarizeSection(item.getTitle()));
            item.setLevel(Math.max(1, Math.min(6, item.getLevel() <= 0 ? 1 : item.getLevel())));
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId("outline-" + order);
            }
            if (item.getSource() == null || item.getSource().isBlank()) {
                item.setSource("document_outline");
            }
            outline.add(item);
            order++;
        }
        return outline;
    }

    private static TranslationKnowledgeChunkDto toChunk(
            String documentId,
            int chunkOrder,
            List<TranslationDocumentElementDto> elements,
            List<String> sectionPath) {
        TranslationKnowledgeChunkDto chunk = new TranslationKnowledgeChunkDto();
        String stableDocumentId = documentId == null || documentId.isBlank() ? "doc" : documentId;
        chunk.setId(stableDocumentId + "-c" + chunkOrder);
        chunk.setChunkOrder(chunkOrder);
        chunk.setChunkType(resolveChunkType(elements));
        chunk.setContent(joinContent(elements));
        chunk.setSummary(summarize(chunk.getContent()));
        chunk.setSourceElementIds(elements.stream().map(TranslationDocumentElementDto::getId).toList());
        chunk.setPageNumbers(uniquePages(elements));
        chunk.setTokenCount(estimateTokens(chunk.getContent()));
        chunk.setQualityScore(round(elements.stream().mapToDouble(TranslationDocumentElementDto::getQualityScore).average().orElse(0)));
        chunk.setEmbeddingStatus("NOT_REQUIRED");
        chunk.setGranularity("small");
        chunk.setStartElementOrder(elements.get(0).getOrder());
        chunk.setEndElementOrder(elements.get(elements.size() - 1).getOrder());
        chunk.setSectionPath(sectionPath);
        return chunk;
    }

    private static void linkNeighborChunks(List<TranslationKnowledgeChunkDto> chunks) {
        for (int index = 0; index < chunks.size(); index++) {
            if (index > 0) {
                chunks.get(index).setPrevChunkId(chunks.get(index - 1).getId());
            }
            if (index < chunks.size() - 1) {
                chunks.get(index).setNextChunkId(chunks.get(index + 1).getId());
            }
        }
    }

    private static List<TranslationDocumentAssetDto> buildAssets(
            TranslationDocumentParseResponse response,
            List<TranslationDocumentElementDto> elements,
            TranslationParseDiagnosisDto diagnosis,
            String provider) {
        List<TranslationDocumentAssetDto> assets = new ArrayList<>();
        String stableDocumentId = response.getDocumentId() == null || response.getDocumentId().isBlank()
                ? "doc"
                : response.getDocumentId();
        int assetOrder = 1;
        for (TranslationDocumentAssetDto asset : response.getAssets() == null ? List.<TranslationDocumentAssetDto>of() : response.getAssets()) {
            if (asset.getAssetType() == null || asset.getAssetType().isBlank()) {
                continue;
            }
            if (asset.getId() == null || asset.getId().isBlank()) {
                asset.setId(stableDocumentId + "-a" + assetOrder);
            }
            if (asset.getProvider() == null || asset.getProvider().isBlank()) {
                asset.setProvider(provider);
            }
            if (asset.getRecognitionStatus() == null || asset.getRecognitionStatus().isBlank()) {
                asset.setRecognitionStatus("READY");
            }
            asset.setMetadata(new LinkedHashMap<>(asset.getMetadata()));
            assets.add(asset);
            assetOrder++;
        }
        for (TranslationDocumentElementDto element : elements) {
            if (!"table".equals(element.getType())) {
                continue;
            }
            TranslationDocumentAssetDto asset = new TranslationDocumentAssetDto();
            asset.setId(stableDocumentId + "-a" + assetOrder++);
            asset.setAssetType("table");
            asset.setPageNumber(element.getPageNumber());
            asset.setSourceElementId(element.getId());
            asset.setRecognizedText(element.getText());
            asset.setProvider(provider);
            asset.setRecognitionStatus("READY");
            asset.setConfidence(element.getConfidence());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("format", "markdown_table_or_plain_text");
            asset.setMetadata(metadata);
            assets.add(asset);
        }
        for (Integer page : diagnosis.getImageOnlyPages()) {
            TranslationDocumentAssetDto asset = new TranslationDocumentAssetDto();
            asset.setId(stableDocumentId + "-a" + assetOrder++);
            asset.setAssetType("page_snapshot");
            asset.setPageNumber(page);
            asset.setProvider(provider);
            asset.setRecognitionStatus("NEEDS_OCR");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("reason", "text_layer_not_detected");
            asset.setMetadata(metadata);
            assets.add(asset);
        }
        return assets;
    }

    private static TranslationLanguageProfileDto detectLanguage(List<TranslationDocumentElementDto> elements) {
        String content = joinContent(elements);
        int latin = 0;
        int han = 0;
        for (int index = 0; index < content.length(); index++) {
            char value = content.charAt(index);
            if (Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN) {
                han++;
            } else if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')) {
                latin++;
            }
        }
        int total = latin + han;
        TranslationLanguageProfileDto profile = new TranslationLanguageProfileDto();
        if (total == 0) {
            profile.setLanguageMixType("UNKNOWN");
            profile.setLanguageConfidence(0);
            return profile;
        }
        boolean englishPrimary = latin >= han;
        profile.setPrimaryLanguage(englishPrimary ? "en" : "zh");
        if (englishPrimary && han > Math.max(8, total * 0.08)) {
            profile.setSecondaryLanguages(List.of("zh"));
        } else if (!englishPrimary && latin > Math.max(8, total * 0.08)) {
            profile.setSecondaryLanguages(List.of("en"));
        }
        if (profile.getSecondaryLanguages().isEmpty()) {
            profile.setLanguageMixType(englishPrimary ? "EN" : "ZH");
        } else {
            profile.setLanguageMixType(englishPrimary ? "EN_ZH_LEARNING_MATERIAL" : "ZH_EN_LEARNING_MATERIAL");
        }
        profile.setLanguageConfidence(round(Math.max(latin, han) / (double) total));
        return profile;
    }

    private static TranslationParseJobDto buildParseJob(TranslationDocumentParseResponse response, String provider) {
        TranslationParseJobDto job = new TranslationParseJobDto();
        String stableDocumentId = response.getDocumentId() == null || response.getDocumentId().isBlank()
                ? "doc"
                : response.getDocumentId();
        String status = response.getParseStatus() == null || response.getParseStatus().isBlank()
                ? "SUCCEEDED"
                : response.getParseStatus();
        job.setDocumentId(response.getDocumentId());
        job.setJobId(stableDocumentId + "-parse");
        job.setStatus(status);
        job.setProvider(provider);
        job.setFallbackUsed(response.isFallbackUsed());
        job.setStage("SUCCEEDED".equals(status) ? "READY" : "PARSING");
        job.setProgress("SUCCEEDED".equals(status) ? 1.0 : 0.35);
        if ("NEEDS_OCR".equals(status)) {
            job.setErrorCode("NEEDS_OCR");
        } else if ("FAILED".equals(status)) {
            job.setErrorCode("PARSE_FAILED");
        }
        return job;
    }

    private static TranslationParseDiagnosisDto diagnose(
            TranslationDocumentParseResponse response,
            List<TranslationDocumentElementDto> elements) {
        TranslationParseDiagnosisDto diagnosis = new TranslationParseDiagnosisDto();
        int textLength = elements.stream().map(TranslationDocumentElementDto::getText).mapToInt(TranslationDocumentKnowledgePipeline::compactLength).sum();
        double coverage = textCoverage(textLength, response.getPageCount());
        double garbledRatio = garbledRatio(elements);

        diagnosis.setTextCoverageRatio(coverage);
        diagnosis.setGarbledRatio(garbledRatio);
        diagnosis.setHeaderFooterRatio(headerFooterRatio(elements));
        diagnosis.setWarnings(response.getWarnings());

        if (elements.isEmpty() || "NEEDS_OCR".equals(response.getParseStatus())) {
            diagnosis.setTextLayer("NONE");
            diagnosis.setOcrRecommended(true);
            diagnosis.setImageOnlyPages(inferImageOnlyPages(response.getPageCount()));
            return diagnosis;
        }

        diagnosis.setTextLayer(coverage < 0.35 ? "LOW" : "GOOD");
        diagnosis.setOcrRecommended(coverage < 0.2);
        diagnosis.setHighQualityProviderRecommended(garbledRatio > 0.05 || diagnosis.getHeaderFooterRatio() > 0.25);
        return diagnosis;
    }

    private static TranslationDocumentQualityDto scoreDocument(
            TranslationParseDiagnosisDto diagnosis,
            List<TranslationDocumentElementDto> elements,
            List<TranslationKnowledgeChunkDto> chunks) {
        TranslationDocumentQualityDto quality = new TranslationDocumentQualityDto();
        boolean paginated = elements.stream().anyMatch(element -> element.getPageNumber() > 0);
        double locationCoverage = elements.isEmpty()
                ? 0
                : paginated ? elements.stream().filter(element -> element.getPageNumber() > 0).count() / (double) elements.size() : 1;
        double highQualityChunks = chunks.isEmpty() ? 0 : chunks.stream().filter(chunk -> chunk.getQualityScore() >= 0.7).count() / (double) chunks.size();
        double score = 0.25
                + diagnosis.getTextCoverageRatio() * 0.5
                + locationCoverage * 0.15
                + highQualityChunks * 0.1
                - diagnosis.getGarbledRatio() * 0.4
                - diagnosis.getHeaderFooterRatio() * 0.15;
        if (diagnosis.isOcrRecommended()) {
            score = Math.min(score, 0.35);
        }

        quality.setDocumentQualityScore(round(clamp(score)));
        quality.setTextCoverageRatio(diagnosis.getTextCoverageRatio());
        quality.setGarbledRatio(diagnosis.getGarbledRatio());
        quality.setLocationCoverageRatio(round(locationCoverage));
        quality.setChunkHighQualityRatio(round(highQualityChunks));
        quality.setFallbackRecommended(quality.getDocumentQualityScore() < 0.55 || diagnosis.isHighQualityProviderRecommended());
        return quality;
    }

    private static double scoreElement(String text, int pageNumber) {
        int length = compactLength(text);
        double lengthScore = Math.min(1, length / 80.0);
        double locationScore = pageNumber > 0 ? 0.12 : 0;
        double garbledPenalty = garbledRatio(text) * 0.45;
        return round(clamp(0.35 + lengthScore * 0.55 + locationScore - garbledPenalty));
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left.endsWith("-") ? left.substring(0, left.length() - 1) + right : left + " " + right)
                .orElse("")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    private static boolean isPageNumberLine(String text) {
        return text.matches("(?i)^(page\\s*)?\\d{1,4}(\\s*/\\s*\\d{1,4})?$");
    }

    private static String resolveProvider(TranslationDocumentParseResponse response) {
        if (response.getProvider() != null && !response.getProvider().isBlank()) {
            return response.getProvider();
        }
        String sourceType = response.getSourceType() == null ? "" : response.getSourceType().toUpperCase(Locale.ROOT);
        return switch (sourceType) {
            case "PDF" -> "pdfbox";
            case "DOCX" -> "apache-poi";
            case "MD" -> "markdown";
            case "TXT" -> "plain-text";
            default -> "unknown";
        };
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "paragraph";
        }
        String normalized = type.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "text", "body", "normal", "plain_text", "block", "paragraph_text" -> "paragraph";
            case "section", "section_header", "header", "chapter", "subtitle" -> "heading";
            case "document_title", "main_title" -> "title";
            case "bullet", "ordered_list", "unordered_list" -> "list";
            case "figure", "picture", "diagram", "chart" -> "image";
            case "equation", "math" -> "formula";
            case "paragraph", "heading", "title", "list", "table", "image", "formula", "caption" -> normalized;
            default -> "paragraph";
        };
    }

    private static String resolveElementSource(String provider) {
        String normalized = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pdfbox" -> "pdf_text_layer";
            case "paddle_ocr" -> "paddle_ocr";
            case "apache-poi" -> "docx_text_layer";
            case "markdown" -> "markdown_text";
            case "plain-text" -> "plain_text";
            default -> normalized.isBlank() ? "document_parse" : normalized;
        };
    }

    private static String stableElementId(int pageNumber, int order) {
        String pagePart = pageNumber > 0 ? "p" + pageNumber : "p0";
        return pagePart + "-e" + order;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isLowQualityElementText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String compact = text.replaceAll("\\s+", "");
        if (compact.isBlank()) {
            return true;
        }
        int suspicious = countGarbled(compact);
        if (suspicious == 0) {
            return false;
        }
        return suspicious / (double) compact.length() > 0.2 || compact.length() <= 8;
    }

    private static boolean isHeading(TranslationDocumentElementDto element) {
        return "heading".equals(element.getType()) || "title".equals(element.getType());
    }

    private static boolean isOutlineCandidate(TranslationDocumentElementDto element) {
        if (element == null || element.getText() == null || element.getText().isBlank()) {
            return false;
        }
        if (isHeading(element)) {
            return true;
        }
        String text = element.getText().strip();
        return compactLength(text) <= 80 && (
                text.matches("^第[一二三四五六七八九十百千万0-9]+[章节篇部].*")
                        || text.matches("(?i)^chapter\\s+\\d+.*")
                        || text.matches("(?i)^unit\\s+\\d+.*")
                        || text.matches("^§?\\d+(\\.\\d+){1,4}\\s+.+")
        );
    }

    private static int inferOutlineLevel(String title, String type) {
        if (title == null || title.isBlank() || "title".equals(type)) {
            return 1;
        }
        String text = title.strip();
        if (text.matches("^第[一二三四五六七八九十百千万0-9]+[章节篇部].*")
                || text.matches("(?i)^chapter\\s+\\d+.*")
                || text.matches("(?i)^unit\\s+\\d+.*")) {
            return 1;
        }
        if (text.matches("^§?\\d+(\\.\\d+){1,4}.*")) {
            String numberPart = text.replaceFirst("^§?", "").split("\\s+")[0];
            int dots = 0;
            for (int index = 0; index < numberPart.length(); index++) {
                if (numberPart.charAt(index) == '.') {
                    dots++;
                }
            }
            return Math.min(6, dots + 1);
        }
        return 2;
    }

    private static String resolveChunkType(List<TranslationDocumentElementDto> elements) {
        if (elements.stream().allMatch(element -> "table".equals(element.getType()))) {
            return "table";
        }
        if (elements.stream().anyMatch(element -> "list".equals(element.getType()))) {
            return "expression_list";
        }
        if (elements.stream().anyMatch(element -> "heading".equals(element.getType()) || "title".equals(element.getType()))) {
            return "section";
        }
        return "paragraph";
    }

    private static String joinContent(List<TranslationDocumentElementDto> elements) {
        return String.join("\n\n", elements.stream().map(TranslationDocumentElementDto::getText).toList());
    }

    private static String summarize(String content) {
        if (content == null) {
            return "";
        }
        String singleLine = content.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
    }

    private static String summarizeSection(String content) {
        if (content == null) {
            return "";
        }
        String singleLine = content.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= 60 ? singleLine : singleLine.substring(0, 60) + "...";
    }

    private static List<Integer> uniquePages(List<TranslationDocumentElementDto> elements) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (TranslationDocumentElementDto element : elements) {
            if (element.getPageNumber() > 0) {
                pages.add(element.getPageNumber());
            }
        }
        return new ArrayList<>(pages);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int latinWords = text.split("\\s+").length;
        int cjkChars = (int) text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .count();
        return Math.max(1, latinWords + cjkChars);
    }

    private static int compactLength(String text) {
        return text == null ? 0 : text.replaceAll("\\s+", "").length();
    }

    private static double textCoverage(int textLength, int pageCount) {
        if (textLength <= 0) {
            return 0;
        }
        int denominator = pageCount <= 0 ? 60 : Math.max(120, pageCount * 120);
        return round(Math.min(1, textLength / (double) denominator));
    }

    private static double garbledRatio(List<TranslationDocumentElementDto> elements) {
        int total = 0;
        int garbled = 0;
        for (TranslationDocumentElementDto element : elements) {
            String text = element.getText() == null ? "" : element.getText();
            total += text.length();
            garbled += countGarbled(text);
        }
        return total == 0 ? 0 : round(garbled / (double) total);
    }

    private static double garbledRatio(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return countGarbled(text) / (double) text.length();
    }

    private static int countGarbled(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\uFFFD' || Character.isISOControl(value)) {
                count++;
            }
        }
        return count;
    }

    private static double headerFooterRatio(List<TranslationDocumentElementDto> elements) {
        if (elements.size() < 4) {
            return 0;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TranslationDocumentElementDto element : elements) {
            String text = element.getText() == null ? "" : element.getText().strip().toLowerCase(Locale.ROOT);
            if (text.length() <= 80) {
                counts.put(text, counts.getOrDefault(text, 0) + 1);
            }
        }
        long repeated = counts.values().stream().filter(count -> count > 1).mapToInt(Integer::intValue).sum();
        return round(repeated / (double) elements.size());
    }

    private static List<Integer> inferImageOnlyPages(int pageCount) {
        List<Integer> pages = new ArrayList<>();
        for (int page = 1; page <= pageCount; page++) {
            pages.add(page);
        }
        return pages;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
