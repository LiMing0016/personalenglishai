# Learning Asset Canvas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first version of the Learning Asset Canvas so users can select a word in an assistant reply, create a Markdown vocabulary note, keep the draft in the current conversation, and save it to the global vocabulary area.

**Architecture:** Add a generic `learning_note` backend model and API, but expose only `type=vocabulary` in the first UI. On the web side, add a focused learning asset draft store, a right-side Markdown canvas, assistant reply selection handling, and a vocabulary-page section for saved notes.

**Tech Stack:** Vue 3, TypeScript, localStorage, existing assistant Markdown renderer, Spring Boot 3, MyBatis XML mappers, MySQL, JUnit 5, Mockito, Vite.

---

## Pre-Execution Isolation

The current checkout has many unrelated uncommitted OCR and assistant changes. Execute this plan from an isolated worktree or equivalent isolated workspace on branch `codex/learning-asset-canvas`.

- [ ] **Step 1: Detect whether the current checkout is already isolated**

Run:

```bash
git rev-parse --show-toplevel
git rev-parse --git-dir
git rev-parse --git-common-dir
git branch --show-current
git rev-parse --show-superproject-working-tree
```

Expected:

- If `git-dir` and `git-common-dir` differ and `show-superproject-working-tree` is empty, the checkout is already an isolated worktree.
- Otherwise, ask the user for permission to create an isolated worktree before modifying feature code.

- [ ] **Step 2: Create isolated worktree after permission**

Use a worktree path outside tracked source files or an ignored `.worktrees/` directory. If using project-local `.worktrees/`, verify it is ignored before creation.

Run:

```bash
git check-ignore -q .worktrees || printf '\n.worktrees/\n' >> .gitignore
git add .gitignore
git commit -m "chore: ignore local worktrees"
git worktree add .worktrees/learning-asset-canvas -b codex/learning-asset-canvas
```

Expected:

- `.worktrees/learning-asset-canvas` exists.
- `git -C .worktrees/learning-asset-canvas branch --show-current` prints `codex/learning-asset-canvas`.

If `.worktrees/` is already ignored, skip the `.gitignore` commit.

## File Structure

Backend files to create:

- `backend/src/main/resources/db/migrate_create_learning_note_tables.sql`: creates `learning_note`.
- `backend/src/main/java/com/personalenglishai/backend/entity/learning/LearningNote.java`: backend entity.
- `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteRequest.java`: create/update request.
- `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteResponse.java`: API response.
- `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeRequest.java`: AI organize request.
- `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeResponse.java`: AI organize response.
- `backend/src/main/java/com/personalenglishai/backend/mapper/learning/LearningNoteMapper.java`: MyBatis mapper interface.
- `backend/src/main/resources/mapper/LearningNoteMapper.xml`: MyBatis SQL.
- `backend/src/main/java/com/personalenglishai/backend/service/learning/LearningNoteService.java`: CRUD and normalization.
- `backend/src/main/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeService.java`: prompt and model call for Markdown generation.
- `backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java`: `/api/learning-notes` and organize endpoints.
- `backend/src/test/java/com/personalenglishai/backend/db/LearningNoteSchemaTest.java`: schema contract.
- `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningNoteServiceTest.java`: service behavior.
- `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeServiceTest.java`: AI prompt behavior with mocked model.
- `backend/src/test/java/com/personalenglishai/backend/controller/LearningNoteControllerTest.java`: controller auth and response shape.

Frontend files to create:

- `web/src/types/learningAssets.ts`: shared learning asset types and validators.
- `web/src/api/learningNotes.ts`: backend API client.
- `web/src/pages/app/learningAssetDraftStore.ts`: local draft persistence by conversation.
- `web/src/components/assistant/LearningAssetSelectionToolbar.vue`: selection action toolbar.
- `web/src/components/assistant/LearningAssetCanvas.vue`: right-side Markdown canvas.
- `web/tests/learningAssets.test.ts`: type normalization and template tests.
- `web/tests/learningAssetDraftStore.test.ts`: draft persistence tests.
- `web/tests/assistantLearningAssetSelection.test.ts`: assistant selection action contract.
- `web/tests/learningNotesApi.test.ts`: API contract.
- `web/tests/learningAssetCanvas.test.ts`: canvas state contract.

Frontend files to modify:

- `web/src/components/assistant/AssistantChatView.vue`: emit selection action from assistant message content.
- `web/src/pages/app/AssistantPage.vue`: own the canvas state and draft store integration.
- `web/src/views/VocabularyView.vue`: add saved vocabulary notes section.

Docs to update after implementation:

- `docs/api/index.md` or a new `docs/api/learning-notes.md`: endpoint contract.
- `docs/data/index.md` or a new `docs/data/learning-note-schema.md`: table contract.
- `docs/ai/assistant-output-format.md`: AI learning canvas organize rules.

---

