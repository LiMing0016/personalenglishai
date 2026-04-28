# Exam Setup Dialogue Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the exam setup page into a multi-turn prompt-design workbench where the left pane behaves like a dialogue assistant, attachments enter the message history, and the right pane auto-refreshes a draft/final prompt sheet after every turn.

**Architecture:** Keep `ExamSetupPage.vue` as the page-level orchestrator, but stop treating it as the storage and rendering home for every UI concern. Add one backend “dialogue turn” endpoint that receives the current conversation context and returns both a structured assistant reply and the current prompt-sheet draft state. On the frontend, extract message types, conversation helpers, and pane components so the left chat flow and right preview states share one page-scoped source of truth instead of ad hoc booleans.

**Tech Stack:** Vue 3, TypeScript, existing node:test helper pattern, Spring Boot, existing writing prompt services, existing AI provider abstraction, Maven, Vite.

---

## File Structure

### Frontend files

- Modify: `web/src/pages/app/ExamSetupPage.vue`
  - Reduce it to page orchestration, route exit logic, mode switching, and start-writing handoff
  - Replace the current one-shot compose flow with conversation-driven state
- Create: `web/src/pages/app/exam-setup/examWorkbenchConversation.ts`
  - Page-scoped message types, draft status types, message builders, and turn-application helpers
- Create: `web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`
  - node:test coverage for message insertion, asset replacement, draft status transitions, and start-button gating
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchConversationPane.vue`
  - Left-pane message history, asset message rendering, assistant messages, status rows
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchComposer.vue`
  - Bottom composer, attachment menu, provider picker, send/cancel action
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchPreviewPane.vue`
  - Right-pane empty/draft/ready states and paper-sheet rendering wrapper
- Modify: `web/src/pages/app/examWorkbenchState.ts`
  - Remove assumptions that the workbench is only `empty/waiting/ready` from a single submitted text
  - Keep only narrow page-level helpers that still make sense
- Modify: `web/src/pages/app/examPromptHelpers.ts`
  - Reuse prompt-sheet assembly helpers in the new preview component without duplicating paper rendering logic
- Modify: `web/src/api/writing.ts`
  - Add the dialogue-turn request/response contract

### Backend files

- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnRequest.java`
  - Request DTO for current user message, current attachment snapshot, current conversation messages, study stage, mode, and AI provider
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnResponse.java`
  - Response DTO for assistant reply blocks, prompt completeness status, prompt-sheet draft, and missing-field hints
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/ExamWorkbenchMessageDto.java`
  - Narrow DTO for backend-readable conversation turns
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/WritingExamDialogueService.java`
  - Service boundary for one dialogue turn
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamDialogueServiceImpl.java`
  - Compose audit + prompt-sheet generation + assistant reply synthesis in one place
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
  - Add `POST /api/writing/generate-exam-dialogue-turn`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java`
  - Reuse extraction logic from the dialogue service without pushing controller logic into the page
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java`
  - Expose any narrow helper needed to build prompt-sheet drafts without re-parsing the same structure twice
- Create: `backend/src/main/resources/prompts/exam-sheet/exam-dialogue-system.md`
  - Prompt for the assistant “planner” reply blocks and missing-info guidance
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`
  - Endpoint contract tests for the new dialogue-turn API
- Create: `backend/src/test/java/com/personalenglishai/backend/service/writing/WritingExamDialogueServiceTest.java`
  - Service tests for reply structure, missing-field hints, and prompt-sheet status selection

### Docs

- Modify: `docs/writing-ai-exam-prompt-api.md`
  - Document the new dialogue-turn endpoint and explain how it differs from one-shot prompt generation

## Task 1: Lock the backend dialogue-turn contract

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/ExamWorkbenchMessageDto.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`

- [ ] **Step 1: Write the failing controller test for the new dialogue-turn endpoint**

