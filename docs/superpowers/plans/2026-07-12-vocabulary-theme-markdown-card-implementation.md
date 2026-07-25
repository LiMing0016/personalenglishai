# Vocabulary Theme and Markdown Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build versioned vocabulary themes, let users select a theme when capturing words, and persist each card as queryable core JSON plus theme-generated Markdown without breaking legacy cards.

**Architecture:** Add immutable theme revisions and additive card/job columns while retaining legacy template fields for compatibility. Resolve a theme before enqueueing generation, build core JSON from dictionary truth, generate Markdown through a theme-specific Prompt strategy, and expose the new format through additive API fields. The Vue frontend uses TanStack Query for theme assets, a dedicated theme library route for management, a compact theme shelf in capture, and a core/Markdown inspector for card content.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, MySQL 8, Jackson, JUnit 5, Mockito, Vue 3, TypeScript, TanStack Vue Query, Vue Router, Axios, Vite, Playwright.

## Global Constraints

- Keep `manual` and `dictionary` as the only active capture sources in this release.
- Do not add backend or frontend runtime dependencies.
- Keep `template_key`, `template_version`, and `content_json` readable and writable during the compatibility period.
- Use fixed system theme UIDs: `theme_system_basic`, `theme_system_exam`, and `theme_system_reading`.
- A custom theme inherits the basic content scaffold in v1; only its name and purpose are user-editable.
- Theme purpose is untrusted user data and must not override system instructions, card identity, or output format.
- Core content uses schema version `1`; theme/card content format uses version `1`.
- Markdown must be UTF-8 text, at most 20,000 characters, with raw HTML rejected in v1.
- Existing cards are not bulk-regenerated; they upgrade only when explicitly regenerated.
- All database changes are additive and rollback must never delete theme or card-version data.
- Use Conventional Commits with concise Chinese descriptions.

---

## File Map

### Backend additions

