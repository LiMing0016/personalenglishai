package com.personalenglishai.backend.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                () -> assertTrue(docs.contains("当前阶段支持 `manual`、`dictionary` 和 `ocr_image`")),
                () -> assertTrue(docs.contains("PDF、AI 对话、会话自动抽取、笔记同步和错题尚未接入")),
                () -> assertTrue(docs.contains("初始全量 schema 已包含")),
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
                () -> assertTrue(readme.contains("Docker 首次初始化仅执行 `backend/src/main/resources/db/schema.sql`")),
                () -> assertTrue(readme.contains("全新库只执行 `backend/src/main/resources/db/schema.sql`")),
                () -> assertTrue(readme.contains("`schema.sql` 已包含完整的主题表、索引、系统主题种子")),
                () -> assertTrue(readme.contains("新库不得执行 `migrate_add_vocabulary_review_semantics.sql` 或 "
                        + "`migrate_add_vocabulary_generation_metadata.sql`")),
                () -> assertTrue(readme.contains("历史库必须按以下顺序执行")),
                () -> assertHistoricalUpgradeOrder(readme,
                        "第四步执行审核语义增量，补充显式冲突候选和稳定生成结果字段：",
                        "migrate_add_vocabulary_review_semantics.sql",
                        "第五步执行生成元数据迁移，为已有 revision 补充可空的 JSON 审计字段：",
                        "migrate_add_vocabulary_generation_metadata.sql"),
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
                () -> assertHistoricalUpgradeOrder(architecture,
                        "历史库第四步执行审核语义增量，为已有表补充显式冲突候选和生成结果字段：",
                        "migrate_add_vocabulary_review_semantics.sql",
                        "历史库第五步执行生成元数据迁移，为已有 `vocabulary_card_revision` 增加可空的 JSON 审计字段：",
                        "migrate_add_vocabulary_generation_metadata.sql"),
                () -> assertTrue(architecture.contains("全新库只执行 `schema.sql`")),
                () -> assertTrue(architecture.contains("VOCABULARY_MYSQL_INTEGRATION_URL")),
                () -> assertTrue(architecture.contains("VOCABULARY_MYSQL_INTEGRATION_USERNAME")),
                () -> assertTrue(architecture.contains("VOCABULARY_MYSQL_INTEGRATION_PASSWORD")),
                () -> assertTrue(architecture.contains("CREATE DATABASE`、`CREATE TABLE`、`ALTER TABLE`、`INSERT`、`SELECT` 和 `DROP DATABASE")),
                () -> assertTrue(architecture.contains("peai_vocab_generation_metadata_")),
                () -> assertTrue(architecture.contains("旧卡继续冻结在旧主题版本")),
                () -> assertTrue(architecture.contains("Markdown 生成失败")),
                () -> assertTrue(architecture.contains("core_json 仍可见")),
                () -> assertTrue(architecture.contains("needs_review")),
                () -> assertTrue(architecture.contains("搜索同时覆盖 `core_json` 与 legacy `content_json`")),
                () -> assertTrue(architecture.contains("`generationOutcome` 和 `warning`")),
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
                () -> assertTrue(prompts.contains("JSON 序列化模型输入")),
                () -> assertTrue(prompts.contains("VocabularyMarkdownOutput")),
                () -> assertTrue(prompts.contains("都视为数据")),
                () -> assertTrue(prompts.contains("20,000")),
                () -> assertTrue(prompts.contains("词典结果优先")),
                () -> assertTrue(prompts.contains("core 缺少非空音标")),
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

    @Test
    void releaseDocsAndComposeKeepPythonVocabularyGenerationOptInAndExplicit() throws Exception {
        String rootEnvironment = Files.readString(Path.of("../.env.example"));
        String backendEnvironment = Files.readString(Path.of(".env.example"));
        String compose = Files.readString(Path.of("../docker-compose.yml"));
        String architecture = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));
        String prompts = Files.readString(Path.of("../docs/ai/vocabulary-theme-prompts.md"));
        String runbook = Files.readString(Path.of("../docs/runbooks/environment-variables.md"));

        assertAll(
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_GENERATION_PROVIDER=java")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_GENERATION_PYTHON_BASE_URL=http://127.0.0.1:8011")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS=60000")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_GENERATION_INTERNAL_TOKEN=")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_GENERATION_MODEL=gpt-5.4-mini")),
                () -> assertTrue(backendEnvironment.contains("VOCABULARY_GENERATION_PROVIDER=java")),
                () -> assertTrue(backendEnvironment.contains("VOCABULARY_GENERATION_PYTHON_BASE_URL=http://127.0.0.1:8011")),
                () -> assertTrue(backendEnvironment.contains("VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS=60000")),
                () -> assertTrue(backendEnvironment.contains("VOCABULARY_GENERATION_INTERNAL_TOKEN=")),
                () -> assertTrue(compose.contains("ASSISTANT_ORCHESTRATOR_BASE_URL=http://assistant-orchestrator:8002")),
                () -> assertTrue(compose.contains("AI_ORCHESTRATOR_BASE_URL=${AI_ORCHESTRATOR_BASE_URL:-http://assistant-orchestrator:8002}")),
                () -> assertTrue(compose.contains("VOCABULARY_GENERATION_PROVIDER=${VOCABULARY_GENERATION_PROVIDER:-java}")),
                () -> assertTrue(compose.contains("VOCABULARY_GENERATION_PYTHON_BASE_URL=http://assistant-orchestrator:8002")),
                () -> assertTrue(compose.contains("VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS=${VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS:-60000}")),
                () -> assertEquals(2, countOccurrences(compose, "VOCABULARY_GENERATION_INTERNAL_TOKEN=${VOCABULARY_GENERATION_INTERNAL_TOKEN:?")),
                () -> assertTrue(compose.contains("VOCABULARY_GENERATION_MODEL=${VOCABULARY_GENERATION_MODEL:-gpt-5.4-mini}")),
                () -> assertTrue(architecture.contains("Java 负责词典、generation job、租约、revision")),
                () -> assertTrue(architecture.contains("Python 负责 Prompt、模型调用、缺失 core 回填、Markdown 和 typed trace metadata")),
                () -> assertTrue(architecture.contains("Python provider 绕过旧 Java 七天生成缓存")),
                () -> assertTrue(architecture.contains("同一个 job attempt 内不允许静默回退")),
                () -> assertTrue(prompts.contains("策略 key 映射到 Python Prompt 资产")),
                () -> assertTrue(prompts.contains("Prompt version 由 Python 根据本次实际解析到的 Prompt 返回")),
                () -> assertTrue(prompts.contains("只有 Java 回滚 provider 使用七天生成缓存")),
                () -> assertTrue(prompts.contains("last_updated: 2026-07-14")),
                () -> assertTrue(prompts.contains("python/ai_orchestrator/workflows/vocabulary_card_generation.py")),
                () -> assertTrue(prompts.contains("python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md")),
                () -> assertTrue(prompts.contains("VocabularyMarkdownOutput")),
                () -> assertTrue(prompts.contains("未知 strategy key 会在模型调用前永久失败")),
                () -> assertFalse(prompts.contains("<theme-purpose>")),
                () -> assertTrue(runbook.startsWith("---\n")),
                () -> assertTrue(runbook.contains("last_updated: 2026-07-21")),
                () -> assertTrue(runbook.contains("Compose \u9ed8\u8ba4 `gpt-5.4-mini`")),
                () -> assertTrue(runbook.contains("\u76f4\u63a5\u8fd0\u884c\u771f\u5b9e\u6a21\u578b smoke \u65f6\u5fc5\u987b\u663e\u5f0f\u8bbe\u7f6e")),
                () -> assertTrue(runbook.contains("`java` -> `python`")),
                () -> assertTrue(runbook.contains("`VOCABULARY_GENERATION_PROVIDER=java`")),
                () -> assertTrue(runbook.contains("`VOCABULARY_GENERATION_PROVIDER=python`")),
                () -> assertTrue(runbook.contains("partial")),
                () -> assertTrue(runbook.contains("generation_metadata_json")),
                () -> assertTrue(runbook.contains("不会静默回退到 `java`")),
                () -> assertTrue(runbook.contains("不会使用旧 Java 七天缓存")),
                () -> assertTrue(runbook.contains("`migrate_add_vocabulary_generation_metadata.sql`")));
    }

    @Test
    void imageRecognitionDocsDescribeSafeRolloutAndSharedModelContract() throws Exception {
        String rootEnvironment = Files.readString(Path.of("../.env.example"));
        String compose = Files.readString(Path.of("../docker-compose.yml"));
        String architecture = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));
        String api = Files.readString(Path.of("../docs/api/vocabulary.md"));
        String ai = Files.readString(Path.of("../docs/ai/vocabulary-image-recognition.md"));

        assertAll(
                () -> assertTrue(rootEnvironment.contains("VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=false")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS=45000")),
                () -> assertTrue(rootEnvironment.contains("VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS=55000")),
                () -> assertEquals(2, countOccurrences(compose,
                        "VOCABULARY_IMAGE_RECOGNITION_MODEL=${VOCABULARY_IMAGE_RECOGNITION_MODEL:-}")),
                () -> assertTrue(compose.contains(
                        "VOCABULARY_IMAGE_RECOGNITION_PYTHON_BASE_URL=http://assistant-orchestrator:8002")),
                () -> assertTrue(architecture.contains("migrate_create_vocabulary_product_events.sql")),
                () -> assertTrue(architecture.contains("事件表迁移，再部署 Python，再部署 Java，最后部署 Web")),
                () -> assertTrue(api.contains("禁止 `rawText`、图片字节、base64")),
                () -> assertTrue(ai.contains("总调用次数最多 2 次")),
                () -> assertTrue(ai.contains("共享的单调时钟总预算")));
    }

    @Test
    void vocabularyApiDocsLockImageErrorsAndProductEventContract() throws Exception {
        String api = Files.readString(Path.of("../docs/api/vocabulary.md"));

        assertAll(
                () -> assertTrue(api.contains("`429010` | 本月 AI token 额度已用完")),
                () -> assertTrue(api.contains("`400052` | 图片为空、超过 10 MiB、MIME 不支持、扩展名与 MIME 不匹配，或 `file` part 数量不为 1")),
                () -> assertTrue(api.contains("`502050` | Python 或模型响应已返回，但无法通过图片识别结构化契约校验")),
                () -> assertTrue(api.contains("`503050` | Python 服务未配置、不可达、内部鉴权失败，或模型上游不可用")),
                () -> assertTrue(api.contains("`504050` | 图片识别超过 Java 调用 Python 的超时预算")),
                () -> assertTrue(api.contains("POST /api/vocabulary/product-events/batch")),
                () -> assertTrue(api.contains("`events` 必填，必须是包含 1..50 个 `Event` 的数组")),
                () -> assertTrue(api.contains("| `eventUid` | 是 | string，1..128 字符")),
                () -> assertTrue(api.contains("| `eventName` | 是 | string，1..64 字符")),
                () -> assertTrue(api.contains("| `traceId` | 否 | string，最多 128 字符")),
                () -> assertTrue(api.contains("| `sessionId` | 是 | string，1..128 字符")),
                () -> assertTrue(api.contains("| `cardUid` | 否 | string，最多 64 字符")),
                () -> assertTrue(api.contains("| `occurredAt` | 是 | 不带时区的 ISO-8601 本地日期时间")),
                () -> assertTrue(api.contains("| `properties` | 否 | JSON object")),
                () -> assertTrue(api.contains("`vocabulary-event:<32 位小写十六进制>`")),
                () -> assertTrue(api.contains("`vocabulary-capture-submitted:<64 位小写十六进制>`")),
                () -> assertTrue(api.contains("`vocabulary-cards-ready:rev_<32 位小写十六进制>`")),
                () -> assertTrue(api.contains("`vocabulary-session:<32 位小写十六进制或小写 UUID>`")),
                () -> assertTrue(api.contains("`vocab-image-<32 位小写十六进制>` 或 `capture:<64 位小写十六进制>`")),
                () -> assertTrue(api.contains("`card_<32 位小写十六进制>`")),
                () -> assertTrue(api.contains("| `vocabulary_image_recognition_started` | `sourceType` |")),
                () -> assertTrue(api.contains("| `vocabulary_image_recognition_completed` | `sourceType`, `durationMs`, `candidateCount`, `suspectedCount`, `provider`, `model`, `promptVersion`, `modelCallCount`, `warningCodes`, `outcome` |")),
                () -> assertTrue(api.contains("| `vocabulary_image_candidates_confirmed` | `sourceType`, `candidateCount`, `suspectedCount`, `selectedCount`, `editedCount`, `removedCount`, `resolutionCount` |")),
                () -> assertTrue(api.contains("| `vocabulary_capture_submitted` | `sourceType`, `successCount`, `failedCount` |")),
                () -> assertTrue(api.contains("| `vocabulary_cards_ready` | `sourceType` |")),
                () -> assertTrue(api.contains("| `vocabulary_learning_started` | `sourceType` |")),
                () -> assertTrue(api.contains("`manual`、`dictionary`、`ocr_image`")),
                () -> assertTrue(api.contains("整数，范围 0..1,000,000")),
                () -> assertTrue(api.contains("整数，范围 0..86,400,000")),
                () -> assertTrue(api.contains("整数，范围 0..100")),
                () -> assertTrue(api.contains("`success`、`failed`")),
                () -> assertTrue(api.contains("只能是 `openai`")),
                () -> assertTrue(api.contains("必须与服务端 `VOCABULARY_IMAGE_RECOGNITION_MODEL` 的精确配置值一致")),
                () -> assertTrue(api.contains("只能是 `vocabulary-image-recognition-v1`")),
                () -> assertTrue(api.contains("`CANDIDATE_LIMIT_REACHED`、`DICTIONARY_VERIFICATION_UNAVAILABLE`")),
                () -> assertTrue(api.contains("未知键、敏感键、嵌套值、错误类型、错误事件名或错误 ID 均返回 HTTP 400")),
                () -> assertTrue(api.contains("\"data\": {")),
                () -> assertTrue(api.contains("\"accepted\": 1")),
                () -> assertTrue(api.contains("\"duplicate\": 1")),
                () -> assertFalse(api.contains("\"acceptedCount\"")),
                () -> assertFalse(api.contains("\"duplicateCount\"")),
                () -> assertTrue(api.contains("`(user_id, event_uid)`")));
    }

    private static void assertHistoricalUpgradeOrder(
            String document,
            String reviewStepTitle,
            String reviewMigration,
            String metadataStepTitle,
            String metadataMigration) {
        HistoricalUpgradeStep review = historicalUpgradeStep(document, reviewStepTitle);
        HistoricalUpgradeStep metadata = historicalUpgradeStep(document, metadataStepTitle);

        assertEquals(mysqlMigrationCommand(reviewMigration), review.command());
        assertEquals(mysqlMigrationCommand(metadataMigration), metadata.command());
        assertTrue(review.position() < metadata.position(),
                "the review-semantics migration step must precede the generation-metadata migration step");
    }

    private static HistoricalUpgradeStep historicalUpgradeStep(String document, String stepTitle) {
        Pattern titlePattern = Pattern.compile("(?m)^" + Pattern.quote(stepTitle) + "$");
        assertEquals(1, titlePattern.matcher(document).results().count(),
                "historical upgrade step title must be unique: " + stepTitle);

        Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(stepTitle)
                + "\\R+```powershell\\R(?<command>mysql -u <user> -p <database> < [^\\r\\n]+)\\R```");
        Matcher matcher = pattern.matcher(document);
        assertTrue(matcher.find(), "missing historical upgrade step: " + stepTitle);
        HistoricalUpgradeStep step = new HistoricalUpgradeStep(matcher.start(), matcher.group("command"));
        assertFalse(matcher.find(), "historical upgrade step must be unique: " + stepTitle);
        return step;
    }

    private static String mysqlMigrationCommand(String migration) {
        return "mysql -u <user> -p <database> < backend/src/main/resources/db/" + migration;
    }

    private static int countOccurrences(String value, String needle) {
        return value.split(Pattern.quote(needle), -1).length - 1;
    }

    private record HistoricalUpgradeStep(int position, String command) {
    }
}
