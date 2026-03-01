package com.personalenglishai.backend.dto.rubric;

public class RubricRuleRow {

    private String rubricKey;
    private String dimensionKey;
    private String displayName;
    private Integer sortOrder;
    private String level;
    private String criteria;

    public String getRubricKey() {
        return rubricKey;
    }

    public void setRubricKey(String rubricKey) {
        this.rubricKey = rubricKey;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }

    public void setDimensionKey(String dimensionKey) {
        this.dimensionKey = dimensionKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getCriteria() {
        return criteria;
    }

    public void setCriteria(String criteria) {
        this.criteria = criteria;
    }
}
