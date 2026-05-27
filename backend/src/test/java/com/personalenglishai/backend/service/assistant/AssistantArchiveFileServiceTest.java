package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantArchiveFileServiceTest {
    @TempDir
    Path tempDir;

    private final AssistantArchiveFileService service = new AssistantArchiveFileService(new ObjectMapper());

    @Test
    void writeArchive_writesMarkdownJsonAndMetadataUnderSafeFolder() throws Exception {
        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-1");
        conversation.setTitle("OpenAI/学习: 对话?");
        conversation.setSummary("结构化输出学习");
        conversation.setCreatedAt(LocalDateTime.of(2026, 5, 23, 20, 0));
        conversation.setUpdatedAt(LocalDateTime.of(2026, 5, 23, 20, 5));

        AssistantMessage userMessage = message("msg-1", "user", "什么是 Structured Outputs?", 1);
        AssistantMessage assistantMessage = message("msg-2", "assistant", "它用于约束模型输出 JSON schema。", 2);

        AssistantArchiveFileService.ArchiveFiles files = service.writeArchive(
                tempDir,
                "archive-1",
                conversation,
                List.of(userMessage, assistantMessage),
                LocalDateTime.of(2026, 5, 23, 20, 10));

        assertThat(files.archiveDir()).exists();
        assertThat(files.archiveDir().getFileName().toString()).doesNotContain("/", ":", "?");
        assertThat(files.markdownPath()).exists();
        assertThat(files.jsonPath()).exists();
        assertThat(files.metadataPath()).exists();
        assertThat(files.checksum()).hasSize(64);

        String markdown = Files.readString(files.markdownPath());
        assertThat(markdown).contains("# OpenAI/学习: 对话?");
        assertThat(markdown).contains("## 用户");
        assertThat(markdown).contains("什么是 Structured Outputs?");
        assertThat(markdown).contains("## 学习助手");
        assertThat(markdown).contains("它用于约束模型输出 JSON schema。");
    }

    private AssistantMessage message(String uid, String role, String content, int sortOrder) {
        AssistantMessage message = new AssistantMessage();
        message.setMessageUid(uid);
        message.setConversationUid("conv-1");
        message.setRole(role);
        message.setContent(content);
        message.setStatus("done");
        message.setSortOrder(sortOrder);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 23, 20, sortOrder));
        return message;
    }
}
