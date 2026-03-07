package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditTopicResponse {

    /** complete: 信息完整 | need_more_info: 缺失信息 | invalid: 无效输入 */
    private String status;

    private String topic;
    private String genre;
    private String wordRange;

    /** 写作要求/要点 */
    private String requirements;

    /** AI 给用户的提示信息 */
    private String message;

    public AuditTopicResponse() {}

    public static AuditTopicResponse complete(String topic, String genre, String wordRange, String requirements) {
        var r = new AuditTopicResponse();
        r.status = "complete";
        r.topic = topic;
        r.genre = genre;
        r.wordRange = wordRange;
        r.requirements = requirements;
        return r;
    }

    public static AuditTopicResponse needMoreInfo(String topic, String genre, String wordRange, String requirements, String message) {
        var r = new AuditTopicResponse();
        r.status = "need_more_info";
        r.topic = topic;
        r.genre = genre;
        r.wordRange = wordRange;
        r.requirements = requirements;
        r.message = message;
        return r;
    }

    public static AuditTopicResponse invalid(String message) {
        var r = new AuditTopicResponse();
        r.status = "invalid";
        r.message = message;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getWordRange() { return wordRange; }
    public void setWordRange(String wordRange) { this.wordRange = wordRange; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
