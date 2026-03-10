package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.writing.EvaluationDetailResponse;
import com.personalenglishai.backend.dto.writing.EvaluationHistoryResponse;
import com.personalenglishai.backend.dto.writing.WritingChatRequest;
import com.personalenglishai.backend.dto.writing.WritingChatResponse;
import com.personalenglishai.backend.dto.writing.PolishEssayRequest;
import com.personalenglishai.backend.dto.writing.PolishEssayResponse;
import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;
import com.personalenglishai.backend.dto.writing.WritingTemplateRequest;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse;
import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import com.personalenglishai.backend.dto.writing.RecognizeTopicImageRequest;
import com.personalenglishai.backend.dto.writing.RecognizeTopicImageResponse;
import com.personalenglishai.backend.dto.writing.GrammarCheckRequest;
import com.personalenglishai.backend.dto.writing.GrammarCheckResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.EssayFavoriteMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.dto.writing.SuggestionsRequest;
import com.personalenglishai.backend.dto.writing.SuggestionsResponse;
import com.personalenglishai.backend.service.writing.AuditTopicService;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import com.personalenglishai.backend.service.writing.WritingTranslateService;
import com.personalenglishai.backend.service.writing.WritingTemplateService;
import com.personalenglishai.backend.service.writing.WritingMaterialService;
import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;
import com.personalenglishai.backend.service.writing.EssayPromptService;
import com.personalenglishai.backend.dto.writing.TranslateRequest;
import com.personalenglishai.backend.dto.writing.TranslateResponse;
import com.personalenglishai.backend.service.writing.impl.WritingSuggestionsService;
import com.personalenglishai.backend.dto.writing.EssayPromptResponse;
import com.personalenglishai.backend.dto.writing.EssayPromptListResponse;
import com.personalenglishai.backend.entity.EssayPrompt;
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
    private final WritingPolishService writingPolishService;
    private final WritingTranslateService writingTranslateService;
    private final WritingTemplateService writingTemplateService;
    private final WritingMaterialService writingMaterialService;
    private final GrammarCheckService grammarCheckService;
    private final WritingSuggestionsService writingSuggestionsService;
    private final AuditTopicService auditTopicService;
    private final DocumentService documentService;
    private final EssayEvaluationMapper essayEvaluationMapper;
    private final EssayFavoriteMapper essayFavoriteMapper;
    private final EssayPromptService essayPromptService;
    private final ObjectMapper objectMapper;

    public WritingController(WritingEvaluateService writingEvaluateService,
                             WritingEvaluateTaskService writingEvaluateTaskService,
                             WritingChatService writingChatService,
                             WritingPolishService writingPolishService,
                             WritingTranslateService writingTranslateService,
                             WritingTemplateService writingTemplateService,
                             WritingMaterialService writingMaterialService,
                             GrammarCheckService grammarCheckService,
                             WritingSuggestionsService writingSuggestionsService,
                             AuditTopicService auditTopicService,
                             DocumentService documentService,
                             EssayEvaluationMapper essayEvaluationMapper,
                             EssayFavoriteMapper essayFavoriteMapper,
                             EssayPromptService essayPromptService,
                             ObjectMapper objectMapper) {
        this.writingEvaluateService = writingEvaluateService;
        this.writingEvaluateTaskService = writingEvaluateTaskService;
        this.writingChatService = writingChatService;
        this.writingPolishService = writingPolishService;
        this.writingTranslateService = writingTranslateService;
        this.writingTemplateService = writingTemplateService;
        this.writingMaterialService = writingMaterialService;
        this.grammarCheckService = grammarCheckService;
        this.writingSuggestionsService = writingSuggestionsService;
        this.auditTopicService = auditTopicService;
        this.documentService = documentService;
        this.essayEvaluationMapper = essayEvaluationMapper;
        this.essayFavoriteMapper = essayFavoriteMapper;
        this.essayPromptService = essayPromptService;
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
     * 润色指定片段：按档次（basic/steady/advanced/perfect）返回润色结果
     */
    @PostMapping("/polish")
    public ResponseEntity<PolishResponse> polish(
            @Valid @RequestBody PolishRequest request,
            HttpServletRequest httpRequest) {
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        PolishResponse response = writingPolishService.polish(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 全文逐句润色：一次 GPT 调用润色所有句子
     */
    @PostMapping("/polish-essay")
    public ResponseEntity<PolishEssayResponse> polishEssay(
            @Valid @RequestBody PolishEssayRequest request,
            HttpServletRequest httpRequest) {
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        PolishEssayResponse response = writingPolishService.polishEssay(request);
        return ResponseEntity.ok(response);
    }


    /** 模板提炼：从当前作文中抽取可复用句式模板 */
    @PostMapping("/template")
    public ResponseEntity<WritingTemplateResponse> extractTemplate(
            @Valid @RequestBody WritingTemplateRequest request,
            HttpServletRequest httpRequest) {
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        WritingTemplateResponse response = writingTemplateService.extract(request);
        return ResponseEntity.ok(response);
    }

    /** 素材生成：根据题目生成写作素材包 */
    @PostMapping("/material")
    public ResponseEntity<WritingMaterialResponse> generateMaterial(
            @Valid @RequestBody WritingMaterialRequest request,
            HttpServletRequest httpRequest) {
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        WritingMaterialResponse response = writingMaterialService.generate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 翻译：全文翻译 (mode=full) 或逐句精讲 (mode=detailed)
     */
    @PostMapping("/translate")
    public ResponseEntity<TranslateResponse> translate(
            @Valid @RequestBody TranslateRequest request,
            HttpServletRequest httpRequest) {
        request.setUserId((Long) httpRequest.getAttribute("userId"));
        TranslateResponse response = writingTranslateService.translate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 轻量语法检查（LanguageTool + Sapling，不涉及 GPT）
     */
    @PostMapping("/grammar-check")
    public ResponseEntity<GrammarCheckResponse> grammarCheck(
            @Valid @RequestBody GrammarCheckRequest request) {
        var errors = grammarCheckService.check(request.getText());
        return ResponseEntity.ok(new GrammarCheckResponse(errors));
    }

    /**
     * AI 隐形错误检测（GPT 专用：搭配不当、中式英语等）
     */
    @PostMapping("/suggestions")
    public ResponseEntity<SuggestionsResponse> suggestions(
            @Valid @RequestBody SuggestionsRequest request) {
        SuggestionsResponse response = writingSuggestionsService.analyze(request.getText());
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

        // Batch-fetch favorite IDs for this user
        var favIds = new java.util.HashSet<>(essayFavoriteMapper.selectEvalIdsByUserId(userId));

        var items = records.stream().map(r -> new EvaluationHistoryResponse.Item(
                r.getId(), r.getMode(), r.getGaokaoScore(), r.getMaxScore(),
                r.getBand(), r.getOverallScore(),
                trimPreview(r.getEssayText(), 80),
                r.getCreatedAt(),
                favIds.contains(r.getId())
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

    /**
     * 收藏/取消收藏作文
     * POST /api/writing/history/{id}/favorite
     */
    @PostMapping("/history/{id}/favorite")
    public ResponseEntity<java.util.Map<String, Boolean>> toggleFavorite(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();
        boolean exists = essayFavoriteMapper.existsByUserAndEval(userId, id);
        if (exists) {
            essayFavoriteMapper.deleteByUserAndEval(userId, id);
        } else {
            essayFavoriteMapper.insert(userId, id);
        }
        return ResponseEntity.ok(java.util.Map.of("favorited", !exists));
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

    /**
     * 开始写作会话：根据题目查找/创建文档
     * POST /api/writing/start-session
     */
    @PostMapping("/start-session")
    public ResponseEntity<java.util.Map<String, Object>> startSession(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        String tenantId = String.valueOf(userId);
        String mode = body.getOrDefault("mode", "free");
        String taskPrompt = body.get("taskPrompt");
        String title = body.getOrDefault("title", "");
        boolean draft = "true".equals(body.get("draft"));

        // 题目一致时复用同一文档，避免重复创建。
        DocumentService.StartSessionResult result = documentService.findOrCreateForTopic(
                tenantId, "default", userId, title, taskPrompt, "");

        // 非草稿模式（用户点击了"开始写作"）→ 激活文档
        if (!draft) {
            Document doc = documentService.findByPublicId(tenantId, "default", result.docId);
            if (doc != null && doc.getStatus() != null && doc.getStatus() == 0) {
                documentService.activateIfDraft(doc.getId());
            }
        }

        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("docId", result.docId);
        resp.put("latestRevision", result.latestRevision);
        resp.put("isNew", result.isNew);
        resp.put("existingContent", result.existingContent);
        resp.put("initialScore", result.initialScore);
        resp.put("latestScore", result.latestScore);
        resp.put("submitCount", result.submitCount);
        resp.put("mode", mode);
        return ResponseEntity.ok(resp);
    }

    /**
     * 获取用户的写作文档列表（归档视图）
     * GET /api/writing/documents?page=0&size=10
     */
    @GetMapping("/documents")
    public ResponseEntity<java.util.Map<String, Object>> getWritingDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        String tenantId = String.valueOf(userId);
        int safeSize = Math.min(size, 50);
        int offset = page * safeSize;
        var docs = documentService.listByOwner(tenantId, "default", userId, offset, safeSize);
        long total = documentService.countByOwner(tenantId, "default", userId);

        var items = docs.stream().map(d -> {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("docId", d.getPublicId());
            item.put("title", d.getTitle());
            item.put("taskPrompt", d.getTaskPrompt());
            item.put("initialScore", d.getInitialScore());
            item.put("latestScore", d.getLatestScore());
            item.put("submitCount", d.getSubmitCount());
            item.put("status", d.getStatus());
            item.put("createdAt", d.getCreatedAt());
            item.put("updatedAt", d.getUpdatedAt());
            return item;
        }).toList();

        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("items", items);
        resp.put("total", total);
        return ResponseEntity.ok(resp);
    }

    /**
     * 获取某文档的所有评分记录
     * GET /api/writing/documents/{docId}/evaluations?page=0&size=20
     */
    @GetMapping("/documents/{docId}/evaluations")
    public ResponseEntity<java.util.Map<String, Object>> getDocumentEvaluations(
            @PathVariable String docId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        String tenantId = String.valueOf(userId);
        Document doc = documentService.findByPublicId(tenantId, "default", docId);
        if (doc == null || !doc.getOwnerUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        int safeSize = Math.min(size, 50);
        int offset = page * safeSize;
        var records = essayEvaluationMapper.selectByDocumentId(doc.getId(), offset, safeSize);
        long total = essayEvaluationMapper.countByDocumentId(doc.getId());

        var items = records.stream().map(r -> {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id", r.getId());
            item.put("overallScore", r.getOverallScore());
            item.put("gaokaoScore", r.getGaokaoScore());
            item.put("band", r.getBand());
            item.put("contentQuality", r.getContentQuality());
            item.put("taskAchievement", r.getTaskAchievement());
            item.put("structureScore", r.getStructureScore());
            item.put("vocabularyScore", r.getVocabularyScore());
            item.put("grammarScore", r.getGrammarScore());
            item.put("expressionScore", r.getExpressionScore());
            item.put("grammarErrorCount", r.getGrammarErrorCount());
            item.put("spellingErrorCount", r.getSpellingErrorCount());
            item.put("vocabularyErrorCount", r.getVocabularyErrorCount());
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).toList();

        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("items", items);
        resp.put("total", total);
        return ResponseEntity.ok(resp);
    }

    /**
     * 获取用户写作聚合统计（维度平均分 + 错误分布）
     * GET /api/writing/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getWritingStats(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        var stats = essayEvaluationMapper.selectAggregatedStatsByUserId(userId);
        if (stats == null) {
            stats = new java.util.LinkedHashMap<>();
        }
        return ResponseEntity.ok(stats);
    }

    /** 题目智能解析（千问） */
    @PostMapping("/audit-topic")
    public ResponseEntity<AuditTopicResponse> auditTopic(
            @Valid @RequestBody AuditTopicRequest request) {
        AuditTopicResponse response = auditTopicService.audit(request);
        return ResponseEntity.ok(response);
    }

    /** 图片题目识别（千问 VL） */
    @PostMapping("/recognize-topic-image")
    public ResponseEntity<RecognizeTopicImageResponse> recognizeTopicImage(
            @Valid @RequestBody RecognizeTopicImageRequest request) {
        RecognizeTopicImageResponse response = auditTopicService.recognizeImage(request.getImageBase64());
        return ResponseEntity.ok(response);
    }

    /** 历年真题列表（分页 + 搜索 + 按年份筛选） */
    @GetMapping("/prompts")
    public ResponseEntity<EssayPromptListResponse> listPrompts(
            @RequestParam(defaultValue = "2") Integer stageId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;

        java.util.List<EssayPrompt> items = essayPromptService.search(stageId, keyword, year, page, size);
        long total = essayPromptService.countSearch(stageId, keyword, year);
        java.util.List<Integer> years = essayPromptService.getAvailableYears(stageId);

        EssayPromptListResponse response = new EssayPromptListResponse();
        response.setItems(items.stream().map(this::toPromptResponse).collect(java.util.stream.Collectors.toList()));
        response.setTotal(total);
        response.setYears(years);
        return ResponseEntity.ok(response);
    }

    private EssayPromptResponse toPromptResponse(EssayPrompt entity) {
        EssayPromptResponse dto = new EssayPromptResponse();
        dto.setId(entity.getId());
        dto.setPaper(entity.getPaper());
        dto.setTitle(entity.getTitle());
        dto.setPromptText(entity.getPromptText());
        dto.setExamYear(entity.getExamYear());
        dto.setImageUrl(entity.getImageUrl());
        dto.setMaterialText(entity.getMaterialText());
        dto.setSource(entity.getSource());
        return dto;
    }
}


