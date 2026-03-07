package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuditTopicRequest {

    @NotBlank(message = "题目内容不能为空")
    @Size(max = 2000, message = "题目内容过长")
    private String topic;

    /** 用户选择的体裁，可为空 */
    private String genre;

    /** 用户选择的字数范围，可为空 */
    private String wordRange;

    /** 写作要求/要点，可为空 */
    private String requirements;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getWordRange() { return wordRange; }
    public void setWordRange(String wordRange) { this.wordRange = wordRange; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
}
