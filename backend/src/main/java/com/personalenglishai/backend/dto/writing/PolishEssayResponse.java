package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishEssayResponse {

    private String rubricKey;
    private String policyKey;
    private String polishRubricKey;
    private String route;
    private String processingModeLabel;
    private String topicAlignmentStatus;
    private String rewriteMode;
    private String baselineBand;
    private Integer baselineScore;
    private Map<String, String> baselineGrades;
    private String finalBand;
    private Integer finalScore;
    private Map<String, String> finalGrades;
    private Integer sourceBandRank;
    private Integer targetBandRank;
    private Boolean accepted;
    private Boolean guardTriggered;
    private Boolean fallbackToOriginal;
    private Boolean targetMet;
    private Integer attemptCount;
    private String targetTier;
    private String targetGap;
    private Boolean bestEffort;
    private DirectionSnapshot baselineDirection;
    private DirectionSnapshot finalDirection;
    private String bindingReason;
    private List<String> unmetCoreDimensions;
    private String polishedEssay;
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

    public String getRubricKey() { return rubricKey; }
    public void setRubricKey(String rubricKey) { this.rubricKey = rubricKey; }

    public String getPolicyKey() { return policyKey; }
    public void setPolicyKey(String policyKey) { this.policyKey = policyKey; }

    public String getPolishRubricKey() { return polishRubricKey; }
    public void setPolishRubricKey(String polishRubricKey) { this.polishRubricKey = polishRubricKey; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getProcessingModeLabel() { return processingModeLabel; }
    public void setProcessingModeLabel(String processingModeLabel) { this.processingModeLabel = processingModeLabel; }

    public String getTopicAlignmentStatus() { return topicAlignmentStatus; }
    public void setTopicAlignmentStatus(String topicAlignmentStatus) { this.topicAlignmentStatus = topicAlignmentStatus; }

    public String getRewriteMode() { return rewriteMode; }
    public void setRewriteMode(String rewriteMode) { this.rewriteMode = rewriteMode; }

    public String getBaselineBand() { return baselineBand; }
    public void setBaselineBand(String baselineBand) { this.baselineBand = baselineBand; }

    public Integer getBaselineScore() { return baselineScore; }
    public void setBaselineScore(Integer baselineScore) { this.baselineScore = baselineScore; }

    public Map<String, String> getBaselineGrades() { return baselineGrades; }
    public void setBaselineGrades(Map<String, String> baselineGrades) { this.baselineGrades = baselineGrades; }

    public String getFinalBand() { return finalBand; }
    public void setFinalBand(String finalBand) { this.finalBand = finalBand; }

    public Integer getFinalScore() { return finalScore; }
    public void setFinalScore(Integer finalScore) { this.finalScore = finalScore; }

    public Map<String, String> getFinalGrades() { return finalGrades; }
    public void setFinalGrades(Map<String, String> finalGrades) { this.finalGrades = finalGrades; }

    public Integer getSourceBandRank() { return sourceBandRank; }
    public void setSourceBandRank(Integer sourceBandRank) { this.sourceBandRank = sourceBandRank; }

    public Integer getTargetBandRank() { return targetBandRank; }
    public void setTargetBandRank(Integer targetBandRank) { this.targetBandRank = targetBandRank; }

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }

    public Boolean getGuardTriggered() { return guardTriggered; }
    public void setGuardTriggered(Boolean guardTriggered) { this.guardTriggered = guardTriggered; }

    public Boolean getFallbackToOriginal() { return fallbackToOriginal; }
    public void setFallbackToOriginal(Boolean fallbackToOriginal) { this.fallbackToOriginal = fallbackToOriginal; }

    public Boolean getTargetMet() { return targetMet; }
    public void setTargetMet(Boolean targetMet) { this.targetMet = targetMet; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public String getTargetTier() { return targetTier; }
    public void setTargetTier(String targetTier) { this.targetTier = targetTier; }

    public String getTargetGap() { return targetGap; }
    public void setTargetGap(String targetGap) { this.targetGap = targetGap; }

    public Boolean getBestEffort() { return bestEffort; }
    public void setBestEffort(Boolean bestEffort) { this.bestEffort = bestEffort; }

    public DirectionSnapshot getBaselineDirection() { return baselineDirection; }
    public void setBaselineDirection(DirectionSnapshot baselineDirection) { this.baselineDirection = baselineDirection; }

    public DirectionSnapshot getFinalDirection() { return finalDirection; }
    public void setFinalDirection(DirectionSnapshot finalDirection) { this.finalDirection = finalDirection; }

    public String getBindingReason() { return bindingReason; }
    public void setBindingReason(String bindingReason) { this.bindingReason = bindingReason; }

    public List<String> getUnmetCoreDimensions() { return unmetCoreDimensions; }
    public void setUnmetCoreDimensions(List<String> unmetCoreDimensions) { this.unmetCoreDimensions = unmetCoreDimensions; }

    public String getPolishedEssay() { return polishedEssay; }
    public void setPolishedEssay(String polishedEssay) { this.polishedEssay = polishedEssay; }

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
    public static class DirectionSnapshot {
        private String relevance;
        private String taskCompletion;
        private String coverage;
        private String maxBand;

        public DirectionSnapshot() {}

        public DirectionSnapshot(String relevance, String taskCompletion, String coverage, String maxBand) {
            this.relevance = relevance;
            this.taskCompletion = taskCompletion;
            this.coverage = coverage;
            this.maxBand = maxBand;
        }

        public String getRelevance() { return relevance; }
        public void setRelevance(String relevance) { this.relevance = relevance; }

        public String getTaskCompletion() { return taskCompletion; }
        public void setTaskCompletion(String taskCompletion) { this.taskCompletion = taskCompletion; }

        public String getCoverage() { return coverage; }
        public void setCoverage(String coverage) { this.coverage = coverage; }

        public String getMaxBand() { return maxBand; }
        public void setMaxBand(String maxBand) { this.maxBand = maxBand; }
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
