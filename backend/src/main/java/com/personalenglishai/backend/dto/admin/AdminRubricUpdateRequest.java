package com.personalenglishai.backend.dto.admin;

import java.util.List;

public class AdminRubricUpdateRequest {
    private String rubricKey;
    private String stage;
    private List<DimensionInput> dimensions;

    public String getRubricKey() { return rubricKey; }
    public void setRubricKey(String rubricKey) { this.rubricKey = rubricKey; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public List<DimensionInput> getDimensions() { return dimensions; }
    public void setDimensions(List<DimensionInput> dimensions) { this.dimensions = dimensions; }

    public static class DimensionInput {
        private String mode;
        private String dimensionKey;
        private String displayName;
        private Integer sortOrder;
        private List<LevelInput> levels;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getDimensionKey() { return dimensionKey; }
        public void setDimensionKey(String dimensionKey) { this.dimensionKey = dimensionKey; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public List<LevelInput> getLevels() { return levels; }
        public void setLevels(List<LevelInput> levels) { this.levels = levels; }
    }

    public static class LevelInput {
        private String level;
        private Integer score;
        private String criteria;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getCriteria() { return criteria; }
        public void setCriteria(String criteria) { this.criteria = criteria; }
    }
}
