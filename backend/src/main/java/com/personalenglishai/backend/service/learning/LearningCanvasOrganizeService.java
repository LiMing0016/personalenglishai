package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import org.springframework.stereotype.Service;

@Service
public class LearningCanvasOrganizeService {
    private static final String SYSTEM_PROMPT = """
            你是英语学习笔记整理助手。只输出 Markdown，不输出解释性前后缀。
            你必须帮助用户整理学习资产，首版类型是 vocabulary。
            format 模式下尽量保留用户原意，不要删除用户的个人笔记。
            """;

    private final OpenAiClient openAiClient;

    public LearningCanvasOrganizeService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public LearningCanvasOrganizeResponse organize(LearningCanvasOrganizeRequest request) {
        String userPrompt = buildPrompt(request);
        String markdown = openAiClient.callWithProvider(
                null,
                SYSTEM_PROMPT,
                userPrompt,
                "learning-canvas-organize",
                0.2,
                1200);
        LearningCanvasOrganizeResponse response = new LearningCanvasOrganizeResponse();
        response.setCandidateMarkdown(markdown == null ? "" : markdown.trim());
        return response;
    }

    String buildPrompt(LearningCanvasOrganizeRequest request) {
        LearningCanvasOrganizeRequest safeRequest = request == null ? new LearningCanvasOrganizeRequest() : request;
        String mode = safe(safeRequest.getMode()).toLowerCase();
        if ("format".equals(mode)) {
            return """
                    请优化下面 Markdown 的格式，尽量保留用户原意。
                    只调整标题、加粗、引用、列表和段落结构；不要删除用户的个人笔记。

                    当前 Markdown：
                    %s
                    """.formatted(safe(safeRequest.getCurrentMarkdown()));
        }
        return """
                请按默认单词卡模板整理 vocabulary 学习资产。

                默认单词卡模板：
                # {{title}}
                **词性：**
                **中文释义：**
                **English meaning：**
                **原句：**
                **AI 例句：**
                **常见搭配：**
                ## 我的笔记

                title: %s
                selectedText: %s
                contextText: %s
                """.formatted(
                safe(safeRequest.getTitle()),
                safe(safeRequest.getSelectedText()),
                safe(safeRequest.getContextText()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
