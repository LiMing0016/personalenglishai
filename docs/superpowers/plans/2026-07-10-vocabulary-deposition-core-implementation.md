# Vocabulary Deposition Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent vocabulary deposition loop where manual input and dictionary favorites immediately become durable cards, AI fills a selected template asynchronously, exact duplicates merge sources, and user edits are protected by revisions and explicit conflict resolution.

**Architecture:** Add a user-owned vocabulary aggregate beside the shared dictionary tables: one card identity, append-only sources and revisions, and database-backed generation jobs. Spring services own capture transactions and version rules, a scheduled worker reuses the existing local-first dictionary lookup and `OpenAiClient`, and Vue consumes the REST API through TanStack Query while splitting the current large vocabulary view into focused components.

**Tech Stack:** Java 17, Spring Boot 3.2.5, MyBatis 3.0.3, MySQL 8, Jackson, JUnit 5, Mockito, Vue 3, TypeScript 5.5, TanStack Vue Query 5, Axios, Vite, Node test runner.

## Global Constraints

- Work on the existing `codex/word-card-design` branch; do not mix or revert unrelated dirty-worktree changes.
- Phase 1 accepts only `manual` capture and existing dictionary favorite actions; assistant, PDF, web, and external adapters remain outside this implementation.
- `vocabulary_card` is the only canonical user card identity; shared `dictionary_*` content and `user_dictionary_word_state` remain separate concerns.
- Exact duplicate identity is `(user_id, language, normalized_term)`; never auto-merge morphological or semantic neighbors.
- Normalize with Unicode NFKC, lowercase comparison, collapsed whitespace, stripped wrapping punctuation, and removed syllable separators/soft hyphens while preserving internal hyphens and apostrophes.
- Persist card, source, and `pending` generation job in one transaction before returning capture success.
- Generation is database-backed and restart-safe; background dictionary enrichment must never increment `lookup_count`.
- AI output must pass the selected template's structured JSON validation before a revision can become active.
- Limit captured `contextText` to 2,000 characters; trim template scalar fields to 2,000 characters and individual list items to 500 characters before persistence or AI submission.
- AI logs may contain card/job UIDs, model, latency, and error codes, but never auth tokens, account data, or raw source context.
- User edits are append-only revisions and require `baseRevisionUid`; stale writes return HTTP `409` and never silently overwrite.
- Repeated captures append an idempotent source but do not change the active template, overwrite user content, or trigger regeneration for an already-active card.
- Do not add frontend or backend dependencies for this feature.
- Service state stays in TanStack Query; local component state owns only input drafts, filters, selection, and edit forms.
- Keep the current vocabulary visual language and shared app rail; do not turn the feature into a landing page or add nested cards.
- All backend tasks run focused Maven tests; all frontend tasks run focused Node tests and `npm run build` before completion.

---

## File Structure

**Backend database and domain**

- `backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql`: deployable migration for cards, sources, revisions, preferences, and generation jobs.
- `backend/src/main/resources/db/schema.sql`: fresh-install mirror of the migration.
- `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/*.java`: MyBatis persistence records only.
- `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/*.java` and `backend/src/main/resources/mapper/Vocabulary*.xml`: aggregate persistence and atomic status transitions.

**Backend behavior and API**

- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyTermNormalizer.java`: the single normalization implementation used by every source.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyTemplateRegistry.java`: built-in `basic`, `exam`, and `reading` definitions plus structured validators.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`: transactional capture, exact dedupe, source idempotency, soft-delete restoration, and initial job creation.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`: list/detail/edit/delete/regenerate/retry/history/conflict use cases and ownership checks.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java`: local-first dictionary enrichment, prompt construction, AI JSON parsing, and template validation.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`: job claiming, retry state, revision commit, and stale-result conflict behavior.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationScheduler.java`: small configurable scheduled batch trigger.
- `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`: authenticated REST surface under `/api/vocabulary`.
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/*.java`: request and response records shared by the controller and services.

**Frontend**

- `web/src/api/vocabulary.ts`: typed API client and response contracts.
- `web/src/features/vocabulary/captureTerms.ts`: pure bulk-input parsing and client request ID helpers.
- `web/src/composables/useVocabularyCards.ts`: query keys, list/detail polling, mutations, and invalidation.
- `web/src/components/vocabulary/VocabularyCapturePanel.vue`: manual/bulk capture and template selection.
- `web/src/components/vocabulary/VocabularyCardList.vue`: filters, durable card rows, statuses, and pagination.
- `web/src/components/vocabulary/VocabularyCardInspector.vue`: structured content, edit form, sources, revisions, retries, and conflict choices.
- `web/src/views/VocabularyView.vue`: reduced to route/query orchestration and composition of the three components.

The work remains one vertical plan because every subsystem serves the same independently testable workflow: capture a word, refresh the page, observe generation, edit it safely, and re-capture it without duplication.

---

### Task 1: Create the vocabulary persistence schema

**Files:**
- Create: `backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyDepositionSchemaTest.java`

**Interfaces:**
- Consumes: existing MySQL 8 conventions in `schema.sql`, especially `utf8mb4_unicode_ci`, `DATETIME`, JSON columns, and named unique indexes.
- Produces: tables `vocabulary_card`, `vocabulary_card_source`, `vocabulary_card_revision`, `user_vocabulary_preference`, and `vocabulary_generation_job` with the exact keys later MyBatis mappers consume.

- [ ] **Step 1: Write the failing schema contract test**

```java
package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyDepositionSchemaTest {
    @Test
    void schemaAndMigrationContainVocabularyAggregate() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql"));
        for (String sql : new String[]{schema, migration}) {
            assertAll(
                    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_card")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_vocabulary_card_identity (user_id, language, normalized_term)")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_vocabulary_source_idempotency (user_id, idempotency_key)")),
                    () -> assertTrue(sql.contains("author_type VARCHAR(24) NOT NULL")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_user_vocabulary_preference (user_id)")),
                    () -> assertTrue(sql.contains("available_at DATETIME NOT NULL"))
            );
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm the migration is missing**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyDepositionSchemaTest test`

Expected: FAIL because `migrate_create_vocabulary_deposition_tables.sql` does not exist.

- [ ] **Step 3: Add the five tables to the migration and append the same DDL to `schema.sql`**

Use these column and key contracts in both files:

```sql
CREATE TABLE IF NOT EXISTS vocabulary_card (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    card_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    original_term VARCHAR(255) NOT NULL,
    normalized_term VARCHAR(255) NOT NULL,
    display_term VARCHAR(255) NOT NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    active_revision_uid VARCHAR(64) NULL,
    last_captured_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_card_uid (card_uid),
    UNIQUE KEY uk_vocabulary_card_identity (user_id, language, normalized_term),
    KEY idx_vocabulary_card_user_status (user_id, status, updated_at),
    KEY idx_vocabulary_card_user_capture (user_id, last_captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_card_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_ref VARCHAR(128) NULL,
    source_title VARCHAR(255) NULL,
    source_url VARCHAR(1024) NULL,
    context_text TEXT NULL,
    raw_term VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    captured_at DATETIME NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_source_uid (source_uid),
    UNIQUE KEY uk_vocabulary_source_idempotency (user_id, idempotency_key),
    KEY idx_vocabulary_source_card (card_uid, captured_at),
    CONSTRAINT fk_vocabulary_source_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_card_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    base_revision_uid VARCHAR(64) NULL,
    author_type VARCHAR(24) NOT NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    content_json JSON NOT NULL,
    change_summary VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_revision_uid (revision_uid),
    KEY idx_vocabulary_revision_card (card_uid, created_at),
    CONSTRAINT fk_vocabulary_revision_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_vocabulary_preference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    default_template_key VARCHAR(32) NOT NULL DEFAULT 'basic',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_vocabulary_preference (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_generation_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    base_revision_uid VARCHAR(64) NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    request_json JSON NOT NULL,
    result_revision_uid VARCHAR(64) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    available_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_job_uid (job_uid),
    KEY idx_vocabulary_job_claim (status, available_at, id),
    KEY idx_vocabulary_job_card (card_uid, created_at),
    CONSTRAINT fk_vocabulary_job_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 4: Run the schema test**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyDepositionSchemaTest test`

Expected: PASS, 1 test, 0 failures.

- [ ] **Step 5: Commit the database contract**

```powershell
git add backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql backend/src/main/resources/db/schema.sql backend/src/test/java/com/personalenglishai/backend/db/VocabularyDepositionSchemaTest.java
git commit -m "feat(vocabulary): 新增单词沉淀数据表"
```

### Task 2: Implement normalization and the three template contracts

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyTermNormalizer.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyTemplateRegistry.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyTemplateResponse.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyTermNormalizerTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyTemplateRegistryTest.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper` already provided by Spring.
- Produces: `String VocabularyTermNormalizer.normalize(String raw)`, `boolean VocabularyTermNormalizer.isReviewRequired(String raw, String normalized)`, `TemplateDefinition VocabularyTemplateRegistry.require(String key)`, `List<VocabularyTemplateResponse> VocabularyTemplateRegistry.list()`, and `void VocabularyTemplateRegistry.validate(String key, JsonNode content)`.

- [ ] **Step 1: Write failing normalization and validation tests**

```java
class VocabularyTermNormalizerTest {
    private final VocabularyTermNormalizer normalizer = new VocabularyTermNormalizer();

