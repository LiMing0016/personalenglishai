package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.service.subscription.AiUsageRecorder;
import com.personalenglishai.backend.service.writing.WritingExamPromptService;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class WritingExamPromptServiceImpl implements WritingExamPromptService {

    private static final Logger log = LoggerFactory.getLogger(WritingExamPromptServiceImpl.class);
    private static final Duration ORCHESTRATOR_TIMEOUT = Duration.ofSeconds(120);

    private final WebClient webClient;
    private final WritingPromptSheetAssembler promptSheetAssembler;
    private final WritingPromptSheetService writingPromptSheetService;
    private final PromptSheetChartImageService chartImageService;
    @Autowired(required = false)
    private AiUsageRecorder aiUsageRecorder;

    @Autowired
    public WritingExamPromptServiceImpl(
            @Value("${AI_ORCHESTRATOR_BASE_URL:http://127.0.0.1:8091}") String orchestratorBaseUrl,
            WritingPromptSheetAssembler promptSheetAssembler,
            WritingPromptSheetService writingPromptSheetService,
            PromptSheetChartImageService chartImageService) {
        this(WebClient.builder().baseUrl(normalizeBaseUrl(orchestratorBaseUrl)).build(),
                promptSheetAssembler,
                writingPromptSheetService,
                chartImageService);
    }

    WritingExamPromptServiceImpl(WebClient webClient,
                                 WritingPromptSheetAssembler promptSheetAssembler,
                                 WritingPromptSheetService writingPromptSheetService) {
        this(webClient, promptSheetAssembler, writingPromptSheetService,
                new PromptSheetChartImageService(java.nio.file.Path.of("uploads"), "/uploads"));
    }

    WritingExamPromptServiceImpl(WebClient webClient,
                                 WritingPromptSheetAssembler promptSheetAssembler,
                                 WritingPromptSheetService writingPromptSheetService,
                                 PromptSheetChartImageService chartImageService) {
        this.webClient = webClient;
        this.promptSheetAssembler = promptSheetAssembler;
        this.writingPromptSheetService = writingPromptSheetService;
        this.chartImageService = chartImageService;
    }

    @Override
    public GenerateExamPromptResponse generate(GenerateExamPromptRequest request) {
        log.info("[WRITING-EXAM-PROMPT] proxy_to_python userId={} stage={} promptType={} taskType={} originalInputLen={}",
                request.getUserId(),
                request.getStudyStage(),
                request.getPromptType(),
                request.getTaskType(),
                request.getOriginalInput() == null ? 0 : request.getOriginalInput().length());

        GenerateExamPromptResponse response = webClient.post()
                .uri("/prompt-sheet/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateExamPromptResponse.class)
                .block(ORCHESTRATOR_TIMEOUT);
        if (response == null) {
            throw new IllegalStateException("Python orchestrator returned empty prompt sheet response");
        }
        recordAgentUsage(response);

        normalizeResponse(request, response);
        WritingPromptSheet promptSheet = writingPromptSheetService.createGeneratedPromptSheet(request, response);
        response.setPromptSheetId(promptSheet.getId());
        response.setPaper(promptSheet.getPaper());
        return response;
    }

    private void recordAgentUsage(GenerateExamPromptResponse response) {
        if (aiUsageRecorder == null || response == null || response.getAgentUsage() == null) {
            return;
        }
        var usage = response.getAgentUsage();
        aiUsageRecorder.recordCurrentContext(
                usage.getProvider() == null ? "openai_agents" : usage.getProvider(),
                usage.getModel(),
                usage.getResponseId(),
                usage.getInputTokens(),
                usage.getCachedInputTokens(),
                usage.getOutputTokens(),
                usage.getReasoningTokens(),
                usage.getTotalTokens()
        );
    }

    private void normalizeResponse(GenerateExamPromptRequest request, GenerateExamPromptResponse response) {
        response.setPromptType(firstNonBlank(response.getPromptType(), firstNonBlank(request.getPromptType(), "general")));
        response.setSourceType("ai_generated");
        response.setTaskType(firstNonBlank(response.getTaskType(), firstNonBlank(request.getTaskType(), "task1")));
        if (response.getWordRange() == null) {
            response.setWordRange(trimToNull(request.getWordRange()));
        }
        if (response.getMaxScore() == null) {
            response.setMaxScore(request.getMaxScore());
        }
        if (response.getComicScenes() == null) {
            response.setComicScenes(List.of());
        }
        applyWordRange(response);
        promptSheetAssembler.populate(request, response);
        chartImageService.attachChartImage(response);
    }

    private void applyWordRange(GenerateExamPromptResponse response) {
        String wordRange = trimToNull(response.getWordRange());
        if (wordRange == null) {
            return;
        }
        String compact = wordRange.replaceAll("\\s+", "");
        java.util.regex.Matcher rangeMatch = java.util.regex.Pattern.compile("^(\\d+)\\s*[-~至]\\s*(\\d+)$").matcher(compact);
        if (rangeMatch.find()) {
            response.setMinWords(Integer.parseInt(rangeMatch.group(1)));
            response.setRecommendedMaxWords(Integer.parseInt(rangeMatch.group(2)));
            return;
        }
        java.util.regex.Matcher minMatch = java.util.regex.Pattern.compile("^(\\d+)\\+$").matcher(compact);
        if (minMatch.find()) {
            response.setMinWords(Integer.parseInt(minMatch.group(1)));
            return;
        }
        java.util.regex.Matcher singleMatch = java.util.regex.Pattern.compile("^(\\d+)$").matcher(compact);
        if (singleMatch.find()) {
            int value = Integer.parseInt(singleMatch.group(1));
            response.setMinWords(value);
            response.setRecommendedMaxWords(value);
        }
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "http://127.0.0.1:8091" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalized = trimToNull(primary);
        return normalized != null ? normalized : trimToNull(fallback);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }
}
