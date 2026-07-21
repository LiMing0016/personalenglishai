# Student Assistant Entry And Responsive Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recreate the selected PEAI option 2 by making the empty-state composer the primary action and turning the narrow-screen learning canvas into an on-demand overlay that does not squeeze the chat.

**Architecture:** Keep `AssistantPage.vue` as the page-level state owner, reuse `AssistantComposer.vue` for both empty and active conversations, and let `AssistantStarterCards.vue` own only temporary goal selection and example prompts. Derive compact learning-canvas behavior from viewport state without adding a store or persistence key, and preserve the existing learning-asset data and save flow.

**Tech Stack:** Vue 3, TypeScript, scoped CSS, existing Node assertion contract tests, Vite.

## Global Constraints

- Preserve the selected visual reference at `output/design/student-assistant-option-2-2026-07-21/reference.png`.
- Do not add routes, state libraries, icon libraries, UI frameworks, storage keys, or backend changes.
- Keep the existing PEAI white, ink-navy, forest-green, and pale-mint design language.
- Empty-state starter interactions fill the composer and wait for the student to send; they do not create a remote conversation.
- At `1024px` and below, the learning canvas must overlay the chat and must not reserve horizontal width.
- Do not change learning-asset save semantics or API error handling in this feature.

---

### Task 1: Empty-State Learning Goals And Prompt Rows

**Files:**
- Modify: `web/src/components/assistant/AssistantStarterCards.vue`
- Modify: `web/src/components/assistant/AssistantChatView.vue`
- Test: `web/tests/assistantStartExperience.test.ts`

**Interfaces:**
- Consumes: existing `chooseStarter(prompt: string)` event from `AssistantChatView.vue`.
- Produces: `selectGoal(goalId: AssistantStarterGoalId)` event, `selectedGoal` prop, and an empty-composer slot positioned between goal choices and prompt rows.

- [ ] **Step 1: Write the failing empty-state contract test**

Create `web/tests/assistantStartExperience.test.ts` with source assertions for the exact title copy, four goal labels, three prompt/outcome rows, `aria-pressed`, and the empty-composer slot:

```ts
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const chat = readFileSync(new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url), 'utf8')
const starters = readFileSync(new URL('../src/components/assistant/AssistantStarterCards.vue', import.meta.url), 'utf8')

assert.ok(page.includes("const emptyTitle = '今天想完成什么？'"))
assert.ok(page.includes("const emptySubtitle = '先选一个学习目标，再把内容发给我。'"))
for (const text of [
  '检查句子', '润色表达', '设计练习', '讲解词句',
  '检查这句话是否自然', '给出原因和改法',
  '帮我升级这段表达', '保留原意，更地道',
  '设计一道写作练习', '包含题目、思路和反馈',
]) assert.ok(starters.includes(text), `missing starter copy: ${text}`)
assert.ok(starters.includes(':aria-pressed="selectedGoal === goal.id"'))
assert.ok(starters.includes('<slot name="composer"'))
assert.ok(chat.includes('#empty-composer'))
console.log('assistant-start-experience-ok')
```

- [ ] **Step 2: Run the test and confirm it fails for the missing redesign**

Run: `cd web && npx tsx tests/assistantStartExperience.test.ts`

Expected: FAIL on the first missing option-2 title or goal contract.

- [ ] **Step 3: Implement the starter goals and prompt rows**

In `AssistantStarterCards.vue`, define exact typed data and render goal buttons, the named composer slot, and lightweight prompt rows:

```ts
export type AssistantStarterGoalId = 'check' | 'polish' | 'practice' | 'explain'

const goals = [
  { id: 'check', label: '检查句子' },
  { id: 'polish', label: '润色表达' },
  { id: 'practice', label: '设计练习' },
  { id: 'explain', label: '讲解词句' },
] as const

const examples = [
  { prompt: '检查这句话是否自然', outcome: '给出原因和改法' },
  { prompt: '帮我升级这段表达', outcome: '保留原意，更地道' },
  { prompt: '设计一道写作练习', outcome: '包含题目、思路和反馈' },
] as const
```

Use native buttons and `aria-pressed`; do not add handcrafted SVGs or icon dependencies. Style the goals as compact pills and examples as one grouped surface with row separators.

- [ ] **Step 4: Pass the empty composer slot through the chat view**

In `AssistantChatView.vue`, replace the old card-only call with:

