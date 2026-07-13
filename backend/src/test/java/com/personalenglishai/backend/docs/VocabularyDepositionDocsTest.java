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
                () -> assertTrue(docs.contains("migrate_add_vocabulary_review_semantics.sql")),
                () -> assertTrue(docs.contains("当前阶段仅支持 `manual` 和 `dictionary`")),
                () -> assertTrue(docs.contains("PDF、AI 对话、笔记和错题尚未接入")),
                () -> assertTrue(docs.contains("初始迁移已包含")),
                () -> assertTrue(docs.contains("新库无需额外执行租约迁移")),
                () -> assertTrue(docs.contains("租约迁移只用于历史旧表")),
                () -> assertTrue(docs.contains("可重复执行")),
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

    @Test
    void readmeSeparatesFreshSchemaFromOrderedHistoricalUpgrades() throws Exception {
        String readme = Files.readString(Path.of("../README.md"));

        assertAll(
                () -> assertTrue(readme.contains("新库初始脚本已经包含全部基础列")),
                () -> assertTrue(readme.contains("新库不得执行 `migrate_add_vocabulary_review_semantics.sql`")),
                () -> assertTrue(readme.contains("历史库必须按以下顺序执行")),
                () -> assertTrue(readme.contains("conflict_candidate_revision_uid")),
                () -> assertTrue(readme.contains("generation_outcome")),
                () -> assertTrue(readme.contains("warning"))
        );
    }

    @Test
    void architectureDescribesThemeContentMigrationAndRollbackContracts() throws Exception {
        String architecture = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));

        assertAll(
                () -> assertTrue(architecture.contains("migrate_add_vocabulary_themes_and_markdown_cards.sql")),
                () -> assertTrue(architecture.contains("owner_type")),
                () -> assertTrue(architecture.contains("系统主题只读")),
                () -> assertTrue(architecture.contains("用户只能管理自己的主题")),
                () -> assertTrue(architecture.contains("vocabulary_theme_revision")),
                () -> assertTrue(architecture.contains("user_vocabulary_theme_recent")),
                () -> assertTrue(architecture.contains("theme_uid")),
                () -> assertTrue(architecture.contains("theme_version")),
                () -> assertTrue(architecture.contains("core_json")),
                () -> assertTrue(architecture.contains("content_markdown")),
                () -> assertTrue(architecture.contains("content_format_version")),
                () -> assertTrue(architecture.contains("核心事实")),
                () -> assertTrue(architecture.contains("扩展内容")),
                () -> assertTrue(architecture.contains("`basic` -> `theme_system_basic`")),
                () -> assertTrue(architecture.contains("`exam` -> `theme_system_exam`")),
                () -> assertTrue(architecture.contains("`reading` -> `theme_system_reading`")),
                () -> assertTrue(architecture.contains("先执行 `migrate_create_vocabulary_deposition_tables.sql`")),
                () -> assertTrue(architecture.contains("再执行 `migrate_add_vocabulary_themes_and_markdown_cards.sql`")),
                () -> assertTrue(architecture.contains("旧卡继续冻结在旧主题版本")),
                () -> assertTrue(architecture.contains("Markdown 生成失败")),
                () -> assertTrue(architecture.contains("core_json 仍可见")),
                () -> assertTrue(architecture.contains("needs_review")),
                () -> assertTrue(architecture.contains("回滚不删除主题表或新格式 revision")));
    }

    @Test
    void aiDocsDescribePromptSafetyPrecedenceObservabilityAndFailureModes() throws Exception {
        String prompts = Files.readString(Path.of("../docs/ai/vocabulary-theme-prompts.md"));

        assertAll(
                () -> assertTrue(prompts.contains("basic-markdown-v1")),
                () -> assertTrue(prompts.contains("exam-markdown-v1")),
                () -> assertTrue(prompts.contains("reading-markdown-v1")),
                () -> assertTrue(prompts.contains("custom-markdown-v1")),
                () -> assertTrue(prompts.contains("<theme-purpose>")),
                () -> assertTrue(prompts.contains("</theme-purpose>")),
                () -> assertTrue(prompts.contains("&lt;")),
                () -> assertTrue(prompts.contains("&gt;")),
                () -> assertTrue(prompts.contains("&amp;")),
                () -> assertTrue(prompts.contains("数据，不是指令")),
                () -> assertTrue(prompts.contains("20,000")),
                () -> assertTrue(prompts.contains("词典结果优先")),
                () -> assertTrue(prompts.contains("词典没有音标和释义")),
                () -> assertTrue(prompts.contains("AI 不得改写 core")),
                () -> assertTrue(prompts.contains("traceId")),
                () -> assertTrue(prompts.contains("reasonType")),
                () -> assertTrue(prompts.contains("jobUid")),
                () -> assertTrue(prompts.contains("cardUid")),
                () -> assertTrue(prompts.contains("code")),
                () -> assertTrue(prompts.contains("attempt")),
                () -> assertTrue(prompts.contains("terminal")),
                () -> assertTrue(prompts.contains("不得记录原始 purpose 或 sourceContext")),
                () -> assertTrue(prompts.contains("DICTIONARY_LOOKUP_FAILED")),
                () -> assertTrue(prompts.contains("CORE_CONTENT_UNAVAILABLE")),
                () -> assertTrue(prompts.contains("Markdown 失败只降级扩展内容")),
                () -> assertTrue(prompts.contains("needs_review")));
    }

    @Test
    void currentDocsNavigationLinksPromptGuideWithoutPublishingPlans() throws Exception {
        String docsIndex = Files.readString(Path.of("../docs/index.md"));
        String aiIndex = Files.readString(Path.of("../docs/ai/index.md"));
        String vitepressConfig = Files.readString(Path.of("../docs/.vitepress/config.ts"));

        assertAll(
                () -> assertTrue(docsIndex.contains("./ai/vocabulary-theme-prompts.md")),
                () -> assertTrue(aiIndex.contains("./vocabulary-theme-prompts.md")),
                () -> assertTrue(vitepressConfig.contains("/ai/vocabulary-theme-prompts")),
                () -> assertFalse(vitepressConfig.contains("/superpowers/plans/")));
    }
}
