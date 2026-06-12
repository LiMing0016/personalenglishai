package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocxTranslationDocumentParser implements TranslationDocumentParser {
    @Override
    public boolean supports(UploadedTranslationDocument document) {
        return TranslationDocumentFileTypes.hasExtension(document, "docx")
                || TranslationDocumentFileTypes.contentTypeContains(document, "wordprocessingml.document");
    }

    @Override
    public TranslationDocumentParseResponse parse(UploadedTranslationDocument document) {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(document.getBytes()))) {
            List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
            int order = 1;
            for (IBodyElement element : docx.getBodyElements()) {
                if (element.getElementType() == BodyElementType.PARAGRAPH && element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
                    if (text.isBlank()) {
                        continue;
                    }
                    blocks.add(TranslationDocumentBlockFactory.block("docx-b", order, inferType(paragraph, text), text));
                    order++;
                } else if (element.getElementType() == BodyElementType.TABLE && element instanceof XWPFTable table) {
                    String text = tableText(table);
                    if (text.isBlank()) {
                        continue;
                    }
                    blocks.add(TranslationDocumentBlockFactory.block("docx-b", order, "table", text));
                    order++;
                }
            }
            return TranslationDocumentBlockFactory.response(document.getOriginalFilename(), "DOCX", blocks);
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "DOCX 解析失败，请确认文件未损坏");
        }
    }

    private String inferType(XWPFParagraph paragraph, String text) {
        String style = paragraph.getStyle();
        String normalizedStyle = style == null ? "" : style.toLowerCase(Locale.ROOT);
        if (normalizedStyle.contains("heading") || normalizedStyle.startsWith("标题") || normalizedStyle.matches(".*[1-6]$")) {
            return "heading";
        }
        if (paragraph.getNumID() != null) {
            return "list";
        }
        if (text.length() <= 90 && !text.matches(".*[.!?。！？]$")) {
            return "heading";
        }
        return "paragraph";
    }

    private String tableText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText() == null ? "" : cell.getText().strip();
                if (!text.isBlank()) {
                    cells.add(text);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(String.join(" | ", cells));
            }
        }
        return String.join("\n", rows);
    }
}