```vue
<AssistantStarterCards
  :selected-goal="selectedGoal"
  @select-goal="$emit('selectGoal', $event)"
  @choose="$emit('chooseStarter', $event)"
>
  <template #composer>
    <slot name="empty-composer"></slot>
  </template>
</AssistantStarterCards>
```

Add `selectedGoal` to props and `selectGoal` to emits. Update empty-state spacing to match the selected reference at desktop width without changing active-message styles.

- [ ] **Step 5: Run the focused test**

Run: `cd web && npx tsx tests/assistantStartExperience.test.ts`

Expected: `assistant-start-experience-ok`.

- [ ] **Step 6: Commit the isolated task**

Run:

```powershell
git add -- web/src/components/assistant/AssistantStarterCards.vue web/src/components/assistant/AssistantChatView.vue web/tests/assistantStartExperience.test.ts
git commit --only -m "feat(ui): 升级助手首屏学习目标" -- web/src/components/assistant/AssistantStarterCards.vue web/src/components/assistant/AssistantChatView.vue web/tests/assistantStartExperience.test.ts
```

Expected: commit contains only these three files.

---

### Task 2: Empty-State Composer Placement And Focus

**Files:**
- Modify: `web/src/components/assistant/AssistantComposer.vue`
- Modify: `web/src/pages/app/AssistantPage.vue`
- Modify: `web/tests/assistantStartExperience.test.ts`

**Interfaces:**
- Consumes: `AssistantStarterGoalId` from `AssistantStarterCards.vue` and existing `applyStarter(prompt: string)`.
- Produces: exposed `focus(): void` method on `AssistantComposer` and page-local `selectedStarterGoal` state.

- [ ] **Step 1: Extend the focused test with composer behavior contracts**

Add assertions:

```ts
const composer = readFileSync(new URL('../src/components/assistant/AssistantComposer.vue', import.meta.url), 'utf8')
assert.ok(composer.includes('defineExpose({ focus: focusTextarea })'))
assert.ok(page.includes('const selectedStarterGoal'))
assert.ok(page.includes('<template #empty-composer>'))
assert.ok(page.includes('v-if="activeConversation.messages.length === 0"'))
assert.ok(page.includes('v-else class="composer-dock"'))
assert.ok(!page.includes('markdown-theme-control'))
```

- [ ] **Step 2: Run the test and confirm the new assertions fail**

Run: `cd web && npx tsx tests/assistantStartExperience.test.ts`

Expected: FAIL because the composer does not expose focus and the page still renders one fixed composer plus the theme control.

- [ ] **Step 3: Expose composer focus**

In `AssistantComposer.vue`, add:

```ts
function focusTextarea() {
  textareaRef.value?.focus()
}

defineExpose({ focus: focusTextarea })
```

- [ ] **Step 4: Render the composer once per state**

In `AssistantPage.vue`:

- Remove the `MarkText / Milkdown` header control but retain the stored `markdownTheme` value for message rendering.
- Add `selectedStarterGoal`, `emptyComposerRef`, and `handleSelectStarterGoal`.
- Render `AssistantComposer` in the `#empty-composer` slot when the conversation has no messages.
- Render the existing fixed `.composer-dock` only when messages exist.
- Use one shared `AssistantComposer` prop/event block so attachments, modes, paste, and send behavior stay identical.
- Reset `selectedStarterGoal` when `activeConversationId` changes.
- Call `nextTick(() => emptyComposerRef.value?.focus())` after goal or example selection.

Use this ref type:

```ts
const emptyComposerRef = ref<InstanceType<typeof AssistantComposer> | null>(null)
```

- [ ] **Step 5: Update desktop empty-state styling**

Ensure the inline composer is `width: min(680px, 100%)`, uses the existing composer component, and the empty state leaves enough bottom room without the old fixed-dock padding. Active conversations keep the existing fixed dock.

- [ ] **Step 6: Run the focused and existing assistant tests**

Run:

```powershell
cd web
npx tsx tests/assistantStartExperience.test.ts
npx tsx tests/assistantUnifiedSidebar.test.ts
npx tsx tests/assistantAdaptiveSidebar.test.ts
```

Expected: all three scripts exit `0` and print their success markers.

- [ ] **Step 7: Commit the isolated task**

Run:

