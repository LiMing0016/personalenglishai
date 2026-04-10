# AI 考试写作题目工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将考试写作入口改造成“统一输入区 + 题单整理 + 统一预览页”的工作台流程，替代当前多 Tab 表单入口。

**Architecture:** 前端以 `ExamSetupPage.vue` 为入口，收敛为一个统一输入工作台，所有来源最终组装为同一份 `题单` 结构。后端继续保留“解析意图”和“生成题目”两段能力，但以题单为统一输出，预览页和写作页只消费题单，不关心题目来源。

**Tech Stack:** Vue 3, TypeScript, Pinia, Axios, Spring Boot, JUnit 5, MockMvc

---

## File Map

### Frontend

- Modify: `web/src/pages/app/ExamSetupPage.vue`
  - 重写主界面交互，从三 Tab 结构收敛为统一输入区 + `+` 菜单 + 进入预览页。
- Modify: `web/src/pages/app/WritingPage.vue`
  - 继续消费统一题单，保持进入写作后的任务提示构建兼容。
- Modify: `web/src/pages/app/examPromptHelpers.ts`
  - 收敛题单结构、题型映射、题面拼装、附件摘要逻辑。
- Modify: `web/src/api/writing.ts`
  - 前端请求和响应 DTO 向“题单化”对齐。
- Modify: `web/src/stores/writingDraftStore.ts`
  - 保存和恢复统一题单草稿状态。
- Modify: `web/src/components/writing/editorShellStorage.ts`
  - 如需跨刷新恢复题面和附件摘要，统一扩展持久化字段。
- Create: `web/tests/examWorkbenchFlow.test.ts`
  - 纯函数 / 轻状态级测试，验证题单整理与页面状态流。

### Backend

- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
  - 暴露题单整理 / 生成 / 附件识别所需接口。
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java`
  - 从“识别题型”进一步走向“输出题单骨架”。
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java`
  - 统一输出标准题单字段，而不只是单个 prompt 文本。
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/StartWritingSessionRequest.java`
  - 如有必要，补齐题单字段透传。
- Create: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingPromptSheetAssemblerTest.java`
  - 题单拼装单测。
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/writing/AuditTopicServiceTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImplTest.java`

### Docs

- Modify: `docs/superpowers/specs/2026-04-08-ai-exam-workbench-design.md`
  - 只在实现中出现必要边界调整时回写。
- Create or Modify: `docs/writing-ai-exam-prompt-api.md`
  - 同步接口与题单字段。

## Shared Data Shape

统一题单建议收敛为以下前后端一致结构：

- `part: string`
- `questionNo?: string`
- `directions: string`
- `promptText: string`
- `requirements: string[]`
- `wordRange?: string`
- `score?: number`
- `attachmentType: 'none' | 'material' | 'visual'`
- `attachmentTitle?: string`
- `attachmentContent?: string`
- `attachmentImageUrl?: string`
- `visualKind?: 'image' | 'comic' | 'chart' | 'table'`
- `sourceType: 'manual' | 'ai_generated' | 'past_exam' | 'imported_attachment'`

---

### Task 1: Lock The Unified Prompt Sheet Contract

**Files:**
- Modify: `web/src/pages/app/examPromptHelpers.ts`
- Modify: `web/src/api/writing.ts`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptResponse.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingPromptSheetAssemblerTest.java`

- [ ] **Step 1: Write the failing backend contract test**

Create a test that asserts the service can output the new prompt-sheet structure:

```java
@Test
void shouldBuildVisualPromptSheet() {
    GenerateExamPromptResponse response = service.generate(...);
    assertThat(response.getAttachmentType()).isEqualTo("visual");
    assertThat(response.getDirections()).isNotBlank();
    assertThat(response.getRequirements()).isNotEmpty();
}
```

- [ ] **Step 2: Run the targeted backend test to verify it fails**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=WritingPromptSheetAssemblerTest" test
```

Expected: FAIL because the contract or assembler does not exist yet.

- [ ] **Step 3: Write the minimal DTO and helper changes**

Add the shared fields to frontend helper types and backend response DTOs. Keep old fields only if needed for compatibility, but make the new prompt-sheet fields the primary representation.

- [ ] **Step 4: Add a frontend helper test for prompt-sheet normalization**

Test:

```ts
test('normalizePromptSheet maps chart prompt into visual attachment', () => {
  const result = normalizePromptSheet(...)
  expect(result.attachmentType).toBe('visual')
  expect(result.visualKind).toBe('chart')
})
```

- [ ] **Step 5: Run backend and frontend contract tests**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=WritingPromptSheetAssemblerTest" test
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptResponse.java backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingPromptSheetAssemblerTest.java web/src/pages/app/examPromptHelpers.ts web/src/api/writing.ts web/tests/examWorkbenchFlow.test.ts
git commit -m "feat(prompt): 收敛考试写作题单结构"
```

