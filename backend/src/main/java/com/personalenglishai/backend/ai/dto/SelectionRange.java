package com.personalenglishai.backend.ai.dto;

/**
 * 选区范围，用于 contextRefs.selectionRange
 */
public class SelectionRange {

    private Integer start;
    private Integer end;

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getEnd() {
        return end;
    }

    public void setEnd(Integer end) {
        this.end = end;
    }
}
