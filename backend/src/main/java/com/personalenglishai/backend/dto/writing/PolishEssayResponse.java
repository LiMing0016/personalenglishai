package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishEssayResponse {

    private Summary summary;
    private List<SentencePolish> sentences;

    public PolishEssayResponse() {}

    public PolishEssayResponse(List<SentencePolish> sentences) {
        this.sentences = sentences;
    }

    public PolishEssayResponse(Summary summary, List<SentencePolish> sentences) {
        this.summary = summary;
        this.sentences = sentences;
    }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public List<SentencePolish> getSentences() { return sentences; }
    public void setSentences(List<SentencePolish> sentences) { this.sentences = sentences; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private List<String> strengths;
        private List<String> improvements;

        public Summary() {}

        public Summary(List<String> strengths, List<String> improvements) {
            this.strengths = strengths;
            this.improvements = improvements;
        }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getImprovements() { return improvements; }
        public void setImprovements(List<String> improvements) { this.improvements = improvements; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SentencePolish {
        private String original;
        private String polished;
        private String explanation;

        public SentencePolish() {}

        public SentencePolish(String original, String polished, String explanation) {
            this.original = original;
            this.polished = polished;
            this.explanation = explanation;
        }

        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }

        public String getPolished() { return polished; }
        public void setPolished(String polished) { this.polished = polished; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}
