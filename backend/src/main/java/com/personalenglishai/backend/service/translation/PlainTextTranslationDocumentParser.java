package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlainTextTranslationDocumentParser implements TranslationDocumentParser {
    @Override
    public boolean supports(UploadedTranslationDocument document) {
        return TranslationDocumentFileTypes.hasExtension(document, "txt")
                || TranslationDocumentFileTypes.contentTypeContains(document, "text/plain");
    }

    @Override
    public TranslationDocumentParseResponse parse(UploadedTranslationDocument document) {
        String text = new String(document.getBytes(), StandardCharsets.UTF_8);
        List<String> paragraphs = TranslationDocumentBlockFactory.splitParagraphs(text);
        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        for (int index = 0; index < paragraphs.size(); index++) {
            blocks.add(TranslationDocumentBlockFactory.block("txt-b", index + 1, "paragraph", paragraphs.get(index)));
        }
        return TranslationDocumentBlockFactory.response(document.getOriginalFilename(), "TXT", blocks);
    }
}
