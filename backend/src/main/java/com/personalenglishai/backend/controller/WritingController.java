package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.writing.EvaluationDetailResponse;
import com.personalenglishai.backend.dto.writing.EvaluationHistoryResponse;
import com.personalenglishai.backend.dto.writing.WritingChatRequest;
import com.personalenglishai.backend.dto.writing.WritingChatResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 写作接口：evaluate（评分）、chat（改写指令，先 mock 后接 GPT）
 */
@RestController
@RequestMapping("/api/writing")
public class WritingController {

    private final WritingEvaluateService writingEvaluateService;
    private final WritingEvaluateTaskService writingEvaluateTaskService;
    private final WritingChatService writingChatService;
    private final EssayEvaluationMapper essayEvaluationMapper;
    private final ObjectMapper objectMapper;

    public WritingController(WritingEvaluateService writingEvaluateService,
                             WritingEvaluateTaskService writingEvaluateTaskService,
                             WritingChatService writingChatService,
                             EssayEvaluationMapper essayEvaluationMapper,
                             ObjectMapper objectMapper) {
        this.writingEvaluateService = writingEvaluateService;
        this.writingEvaluateTaskService = writingEvaluateTaskService;
        this.writingChatService = writingChatService;
        this.essayEvaluationMapper = essayEvaluationMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交作文进行评分与错误检测
     * Request: essay (required), aiHint (optional), mode (default free), lang (default en)
     * Response: requestId, score, summary, errors
     */
    @PostMapping("/evaluate")
    public ResponseEntity<WritingEvaluateResponse> evaluate(
            @Valid @RequestBody WritingEvaluateRequest request,
            HttpServletRequest httpRequest) {
        normalizeEvaluateRequest(request);
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        WritingEvaluateResponse response = writingEvaluateService.evaluate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/evaluate/submit")
    public ResponseEntity<WritingEvaluateTaskResponse> submitEvaluate(
            @Valid @RequestBody WritingEvaluateRequest request,
            HttpServletRequest httpRequest) {
        normalizeEvaluateRequest(request);
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        WritingEvaluateTaskResponse response = writingEvaluateTaskService.submit(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/evaluate/tasks/{requestId}")
    public ResponseEntity<WritingEvaluateTaskResponse> getEvaluateTask(
            @PathVariable String requestId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        WritingEvaluateTaskResponse response = writingEvaluateTaskService.getTask(requestId);
        if (response == null || !userId.equals(response.getUserId())) {
            return ResponseEntity.notFound().build();
        }
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

    /**
     * 获取当前用户的评分历史列表（轻量，不含完整 JSON）
     * GET /api/writing/history?page=0&size=10
     */
    @GetMapping("/history")
    public ResponseEntity<EvaluationHistoryResponse> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        int safeSize = Math.min(size, 50);
        int offset = page * safeSize;
        var records = essayEvaluationMapper.selectByUserId(userId, offset, safeSize);
        long total = essayEvaluationMapper.countByUserId(userId);

        var items = records.stream().map(r -> new EvaluationHistoryResponse.Item(
                r.getId(), r.getMode(), r.getGaokaoScore(), r.getMaxScore(),
                r.getBand(), r.getOverallScore(),
                trimPreview(r.getEssayText(), 80),
                r.getCreatedAt()
        )).toList();

        return ResponseEntity.ok(new EvaluationHistoryResponse(items, total));
    }

    /**
     * 获取单条历史记录完整评分结果
     * GET /api/writing/history/{id}
     */
    @GetMapping("/history/{id}")
    public ResponseEntity<EvaluationDetailResponse> getHistoryDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        EssayEvaluation record = essayEvaluationMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            WritingEvaluateResponse result =
                    objectMapper.readValue(record.getResultJson(), WritingEvaluateResponse.class);
            return ResponseEntity.ok(new EvaluationDetailResponse(record.getEssayText(), result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String trimPreview(String text, int maxLen) {
        if (text == null) return "";
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "…";
    }

    private static final int MIN_WORDS = 20;
    private static final int MAX_WORDS = 500;

    private void normalizeEvaluateRequest(WritingEvaluateRequest request) {
        if (request.getMode() == null || request.getMode().isBlank()) {
            request.setMode("free");
        } else {
            String mode = request.getMode().trim().toLowerCase();
            request.setMode("exam".equals(mode) ? "exam" : "free");
        }
        if (request.getLang() == null || request.getLang().isBlank()) {
            request.setLang("en");
        }
        validateEssayLength(request.getEssay());
    }

    private void validateEssayLength(String essay) {
        if (essay == null || essay.isBlank()) return; // @NotBlank handles null
        int wordCount = essay.trim().split("\\s+").length;
        if (wordCount < MIN_WORDS) {
            throw new BizException(ErrorCode.ESSAY_TOO_SHORT,
                    "作文太短（" + wordCount + " 词），至少需要 " + MIN_WORDS + " 个词");
        }
        if (wordCount > MAX_WORDS) {
            throw new BizException(ErrorCode.ESSAY_TOO_LONG,
                    "作文太长（" + wordCount + " 词），最多支持 " + MAX_WORDS + " 个词");
        }
    }
}
