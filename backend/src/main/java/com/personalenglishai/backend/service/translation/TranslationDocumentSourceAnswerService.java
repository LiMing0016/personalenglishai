package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerRequest;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationKnowledgeChunkDto;
import com.personalenglishai.backend.dto.translation.TranslationSourceCitationDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TranslationDocumentSourceAnswerService {
    private static final int MAX_CITATIONS = 3;
    private static final int QUOTE_LIMIT = 180;

    private final TranslationDocumentKnowledgeStore knowledgeStore;

    public TranslationDocumentSourceAnswerService(TranslationDocumentKnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    public TranslationDocumentAgentAnswerResponse answer(
            String documentId,
            TranslationDocumentAgentAnswerRequest request) {
        TranslationDocumentParseResponse document = knowledgeStore.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档知识快照不存在"));

        List<TranslationKnowledgeChunkDto> chunks = document.getKnowledgeChunks();
        if (chunks == null || chunks.isEmpty()) {
            TranslationDocumentAgentAnswerResponse empty = new TranslationDocumentAgentAnswerResponse();
            empty.setAnswer("当前文档还没有可检索的 source chunks，请先完成文档解析和知识构建。");
            return empty;
        }

        Map<String, TranslationDocumentElementDto> elementMap = document.getElements().stream()
                .collect(Collectors.toMap(TranslationDocumentElementDto::getId, Function.identity(), (left, ignored) -> left));
        List<ScoredChunk> ranked = rankChunks(chunks, request);
        List<ScoredChunk> selected = ranked.stream()
                .filter(item -> item.score > 0)
                .limit(MAX_CITATIONS)
                .toList();
        if (selected.isEmpty()) {
            selected = ranked.stream().limit(1).toList();
        }

        TranslationDocumentAgentAnswerResponse response = new TranslationDocumentAgentAnswerResponse();
        response.setSourceChunks(selected.stream().map(item -> item.chunk).toList());
        response.setCitations(selected.stream()
                .map(item -> toCitation(documentId, item, request, elementMap))
                .toList());
        response.setAnswer(buildAnswer(request, response.getCitations()));
        return response;
    }

    private List<ScoredChunk> rankChunks(
            List<TranslationKnowledgeChunkDto> chunks,
            TranslationDocumentAgentAnswerRequest request) {
        List<ScoredChunk> ranked = new ArrayList<>();
        for (TranslationKnowledgeChunkDto chunk : chunks) {
            double score = 0;
            if (hasText(request.getElementId()) && chunk.getSourceElementIds().contains(request.getElementId())) {
                score += 5;
            }
            if (request.getPageNumber() != null && chunk.getPageNumbers().contains(request.getPageNumber())) {
                score += 2;
            }
            score += overlapScore(request.getSelectedText(), chunk.getContent()) * 3;
            score += overlapScore(request.getQuestion(), chunk.getContent()) * 2;
            ranked.add(new ScoredChunk(chunk, score));
        }
        ranked.sort(Comparator
                .comparingDouble(ScoredChunk::score).reversed()
                .thenComparingInt(item -> item.chunk.getChunkOrder()));
        return ranked;
    }

    private TranslationSourceCitationDto toCitation(
            String documentId,
            ScoredChunk scored,
            TranslationDocumentAgentAnswerRequest request,
            Map<String, TranslationDocumentElementDto> elementMap) {
        TranslationKnowledgeChunkDto chunk = scored.chunk;
        String elementId = resolveCitationElementId(chunk, request);
        TranslationDocumentElementDto element = elementMap.get(elementId);

        TranslationSourceCitationDto citation = new TranslationSourceCitationDto();
        citation.setDocumentId(documentId);
        citation.setChunkId(chunk.getId());
        citation.setPageNumber(resolveCitationPageNumber(chunk, request, element));
        citation.setElementId(elementId);
        citation.setBbox(resolveCitationBbox(request, element));
        citation.setQuote(resolveQuote(request, chunk));
        citation.setSectionPath(chunk.getSectionPath());
        citation.setScore(round(scored.score));
        return citation;
    }

    private String resolveCitationElementId(TranslationKnowledgeChunkDto chunk, TranslationDocumentAgentAnswerRequest request) {
        if (hasText(request.getElementId()) && chunk.getSourceElementIds().contains(request.getElementId())) {
            return request.getElementId();
        }
        return chunk.getSourceElementIds().isEmpty() ? null : chunk.getSourceElementIds().get(0);
    }

    private Integer resolveCitationPageNumber(
            TranslationKnowledgeChunkDto chunk,
            TranslationDocumentAgentAnswerRequest request,
            TranslationDocumentElementDto element) {
        if (request.getPageNumber() != null && chunk.getPageNumbers().contains(request.getPageNumber())) {
            return request.getPageNumber();
        }
        if (element != null && element.getPageNumber() > 0) {
            return element.getPageNumber();
        }
        return chunk.getPageNumbers().isEmpty() ? null : chunk.getPageNumbers().get(0);
    }

    private String resolveCitationBbox(TranslationDocumentAgentAnswerRequest request, TranslationDocumentElementDto element) {
        if (element != null && hasText(element.getBbox())) {
            return element.getBbox();
        }
        return hasText(request.getBbox()) ? request.getBbox() : null;
    }

    private String resolveQuote(TranslationDocumentAgentAnswerRequest request, TranslationKnowledgeChunkDto chunk) {
        if (hasText(request.getSelectedText()) && overlapScore(request.getSelectedText(), chunk.getContent()) > 0.35) {
            return truncate(request.getSelectedText(), QUOTE_LIMIT);
        }
        return truncate(chunk.getContent(), QUOTE_LIMIT);
    }

    private String buildAnswer(
            TranslationDocumentAgentAnswerRequest request,
            List<TranslationSourceCitationDto> citations) {
        if (citations.isEmpty()) {
            return "当前问题没有命中可引用的资料片段，请换一个更具体的问题或重新解析文档。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("基于当前文档的 source chunks，我找到 ")
                .append(citations.size())
                .append(" 个相关来源。");
        if (hasText(request.getQuestion())) {
            builder.append("针对“").append(truncate(request.getQuestion(), 60)).append("”，可以先按原文这样理解：");
        }
        builder.append("\n\n");
        for (int index = 0; index < citations.size(); index++) {
            TranslationSourceCitationDto citation = citations.get(index);
            builder.append(index + 1)
                    .append(". Page ")
                    .append(citation.getPageNumber() == null ? "?" : citation.getPageNumber())
                    .append("：")
                    .append(citation.getQuote());
            if (index < citations.size() - 1) {
                builder.append("\n");
            }
        }
        builder.append("\n\n你可以点击引用回到 PDF 对应页继续精读。");
        return builder.toString();
    }

    private double overlapScore(String query, String content) {
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        if (!hasText(normalizedQuery) || !hasText(normalizedContent)) {
            return 0;
        }
        if (normalizedContent.contains(normalizedQuery) || normalizedQuery.contains(normalizedContent)) {
            return 1;
        }

        Set<String> queryTokens = tokenize(normalizedQuery);
        Set<String> contentTokens = tokenize(normalizedContent);
        if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
            return 0;
        }

        long hits = queryTokens.stream().filter(contentTokens::contains).count();
        return hits / (double) queryTokens.size();
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        for (int index = 0; index < value.length() - 1; index++) {
            char left = value.charAt(index);
            char right = value.charAt(index + 1);
            if (isCjk(left) && isCjk(right)) {
                tokens.add(value.substring(index, index + 2));
            }
        }
        return tokens;
    }

    private boolean isCjk(char value) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit).trim() + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static final class ScoredChunk {
        private final TranslationKnowledgeChunkDto chunk;
        private final double score;

        private ScoredChunk(TranslationKnowledgeChunkDto chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        private double score() {
            return score;
        }
    }
}
