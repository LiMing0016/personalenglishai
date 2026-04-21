package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.entity.EssayPrompt;
import com.personalenglishai.backend.entity.WritingPromptSheet;
import com.personalenglishai.backend.service.writing.EssayPromptService;
import com.personalenglishai.backend.service.writing.WritingExamPromptService;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WritingExamPromptServiceImpl implements WritingExamPromptService {

    private static final Logger log = LoggerFactory.getLogger(WritingExamPromptServiceImpl.class);
    private static final String SYSTEM_PROMPT_PATH = "prompts/exam-sheet/exam-prompt-system.md";
    private static final String USER_PROMPT_PATH = "prompts/exam-sheet/exam-prompt-user.md";
    private static final String IMAGE_PROMPT_PATH = "prompts/exam-sheet/exam-prompt-image.md";

    private final OpenAiClient openAiClient;
    private final EssayPromptService essayPromptService;
    private final ObjectMapper objectMapper;
    private final WritingPromptSheetAssembler promptSheetAssembler;
    private final WritingPromptSheetService writingPromptSheetService;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;
    private final String imagePromptTemplate;

    public WritingExamPromptServiceImpl(OpenAiClient openAiClient,
                                        EssayPromptService essayPromptService,
                                        ObjectMapper objectMapper,
                                        WritingPromptSheetAssembler promptSheetAssembler,
                                        WritingPromptSheetService writingPromptSheetService) {
        this.openAiClient = openAiClient;
        this.essayPromptService = essayPromptService;
        this.objectMapper = objectMapper;
        this.promptSheetAssembler = promptSheetAssembler;
        this.writingPromptSheetService = writingPromptSheetService;
        this.systemPromptTemplate = loadPromptTemplate(SYSTEM_PROMPT_PATH, defaultSystemPrompt());
        this.userPromptTemplate = loadPromptTemplate(USER_PROMPT_PATH, defaultUserPrompt());
        this.imagePromptTemplate = loadPromptTemplate(IMAGE_PROMPT_PATH, defaultImagePrompt());
    }

    @Override
    public GenerateExamPromptResponse generate(GenerateExamPromptRequest request) {
        String traceId = "exam-prompt-" + UUID.randomUUID().toString().substring(0, 8);
        String stage = normalizeStage(request.getStudyStage());
        String promptType = normalizePromptType(request.getPromptType());
        List<EssayPrompt> references = loadReferences(stage);

        log.info("[WRITING-EXAM-PROMPT] traceId={} userId={} stage={} promptType={} originalInputLen={} topicLen={}",
                traceId,
                request.getUserId(),
                stage,
                promptType,
                request.getOriginalInput() == null ? 0 : request.getOriginalInput().length(),
                request.getTopic() == null ? 0 : request.getTopic().length());

        String raw = openAiClient.callWithProvider(
                request.getAiProvider(),
                buildSystemPrompt(stage),
                buildUserPrompt(request, stage, promptType, references),
                traceId,
                0.8,
                4096
        );

        GenerateExamPromptResponse response = parseResponse(raw, request, promptType);
        response.setPromptType(promptType);
        response.setSourceType("ai_generated");
        response.setTaskType(firstNonBlank(response.getTaskType(), trimToNull(request.getTaskType())));
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
        attachGeneratedVisual(request, response, traceId);
        WritingPromptSheet promptSheet = writingPromptSheetService.createGeneratedPromptSheet(request, response);
        response.setPromptSheetId(promptSheet.getId());
        response.setPaper(promptSheet.getPaper());
        return response;
    }

    private String buildSystemPrompt(String stage) {
        String stageInstruction = "当前学段硬约束：" + stage + "。你必须仿照该学段对应的真实考试风格来组织题面、任务要求、字数习惯和表达语气，不要写成泛化的普通作文提示。";
        return systemPromptTemplate
                .replace("{{stage}}", stage)
                .trim() + "\n\n" + stageInstruction;
    }

    private String buildUserPrompt(GenerateExamPromptRequest request,
                                   String stage,
                                   String promptType,
                                   List<EssayPrompt> references) {
        return userPromptTemplate
                .replace("{{inputBlock}}", buildInputBlock(request, stage, promptType))
                .replace("{{styleReferencesBlock}}", buildStyleReferencesBlock(references))
                .trim();
    }

    private String buildInputBlock(GenerateExamPromptRequest request, String stage, String promptType) {
        List<String> lines = new ArrayList<>();
        lines.add("study_stage=" + stage);
        lines.add("study_stage_constraint=必须仿照该学段考试风格命题");
        lines.add("requested_prompt_type=" + promptType);
        lines.add("topic=" + trimToNull(request.getTopic()));
        if (trimToNull(request.getGenre()) != null) {
            lines.add("genre=" + trimToNull(request.getGenre()));
        }
        if (trimToNull(request.getWordRange()) != null) {
            lines.add("word_range=" + trimToNull(request.getWordRange()));
        }
        if (request.getMaxScore() != null) {
            lines.add("max_score=" + request.getMaxScore());
        }
        if (trimToNull(request.getRequirements()) != null) {
            lines.add("requirements=" + trimToNull(request.getRequirements()));
        }
        return String.join("\n", lines);
    }

    private String buildStyleReferencesBlock(List<EssayPrompt> references) {
        if (references == null || references.isEmpty()) {
            return "(none)";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(4, references.size()); i++) {
            EssayPrompt prompt = references.get(i);
            StringBuilder line = new StringBuilder("- ").append(trimToNull(prompt.getPromptText()));
            if (trimToNull(prompt.getMaterialText()) != null) {
                line.append(" | material: ").append(trimToNull(prompt.getMaterialText()));
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    private GenerateExamPromptResponse parseResponse(String raw,
                                                     GenerateExamPromptRequest request,
                                                     String promptType) {
        GenerateExamPromptResponse response = emptyResponse(promptType, request);
        if (raw == null || raw.isBlank()) {
            return response;
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            response.setTopic(firstNonBlank(node.path("topic").asText(null), request.getTopic()));
            response.setPromptText(firstNonBlank(node.path("promptText").asText(null), request.getTopic()));
            response.setRequirements(firstNonBlank(node.path("requirements").asText(null), request.getRequirements()));
            response.setTaskType(trimToNull(node.path("taskType").asText(null)));
            response.setGenre(firstNonBlank(node.path("genre").asText(null), request.getGenre()));
            response.setWordRange(firstNonBlank(node.path("wordRange").asText(null), request.getWordRange()));
            if (!node.path("maxScore").isMissingNode() && !node.path("maxScore").isNull()) {
                response.setMaxScore(node.path("maxScore").asInt(request.getMaxScore() == null ? 100 : request.getMaxScore()));
            }
            response.setMaterialText(trimToNull(node.path("materialText").asText(null)));
            response.setChartSpec(readChartSpec(node.path("chartSpec")));
            response.setComicScenes(readComicScenes(node.path("comicScenes")));
            return response;
        } catch (Exception e) {
            log.warn("[WRITING-EXAM-PROMPT] parse failed raw={}", raw, e);
            return response;
        }
    }

    private GenerateExamPromptResponse emptyResponse(String promptType, GenerateExamPromptRequest request) {
        GenerateExamPromptResponse response = new GenerateExamPromptResponse();
        response.setPromptType(promptType);
        response.setTopic(trimToNull(request.getTopic()));
        response.setPromptText(trimToNull(request.getTopic()));
        response.setRequirements(trimToNull(request.getRequirements()));
        response.setGenre(trimToNull(request.getGenre()));
        response.setWordRange(trimToNull(request.getWordRange()));
        response.setMaxScore(request.getMaxScore());
        response.setComicScenes(List.of());
        return response;
    }

    private GenerateExamPromptResponse.ChartSpec readChartSpec(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        GenerateExamPromptResponse.ChartSpec spec = new GenerateExamPromptResponse.ChartSpec();
        spec.setTitle(trimToNull(node.path("title").asText(null)));
        spec.setDisplayType(trimToNull(node.path("displayType").asText(null)));
        spec.setSummary(trimToNull(node.path("summary").asText(null)));

        List<String> columns = new ArrayList<>();
        JsonNode columnsNode = node.path("columns");
        if (columnsNode.isArray()) {
            for (JsonNode item : columnsNode) {
                String value = trimToNull(item.asText(null));
                if (value != null) {
                    columns.add(value);
                }
            }
        }
        spec.setColumns(columns);

        List<List<String>> rows = new ArrayList<>();
        JsonNode rowsNode = node.path("rows");
        if (rowsNode.isArray()) {
            for (JsonNode rowNode : rowsNode) {
                if (!rowNode.isArray()) {
                    continue;
                }
                List<String> row = new ArrayList<>();
                for (JsonNode cell : rowNode) {
                    String value = trimToNull(cell.asText(null));
                    row.add(value == null ? "" : value);
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
        }
        spec.setRows(rows);
        return spec.getColumns().isEmpty() && spec.getRows().isEmpty() && spec.getSummary() == null && spec.getTitle() == null
                ? null
                : spec;
    }

    private List<GenerateExamPromptResponse.ComicScene> readComicScenes(JsonNode node) {
        List<GenerateExamPromptResponse.ComicScene> scenes = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return scenes;
        }
        for (JsonNode sceneNode : node) {
            GenerateExamPromptResponse.ComicScene scene = new GenerateExamPromptResponse.ComicScene();
            scene.setTitle(trimToNull(sceneNode.path("title").asText(null)));
            scene.setDescription(trimToNull(sceneNode.path("description").asText(null)));
            scene.setDialogue(trimToNull(sceneNode.path("dialogue").asText(null)));
            if (scene.getDescription() != null) {
                scenes.add(scene);
            }
        }
        return scenes;
    }

    private void attachGeneratedVisual(GenerateExamPromptRequest request,
                                       GenerateExamPromptResponse response,
                                       String traceId) {
        if (!shouldGenerateVisualAttachment(response)) {
            return;
        }
        String imagePrompt = buildVisualImagePrompt(request, response);
        String attachmentImageUrl = trimToNull(openAiClient.generateImageWithProvider(
                request.getAiProvider(),
                imagePrompt,
                traceId + "-image"
        ));
        if (attachmentImageUrl != null) {
            response.setAttachmentImageUrl(attachmentImageUrl);
        }
    }

    private boolean shouldGenerateVisualAttachment(GenerateExamPromptResponse response) {
        return "visual".equalsIgnoreCase(trimToNull(response.getAttachmentType()));
    }

    private String buildVisualImagePrompt(GenerateExamPromptRequest request,
                                          GenerateExamPromptResponse response) {
        return imagePromptTemplate
                .replace("{{visualTypeLine}}", buildSingleLine("Visual type", firstNonBlank(response.getVisualKind(), response.getPromptType()), true))
                .replace("{{topicLine}}", buildSingleLine("Topic", response.getTopic(), true))
                .replace("{{promptLine}}", buildSingleLine("Prompt", response.getPromptText(), true))
                .replace("{{requirementsLine}}", buildSingleLine("Requirements", response.getRequirements(), true))
                .replace("{{attachmentTitleLine}}", buildSingleLine("Attachment title", response.getAttachmentTitle(), true))
                .replace("{{attachmentContentLine}}", buildBlock("Attachment content", response.getAttachmentContent()))
                .replace("{{originalInputLine}}", buildBlock("Original user request", request.getOriginalInput()))
                .replaceAll("(?m)^[ \\t]*\\r?\\n", "")
                .trim();
    }

    private String buildSingleLine(String label, String value, boolean trailingPeriod) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "";
        }
        return label + ": " + normalized.trim() + (trailingPeriod ? "." : "");
    }

    private String buildBlock(String label, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "";
        }
        return label + ":\n" + normalized.trim();
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
        java.util.regex.Matcher singleMatch = java.util.regex.Pattern.compile("^(\\d+)$").matcher(compact);
        if (singleMatch.find()) {
            int value = Integer.parseInt(singleMatch.group(1));
            response.setMinWords(value);
            response.setRecommendedMaxWords(value);
        }
    }

    private List<EssayPrompt> loadReferences(String stage) {
        Integer stageId = mapStageId(stage);
        if (stageId == null) {
            return List.of();
        }
        try {
            return essayPromptService.listByStage(stageId);
        } catch (Exception e) {
            log.warn("[WRITING-EXAM-PROMPT] failed to load references stage={}", stage, e);
            return List.of();
        }
    }

    private Integer mapStageId(String stage) {
        return switch (stage) {
            case "highschool", "senior" -> 1;
            case "cet4" -> 2;
            case "cet6" -> 3;
            case "postgrad" -> 4;
            default -> null;
        };
    }

    private String normalizeStage(String stage) {
        String normalized = trimToNull(stage);
        if (normalized == null) {
            return "highschool";
        }
        return switch (normalized.toLowerCase()) {
            case "highschool", "senior", "cet4", "cet6", "postgrad" -> normalized.toLowerCase();
            default -> normalized.toLowerCase();
        };
    }

    private String normalizePromptType(String promptType) {
        String normalized = trimToNull(promptType);
        if (normalized == null) {
            return "general";
        }
        return switch (normalized.toLowerCase()) {
            case "material", "chart", "comic" -> normalized.toLowerCase();
            default -> "general";
        };
    }

    private String stripCodeFences(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private String firstNonBlank(String first, String fallback) {
        String normalized = trimToNull(first);
        return normalized != null ? normalized : trimToNull(fallback);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }

    private String loadPromptTemplate(String path, String fallback) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                return content;
            }
        } catch (Exception e) {
            log.warn("[WRITING-EXAM-PROMPT] failed to load prompt template path={} reason={}", path, e.getMessage());
        }
        return fallback;
    }

    private String defaultSystemPrompt() {
        return """
                你是一位英语考试命题助手。你的任务是把用户想练的主题、材料、人物设定或数据要求，整理成一题“仿照 {{stage}} 真实考试风格”的英语写作题。

                严格要求：
                - 用户给出的细节是硬约束，必须尽量保留，不要随意替换题材或核心事实
                - 当前学段是硬约束，题目必须符合该学段的考试写作语气、题面结构和字数习惯，不要写成普通作文提示
                - 只生成 1 道题
                - 不生成真实图片，只生成结构化信息
                - chart 类型输出 chartSpec
                - comic 类型输出 comicScenes
                - material 类型输出 materialText

                promptType 只能是：general、material、chart、comic

                输出必须是合法 JSON：
                {
                  "promptType": "general|material|chart|comic",
                  "topic": "中文或英文主题标题",
                  "promptText": "完整英文写作题干",
                  "requirements": "对写作要求的补充说明",
                  "genre": "体裁，可为空",
                  "wordRange": "如 120-150，可为空",
                  "maxScore": 20,
                  "materialText": "材料题的材料正文，可为空",
                  "chartSpec": {
                    "title": "图表标题",
                    "displayType": "table|chart",
                    "columns": ["列1", "列2"],
                    "rows": [["值1", "值2"]],
                    "summary": "一句概括"
                  },
                  "comicScenes": [
                    {
                      "title": "分镜标题",
                      "description": "画面描述",
                      "dialogue": "对白，可为空"
                    }
                  ]
                }

                除 JSON 外不要输出任何其他内容。
                """;
    }

    private String defaultImagePrompt() {
        return """
                Generate a clean exam-style visual attachment for an English writing prompt.

                The visual should be suitable for a student composition task.

                {{visualTypeLine}}
                {{topicLine}}
                {{promptLine}}
                {{requirementsLine}}
                {{attachmentTitleLine}}
                {{attachmentContentLine}}
                {{originalInputLine}}
                Preserve any provided entities, labels, and relationships.

                Do not add unrelated elements.
                """;
    }

    private String defaultUserPrompt() {
        return """
                [INPUT]
                {{inputBlock}}

                [STYLE_REFERENCES]
                {{styleReferencesBlock}}
                """;
    }
}
