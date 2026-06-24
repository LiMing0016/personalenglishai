package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerRequest;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationKnowledgeChunkDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDocumentSourceAnswerServiceTest {

    @Test
    void answersWithSourceChunkCitationForCurrentSelection() {
        FakeKnowledgeStore knowledgeStore = new FakeKnowledgeStore(response());
        TranslationDocumentSourceAnswerService service = new TranslationDocumentSourceAnswerService(knowledgeStore);

        TranslationDocumentAgentAnswerRequest request = new TranslationDocumentAgentAnswerRequest();
        request.setQuestion("请解释动态空间扩容");
        request.setSelectedText("动态空间扩容会在容量不足时申请更大的数组");
        request.setPageNumber(55);
        request.setElementId("p55-e2");
        request.setBbox("[[100,200],[500,200],[500,260],[100,260]]");

        TranslationDocumentAgentAnswerResponse answer = service.answer("doc-001", request);

        assertThat(answer.getAnswer()).contains("动态空间扩容");
        assertThat(answer.getSourceChunks()).extracting(TranslationKnowledgeChunkDto::getId)
                .containsExactly("doc-001-c2");
        assertThat(answer.getCitations()).hasSize(1);
        assertThat(answer.getCitations().get(0).getDocumentId()).isEqualTo("doc-001");
        assertThat(answer.getCitations().get(0).getChunkId()).isEqualTo("doc-001-c2");
        assertThat(answer.getCitations().get(0).getPageNumber()).isEqualTo(55);
        assertThat(answer.getCitations().get(0).getElementId()).isEqualTo("p55-e2");
        assertThat(answer.getCitations().get(0).getBbox()).isEqualTo("[[100,200],[500,200],[500,260],[100,260]]");
        assertThat(answer.getCitations().get(0).getQuote()).contains("容量不足");
    }

    private static TranslationDocumentParseResponse response() {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId("doc-001");
        response.setFileName("source.pdf");

        TranslationDocumentElementDto first = element("p54-e1", 54, 1,
                "向量是一种顺序表结构，支持随机访问。", "[[10,20],[200,20],[200,60],[10,60]]");
        TranslationDocumentElementDto second = element("p55-e2", 55, 2,
                "动态空间扩容会在容量不足时申请更大的数组，并复制原有元素。", "[[100,200],[500,200],[500,260],[100,260]]");
        response.setElements(List.of(first, second));

        TranslationKnowledgeChunkDto unrelated = chunk("doc-001-c1", 1, first);
        TranslationKnowledgeChunkDto matched = chunk("doc-001-c2", 2, second);
        response.setKnowledgeChunks(List.of(unrelated, matched));
        return response;
    }

    private static TranslationDocumentElementDto element(String id, int pageNumber, int order, String text, String bbox) {
        TranslationDocumentElementDto element = new TranslationDocumentElementDto();
        element.setId(id);
        element.setPageNumber(pageNumber);
        element.setOrder(order);
        element.setType("paragraph");
        element.setText(text);
        element.setBbox(bbox);
        element.setQualityScore(0.9);
        return element;
    }

    private static TranslationKnowledgeChunkDto chunk(String id, int order, TranslationDocumentElementDto element) {
        TranslationKnowledgeChunkDto chunk = new TranslationKnowledgeChunkDto();
        chunk.setId(id);
        chunk.setChunkOrder(order);
        chunk.setChunkType("paragraph");
        chunk.setContent(element.getText());
        chunk.setSummary(element.getText());
        chunk.setSourceElementIds(List.of(element.getId()));
        chunk.setPageNumbers(List.of(element.getPageNumber()));
        chunk.setStartElementOrder(element.getOrder());
        chunk.setEndElementOrder(element.getOrder());
        chunk.setSectionPath(List.of("第2章 向量"));
        return chunk;
    }

    private static final class FakeKnowledgeStore extends TranslationDocumentKnowledgeStore {
        private final TranslationDocumentParseResponse response;

        private FakeKnowledgeStore(TranslationDocumentParseResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public Optional<TranslationDocumentParseResponse> findByDocumentId(String documentId) {
            return response.getDocumentId().equals(documentId) ? Optional.of(response) : Optional.empty();
        }
    }
}
