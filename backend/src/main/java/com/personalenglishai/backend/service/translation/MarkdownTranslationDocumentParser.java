package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownTranslationDocumentParser implements TranslationDocumentParser {
    @Override
    public boolean supports(UploadedTranslationDocument document) {
        return TranslationDocumentFileTypes.hasExtension(document, "md")
                || TranslationDocumentFileTypes.contentTypeContains(document, "markdown");
    }

    @Override
    public TranslationDocumentParseResponse parse(UploadedTranslationDocument document) {
        String text = new String(document.getBytes(), StandardCharsets.UTF_8);
        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        int order = 1;
        for (String paragraph : splitMarkdownBlocks(text)) {
            String type = inferType(paragraph);
            String cleanText = cleanMarkdownText(paragraph, type);
            if (!cleanText.isBlank()) {
                blocks.add(TranslationDocumentBlockFactory.block("md-b", order, type, cleanText));
                order++;
            }
        }
        return TranslationDocumentBlockFactory.response(document.getOriginalFilename(), "MD", blocks);
    }

    private List<String> splitMarkdownBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = raw.strip();
            if (line.isBlank()) {
                continue;
            }
            blocks.add(line);
        }
        return blocks;
    }

    private String inferType(String text) {
        if (text.matches("^#{1,6}\\s+.+")) {
            return "heading";
        }
        if (text.matches("^([-*+] |\\d+\\.\\s+).+")) {
            return "list";
        }
        return "paragraph";
    }

    private String cleanMarkdownText(String text, String type) {
        if ("heading".equals(type)) {
            return text.replaceFirst("^#{1,6}\\s+", "").strip();
        }
        if ("list".equals(type)) {
            return text.replaceFirst("^([-*+] |\\d+\\.\\s+)", "").strip();
        }
        return text.strip();
    }
}
