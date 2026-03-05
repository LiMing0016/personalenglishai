package com.personalenglishai.backend.controller.dto;

public class UserStatsResponse {
    private long totalEssays;
    private Double averageScore;
    private Integer bestScore;
    private long studyDays;
    private String memberSince;

    public long getTotalEssays() { return totalEssays; }
    public void setTotalEssays(long totalEssays) { this.totalEssays = totalEssays; }
    public Double getAverageScore() { return averageScore; }
    public void setAverageScore(Double averageScore) { this.averageScore = averageScore; }
    public Integer getBestScore() { return bestScore; }
    public void setBestScore(Integer bestScore) { this.bestScore = bestScore; }
    public long getStudyDays() { return studyDays; }
    public void setStudyDays(long studyDays) { this.studyDays = studyDays; }
    public String getMemberSince() { return memberSince; }
    public void setMemberSince(String memberSince) { this.memberSince = memberSince; }
}
