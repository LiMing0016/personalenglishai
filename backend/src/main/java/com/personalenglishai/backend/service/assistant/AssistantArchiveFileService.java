package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class AssistantArchiveFileService {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_FOLDER_TITLE_LENGTH = 60;

    private final ObjectMapper objectMapper;

    public AssistantArchiveFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ArchiveFiles writeArchive(
            Path baseDir,
            String archiveUid,
            AssistantConversation conversation,
            List<AssistantMessage> messages,
            LocalDateTime archivedAt) {
        try {
            Path archiveDir = resolveArchiveDir(baseDir, archiveUid, conversation.getTitle(), archivedAt);
            Files.createDirectories(archiveDir);

            String markdown = buildMarkdown(conversation, messages, archivedAt);
            Map<String, Object> snapshot = buildSnapshot(archiveUid, conversation, messages, archivedAt);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
            String checksum = sha256(json);
            Map<String, Object> metadata = Map.of(
                    "archiveUid", archiveUid,
                    "conversationUid", conversation.getConversationUid(),
                    "title", conversation.getTitle(),
                    "messageCount", messages.size(),
                    "archivedAt", archivedAt.toString(),
                    "checksum", checksum);

            Path markdownPath = archiveDir.resolve("conversation.md");
            Path jsonPath = archiveDir.resolve("conversation.json");
            Path metadataPath = archiveDir.resolve("metadata.json");
            writeUtf8Atomically(markdownPath, markdown);
            writeUtf8Atomically(jsonPath, json);
            writeUtf8Atomically(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));

            return new ArchiveFiles(archiveDir, markdownPath, jsonPath, metadataPath, checksum);
        } catch (IOException e) {
            throw new UncheckedIOException("写入助手归档文件失败", e);
        }
    }

    private Path resolveArchiveDir(Path baseDir, String archiveUid, String title, LocalDateTime archivedAt) {
        String safeTitle = safeFilename(title == null || title.isBlank() ? "新对话" : title);
        if (safeTitle.length() > MAX_FOLDER_TITLE_LENGTH) {
            safeTitle = safeTitle.substring(0, MAX_FOLDER_TITLE_LENGTH).trim();
        }
        String folderName = safeTitle + "-" + archivedAt.format(STAMP_FORMATTER) + "-" + archiveUid;
        return baseDir.resolve(archivedAt.format(MONTH_FORMATTER)).resolve(folderName);
    }

    private String buildMarkdown(AssistantConversation conversation, List<AssistantMessage> messages, LocalDateTime archivedAt) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(nullToEmpty(conversation.getTitle())).append("\n\n");
        markdown.append("- 对话 ID：`").append(conversation.getConversationUid()).append("`\n");
        markdown.append("- 摘要：").append(nullToEmpty(conversation.getSummary())).append("\n");
        markdown.append("- 归档时间：").append(archivedAt).append("\n");
        markdown.append("- 消息数：").append(messages.size()).append("\n\n");
        markdown.append("---\n\n");
        for (AssistantMessage message : messages) {
            markdown.append("## ").append(roleLabel(message.getRole())).append("\n\n");
            markdown.append(nullToEmpty(message.getContent()).trim()).append("\n\n");
        }
        return markdown.toString();
    }

    private Map<String, Object> buildSnapshot(
            String archiveUid,
            AssistantConversation conversation,
            List<AssistantMessage> messages,
            LocalDateTime archivedAt) {
        return Map.of(
                "archiveUid", archiveUid,
                "archivedAt", archivedAt.toString(),
                "conversation", Map.of(
                        "id", conversation.getConversationUid(),
                        "title", nullToEmpty(conversation.getTitle()),
                        "summary", nullToEmpty(conversation.getSummary()),
                        "createdAt", conversation.getCreatedAt() == null ? "" : conversation.getCreatedAt().toString(),
                        "updatedAt", conversation.getUpdatedAt() == null ? "" : conversation.getUpdatedAt().toString()),
                "messages", messages.stream().map(message -> Map.of(
                        "id", message.getMessageUid(),
                        "role", message.getRole(),
                        "content", nullToEmpty(message.getContent()),
                        "status", nullToEmpty(message.getStatus()),
                        "sortOrder", message.getSortOrder() == null ? 0 : message.getSortOrder(),
                        "createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString()
                )).toList());
    }

    private String roleLabel(String role) {
        if ("user".equals(role)) {
            return "用户";
        }
        if ("assistant".equals(role)) {
            return "学习助手";
        }
        return role == null || role.isBlank() ? "消息" : role;
    }

    private String safeFilename(String value) {
        String normalized = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? "新对话" : normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void writeUtf8Atomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record ArchiveFiles(
            Path archiveDir,
            Path markdownPath,
            Path jsonPath,
            Path metadataPath,
            String checksum) {
    }
}
