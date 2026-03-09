package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.TranslateRequest;
import com.personalenglishai.backend.dto.writing.TranslateResponse;

public interface WritingTranslateService {
    TranslateResponse translate(TranslateRequest request);
}
