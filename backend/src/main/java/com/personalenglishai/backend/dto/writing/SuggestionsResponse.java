package com.personalenglishai.backend.dto.writing;

import java.util.List;

public class SuggestionsResponse {

    /** GPT 复检发现的硬性错误（LT/Sapling 漏检的） */
    private List<ErrorItem> errors;
    /** 软性建议（搭配、用词等） */
    private List<SuggestionItem> suggestions;

    public SuggestionsResponse() {}
    public SuggestionsResponse(List<ErrorItem> errors, List<SuggestionItem> suggestions) {
        this.errors = errors;
        this.suggestions = suggestions;
    }

    public List<ErrorItem> getErrors() { return errors; }
    public void setErrors(List<ErrorItem> errors) { this.errors = errors; }
    public List<SuggestionItem> getSuggestions() { return suggestions; }
    public void setSuggestions(List<SuggestionItem> suggestions) { this.suggestions = suggestions; }

    /** GPT 复检的硬性错误，与 WritingEvaluateResponse.ErrorDto 结构对齐 */
    public static class ErrorItem {
        private String id;
        private String type;
        private String severity;
        private String original;
        private String suggestion;
        private String reason;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class SuggestionItem {
        private String id;
        private String type;
        private String original;
        private String suggestion;
        private String reason;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
