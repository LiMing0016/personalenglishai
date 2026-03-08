package com.personalenglishai.backend.dto.writing;

import java.util.List;

public class EssayPromptListResponse {
    private List<EssayPromptResponse> items;
    private long total;
    private List<Integer> years;

    public List<EssayPromptResponse> getItems() { return items; }
    public void setItems(List<EssayPromptResponse> items) { this.items = items; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public List<Integer> getYears() { return years; }
    public void setYears(List<Integer> years) { this.years = years; }
}