- `backend/src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql`: additive theme tables, recent-use table, and nullable compatibility columns.
- `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyTheme.java`: mutable theme identity row.
- `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyThemeRevision.java`: immutable theme revision row.
- `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyThemeMapper.java`: theme/revision/recent-use persistence contract.
- `backend/src/main/resources/mapper/VocabularyThemeMapper.xml`: MySQL statements and result maps.
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/*Theme*.java`: theme catalog and command contracts.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyThemeService.java`: ownership, lifecycle, default, copy, and version rules.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/ResolvedVocabularyTheme.java`: immutable theme snapshot consumed by capture and generation.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodec.java`: core JSON validation, dictionary conversion, and legacy projection.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyMarkdownPromptBuilder.java`: safe Prompt selection and assembly.

### Backend modifications

- `VocabularyController`, capture/regenerate DTOs, capture services, card service, generator, worker, finalizer, entities, mappers, and API response DTOs under the existing vocabulary packages.
- Existing SQL mapper XML files for card, revision, job, and preference persistence.

### Frontend additions

- `web/src/composables/useVocabularyThemes.ts`: theme queries and mutations.
- `web/src/pages/app/VocabularyThemesPage.vue`: theme library route container.
- `web/src/components/vocabulary/VocabularyThemeShelf.vue`: default/recent theme selector in capture.
- `web/src/components/vocabulary/VocabularyThemeLibrary.vue`: system/user theme sections.
- `web/src/components/vocabulary/VocabularyThemeDialog.vue`: create/edit form.
- `web/src/components/vocabulary/VocabularyCoreSummary.vue`: stable core JSON presentation.
- `web/src/components/vocabulary/VocabularyMarkdownEditor.vue`: source/preview toggle and Markdown editing without a new renderer dependency.

### Frontend modifications

- `web/src/api/vocabulary.ts`, `web/src/composables/useVocabularyCards.ts`, `web/src/views/VocabularyView.vue`, `web/src/components/vocabulary/VocabularyCapturePanel.vue`, `web/src/components/vocabulary/VocabularyCardInspector.vue`, and `web/src/router/index.ts`.

---

### Task 1: Additive Theme and Card Content Schema

**Files:**
- Create: `backend/src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyTheme.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyThemeRevision.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyThemeMapper.java`
- Create: `backend/src/main/resources/mapper/VocabularyThemeMapper.xml`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCard.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyGenerationJob.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/UserVocabularyPreference.java`
- Modify: `backend/src/main/resources/mapper/VocabularyCardMapper.xml`
- Modify: `backend/src/main/resources/mapper/VocabularyRevisionMapper.xml`
- Modify: `backend/src/main/resources/mapper/VocabularyGenerationJobMapper.xml`
- Modify: `backend/src/main/resources/mapper/UserVocabularyPreferenceMapper.xml`
- Test: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyDepositionSchemaTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyMapperContractTest.java`

**Interfaces:**
- Produces: `VocabularyThemeMapper`, new `themeUid/themeVersion/coreJson/contentMarkdown/contentFormatVersion` entity properties, and additive columns consumed by every later task.

- [ ] **Step 1: Write failing schema and mapper contract tests**

Add assertions that the migration contains the three tables and additive columns:

```java
assertAll(
    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_theme")),
    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_theme_revision")),
    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS user_vocabulary_theme_recent")),
    () -> assertTrue(sql.contains("ADD COLUMN theme_uid VARCHAR(64) NULL")),
    () -> assertTrue(sql.contains("ADD COLUMN core_json JSON NULL")),
    () -> assertTrue(sql.contains("ADD COLUMN content_markdown MEDIUMTEXT NULL"))
);
```

Add MyBatis contract assertions for `findVisibleThemes`, `findCurrentRevision`, `insertTheme`, `insertRevision`, `recordRecentUse`, and `setDefaultTheme`.

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=VocabularyDepositionSchemaTest,VocabularyMapperContractTest" test
```

Expected: FAIL because the migration, mapper, and entity properties do not exist.

- [ ] **Step 3: Add the migration and persistence model**

The migration must:

```sql
CREATE TABLE IF NOT EXISTS vocabulary_theme (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    theme_uid VARCHAR(64) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    user_id BIGINT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    current_version INT NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_theme_uid (theme_uid),
    KEY idx_vocabulary_theme_user_status (user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_theme_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_uid VARCHAR(64) NOT NULL,
    theme_uid VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    name_snapshot VARCHAR(80) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    prompt_strategy_key VARCHAR(64) NOT NULL,
    content_format_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_theme_revision_uid (revision_uid),
    UNIQUE KEY uk_vocabulary_theme_version (theme_uid, version),
    CONSTRAINT fk_vocabulary_theme_revision_theme FOREIGN KEY (theme_uid) REFERENCES vocabulary_theme(theme_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_vocabulary_theme_recent (
    user_id BIGINT NOT NULL,
    theme_uid VARCHAR(64) NOT NULL,
    last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, theme_uid),
    KEY idx_vocabulary_theme_recent (user_id, last_used_at),
    CONSTRAINT fk_vocabulary_theme_recent_theme FOREIGN KEY (theme_uid) REFERENCES vocabulary_theme(theme_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Use `information_schema.columns` guarded dynamic SQL for additive columns so the migration can run once against the current development database. Add `theme_uid/theme_version` to cards and jobs; add `theme_uid/theme_version/core_json/content_markdown/content_format_version` to revisions; add `default_theme_uid` to preferences. Keep every existing legacy column unchanged.

Define mapper signatures exactly:

```java
List<VocabularyTheme> findVisibleThemes(@Param("userId") Long userId);
VocabularyTheme findOwnedByUid(@Param("userId") Long userId, @Param("themeUid") String themeUid);
VocabularyThemeRevision findCurrentRevision(@Param("themeUid") String themeUid);
VocabularyThemeRevision findRevision(@Param("themeUid") String themeUid, @Param("version") int version);
int insertTheme(VocabularyTheme theme);
int insertRevision(VocabularyThemeRevision revision);
int advanceVersion(@Param("userId") Long userId, @Param("themeUid") String themeUid,
                   @Param("expectedVersion") int expectedVersion, @Param("nextVersion") int nextVersion,
                   @Param("name") String name);
int setStatus(@Param("userId") Long userId, @Param("themeUid") String themeUid,
              @Param("status") String status);
int softDelete(@Param("userId") Long userId, @Param("themeUid") String themeUid);
int recordRecentUse(@Param("userId") Long userId, @Param("themeUid") String themeUid);
List<String> findRecentThemeUids(@Param("userId") Long userId, @Param("limit") int limit);
```

- [ ] **Step 4: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/db backend/src/main/resources/mapper backend/src/main/java/com/personalenglishai/backend/entity/vocabulary backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary backend/src/test/java/com/personalenglishai/backend/db
git commit -m "feat(vocabulary): 新增单词主题与Markdown存储结构"
```

---

### Task 2: Theme Catalog, Ownership, Versioning, and CRUD API

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/ResolvedVocabularyTheme.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyThemeService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyThemeResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyThemeCatalogResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/CreateVocabularyThemeRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/UpdateVocabularyThemeRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyThemeServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`

**Interfaces:**
- Consumes: `VocabularyThemeMapper` from Task 1.
- Produces: `ResolvedVocabularyTheme resolve(Long userId, String themeUid, String legacyTemplateKey)`, theme CRUD endpoints, and the catalog consumed by capture and frontend tasks.

- [ ] **Step 1: Write failing service tests**

Cover these exact rules:

```java
assertEquals("theme_system_basic", service.resolve(7L, null, "basic").themeUid());
assertEquals("basic-markdown-v1", service.resolve(7L, "theme_system_basic", null).promptStrategyKey());
assertThrows(BizException.class, () -> service.update(7L, "theme_system_basic", update));
assertEquals(2, service.update(7L, "theme_user_1", update).version());
assertEquals("custom-markdown-v1", service.create(7L, create).promptStrategyKey());
```

Also verify duplicate names, cross-user reads, deleted/disabled selection, immutable revision insertion, system-theme copy, default fallback, and recent theme ordering.

- [ ] **Step 2: Run tests and verify failure**

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=VocabularyThemeServiceTest,VocabularyControllerTest" test
```

Expected: FAIL because the theme service and endpoints do not exist.

- [ ] **Step 3: Implement theme contracts and service**

Use these records:

```java
public record ResolvedVocabularyTheme(
        String themeUid, int version, String name, String purpose,
        String promptStrategyKey, int contentFormatVersion, String legacyTemplateKey) {}

public record VocabularyThemeResponse(
        String themeUid, String ownerType, String name, String purpose,
        int version, String status, boolean system, boolean defaultTheme,
        boolean recent, String promptStrategyKey) {}

public record VocabularyThemeCatalogResponse(
        List<VocabularyThemeResponse> systemThemes,
        List<VocabularyThemeResponse> userThemes,
        String defaultThemeUid,
        List<String> recentThemeUids) {}
```

Validate requests with `@NotBlank`, name length `1..80`, and purpose length `1..1000`. Create UIDs with the existing UUID convention (`theme_` and `theme_rev_`). `update` must lock/guard the current version, insert version `n + 1`, and then advance the identity row. `delete` is always soft delete. `resolve` must reject inaccessible user themes and map legacy keys to fixed system UIDs.

- [ ] **Step 4: Add REST endpoints**

Add exactly:

```text
GET    /api/vocabulary/themes
POST   /api/vocabulary/themes
PUT    /api/vocabulary/themes/{themeUid}
POST   /api/vocabulary/themes/{themeUid}/copy
POST   /api/vocabulary/themes/{themeUid}/default
POST   /api/vocabulary/themes/{themeUid}/disable
DELETE /api/vocabulary/themes/{themeUid}
```

Controllers only authenticate, validate, call `VocabularyThemeService`, and wrap `ApiResponse`.

- [ ] **Step 5: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyThemeServiceTest.java
git commit -m "feat(vocabulary): 提供单词主题管理与版本接口"
```

---

### Task 3: Resolve Themes During Capture and Regeneration

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/RegenerateVocabularyCardRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/UserVocabularyPreferenceMapper.java`
- Modify: `backend/src/main/resources/mapper/UserVocabularyPreferenceMapper.xml`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`

**Interfaces:**
- Consumes: `VocabularyThemeService.resolve(...)`.
- Produces: capture and generation jobs with frozen `themeUid/themeVersion`; legacy `templateKey` remains accepted.

- [ ] **Step 1: Write failing compatibility and freezing tests**

Assert:

```java
verify(themeService).resolve(7L, "theme_user_1", null);
assertEquals("theme_user_1", insertedJob.getThemeUid());
assertEquals(3, insertedJob.getThemeVersion());
assertEquals("basic", insertedJob.getTemplateKey()); // custom theme compatibility projection
verify(themeMapper).recordRecentUse(7L, "theme_user_1");
```

Also verify old `{ "templateKey": "exam" }` requests resolve to `theme_system_exam`, omitted theme uses the user's default theme, and regeneration freezes the latest theme revision only when `useLatestThemeVersion=true`.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest,VocabularyCardServiceTest" test
```

- [ ] **Step 3: Add additive request fields and resolution**

Use additive request records:

```java
public record VocabularyCaptureRequest(
        @NotBlank String clientRequestId,
        @NotEmpty List<@NotBlank String> terms,
        @NotBlank String language,
        String themeUid,
        @Pattern(regexp = "basic|exam|reading") String templateKey,
        @Valid @NotNull Source source) {}

public record RegenerateVocabularyCardRequest(
        String themeUid,
        Boolean useLatestThemeVersion,
        @Pattern(regexp = "basic|exam|reading") String templateKey) {}
```

Resolve once at the capture batch boundary, pass `ResolvedVocabularyTheme` into item capture, persist both new theme fields and legacy projection, and record recent use only after at least one item is created/restored/source-merged. Keep the same `clientRequestId` behavior.

- [ ] **Step 4: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/UserVocabularyPreferenceMapper.java backend/src/main/resources/mapper/UserVocabularyPreferenceMapper.xml backend/src/test/java/com/personalenglishai/backend/service/vocabulary
git commit -m "feat(vocabulary): 在沉淀与重生成中固化主题版本"
```

---

### Task 4: Core JSON Contract and Legacy Projection

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodec.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodecTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java`

**Interfaces:**
- Produces: `ObjectNode fromDictionary(String term, DictionaryLookupResponse dictionary)`, `ObjectNode fromLegacy(String term, JsonNode legacy)`, `void validate(JsonNode core)`, and summary helpers consumed by generation and card responses.

- [ ] **Step 1: Write failing codec tests**

Test multi-region phonetics, multiple parts of speech, bilingual definition splitting, empty arrays, term override, and legacy projection:

```java
ObjectNode core = codec.fromLegacy("record", legacyContent);
assertEquals(1, core.path("schemaVersion").asInt());
assertEquals("record", core.path("term").asText());
assertTrue(core.path("phonetics").isArray());
assertEquals("noun", core.path("senses").get(0).path("partOfSpeech").asText());
assertThrows(IllegalArgumentException.class, () -> codec.validate(invalidCore));
```

- [ ] **Step 2: Run test and verify failure**

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=VocabularyCoreContentCodecTest" test
```

- [ ] **Step 3: Implement the codec**

The emitted shape must always be:

```json
{
  "schemaVersion": 1,
  "term": "...",
  "phonetics": [{"region":"uk|us|other","text":"...","audioUrl":null}],
  "senses": [{"partOfSpeech":"...","meanings":[{"definitionEn":"...","definitionZh":"..."}]}]
}
```

`validate` must reject unknown top-level fields, non-text values, more than 10 phonetics, more than 20 senses, more than 30 meanings per sense, scalar values over 2,000 characters, and a term that does not match the card's display term when the caller supplies an expected term. `fromLegacy` maps the old scalar `phonetic`, scalar `partOfSpeech`, and `definitions` array without deleting legacy data.

- [ ] **Step 4: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodec.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodecTest.java backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java
git commit -m "feat(vocabulary): 定义单词卡核心JSON契约"
```

---

### Task 5: Theme Prompt Strategies and Markdown Generation

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyMarkdownPromptBuilder.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreFallbackGenerator.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/ai/client/OpenAiClient.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationCache.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/ai/client/OpenAiClientResponsesPayloadTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyMarkdownPromptBuilderTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreFallbackGeneratorTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationCacheTest.java`

**Interfaces:**
- Consumes: frozen `ResolvedVocabularyTheme` and `VocabularyCoreContentCodec`.
- Produces: `GeneratedVocabularyCard(JsonNode core, String markdown, int contentFormatVersion, String model, String changeSummary, boolean partial)`.

- [ ] **Step 1: Write failing Prompt and generator tests**

Verify each system strategy has distinct instructions, custom purpose is delimited as untrusted data, structured fallback uses strict JSON Schema, card term comes from identity, raw HTML is rejected, and dictionary-backed output survives Markdown AI failure:

```java
assertTrue(builder.systemPrompt(theme("exam-markdown-v1")).contains("考试考义"));
assertTrue(builder.systemPrompt(theme("reading-markdown-v1")).contains("上下文解释"));
assertTrue(builder.userPrompt(customTheme, core, context).contains("<theme-purpose>"));

GeneratedVocabularyCard partial = generator.generate(card, sources, customTheme, "trace-1");
assertTrue(partial.partial());
assertEquals("record", partial.core().path("term").asText());
assertEquals("", partial.markdown());

verify(ai).callStructuredWithTraceId(
    anyString(), anyString(), eq("trace-core"), eq("vocabulary_core_v1"),
    any(JsonNode.class), eq(0.0), eq(1200));
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=OpenAiClientResponsesPayloadTest,VocabularyMarkdownPromptBuilderTest,VocabularyCoreFallbackGeneratorTest,VocabularyCardGeneratorTest,VocabularyGenerationCacheTest" test
```

- [ ] **Step 3: Implement Prompt assembly**

`VocabularyMarkdownPromptBuilder` must select only these strategy keys:

```java
private static final Map<String, String> STRATEGIES = Map.of(
    "basic-markdown-v1", "生成常用例句和学习提示。不要重复核心释义。",
    "exam-markdown-v1", "生成考试考义、固定搭配、易错点和真题风格例句。",
    "reading-markdown-v1", "生成语境义、句中作用、同义改写和上下文解释。",
    "custom-markdown-v1", "围绕用户提供的学习目标生成学习内容，使用基础章节骨架。"
);
```

System Prompt must require Markdown only, forbid raw HTML, forbid changing the word, and cap output length. User Prompt serializes core JSON and source context, then places purpose inside `<theme-purpose>...</theme-purpose>` with an explicit statement that it is data, not an instruction source.

- [ ] **Step 4: Add strict structured core fallback**

Add this `OpenAiClient` entry point without changing existing callers:

```java
public String callStructuredWithTraceId(
        String systemPrompt,
        String userPrompt,
        String traceId,
        String schemaName,
        JsonNode schema,
        Double temperature,
        Integer maxTokens)
```

For Responses API payloads, set `text.format.type=json_schema`, `text.format.name=schemaName`, `text.format.strict=true`, and `text.format.schema=schema`. For Chat Completions compatibility, set `response_format.type=json_schema` and the equivalent nested `json_schema`. Reuse existing retry, timeout, trace metrics, response extraction, and sanitization paths.

`VocabularyCoreFallbackGenerator` owns the `vocabulary_core_v1` schema. The schema sets `additionalProperties=false` at every object level, requires every field, uses nullable `audioUrl`, and applies the same array/length limits as `VocabularyCoreContentCodec`. It parses the structured response, overwrites `term` with the card identity, then calls `codec.validate(expectedTerm, core)`.

- [ ] **Step 5: Refactor generation around core plus Markdown**

Change the record exactly:

```java
public record GeneratedVocabularyCard(
        JsonNode core,
        String markdown,
        int contentFormatVersion,
        String model,
        String changeSummary,
        boolean partial) {}
```

Build core from dictionary first. If it contains neither a phonetic nor a sense, call `VocabularyCoreFallbackGenerator` once. If no valid core can be formed after that call, throw `VocabularyGenerationException("CORE_CONTENT_UNAVAILABLE", true, ...)`. Then call AI for Markdown. If the Markdown call or Markdown validation fails, return `partial=true` with validated core and an empty Markdown string. Cache keys include theme UID, theme version, core hash, and source context; cached values store both core and Markdown. A normal dictionary-backed card uses one AI call; a dictionary-missing card uses at most two.

- [ ] **Step 6: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/ai/client/OpenAiClient.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/test/java/com/personalenglishai/backend/ai/client/OpenAiClientResponsesPayloadTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary
git commit -m "feat(vocabulary): 按主题生成核心数据与Markdown内容"
```

---

### Task 6: Persist New Card Revisions and Preserve Conflict Semantics

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizer.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyRevisionWriteService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/UpdateVocabularyCardRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardDetailResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardSummaryResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyRevisionResponse.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizerTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyRevisionWriteServiceTransactionTest.java`

**Interfaces:**
- Consumes: the new `GeneratedVocabularyCard`.
- Produces: additive API fields `theme`, `themeVersion`, `core`, `markdown`, `contentFormatVersion`, while retaining legacy `content`.

- [ ] **Step 1: Write failing persistence and response tests**

Assert generated revisions freeze theme fields, partial output activates with `needs_review`, ready output activates with `ready`, legacy content projects into core, user Markdown edits preserve core identity, and stale edit conflict candidates retain both core and Markdown.

```java
assertEquals("theme_user_1", revision.getThemeUid());
assertEquals(3, revision.getThemeVersion());
assertEquals("record", objectMapper.readTree(revision.getCoreJson()).path("term").asText());
assertEquals("## Exam tips", revision.getContentMarkdown());
assertEquals("needs_review", updatedCardStatus);
```

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=VocabularyGenerationWorkerTest,VocabularyGenerationFinalizerTest,VocabularyCardServiceTest,VocabularyRevisionWriteServiceTransactionTest" test
```

- [ ] **Step 3: Persist generated and edited content**

Extend update requests additively:

```java
public record UpdateVocabularyCardRequest(
        @NotBlank String baseRevisionUid,
        JsonNode core,
        @Size(max = 20000) String markdown,
        JsonNode content,
        @Size(max = 255) String changeSummary) {}
```

New clients send `core` and `markdown`; old clients may still send `content`. The service validates core, overwrites `core.term` from the card identity, rejects raw HTML in Markdown, and writes both the new columns and a compatibility `content_json`. For compatibility JSON, include the core fields plus `markdown` without making it authoritative.

`VocabularyGenerationFinalizer.activate` must choose `ready` or `needs_review` from the generated revision outcome and still guard on the current active revision. Do not weaken lease ownership, candidate revision, or append-only conflict rules.

- [ ] **Step 4: Add response projection**

Add these response fields without removing old fields:

```java
VocabularyThemeSnapshot theme,
Integer themeVersion,
JsonNode core,
String markdown,
Integer contentFormatVersion
```

Summary `phonetic` comes from the first nonblank core phonetic; `coreDefinition` comes from the first bilingual meaning. Fall back to legacy projection when new columns are null.

- [ ] **Step 5: Run focused tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/test/java/com/personalenglishai/backend/service/vocabulary
git commit -m "feat(vocabulary): 保存主题化单词卡版本与兼容响应"
```

---

### Task 7: Frontend Theme and Card Content API Layer

**Files:**
- Modify: `web/src/api/vocabulary.ts`
- Create: `web/src/composables/useVocabularyThemes.ts`
- Modify: `web/src/composables/useVocabularyCards.ts`
- Test: `web/tests/vocabularyApiContract.test.ts`
- Create: `web/tests/vocabularyThemeApiContract.test.ts`

**Interfaces:**
- Consumes: theme/card APIs from Tasks 2, 3, and 6.
- Produces: typed theme catalog/mutations and new card content types used by UI tasks.

- [ ] **Step 1: Write failing source-contract tests**

Assert API paths, additive compatibility fields, and query invalidation:

```ts
assert.match(apiSource, /GET|http\.get\('\/vocabulary\/themes'/)
assert.match(apiSource, /themeUid\?: string/)
assert.match(apiSource, /core: VocabularyCoreContent \| null/)
assert.match(apiSource, /markdown: string \| null/)
assert.match(composableSource, /\['vocabulary', 'themes'\]/)
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
cd web
npx tsx --test tests/vocabularyApiContract.test.ts tests/vocabularyThemeApiContract.test.ts
```

- [ ] **Step 3: Add types and API functions**

Define:

```ts
export interface VocabularyCoreContent {
  schemaVersion: 1
  term: string
  phonetics: Array<{ region: 'uk' | 'us' | 'other'; text: string; audioUrl: string | null }>
  senses: Array<{
    partOfSpeech: string
    meanings: Array<{ definitionEn: string; definitionZh: string }>
  }>
}

export interface VocabularyTheme {
  themeUid: string
  ownerType: 'system' | 'user'
  name: string
  purpose: string
  version: number
  status: 'active' | 'disabled'
  system: boolean
  defaultTheme: boolean
  recent: boolean
  promptStrategyKey: string
}
```

Add list/create/update/copy/default/disable/delete functions. Capture and regenerate send `themeUid` while retaining optional `templateKey`. Card detail and revision types gain `theme`, `themeVersion`, `core`, `markdown`, and `contentFormatVersion` without deleting `content`.

- [ ] **Step 4: Add `useVocabularyThemes`**

Use query key `['vocabulary', 'themes']`. Every mutation invalidates that key; default/delete/disable also invalidate `['vocabulary', 'cards']`. Do not mirror server theme data into Pinia or local storage.

- [ ] **Step 5: Run focused tests and build**

```powershell
npx tsx --test tests/vocabularyApiContract.test.ts tests/vocabularyThemeApiContract.test.ts
npm.cmd run build
```

Expected: tests PASS and Vite build succeeds.

- [ ] **Step 6: Commit**

```powershell
git add web/src/api/vocabulary.ts web/src/composables/useVocabularyCards.ts web/src/composables/useVocabularyThemes.ts web/tests/vocabularyApiContract.test.ts web/tests/vocabularyThemeApiContract.test.ts
git commit -m "feat(ui): 接入单词主题与Markdown卡片数据契约"
```

---

### Task 8: Theme Library Page

**Files:**
- Create: `web/src/pages/app/VocabularyThemesPage.vue`
- Create: `web/src/components/vocabulary/VocabularyThemeLibrary.vue`
- Create: `web/src/components/vocabulary/VocabularyThemeDialog.vue`
- Modify: `web/src/router/index.ts`
- Test: `web/tests/vocabularyThemeLibrary.test.ts`

**Interfaces:**
- Consumes: `useVocabularyThemes`.
- Produces: `/app/vocabulary/themes` and theme management interactions.

- [ ] **Step 1: Write failing UI contract test**

Assert route, system/user sections, and all required actions:

```ts
assert.match(routerSource, /path:\s*['"]vocabulary\/themes['"]/)
for (const label of ['系统主题', '我的主题', '新建主题', '复制', '设为默认', '停用', '删除']) {
  assert.ok(librarySource.includes(label))
}
assert.match(dialogSource, /maxlength="80"/)
assert.match(dialogSource, /maxlength="1000"/)
```

- [ ] **Step 2: Run test and verify failure**

```powershell
cd web
npx tsx --test tests/vocabularyThemeLibrary.test.ts
```

- [ ] **Step 3: Implement the route and page**

The page uses an unframed constrained layout, not nested cards. System and user themes are separate full-width sections. Repeated theme items may be compact cards with radius no greater than 8px. Use existing icon library for add, copy, star/default, pause, edit, and delete actions, with tooltips for icon-only buttons.

The dialog contains only `name` and `purpose`, displays inline validation, disables submit while pending, and closes only after mutation success. System themes never show edit/disable/delete. Deleting a user theme requires confirmation that historical cards remain available.

- [ ] **Step 4: Run test and build**

```powershell
npx tsx --test tests/vocabularyThemeLibrary.test.ts
npm.cmd run build
```

- [ ] **Step 5: Commit**

```powershell
git add web/src/pages/app/VocabularyThemesPage.vue web/src/components/vocabulary/VocabularyThemeLibrary.vue web/src/components/vocabulary/VocabularyThemeDialog.vue web/src/router/index.ts web/tests/vocabularyThemeLibrary.test.ts
git commit -m "feat(ui): 新增单词主题库与管理流程"
```

---

### Task 9: Theme Shelf in Vocabulary Capture

**Files:**
- Create: `web/src/components/vocabulary/VocabularyThemeShelf.vue`
- Modify: `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- Modify: `web/src/views/VocabularyView.vue`
- Test: `web/tests/vocabularyThemeShelf.test.ts`
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`

**Interfaces:**
- Consumes: theme catalog and `themeUid` capture request.
- Produces: compact default/recent selection and explicit themed capture action.

- [ ] **Step 1: Write failing UI contract tests**

Assert the shelf has default/recent themes, create/manage links, selected state, and explicit button copy:

```ts
assert.match(shelfSource, /管理全部主题/)
assert.match(shelfSource, /新建主题/)
assert.match(captureSource, /按「.*」生成/)
assert.match(captureSource, /themeUid:\s*selectedThemeUid/)
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
cd web
npx tsx --test tests/vocabularyThemeShelf.test.ts tests/vocabularyDepositionWorkspace.test.ts
```

- [ ] **Step 3: Implement theme selection**

Show at most four items: default theme first, then recent active themes without duplication, then a fixed create action. The manage link routes to `/app/vocabulary/themes`. Keep selection inside the capture panel because it is draft UI state; initialize from `defaultThemeUid`, preserve a valid manual choice when the catalog refetches, and fall back when the selected theme becomes unavailable.

Submit payload:

```ts
{
  clientRequestId: requestId.value,
  terms: terms.value,
  language: 'en',
  themeUid: selectedThemeUid.value,
  source: { type: 'manual', sourceTitle: '手动录入', contextText, metadata: {} },
}
```

- [ ] **Step 4: Run tests and build**

```powershell
npx tsx --test tests/vocabularyThemeShelf.test.ts tests/vocabularyDepositionWorkspace.test.ts
npm.cmd run build
```

- [ ] **Step 5: Commit**

```powershell
git add web/src/components/vocabulary/VocabularyThemeShelf.vue web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/views/VocabularyView.vue web/tests/vocabularyThemeShelf.test.ts web/tests/vocabularyDepositionWorkspace.test.ts
git commit -m "feat(ui): 在单词沉淀中加入主题快捷选择"
```

---

### Task 10: Core JSON and Markdown Card Inspector

**Files:**
- Create: `web/src/components/vocabulary/VocabularyCoreSummary.vue`
- Create: `web/src/components/vocabulary/VocabularyMarkdownEditor.vue`
- Modify: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Modify: `web/src/composables/useVocabularyCards.ts`
- Test: `web/tests/vocabularyCoreSummary.test.ts`
- Modify: `web/tests/vocabularyCardInspector.test.ts`

**Interfaces:**
- Consumes: additive card detail/revision fields and themed regeneration API.
- Produces: stable core display, editable Markdown source, and latest-theme regeneration confirmation.

- [ ] **Step 1: Write failing inspector tests**

Assert multiple phonetics and parts of speech render, Markdown source is editable, save sends core plus Markdown, and regenerate selects a theme UID:

```ts
assert.match(coreSource, /core\.phonetics/)
assert.match(coreSource, /core\.senses/)
assert.match(markdownSource, /Markdown 内容/)
assert.doesNotMatch(markdownSource, /v-html/)
assert.match(inspectorSource, /themeUid/)
assert.match(inspectorSource, /使用主题最新版本/)
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
cd web
npx tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts
```

- [ ] **Step 3: Implement core display and Markdown editing**

`VocabularyCoreSummary` renders term, each phonetic region/text, and senses with bilingual meanings. It handles empty arrays with neutral copy and never reads legacy fields directly.

`VocabularyMarkdownEditor` uses a textarea labelled `Markdown 内容`, shows character count against the 20,000-character limit, and preserves line breaks exactly. Do not render model Markdown through `v-html` and do not add a Markdown dependency; rich rendering is explicitly deferred until content quality is validated.

The inspector adapter obtains core in this order: `card.core`, legacy projection helper, then a minimal `{schemaVersion:1, term, phonetics:[], senses:[]}`. Save sends `baseRevisionUid`, `core`, `markdown`, and change summary. Conflict dialog compares Markdown as a whole in v1; existing field merge remains only for legacy structured revisions.

- [ ] **Step 4: Implement themed regenerate behavior**

Replace the old template selector with theme selection. If the selected theme/version differs from the card's revision theme/version, show confirmation copy `将使用主题最新版本重新生成，当前版本会保留在历史中。` Send `{ themeUid, useLatestThemeVersion: true }`.

- [ ] **Step 5: Run tests and build**

```powershell
npx tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts
npm.cmd run build
```

- [ ] **Step 6: Commit**

```powershell
git add web/src/components/vocabulary/VocabularyCoreSummary.vue web/src/components/vocabulary/VocabularyMarkdownEditor.vue web/src/components/vocabulary/VocabularyCardInspector.vue web/src/composables/useVocabularyCards.ts web/tests/vocabularyCoreSummary.test.ts web/tests/vocabularyCardInspector.test.ts
git commit -m "feat(ui): 展示核心词典数据并编辑Markdown卡片"
```

---

### Task 11: Migration Verification, End-to-End Acceptance, and Documentation

**Files:**
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`
- Modify: `backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java`
- Modify: `docs/architecture/vocabulary-deposition.md`
- Create: `docs/ai/vocabulary-theme-prompts.md`
- Modify: `docs/index.md`
- Modify: `docs/.vitepress/config.ts`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: reproducible migration, API/UI acceptance evidence, and current operational documentation.

- [ ] **Step 1: Extend end-to-end tests before final verification**

Add Playwright scenarios that mock or drive the real API for:

```text
create custom theme -> set default -> select in shelf -> capture two words
edit theme -> old card remains on old version -> regenerate with latest version
Markdown generation failure -> core content remains visible with needs_review
open legacy basic card -> compatibility display -> regenerate into new format
desktop 1440x900 and mobile 390x844 layout checks
```

Assert no horizontal overflow and no visible technical error string such as `AI output failed structured validation`.

- [ ] **Step 2: Apply and verify migration on a disposable database**

Create a disposable MySQL schema, apply migrations in order, and query:

```sql
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('vocabulary_theme','vocabulary_theme_revision','user_vocabulary_theme_recent');

SELECT column_name FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'vocabulary_card_revision'
  AND column_name IN ('theme_uid','theme_version','core_json','content_markdown','content_format_version');
```

Expected: table count `3`; column count `5`. Drop only the disposable schema after verification.

- [ ] **Step 3: Run complete backend verification**

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-theme-suite-32-bytes'
.\mvnw.cmd -q test
```

Expected: all tests PASS with zero failures/errors.

- [ ] **Step 4: Run complete vocabulary frontend verification**

```powershell
cd ..\web
npx tsx --test "tests/vocabulary*.test.ts"
npm.cmd run build
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:5176'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium
```

Expected: all vocabulary contract tests PASS, production build succeeds, and the vocabulary Playwright flow passes including auth setup.

- [ ] **Step 5: Update architecture, Prompt, and migration documentation**

`docs/architecture/vocabulary-deposition.md` must document theme ownership/versioning, core JSON plus Markdown, legacy template mapping, migration order, partial-card behavior, and rollback. `docs/ai/vocabulary-theme-prompts.md` must document the four strategy keys, safe purpose delimiters, Markdown limits, dictionary precedence, logging fields, and failure modes. Add the AI document to the AI sidebar/index only; keep implementation plans out of main navigation.

- [ ] **Step 6: Build docs and perform final diff checks**

```powershell
cd ..\docs
npm.cmd run build
cd ..
git diff --check
git status --short
```

Expected: docs build succeeds, `git diff --check` has no output, and status contains only intended files.

- [ ] **Step 7: Commit**

```powershell
git add web/tests/vocabularyDepositionFlow.spec.ts backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java docs/architecture/vocabulary-deposition.md docs/ai/vocabulary-theme-prompts.md docs/index.md docs/.vitepress/config.ts
git commit -m "docs(vocabulary): 完善主题化单词卡验收与运行说明"
```

- [ ] **Step 8: Final branch review**

Review `git diff main...HEAD` for Critical/Important findings, rerun the focused test for every changed area after review fixes, then present merge/push/keep/discard options. Do not merge or push without the user's explicit choice.
