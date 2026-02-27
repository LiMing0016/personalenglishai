package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingChatRequest;
import com.personalenglishai.backend.dto.writing.WritingChatResponse;
import com.personalenglishai.backend.service.writing.WritingChatService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 改写接口 mock：返回固定 assistantMessage + 基于 essay 的简单改写占位
 */
@Service
public class WritingChatMockService implements WritingChatService {

    @Override
    public WritingChatResponse chat(WritingChatRequest request) {
        String requestId = "chat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String assistantMessage = "已根据你的改进需求完成改写，请查看下方改写结果。点击「应用到作文」可替换左侧正文。（mock）";
        String fullText = request.getEssay() == null ? "" : request.getEssay().trim() + "\n\n[改写后的内容 - mock。后续接入 GPT 将返回真实改写全文。]";

        WritingChatResponse.RewriteDto rewrite = new WritingChatResponse.RewriteDto();
        rewrite.setFullText(fullText);
        rewrite.setSummary("基于当前作文的改写预览");

        String resultText = request.getSelectedText() != null && !request.getSelectedText().isEmpty()
            ? "[mock 改写: " + request.getSelectedText().trim() + " → 已根据「" + (request.getInstruction() != null ? request.getInstruction() : "") + "」处理]"
            : null;

        WritingChatResponse response = new WritingChatResponse();
        response.setRequestId(requestId);
        response.setAssistantMessage(assistantMessage);
        response.setRewrite(rewrite);
        response.setResultText(resultText);
        return response;
    }
}