### Task 2: Replace The Three-Tab Entry With A Unified Workbench

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Modify: `web/src/stores/writingDraftStore.ts`
- Modify: `web/src/components/writing/editorShellStorage.ts`
- Test: `web/tests/examWorkbenchFlow.test.ts`

- [ ] **Step 1: Write the failing frontend state-flow test**

Add tests for these transitions:

```ts
test('workbench starts in unified input mode', ...)
test('plus menu only exposes attachment and past exam actions', ...)
test('submitting free text creates a prompt-sheet draft request', ...)
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run:

```bash
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
```

Expected: FAIL because the state flow still assumes tab-based entry.

- [ ] **Step 3: Implement the unified input shell**

In `ExamSetupPage.vue`:

- Remove the three large top-level entry tabs as the primary layout
- Add a single prompt input area
- Add a `+` trigger
- Restrict the menu items to:
  - `添加图片和文件`
  - `历年真题`
- Keep light auxiliary chips only if they help the current draft and do not become a second form

- [ ] **Step 4: Implement draft persistence for the new input state**

Persist:

- draft text
- selected attachment metadata
- selected past-exam metadata
- normalized prompt-sheet draft if already assembled

Do not create a second source of truth; keep one page-level state boundary.

- [ ] **Step 5: Re-run the frontend test and the production build**

Run:

```bash
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
Set-Location F:\personalenglishai\web; npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/app/ExamSetupPage.vue web/src/stores/writingDraftStore.ts web/src/components/writing/editorShellStorage.ts web/tests/examWorkbenchFlow.test.ts
git commit -m "feat(ui): 改造考试写作统一输入工作台"
```

### Task 3: Make Attachment Intake Drive Prompt-Sheet Assembly

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Modify: `web/src/pages/app/examPromptHelpers.ts`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/AuditTopicRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/AuditTopicResponse.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/writing/AuditTopicServiceTest.java`

- [ ] **Step 1: Write the failing service tests for attachment-aware parsing**

Add cases for:

- text only -> normal prompt sheet
- text + material -> material prompt sheet
- text + image hint -> visual prompt sheet

- [ ] **Step 2: Run the targeted parsing tests to verify they fail**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=AuditTopicServiceTest" test
```

Expected: FAIL because current parsing focuses on prompt type only, not prompt-sheet assembly.

- [ ] **Step 3: Extend parsing output to include attachment-aware skeleton fields**

The audit phase should produce:

- normalized `attachmentType`
- possible `visualKind`
- cleaned `directions` draft when possible
- normalized `requirements`

It should remain tolerant of partial input.

- [ ] **Step 4: Wire the frontend to use attachment-aware audit results**

The input workbench should:

- keep text input and attachments together
- request normalized skeleton output
- display a lightweight review state before full preview

- [ ] **Step 5: Re-run backend parsing tests and frontend build**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=AuditTopicServiceTest" test
Set-Location F:\personalenglishai\web; npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/personalenglishai/backend/service/writing/AuditTopicService.java backend/src/main/java/com/personalenglishai/backend/dto/writing/AuditTopicRequest.java backend/src/main/java/com/personalenglishai/backend/dto/writing/AuditTopicResponse.java backend/src/test/java/com/personalenglishai/backend/service/writing/AuditTopicServiceTest.java web/src/pages/app/ExamSetupPage.vue web/src/pages/app/examPromptHelpers.ts
git commit -m "feat(prompt): 支持附件驱动的题单整理"
```

### Task 4: Rework The AI Generation Endpoint Around Prompt Sheets

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/WritingExamPromptService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptRequest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImplTest.java`

- [ ] **Step 1: Write the failing generation tests for the three template families**

Add one test each for:

- normal prompt sheet
- material prompt sheet
- visual prompt sheet

- [ ] **Step 2: Run targeted generation tests to verify they fail**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=WritingExamPromptServiceImplTest,WritingControllerTest" test
```

Expected: FAIL because current generation is not yet aligned with the new prompt-sheet contract.

- [ ] **Step 3: Update the generation service**

The generation service should return a complete prompt sheet:

- `part`
- `directions`
- `promptText`
- `requirements`
- `attachmentType`
- attachment payload fields

For visual prompts, use one template family with `visualKind` differentiating image/comic/chart/table.

- [ ] **Step 4: Preserve compatibility with session creation**