```java
@Test
void generateExamDialogueTurn_returnsAssistantReplyAndDraftPreview() throws Exception {
    when(writingExamDialogueService.generateTurn(eq(1L), any()))
            .thenReturn(new GenerateExamDialogueTurnResponse(
                    List.of(new AssistantReplyBlockDto("understanding", "我理解你想保留原题表述。")),
                    "draft",
                    List.of("待补充字数"),
                    samplePromptSheetDraft()
            ));

    mockMvc.perform(post("/api/writing/generate-exam-dialogue-turn")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "studyStage": "postgrad",
                              "aiProvider": "openai",
                              "messages": [{"role":"user","kind":"text","text":"不要改图片原题"}]
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewStatus").value("draft"))
            .andExpect(jsonPath("$.assistantReplyBlocks[0].text").value("我理解你想保留原题表述。"));
}
```

- [ ] **Step 2: Run the narrow controller test to verify it fails**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest" test`

Expected: FAIL because the endpoint DTOs and controller wiring do not exist yet.

- [ ] **Step 3: Add the minimal DTOs for request messages and response shape**

```java
public class ExamWorkbenchMessageDto {
    private String role;
    private String kind;
    private String text;
    private String assetType;
    private String assetSummary;
}
```

```java
public class GenerateExamDialogueTurnResponse {
    private List<AssistantReplyBlockDto> assistantReplyBlocks;
    private String previewStatus;
    private List<String> missingFields;
    private GenerateExamPromptResponse promptSheetDraft;
}
```

- [ ] **Step 4: Re-run the controller test**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest" test`

Expected: FAIL again, but now at missing controller/service implementation instead of DTO compilation.

- [ ] **Step 5: Commit the contract**

```bash
git add backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnRequest.java backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamDialogueTurnResponse.java backend/src/main/java/com/personalenglishai/backend/dto/writing/ExamWorkbenchMessageDto.java backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java
git commit -m "feat(prompt): 定义命题对话回合接口契约"
```

## Task 2: Implement the backend dialogue-turn service

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/WritingExamDialogueService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamDialogueServiceImpl.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java`
- Create: `backend/src/main/resources/prompts/exam-sheet/exam-dialogue-system.md`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/writing/WritingExamDialogueServiceTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`

- [ ] **Step 1: Write the failing service test for draft-vs-ready status selection**

```java
@Test
void generateTurn_marksDraftWhenWordRangeIsMissing() {
    when(auditTopicService.audit(any(), eq("openai")))
            .thenReturn(AuditTopicResponse.needMoreInfo(
                    "Write an essay based on the picture below.",
                    "comic",
                    "看图作文",
                    null,
                    "1) describe the picture briefly 2) interpret the meaning 3) give your comments",
                    "请补充字数范围"
            ));

    var response = service.generateTurn(1L, requestWithSingleUserMessage());

    assertThat(response.getPreviewStatus()).isEqualTo("draft");
    assertThat(response.getMissingFields()).contains("待补充字数");
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `./mvnw.cmd -q "-Dtest=WritingExamDialogueServiceTest" test`

Expected: FAIL because the service does not exist yet.

- [ ] **Step 3: Implement the minimal dialogue-turn service and controller**

Implementation rules:

- Reuse `AuditTopicService` to extract normalized topic intent
- Reuse prompt-sheet generation instead of inventing a second paper-format pipeline
- Add one assistant-reply prompt that produces structured sections:
  - `understanding`
  - `follow_up`
  - `action`
- Derive `previewStatus` as `empty | draft | ready`
- Populate `missingFields` from normalized gaps, not from freeform model prose

Minimal controller wiring:

```java
@PostMapping("/generate-exam-dialogue-turn")
public ResponseEntity<GenerateExamDialogueTurnResponse> generateExamDialogueTurn(
        @Valid @RequestBody GenerateExamDialogueTurnRequest request,
        HttpServletRequest httpRequest) {
    Long userId = (Long) httpRequest.getAttribute("userId");
    if (userId == null) {
        return ResponseEntity.status(401).build();
    }
    return ResponseEntity.ok(writingExamDialogueService.generateTurn(userId, request));
}
```

- [ ] **Step 4: Re-run service + controller tests**

Run: `./mvnw.cmd -q "-Dtest=WritingExamDialogueServiceTest,WritingControllerTest" test`

Expected: PASS for the new endpoint and service behavior.

- [ ] **Step 5: Commit the backend dialogue-turn path**

```bash
git add backend/src/main/java/com/personalenglishai/backend/service/writing/WritingExamDialogueService.java backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamDialogueServiceImpl.java backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java backend/src/main/resources/prompts/exam-sheet/exam-dialogue-system.md backend/src/test/java/com/personalenglishai/backend/service/writing/WritingExamDialogueServiceTest.java backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java
git commit -m "feat(prompt): 增加命题对话回合服务"
```

## Task 3: Extract frontend conversation state and helper logic

**Files:**
- Create: `web/src/pages/app/exam-setup/examWorkbenchConversation.ts`
- Create: `web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`
- Modify: `web/src/pages/app/examWorkbenchState.ts`

- [ ] **Step 1: Write the failing helper test for message insertion and preview status**

```ts
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  appendUserTextMessage,
  appendAssetMessage,
  applyDialogueTurnResponse,
  resolvePreviewStatus,
} from './examWorkbenchConversation.js'

