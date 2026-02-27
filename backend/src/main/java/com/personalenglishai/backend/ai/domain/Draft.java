package com.personalenglishai.backend.ai.domain;

/**
 * 草稿：id + content，供 Handler 使用
 */
public class Draft {

    private String id;
    private String content;

    public Draft() {
    }

    public Draft(String id, String content) {
        this.id = id;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