    @Test void normalizesDictionaryAndPastedForms() {
        assertAll(
                () -> assertEquals("innovative", normalizer.normalize("  (In·nova\u00ADtive). ")),
                () -> assertEquals("state-of-the-art", normalizer.normalize("STATE-OF-THE-ART")),
                () -> assertEquals("don't", normalizer.normalize("‘Don't’")),
                () -> assertEquals("machine learning", normalizer.normalize("machine   learning"))
        );
    }

    @Test void routesLongOrNonEnglishInputToReview() {
        assertTrue(normalizer.isReviewRequired("x".repeat(121), "x".repeat(121)));
        assertTrue(normalizer.isReviewRequired("你好", "你好"));
        assertFalse(normalizer.isReviewRequired("sustainable", "sustainable"));
    }
}

class VocabularyTemplateRegistryTest {
    private final VocabularyTemplateRegistry registry = new VocabularyTemplateRegistry(new ObjectMapper());

    @Test void exposesStableBuiltInTemplates() {
        assertEquals(List.of("basic", "exam", "reading"),
                registry.list().stream().map(VocabularyTemplateResponse::key).toList());
        assertEquals(1, registry.require("basic").version());
    }

    @Test void rejectsContentMissingRequiredBasicFields() {
        JsonNode invalid = new ObjectMapper().createObjectNode().put("term", "innovative");
        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }
}
```

- [ ] **Step 2: Run both tests and confirm the classes are absent**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyTermNormalizerTest,VocabularyTemplateRegistryTest test`

Expected: compilation FAIL for missing vocabulary service classes.

- [ ] **Step 3: Implement one normalizer and explicit structured template definitions**

```java
public final class VocabularyTermNormalizer {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern WRAPPING = Pattern.compile("^[\\p{Punct}\\p{Ps}\\p{Pe}‘’“”]+|[\\p{Punct}\\p{Ps}\\p{Pe}‘’“”]+$");
    private static final Pattern ENGLISH_TERM = Pattern.compile("[a-z]+(?:[ '-][a-z]+)*");

    public String normalize(String raw) {
        if (raw == null) return "";
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace("·", "").replace("•", "").replace("\u00AD", "")
                .trim().toLowerCase(Locale.ROOT);
        value = WRAPPING.matcher(value).replaceAll("").trim();
        return SPACE.matcher(value).replaceAll(" ");
    }

    public boolean isReviewRequired(String raw, String normalized) {
        return raw == null || raw.length() > 120 || normalized.isBlank()
                || !ENGLISH_TERM.matcher(normalized).matches();
    }
}
```

```java
public record VocabularyTemplateResponse(String key, int version, String name, List<String> fields) {}

public final class VocabularyTemplateRegistry {
    public record TemplateDefinition(String key, int version, String name, List<String> requiredFields) {}
    private final Map<String, TemplateDefinition> templates = new LinkedHashMap<>();

    public VocabularyTemplateRegistry(ObjectMapper ignored) {
        templates.put("basic", new TemplateDefinition("basic", 1, "基础单词卡",
                List.of("term", "phonetic", "partOfSpeech", "definitions", "examples", "notes")));
        templates.put("exam", new TemplateDefinition("exam", 1, "考试词汇卡",
                List.of("term", "phonetic", "partOfSpeech", "definitions", "examTips", "collocations", "examples", "notes")));
        templates.put("reading", new TemplateDefinition("reading", 1, "阅读语境卡",
                List.of("term", "definitions", "sourceContext", "contextExplanation", "paraphrases", "notes")));
    }

    public TemplateDefinition require(String key) {
        TemplateDefinition value = templates.get(key == null || key.isBlank() ? "basic" : key);
        if (value == null) throw new IllegalArgumentException("unsupported template: " + key);
        return value;
    }

    public List<VocabularyTemplateResponse> list() {
        return templates.values().stream()
                .map(t -> new VocabularyTemplateResponse(t.key(), t.version(), t.name(), t.requiredFields()))
                .toList();
    }

    public void validate(String key, JsonNode content) {
        if (content == null || !content.isObject()) throw new IllegalArgumentException("content must be an object");
        for (String field : require(key).requiredFields()) {
            if (!content.has(field) || content.get(field).isNull()) {
                throw new IllegalArgumentException("missing template field: " + field);
            }
        }
        if (!content.get("term").isTextual() || !content.get("definitions").isArray()) {
            throw new IllegalArgumentException("invalid term or definitions field");
        }
    }
}
```

Register both service classes with `@Component` so later services inject the same behavior.

- [ ] **Step 4: Run focused tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyTermNormalizerTest,VocabularyTemplateRegistryTest test`

Expected: PASS, all normalization examples and all three template keys succeed.

- [ ] **Step 5: Commit the domain rules**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/test/java/com/personalenglishai/backend/service/vocabulary
git commit -m "feat(vocabulary): 新增词条标准化与卡片模板"
```

### Task 3: Add MyBatis entities and aggregate mappers

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCard.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardSource.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyGenerationJob.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/UserVocabularyPreference.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyCardMapper.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularySourceMapper.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyRevisionMapper.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyGenerationJobMapper.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/UserVocabularyPreferenceMapper.java`
- Create: `backend/src/main/resources/mapper/VocabularyCardMapper.xml`
- Create: `backend/src/main/resources/mapper/VocabularySourceMapper.xml`
- Create: `backend/src/main/resources/mapper/VocabularyRevisionMapper.xml`
- Create: `backend/src/main/resources/mapper/VocabularyGenerationJobMapper.xml`
- Create: `backend/src/main/resources/mapper/UserVocabularyPreferenceMapper.xml`
- Create: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyMapperContractTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java`

**Interfaces:**
- Consumes: Task 1 table/column names and Task 2 normalized terms/template versions.
- Produces: mapper methods `findByIdentityIncludingDeleted`, `findByUidIncludingDeleted`, `insert`, `restoreAndTouch`, `touch`, `listByUser`, `countByUser`, `findOwnedByUid`, `updateActiveRevision`, `markConflictCandidate`, `markGenerationFailed`, `softDelete`, `insertSource`, `findSourceByIdempotencyKey`, `listSources`, `insertRevision`, `findRevision`, `listRevisions`, `insertJob`, `selectClaimable`, `findLatestByCard`, `markRunning`, `markSucceeded`, `markFailed`, `cancel`, `cancelPendingForCard`, `requeueStaleRunning`, `findPreferenceByUser`, and `upsertDefaultTemplate`.

- [ ] **Step 1: Write a mapper XML contract test**

```java
class VocabularyMapperContractTest {
    @Test void mapperXmlContainsOwnershipAndAtomicJobGuards() throws Exception {
        String cards = Files.readString(Path.of("src/main/resources/mapper/VocabularyCardMapper.xml"));
        String jobs = Files.readString(Path.of("src/main/resources/mapper/VocabularyGenerationJobMapper.xml"));
        assertAll(
                () -> assertTrue(cards.contains("user_id = #{userId}")),
                () -> assertTrue(cards.contains("deleted_at IS NULL")),
                () -> assertTrue(cards.contains("active_revision_uid = #{baseRevisionUid}")),
                () -> assertTrue(jobs.contains("status = 'pending'")),
                () -> assertTrue(jobs.contains("available_at &lt;= CURRENT_TIMESTAMP")),
                () -> assertTrue(jobs.contains("WHERE job_uid = #{jobUid} AND status = 'pending'"))
        );
    }
}
```

- [ ] **Step 2: Run the mapper contract test and confirm the XML files are absent**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyMapperContractTest test`

Expected: FAIL because the mapper XML files do not exist.

- [ ] **Step 3: Add persistence POJOs, mapper signatures, and guarded SQL**

Each entity is a bean with fields matching Task 1 columns and `Long id`/`LocalDateTime` timestamps. The key mapper signatures are:

```java
@Mapper
public interface VocabularyCardMapper {
    VocabularyCard findByIdentityIncludingDeleted(@Param("userId") Long userId,
            @Param("language") String language, @Param("normalizedTerm") String normalizedTerm);
    int insert(VocabularyCard card);
    VocabularyCard findByUidIncludingDeleted(@Param("cardUid") String cardUid);
    int restoreAndTouch(@Param("userId") Long userId, @Param("cardUid") String cardUid,
            @Param("displayTerm") String displayTerm, @Param("status") String status,
            @Param("capturedAt") LocalDateTime capturedAt);
    int touch(@Param("userId") Long userId, @Param("cardUid") String cardUid,
            @Param("capturedAt") LocalDateTime capturedAt);
    VocabularyCard findOwnedByUid(@Param("userId") Long userId, @Param("cardUid") String cardUid);
    List<VocabularyCard> listByUser(@Param("userId") Long userId, @Param("keyword") String keyword,
            @Param("status") String status, @Param("sourceType") String sourceType,
            @Param("offset") int offset, @Param("limit") int limit);
    long countByUser(@Param("userId") Long userId, @Param("keyword") String keyword,
            @Param("status") String status, @Param("sourceType") String sourceType);
    int updateActiveRevision(@Param("userId") Long userId, @Param("cardUid") String cardUid,
            @Param("baseRevisionUid") String baseRevisionUid, @Param("revisionUid") String revisionUid,
            @Param("status") String status, @Param("templateKey") String templateKey,
            @Param("templateVersion") int templateVersion);
    int markConflictCandidate(@Param("cardUid") String cardUid);
    int markGenerationFailed(@Param("cardUid") String cardUid, @Param("terminal") boolean terminal);
    int softDelete(@Param("userId") Long userId, @Param("cardUid") String cardUid);
}
```

```java
@Mapper
public interface VocabularyGenerationJobMapper {
    int insertJob(VocabularyGenerationJob job);
    List<VocabularyGenerationJob> selectClaimable(@Param("limit") int limit);
    VocabularyGenerationJob findLatestByCard(@Param("cardUid") String cardUid);
    int markRunning(@Param("jobUid") String jobUid);
    int markSucceeded(@Param("jobUid") String jobUid, @Param("revisionUid") String revisionUid);
    int markFailed(@Param("jobUid") String jobUid, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("availableAt") LocalDateTime availableAt,
            @Param("terminal") boolean terminal);
    int cancel(@Param("jobUid") String jobUid);
    int cancelPendingForCard(@Param("cardUid") String cardUid);
    int requeueStaleRunning(@Param("staleBefore") LocalDateTime staleBefore);
}

