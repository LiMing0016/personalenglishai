package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnResponse;

public interface WritingExamDialogueService {

    GenerateExamDialogueTurnResponse generateTurn(Long userId, GenerateExamDialogueTurnRequest request);
}
