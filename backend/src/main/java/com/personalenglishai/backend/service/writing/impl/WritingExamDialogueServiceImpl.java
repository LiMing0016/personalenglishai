package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import com.personalenglishai.backend.dto.writing.ExamWorkbenchMessageDto;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnResponse;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.service.writing.AuditTopicService;
import com.personalenglishai.backend.service.writing.WritingExamDialogueService;
import com.personalenglishai.backend.service.writing.WritingExamPromptService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WritingExamDialogueServiceImpl implements WritingExamDialogueService {

    private final AuditTopicService auditTopicService;
    private final WritingExamPromptService writingExamPromptService;

    public WritingExamDialogueServiceImpl(AuditTopicService auditTopicService,
                                          WritingExamPromptService writingExamPromptService) {
        this.auditTopicService = auditTopicService;
        this.writingExamPromptService = writingExamPromptService;
    }

    @Override
    public GenerateExamDialogueTurnResponse generateTurn(Long userId, GenerateExamDialogueTurnRequest request) {
        GenerateExamDialogueTurnResponse response = new GenerateExamDialogueTurnResponse();
        String originalInput = buildOriginalInput(request.getMessages());
        if (originalInput == null) {
            response.setPreviewStatus("empty");
            response.setAssistantReply("我还没有收到有效的命题信息。请先输入题目需求，或插入图片、材料、真题。");
            response.setAssistantReplyBlocks(List.of(
                    block("understanding", "我还没有收到有效的命题信息。"),
                    block("follow_up", "请先输入题目需求，或插入图片、材料、真题。"),
                    block("action", "右侧暂时保持空白，等你发出第一条命题消息后再整理题单。")
            ));
            return response;
        }

        AuditTopicRequest auditRequest = new AuditTopicRequest();
        auditRequest.setTopic(originalInput);
        auditRequest.setStudyStage(request.getStudyStage());
        AuditTopicResponse audit = auditTopicService.audit(auditRequest, request.getAiProvider());

        if ("invalid".equalsIgnoreCase(audit.getStatus())) {
            response.setPreviewStatus("empty");
            response.setMissingFields(List.of("待补充有效题目信息"));
            response.setAssistantReply(firstNonBlank(audit.getAssistantReply(), audit.getMessage(), "我暂时没能把这段内容识别成有效作文题。"));
            response.setAssistantReplyBlocks(List.of(
                    block("understanding", "我暂时没能把这段内容识别成有效作文题。"),
                    block("follow_up", firstNonBlank(audit.getMessage(), "请补充更明确的题目、材料或图片说明。")),
                    block("action", "右侧先不生成题单，等输入变得更明确后再继续。")
            ));
            return response;
        }

        GenerateExamPromptRequest promptRequest = new GenerateExamPromptRequest();
        promptRequest.setUserId(userId);
        promptRequest.setAiProvider(request.getAiProvider());
        promptRequest.setStudyStage(request.getStudyStage());
        promptRequest.setOriginalInput(originalInput);
        promptRequest.setTopic(firstNonBlank(audit.getTopic(), originalInput));
        promptRequest.setPromptType(audit.getPromptType());
        promptRequest.setGenre(audit.getGenre());
        promptRequest.setWordRange(audit.getWordRange());
        promptRequest.setRequirements(audit.getRequirements());

        GenerateExamPromptResponse promptSheetDraft = writingExamPromptService.generate(promptRequest);
        backfillPromptDraft(promptSheetDraft, audit, originalInput);
        applyAttachmentMetadata(promptSheetDraft, audit);

        List<String> missingFields = collectMissingFields(audit, promptSheetDraft);
        response.setMissingFields(missingFields);
        response.setPromptSheetDraft(promptSheetDraft);
        boolean promptReady = Boolean.TRUE.equals(audit.getPromptReady()) || missingFields.isEmpty();
        response.setPreviewStatus(promptReady ? "ready" : "draft");
        response.setAssistantReply(firstNonBlank(audit.getAssistantReply(), audit.getMessage()));
        response.setAssistantReplyBlocks(buildReplyBlocks(audit, missingFields, promptSheetDraft));
        return response;
    }

    private void backfillPromptDraft(GenerateExamPromptResponse draft, AuditTopicResponse audit, String originalInput) {
        if (draft == null) {
            return;
        }
        if (isBlank(draft.getTopic())) {
            draft.setTopic(firstNonBlank(audit.getTopic(), originalInput));
        }
        if (isBlank(draft.getPromptText())) {
            draft.setPromptText(firstNonBlank(audit.getTopic(), originalInput));
        }
        if (isBlank(draft.getPromptType())) {
            draft.setPromptType(audit.getPromptType());
        }
        if (isBlank(draft.getGenre())) {
            draft.setGenre(audit.getGenre());
        }
        if (isBlank(draft.getWordRange())) {
            draft.setWordRange(audit.getWordRange());
        }
        if (isBlank(draft.getRequirements())) {
            draft.setRequirements(audit.getRequirements());
        }
    }

    private void applyAttachmentMetadata(GenerateExamPromptResponse draft, AuditTopicResponse audit) {
        if (draft == null || !Boolean.TRUE.equals(audit.getNeedsAttachment())) {
            return;
        }

        String attachmentType = audit.getAttachmentType();
        if ("image".equals(attachmentType)) {
            if (isBlank(draft.getAttachmentType())) {
                draft.setAttachmentType("visual");
            }
            if (isBlank(draft.getVisualKind())) {
                draft.setVisualKind("comic".equalsIgnoreCase(audit.getPromptType()) ? "comic" : "image");
            }
        } else if ("chart".equals(attachmentType)) {
            if (isBlank(draft.getAttachmentType())) {
                draft.setAttachmentType("visual");
            }
            if (isBlank(draft.getVisualKind())) {
                draft.setVisualKind("chart");
            }
        } else if ("material_text".equals(attachmentType)) {
            if (isBlank(draft.getAttachmentType())) {
                draft.setAttachmentType("material");
            }
        }

        if (isBlank(draft.getAttachmentTitle())) {
            draft.setAttachmentTitle(audit.getAttachmentTitle());
        }
        if (isBlank(draft.getAttachmentSource())) {
            draft.setAttachmentSource(audit.getAttachmentSource());
        }

        String attachmentContent = buildAttachmentContent(audit);
        if (isBlank(draft.getAttachmentContent()) && !isBlank(attachmentContent)) {
            draft.setAttachmentContent(attachmentContent);
        }

        if ("material_text".equals(attachmentType) && isBlank(draft.getMaterialText())) {
            draft.setMaterialText(extractPayloadText(audit.getAttachmentPayload(), "materialText"));
        }
    }

    private List<GenerateExamDialogueTurnResponse.AssistantReplyBlock> buildReplyBlocks(
            AuditTopicResponse audit,
            List<String> missingFields,
            GenerateExamPromptResponse draft
    ) {
        String assistantReply = audit.getAssistantReply();
        if (!isBlank(assistantReply)) {
            return List.of(block("reply", assistantReply));
        }

        List<GenerateExamDialogueTurnResponse.AssistantReplyBlock> blocks = new ArrayList<>();
        String understanding = "我先按当前信息整理成一版"
                + firstNonBlank(audit.getGenre(), "作文题")
                + "草稿。";
        if (!isBlank(draft.getPromptType())) {
            understanding = "我先把这轮需求整理成一版 " + draft.getPromptType() + " 题单草稿。";
        }
        blocks.add(block("understanding", understanding));

        String followUp = missingFields.isEmpty()
                ? "当前关键信息已经足够，可以直接进入写作。"
                : "还需要继续确认：" + String.join("、", missingFields) + "。";
        if (!isBlank(audit.getMessage()) && !missingFields.isEmpty()) {
            followUp = followUp + " " + audit.getMessage();
        }
        blocks.add(block("follow_up", followUp));

        String action = missingFields.isEmpty()
                ? "右侧题单已刷新为完成态。"
                : "右侧题单已刷新为草稿态，你继续补充后我会自动更新。";
        blocks.add(block("action", action));
        return blocks;
    }

    private List<String> collectMissingFields(AuditTopicResponse audit, GenerateExamPromptResponse draft) {
        if (audit.getMissingFields() != null && !audit.getMissingFields().isEmpty()) {
            return new ArrayList<>(audit.getMissingFields());
        }
        List<String> missingFields = new ArrayList<>();
        if (isBlank(firstNonBlank(draft == null ? null : draft.getWordRange(), audit.getWordRange()))) {
            missingFields.add("待补充字数");
        }
        if (isBlank(firstNonBlank(draft == null ? null : draft.getGenre(), audit.getGenre()))) {
            missingFields.add("待确认体裁");
        }
        if (isBlank(firstNonBlank(draft == null ? null : draft.getRequirements(), audit.getRequirements()))) {
            missingFields.add("待补充写作要求");
        }
        return missingFields;
    }

    private String buildOriginalInput(List<ExamWorkbenchMessageDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String latestUserText = messages.stream()
                .filter(message -> message != null
                        && "user".equalsIgnoreCase(message.getRole())
                        && "text".equalsIgnoreCase(message.getKind())
                        && !isBlank(message.getText()))
                .reduce((first, second) -> second)
                .map(message -> message.getText().trim())
                .orElse(null);

        List<String> parts = new ArrayList<>();
        if (!isBlank(latestUserText)) {
            parts.add(latestUserText);
        }

        messages.stream()
                .filter(message -> message != null
                        && "user".equalsIgnoreCase(message.getRole())
                        && "asset".equalsIgnoreCase(message.getKind()))
                .map(this::messageToLine)
                .filter(Objects::nonNull)
                .forEach(parts::add);

        String joined = String.join("\n\n", parts).trim();
        return joined.isEmpty() ? null : joined;
    }

    private String messageToLine(ExamWorkbenchMessageDto message) {
        if (message == null) {
            return null;
        }
        if (!isBlank(message.getText())) {
            return message.getText().trim();
        }
        if (!isBlank(message.getAssetSummary())) {
            String assetType = firstNonBlank(message.getAssetType(), "素材");
            return "[" + assetType + "] " + message.getAssetSummary().trim();
        }
        return null;
    }

    private String buildAttachmentContent(AuditTopicResponse audit) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(audit.getAttachmentInstruction())) {
            parts.add(audit.getAttachmentInstruction().trim());
        }
        Map<String, Object> payload = audit.getAttachmentPayload();
        if (payload == null || payload.isEmpty()) {
            return parts.isEmpty() ? null : String.join("\n", parts);
        }

        String imagePrompt = extractPayloadText(payload, "imagePrompt");
        String chartSummary = extractPayloadText(payload, "chartTrendSummary");
        String materialText = extractPayloadText(payload, "materialText");
        String imageStyle = extractPayloadText(payload, "imageStyle");
        if (!isBlank(imagePrompt)) {
            parts.add(imagePrompt);
        }
        if (!isBlank(chartSummary)) {
            parts.add(chartSummary);
        }
        if (!isBlank(materialText)) {
            parts.add(materialText);
        }
        if (!isBlank(imageStyle) && parts.stream().noneMatch(part -> part.contains(imageStyle))) {
            parts.add("素材风格：" + imageStyle);
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private String extractPayloadText(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private GenerateExamDialogueTurnResponse.AssistantReplyBlock block(String kind, String text) {
        GenerateExamDialogueTurnResponse.AssistantReplyBlock block =
                new GenerateExamDialogueTurnResponse.AssistantReplyBlock();
        block.setKind(kind);
        block.setText(text);
        return block;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
