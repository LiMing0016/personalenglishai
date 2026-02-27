package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.dto.writing.WritingChatRequest;
import com.personalenglishai.backend.dto.writing.WritingChatResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 写作接口：evaluate（评分）、chat（改写指令，先 mock 后接 GPT）
 */
@RestController
@RequestMapping("/api/writing")
public class WritingController {

    private final WritingEvaluateService writingEvaluateService;
    private final WritingChatService writingChatService;

    public WritingController(WritingEvaluateService writingEvaluateService,
                             WritingChatService writingChatService) {
        this.writingEvaluateService = writingEvaluateService;
        this.writingChatService = writingChatService;
    }

    /**
     * 提交作文进行评分与错误检测
     * Request: essay (required), aiHint (optional), mode (default free), lang (default en)
     * Response: requestId, score, summary, errors
     */
    @PostMapping("/evaluate")
    public ResponseEntity<WritingEvaluateResponse> evaluate(
            @Valid @RequestBody WritingEvaluateRequest request) {
        if (request.getMode() == null || request.getMode().isBlank()) {
            request.setMode("free");
        }
        if (request.getLang() == null || request.getLang().isBlank()) {
            request.setLang("en");
        }
        WritingEvaluateResponse response = writingEvaluateService.evaluate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * AI 对话/改写：提交改进需求，返回 assistantMessage + rewrite.fullText
     * Request: essay (required), instruction (required), lang, mode, aiHint (optional)
     * Response: requestId, assistantMessage, rewrite: { fullText, summary? }
     */
    @PostMapping("/chat")
    public ResponseEntity<WritingChatResponse> chat(
            @Valid @RequestBody WritingChatRequest request) {
        if (request.getLang() == null || request.getLang().isBlank()) {
            request.setLang("en");
        }
        if (request.getMode() == null || request.getMode().isBlank()) {
            request.setMode("free");
        }
        WritingChatResponse response = writingChatService.chat(request);
        return ResponseEntity.ok(response);
    }
}
