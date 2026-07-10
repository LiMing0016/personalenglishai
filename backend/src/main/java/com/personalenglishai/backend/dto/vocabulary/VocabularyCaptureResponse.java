package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyCaptureResponse(List<Item> items) {
    public record Item(String term, String cardUid, String action, String status) {
    }
}
