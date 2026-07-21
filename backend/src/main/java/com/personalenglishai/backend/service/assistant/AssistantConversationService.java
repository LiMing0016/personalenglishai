package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationDetailResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationSummaryResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantMessageResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantShareResponse;
import com.personalenglishai.backend.controller.dto.assistant.CreateAssistantConversationRequest;
import com.personalenglishai.backend.controller.dto.assistant.MoveAssistantConversationRequest;
import com.personalenglishai.backend.controller.dto.assistant.PublicAssistantShareResponse;
import com.personalenglishai.backend.controller.dto.assistant.SendAssistantMessageRequest;
import com.personalenglishai.backend.controller.dto.assistant.UpdateAssistantConversationRequest;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.assistant.AssistantProject;
import com.personalenglishai.backend.entity.assistant.AssistantShare;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantProjectMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantShareMapper;
import com.personalenglishai.backend.service.learning.LearningCaptureService;
import com.personalenglishai.backend.service.ops.AgentDebugService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AssistantConversationService {
    private static final Logger log = LoggerFactory.getLogger(AssistantConversationService.class);
    private static final String DEFAULT_TITLE = "新对话";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTACHMENT_COUNT = 5;
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final int AGENT_HISTORY_MESSAGE_LIMIT = 20;
    private static final int AGENT_HISTORY_TOTAL_CHARS = 12_000;
    private static final int AGENT_HISTORY_MESSAGE_CHARS = 4_000;
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            ".pdf",
            ".txt",
            ".doc",
            ".docx");

    private final AssistantProjectMapper projectMapper;
    private final AssistantConversationMapper conversationMapper;
    private final AssistantMessageMapper messageMapper;
    private final AssistantShareMapper shareMapper;
    private final PythonAssistantClient pythonAssistantClient;
    private final AssistantRequestValidator assistantRequestValidator;
    private final ObjectMapper objectMapper;
    private final AgentDebugService agentDebugService;
    private final LearningCaptureService learningCaptureService;

    public AssistantConversationService(
            AssistantProjectMapper projectMapper,
            AssistantConversationMapper conversationMapper,
            AssistantMessageMapper messageMapper,
            AssistantShareMapper shareMapper,
            PythonAssistantClient pythonAssistantClient,
            AssistantRequestValidator assistantRequestValidator,
            ObjectMapper objectMapper,
            AgentDebugService agentDebugService,
            LearningCaptureService learningCaptureService) {
        this.projectMapper = projectMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.shareMapper = shareMapper;
        this.pythonAssistantClient = pythonAssistantClient;
        this.assistantRequestValidator = assistantRequestValidator;
        this.objectMapper = objectMapper;
        this.agentDebugService = agentDebugService;
        this.learningCaptureService = learningCaptureService;
    }

    public List<AssistantProjectResponse> listProjects(Long userId) {
        return projectMapper.selectActiveByUserId(userId).stream()
                .map(this::toProjectResponse)
                .toList();
    }

    @Transactional
    public AssistantProjectResponse createProject(Long userId, AssistantProjectRequest request) {
        AssistantProject project = new AssistantProject();
        project.setUserId(userId);
        project.setName(cleanRequired(request.getName()));
        project.setDescription(cleanOptional(request.getDescription()));
        projectMapper.insert(project);
        return toProjectResponse(projectMapper.findOwnedActiveById(userId, project.getId()));
    }

    @Transactional
    public AssistantProjectResponse updateProject(Long userId, Long projectId, AssistantProjectRequest request) {
        ensureProject(userId, projectId);
        AssistantProject project = new AssistantProject();
        project.setId(projectId);
        project.setUserId(userId);
        project.setName(cleanRequired(request.getName()));
        project.setDescription(cleanOptional(request.getDescription()));
        projectMapper.updateOwned(project);
        return toProjectResponse(ensureProject(userId, projectId));
    }

    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        ensureProject(userId, projectId);
        projectMapper.softDeleteOwned(userId, projectId, LocalDateTime.now());
    }

    public List<AssistantConversationSummaryResponse> listConversations(Long userId, Boolean archived, Long projectId) {
        if (projectId != null) {
            ensureProject(userId, projectId);
        }
        return conversationMapper.selectByUser(userId, archived, projectId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional
    public AssistantConversationDetailResponse createConversation(Long userId, CreateAssistantConversationRequest request) {
        Long projectId = request.getProjectId();
        if (projectId != null) {
            ensureProject(userId, projectId);
        }

        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-" + UUID.randomUUID());
        conversation.setUserId(userId);
        conversation.setProjectId(projectId);
        conversation.setTitle(cleanOptional(request.getTitle()) == null ? DEFAULT_TITLE : cleanOptional(request.getTitle()));
        conversation.setSummary("");
        conversation.setPinned(false);
        conversationMapper.insert(conversation);
        return getConversation(userId, conversation.getConversationUid());
    }

    public AssistantConversationDetailResponse getConversation(Long userId, String conversationUid) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        return toDetailResponse(conversation, messageMapper.selectByConversationUid(conversationUid));
    }

    @Transactional
    public AssistantConversationDetailResponse updateConversation(
            Long userId,
            String conversationUid,
            UpdateAssistantConversationRequest request) {
        ensureConversation(userId, conversationUid);
        conversationMapper.updateTitleSummaryOwned(
                userId,
                conversationUid,
                cleanRequired(request.getTitle()),
                cleanOptional(request.getSummary()));
        return getConversation(userId, conversationUid);
    }

    @Transactional
    public AssistantConversationDetailResponse sendMessage(
            Long userId,
            String conversationUid,
            SendAssistantMessageRequest request,
            String authorization) {
        return sendMessageInternal(userId, conversationUid, request, Collections.emptyList(), authorization);
    }

    @Transactional
    public AssistantConversationDetailResponse sendMessageWithFiles(
            Long userId,
            String conversationUid,
            SendAssistantMessageRequest request,
            List<MultipartFile> files,
            String authorization) {
        return sendMessageInternal(userId, conversationUid, request, toPythonFiles(files), authorization);
    }

    @Transactional
    public AssistantConversationDetailResponse sendAgentMessage(
            Long userId,
            String conversationUid,
            AssistantRequest request,
            String authorization) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        request.setAppConversationId(conversationUid);
        assistantRequestValidator.validateForAgentRun(request);

        String prompt = displayPrompt(request);
        int nextOrder = nextSortOrder(conversationUid);
        attachConversationHistory(request, conversationUid);
        AssistantMessage userMessage = buildMessage(userId, conversationUid, "user", prompt, nextOrder);
        persistAndCaptureMessage(userMessage);

        PythonAssistantClient.PythonAssistantReply reply = pythonAssistantClient.run(request, authorization);
        String replyText = reply == null ? "" : reply.text();
        if (replyText.isBlank()) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE);
        }
        agentDebugService.recordAssistantRun(userId, conversationUid, prompt, reply.getRun(), replyText);

        AssistantMessage assistantMessage = buildMessage(userId, conversationUid, "assistant", replyText, nextOrder + 1);
        assistantMessage.setPartsJson(writePartsJson(reply.getParts()));
        persistAndCaptureMessage(assistantMessage);
        String title = shouldAutoTitle(conversation) ? buildTitle(prompt) : conversation.getTitle();
        conversationMapper.updateTitleSummaryOwned(userId, conversationUid, title, buildSummary(prompt));
        return getConversation(userId, conversationUid);
    }

    public void writeAgentMessageStream(
            Long userId,
            String conversationUid,
            AssistantRequest request,
            String authorization,
            OutputStream outputStream) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        request.setAppConversationId(conversationUid);
        assistantRequestValidator.validateForAgentRun(request);

        String prompt = displayPrompt(request);
        int nextOrder = nextSortOrder(conversationUid);
        attachConversationHistory(request, conversationUid);
        persistAndCaptureMessage(buildMessage(userId, conversationUid, "user", prompt, nextOrder));

        StringBuilder deltaContent = new StringBuilder();
        StringBuilder completedContent = new StringBuilder();
        AssistantRunMetadataHolder runMetadataHolder = new AssistantRunMetadataHolder();
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            pythonAssistantClient.streamRun(request, authorization)
                    .doOnNext(eventJson -> {
                        String normalized = normalizeStreamEvent(eventJson);
                        if (normalized.isBlank()) {
                            return;
                        }
                        captureAssistantStreamContent(normalized, deltaContent, completedContent, runMetadataHolder, failed);
                        writeSseEvent(outputStream, normalized);
                    })
                    .blockLast();
        } catch (Exception e) {
            failed.set(true);
            writeSseEvent(outputStream, buildStreamFailureEvent(e));
        }

        String replyText = !completedContent.isEmpty() ? completedContent.toString() : deltaContent.toString();
        if (!failed.get() && !replyText.isBlank()) {
            agentDebugService.recordAssistantRun(userId, conversationUid, prompt, runMetadataHolder.run, replyText);
            AssistantMessage assistantMessage = buildMessage(userId, conversationUid, "assistant", replyText, nextOrder + 1);
            assistantMessage.setPartsJson(writePartsJson(runMetadataHolder.parts));
            persistAndCaptureMessage(assistantMessage);
            String title = shouldAutoTitle(conversation) ? buildTitle(prompt) : conversation.getTitle();
            conversationMapper.updateTitleSummaryOwned(userId, conversationUid, title, buildSummary(prompt));
        }
    }

    private AssistantConversationDetailResponse sendMessageInternal(
            Long userId,
            String conversationUid,
            SendAssistantMessageRequest request,
            List<PythonAssistantClient.PythonAssistantFile> files,
            String authorization) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        String prompt = cleanRequired(request.getMessage());
        if (prompt.isBlank()) {
            if (files.isEmpty()) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "message 或 files 至少要提供一个");
            }
            prompt = "请查看我上传的 " + files.size() + " 个文件，并结合它回答。";
        }
        int nextOrder = nextSortOrder(conversationUid);

        AssistantMessage userMessage = buildMessage(userId, conversationUid, "user", prompt, nextOrder);
        persistAndCaptureMessage(userMessage);

            PythonAssistantClient.PythonAssistantReply reply = pythonAssistantClient.chat(
                new PythonAssistantClient.PythonAssistantChatRequest(
                        prompt,
                        conversationUid,
                        request.getStudyStage(),
                        request.getAssistantMode(),
                        files),
                authorization);
        String replyText = reply == null ? "" : reply.text();
        if (replyText.isBlank()) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE);
        }
        agentDebugService.recordAssistantRun(userId, conversationUid, prompt, reply.getRun(), replyText);

        persistAndCaptureMessage(buildMessage(userId, conversationUid, "assistant", replyText, nextOrder + 1));
        String title = shouldAutoTitle(conversation) ? buildTitle(prompt) : conversation.getTitle();
        conversationMapper.updateTitleSummaryOwned(userId, conversationUid, title, buildSummary(prompt));
        return getConversation(userId, conversationUid);
    }

    private List<PythonAssistantClient.PythonAssistantFile> toPythonFiles(List<MultipartFile> files) {
        List<MultipartFile> nonEmptyFiles = files == null
                ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (nonEmptyFiles.size() > MAX_ATTACHMENT_COUNT) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "最多只能添加 5 个项目");
        }

        return nonEmptyFiles.stream().map(this::toPythonFile).toList();
    }

    private PythonAssistantClient.PythonAssistantFile toPythonFile(MultipartFile file) {
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "单个文件最大支持 10MB");
        }

        String filename = cleanFilename(file.getOriginalFilename());
        String contentType = cleanContentType(file.getContentType());
        if (!isAllowedAttachment(filename, contentType)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "仅支持 PNG、JPG、WebP、PDF、TXT、DOC、DOCX");
        }

        try {
            return new PythonAssistantClient.PythonAssistantFile(filename, contentType, file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR);
        }
    }

    private boolean isAllowedAttachment(String filename, String contentType) {
        return ALLOWED_ATTACHMENT_TYPES.contains(contentType) ||
                ALLOWED_ATTACHMENT_EXTENSIONS.contains(fileExtension(filename));
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "attachment";
        }
        return filename.trim();
    }

    private String cleanContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String fileExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index).toLowerCase(Locale.ROOT) : "";
    }

    @Transactional
    public AssistantConversationSummaryResponse archiveConversation(Long userId, String conversationUid) {
        ensureConversation(userId, conversationUid);
        conversationMapper.setArchivedAtOwned(userId, conversationUid, LocalDateTime.now());
        return toSummaryResponse(ensureConversation(userId, conversationUid));
    }

    @Transactional
    public AssistantConversationSummaryResponse restoreConversation(Long userId, String conversationUid) {
        ensureConversation(userId, conversationUid);
        conversationMapper.setArchivedAtOwned(userId, conversationUid, null);
        return toSummaryResponse(ensureConversation(userId, conversationUid));
    }

    @Transactional
    public AssistantConversationSummaryResponse setPinned(Long userId, String conversationUid, boolean pinned) {
        ensureConversation(userId, conversationUid);
        conversationMapper.setPinnedOwned(userId, conversationUid, pinned);
        return toSummaryResponse(ensureConversation(userId, conversationUid));
    }

    @Transactional
    public AssistantConversationSummaryResponse moveConversation(
            Long userId,
            String conversationUid,
            MoveAssistantConversationRequest request) {
        ensureConversation(userId, conversationUid);
        if (request.getProjectId() != null) {
            ensureProject(userId, request.getProjectId());
        }
        conversationMapper.moveOwned(userId, conversationUid, request.getProjectId());
        return toSummaryResponse(ensureConversation(userId, conversationUid));
    }

    @Transactional
    public void deleteConversation(Long userId, String conversationUid) {
        ensureConversation(userId, conversationUid);
        conversationMapper.softDeleteOwned(userId, conversationUid, LocalDateTime.now());
    }

    @Transactional
    public AssistantShareResponse shareConversation(Long userId, String conversationUid) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        AssistantShare existing = shareMapper.findActiveByConversationOwned(userId, conversationUid);
        if (existing != null) {
            return toShareResponse(existing);
        }

        AssistantShare share = new AssistantShare();
        share.setShareToken(generateShareToken());
        share.setConversationUid(conversationUid);
        share.setOwnerUserId(userId);
        share.setTitleSnapshot(conversation.getTitle());
        share.setMessagesSnapshot(writeMessagesSnapshot(messageMapper.selectByConversationUid(conversationUid)));
        shareMapper.insert(share);
        return toShareResponse(shareMapper.findActiveByToken(share.getShareToken()));
    }

    @Transactional
    public void revokeShare(Long userId, String shareToken) {
        shareMapper.revokeOwned(userId, shareToken, LocalDateTime.now());
    }

    public PublicAssistantShareResponse getPublicShare(String shareToken) {
        AssistantShare share = shareMapper.findActiveByToken(shareToken);
        if (share == null) {
            throw new BizException(ErrorCode.ASSISTANT_SHARE_NOT_FOUND);
        }
        return new PublicAssistantShareResponse(
                share.getTitleSnapshot(),
                readMessagesSnapshot(share.getMessagesSnapshot()),
                share.getCreatedAt());
    }

    private AssistantConversation ensureConversation(Long userId, String conversationUid) {
        AssistantConversation conversation = conversationMapper.findOwnedActiveByUid(userId, conversationUid);
        if (conversation == null) {
            throw new BizException(ErrorCode.ASSISTANT_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private AssistantProject ensureProject(Long userId, Long projectId) {
        AssistantProject project = projectMapper.findOwnedActiveById(userId, projectId);
        if (project == null) {
            throw new BizException(ErrorCode.ASSISTANT_PROJECT_NOT_FOUND);
        }
        return project;
    }

    private AssistantMessage buildMessage(Long userId, String conversationUid, String role, String content, int sortOrder) {
        AssistantMessage message = new AssistantMessage();
        message.setMessageUid("msg-" + UUID.randomUUID());
        message.setConversationUid(conversationUid);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("done");
        message.setSortOrder(sortOrder);
        return message;
    }

    private void persistAndCaptureMessage(AssistantMessage message) {
        messageMapper.insert(message);
        try {
            learningCaptureService.captureMessage(
                    message.getUserId(),
                    message.getConversationUid(),
                    message.getMessageUid(),
                    message.getRole(),
                    message.getContent());
        } catch (Exception e) {
            log.warn("learning capture failed. userId={} conversationUid={} messageUid={} role={}",
                    message.getUserId(),
                    message.getConversationUid(),
                    message.getMessageUid(),
                    message.getRole(),
                    e);
        }
    }

    private int nextSortOrder(String conversationUid) {
        Integer max = messageMapper.selectMaxSortOrder(conversationUid);
        return max == null ? 1 : max + 1;
    }

    private void attachConversationHistory(AssistantRequest request, String conversationUid) {
        List<AssistantMessage> messages = messageMapper.selectByConversationUid(conversationUid);
        if (messages == null || messages.isEmpty()) {
            request.setConversationHistory(List.of());
            return;
        }

        List<AssistantRequest.ConversationHistoryMessage> newestFirst = new ArrayList<>();
        int totalChars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AssistantMessage message = messages.get(i);
            if (message == null || !"done".equals(message.getStatus())) {
                continue;
            }
            String role = normalizeHistoryRole(message.getRole());
            if (role == null) {
                continue;
            }
            String content = truncateHistoryContent(message.getContent());
            if (content.isBlank()) {
                continue;
            }
            int nextTotal = totalChars + content.length();
            if (nextTotal > AGENT_HISTORY_TOTAL_CHARS && !newestFirst.isEmpty()) {
                break;
            }
            newestFirst.add(new AssistantRequest.ConversationHistoryMessage(role, content));
            totalChars = nextTotal;
            if (newestFirst.size() >= AGENT_HISTORY_MESSAGE_LIMIT) {
                break;
            }
        }

        Collections.reverse(newestFirst);
        request.setConversationHistory(newestFirst);
    }

    private String normalizeHistoryRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return ("user".equals(normalized) || "assistant".equals(normalized)) ? normalized : null;
    }

    private String truncateHistoryContent(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= AGENT_HISTORY_MESSAGE_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, AGENT_HISTORY_MESSAGE_CHARS - 3).trim() + "...";
    }

    private boolean shouldAutoTitle(AssistantConversation conversation) {
        return conversation.getTitle() == null || DEFAULT_TITLE.equals(conversation.getTitle());
    }

    private String buildTitle(String input) {
        String trimmed = input.trim();
        return trimmed.length() > 18 ? trimmed.substring(0, 18) + "..." : trimmed;
    }

    private String buildSummary(String input) {
        String trimmed = input.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    private String displayPrompt(AssistantRequest request) {
        String text = request.getMessage() == null ? null : request.getMessage().getText();
        if (text != null && !text.trim().isEmpty()) {
            return text.trim();
        }
        if (request.getSelection() != null && request.getSelection().getText() != null &&
                !request.getSelection().getText().trim().isEmpty()) {
            return "请帮我解释这段内容";
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            return "请查看我上传的 " + request.getAttachments().size() + " 个文件，并结合它回答。";
        }
        return "";
    }

    private String normalizeStreamEvent(String eventJson) {
        String normalized = eventJson == null ? "" : eventJson.trim();
        if (normalized.startsWith("data:")) {
            normalized = normalized.substring("data:".length()).trim();
        }
        return normalized;
    }

    private void captureAssistantStreamContent(
            String eventJson,
            StringBuilder deltaContent,
            StringBuilder completedContent,
            AssistantRunMetadataHolder runMetadataHolder,
            AtomicBoolean failed) {
        try {
            JsonNode event = objectMapper.readTree(eventJson);
            String type = event.path("type").asText("");
            if ("message.delta".equals(type)) {
                deltaContent.append(event.path("delta").asText(""));
            } else if ("message.completed".equals(type)) {
                completedContent.setLength(0);
                completedContent.append(event.path("content").asText(""));
                runMetadataHolder.parts = normalizeParts(event.get("parts"));
            } else if ("run.completed".equals(type) && event.has("run")) {
                runMetadataHolder.run = objectMapper.treeToValue(event.get("run"), com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse.class);
            } else if ("run.failed".equals(type)) {
                failed.set(true);
            }
        } catch (JsonProcessingException ignored) {
            // Invalid stream chunks are still forwarded; they are not persisted as assistant content.
        }
    }

    private void writeSseEvent(OutputStream outputStream, String eventJson) {
        try {
            outputStream.write(("data: " + eventJson + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String buildStreamFailureEvent(Exception e) {
        String message = e instanceof BizException ? e.getMessage() : ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE.getMessage();
        try {
            return objectMapper.writeValueAsString(new StreamFailureEvent(message));
        } catch (JsonProcessingException ignored) {
            return "{\"type\":\"run.failed\",\"error\":{\"code\":\"OPENAI_RUN_FAILED\",\"message\":\"学习助手暂时不可用\"}}";
        }
    }

    private record StreamFailureEvent(String type, StreamFailureError error) {
        private StreamFailureEvent(String message) {
            this("run.failed", new StreamFailureError("OPENAI_RUN_FAILED", message));
        }
    }

    private record StreamFailureError(String code, String message) {
    }

    private static final class AssistantRunMetadataHolder {
        private com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse run;
        private JsonNode parts;
    }

    private String generateShareToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String writeMessagesSnapshot(List<AssistantMessage> messages) {
        try {
            return objectMapper.writeValueAsString(messages.stream().map(this::toMessageResponse).toList());
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR);
        }
    }

    private List<AssistantMessageResponse> readMessagesSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, new TypeReference<List<AssistantMessageResponse>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR);
        }
    }

    private AssistantProjectResponse toProjectResponse(AssistantProject project) {
        return new AssistantProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private AssistantConversationSummaryResponse toSummaryResponse(AssistantConversation conversation) {
        return new AssistantConversationSummaryResponse(
                conversation.getConversationUid(),
                conversation.getProjectId(),
                conversation.getTitle(),
                conversation.getSummary(),
                Boolean.TRUE.equals(conversation.getPinned()),
                conversation.getArchivedAt() != null,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    private AssistantConversationDetailResponse toDetailResponse(
            AssistantConversation conversation,
            List<AssistantMessage> messages) {
        return new AssistantConversationDetailResponse(
                conversation.getConversationUid(),
                conversation.getProjectId(),
                conversation.getTitle(),
                conversation.getSummary(),
                Boolean.TRUE.equals(conversation.getPinned()),
                conversation.getArchivedAt() != null,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messages.stream().map(this::toMessageResponse).toList());
    }

    private AssistantMessageResponse toMessageResponse(AssistantMessage message) {
        return new AssistantMessageResponse(
                message.getMessageUid(),
                message.getRole(),
                message.getContent(),
                readPartsJson(message.getPartsJson()),
                message.getStatus(),
                message.getCreatedAt());
    }

    private JsonNode normalizeParts(JsonNode parts) {
        return parts != null && parts.isArray() && !parts.isEmpty() ? parts : null;
    }

    private String writePartsJson(JsonNode parts) {
        JsonNode normalized = normalizeParts(parts);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            log.warn("assistant parts serialization failed", e);
            return null;
        }
    }

    private JsonNode readPartsJson(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return null;
        }
        try {
            return normalizeParts(objectMapper.readTree(partsJson));
        } catch (JsonProcessingException e) {
            log.warn("invalid assistant parts JSON ignored", e);
            return null;
        }
    }

    private AssistantShareResponse toShareResponse(AssistantShare share) {
        return new AssistantShareResponse(
                share.getShareToken(),
                "/assistant/share/" + share.getShareToken(),
                share.getCreatedAt());
    }

    private String cleanRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
