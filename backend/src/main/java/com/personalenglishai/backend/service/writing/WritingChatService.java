package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingChatRequest;
import com.personalenglishai.backend.dto.writing.WritingChatResponse;

/**
 * 写作 AI 对话/改写：先 mock，后接 GPT
 */
public interface WritingChatService {

    WritingChatResponse chat(WritingChatRequest request);
}
