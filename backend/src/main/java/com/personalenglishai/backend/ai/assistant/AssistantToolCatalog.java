package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public final class AssistantToolCatalog {

    private AssistantToolCatalog() {
    }

    public static List<AssistantToolDefinition> defaultTools() {
        return List.of(
                new AssistantToolDefinition(
                        "polish_selection",
                        "润色当前选中的英文句子，返回更自然或更高分的版本。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "selectedText": { "type": "string" },
                                    "context": { "type": ["string", "null"] },
                                    "reason": { "type": ["string", "null"] },
                                    "tier": { "type": ["string", "null"] }
                                  },
                                  "required": ["selectedText", "context", "reason", "tier"],
                                  "additionalProperties": false
                                }
                                """
                ),
                new AssistantToolDefinition(
                        "rewrite_selection",
                        "根据用户要求重写当前选中的英文内容。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "selectedText": { "type": "string" },
                                    "instruction": { "type": ["string", "null"] },
                                    "context": { "type": ["string", "null"] }
                                  },
                                  "required": ["selectedText", "instruction", "context"],
                                  "additionalProperties": false
                                }
                                """
                ),
                new AssistantToolDefinition(
                        "translate_selection",
                        "翻译当前选中的英文内容。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "selectedText": { "type": "string" },
                                    "mode": { "type": ["string", "null"] }
                                  },
                                  "required": ["selectedText", "mode"],
                                  "additionalProperties": false
                                }
                                """
                ),
                new AssistantToolDefinition(
                        "get_model_essay",
                        "获取与当前题目或作文相关的优秀范文和满分范文。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "essay": { "type": ["string", "null"] },
                                    "studyStage": { "type": ["string", "null"] },
                                    "writingMode": { "type": ["string", "null"] },
                                    "taskType": { "type": ["string", "null"] },
                                    "topicContent": { "type": ["string", "null"] },
                                    "taskPrompt": { "type": ["string", "null"] },
                                    "minWords": { "type": ["integer", "null"] },
                                    "recommendedMaxWords": { "type": ["integer", "null"] }
                                  },
                                  "required": ["essay", "studyStage", "writingMode", "taskType", "topicContent", "taskPrompt", "minWords", "recommendedMaxWords"],
                                  "additionalProperties": false
                                }
                                """
                ),
                new AssistantToolDefinition(
                        "get_writing_material",
                        "获取围绕当前题目的主题词、短语和句子素材。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "taskPrompt": { "type": ["string", "null"] },
                                    "essayText": { "type": ["string", "null"] },
                                    "studyStage": { "type": ["string", "null"] },
                                    "writingMode": { "type": ["string", "null"] }
                                  },
                                  "required": ["taskPrompt", "essayText", "studyStage", "writingMode"],
                                  "additionalProperties": false
                                }
                                """
                ),
                new AssistantToolDefinition(
                        "get_score_summary",
                        "读取当前作文最近一次评分概览。",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "docId": { "type": ["string", "null"] }
                                  },
                                  "required": ["docId"],
                                  "additionalProperties": false
                                }
                                """
                )
        );
    }
}
