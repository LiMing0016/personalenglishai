package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.entity.WritingPromptSheet;

public interface WritingPromptSheetService {

    WritingPromptSheet createGeneratedPromptSheet(GenerateExamPromptRequest request, GenerateExamPromptResponse response);

    void bindDocument(Long promptSheetId, Long documentId);
}