### Task 1: Backend Schema Contract

**Files:**
- Create: `backend/src/main/resources/db/migrate_create_learning_note_tables.sql`
- Create: `backend/src/test/java/com/personalenglishai/backend/db/LearningNoteSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

Create `backend/src/test/java/com/personalenglishai/backend/db/LearningNoteSchemaTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -Dtest=LearningNoteSchemaTest test
```

Expected: FAIL because `migrate_create_learning_note_tables.sql` does not exist.

- [ ] **Step 3: Add the migration**

Create `backend/src/main/resources/db/migrate_create_learning_note_tables.sql`:

```sql
CREATE TABLE IF NOT EXISTS learning_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_markdown MEDIUMTEXT NOT NULL,
    structured_payload JSON NULL,
    source_conversation_uid VARCHAR(64) NULL,
    source_message_uid VARCHAR(64) NULL,
    source_text TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_learning_note_uid (note_uid),
    KEY idx_learning_note_user_type (user_id, type, deleted_at, updated_at),
    KEY idx_learning_note_source_conversation (source_conversation_uid)
);
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
mvn -Dtest=LearningNoteSchemaTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/resources/db/migrate_create_learning_note_tables.sql backend/src/test/java/com/personalenglishai/backend/db/LearningNoteSchemaTest.java
git commit -m "feat(learning): add learning note schema"
```

---

### Task 2: Backend Learning Note CRUD

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/learning/LearningNote.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/learning/LearningNoteMapper.java`
- Create: `backend/src/main/resources/mapper/LearningNoteMapper.xml`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/learning/LearningNoteService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningNoteServiceTest.java`

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningNoteServiceTest.java`:

```java
package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.learning.LearningNoteRequest;
import com.personalenglishai.backend.entity.learning.LearningNote;
import com.personalenglishai.backend.mapper.learning.LearningNoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningNoteServiceTest {

    @Mock
    private LearningNoteMapper mapper;

    @Test
    void createVocabularyNoteNormalizesTypeAndTrimsTitle() {
        LearningNoteService service = new LearningNoteService(mapper);
        LearningNoteRequest request = new LearningNoteRequest();
        request.setType("VOCABULARY");
        request.setTitle("  nuanced  ");
        request.setContentMarkdown("# nuanced");
        request.setSourceConversationId("conv-1");
        request.setSourceMessageId("msg-1");
        request.setSourceText("A nuanced answer considers different sides.");

        LearningNote stored = note("note-1", 7L, "vocabulary", "nuanced", "# nuanced");
        when(mapper.selectByUidForUser(eq(7L), eq("note-1"))).thenReturn(stored);

        var response = service.create(7L, request);

        ArgumentCaptor<LearningNote> noteCaptor = ArgumentCaptor.forClass(LearningNote.class);
        verify(mapper).insert(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getType()).isEqualTo("vocabulary");
        assertThat(noteCaptor.getValue().getTitle()).isEqualTo("nuanced");
        assertThat(noteCaptor.getValue().getContentMarkdown()).isEqualTo("# nuanced");
        assertThat(response.getNoteUid()).isEqualTo("note-1");
        assertThat(response.getType()).isEqualTo("vocabulary");
    }

    @Test
    void createRejectsBlankMarkdown() {
        LearningNoteService service = new LearningNoteService(mapper);
        LearningNoteRequest request = new LearningNoteRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setContentMarkdown(" ");

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentMarkdown");
    }

    @Test
    void listVocabularyNotesUsesSafePaging() {
        LearningNoteService service = new LearningNoteService(mapper);
        when(mapper.selectByUserAndType(7L, "vocabulary", 0, 20))
                .thenReturn(List.of(note("note-1", 7L, "vocabulary", "nuanced", "# nuanced")));
        when(mapper.countByUserAndType(7L, "vocabulary")).thenReturn(1L);

        var page = service.list(7L, "vocabulary", 0, 200);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private LearningNote note(String uid, Long userId, String type, String title, String markdown) {
        LearningNote note = new LearningNote();
        note.setNoteUid(uid);
        note.setUserId(userId);
        note.setType(type);
        note.setTitle(title);
        note.setContentMarkdown(markdown);
        note.setStatus("active");
        note.setCreatedAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        note.setUpdatedAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        return note;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -Dtest=LearningNoteServiceTest test
```

Expected: FAIL because classes do not exist.

- [ ] **Step 3: Add entity, DTOs, mapper, service**

Implement these signatures:

```java
// backend/src/main/java/com/personalenglishai/backend/entity/learning/LearningNote.java
package com.personalenglishai.backend.entity.learning;

import java.time.LocalDateTime;

public class LearningNote {
    private Long id;
    private String noteUid;
    private Long userId;
    private String type;
    private String title;
    private String contentMarkdown;
    private String structuredPayload;
    private String sourceConversationUid;
    private String sourceMessageUid;
    private String sourceText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // Generate standard getters and setters for every field.
}
```

