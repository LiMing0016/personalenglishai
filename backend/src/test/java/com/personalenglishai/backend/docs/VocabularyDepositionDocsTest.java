package com.personalenglishai.backend.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VocabularyDepositionDocsTest {

    @Test
    void docsDescribeMigrationWorkerAndOwnershipBoundary() throws Exception {
        String docs = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));

        assertAll(
                () -> assertTrue(docs.contains("migrate_create_vocabulary_deposition_tables.sql")),
                () -> assertTrue(docs.contains("migrate_add_vocabulary_generation_job_leases.sql")),
                () -> assertTrue(docs.contains("migrate_make_vocabulary_identity_exact.sql")),
                () -> assertTrue(docs.contains("当前阶段仅支持 `manual` 和 `dictionary`")),
                () -> assertTrue(docs.contains("PDF、AI 对话、笔记和错题尚未接入")),
                () -> assertTrue(docs.contains("初始迁移已包含")),
                () -> assertTrue(docs.contains("新库只执行初始迁移，不得再执行租约迁移")),
                () -> assertTrue(docs.contains("租约迁移只用于历史旧表")),
                () -> assertTrue(docs.contains("vocabulary.generation.scheduler.enabled")),
                () -> assertTrue(docs.contains("user_dictionary_word_state")),
                () -> assertTrue(docs.contains("vocabulary_card")),
                () -> assertTrue(docs.contains("vocabulary_generation_job")),
                () -> assertTrue(docs.contains("POST /api/vocabulary/captures")),
                () -> assertTrue(docs.contains("baseRevisionUid")),
                () -> assertTrue(docs.contains("409030")),
                () -> assertTrue(docs.contains("/app/vocabulary/cards/:cardUid")),
                () -> assertTrue(docs.contains("keep_current")),
                () -> assertTrue(docs.contains("use_ai")),
                () -> assertTrue(docs.contains("merge_fields")),
                () -> assertTrue(docs.contains("system_merge")),
                () -> assertTrue(docs.contains("templateKey")),
                () -> assertTrue(docs.contains("`sort=recent`")),
                () -> assertTrue(docs.contains("`sort=az`")),
                () -> assertTrue(docs.contains("active revision 的 `definitions`")),
                () -> assertTrue(docs.contains("数据库和基础设施异常必须向上抛出")),
                () -> assertTrue(docs.contains("同一条 MySQL 多表更新")),
                () -> assertFalse(docs.contains("PaddleOCR")));
    }
}
