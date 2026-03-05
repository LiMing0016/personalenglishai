package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class EvaluationHistoryResponse {

    private List<Item> items;
    private long total;

    public EvaluationHistoryResponse(List<Item> items, long total) {
        this.items = items;
        this.total = total;
    }

    public List<Item> getItems() { return items; }
    public long getTotal() { return total; }

    public static class Item {
        private Long id;
        private String mode;
        @JsonProperty("gaokao_score")
        private Integer gaokaoScore;
        @JsonProperty("max_score")
        private Integer maxScore;
        private String band;
        @JsonProperty("overall_score")
        private Integer overallScore;
        @JsonProperty("essay_preview")
        private String essayPreview;
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
        private boolean favorited;

        public Item(Long id, String mode, Integer gaokaoScore, Integer maxScore,
                    String band, Integer overallScore, String essayPreview, LocalDateTime createdAt,
                    boolean favorited) {
            this.id = id;
            this.mode = mode;
            this.gaokaoScore = gaokaoScore;
            this.maxScore = maxScore;
            this.band = band;
            this.overallScore = overallScore;
            this.essayPreview = essayPreview;
            this.createdAt = createdAt;
            this.favorited = favorited;
        }

        public Long getId() { return id; }
        public String getMode() { return mode; }
        public Integer getGaokaoScore() { return gaokaoScore; }
        public Integer getMaxScore() { return maxScore; }
        public String getBand() { return band; }
        public Integer getOverallScore() { return overallScore; }
        public String getEssayPreview() { return essayPreview; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public boolean isFavorited() { return favorited; }
    }
}