```java
// backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteRequest.java
package com.personalenglishai.backend.dto.learning;

public class LearningNoteRequest {
    private String type;
    private String title;
    private String contentMarkdown;
    private String structuredPayload;
    private String sourceConversationId;
    private String sourceMessageId;
    private String sourceText;

    // Generate standard getters and setters for every field.
}
```

```java
// backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteResponse.java
package com.personalenglishai.backend.dto.learning;

import java.time.LocalDateTime;

public class LearningNoteResponse {
    private String noteUid;
    private String type;
    private String title;
    private String contentMarkdown;
    private String structuredPayload;
    private String sourceConversationId;
    private String sourceMessageId;
    private String sourceText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Generate standard getters and setters for every field.
}
```

```java
// backend/src/main/java/com/personalenglishai/backend/mapper/learning/LearningNoteMapper.java
package com.personalenglishai.backend.mapper.learning;

import com.personalenglishai.backend.entity.learning.LearningNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningNoteMapper {
    int insert(LearningNote note);
    int updateForUser(LearningNote note);
    LearningNote selectByUidForUser(@Param("userId") Long userId, @Param("noteUid") String noteUid);
    List<LearningNote> selectByUserAndType(@Param("userId") Long userId, @Param("type") String type, @Param("offset") int offset, @Param("size") int size);
    long countByUserAndType(@Param("userId") Long userId, @Param("type") String type);
    int softDelete(@Param("userId") Long userId, @Param("noteUid") String noteUid);
}
```

Create `backend/src/main/java/com/personalenglishai/backend/service/learning/LearningNoteService.java` with methods:

```java
public LearningNoteResponse create(Long userId, LearningNoteRequest request)
public LearningNoteResponse update(Long userId, String noteUid, LearningNoteRequest request)
public LearningNoteResponse get(Long userId, String noteUid)
public AdminPageResponse<LearningNoteResponse> list(Long userId, String type, Integer page, Integer size)
public void delete(Long userId, String noteUid)
```

Normalization rules:

```java
private String normalizeType(String value) {
    String normalized = value == null ? "vocabulary" : value.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("vocabulary", "sentence", "grammar", "expression").contains(normalized)) {
        throw new IllegalArgumentException("invalid type");
    }
    return normalized;
}
```

Validation rules:

```java
if (userId == null) throw new IllegalArgumentException("invalid user");
if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("title required");
if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) throw new IllegalArgumentException("contentMarkdown required");
```

For deterministic tests, use a package-private UID factory method:

```java
String createNoteUid() {
    return "note-" + UUID.randomUUID().toString().replace("-", "");
}
```

In the test, mock `mapper.selectByUidForUser(7L, "note-1")` and set note UID to `note-1` by overriding the service in an anonymous subclass:

```java
LearningNoteService service = new LearningNoteService(mapper) {
    @Override
    String createNoteUid() {
        return "note-1";
    }
};
```

`LearningNoteMapper.xml` must map every column and implement the mapper methods with `deleted_at IS NULL`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
mvn -Dtest=LearningNoteServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/java/com/personalenglishai/backend/entity/learning/LearningNote.java \
  backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteRequest.java \
  backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningNoteResponse.java \
  backend/src/main/java/com/personalenglishai/backend/mapper/learning/LearningNoteMapper.java \
  backend/src/main/resources/mapper/LearningNoteMapper.xml \
  backend/src/main/java/com/personalenglishai/backend/service/learning/LearningNoteService.java \
  backend/src/test/java/com/personalenglishai/backend/service/learning/LearningNoteServiceTest.java
git commit -m "feat(learning): add learning note service"
```

---

### Task 3: Backend Controller and AI Organize Endpoint

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeServiceTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/controller/LearningNoteControllerTest.java`

- [ ] **Step 1: Write failing organize service test**

Create `backend/src/test/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeServiceTest.java`:

