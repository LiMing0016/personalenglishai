package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;

public interface WritingExamPromptService {
    GenerateExamPromptResponse generate(GenerateExamPromptRequest request);
}
