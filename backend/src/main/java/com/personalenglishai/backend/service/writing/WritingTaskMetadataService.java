package com.personalenglishai.backend.service.writing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.entity.WritingTaskMetadata;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import com.personalenglishai.backend.mapper.WritingTaskMetadataMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class WritingTaskMetadataService {
    private static final String DEFAULT_WORKSPACE = "default";
    private static final String METADATA_VERSION = "writing-task-metadata@test-v1";
    private static final String RUBRIC_SOURCE = "writing-coach-stage-policy@test-v1";

    private final DocumentMapper documentMapper;
    private final WritingMetadataMapper writingMetadataMapper;
    private final WritingTaskMetadataMapper writingTaskMetadataMapper;
    private final ObjectMapper objectMapper;

    public WritingTaskMetadataService(
            DocumentMapper documentMapper,
            WritingMetadataMapper writingMetadataMapper,
            WritingTaskMetadataMapper writingTaskMetadataMapper,
            ObjectMapper objectMapper
    ) {
        this.documentMapper = documentMapper;
        this.writingMetadataMapper = writingMetadataMapper;
        this.writingTaskMetadataMapper = writingTaskMetadataMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public WritingTaskMetadata ensureForDocument(String tenantId, String workspaceId, String publicDocId, Long userId) {
        String ws = workspaceId == null || workspaceId.isBlank() ? DEFAULT_WORKSPACE : workspaceId;
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, ws);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        }
        if (!doc.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        }

        WritingTaskMetadata existing = writingTaskMetadataMapper.selectByDocumentId(doc.getId());
        if (existing != null) {
            return existing;
        }

        WritingMetadata writingMetadata = writingMetadataMapper.selectByDocumentId(doc.getId());
        WritingTaskMetadata generated = generateTestMetadata(doc, writingMetadata, userId);
        writingTaskMetadataMapper.insert(generated);
        return generated;
    }

    private WritingTaskMetadata generateTestMetadata(Document doc, WritingMetadata writingMetadata, Long userId) {
        String promptText = coalesce(
                writingMetadata == null ? null : writingMetadata.getPromptText(),
                doc.getTaskPrompt(),
                doc.getTitle(),
                ""
        );
        String studyStage = writingMetadata == null ? null : trimToNull(writingMetadata.getStudyStage());
        String mode = writingMetadata == null ? null : trimToNull(writingMetadata.getMode());
        String genre = writingMetadata == null ? null : trimToNull(writingMetadata.getGenre());
        String taskType = resolveTaskType(promptText, genre);
        String topic = normalizePromptForCentralTask(promptText);

        WritingTaskMetadata metadata = new WritingTaskMetadata();
        metadata.setDocumentId(doc.getId());
        metadata.setDocumentPublicId(doc.getPublicId());
        metadata.setUserId(userId);
        metadata.setStudyStage(studyStage);
        metadata.setAssistantMode(mode);
        metadata.setPromptText(promptText);
        metadata.setTaskType(taskType);
        metadata.setCentralTask(buildCentralTask(topic, taskType));
        metadata.setMustAnswerPointsJson(toJson(List.of(
                "明确回答题目核心问题",
                "给出清晰立场或中心判断",
                "用至少两个理由或细节展开",
                "用例子、解释或对比支撑观点",
                "结尾回到题目要求"
        )));
        metadata.setWritingFocusJson(toJson(List.of(
                "先保证扣题，再追求表达升级",
                "观点要稳定，段落功能要清楚",
                "每个主体段都要有主题句、解释和支撑",
                "语言难度服从当前学段输出标准"
        )));
        metadata.setRiskPointsJson(toJson(List.of(
                "只罗列信息但没有回答题目核心问题会偏题",
                "观点摇摆或结论不明确会影响任务完成度",
                "例子太空泛会削弱论证",
                "为了高级表达堆长句可能降低准确性"
        )));
        metadata.setRecommendedStructureJson(toJson(Map.of(
                "intro", "背景引入 + 改写题目 + 明确中心观点",
                "body_1", "第一个理由/要点 + 解释 + 例子",
                "body_2", "第二个理由/要点 + 对比或补充说明",
                "conclusion", "总结判断 + 回扣题目"
        )));
        metadata.setRubricFocusJson(toJson(List.of(
                "task_response",
                "organization",
                "idea_development",
                "language_accuracy",
                "stage_appropriateness"
        )));
        metadata.setMetadataVersion(METADATA_VERSION);
        metadata.setRubricSource(RUBRIC_SOURCE);
        return metadata;
    }

    private String resolveTaskType(String promptText, String genre) {
        if (genre != null) {
            return genre;
        }
        String lower = promptText == null ? "" : promptText.toLowerCase();
        if (lower.contains("better than") || lower.contains("agree") || lower.contains("opinion")) {
            return "argumentative";
        }
        if (lower.contains("chart") || lower.contains("table") || lower.contains("graph")) {
            return "chart_description";
        }
        return "general_essay";
    }

    private String buildCentralTask(String topic, String taskType) {
        if ("chart_description".equals(taskType)) {
            return "围绕题目材料描述关键信息、解释趋势或差异，并给出符合题目要求的评论。";
        }
        return "围绕“" + topic + "”建立清晰中心观点，并用理由、例子和段落逻辑证明这个观点。";
    }

    private String normalizePromptForCentralTask(String promptText) {
        String value = trimToNull(promptText);
        if (value == null) {
            return "本篇作文题目";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 160) {
            return normalized.substring(0, 160);
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize writing task metadata", e);
        }
    }

    private String coalesce(String first, String second, String third, String fallback) {
        String value = trimToNull(first);
        if (value != null) return value;
        value = trimToNull(second);
        if (value != null) return value;
        value = trimToNull(third);
        return value != null ? value : fallback;
    }

    private String trimToNull(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }
}