test('applyDialogueTurnResponse appends assistant reply and updates preview status', () => {
  const messages = [appendUserTextMessage([], '不要改图片里的原题')]
  const next = applyDialogueTurnResponse(messages, {
    previewStatus: 'draft',
    assistantReplyBlocks: [
      { kind: 'understanding', text: '我会保留原题表述。' },
      { kind: 'follow_up', text: '还需要补充字数范围。' },
    ],
  })

  assert.equal(next.previewStatus, 'draft')
  assert.equal(next.messages.at(-1)?.role, 'assistant')
})
```

- [ ] **Step 2: Run the helper test to verify it fails**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Expected: FAIL because the helper module does not exist yet.

- [ ] **Step 3: Implement the helper module**

The module should own:

- message unions (`user_text`, `user_asset`, `assistant_reply`, `assistant_status`)
- preview status unions (`empty`, `draft`, `ready`, `waiting`)
- turn-application helpers
- start-writing gating helper
- attachment replacement helper for single-image semantics

Keep `examWorkbenchState.ts` only for narrow page-agnostic helpers that remain reusable.

- [ ] **Step 4: Re-run the helper test**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Expected: PASS and confirm the new state transitions before touching UI rendering.

- [ ] **Step 5: Commit the frontend conversation helpers**

```bash
git add web/src/pages/app/exam-setup/examWorkbenchConversation.ts web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts web/src/pages/app/examWorkbenchState.ts
git commit -m "refactor(ui): 抽离命题对话工作台状态"
```

## Task 4: Split the left pane into real conversation components

**Files:**
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchConversationPane.vue`
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchComposer.vue`
- Modify: `web/src/pages/app/ExamSetupPage.vue`

- [ ] **Step 1: Write the failing helper test for asset-message replacement flow**

```ts
test('appendAssetMessage replaces existing image asset when a new image is chosen', () => {
  const first = appendAssetMessage([], { assetType: 'image', label: '图片附件', previewUrl: 'a.png' })
  const second = appendAssetMessage(first, { assetType: 'image', label: '图片附件', previewUrl: 'b.png' })

  assert.equal(second.filter((item) => item.kind === 'asset').length, 1)
  assert.equal(second.find((item) => item.kind === 'asset')?.asset?.previewUrl, 'b.png')
})
```

- [ ] **Step 2: Run the helper test to verify it fails for the replacement case**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Expected: FAIL because image replacement behavior is not implemented yet.

- [ ] **Step 3: Build the left-pane components and wire them into the page**

`ExamWorkbenchConversationPane.vue` should render:

- user text bubbles
- asset message cards
- assistant reply cards with three sections
- assistant status rows

`ExamWorkbenchComposer.vue` should render:

- autosizing input
- `+` attachment menu
- provider picker
- send / cancel button

`ExamSetupPage.vue` should stop directly rendering the current one-shot compose DOM and instead orchestrate props/events between the pane and composer.

- [ ] **Step 4: Re-run the helper test and frontend build**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Run: `npm run build`

Expected: helper test PASS, frontend build PASS with the split components.

- [ ] **Step 5: Commit the left-pane refactor**

```bash
git add web/src/pages/app/exam-setup/ExamWorkbenchConversationPane.vue web/src/pages/app/exam-setup/ExamWorkbenchComposer.vue web/src/pages/app/ExamSetupPage.vue web/src/pages/app/exam-setup/examWorkbenchConversation.ts web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts
git commit -m "feat(ui): 改造命题左侧为对话工作台"
```

## Task 5: Implement the right-pane empty/draft/ready preview flow

**Files:**
- Create: `web/src/pages/app/exam-setup/ExamWorkbenchPreviewPane.vue`
- Modify: `web/src/pages/app/examPromptHelpers.ts`
- Modify: `web/src/pages/app/ExamSetupPage.vue`

- [ ] **Step 1: Write the failing helper test for preview status gating**

```ts
test('resolvePreviewStatus enables start only when prompt sheet is ready', () => {
  assert.equal(resolvePreviewStatus({ hasPromptSheet: false, missingFields: [] }), 'empty')
  assert.equal(resolvePreviewStatus({ hasPromptSheet: true, missingFields: ['待补充字数'] }), 'draft')
  assert.equal(resolvePreviewStatus({ hasPromptSheet: true, missingFields: [] }), 'ready')
})
```

- [ ] **Step 2: Run the helper test to verify it fails**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Expected: FAIL until the final status logic is aligned with the right-pane requirements.

- [ ] **Step 3: Implement the preview pane and wire auto-refresh**

Preview rules:

- `empty`: show prompt to start designing on the left
- `draft`: show status bar + missing-field chips + current prompt-sheet draft
- `ready`: show status bar + full prompt sheet + enabled start-writing gate
- `waiting`: preserve current paper shell but mark the right pane as refreshing

Reuse existing paper rendering helpers from `examPromptHelpers.ts` so the preview content shape remains compatible with the writing session handoff.

- [ ] **Step 4: Re-run the helper test and frontend build**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Run: `npm run build`

Expected: PASS and no regression in prompt-sheet preview rendering.

- [ ] **Step 5: Commit the preview-state implementation**

```bash
git add web/src/pages/app/exam-setup/ExamWorkbenchPreviewPane.vue web/src/pages/app/examPromptHelpers.ts web/src/pages/app/ExamSetupPage.vue web/src/pages/app/exam-setup/examWorkbenchConversation.ts web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts
git commit -m "feat(ui): 增加题单草稿三态预览"
```

## Task 6: Connect end-to-end flow, verify, and document

**Files:**
- Modify: `web/src/api/writing.ts`
- Modify: `docs/writing-ai-exam-prompt-api.md`
- Modify if needed: `web/src/pages/app/ExamSetupPage.vue`

- [ ] **Step 1: Wire the frontend API client to the new backend endpoint**

Add the request/response contract:

```ts
export interface GenerateExamDialogueTurnRequest {
  studyStage?: string | null
  aiProvider?: WritingAiProvider
  messages: ExamWorkbenchMessage[]
  selectedMode: 'free' | 'exam'
}
```

```ts
export function generateExamDialogueTurn(
  req: GenerateExamDialogueTurnRequest,
  options?: { signal?: AbortSignal },
): Promise<GenerateExamDialogueTurnResponse> {
  return http.post('/writing/generate-exam-dialogue-turn', req, { signal: options?.signal }).then((res) => res.data)
}
```

- [ ] **Step 2: Run backend targeted tests**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest,WritingExamDialogueServiceTest,AuditTopicServiceTest" test`

Expected: PASS

- [ ] **Step 3: Run frontend verification**

Run: `node --test --experimental-strip-types web/src/pages/app/exam-setup/examWorkbenchConversation.test.ts`

Run: `npm run build`

Expected: PASS

- [ ] **Step 4: Manual regression**

Check:

- 打开考试模式题目设计页
- 连续发送多轮文本消息，左侧历史正确追加
- 上传图片后作为消息插入，并且可更换当前图片
- 系统每轮回复后，右侧自动刷新
- 信息不全时右侧进入草稿态并显示缺口
- 信息补全后右侧进入完成态
- 模型切换后，识图和审题不再固定走千问
- 进入写作后本次对话历史结束，不污染下一次新会话

- [ ] **Step 5: Commit API/docs/polish**

```bash
git add web/src/api/writing.ts docs/writing-ai-exam-prompt-api.md web/src/pages/app/ExamSetupPage.vue
git commit -m "docs(prompt): 补充命题对话工作台接口说明"
```
