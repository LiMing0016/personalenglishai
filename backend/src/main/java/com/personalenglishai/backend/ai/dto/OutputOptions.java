package com.personalenglishai.backend.ai.dto;

/**
 * 输出选项：format (diff | markdown | json)、stream
 */
public class OutputOptions {

    private String format;
    private Boolean stream;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}
