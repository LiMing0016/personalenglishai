package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationDetailResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationSummaryResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantMessageResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AssistantConversationService {
    private static final String DEFAULT_TITLE = "新对话";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AssistantProjectMapper projectMapper;
    private final AssistantConversationMapper conversationMapper;
    private final AssistantMessageMapper messageMapper;
    private final AssistantShareMapper shareMapper;
    private final PythonAssistantClient pythonAssistantClient;
    private final ObjectMapper objectMapper;

    public AssistantConversationService(
            AssistantProjectMapper projectMapper,
            AssistantConversationMapper conversationMapper,
            AssistantMessageMapper messageMapper,
            AssistantShareMapper shareMapper,
            PythonAssistantClient pythonAssistantClient,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.shareMapper = shareMapper;
        this.pythonAssistantClient = pythonAssistantClient;
        this.objectMapper = objectMapper;
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
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        String prompt = cleanRequired(request.getMessage());
        int nextOrder = nextSortOrder(conversationUid);

        AssistantMessage userMessage = buildMessage(userId, conversationUid, "user", prompt, nextOrder);
        messageMapper.insert(userMessage);

        PythonAssistantClient.PythonAssistantReply reply = pythonAssistantClient.chat(
                new PythonAssistantClient.PythonAssistantChatRequest(
                        prompt,
                        conversationUid,
                        request.getStudyStage(),
                        request.getAssistantMode()),
                authorization);
        String replyText = reply == null ? "" : reply.text();
        if (replyText.isBlank()) {
            throw new BizException(ErrorCode.ASSISTANT_UPSTREAM_UNAVAILABLE);
        }

        messageMapper.insert(buildMessage(userId, conversationUid, "assistant", replyText, nextOrder + 1));
        String title = shouldAutoTitle(conversation) ? buildTitle(prompt) : conversation.getTitle();
        conversationMapper.updateTitleSummaryOwned(userId, conversationUid, title, buildSummary(prompt));
        return getConversation(userId, conversationUid);
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

    private int nextSortOrder(String conversationUid) {
        Integer max = messageMapper.selectMaxSortOrder(conversationUid);
        return max == null ? 1 : max + 1;
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
                message.getStatus(),
                message.getCreatedAt());
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
