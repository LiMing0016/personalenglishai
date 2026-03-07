package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.dto.AiGenerateRequest;
import com.personalenglishai.backend.dto.AiGenerateResponse;
import com.personalenglishai.backend.service.AiGenerateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 生成控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiGenerateService aiGenerateService;

    public AiController(AiGenerateService aiGenerateService) {
        this.aiGenerateService = aiGenerateService;
    }

    /**
     * AI 生成接口
     * POST /api/ai/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<AiGenerateResponse> generate(@RequestBody AiGenerateRequest request,
                                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String content = aiGenerateService.generate(userId, request.getPrompt());
        return ResponseEntity.ok(new AiGenerateResponse(content));
    }
}
