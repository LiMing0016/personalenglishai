package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * POST /api/writing/evaluate 响应体（固定结构）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingEvaluateResponse {

    private String requestId;
    private String mode;
    private String source;
    private Map<String, String> grades;
    private Map<String, Integer> dimensionScores;
    private Map<String, AnalysisDto> analysis;
    @JsonProperty("priority_focus")
    private List<String> priorityFocus;
    @JsonProperty("priority_focus_detail")
    private PriorityFocusDto priorityFocusDetail;
    private ScoreDto score;
    @JsonProperty("gaokao_score")
    private GaokaoScoreDto gaokaoScore;
    private ImprovementDto improvement;
    private String summary;
    private List<ErrorDto> errors;
    @JsonProperty("error_count")
    private Integer errorCount;

    public WritingEvaluateResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public ScoreDto getScore() {
        return score;
    }

    public void setScore(ScoreDto score) {
        this.score = score;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, String> getGrades() {
        return grades;
    }

    public void setGrades(Map<String, String> grades) {
        this.grades = grades;
    }

    public Map<String, Integer> getDimensionScores() {
        return dimensionScores;
    }

    public void setDimensionScores(Map<String, Integer> dimensionScores) {
        this.dimensionScores = dimensionScores;
    }

    public Map<String, AnalysisDto> getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Map<String, AnalysisDto> analysis) {
        this.analysis = analysis;
    }

    public List<String> getPriorityFocus() {
        return priorityFocus;
    }

    public void setPriorityFocus(List<String> priorityFocus) {
        this.priorityFocus = priorityFocus;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ErrorDto> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDto> errors) {
        this.errors = errors;
        this.errorCount = (errors != null) ? errors.size() : 0;
    }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public GaokaoScoreDto getGaokaoScore() { return gaokaoScore; }
    public void setGaokaoScore(GaokaoScoreDto gaokaoScore) { this.gaokaoScore = gaokaoScore; }

    public PriorityFocusDto getPriorityFocusDetail() { return priorityFocusDetail; }
    public void setPriorityFocusDetail(PriorityFocusDto priorityFocusDetail) { this.priorityFocusDetail = priorityFocusDetail; }

    public ImprovementDto getImprovement() { return improvement; }
    public void setImprovement(ImprovementDto improvement) { this.improvement = improvement; }

    /** 高考预估分（换算成高考实际分制） */
    public static class GaokaoScoreDto {
        /** 预估得分，如 11 */
        private Integer score;
        /** 该模式满分，free=15 exam=25 */
        @JsonProperty("max_score")
        private Integer maxScore;
        /** 所属档次，如"良好" */
        private String band;

        public GaokaoScoreDto() {
        }

        public GaokaoScoreDto(Integer score, Integer maxScore, String band) {
            this.score = score;
            this.maxScore = maxScore;
            this.band = band;
        }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public Integer getMaxScore() { return maxScore; }
        public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
        public String getBand() { return band; }
        public void setBand(String band) { this.band = band; }
    }

    /** 各维度分数 */
    public static class ScoreDto {
        private Integer overall;
        private Integer task;
        private Integer coherence;
        private Integer lexical;
        private Integer grammar;

        public ScoreDto() {
        }

        public Integer getOverall() {
            return overall;
        }

        public void setOverall(Integer overall) {
            this.overall = overall;
        }

        public Integer getTask() {
            return task;
        }

        public void setTask(Integer task) {
            this.task = task;
        }

        public Integer getCoherence() {
            return coherence;
        }

        public void setCoherence(Integer coherence) {
            this.coherence = coherence;
        }

        public Integer getLexical() {
            return lexical;
        }

        public void setLexical(Integer lexical) {
            this.lexical = lexical;
        }

        public Integer getGrammar() {
            return grammar;
        }

        public void setGrammar(Integer grammar) {
            this.grammar = grammar;
        }
    }

    /** 单条错误 */
    public static class ErrorDto {
        private String id;
        /** spelling|morphology|subject_verb|tense|article|preposition|collocation|syntax|word_choice|part_of_speech|punctuation|logic */
        private String type;
        /** error = 客观语言错误, suggestion = 可改进建议 */
        private String category;
        /** minor | major */
        private String severity;
        private SpanDto span;
        /** 原文片段（用于前端高亮） */
        private String original;
        /** 修改建议 */
        private String suggestion;
        /** 错误原因（中文说明） */
        private String reason;
        /** 语言分类，如 Articles, Prepositions, Singular-Plural nouns */
        @JsonProperty("lang_category")
        private String langCategory;
        /** 多个修改建议（第一个同 suggestion） */
        private List<String> alternatives;
        /** 来源引擎：lt | sapling | trinka | textgears | gpt */
        private String engine;

        public ErrorDto() {
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public SpanDto getSpan() { return span; }
        public void setSpan(SpanDto span) { this.span = span; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getLangCategory() { return langCategory; }
        public void setLangCategory(String langCategory) { this.langCategory = langCategory; }
        public List<String> getAlternatives() { return alternatives; }
        public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }
        public String getEngine() { return engine; }
        public void setEngine(String engine) { this.engine = engine; }
    }

    public static class AnalysisDto {
        /** 从原文直接引用的证据句（兼容旧格式） */
        private String quote;
        @JsonProperty("strength_quote")
        private String strengthQuote;
        @JsonProperty("weakness_quote")
        private String weaknessQuote;
        private String strength;
        private String weakness;
        private String suggestion;

        public AnalysisDto() {
        }

        public AnalysisDto(String strength, String weakness, String suggestion) {
            this.strength = strength;
            this.weakness = weakness;
            this.suggestion = suggestion;
        }

        public String getQuote() { return quote; }
        public void setQuote(String quote) { this.quote = quote; }
        public String getStrengthQuote() { return strengthQuote; }
        public void setStrengthQuote(String strengthQuote) { this.strengthQuote = strengthQuote; }
        public String getWeaknessQuote() { return weaknessQuote; }
        public void setWeaknessQuote(String weaknessQuote) { this.weaknessQuote = weaknessQuote; }
        public String getStrength() { return strength; }
        public void setStrength(String strength) { this.strength = strength; }
        public String getWeakness() { return weakness; }
        public void setWeakness(String weakness) { this.weakness = weakness; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    /** 与上次历史成绩对比 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImprovementDto {
        @JsonProperty("previous_score")
        private Integer previousScore;
        @JsonProperty("current_score")
        private Integer currentScore;
        private Integer delta;
        private String message;

        public ImprovementDto() {}

        public ImprovementDto(Integer previousScore, Integer currentScore, Integer delta, String message) {
            this.previousScore = previousScore;
            this.currentScore = currentScore;
            this.delta = delta;
            this.message = message;
        }

        public Integer getPreviousScore() { return previousScore; }
        public void setPreviousScore(Integer previousScore) { this.previousScore = previousScore; }
        public Integer getCurrentScore() { return currentScore; }
        public void setCurrentScore(Integer currentScore) { this.currentScore = currentScore; }
        public Integer getDelta() { return delta; }
        public void setDelta(Integer delta) { this.delta = delta; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /** 优先改进建议 */
    public static class PriorityFocusDto {
        private String dimension;
        private String reason;
        @JsonProperty("action_item")
        private String actionItem;

        public PriorityFocusDto() {}

        public PriorityFocusDto(String dimension, String reason, String actionItem) {
            this.dimension = dimension;
            this.reason = reason;
            this.actionItem = actionItem;
        }

        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getActionItem() { return actionItem; }
        public void setActionItem(String actionItem) { this.actionItem = actionItem; }
    }

    /** 字符区间 */
    public static class SpanDto {
        private Integer start;
        private Integer end;

        public SpanDto() {
        }

        public SpanDto(Integer start, Integer end) {
            this.start = start;
            this.end = end;
        }

        public Integer getStart() {
            return start;
        }

        public void setStart(Integer start) {
            this.start = start;
        }

        public Integer getEnd() {
            return end;
        }

        public void setEnd(Integer end) {
            this.end = end;
        }
    }
}
