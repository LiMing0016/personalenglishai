package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;

public class GrammarSuppressRequest {

    @NotBlank(message = "docId must not be blank")
    private String docId;

    @NotBlank(message = "original must not be blank")
    private String original;

    private String suggestion;

    private String ruleType;

    /** The sentence containing the error, used for context hash. */
    private String context;

    /** Source engine: lt | sapling | trinka | textgears | gpt */
    private String engine;

    /** "dismiss" or "fix" */
    private String action = "dismiss";

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
