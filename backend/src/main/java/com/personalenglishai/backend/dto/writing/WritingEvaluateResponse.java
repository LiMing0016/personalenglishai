package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * POST /api/writing/evaluate 响应体（固定结构）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingEvaluateResponse {

    private String requestId;
    private ScoreDto score;
    private String summary;
    private List<ErrorDto> errors;

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
        /** grammar | spelling | word_choice | coherence | punctuation */
        private String type;
        /** minor | major */
        private String severity;
        private SpanDto span;
        private String suggestion;

        public ErrorDto() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public SpanDto getSpan() {
            return span;
        }

        public void setSpan(SpanDto span) {
            this.span = span;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
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
