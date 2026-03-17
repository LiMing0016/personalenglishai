package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingModelEssayRequest;
import com.personalenglishai.backend.dto.writing.WritingModelEssayResponse;

public interface WritingModelEssayService {
    WritingModelEssayResponse generate(WritingModelEssayRequest request);
}
