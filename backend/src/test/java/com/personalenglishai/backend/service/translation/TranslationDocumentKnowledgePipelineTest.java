package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentKnowledgePipelineTest {

    @Test
    void enrichStandardizesTextLayerBlocksIntoStableElements() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-elements");
        response.setFileName("source.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(2);
        response.setBlocks(List.of(
                new TranslationDocumentBlockDto("p1-b1", "text", 1, 1,
                        "This paragraph should become a standard paragraph element tied to page one.", 0.91),
                new TranslationDocumentBlockDto("p2-b2", "section_header", 2, 2,
                        "Chapter 1 Reliable Source", 0.95)
        ));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getId)
                .containsExactly("p1-b1", "p2-b2");
        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getType)
                .containsExactly("paragraph", "heading");
        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getPageNumber)
                .containsExactly(1, 2);
        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getProvider)
                .containsOnly("pdfbox");
        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getRecognitionStatus)
                .containsOnly("READY");
        assertThat(enriched.getElements().get(0).getMetadata())
                .containsEntry("source", "pdf_text_layer")
                .containsEntry("originalType", "text");
        assertThat(enriched.getElements().get(1).getMetadata())
                .containsEntry("source", "pdf_text_layer")
                .containsEntry("originalType", "section_header");
        assertThat(enriched.getKnowledgeChunks().stream()
                .flatMap(chunk -> chunk.getSourceElementIds().stream())
                .toList())
                .containsExactly("p1-b1", "p2-b2");
    }

    @Test
    void enrichDropsLowQualityElementsBeforeChunking() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-low-quality");
        response.setFileName("source.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(2);
        response.setElements(List.of(
                element(null, "text", 1, "���\u0001\u0002"),
                element(null, "text", 2,
                        "A useful paragraph with enough readable source content for grounded learning answers.")
        ));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getElements()).hasSize(1);
        assertThat(enriched.getElements().get(0).getId()).isEqualTo("p2-e1");
        assertThat(enriched.getElements().get(0).getType()).isEqualTo("paragraph");
        assertThat(enriched.getElements().get(0).getText()).contains("useful paragraph");
        assertThat(enriched.getKnowledgeChunks()).hasSize(1);
        assertThat(enriched.getKnowledgeChunks().get(0).getSourceElementIds()).containsExactly("p2-e1");
        assertThat(enriched.getKnowledgeChunks().get(0).getContent()).doesNotContain("���");
    }

    @Test
    void enrichOrdersSourceElementsByPageAndOriginalOrder() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-reading-order");
        response.setFileName("source.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(2);
        response.setElements(List.of(
                element("p2-e2", "text", 2, 2,
                        "Second page paragraph should appear after all first page elements."),
                element("p1-e2", "text", 2, 1,
                        "First page second paragraph should keep its local order."),
                element("p1-e1", "section_header", 1, 1,
                        "Chapter 1 Reading Order")
        ));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getId)
                .containsExactly("p1-e1", "p1-e2", "p2-e2");
        assertThat(enriched.getElements())
                .extracting(TranslationDocumentElementDto::getOrder)
                .containsExactly(1, 2, 3);
        assertThat(enriched.getKnowledgeChunks().stream()
                .flatMap(chunk -> chunk.getSourceElementIds().stream())
                .toList())
                .containsExactly("p1-e1", "p1-e2", "p2-e2");
    }

    @Test
    void enrichBuildsOutlineFromHeadingElementsWithoutParagraphNoise() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-outline");
        response.setFileName("computer-science.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(3);
        response.setBlocks(List.of(
                new TranslationDocumentBlockDto("p1-b1", "paragraph", 1, 1, "清华大学出版社", null),
                new TranslationDocumentBlockDto("p2-b2", "heading", 2, 2, "第1章 绪论", null),
                new TranslationDocumentBlockDto("p2-b3", "paragraph", 3, 2, "这一段正文不应该进入左侧目录。", null),
                new TranslationDocumentBlockDto("p3-b4", "heading", 4, 3, "§1.1 计算机与算法", null),
                new TranslationDocumentBlockDto("p3-b5", "paragraph", 5, 3, "算法章节正文。", null)
        ));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getTitle)
                .containsExactly("第1章 绪论", "§1.1 计算机与算法");
        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getPageNumber)
                .containsExactly(2, 3);
        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getLevel)
                .containsExactly(1, 2);
        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getSource)
                .containsOnly("rule_heading");
    }

    @Test
    void enrichPreservesExplicitPdfOutlineInsteadOfReplacingItWithRuleHeadings() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-pdf-outline");
        response.setFileName("computer-science.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setPageCount(2);
        response.setBlocks(List.of(
                new TranslationDocumentBlockDto("p1-b1", "heading", 1, 1, "Noisy extracted heading", null),
                new TranslationDocumentBlockDto("p2-b2", "paragraph", 2, 2, "Body text.", null)
        ));

        TranslationDocumentOutlineItemDto outlineItem = new TranslationDocumentOutlineItemDto();
        outlineItem.setId("pdf-outline-1");
        outlineItem.setTitle("Chapter 1 Real Bookmark");
        outlineItem.setLevel(1);
        outlineItem.setPageNumber(2);
        outlineItem.setSource("pdf_outline");
        outlineItem.setConfidence(1.0);
        response.setOutline(List.of(outlineItem));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getTitle)
                .containsExactly("Chapter 1 Real Bookmark");
        assertThat(enriched.getOutline())
                .extracting(TranslationDocumentOutlineItemDto::getSource)
                .containsExactly("pdf_outline");
    }

    @Test
    void enrichPreservesProviderImageAssets() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-image-assets");
        response.setFileName("source.pdf");
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("SUCCEEDED");
        response.setPageCount(1);
        response.setElements(List.of(element("p1-e1", "title", 1, 1, "Document Title")));
        TranslationDocumentAssetDto asset = new TranslationDocumentAssetDto();
        asset.setId("p1-vl-a1");
        asset.setAssetType("image");
        asset.setPageNumber(1);
        asset.setBbox("[[30,120],[330,120],[330,260],[30,260]]");
        asset.setProvider("paddle_vl");
        asset.setRecognitionStatus("READY");
        asset.setConfidence(0.87);
        asset.setMetadata(Map.of("dataUrl", "data:image/jpeg;base64,ZmFrZS1pbWFnZQ=="));
        response.setAssets(List.of(asset));

        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);

        assertThat(enriched.getAssets())
                .anySatisfy(enrichedAsset -> {
                    assertThat(enrichedAsset.getAssetType()).isEqualTo("image");
                    assertThat(enrichedAsset.getPageNumber()).isEqualTo(1);
                    assertThat(enrichedAsset.getMetadata())
                            .containsEntry("dataUrl", "data:image/jpeg;base64,ZmFrZS1pbWFnZQ==");
                });
    }

    private static TranslationDocumentElementDto element(String id, String type, int pageNumber, String text) {
        return element(id, type, 0, pageNumber, text);
    }

    private static TranslationDocumentElementDto element(String id, String type, int order, int pageNumber, String text) {
        TranslationDocumentElementDto element = new TranslationDocumentElementDto();
        element.setId(id);
        element.setType(type);
        element.setOrder(order);
        element.setPageNumber(pageNumber);
        element.setText(text);
        element.setProvider("pdfbox");
        return element;
    }
}
