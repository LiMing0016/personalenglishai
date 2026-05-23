package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantArchiveSettingsResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationSummaryResponse;
import com.personalenglishai.backend.controller.dto.assistant.UpdateAssistantArchiveSettingsRequest;
import com.personalenglishai.backend.entity.assistant.AssistantArchiveSetting;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantConversationArchive;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.mapper.assistant.AssistantArchiveSettingMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationArchiveMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AssistantConversationArchiveService {
    private static final String DEFAULT_ARCHIVE_FOLDER = "PEAI/assistant-archives";

    private final AssistantConversationMapper conversationMapper;
    private final AssistantMessageMapper messageMapper;
    private final AssistantArchiveSettingMapper archiveSettingMapper;
    private final AssistantConversationArchiveMapper archiveMapper;
    private final AssistantArchiveFileService archiveFileService;

    public AssistantConversationArchiveService(
            AssistantConversationMapper conversationMapper,
            AssistantMessageMapper messageMapper,
            AssistantArchiveSettingMapper archiveSettingMapper,
            AssistantConversationArchiveMapper archiveMapper,
            AssistantArchiveFileService archiveFileService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.archiveSettingMapper = archiveSettingMapper;
        this.archiveMapper = archiveMapper;
        this.archiveFileService = archiveFileService;
    }

    public AssistantArchiveSettingsResponse getArchiveSettings(Long userId) {
        String defaultDir = defaultArchiveDir().toString();
        AssistantArchiveSetting setting = archiveSettingMapper.findByUserId(userId);
        if (setting == null || setting.getArchiveDir() == null || setting.getArchiveDir().isBlank()) {
            return new AssistantArchiveSettingsResponse(defaultDir, defaultDir, false);
        }
        return new AssistantArchiveSettingsResponse(setting.getArchiveDir(), defaultDir, true);
    }

    @Transactional
    public AssistantArchiveSettingsResponse updateArchiveSettings(
            Long userId,
            UpdateAssistantArchiveSettingsRequest request) {
        Path archiveDir = resolveArchiveDir(request == null ? null : request.getArchiveDir());
        AssistantArchiveSetting setting = new AssistantArchiveSetting();
        setting.setUserId(userId);
        setting.setArchiveDir(archiveDir.toString());
        archiveSettingMapper.upsert(setting);
        return getArchiveSettings(userId);
    }

    @Transactional
    public AssistantConversationSummaryResponse archiveConversation(Long userId, String conversationUid) {
        AssistantConversation conversation = ensureConversation(userId, conversationUid);
        List<AssistantMessage> messages = messageMapper.selectByConversationUid(conversationUid);
        LocalDateTime archivedAt = LocalDateTime.now();
        String archiveUid = "archive-" + UUID.randomUUID();
        AssistantArchiveFileService.ArchiveFiles files = null;

        try {
            files = archiveFileService.writeArchive(
                    currentArchiveDir(userId),
                    archiveUid,
                    conversation,
                    messages,
                    archivedAt);
            archiveMapper.insert(toArchiveRecord(archiveUid, conversation, messages, files, archivedAt));
            conversationMapper.setArchivedAtOwned(userId, conversationUid, archivedAt);
            return toSummaryResponse(ensureConversation(userId, conversationUid));
        } catch (RuntimeException e) {
            if (files != null) {
                deleteArchiveFolderQuietly(files.archiveDir());
            }
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "归档失败，请检查本地归档目录是否可写");
        }
    }

    @Transactional
    public AssistantConversationSummaryResponse restoreConversation(Long userId, String conversationUid) {
        ensureConversation(userId, conversationUid);
        AssistantConversationArchive archive = archiveMapper.findLatestActive(userId, conversationUid);
        if (archive != null) {
            archiveMapper.markRestored(archive.getId(), userId);
        }
        conversationMapper.setArchivedAtOwned(userId, conversationUid, null);
        return toSummaryResponse(ensureConversation(userId, conversationUid));
    }

    private AssistantConversation ensureConversation(Long userId, String conversationUid) {
        AssistantConversation conversation = conversationMapper.findOwnedActiveByUid(userId, conversationUid);
        if (conversation == null) {
            throw new BizException(ErrorCode.ASSISTANT_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private Path currentArchiveDir(Long userId) {
        AssistantArchiveSetting setting = archiveSettingMapper.findByUserId(userId);
        if (setting == null || setting.getArchiveDir() == null || setting.getArchiveDir().isBlank()) {
            return defaultArchiveDir();
        }
        return resolveArchiveDir(setting.getArchiveDir());
    }

    private Path resolveArchiveDir(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return defaultArchiveDir();
        }
        try {
            return Path.of(trimmed).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "归档目录路径无效");
        }
    }

    private Path defaultArchiveDir() {
        return Path.of(System.getProperty("user.home"), "Documents", DEFAULT_ARCHIVE_FOLDER)
                .toAbsolutePath()
                .normalize();
    }

    private AssistantConversationArchive toArchiveRecord(
            String archiveUid,
            AssistantConversation conversation,
            List<AssistantMessage> messages,
            AssistantArchiveFileService.ArchiveFiles files,
            LocalDateTime archivedAt) {
        AssistantConversationArchive archive = new AssistantConversationArchive();
        archive.setArchiveUid(archiveUid);
        archive.setConversationUid(conversation.getConversationUid());
        archive.setUserId(conversation.getUserId());
        archive.setTitle(conversation.getTitle() == null || conversation.getTitle().isBlank()
                ? "新对话"
                : conversation.getTitle());
        archive.setSummary(conversation.getSummary());
        archive.setMessageCount(messages.size());
        archive.setArchiveDir(files.archiveDir().toString());
        archive.setMarkdownPath(files.markdownPath().toString());
        archive.setJsonPath(files.jsonPath().toString());
        archive.setMetadataPath(files.metadataPath().toString());
        archive.setChecksum(files.checksum());
        archive.setStatus("archived");
        archive.setArchivedAt(archivedAt);
        return archive;
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

    private void deleteArchiveFolderQuietly(Path archiveDir) {
        try (Stream<Path> paths = Files.walk(archiveDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup. The user can remove leftovers from the archive folder.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup. The user can remove leftovers from the archive folder.
        }
    }
}
