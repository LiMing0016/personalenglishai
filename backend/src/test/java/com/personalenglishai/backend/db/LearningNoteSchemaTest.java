package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LearningNoteSchemaTest {

    @Test
    void migrationCreatesGenericLearningNoteTable() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migrate_create_learning_note_tables.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS learning_note");
        assertThat(sql).contains("note_uid");
        assertThat(sql).contains("user_id");
        assertThat(sql).contains("type");
        assertThat(sql).contains("content_markdown");
        assertThat(sql).contains("structured_payload");
        assertThat(sql).contains("source_conversation_uid");
        assertThat(sql).contains("source_message_uid");
        assertThat(sql).contains("source_text");
        assertThat(sql).contains("deleted_at");
        assertThat(sql).contains("UNIQUE KEY uk_learning_note_uid");
        assertThat(sql).contains("KEY idx_learning_note_user_type");
    }
}
