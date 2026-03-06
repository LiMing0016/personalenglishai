package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishResponse {

    private String polished;
    private String explanation;
    private List<Candidate> candidates;

    public PolishResponse() {}

    public PolishResponse(String polished, String explanation) {
        this.polished = polished;
        this.explanation = explanation;
    }

    public PolishResponse(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public String getPolished() { return polished; }
    public void setPolished(String polished) { this.polished = polished; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public List<Candidate> getCandidates() { return candidates; }
    public void setCandidates(List<Candidate> candidates) { this.candidates = candidates; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        private String polished;
        private String explanation;

        public Candidate() {}

        public Candidate(String polished, String explanation) {
            this.polished = polished;
            this.explanation = explanation;
        }

        public String getPolished() { return polished; }
        public void setPolished(String polished) { this.polished = polished; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}
