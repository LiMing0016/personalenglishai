package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingModelEssayResponse {

    private String rubricKey;
    private String mode;
    private String stage;
    private String topicContent;
    private String taskPrompt;
    private ModelEssayCard excellentEssay;
    private ModelEssayCard perfectEssay;

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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getTopicContent() {
        return topicContent;
    }

    public void setTopicContent(String topicContent) {
        this.topicContent = topicContent;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public void setTaskPrompt(String taskPrompt) {
        this.taskPrompt = taskPrompt;
    }

    public ModelEssayCard getExcellentEssay() {
        return excellentEssay;
    }

    public void setExcellentEssay(ModelEssayCard excellentEssay) {
        this.excellentEssay = excellentEssay;
    }

    public ModelEssayCard getPerfectEssay() {
        return perfectEssay;
    }

    public void setPerfectEssay(ModelEssayCard perfectEssay) {
        this.perfectEssay = perfectEssay;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelEssayCard {
        private String label;
        private String essay;
        private String summary;
        private List<String> highScoreReasons = new ArrayList<>();
        private List<String> improvementGuidance = new ArrayList<>();

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getEssay() {
            return essay;
        }

        public void setEssay(String essay) {
            this.essay = essay;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public List<String> getHighScoreReasons() {
            return highScoreReasons;
        }

        public void setHighScoreReasons(List<String> highScoreReasons) {
            this.highScoreReasons = highScoreReasons;
        }

        public List<String> getImprovementGuidance() {
            return improvementGuidance;
        }

        public void setImprovementGuidance(List<String> improvementGuidance) {
            this.improvementGuidance = improvementGuidance;
        }
    }
}
