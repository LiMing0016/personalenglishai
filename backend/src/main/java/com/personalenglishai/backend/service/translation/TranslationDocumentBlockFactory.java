package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TranslationDocumentBlockFactory {
    private TranslationDocumentBlockFactory() {
    }

    static TranslationDocumentParseResponse response(String fileName, String sourceType, List<TranslationDocumentBlockDto> blocks) {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId(UUID.randomUUID().toString());
        response.setFileName(fileName);
        response.setSourceType(sourceType);
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(0);
        response.setBlocks(blocks);
        return TranslationDocumentKnowledgePipeline.enrich(response);
    }

    static List<String> splitParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .trim();
        String[] rawParagraphs = normalized.split("\\n\\s*\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String raw : rawParagraphs) {
            String paragraph = raw.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("")
                    .strip()
                    .replaceAll("\\s{2,}", " ");
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    static TranslationDocumentBlockDto block(String idPrefix, int order, String type, String text) {
        return new TranslationDocumentBlockDto(
                idPrefix + order,
                type,
                order,
                0,
                text,
                null
        );
    }
}
