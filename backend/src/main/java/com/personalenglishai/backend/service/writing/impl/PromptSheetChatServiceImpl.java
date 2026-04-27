package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.dto.writing.PromptSheetChatRequest;
import com.personalenglishai.backend.dto.writing.PromptSheetChatResponse;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.service.writing.PromptSheetChatService;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class PromptSheetChatServiceImpl implements PromptSheetChatService {

    private static final Logger log = LoggerFactory.getLogger(PromptSheetChatServiceImpl.class);
    private static final Duration ORCHESTRATOR_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;
    private final WritingPromptSheetAssembler promptSheetAssembler;
    private final WritingPromptSheetService writingPromptSheetService;
    private final PromptSheetChartImageService chartImageService;

    @Autowired
    public PromptSheetChatServiceImpl(
            @Value("${AI_ORCHESTRATOR_BASE_URL:http://127.0.0.1:8091}") String orchestratorBaseUrl,
            WritingPromptSheetAssembler promptSheetAssembler,
            WritingPromptSheetService writingPromptSheetService,
            PromptSheetChartImageService chartImageService) {
        this(WebClient.builder().baseUrl(normalizeBaseUrl(orchestratorBaseUrl)).build(),
                promptSheetAssembler,
                writingPromptSheetService,
                chartImageService);
    }

    PromptSheetChatServiceImpl(WebClient webClient,
                               WritingPromptSheetAssembler promptSheetAssembler,
                               WritingPromptSheetService writingPromptSheetService) {
        this(webClient, promptSheetAssembler, writingPromptSheetService,
                new PromptSheetChartImageService(java.nio.file.Path.of("uploads"), "/uploads"));
    }

    PromptSheetChatServiceImpl(WebClient webClient,
                               WritingPromptSheetAssembler promptSheetAssembler,
                               WritingPromptSheetService writingPromptSheetService,
                               PromptSheetChartImageService chartImageService) {
        this.webClient = webClient;
        this.promptSheetAssembler = promptSheetAssembler;
        this.writingPromptSheetService = writingPromptSheetService;
        this.chartImageService = chartImageService;
    }

    @Override
    public PromptSheetChatResponse chat(PromptSheetChatRequest request) {
        try {
            PromptSheetChatResponse response = webClient.post()
                    .uri("/prompt-sheet/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PromptSheetChatResponse.class)
                    .block(ORCHESTRATOR_TIMEOUT);
            if (response == null) {
                return fallbackResponse();
            }
            persistEmbeddedPromptSheet(request, response);
            return response;
        } catch (Exception e) {
            log.warn("[PROMPT-SHEET-CHAT] python orchestrator failed userId={} reason={}",
                    request.getUserId(), e.getMessage());
            return fallbackResponse();
        }
    }

    private void persistEmbeddedPromptSheet(PromptSheetChatRequest chatRequest, PromptSheetChatResponse chatResponse) {
        GenerateExamPromptResponse promptSheetResponse = chatResponse.getPromptSheet();
        if (promptSheetResponse == null || !Boolean.TRUE.equals(chatResponse.getNeedsCanvasUpdate())) {
            return;
        }
        GenerateExamPromptRequest generatedRequest = toGenerateRequest(chatRequest, promptSheetResponse);
        normalizePromptSheet(generatedRequest, promptSheetResponse);
        WritingPromptSheet promptSheet = writingPromptSheetService.createGeneratedPromptSheet(
                generatedRequest,
                promptSheetResponse
        );
        promptSheetResponse.setPromptSheetId(promptSheet.getId());
        promptSheetResponse.setPaper(promptSheet.getPaper());
    }

    private GenerateExamPromptRequest toGenerateRequest(PromptSheetChatRequest chatRequest,
                                                        GenerateExamPromptResponse promptSheetResponse) {
        GenerateExamPromptRequest request = new GenerateExamPromptRequest();
        request.setOriginalInput(firstNonBlank(chatRequest.getMessage(), chatResponseInstruction(chatRequest)));
        request.setTopic(firstNonBlank(promptSheetResponse.getTopic(), chatRequest.getCurrentTopic()));
        request.setStudyStage(chatRequest.getStudyStage());
        request.setPromptType(firstNonBlank(promptSheetResponse.getPromptType(), chatRequest.getPromptType()));
        request.setTaskType(firstNonBlank(promptSheetResponse.getTaskType(), chatRequest.getTaskType()));
        request.setGenre(firstNonBlank(promptSheetResponse.getGenre(), chatRequest.getGenre()));
        request.setWordRange(firstNonBlank(promptSheetResponse.getWordRange(), chatRequest.getWordRange()));
        request.setRequirements(firstNonBlank(promptSheetResponse.getRequirements(), chatRequest.getRequirements()));
        request.setMaxScore(promptSheetResponse.getMaxScore());
        request.setAiProvider(chatRequest.getAiProvider());
        request.setUserId(chatRequest.getUserId());
        return request;
    }

    private String chatResponseInstruction(PromptSheetChatRequest request) {
        return firstNonBlank(request.getCurrentTopic(), request.getCurrentPromptText());
    }

    private void normalizePromptSheet(GenerateExamPromptRequest request, GenerateExamPromptResponse response) {
        response.setPromptType(firstNonBlank(response.getPromptType(), request.getPromptType()));
        response.setSourceType("ai_generated");
        response.setTaskType(firstNonBlank(response.getTaskType(), firstNonBlank(request.getTaskType(), "task1")));
        response.setWordRange(firstNonBlank(response.getWordRange(), request.getWordRange()));
        applyWordRange(response);
        promptSheetAssembler.populate(request, response);
        chartImageService.attachChartImage(response);
    }

    private PromptSheetChatResponse fallbackResponse() {
        PromptSheetChatResponse response = new PromptSheetChatResponse();
        response.setReply("题单助教暂时没有响应。你可以继续补充主题、题型、字数或材料要求，我稍后再帮你整理。");
        response.setAction("chat_only");
        response.setNeedsCanvasUpdate(false);
        response.setNeedsConfirmation(false);
        return response;
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
