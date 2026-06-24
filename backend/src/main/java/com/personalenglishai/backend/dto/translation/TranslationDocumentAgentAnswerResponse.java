package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationDocumentAgentAnswerResponse {
    private String answer;
    private List<TranslationSourceCitationDto> citations = new ArrayList<>();
    private List<TranslationKnowledgeChunkDto> sourceChunks = new ArrayList<>();

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<TranslationSourceCitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<TranslationSourceCitationDto> citations) {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public List<TranslationKnowledgeChunkDto> getSourceChunks() {
        return sourceChunks;
    }

    public void setSourceChunks(List<TranslationKnowledgeChunkDto> sourceChunks) {
        this.sourceChunks = sourceChunks == null ? new ArrayList<>() : sourceChunks;
    }
}
