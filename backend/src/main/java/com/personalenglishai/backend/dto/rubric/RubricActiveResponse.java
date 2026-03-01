package com.personalenglishai.backend.dto.rubric;

import java.util.ArrayList;
import java.util.List;

public class RubricActiveResponse {

    private String rubricKey;
    private String mode;
    private List<DimensionDto> dimensions = new ArrayList<>();

    public String getRubricKey() {
        return rubricKey;
    }

    public void setRubricKey(String rubricKey) {
        this.rubricKey = rubricKey;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<DimensionDto> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<DimensionDto> dimensions) {
        this.dimensions = dimensions;
    }

    public static class DimensionDto {
        private String dimensionKey;
        private String displayName;
        private List<LevelDto> levels = new ArrayList<>();

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

        public List<LevelDto> getLevels() {
            return levels;
        }

        public void setLevels(List<LevelDto> levels) {
            this.levels = levels;
        }
    }

    public static class LevelDto {
        private String level;
        private Integer score;
        private String criteria;

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getCriteria() {
            return criteria;
        }

        public void setCriteria(String criteria) {
            this.criteria = criteria;
        }
    }
}

