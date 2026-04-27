package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.PromptSheetChatRequest;
import com.personalenglishai.backend.dto.writing.PromptSheetChatResponse;

public interface PromptSheetChatService {
    PromptSheetChatResponse chat(PromptSheetChatRequest request);
}
