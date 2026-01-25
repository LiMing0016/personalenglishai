package com.personalenglishai.backend.dto;

/**
 * 更新学段请求
 */
public class UpdateStageRequest {
    private String studyStage; // 学段，可为空

    public UpdateStageRequest() {
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }
}