@Mapper
public interface UserVocabularyPreferenceMapper {
    UserVocabularyPreference findPreferenceByUser(@Param("userId") Long userId);
    int upsertDefaultTemplate(@Param("userId") Long userId, @Param("templateKey") String templateKey);
}
```

Use a conditional update for optimistic activation:

```xml
<update id="updateActiveRevision">
  UPDATE vocabulary_card
  SET active_revision_uid = #{revisionUid}, status = #{status},
      template_key = #{templateKey}, template_version = #{templateVersion}, deleted_at = NULL
  WHERE user_id = #{userId} AND card_uid = #{cardUid} AND deleted_at IS NULL
  <choose>
    <when test="baseRevisionUid == null">AND active_revision_uid IS NULL</when>
    <otherwise>AND active_revision_uid = #{baseRevisionUid}</otherwise>
  </choose>
</update>
```

Use an atomic job claim:

```xml
<update id="markRunning">
  UPDATE vocabulary_generation_job
  SET status = 'running', attempt_count = attempt_count + 1,
      started_at = CURRENT_TIMESTAMP, error_code = NULL, error_message = NULL
  WHERE job_uid = #{jobUid} AND status = 'pending' AND available_at &lt;= CURRENT_TIMESTAMP
</update>
```

Every card read/update/delete SQL statement must include `user_id = #{userId}`; `listByUser` uses an `EXISTS` source subquery when `sourceType` is present.

Add `VocabularyTestFixtures` as a test-only factory so later tests do not depend on constructors that drift with persistence fields. It provides these exact static methods: `ready(String cardUid, Long userId, String normalizedTerm, String activeRevisionUid)`, `ready(String cardUid, String activeRevisionUid)`, `generating(String term)`, `generating(String cardUid, String activeRevisionUid)`, `manualSource(String contextText)`, `pendingJob(String jobUid, String cardUid, String baseRevisionUid, int attemptCount)`, `userRevision(String revisionUid)`, `basicGeneratedCard()`, and `dictionaryLookup(String word, String partOfSpeech, String definition)`. Each method creates the real mutable entity, sets every field used by the receiving test, and returns it; no mocked domain subclasses are introduced.

- [ ] **Step 4: Run mapper and schema contract tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyMapperContractTest,VocabularyDepositionSchemaTest test`

Expected: PASS and Spring/MyBatis resource parsing reports no duplicate statement IDs.

- [ ] **Step 5: Commit persistence adapters**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/entity/vocabulary backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary backend/src/main/resources/mapper/Vocabulary* backend/src/test/java/com/personalenglishai/backend/db/VocabularyMapperContractTest.java backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java
git commit -m "feat(vocabulary): 新增单词卡持久化映射"
```

### Task 4: Build transactional capture, dedupe, idempotency, and restoration

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java`

**Interfaces:**
- Consumes: `VocabularyTermNormalizer`, `VocabularyTemplateRegistry`, and Task 3 card/source/job/preference mappers.
- Produces: `VocabularyCaptureResponse capture(Long userId, VocabularyCaptureRequest request)`, `VocabularyCaptureResponse captureDictionaryFavorite(Long userId, String word, String language, String contextText)`, and `Item captureOne(Long userId, VocabularyCaptureRequest request, int index)` running in `REQUIRES_NEW` so one bad bulk item cannot roll back successful siblings.

- [ ] **Step 1: Write failing service tests for create, duplicate merge, idempotency, and review routing**

```java
@ExtendWith(MockitoExtension.class)
class VocabularyCaptureItemServiceTest {
    @Mock VocabularyCardMapper cards;
    @Mock VocabularySourceMapper sources;
    @Mock VocabularyGenerationJobMapper jobs;
    @Mock UserVocabularyPreferenceMapper preferences;
    VocabularyCaptureItemService service;

    @BeforeEach void setUp() {
        service = new VocabularyCaptureItemService(cards, sources, jobs, preferences,
                new VocabularyTermNormalizer(), new VocabularyTemplateRegistry(new ObjectMapper()), new ObjectMapper());
    }

    @Test void createsCardSourceAndPendingJobBeforeReturning() {
        var request = VocabularyCaptureRequest.manual("req-1", List.of("In·nova·tive"), "en", "basic");
        VocabularyCaptureResponse.Item result = service.captureOne(7L, request, 0);
        assertEquals("created", result.action());
        assertEquals("generating", result.status());
        InOrder order = inOrder(cards, sources, jobs);
        order.verify(cards).insert(any());
        order.verify(sources).insertSource(any());
        order.verify(jobs).insertJob(any());
        verify(preferences).upsertDefaultTemplate(7L, "basic");
    }

    @Test void mergesARepeatedTermWithoutCreatingAnotherJob() {
        VocabularyCard existing = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_user");
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(existing);
        var result = service.captureOne(7L, VocabularyCaptureRequest.manual("req-2", List.of("innovative"), "en", "exam"), 0);
        assertEquals("source_merged", result.action());
        verify(sources).insertSource(argThat(s -> s.getIdempotencyKey().equals("req-2:0")));
        verify(cards).touch(eq(7L), eq("card_1"), any());
        verifyNoInteractions(jobs);
    }

    @Test void marksInvalidInputForReviewWithoutSchedulingAi() {
        var result = service.captureOne(7L, VocabularyCaptureRequest.manual("req-3", List.of("你好"), "en", "basic"), 0);
        assertEquals("needs_review", result.action());
        verifyNoInteractions(jobs);
    }

    @Test void retryingTheSameRequestDoesNotInsertAnotherSourceOrJob() {
        when(sources.findSourceByIdempotencyKey(7L, "req-4:0"))
                .thenReturn(VocabularyTestFixtures.manualSource(null));
        var result = service.captureOne(7L, VocabularyCaptureRequest.manual("req-4", List.of("innovative"), "en", "basic"), 0);
        assertEquals("source_merged", result.action());
        verify(sources, never()).insertSource(any());
        verifyNoInteractions(jobs);
    }

    @Test void recaptureRestoresTheSameSoftDeletedCard() {
        VocabularyCard deleted = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_1");
        deleted.setDeletedAt(LocalDateTime.now());
        when(cards.findByIdentityIncludingDeleted(7L, "en", "innovative")).thenReturn(deleted);
        var result = service.captureOne(7L, VocabularyCaptureRequest.manual("req-5", List.of("innovative"), "en", "basic"), 0);
        assertEquals("card_1", result.cardUid());
        verify(cards).restoreAndTouch(eq(7L), eq("card_1"), eq("innovative"), anyString(), any());
    }
}
```

Add this orchestrator test in `VocabularyCaptureServiceTest`:

```java
@Test void bulkCaptureKeepsSuccessfulItemsWhenOneItemFails() {
    when(itemService.captureOne(eq(7L), any(), eq(0)))
            .thenReturn(new VocabularyCaptureResponse.Item("good", "card_1", "created", "generating"));
    when(itemService.captureOne(eq(7L), any(), eq(1))).thenThrow(new RuntimeException("db unavailable"));
    var result = service.capture(7L, VocabularyCaptureRequest.manual("req-bulk", List.of("good", "bad"), "en", "basic"));
    assertEquals(List.of("created", "rejected"), result.items().stream().map(VocabularyCaptureResponse.Item::action).toList());
}
```

- [ ] **Step 2: Run the service test and confirm capture types are missing**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest test`

Expected: compilation FAIL for missing request, response, and service.

- [ ] **Step 3: Implement the capture transaction and stable source idempotency**

Use records that serialize directly through Jackson:

```java
public record VocabularyCaptureRequest(String clientRequestId, List<String> terms, String language,
        String templateKey, Source source) {
    public record Source(String type, String sourceRef, String sourceTitle, String sourceUrl,
            String contextText, Map<String, Object> metadata) {}
    public static VocabularyCaptureRequest manual(String requestId, List<String> terms, String language, String templateKey) {
        return new VocabularyCaptureRequest(requestId, terms, language, templateKey,
                new Source("manual", null, "手动输入", null, null, Map.of()));
    }
}

public record VocabularyCaptureResponse(List<Item> items) {
    public record Item(String term, String cardUid, String action, String status) {}
}
```

The service entry point must be transactional and validate source types:

```java
@Service
public class VocabularyCaptureService {
    public VocabularyCaptureResponse capture(Long userId, VocabularyCaptureRequest request) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null || request.clientRequestId() == null || request.clientRequestId().isBlank())
            throw new IllegalArgumentException("clientRequestId is required");
        if (request.terms() == null || request.terms().isEmpty() || request.terms().size() > 100)
            throw new IllegalArgumentException("terms must contain 1 to 100 items");
        String sourceType = request.source() == null ? "manual" : request.source().type();
        if (!Set.of("manual", "dictionary").contains(sourceType))
            throw new IllegalArgumentException("unsupported source type");
        List<VocabularyCaptureResponse.Item> items = new ArrayList<>();
        for (int index = 0; index < request.terms().size(); index++) {
            try {
                items.add(itemService.captureOne(userId, request, index));
            } catch (RuntimeException ex) {
                items.add(new VocabularyCaptureResponse.Item(request.terms().get(index), null, "rejected", "failed"));
            }
        }
        return new VocabularyCaptureResponse(items);
    }
}
```

`VocabularyCaptureItemService.captureOne` is annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`. It resolves and persists the selected default template, uses `request.clientRequestId() + ":" + index`, checks the source mapper first for an already-used idempotency key, catches `DuplicateKeyException` around card insert and re-selects the unique identity for concurrent captures, restores `deleted_at`, inserts a source on every non-idempotent capture, and inserts a pending job only when a new/restored valid card has no active revision. UIDs use `card_`, `src_`, and `job_` plus UUID without dashes. Invalid items persist as `needs_review` cards and sources but do not enqueue jobs. Log rejected-item diagnostics with request ID and index, never raw context text.

- [ ] **Step 4: Run capture service tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest test`

Expected: PASS; Mockito verifies one transaction path and no duplicate generation for an existing active card.

- [ ] **Step 5: Commit capture behavior**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java
git commit -m "feat(vocabulary): 实现单词捕获与精确去重"
```

### Task 5: Expose capture, templates, list, and detail APIs

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardSummaryResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardDetailResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyTemplateCatalogResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`

**Interfaces:**
- Consumes: `VocabularyCaptureService.capture`, `VocabularyTemplateRegistry.list`, and Task 3 read mappers.
- Produces: `POST /api/vocabulary/captures`, `GET /api/vocabulary/templates`, `GET /api/vocabulary/cards`, and `GET /api/vocabulary/cards/{cardUid}`; `VocabularyTemplateCatalogResponse` contains `items` and the user's `defaultTemplateKey`.

- [ ] **Step 1: Write failing MockMvc tests for authentication and durable reads**

```java
@WebMvcTest(VocabularyController.class)
@AutoConfigureMockMvc(addFilters = false)
class VocabularyControllerTest {
    @Resource MockMvc mockMvc;
    @MockBean VocabularyCaptureService captureService;
    @MockBean VocabularyCardService cardService;
    @MockBean VocabularyTemplateRegistry templateRegistry;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtInterceptor jwtInterceptor;

    @Test void capturesManualTerms() throws Exception {
        when(captureService.capture(eq(7L), any())).thenReturn(new VocabularyCaptureResponse(List.of(
                new VocabularyCaptureResponse.Item("innovative", "card_1", "created", "generating"))));
        mockMvc.perform(post("/api/vocabulary/captures").requestAttr("userId", 7L)
                .contentType("application/json")
                .content("""{"clientRequestId":"req-1","terms":["innovative"],"language":"en","templateKey":"basic","source":{"type":"manual"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cardUid").value("card_1"));
    }

    @Test void rejectsAnonymousCardList() throws Exception {
        mockMvc.perform(get("/api/vocabulary/cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401000"));
    }
}
```

Also add `VocabularyCardServiceTest` that verifies `getDetail(7L, "card_1")` rejects a missing/foreign card and maps source and active revision JSON for an owned card.

- [ ] **Step 2: Run controller and card service tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyControllerTest,VocabularyCardServiceTest test`

Expected: compilation FAIL because the controller, service, and response types do not exist.

- [ ] **Step 3: Implement authenticated endpoints and response assembly**

```java
@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {
    @PostMapping("/captures")
    public ResponseEntity<ApiResponse<VocabularyCaptureResponse>> capture(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody VocabularyCaptureRequest request) {
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(captureService.capture(userId, request)));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<VocabularyTemplateCatalogResponse>> templates(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(cardService.templateCatalog(userId)));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<AdminPageResponse<VocabularyCardSummaryResponse>>> cards(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(cardService.list(userId, keyword, status, sourceType, page, size)));
    }

    @GetMapping("/cards/{cardUid}")
    public ResponseEntity<ApiResponse<VocabularyCardDetailResponse>> detail(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid) {
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(cardService.getDetail(userId, cardUid)));
    }
}
```

`VocabularyCardSummaryResponse` includes `cardUid`, `displayTerm`, `normalizedTerm`, `templateKey`, `status`, `activeRevisionUid`, `sourceTypes`, `lastCapturedAt`, and `updatedAt`. `VocabularyCardDetailResponse` adds parsed `JsonNode content`, ordered source items, `generationStatus`, `generationError`, and timestamps. Clamp page size to `1..50` and return not-found for cards not owned by the current user.

`VocabularyCardService.templateCatalog(userId)` reads `UserVocabularyPreferenceMapper.findPreferenceByUser`; missing or unsupported preferences return `basic`, while the `items` list always comes from `VocabularyTemplateRegistry.list()`.

- [ ] **Step 4: Run API tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyControllerTest,VocabularyCardServiceTest test`

Expected: PASS with authenticated capture/detail and unauthenticated rejection covered.

- [ ] **Step 5: Commit the read API**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java
git commit -m "feat(vocabulary): 提供单词捕获与查询接口"
```

### Task 6: Make dictionary favorites deposit vocabulary cards

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/dictionary/DictionaryWordStateService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/DictionaryControllerTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/dictionary/DictionaryWordStateServiceTest.java`

**Interfaces:**
- Consumes: `VocabularyCaptureService.captureDictionaryFavorite(Long userId, String word, String language, String contextText)`.
- Produces: existing `setFavorite(Long userId, String word, String language, boolean favorite)` retains its response contract while successful `favorite=true` creates/merges a `dictionary` source; `favorite=false` never deletes a card.

- [ ] **Step 1: Write a failing favorite integration test**

```java
@ExtendWith(MockitoExtension.class)
class DictionaryWordStateServiceTest {
    @Mock UserDictionaryWordStateMapper mapper;
    @Mock DictionaryContentMapper contentMapper;
    @Mock VocabularyCaptureService captureService;

    @Test void favoriteDepositsCardButUnfavoriteDoesNotDeleteIt() {
        var service = new DictionaryWordStateService(mapper, contentMapper, captureService);
        UserDictionaryWordState state = new UserDictionaryWordState();
        state.setWord("innovative"); state.setLanguage("en-gb"); state.setFavorite(true); state.setLookupCount(1);
        when(mapper.selectByUserAndWord(7L, "innovative")).thenReturn(state);
        service.setFavorite(7L, "innovative", "en-gb", true);
        service.setFavorite(7L, "innovative", "en-gb", false);
        verify(captureService, times(1)).captureDictionaryFavorite(7L, "innovative", "en-gb", null);
    }
}
```

- [ ] **Step 2: Run dictionary regression tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=DictionaryWordStateServiceTest,DictionaryControllerTest test`

Expected: compilation FAIL because `DictionaryWordStateService` does not yet accept or call `VocabularyCaptureService`.

- [ ] **Step 3: Invoke vocabulary capture after favorite state persists**

```java
public DictionaryWordStateResponse setFavorite(Long userId, String word, String language, boolean favorite) {
    String normalizedWord = normalizeWord(word);
    if (userId == null || normalizedWord.isBlank()) throw new IllegalArgumentException("invalid user or word");
    String displayWord = word.trim();
    String effectiveLanguage = firstNonBlank(language, "en-gb");
    mapper.setFavorite(userId, displayWord, normalizedWord, effectiveLanguage, favorite);
    if (favorite) {
        vocabularyCaptureService.captureDictionaryFavorite(userId, displayWord, effectiveLanguage, null);
    }
    return toResponse(mapper.selectByUserAndWord(userId, normalizedWord), displayWord, effectiveLanguage);
}
```

`captureDictionaryFavorite` maps `en-gb`/`en-us` to card language `en`, uses a fresh `clientRequestId` prefixed `dictionary-favorite-`, source type `dictionary`, source title `词典收藏`, and stable source ref `dictionary:<normalized-term>`. It leaves enrichment to the background generator, which avoids lookup-state side effects.

- [ ] **Step 4: Run favorite and capture regression tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=DictionaryWordStateServiceTest,DictionaryControllerTest,VocabularyCaptureServiceTest test`