Keep `sourceType=ai_generated` and do not break downstream writing-session creation.

- [ ] **Step 5: Re-run targeted backend tests**

Run:

```bash
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=WritingExamPromptServiceImplTest,WritingControllerTest" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java backend/src/main/java/com/personalenglishai/backend/service/writing/WritingExamPromptService.java backend/src/main/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImpl.java backend/src/main/java/com/personalenglishai/backend/dto/writing/GenerateExamPromptRequest.java backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/writing/impl/WritingExamPromptServiceImplTest.java
git commit -m "feat(api): 生成统一考试写作题单"
```

### Task 5: Build The Unified Preview Page

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Modify: `web/src/pages/app/examPromptHelpers.ts`
- Modify: `web/src/pages/app/WritingPage.vue`
- Test: `web/tests/examWorkbenchFlow.test.ts`

- [ ] **Step 1: Write the failing preview rendering tests**

Add tests for three prompt-sheet template families:

```ts
test('renders normal prompt sheet without attachment block', ...)
test('renders material prompt sheet with material block after directions', ...)
test('renders visual prompt sheet with visual block after directions', ...)
```

- [ ] **Step 2: Run the frontend tests to verify they fail**

Run:

```bash
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
```

Expected: FAIL because current preview rendering still reflects older structures.

- [ ] **Step 3: Implement the preview page**

Render one unified paper-like surface inside the product shell.

Keep the fixed order:

1. `Part`
2. `Directions`
3. prompt text and requirements
4. attachment block

Support exactly three template families:

- `none` -> normal prompt
- `material`
- `visual`

- [ ] **Step 4: Make start-writing consume the prompt sheet**

When the user confirms preview, map the prompt sheet into the existing writing session request.

- [ ] **Step 5: Re-run tests and build**

Run:

```bash
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
Set-Location F:\personalenglishai\web; npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/app/ExamSetupPage.vue web/src/pages/app/examPromptHelpers.ts web/src/pages/app/WritingPage.vue web/tests/examWorkbenchFlow.test.ts
git commit -m "feat(ui): 增加统一考试题单预览页"
```

### Task 6: Final Integration, Regression, And Docs

**Files:**
- Modify: `docs/writing-ai-exam-prompt-api.md`
- Modify: `docs/superpowers/specs/2026-04-08-ai-exam-workbench-design.md` (only if implementation changed a confirmed boundary)
- Modify: `web/tests/examWorkbenchFlow.test.ts`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`

- [ ] **Step 1: Add integration-level test coverage**

Cover:

- text-only input
- attachment-assisted input
- past-exam injection
- preview generation
- start-writing handoff

- [ ] **Step 2: Run the targeted verification suite**

Run:

```bash
node --experimental-strip-types web/tests/examWorkbenchFlow.test.ts
Set-Location F:\personalenglishai\web; npm run build
./mvnw.cmd -q "-Dmaven.repo.local=F:\personalenglishai\.m2repo" "-Dtest=AuditTopicServiceTest,WritingExamPromptServiceImplTest,WritingControllerTest,WritingPromptSheetAssemblerTest" test
```

Expected: PASS for the targeted suite.

- [ ] **Step 3: Manually verify the primary UI paths**

Manual checks:

- direct text input -> preview
- add attachment -> preview
- insert past exam -> preview
- preview -> start writing
- refresh restore before preview

- [ ] **Step 4: Update docs**

Document:

- prompt-sheet contract
- `+` menu semantics
- unified preview rendering families

- [ ] **Step 5: Commit**

```bash
git add docs/writing-ai-exam-prompt-api.md docs/superpowers/specs/2026-04-08-ai-exam-workbench-design.md web/tests/examWorkbenchFlow.test.ts backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java
git commit -m "docs(ui): 补充考试写作题目工作台说明"
```

## Risks To Watch

- `ExamSetupPage.vue` 已经较大，实施过程中要优先拆出纯函数和局部渲染组件，避免继续堆状态。
- 写作页主链路高风险，`WritingPage.vue` 只做 prompt-sheet 消费兼容，不要把复杂状态重新塞进写作页。
- 预览页是统一模板，但视觉附件题内部仍要支持 `image / comic / chart / table` 的局部差异。
- 当前仓库已有 unrelated worktree changes，实施时必须避免回退它们。

## Verification Standard

完成前至少要拿到这些证据：

- `npm run build` 通过
- prompt-sheet 相关前端测试通过
- targeted backend tests 通过
- 手工走通“输入 -> 整理 -> 预览 -> 开始写作”主流程

## Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-08-ai-exam-workbench-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