```powershell
git add -- web/src/components/assistant/AssistantComposer.vue web/src/pages/app/AssistantPage.vue web/tests/assistantStartExperience.test.ts
git commit --only -m "feat(ui): 突出助手首屏输入体验" -- web/src/components/assistant/AssistantComposer.vue web/src/pages/app/AssistantPage.vue web/tests/assistantStartExperience.test.ts
```

---

### Task 3: Compact Learning Results Overlay

**Files:**
- Modify: `web/src/pages/app/assistantSidebarState.ts`
- Modify: `web/src/pages/app/AssistantPage.vue`
- Modify: `web/src/components/assistant/LearningAssetCanvas.vue`
- Modify: `web/tests/assistantAdaptiveSidebar.test.ts`
- Create: `web/tests/assistantCompactLearningCanvas.test.ts`

**Interfaces:**
- Produces: `COMPACT_LEARNING_CANVAS_WIDTH = 1024` and `shouldUseCompactLearningCanvas(viewportWidth: number): boolean`.
- Consumes: existing `learningAssetDrafts`, `learningAssetDraft`, `assistantMode`, and `LearningAssetCanvas` close event.

- [ ] **Step 1: Write failing compact-canvas policy and page contract tests**

Create `assistantCompactLearningCanvas.test.ts` that imports the helper and asserts `1024 => true`, `1025 => false`, then checks the page source for the exact “学习成果” button, `aria-expanded`, `aria-controls`, compact-open state, and zero-width reservation class.

```ts
assert.equal(shouldUseCompactLearningCanvas(1024), true)
assert.equal(shouldUseCompactLearningCanvas(1025), false)
for (const text of [
  '学习成果',
  'compactLearningCanvas',
  'compactLearningCanvasOpen',
  'aria-controls="learning-asset-canvas"',
  ':aria-expanded="learningCanvasVisible"',
  'assistant-page--compact-learning-canvas',
]) assert.ok(page.includes(text), `missing compact learning canvas contract: ${text}`)
```

- [ ] **Step 2: Run the new test and confirm it fails**

Run: `cd web && npx tsx tests/assistantCompactLearningCanvas.test.ts`

Expected: FAIL because the compact helper and button do not exist.

- [ ] **Step 3: Add the pure compact policy**

In `assistantSidebarState.ts` add:

```ts
export const COMPACT_LEARNING_CANVAS_WIDTH = 1024

export function shouldUseCompactLearningCanvas(viewportWidth: number) {
  return viewportWidth <= COMPACT_LEARNING_CANVAS_WIDTH
}
```

- [ ] **Step 4: Separate canvas availability from visibility**

In `AssistantPage.vue`:

```ts
const learningCanvasAvailable = computed(() => assistantMode.value === 'learning' || Boolean(learningAssetDraft.value))
const compactLearningCanvas = computed(() => shouldUseCompactLearningCanvas(viewportWidth.value))
const compactLearningCanvasOpen = ref(false)
const learningCanvasVisible = computed(() => (
  learningCanvasAvailable.value
  && (!compactLearningCanvas.value || compactLearningCanvasOpen.value)
))
```

Use `learningCanvasVisible` for rendering and horizontal width reservation. On compact screens, the existing close event only closes the overlay and retains drafts; on desktop, preserve `closeLearningAssetCanvas()` behavior. When a new asset is explicitly created or opened on compact screens, set `compactLearningCanvasOpen.value = true`.

- [ ] **Step 5: Add the learning-results trigger and overlay semantics**

Add a header button when a compact screen has a non-empty conversation and learning assets are available:

```vue
<button
  v-if="compactLearningCanvas && activeConversation.messages.length > 0 && learningCanvasAvailable"
  type="button"
  class="learning-results-button"
  aria-controls="learning-asset-canvas"
  :aria-expanded="learningCanvasVisible"
  @click="openCompactLearningCanvas"
>
  学习成果
  <span v-if="learningAssetDrafts.length" class="learning-results-count">{{ learningAssetDrafts.length }}</span>
</button>
```

Give the canvas `id="learning-asset-canvas"`; add an overlay scrim under the canvas on compact screens; close on scrim click and Escape; restore focus to the trigger after close.

- [ ] **Step 6: Ensure compact overlay reserves no chat width**

Add `.assistant-page--compact-learning-canvas` so `--learning-canvas-current-width: 0px` even while the overlay is open. Change `LearningAssetCanvas.vue` media query from `960px` to `1024px`, keep it fixed on the right, and cap width with `width: min(100vw, 420px)` so 768px preserves a readable chat behind the overlay.