Expected: PASS; unfavorite calls no delete or generation API.

- [ ] **Step 5: Commit dictionary integration**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/dictionary/DictionaryWordStateService.java backend/src/test/java/com/personalenglishai/backend/controller/DictionaryControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/dictionary/DictionaryWordStateServiceTest.java
git commit -m "feat(vocabulary): 接入词典收藏沉淀"
```

### Task 7: Generate and validate structured card content

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyDictionaryEnricher.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationCache.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationException.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java`

**Interfaces:**
- Consumes: `DictionaryLookupService.lookup(String word, String language)`, `OpenAiClient.callWithTraceId(String systemPrompt, String userPrompt, String traceId, Double temperature, Integer maxTokens)`, existing `StringRedisTemplate`, source context, and `VocabularyTemplateRegistry.validate`.
- Produces: `GeneratedVocabularyCard generate(VocabularyCard card, List<VocabularyCardSource> sources, VocabularyTemplateRegistry.TemplateDefinition template, String traceId)` where `GeneratedVocabularyCard` contains validated `JsonNode content`, model name, and change summary; `VocabularyGenerationException` exposes `code()` and `retryable()`.

- [ ] **Step 1: Write failing generator tests for dictionary priority and invalid AI JSON**

```java
@ExtendWith(MockitoExtension.class)
class VocabularyCardGeneratorTest {
    @Mock DictionaryLookupService dictionary;
    @Mock OpenAiClient ai;
    @Mock VocabularyGenerationCache cache;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void missCacheByDefault() {
        lenient().when(cache.get(anyString())).thenReturn(Optional.empty());
    }

    @Test void preservesDictionaryDefinitionAndCapturedSourceContext() throws Exception {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "introducing new ideas"));
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_1"), eq(0.2), eq(1200)))
                .thenReturn("""{"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":["wrong"],"examples":[],"notes":""}""");
        var registry = new VocabularyTemplateRegistry(objectMapper);
        var generator = new VocabularyCardGenerator(new VocabularyDictionaryEnricher(dictionary), ai,
                cache, registry, objectMapper);
        var result = generator.generate(VocabularyTestFixtures.generating("innovative"),
                List.of(VocabularyTestFixtures.manualSource("The company is innovative.")),
                registry.require("basic"), "job_1");
        assertEquals("introducing new ideas", result.content().path("definitions").get(0).asText());
    }

    @Test void rejectsMalformedStructuredOutput() {
        when(ai.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("not-json");
        var registry = new VocabularyTemplateRegistry(objectMapper);
        var generator = new VocabularyCardGenerator(new VocabularyDictionaryEnricher(dictionary), ai, cache, registry, objectMapper);
        assertThrows(VocabularyGenerationException.class, () -> generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(), registry.require("basic"), "job_2"));
    }

    @Test void usesValidatedCacheBeforeCallingAi() throws Exception {
        var registry = new VocabularyTemplateRegistry(objectMapper);
        JsonNode cached = objectMapper.readTree("""{"term":"innovative","phonetic":"","partOfSpeech":"adjective","definitions":[],"examples":[],"notes":""}""");
        when(cache.get(anyString())).thenReturn(Optional.of(cached));
        var generator = new VocabularyCardGenerator(new VocabularyDictionaryEnricher(dictionary), ai, cache, registry, objectMapper);
        assertEquals(cached, generator.generate(VocabularyTestFixtures.generating("innovative"), List.of(), registry.require("basic"), "job_3").content());
        verifyNoInteractions(ai);
    }
}
```

- [ ] **Step 2: Run generator tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyCardGeneratorTest test`

Expected: compilation FAIL because generator/enricher classes are missing.

- [ ] **Step 3: Implement local-first enrichment, strict JSON parsing, and deterministic merging**

```java
public record GeneratedVocabularyCard(JsonNode content, String model, String changeSummary) {}

public GeneratedVocabularyCard generate(VocabularyCard card, List<VocabularyCardSource> sources,
        TemplateDefinition template, String traceId) {
    DictionaryLookupResponse dictionaryData = enricher.lookupWithoutUserState(card.getDisplayTerm(), "en-gb");
    String capturedContext = sources.stream().map(VocabularyCardSource::getContextText)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
    String cacheKey = cache.key(card.getNormalizedTerm(), template.key(), template.version(), dictionaryData, capturedContext);
    Optional<JsonNode> cached = cache.get(cacheKey);
    if (cached.isPresent()) {
        templateRegistry.validate(template.key(), cached.get());
        return new GeneratedVocabularyCard(cached.get(), "cache", "复用已验证生成内容");
    }
    String raw = openAiClient.callWithTraceId(systemPrompt(template),
            userPrompt(card, dictionaryData, capturedContext), traceId, 0.2, 1200);
    JsonNode aiContent = parseObject(stripCodeFences(raw));
    ObjectNode merged = mergeDictionaryTruth(aiContent, dictionaryData);
    merged.put("term", card.getDisplayTerm());
    if ("reading".equals(template.key())) merged.put("sourceContext", capturedContext);
    templateRegistry.validate(template.key(), merged);
    cache.put(cacheKey, merged, Duration.ofDays(7));
    return new GeneratedVocabularyCard(merged, openAiClient.getModel(), "AI 按" + template.name() + "生成");
}
```

The prompt embeds the exact required field list from the template and instructs the model to return one JSON object without Markdown. `mergeDictionaryTruth` overwrites AI phonetic, part of speech, definitions, and dictionary examples whenever those fields exist. `sourceContext` is only copied from captured sources; an empty captured context stays empty. Directly call `DictionaryLookupService.lookup` and never call `DictionaryWordStateService.attachLookupState`. `VocabularyGenerationCache.key` SHA-256 hashes normalized term, template key/version, dictionary content, and context; Redis stores only validated output for seven days, so different reading contexts never share content.

- [ ] **Step 4: Run generator and template tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyCardGeneratorTest,VocabularyTemplateRegistryTest test`

Expected: PASS; malformed output is rejected and dictionary truth wins.

- [ ] **Step 5: Commit generation logic**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyDictionaryEnricher.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationCache.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationException.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java
git commit -m "feat(vocabulary): 生成结构化单词卡内容"
```

### Task 8: Process restart-safe generation jobs and preserve stale results

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationScheduler.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java`

**Interfaces:**
- Consumes: Task 3 job/revision/card/source mappers and `VocabularyCardGenerator.generate`.
- Produces: `int processPendingJobs(int batchSize)` and scheduler properties `vocabulary.generation.scheduler.enabled`, `fixed-delay-ms`, and `batch-size`.

- [ ] **Step 1: Write failing worker tests for success, stale user revision, and retry**

```java
@ExtendWith(MockitoExtension.class)
class VocabularyGenerationWorkerTest {
    @Mock VocabularyGenerationJobMapper jobs;
    @Mock VocabularyCardMapper cards;
    @Mock VocabularySourceMapper sources;
    @Mock VocabularyRevisionMapper revisions;
    @Mock VocabularyCardGenerator generator;

    @Test void activatesSuccessfulRevisionWhenBaseStillMatches() {
        var job = VocabularyTestFixtures.pendingJob("job_1", "card_1", null, 0);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_1")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(VocabularyTestFixtures.generating("card_1", null));
        when(generator.generate(any(), anyList(), any(), eq("job_1"))).thenReturn(VocabularyTestFixtures.basicGeneratedCard());
        when(cards.updateActiveRevision(anyLong(), eq("card_1"), isNull(), anyString(), eq("ready"), eq("basic"), eq(1))).thenReturn(1);
        assertEquals(1, worker().processPendingJobs(10));
        verify(jobs).markSucceeded(eq("job_1"), anyString());
    }

    @Test void storesButDoesNotActivateResultOverAUserRevision() {
        var job = VocabularyTestFixtures.pendingJob("job_2", "card_1", "rev_ai_old", 0);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_2")).thenReturn(1);
        when(cards.findByUidIncludingDeleted("card_1")).thenReturn(VocabularyTestFixtures.ready("card_1", "rev_user"));
        when(revisions.findRevision("rev_user")).thenReturn(VocabularyTestFixtures.userRevision("rev_user"));
        when(generator.generate(any(), anyList(), any(), eq("job_2"))).thenReturn(VocabularyTestFixtures.basicGeneratedCard());
        assertEquals(1, worker().processPendingJobs(10));
        verify(cards).updateActiveRevision(anyLong(), eq("card_1"), eq("rev_ai_old"), anyString(), eq("ready"), eq("basic"), eq(1));
        verify(jobs).markSucceeded(eq("job_2"), anyString());
    }

    @Test void requeuesTransientFailureWithBackoff() {
        var job = VocabularyTestFixtures.pendingJob("job_3", "card_1", null, 0);
        when(jobs.selectClaimable(10)).thenReturn(List.of(job));
        when(jobs.markRunning("job_3")).thenReturn(1);
        when(generator.generate(any(), anyList(), any(), eq("job_3")))
                .thenThrow(new VocabularyGenerationException("AI_TIMEOUT", "timeout", true));
        worker().processPendingJobs(10);
        verify(jobs).markFailed(eq("job_3"), eq("AI_TIMEOUT"), eq("timeout"), any(), eq(false));
    }
}
```

