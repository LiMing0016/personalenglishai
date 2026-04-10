# Exam Task Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a required `Task 1 / Task 2` selector for exam mode and reassemble the right-side prompt sheet immediately when the task changes.

**Architecture:** Keep the change local to the existing exam workbench flow. Add a small front-end task-standard mapping helper, feed the selected task into prompt assembly, and update the preview chips so `task_type` becomes the primary exam constraint. Persist the selected task in existing local live state so refresh/resume stays coherent.

**Tech Stack:** Vue 3, TypeScript, existing node:test-based helper tests, Vite build

---

### Task 1: Add task gating helper coverage first

**Files:**
- Modify: `web/src/pages/app/examPromptHelpers.ts`
- Test: `web/tests/examPromptHelpers.test.ts`

- [ ] **Step 1: Write the failing tests**

Add tests that define the expected local exam-task standard mapping:
- `task1` resolves to its display label and standard word/score defaults
- `task2` resolves to its display label and standard word/score defaults
- unknown task returns `null`

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `node --test web/tests/examPromptHelpers.test.ts`
Expected: FAIL because the new task-standard helper does not exist yet.

- [ ] **Step 3: Implement the minimal helper**

Add a focused helper in `examPromptHelpers.ts` that:
- defines the front-end task option type
- exposes standard values for `task1` and `task2`
- returns `null` for unsupported values

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `node --test web/tests/examPromptHelpers.test.ts`
Expected: PASS

### Task 2: Make exam workbench require and persist task selection

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Test: `web/tests/examWorkbenchState.test.ts`

- [ ] **Step 1: Write the failing tests**

Add test coverage for the task-gating state rules:
- exam mode without a selected task should not be considered ready for sheet assembly
- selecting a task should unlock the next state

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `node --test web/tests/examWorkbenchState.test.ts`
Expected: FAIL because no task-gating logic exists yet.

- [ ] **Step 3: Implement the minimal state changes**

Update `ExamSetupPage.vue` to:
- introduce a local `selectedExamTask` state
- render a `Task 1 / Task 2` segmented control in the right-side meta area for exam mode
- persist `selectedExamTask` into existing live state and restore it on load
- make exam assembly require a selected task
- use the selected task to derive standard word range / score defaults

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `node --test web/tests/examWorkbenchState.test.ts`
Expected: PASS

### Task 3: Trigger immediate reassembly when task changes

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Test: `web/tests/examWorkbenchFlow.test.ts`

- [ ] **Step 1: Write the failing test**

Add a flow-level test that captures:
- when a clean exam preview already exists
- switching `task1` to `task2` marks the old preview stale and triggers immediate reassembly intent

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `node --test web/tests/examWorkbenchFlow.test.ts`
Expected: FAIL because task-switch reassembly behavior does not exist yet.

- [ ] **Step 3: Implement the minimal behavior**

Update `ExamSetupPage.vue` so that:
- changing the selected task after a preview exists immediately reruns prompt assembly
- left-side topic / attachments / past prompt reference are preserved
- right-side preview chips show task first, then prompt form, then word count, then score

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `node --test web/tests/examWorkbenchFlow.test.ts`
Expected: PASS

### Task 4: Verify the integrated behavior

**Files:**
- Modify: `web/src/pages/app/ExamSetupPage.vue`
- Verify: `web/tests/examPromptHelpers.test.ts`
- Verify: `web/tests/examWorkbenchState.test.ts`
- Verify: `web/tests/examWorkbenchFlow.test.ts`

- [ ] **Step 1: Run the focused tests together**

Run: `node --test web/tests/examPromptHelpers.test.ts web/tests/examWorkbenchState.test.ts web/tests/examWorkbenchFlow.test.ts`
Expected: PASS

- [ ] **Step 2: Run the web build**

Run: `npm run build`
Expected: exit 0

- [ ] **Step 3: Review for docs impact**

Confirm the change is limited to page interaction and does not require updating broader product docs beyond the existing spec.