- [ ] **Step 7: Run focused responsive tests**

Run:

```powershell
cd web
npx tsx tests/assistantCompactLearningCanvas.test.ts
npx tsx tests/assistantAdaptiveSidebar.test.ts
npx tsx tests/assistantUnifiedSidebar.test.ts
```

Expected: all tests exit `0`.

- [ ] **Step 8: Commit the isolated task**

Run:

```powershell
git add -- web/src/pages/app/assistantSidebarState.ts web/src/pages/app/AssistantPage.vue web/src/components/assistant/LearningAssetCanvas.vue web/tests/assistantAdaptiveSidebar.test.ts web/tests/assistantCompactLearningCanvas.test.ts
git commit --only -m "feat(ui): 优化助手窄屏学习成果" -- web/src/pages/app/assistantSidebarState.ts web/src/pages/app/AssistantPage.vue web/src/components/assistant/LearningAssetCanvas.vue web/tests/assistantAdaptiveSidebar.test.ts web/tests/assistantCompactLearningCanvas.test.ts
```

---

### Task 4: Responsive Reading, Build, And Design QA

**Files:**
- Modify: `web/src/components/assistant/AssistantChatView.vue`
- Modify: `web/tests/assistantStartExperience.test.ts`
- Append without replacing existing sections: `design-qa.md`

**Interfaces:**
- Consumes: selected reference image and the completed compact-canvas behavior.
- Produces: verified screenshots at desktop, `1024px`, and `768px`, plus a passing `design-qa.md`.

- [ ] **Step 1: Add failing narrow-reading style contracts**

Assert that `AssistantChatView.vue` includes compact rules for a full-width assistant bubble, safe long-word wrapping, and the existing table scroll wrapper:

```ts
assert.ok(chat.includes('overflow-wrap: anywhere'))
assert.ok(chat.includes('.message-content--markdown :deep(.markdown-table-scroll)'))
assert.ok(chat.includes('max-width: 100%'))
```

- [ ] **Step 2: Run the focused test and confirm the new assertion fails**

Run: `cd web && npx tsx tests/assistantStartExperience.test.ts`

Expected: FAIL on `overflow-wrap: anywhere`.

- [ ] **Step 3: Implement compact reading rules**

At `max-width: 1024px`, reduce chat padding, allow assistant bubbles to use `100%`, set `overflow-wrap: anywhere` on prose/code content, retain horizontal scrolling for `.markdown-table-scroll`, and ensure the composer does not cover the final message.

- [ ] **Step 4: Run all focused tests and production build**

Run:

```powershell
cd web
npx tsx tests/assistantStartExperience.test.ts
npx tsx tests/assistantCompactLearningCanvas.test.ts
npx tsx tests/assistantAdaptiveSidebar.test.ts
npx tsx tests/assistantUnifiedSidebar.test.ts
npm run build
```

Expected: all tests pass; `vue-tsc && vite build` exits `0`. Existing chunk-size warnings are acceptable if no new warning type appears.

- [ ] **Step 5: Verify in the in-app browser**

Capture and inspect:

- desktop empty state at `1405 × 1272`;
- active conversation at `1024 × 900` with learning results closed and open;
- active conversation at `768 × 1024` with learning results closed and open;
- keyboard selection of a goal, composer focus, drawer open, Escape close, and restored focus;
- browser console errors and warnings.

- [ ] **Step 6: Run blocking visual design QA**

Open the selected reference and implementation screenshots at matching states. Append a new `Student Assistant Option 2` section to the existing `design-qa.md` without changing its earlier navigation reports. Fix all P0/P1/P2 mismatches, repeat screenshots, and stop only when the new section contains:

```md
final result: passed
```

- [ ] **Step 7: Commit the verified UI and QA report**

Run:

```powershell
git add -- web/src/components/assistant/AssistantChatView.vue web/tests/assistantStartExperience.test.ts
git commit --only -m "test(ui): 验证助手响应式体验" -- web/src/components/assistant/AssistantChatView.vue web/tests/assistantStartExperience.test.ts
```

Keep the pre-existing untracked `design-qa.md` out of the commit so earlier unrelated audit content is preserved without being absorbed into this feature history.

- [ ] **Step 8: Evaluate merge readiness**

Confirm the feature commits contain only the planned files. Because the repository already has unrelated staged and working-tree changes, do not merge to `main`; report the clean feature commits and preserved unrelated changes.