- [ ] **Step 2: Run worker tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyGenerationWorkerTest test`

Expected: compilation FAIL because worker/scheduler classes are missing.

- [ ] **Step 3: Implement guarded claims, revision commits, and bounded retry**

```java
@Service
public class VocabularyGenerationWorker {
    public int processPendingJobs(int batchSize) {
        int processed = 0;
        for (VocabularyGenerationJob candidate : jobs.selectClaimable(Math.max(1, Math.min(batchSize, 20)))) {
            if (jobs.markRunning(candidate.getJobUid()) != 1) continue;
            processed++;
            processClaimed(candidate);
        }
        return processed;
    }

    void processClaimed(VocabularyGenerationJob job) {
        try {
            VocabularyCard card = cards.findByUidIncludingDeleted(job.getCardUid());
            if (card == null || card.getDeletedAt() != null) { jobs.cancel(job.getJobUid()); return; }
            GeneratedVocabularyCard generated = generator.generate(card, sources.listSources(card.getCardUid()),
                    templates.require(job.getTemplateKey()), job.getJobUid());
            VocabularyCardRevision revision = revisionFactory.ai(job, generated);
            revisions.insertRevision(revision);
            int activated = cards.updateActiveRevision(card.getUserId(), card.getCardUid(),
                    job.getBaseRevisionUid(), revision.getRevisionUid(), "ready",
                    job.getTemplateKey(), job.getTemplateVersion());
            if (activated == 0) cards.markConflictCandidate(card.getCardUid());
            jobs.markSucceeded(job.getJobUid(), revision.getRevisionUid());
        } catch (VocabularyGenerationException ex) {
            int nextAttempt = job.getAttemptCount() + 1;
            boolean terminal = !ex.retryable() || nextAttempt >= 3;
            jobs.markFailed(job.getJobUid(), ex.code(), ex.getMessage(),
                    LocalDateTime.now().plusSeconds(30L * nextAttempt), terminal);
            cards.markGenerationFailed(job.getCardUid(), terminal);
        }
    }
}
```

`markFailed(..., terminal=false)` sets the job back to `pending`; terminal failures set `failed`. The scheduler defaults to enabled, batch size `5`, fixed delay `5000` ms, and only delegates to the worker. `BackendApplication` already has `@EnableScheduling`, so do not modify it.

```java
@Component
public class VocabularyGenerationScheduler {
    @Scheduled(fixedDelayString = "${vocabulary.generation.scheduler.fixed-delay-ms:5000}")
    public void runBatch() {
        if (enabled) worker.processPendingJobs(batchSize);
    }
}
```

- [ ] **Step 4: Run worker and capture tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyGenerationWorkerTest,VocabularyCaptureServiceTest,VocabularyCardGeneratorTest test`

Expected: PASS; success, stale result preservation, and bounded retry are covered.

- [ ] **Step 5: Commit the restart-safe worker**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationScheduler.java backend/src/main/resources/application.yml backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java
git commit -m "feat(vocabulary): 新增单词卡异步生成任务"
```

### Task 9: Add edits, deletion, regeneration, retry, history, and conflict resolution

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/UpdateVocabularyCardRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/ResolveVocabularyConflictRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyConflictResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyRevisionConflictException.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`

**Interfaces:**
- Consumes: guarded `updateActiveRevision`, append-only revisions, and job insertion/cancellation.
- Produces: `PUT`/`DELETE` card, `POST regenerate`, `POST retry`, `GET revisions`, and `POST conflicts/{revisionUid}/resolve`; error code `VOCABULARY_REVISION_CONFLICT("409030", "单词卡版本已变化")`.

- [ ] **Step 1: Write failing optimistic-lock and conflict-resolution tests**

```java
@Test void staleUserEditReturnsCurrentRevisionAsConflict() throws Exception {
    when(cardService.update(eq(7L), eq("card_1"), any()))
            .thenThrow(new VocabularyRevisionConflictException(new VocabularyConflictResponse(
                    "rev_current", "rev_edit", new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode())));
    mockMvc.perform(put("/api/vocabulary/cards/card_1").requestAttr("userId", 7L)
            .contentType("application/json")
            .content("""{"baseRevisionUid":"rev_old","content":{"term":"innovative","definitions":[]}}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("409030"))
            .andExpect(jsonPath("$.data.currentRevisionUid").value("rev_current"));
}

@Test void fieldMergeCreatesSystemMergeRevision() {
    when(cards.updateActiveRevision(7L, "card_1", "rev_user", "rev_merge", "ready", "basic", 1)).thenReturn(1);
    cardService.resolveConflict(7L, "card_1", "rev_ai", new ResolveVocabularyConflictRequest(
            "merge_fields", "rev_user", Map.of("definitions", List.of("merged"))));
    verify(revisions).insertRevision(argThat(r -> "system_merge".equals(r.getAuthorType())
            && "rev_user".equals(r.getBaseRevisionUid())));
}
```

- [ ] **Step 2: Run card service and controller tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyControllerTest,VocabularyCardServiceTest test`

Expected: FAIL because update/conflict endpoints and exception mapping are absent.

- [ ] **Step 3: Implement append-only commands and explicit choices**

```java
public record UpdateVocabularyCardRequest(String baseRevisionUid, JsonNode content, String changeSummary) {}
public record ResolveVocabularyConflictRequest(String choice, String baseRevisionUid, Map<String, JsonNode> fields) {}
public record VocabularyConflictResponse(String currentRevisionUid, String candidateRevisionUid, JsonNode currentContent, JsonNode candidateContent) {}
```

`VocabularyRevisionConflictException` extends `RuntimeException` and exposes a `VocabularyConflictResponse conflict()` accessor. Add a dedicated `@ExceptionHandler` that returns `new ApiResponse<>("409030", "单词卡版本已变化", ex.conflict())` with HTTP 409, so the frontend receives both the stable error code and current/candidate summaries.

`update` validates the current template content, inserts an `author_type=user` revision, then calls guarded `updateActiveRevision`. If the update count is `0`, keep the inserted revision as history and throw `VocabularyRevisionConflictException` carrying the latest current revision summary. `delete` soft-deletes the owned card and cancels pending/running jobs. `regenerate` enqueues a job based on the active revision and requested template. `retry` only accepts a latest failed job. `listRevisions` returns newest first.

Conflict choices are exact strings:

```java
switch (request.choice()) {
    case "keep_current" -> revisions.insertRevision(revisionFactory.systemMerge(
            card, current, current.getContentJson(), "保留当前内容"));
    case "use_ai" -> revisions.insertRevision(revisionFactory.systemMerge(
            card, current, candidate.getContentJson(), "使用 AI 新版本"));
    case "merge_fields" -> revisions.insertRevision(revisionFactory.systemMerge(
            card, current, mergeAllowedFields(current, candidate, request.fields()), "逐字段合并"));
    default -> throw new IllegalArgumentException("unsupported conflict choice");
}
```

`mergeAllowedFields` permits only fields declared by the current template and never accepts `term` from the client. The resulting `system_merge` revision becomes active with a guarded update against `baseRevisionUid`.

- [ ] **Step 4: Run all vocabulary backend tests**

Run: `cd backend; .\mvnw.cmd -q -Dtest='*Vocabulary*Test,DictionaryWordStateServiceTest,DictionaryControllerTest' test`

Expected: PASS with 409 mapping, edit history, deletion, retry, regeneration, and all three conflict choices covered.

- [ ] **Step 5: Commit the complete command API**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyRevisionConflictException.java backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java
git commit -m "feat(vocabulary): 完善单词卡版本与冲突操作"
```

### Task 10: Add the typed frontend API and bulk-input parser

**Files:**
- Create: `web/src/api/vocabulary.ts`
- Create: `web/src/features/vocabulary/captureTerms.ts`
- Create: `web/tests/vocabularyCaptureTerms.test.ts`
- Create: `web/tests/vocabularyApiContract.test.ts`

**Interfaces:**
- Consumes: all Task 5 and Task 9 endpoint/field names.
- Produces: TypeScript types `VocabularyTemplate`, `VocabularyTemplateCatalog`, `VocabularyCardSummary`, `VocabularyCardDetail`, `VocabularyRevision`, `VocabularyCaptureRequest`, and functions `listVocabularyTemplates`, `captureVocabulary`, `listVocabularyCards`, `getVocabularyCard`, `updateVocabularyCard`, `deleteVocabularyCard`, `regenerateVocabularyCard`, `retryVocabularyCard`, `listVocabularyRevisions`, `resolveVocabularyConflict`; pure `parseCaptureTerms` and `createClientRequestId`.

- [ ] **Step 1: Write failing parser and API contract tests**

