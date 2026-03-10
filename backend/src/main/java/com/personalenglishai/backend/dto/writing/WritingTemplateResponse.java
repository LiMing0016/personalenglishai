package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingTemplateResponse {

    private String essayType;
    private List<ParagraphTemplate> paragraphs;
    private List<String> usageTips;

    public String getEssayType() { return essayType; }
    public void setEssayType(String essayType) { this.essayType = essayType; }

    public List<ParagraphTemplate> getParagraphs() { return paragraphs; }
    public void setParagraphs(List<ParagraphTemplate> paragraphs) { this.paragraphs = paragraphs; }

    public List<String> getUsageTips() { return usageTips; }
    public void setUsageTips(List<String> usageTips) { this.usageTips = usageTips; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParagraphTemplate {
        private int paragraphIndex;
        private String function;
        private String summary;
        private List<TemplateItem> templates;
        private List<KeyExpression> keyExpressions;

        public int getParagraphIndex() { return paragraphIndex; }
        public void setParagraphIndex(int paragraphIndex) { this.paragraphIndex = paragraphIndex; }

        public String getFunction() { return function; }
        public void setFunction(String function) { this.function = function; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public List<TemplateItem> getTemplates() { return templates; }
        public void setTemplates(List<TemplateItem> templates) { this.templates = templates; }

        public List<KeyExpression> getKeyExpressions() { return keyExpressions; }
        public void setKeyExpressions(List<KeyExpression> keyExpressions) { this.keyExpressions = keyExpressions; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateItem {
        private String template;
        private Map<String, List<String>> placeholders;

        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }

        public Map<String, List<String>> getPlaceholders() { return placeholders; }
        public void setPlaceholders(Map<String, List<String>> placeholders) { this.placeholders = placeholders; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeyExpression {
        private String expression;
        private String usage;
        private List<String> usageTips;

        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }

        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }

        public List<String> getUsageTips() { return usageTips; }
        public void setUsageTips(List<String> usageTips) { this.usageTips = usageTips; }
    }
}