```java
package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningCanvasOrganizeServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    @Test
    void createModeAsksModelForVocabularyMarkdown() {
        LearningCanvasOrganizeService service = new LearningCanvasOrganizeService(openAiClient);
        LearningCanvasOrganizeRequest request = new LearningCanvasOrganizeRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setSelectedText("nuanced");
        request.setContextText("A nuanced answer considers different sides.");
        request.setMode("create");

        when(openAiClient.callWithProvider(eq(null), anyString(), anyString(), eq("learning-canvas-organize"), eq(0.2), eq(1200)))
                .thenReturn("# nuanced\n\n**词性：** adjective");

        var response = service.organize(request);

        assertThat(response.getCandidateMarkdown()).startsWith("# nuanced");
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(eq(null), anyString(), userPrompt.capture(), eq("learning-canvas-organize"), eq(0.2), eq(1200));
        assertThat(userPrompt.getValue()).contains("nuanced");
        assertThat(userPrompt.getValue()).contains("默认单词卡模板");
    }

    @Test
    void formatModePreservesUserMarkdownInstruction() {
        LearningCanvasOrganizeService service = new LearningCanvasOrganizeService(openAiClient);
        LearningCanvasOrganizeRequest request = new LearningCanvasOrganizeRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setCurrentMarkdown("# nuanced\nmy own note");
        request.setMode("format");

        when(openAiClient.callWithProvider(eq(null), anyString(), anyString(), eq("learning-canvas-organize"), eq(0.2), eq(1200)))
                .thenReturn("# nuanced\n\n## 我的笔记\nmy own note");

        service.organize(request);

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithProvider(eq(null), anyString(), userPrompt.capture(), eq("learning-canvas-organize"), eq(0.2), eq(1200));
        assertThat(userPrompt.getValue()).contains("尽量保留用户原意");
        assertThat(userPrompt.getValue()).contains("my own note");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -Dtest=LearningCanvasOrganizeServiceTest test
```

Expected: FAIL because organize classes do not exist.

- [ ] **Step 3: Implement organize DTOs and service**

Create request and response DTOs with standard getters/setters:

```java
public class LearningCanvasOrganizeRequest {
    private String type;
    private String title;
    private String selectedText;
    private String contextText;
    private String currentMarkdown;
    private String mode;
}
```

```java
public class LearningCanvasOrganizeResponse {
    private String candidateMarkdown;
}
```

Create `LearningCanvasOrganizeService`:

```java
package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import org.springframework.stereotype.Service;

@Service
public class LearningCanvasOrganizeService {
    private static final String SYSTEM_PROMPT = """
            你是英语学习笔记整理助手。只输出 Markdown，不输出解释性前后缀。
            你必须帮助用户整理学习资产，首版类型是 vocabulary。
            format 模式下尽量保留用户原意，不要删除用户的个人笔记。
            """;

    private final OpenAiClient openAiClient;

    public LearningCanvasOrganizeService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public LearningCanvasOrganizeResponse organize(LearningCanvasOrganizeRequest request) {
        String userPrompt = buildPrompt(request);
        String markdown = openAiClient.callWithProvider(null, SYSTEM_PROMPT, userPrompt, "learning-canvas-organize", 0.2, 1200);
        LearningCanvasOrganizeResponse response = new LearningCanvasOrganizeResponse();
        response.setCandidateMarkdown(markdown == null ? "" : markdown.trim());
        return response;
    }

    String buildPrompt(LearningCanvasOrganizeRequest request) {
        String mode = request.getMode() == null ? "create" : request.getMode().trim().toLowerCase();
        if ("format".equals(mode)) {
            return "请优化下面 Markdown 的格式，尽量保留用户原意：\n\n" + safe(request.getCurrentMarkdown());
        }
        return """
                请按默认单词卡模板整理 vocabulary 学习资产。

                默认单词卡模板：
                # {{title}}
                **词性：**
                **中文释义：**
                **English meaning：**
                **原句：**
                **AI 例句：**
                **常见搭配：**
                ## 我的笔记

                title: %s
                selectedText: %s
                contextText: %s
                """.formatted(safe(request.getTitle()), safe(request.getSelectedText()), safe(request.getContextText()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
```

- [ ] **Step 4: Write failing controller test**

Create `backend/src/test/java/com/personalenglishai/backend/controller/LearningNoteControllerTest.java` with Mockito mocks for `LearningNoteService` and `LearningCanvasOrganizeService`. Test that unauthenticated create returns 401 and authenticated create returns `ApiResponse.success`.

Use this core assertion:

```java
assertThat(response.getStatusCode().value()).isEqualTo(200);
assertThat(response.getBody().getData().getTitle()).isEqualTo("nuanced");
```

- [ ] **Step 5: Implement controller**

Create `backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java`:

```java
@RestController
@RequestMapping("/api/learning-notes")
public class LearningNoteController {
    private final LearningNoteService learningNoteService;
    private final LearningCanvasOrganizeService organizeService;

    public LearningNoteController(LearningNoteService learningNoteService,
                                  LearningCanvasOrganizeService organizeService) {
        this.learningNoteService = learningNoteService;
        this.organizeService = organizeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LearningNoteResponse>> create(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody LearningNoteRequest request) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.create(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<LearningNoteResponse>>> list(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(required = false, defaultValue = "vocabulary") String type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.list(userId, type, page, size)));
    }

    @PostMapping("/organize")
    public ResponseEntity<ApiResponse<LearningCanvasOrganizeResponse>> organize(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody LearningCanvasOrganizeRequest request) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(organizeService.organize(request)));
    }
}
```

Also implement `GET /{noteUid}`, `PUT /{noteUid}`, and `DELETE /{noteUid}` in the same controller using service methods from Task 2.

- [ ] **Step 6: Run backend tests**

Run:

```bash
cd backend
mvn -Dtest=LearningCanvasOrganizeServiceTest,LearningNoteControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeRequest.java \
  backend/src/main/java/com/personalenglishai/backend/dto/learning/LearningCanvasOrganizeResponse.java \
  backend/src/main/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeService.java \
  backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java \
  backend/src/test/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeServiceTest.java \
  backend/src/test/java/com/personalenglishai/backend/controller/LearningNoteControllerTest.java
git commit -m "feat(learning): add learning note API"
```

---

### Task 4: Frontend Types, API Client, and Draft Store

**Files:**
- Create: `web/src/types/learningAssets.ts`
- Create: `web/src/api/learningNotes.ts`
- Create: `web/src/pages/app/learningAssetDraftStore.ts`
- Create: `web/tests/learningAssets.test.ts`
- Create: `web/tests/learningNotesApi.test.ts`
- Create: `web/tests/learningAssetDraftStore.test.ts`

- [ ] **Step 1: Write failing type and store tests**

Create `web/tests/learningAssets.test.ts`:

```ts
import assert from 'node:assert/strict'

import { buildVocabularyMarkdown, normalizeLearningAssetType } from '../src/types/learningAssets.ts'

assert.equal(normalizeLearningAssetType('VOCABULARY'), 'vocabulary')
assert.equal(normalizeLearningAssetType('unknown'), 'vocabulary')
assert.match(buildVocabularyMarkdown({ title: 'nuanced', sourceText: 'A nuanced answer.' }), /^# nuanced/)
assert.match(buildVocabularyMarkdown({ title: 'nuanced', sourceText: 'A nuanced answer.' }), /\*\*原句：\*\* A nuanced answer\./)

console.log('learning-assets-ok')
```

Create `web/tests/learningAssetDraftStore.test.ts`:

```ts
import assert from 'node:assert/strict'

import { createLearningAssetDraftStore } from '../src/pages/app/learningAssetDraftStore.ts'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return Array.from(this.values.keys())[index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

const storage = new MemoryStorage()
const store = createLearningAssetDraftStore({ storage })

const draft = store.createDraft('conv-1', {
  type: 'vocabulary',
  title: 'nuanced',
  sourceMessageId: 'msg-1',
  sourceText: 'A nuanced answer.',
})

assert.equal(draft.title, 'nuanced')
assert.equal(store.listDrafts('conv-1').length, 1)

store.updateDraft('conv-1', draft.id, { contentMarkdown: '# nuanced\nchanged' })

const restored = createLearningAssetDraftStore({ storage })
assert.equal(restored.listDrafts('conv-1')[0]?.contentMarkdown, '# nuanced\nchanged')

restored.markSaved('conv-1', draft.id)
assert.equal(restored.listDrafts('conv-1')[0]?.status, 'saved')

console.log('learning-asset-drafts-ok')
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd web
npx tsx tests/learningAssets.test.ts
npx tsx tests/learningAssetDraftStore.test.ts
```

Expected: FAIL because files do not exist.

- [ ] **Step 3: Implement types and draft store**

Create `web/src/types/learningAssets.ts`:

```ts
export type LearningAssetType = 'vocabulary' | 'sentence' | 'grammar' | 'expression'
export type LearningAssetStatus = 'draft' | 'saved'

export interface LearningAssetDraft {
  id: string
  type: LearningAssetType
  title: string
  contentMarkdown: string
  structuredPayload?: unknown
  sourceConversationId?: string
  sourceMessageId?: string
  sourceText?: string
  status: LearningAssetStatus
  updatedAt: string
}

export interface BuildVocabularyMarkdownInput {
  title: string
  sourceText?: string
}

export function normalizeLearningAssetType(value: unknown): LearningAssetType {
  return value === 'sentence' || value === 'grammar' || value === 'expression' || value === 'vocabulary'
    ? value
    : typeof value === 'string' && value.toLowerCase() === 'vocabulary'
      ? 'vocabulary'
      : 'vocabulary'
}

export function buildVocabularyMarkdown(input: BuildVocabularyMarkdownInput) {
  const title = input.title.trim()
  const sourceLine = input.sourceText?.trim() ? `\n**原句：** ${input.sourceText.trim()}\n` : ''
  return `# ${title}\n\n**词性：** \n\n**中文释义：** \n\n**English meaning：** \n${sourceLine}\n**AI 例句：** \n\n**常见搭配：** \n\n## 我的笔记\n\n`
}
```

Create `web/src/pages/app/learningAssetDraftStore.ts` with `createDraft`, `updateDraft`, `markSaved`, and `listDrafts`. Use key `peai:assistant:learning-asset-drafts:${conversationId}` and `JSON.stringify`.

Create `web/src/api/learningNotes.ts`:

```ts
import { http } from './http'
import type { LearningAssetType } from '@/types/learningAssets'