```typescript
import test from 'node:test'
import assert from 'node:assert/strict'
import { parseCaptureTerms } from '../src/features/vocabulary/captureTerms'

test('parses newline comma and semicolon input without removing duplicate intent', () => {
  assert.deepEqual(parseCaptureTerms(' innovative, sustainable\nstate-of-the-art；innovative '), [
    'innovative', 'sustainable', 'state-of-the-art', 'innovative',
  ])
})

test('caps one capture request at one hundred nonblank items', () => {
  assert.equal(parseCaptureTerms(Array.from({ length: 120 }, (_, i) => `word${i}`).join('\n')).length, 100)
})
```

In `vocabularyApiContract.test.ts`, read `src/api/vocabulary.ts` and assert each exact endpoint string and `baseRevisionUid` are present, matching the repository's existing source-contract test style.

- [ ] **Step 2: Run the frontend tests and confirm imports fail**

Run: `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts`

Expected: FAIL because both frontend modules are missing.

- [ ] **Step 3: Implement parser, UUID fallback, types, and API functions**

```typescript
export function parseCaptureTerms(raw: string): string[] {
  return raw.split(/[\n,;，；]+/u).map((term) => term.trim()).filter(Boolean).slice(0, 100)
}

export function createClientRequestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `capture-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
```

```typescript
export type VocabularyCardStatus = 'captured' | 'generating' | 'ready' | 'needs_review' | 'failed'
export type VocabularyTemplateKey = 'basic' | 'exam' | 'reading'
export interface VocabularyTemplateCatalog { items: VocabularyTemplate[]; defaultTemplateKey: VocabularyTemplateKey }
export interface VocabularyCaptureRequest {
  clientRequestId: string
  terms: string[]
  language: 'en'
  templateKey: VocabularyTemplateKey
  source: { type: 'manual'; sourceTitle: string; contextText?: string; metadata: Record<string, unknown> }
}

export const listVocabularyTemplates = () =>
  unwrap<VocabularyTemplateCatalog>(http.get('/vocabulary/templates'))
export const captureVocabulary = (payload: VocabularyCaptureRequest) =>
  unwrap<VocabularyCaptureResponse>(http.post('/vocabulary/captures', payload))
export const listVocabularyCards = (params: VocabularyCardFilters) =>
  unwrap<VocabularyCardPage>(http.get('/vocabulary/cards', { params }))
export const getVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyCardDetail>(http.get(`/vocabulary/cards/${encodeURIComponent(cardUid)}`))
export const updateVocabularyCard = (cardUid: string, payload: UpdateVocabularyCardRequest) =>
  unwrap<VocabularyCardDetail>(http.put(`/vocabulary/cards/${encodeURIComponent(cardUid)}`, payload))
export const deleteVocabularyCard = (cardUid: string) =>
  unwrap<void>(http.delete(`/vocabulary/cards/${encodeURIComponent(cardUid)}`))
export const regenerateVocabularyCard = (cardUid: string, templateKey: VocabularyTemplateKey) =>
  unwrap<VocabularyCardDetail>(http.post(`/vocabulary/cards/${encodeURIComponent(cardUid)}/regenerate`, { templateKey }))
export const retryVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyCardDetail>(http.post(`/vocabulary/cards/${encodeURIComponent(cardUid)}/retry`))
export const listVocabularyRevisions = (cardUid: string) =>
  unwrap<VocabularyRevision[]>(http.get(`/vocabulary/cards/${encodeURIComponent(cardUid)}/revisions`))
export const resolveVocabularyConflict = (
  cardUid: string,
  revisionUid: string,
  payload: ResolveVocabularyConflictRequest,
) => unwrap<VocabularyCardDetail>(http.post(
  `/vocabulary/cards/${encodeURIComponent(cardUid)}/conflicts/${encodeURIComponent(revisionUid)}/resolve`,
  payload,
))
```

The shared `unwrap` helper returns `response.data.data` and throws when data is absent. Its Axios error branch detects code `409030` and throws `VocabularyConflictError`, whose constructor stores the typed conflict payload on a public `conflict` property.

- [ ] **Step 4: Run frontend unit tests and typecheck build**

Run: `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts; npm run build`

Expected: both tests PASS and `vue-tsc && vite build` exits 0.

- [ ] **Step 5: Commit frontend data contracts**

```powershell
git add web/src/api/vocabulary.ts web/src/features/vocabulary/captureTerms.ts web/tests/vocabularyCaptureTerms.test.ts web/tests/vocabularyApiContract.test.ts
git commit -m "feat(ui): 新增单词沉淀前端数据层"
```

### Task 11: Add TanStack Query orchestration and the capture/list workspace

**Files:**
- Create: `web/src/composables/useVocabularyCards.ts`
- Create: `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- Create: `web/src/components/vocabulary/VocabularyCardList.vue`
- Create: `web/tests/vocabularyDepositionWorkspace.test.ts`
- Modify: `web/src/views/VocabularyView.vue`

**Interfaces:**
- Consumes: Task 10 API functions and parser.
- Produces: `useVocabularyCards(filters, selectedCardUid)` with list/detail/template queries, status-aware polling, capture mutation, invalidation, and selected detail; component events `captured`, `select`, and `update:filters`.

- [ ] **Step 1: Write a failing workspace source contract test**

```typescript
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const view = fs.readFileSync(new URL('../src/views/VocabularyView.vue', import.meta.url), 'utf8')
const capture = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCapturePanel.vue', import.meta.url), 'utf8')
const list = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCardList.vue', import.meta.url), 'utf8')

test('vocabulary view composes durable capture and list components', () => {
  assert.match(view, /VocabularyCapturePanel/)
  assert.match(view, /VocabularyCardList/)
  assert.match(view, /useVocabularyCards/)
  assert.doesNotMatch(view, /const\s+savedWords\s*=\s*ref/)
})

test('capture panel exposes template choice and bulk submission states', () => {
  assert.match(capture, /basic/)
  assert.match(capture, /exam/)
  assert.match(capture, /reading/)
  assert.match(capture, /captureMutation/)
  assert.match(capture, /已存在，已追加来源/)
})

test('list renders every persisted status and filters', () => {
  for (const token of ['generating', 'ready', 'needs_review', 'failed', 'sourceType']) assert.match(list, new RegExp(token))
})
```

- [ ] **Step 2: Run the workspace test and confirm components are absent**

Run: `cd web; npx tsx --test tests/vocabularyDepositionWorkspace.test.ts`

Expected: FAIL while reading missing component files.

- [ ] **Step 3: Implement query orchestration and responsive capture/list components**

```typescript
export function useVocabularyCards(filters: Ref<VocabularyCardFilters>, selectedCardUid: Ref<string | null>) {
  const queryClient = useQueryClient()
  const templateQuery = useQuery({
    queryKey: ['vocabulary', 'templates'],
    queryFn: listVocabularyTemplates,
    staleTime: 300_000,
  })
  const listQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'cards', filters.value]),
    queryFn: () => listVocabularyCards(filters.value),
    refetchInterval: (query) => query.state.data?.items.some((item) => item.status === 'generating') ? 2000 : false,
  })
  const detailQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', selectedCardUid.value]),
    queryFn: () => getVocabularyCard(selectedCardUid.value!),
    enabled: computed(() => Boolean(selectedCardUid.value)),
    refetchInterval: (query) => query.state.data?.status === 'generating' ? 2000 : false,
  })
  const captureMutation = useMutation({
    mutationFn: captureVocabulary,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] }),
  })
  return { templateQuery, listQuery, detailQuery, captureMutation }
}
```

`VocabularyCapturePanel` uses a plain textarea, a compact three-option segmented template control initialized from `templateQuery.data.defaultTemplateKey`, a source-context input, an item-count preview, and a submit button disabled for blank/active submission. It keeps the same `clientRequestId` while retrying a failed request and replaces it only after success. Render per-item outcomes with `已收下`, `已存在，已追加来源`, `待确认`, or the rejection message.

`VocabularyCardList` is an unframed, scan-friendly list with search, status menu, source menu, pagination, stable row heights, empty/loading/error states, and status labels `正在生成`, `已就绪`, `待确认`, `生成失败`. In `VocabularyView.vue`, retain the existing dictionary lookup area but replace mock collection state with the capture/list workspace; route card selection through `cardUid`, not word text.

- [ ] **Step 4: Run workspace tests and build**

Run: `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts; npm run build`

Expected: all focused tests PASS and the production build exits 0 without Vue/TypeScript errors.

- [ ] **Step 5: Commit capture and list UI**

```powershell
git add web/src/composables/useVocabularyCards.ts web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/components/vocabulary/VocabularyCardList.vue web/src/views/VocabularyView.vue web/tests/vocabularyDepositionWorkspace.test.ts
git commit -m "feat(ui): 构建单词沉淀与卡片列表"
```

### Task 12: Complete card editing, history, retry, and conflict UI

