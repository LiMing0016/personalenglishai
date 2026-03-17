package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;

import java.util.List;

public interface GrammarCheckService {

    List<WritingEvaluateResponse.ErrorDto> check(String text);

    default List<WritingEvaluateResponse.ErrorDto> check(String text, String trinkaMode) {
        return check(text);
    }
}