export interface LearningNoteDto {
  noteUid: string
  type: LearningAssetType
  title: string
  contentMarkdown: string
  structuredPayload?: string
  sourceConversationId?: string
  sourceMessageId?: string
  sourceText?: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface LearningNoteListResponse {
  items: LearningNoteDto[]
  total: number
  page: number
  size: number
}

export function createLearningNote(payload: Omit<LearningNoteDto, 'noteUid' | 'status' | 'createdAt' | 'updatedAt'>) {
  return http.post<{ data?: LearningNoteDto; message: string }>('/learning-notes', payload).then((res) => {
    if (!res.data.data) throw new Error(res.data.message || 'learning note create failed')
    return res.data.data
  })
}

export function listLearningNotes(type: LearningAssetType = 'vocabulary') {
  return http.get<{ data?: LearningNoteListResponse; message: string }>('/learning-notes', { params: { type } }).then((res) => {
    if (!res.data.data) throw new Error(res.data.message || 'learning note list failed')
    return res.data.data
  })
}

export function organizeLearningCanvas(payload: {
  type: LearningAssetType
  title: string
  selectedText?: string
  contextText?: string
  currentMarkdown?: string
  mode: 'create' | 'format'
}) {
  return http.post<{ data?: { candidateMarkdown: string }; message: string }>('/learning-notes/organize', payload).then((res) => {
    if (!res.data.data) throw new Error(res.data.message || 'learning canvas organize failed')
    return res.data.data
  })
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd web
npx tsx tests/learningAssets.test.ts
npx tsx tests/learningAssetDraftStore.test.ts
```

Expected: both print ok messages.

- [ ] **Step 5: Commit**

Run:

```bash
git add web/src/types/learningAssets.ts web/src/api/learningNotes.ts web/src/pages/app/learningAssetDraftStore.ts web/tests/learningAssets.test.ts web/tests/learningAssetDraftStore.test.ts web/tests/learningNotesApi.test.ts
git commit -m "feat(web): add learning asset draft data layer"
```

---

### Task 5: Assistant Selection Toolbar and Canvas UI

**Files:**
- Create: `web/src/components/assistant/LearningAssetSelectionToolbar.vue`
- Create: `web/src/components/assistant/LearningAssetCanvas.vue`
- Create: `web/tests/assistantLearningAssetSelection.test.ts`
- Create: `web/tests/learningAssetCanvas.test.ts`
- Modify: `web/src/components/assistant/AssistantChatView.vue`

- [ ] **Step 1: Write failing selection helper test**

Create `web/tests/assistantLearningAssetSelection.test.ts`:

```ts
import assert from 'node:assert/strict'

import { normalizeAssistantSelectionText } from '../src/components/assistant/assistantLearningAssetSelection.ts'

assert.equal(normalizeAssistantSelectionText('  nuanced  '), 'nuanced')
assert.equal(normalizeAssistantSelectionText('\nA nuanced answer\n'), 'A nuanced answer')
assert.equal(normalizeAssistantSelectionText(''), '')

console.log('assistant-learning-asset-selection-ok')
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd web
npx tsx tests/assistantLearningAssetSelection.test.ts
```

Expected: FAIL because helper does not exist.

- [ ] **Step 3: Implement selection helper and toolbar component**

Create `web/src/components/assistant/assistantLearningAssetSelection.ts`:

```ts
export interface AssistantLearningAssetSelection {
  text: string
  messageId: string
  contextText: string
  rect: DOMRect
}

export function normalizeAssistantSelectionText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}
```

Create toolbar component props:

```ts
defineProps<{
  x: number
  y: number
  visible: boolean
}>()

defineEmits<{
  createVocabulary: []
}>()
```

Render a single button:

```html
<button type="button" class="learning-asset-selection-button" @mousedown.prevent @click="$emit('createVocabulary')">
  新建单词卡
</button>
```

- [ ] **Step 4: Modify AssistantChatView selection behavior**

In `AssistantChatView.vue`:

- Add `LearningAssetSelectionToolbar`.
- Add emit `createLearningAsset`.
- Attach `@mouseup` and `@keyup` handlers to assistant Markdown content.
- Only create selection events for assistant messages.

Emit payload:

```ts
{
  type: 'vocabulary',
  title: selectedText,
  selectedText,
  sourceMessageId: message.id,
  sourceText: message.content,
}
```

- [ ] **Step 5: Add canvas component**

Create `LearningAssetCanvas.vue` props:

```ts
defineProps<{
  draft: LearningAssetDraft | null
  loading: boolean
  errorMessage: string
  previewMarkdown: string
}>()
```

Emits:

```ts
defineEmits<{
  close: []
  updateMarkdown: [value: string]
  organize: []
  format: []
  acceptPreview: []
  rejectPreview: []
  save: []
}>()
```

UI:

- Header with title, type chip, close button.
- Toolbar buttons: `AI 整理`, `优化格式`, `预览`, `保存为学习资产`.
- Markdown `<textarea>`.
- Preview panel shown when previewMarkdown is non-empty.

- [ ] **Step 6: Run component contract tests**

Run:

```bash
cd web
npx tsx tests/assistantLearningAssetSelection.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add web/src/components/assistant/assistantLearningAssetSelection.ts \
  web/src/components/assistant/LearningAssetSelectionToolbar.vue \
  web/src/components/assistant/LearningAssetCanvas.vue \
  web/src/components/assistant/AssistantChatView.vue \
  web/tests/assistantLearningAssetSelection.test.ts \
  web/tests/learningAssetCanvas.test.ts
git commit -m "feat(web): add assistant learning asset canvas UI"
```

---

### Task 6: Assistant Page Integration

**Files:**
- Modify: `web/src/pages/app/AssistantPage.vue`
- Create: `web/tests/assistantLearningAssetCanvasFlow.test.ts`

- [ ] **Step 1: Write failing flow test for state functions**

Create `web/tests/assistantLearningAssetCanvasFlow.test.ts`:

```ts
import assert from 'node:assert/strict'

import { createLearningAssetDraftStore } from '../src/pages/app/learningAssetDraftStore.ts'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return Array.from(this.values.keys())[index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

const store = createLearningAssetDraftStore({ storage: new MemoryStorage() })
const draft = store.createDraft('conv-1', {
  type: 'vocabulary',
  title: 'nuanced',
  sourceMessageId: 'msg-1',
  sourceText: 'A nuanced answer considers different sides.',
})

assert.equal(draft.type, 'vocabulary')
assert.match(draft.contentMarkdown, /^# nuanced/)
assert.equal(store.listDrafts('conv-1')[0]?.sourceMessageId, 'msg-1')

console.log('assistant-learning-asset-canvas-flow-ok')
```

- [ ] **Step 2: Run test**

Run:

```bash
cd web
npx tsx tests/assistantLearningAssetCanvasFlow.test.ts
```

Expected: PASS after Task 4; if it fails, fix the draft store before UI integration.

- [ ] **Step 3: Integrate canvas into AssistantPage**

In `AssistantPage.vue`:

- Import `LearningAssetCanvas`.
- Import draft store and API functions.
- Add refs:

```ts
const learningAssetDraftStore = createLearningAssetDraftStore()
const activeLearningAssetDraftId = ref<string | null>(null)
const learningAssetPreviewMarkdown = ref('')
const learningAssetLoading = ref(false)
const learningAssetError = ref('')
```

- Add computed active draft from current conversation ID and draft ID.
- On `create-learning-asset` from chat view, call `createDraft(activeConversation.value.id, payload)`, set active ID, and open canvas.
- On markdown update, call store `updateDraft`.
- On organize, call `organizeLearningCanvas({ mode: 'create' })`.
- On format, call `organizeLearningCanvas({ mode: 'format', currentMarkdown })` and set preview.
- On accept preview, replace draft markdown and clear preview.
- On save, call `createLearningNote`, then `markSaved`.

- [ ] **Step 4: Verify TypeScript compilation locally**

Run:

```bash
cd web
npm run build
```

Expected: `vue-tsc` and `vite build` complete successfully.

- [ ] **Step 5: Commit**

Run:

```bash
git add web/src/pages/app/AssistantPage.vue web/tests/assistantLearningAssetCanvasFlow.test.ts
git commit -m "feat(web): wire learning asset canvas into assistant"
```

---

### Task 7: Vocabulary Page Saved Notes

**Files:**
- Modify: `web/src/views/VocabularyView.vue`
- Create: `web/tests/vocabularyLearningNotes.test.ts`

- [ ] **Step 1: Write saved-note formatting test**

Create `web/tests/vocabularyLearningNotes.test.ts`:

```ts
import assert from 'node:assert/strict'

import { summarizeLearningNoteMarkdown } from '../src/views/vocabularyLearningNotes.ts'

assert.equal(
  summarizeLearningNoteMarkdown('# nuanced\n\n**中文释义：** 有细微差别的\n\n## 我的笔记\n需要复习'),
  '中文释义： 有细微差别的',
)
assert.equal(summarizeLearningNoteMarkdown(''), '暂无笔记摘要')

console.log('vocabulary-learning-notes-ok')
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd web
npx tsx tests/vocabularyLearningNotes.test.ts
```

Expected: FAIL because helper does not exist.

- [ ] **Step 3: Add helper and page section**

Create `web/src/views/vocabularyLearningNotes.ts`:

```ts
export function summarizeLearningNoteMarkdown(markdown: string) {
  const firstMeaningLine = markdown
    .split('\n')
    .map((line) => line.replace(/[*#>`-]/g, '').trim())
    .find((line) => line && !line.startsWith('nuanced'))
  return firstMeaningLine || '暂无笔记摘要'
}
```

Modify `VocabularyView.vue` collection page:

- Load `listLearningNotes('vocabulary')` on entering collection view.
- Add a section title `我的单词笔记`.
- Render note title, summary, updated time, and button `查看笔记`.
- Keep existing `词典收藏` table below this section.

- [ ] **Step 4: Run helper test and build**

Run:

```bash
cd web
npx tsx tests/vocabularyLearningNotes.test.ts
npm run build
```

Expected: helper test prints ok and build passes.

- [ ] **Step 5: Commit**

Run:

```bash
git add web/src/views/VocabularyView.vue web/src/views/vocabularyLearningNotes.ts web/tests/vocabularyLearningNotes.test.ts
git commit -m "feat(web): show vocabulary learning notes"
```

---

### Task 8: Docs and Final Verification

**Files:**
- Create: `docs/api/learning-notes.md`
- Create: `docs/data/learning-note-schema.md`
- Modify: `docs/ai/assistant-output-format.md`
- Modify: `docs/.vitepress/config.ts` only if these docs should appear in sidebar.

- [ ] **Step 1: Add API doc**

Create `docs/api/learning-notes.md` with frontmatter:

```md
---
title: 学习资产 API
status: active
owner: backend
last_updated: 2026-06-24
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java
related_docs:
  - docs/superpowers/specs/2026-06-24-learning-asset-canvas-design.md
---

# 学习资产 API

## 当前结论

学习资产 API 提供学习笔记的创建、查询、更新、删除和 AI Markdown 整理能力。首版只开放 `type=vocabulary` 的 Web 使用场景。

## Endpoint

- `POST /api/learning-notes`
- `GET /api/learning-notes?type=vocabulary&page=1&size=20`
- `GET /api/learning-notes/{noteUid}`
- `PUT /api/learning-notes/{noteUid}`
- `DELETE /api/learning-notes/{noteUid}`
- `POST /api/learning-notes/organize`

## 鉴权

所有接口都需要登录态。未登录返回 `401000`。
```

- [ ] **Step 2: Add data doc**

Create `docs/data/learning-note-schema.md` with the `learning_note` columns from the migration and lifecycle rules:

```md
草稿阶段由 Web 本地保存；保存为学习资产后，后端 `learning_note` 成为真源。
删除使用 `deleted_at` 软删除。
```

- [ ] **Step 3: Update AI output format doc**

Append a section to `docs/ai/assistant-output-format.md`:

```md
## 学习资产画布 Markdown 整理

`/api/learning-notes/organize` 只返回 Markdown 正文。`format` 模式必须尽量保留用户原意，不能直接覆盖用户笔记；前端必须先展示候选预览，由用户确认后替换。
```

- [ ] **Step 4: Run full verification**

Run:

```bash
cd backend
mvn -Dtest=LearningNoteSchemaTest,LearningNoteServiceTest,LearningCanvasOrganizeServiceTest,LearningNoteControllerTest test
```

Expected: PASS.

Run:

```bash
cd web
npx tsx tests/learningAssets.test.ts
npx tsx tests/learningAssetDraftStore.test.ts
npx tsx tests/assistantLearningAssetSelection.test.ts
npx tsx tests/assistantLearningAssetCanvasFlow.test.ts
npx tsx tests/vocabularyLearningNotes.test.ts
npm run build
```

Expected: all tests print ok and build passes.

Run:

```bash
cd docs
npm run build
```

Expected: VitePress build passes.

- [ ] **Step 5: Commit docs and verification fixes**

Run:

```bash
git add docs/api/learning-notes.md docs/data/learning-note-schema.md docs/ai/assistant-output-format.md docs/.vitepress/config.ts
git commit -m "docs: document learning asset APIs"
```

---

## Self-Review

Spec coverage:

- Selection action is covered in Task 5.
- Fixed right-side canvas is covered in Tasks 5 and 6.
- Markdown draft creation and persistence are covered in Task 4.
- Save to global learning assets is covered in Tasks 2, 3, and 6.
- Vocabulary page visibility is covered in Task 7.
- AI organize and format preview are covered in Tasks 3 and 6.
- Backend schema and API are covered in Tasks 1, 2, and 3.
- Docs and verification are covered in Task 8.

Type consistency:

- Frontend uses `LearningAssetDraft`, `LearningAssetType`, `contentMarkdown`, `sourceConversationId`, and `sourceMessageId`.
- Backend uses `LearningNote`, `LearningNoteRequest`, `LearningNoteResponse`, `contentMarkdown`, `sourceConversationId`, and `sourceMessageId`.
- Database columns use snake case equivalents: `content_markdown`, `source_conversation_uid`, and `source_message_uid`.

Execution note:

- Do not implement this plan directly in the dirty `codex/paddle-ocr-high-quality-server` checkout.
- Start implementation from an isolated worktree or a clean branch named `codex/learning-asset-canvas`.