**Files:**
- Create: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Modify: `web/src/composables/useVocabularyCards.ts`
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/src/router/index.ts`
- Create: `web/tests/vocabularyCardInspector.test.ts`
- Create: `web/tests/vocabularyDepositionFlow.spec.ts`

**Interfaces:**
- Consumes: Task 9 command endpoints and Task 11 selected card/query state.
- Produces: complete detail workflow with edit/save, source/history tabs, regenerate/template choice, retry, delete confirmation, and conflict choices `keep_current`, `use_ai`, `merge_fields`.

- [ ] **Step 1: Write failing inspector and browser-flow tests**

```typescript
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const inspector = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url), 'utf8')
test('inspector exposes safe edit and all conflict choices', () => {
  for (const token of ['baseRevisionUid', 'keep_current', 'use_ai', 'merge_fields', 'retryVocabularyCard', 'listVocabularyRevisions']) {
    assert.match(inspector, new RegExp(token))
  }
  assert.match(inspector, /保留当前内容/)
  assert.match(inspector, /使用 AI 新版本/)
  assert.match(inspector, /逐字段合并/)
})
```

```typescript
test('manual word survives refresh and can be edited', async ({ page }) => {
  const cards: Array<Record<string, unknown>> = []
  await page.addInitScript(() => localStorage.setItem('auth_token', 'vocabulary-e2e-token'))
  await page.route('**/api/vocabulary/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/captures')) {
      cards.splice(0, cards.length, { cardUid: 'card_1', displayTerm: 'innovative', status: 'ready', activeRevisionUid: 'rev_1' })
      return route.fulfill({ json: { code: '0', data: { items: [{ term: 'innovative', cardUid: 'card_1', action: 'created', status: 'generating' }] } } })
    }
    if (url.pathname.endsWith('/cards') && route.request().method() === 'GET') {
      return route.fulfill({ json: { code: '0', data: { items: cards, total: cards.length, page: 1, size: 20 } } })
    }
    return route.fulfill({ json: { code: '0', data: { cardUid: 'card_1', displayTerm: 'innovative', status: 'ready', activeRevisionUid: 'rev_1', templateKey: 'basic', content: { term: 'innovative', definitions: [], examples: [], notes: 'Use this in product writing.' }, sources: [] } } })
  })
  await page.goto('/app/vocabulary?tab=collection')
  await page.getByRole('textbox', { name: '新增单词' }).fill('innovative')
  await page.getByRole('button', { name: '收进单词库' }).click()
  await expect(page.getByText('已收下')).toBeVisible()
  await page.reload()
  await expect(page.getByText('innovative', { exact: true })).toBeVisible()
  await page.getByText('innovative', { exact: true }).click()
  await page.getByRole('button', { name: '编辑卡片' }).click()
  await page.getByLabel('个人笔记').fill('Use this in product writing.')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByText('Use this in product writing.')).toBeVisible()
  await page.screenshot({ path: 'test-results/vocabulary-deposition-desktop.png', fullPage: true })
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  expect(overflow).toBe(false)
})
```

- [ ] **Step 2: Run the inspector source test**

Run: `cd web; npx tsx --test tests/vocabularyCardInspector.test.ts`

Expected: FAIL while reading the missing inspector component.

- [ ] **Step 3: Implement the inspector, conflict dialog, route state, and mutations**

`VocabularyCardInspector` renders structured fields from the active template, never raw JSON. `notes` is always editable; array fields use repeatable text inputs; `term` is read-only. The save payload is exact:

```typescript
await updateMutation.mutateAsync({
  cardUid: props.card.cardUid,
  payload: {
    baseRevisionUid: props.card.activeRevisionUid,
    content: structuredClone(editContent.value),
    changeSummary: '用户编辑卡片',
  },
})
```

When `VocabularyConflictError` is caught, show current and candidate fields in two columns on desktop and stacked sections on mobile. Default the radio choice to `keep_current`; for `merge_fields`, render one selector per template field. Successful commands invalidate list, detail, and revisions queries. Delete requires an explicit confirmation dialog and navigates back to `/app/vocabulary?tab=collection`.

Update the route to:

```typescript
{
  path: 'vocabulary/cards/:cardUid',
  name: 'vocabulary-card',
  component: () => import('@/views/VocabularyView.vue'),
}
```

Add mutations in `useVocabularyCards` for update/delete/regenerate/retry/resolve. Poll only while the list or selected card contains `generating`; stop on `ready`, `needs_review`, or `failed`.

- [ ] **Step 4: Run frontend tests, production build, and Playwright flow**

Run: `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyCardInspector.test.ts; npm run build; npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps`

Expected: Node tests PASS, production build exits 0, and Playwright passes with vocabulary API routes mocked inside the spec. Run the same flow in two `test.describe` viewport blocks (`1440x900` and `390x844`), save both screenshots under `web/test-results`, and assert no horizontal overflow via `document.documentElement.scrollWidth <= window.innerWidth`.

- [ ] **Step 5: Commit the closed card-management loop**

```powershell
git add web/src/components/vocabulary/VocabularyCardInspector.vue web/src/composables/useVocabularyCards.ts web/src/views/VocabularyView.vue web/src/router/index.ts web/tests/vocabularyCardInspector.test.ts web/tests/vocabularyDepositionFlow.spec.ts
git commit -m "feat(ui): 完成单词卡编辑与冲突闭环"
```

### Task 13: Update documentation and run end-to-end verification

**Files:**
- Modify: `docs/architecture/dictionary-oxford.md`
- Create: `docs/architecture/vocabulary-deposition.md`
- Modify: `README.md`
- Create: `backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java`

**Interfaces:**
- Consumes: final database schema, API behavior, scheduler properties, and frontend workflow from Tasks 1-12.
- Produces: operator/developer documentation that separates dictionary content, favorite state, vocabulary assets, and generation jobs; exact migration and verification commands.

- [ ] **Step 1: Add a failing documentation contract test**

Create `backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java`:

```java
class VocabularyDepositionDocsTest {
    @Test void docsDescribeMigrationWorkerAndOwnershipBoundary() throws Exception {
        String docs = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));
        assertAll(
                () -> assertTrue(docs.contains("migrate_create_vocabulary_deposition_tables.sql")),
                () -> assertTrue(docs.contains("vocabulary.generation.scheduler.enabled")),
                () -> assertTrue(docs.contains("user_dictionary_word_state")),
                () -> assertTrue(docs.contains("POST /api/vocabulary/captures")),
                () -> assertTrue(docs.contains("baseRevisionUid"))
        );
    }
}
```

- [ ] **Step 2: Run the documentation test**

Run: `cd backend; .\mvnw.cmd -q -Dtest=VocabularyDepositionDocsTest test`

Expected: FAIL because `docs/architecture/vocabulary-deposition.md` does not exist.

- [ ] **Step 3: Document deployment, state flow, and boundaries**

`vocabulary-deposition.md` must include:

```markdown
# 单词沉淀架构

## 资产边界
- `dictionary_*`：共享词典内容，只读补全来源。
- `user_dictionary_word_state`：查词次数和收藏开关，不是单词卡。
- `vocabulary_card*`：用户拥有的持久卡片、来源和版本。
- `vocabulary_generation_job`：可恢复的异步生成队列。

## 部署
1. 执行 `backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql`。
2. 配置 `vocabulary.generation.scheduler.enabled=true`。
3. 启动后端并确认 pending 任务转为 running，再转为 succeeded 或 failed。

## 写入契约
- 捕获：`POST /api/vocabulary/captures`，同一重试复用 `clientRequestId`。
- 编辑：携带 `baseRevisionUid`，版本冲突返回 `409030`。
- 收藏：收藏会沉淀，取消收藏不会删除卡片。
```

Update `dictionary-oxford.md` to link this boundary document. Add the migration and scheduler setting to the root README's local database/startup section without documenting deferred assistant/PDF/web adapters as available features.

- [ ] **Step 4: Run the full verification matrix**

Run backend tests:

```powershell
cd backend
.\mvnw.cmd -q test
```

Expected: Maven exits 0 with no test failures.

Run frontend tests and build:

```powershell
cd web
npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyCardInspector.test.ts
npm run build
```

Expected: all vocabulary Node tests PASS and Vite emits a successful production build.

Apply the migration to a disposable/local MySQL database, start backend and frontend, then verify these exact API/UI cases:

1. Submit `innovative` manually and receive `created` plus persisted `generating`.
2. Refresh and observe the same `cardUid` transition to `ready` or a retryable `failed` state.
3. Submit `In·nova·tive` again with a new request ID and receive `source_merged` with no second card.
4. Favorite the same term in dictionary search and observe a new `dictionary` source; unfavorite leaves the card present.
5. Edit notes with the current `baseRevisionUid`; submit a stale base and confirm HTTP `409030`.
6. Resolve a generated candidate with each of `keep_current`, `use_ai`, and `merge_fields` in isolated cards.
7. Soft-delete a card, capture it again, and confirm restoration of the same `cardUid`.

- [ ] **Step 5: Commit documentation and verification contracts**

```powershell
git add docs/architecture/dictionary-oxford.md docs/architecture/vocabulary-deposition.md README.md backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java
git commit -m "docs(vocabulary): 补充单词沉淀部署与验证说明"
```

---

## Merge Readiness Gate

Before proposing merge into `main`, verify `git diff --check`, inspect every commit to ensure unrelated staged files were not included, and attach the desktop/mobile Playwright screenshots. The branch is mergeable only when the migration has been exercised on local MySQL, the full backend suite and frontend build pass, exact duplicate capture preserves one card identity, and stale AI/user writes have an automated regression test.
