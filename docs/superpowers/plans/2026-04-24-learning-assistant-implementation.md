# Learning Assistant Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new `/app/assistant` learning assistant page with a GPT-style left conversation rail and a pure-text chat workspace, while keeping the existing writing-page `AI Chat` untouched.

## Phase 2 Update: OpenAI Agents SDK Integration

- Keep the existing frontend shell, but replace the local mock reply path with a real Python sidecar.
- Add `python/ai_orchestrator/` as a dedicated FastAPI service powered by the OpenAI Agents SDK.
- Use one Router agent plus four specialists:
  - Evaluation Agent
  - Polish Agent
  - Prompt Design Agent
  - Translate and Vocab Agent
- Use SDK `handoffs` for routing and `SQLiteSession` for conversation memory keyed by the frontend conversation id.
- Keep the writing-page Java chat path unchanged; the new assistant page talks to the Python orchestrator directly.
- Support text plus uploaded image/file inputs by mapping them into Responses-compatible `input_image` / `input_file` items before calling `Runner.run(...)`.
- Keep agent construction in `python/ai_orchestrator/agents/` and formal prompt assets in `python/ai_orchestrator/prompts/`, leaving `assistant_service.py` as the service adapter that configures sessions and calls `Runner.run(...)`.
- Keep shared request/response contracts in `python/ai_orchestrator/schemas/` and OpenAI wire-format conversion in `python/ai_orchestrator/adapters/`, so FastAPI entrypoints do not own core contracts or SDK-specific payload mapping.
- Keep SDK session execution in `python/ai_orchestrator/services/agent_session_runner.py`, so `assistant_service.py` coordinates chat behavior without directly owning `SQLiteSession` or `Runner.run(...)`.
- Allow the local Vite assistant page origins (`localhost:3000` and `127.0.0.1:3000`) to call the Python orchestrator during development.

**Architecture:** Add a new top-level business route and nav item, then implement the page as a focused frontend-only workspace. Keep state local to the assistant page through a dedicated page-level state module plus mock reply helpers, so the first version stays independent from the writing stores and future backend orchestration.

**Tech Stack:** Vue 3, TypeScript, Vue Router, existing app layout, lightweight Node-based frontend tests, Vite build.

---

## File Structure

### Files to Modify

- `web/src/layouts/AppLayout.vue`
  - Add the new `学习助手` nav entry next to `写作`.
- `web/src/router/index.ts`
  - Register the new `/app/assistant` route under `AppLayout`.

### Files to Create

- `web/src/pages/app/AssistantPage.vue`
  - Top-level page shell that composes the sidebar, chat view, starter cards, and composer.
- `web/src/pages/app/assistantState.ts`
  - Page-local state factory for conversations, active conversation selection, starter handling, send flow, loading state, and retry state.
- `web/src/pages/app/assistantMock.ts`
  - Mock conversation seed data and mock reply generator for the frontend-only first version.
- `web/src/components/assistant/AssistantSidebar.vue`
  - Sidebar shell with the new-conversation button, search box, grouped conversation sections, and footer slot area.
- `web/src/components/assistant/AssistantConversationList.vue`
  - Focused renderer for grouped conversation items.
- `web/src/components/assistant/AssistantChatView.vue`
  - Chat area renderer for empty state, message list, loading bubble, and inline retry notice.
- `web/src/components/assistant/AssistantStarterCards.vue`
  - Empty-state starter prompts that fill the composer without auto-send.
- `web/src/components/assistant/AssistantComposer.vue`
  - Bottom-fixed textarea + send button for pure-text input only.

### Tests to Create

- `web/tests/assistantRouting.test.ts`
  - Source-level assertions for the new nav item and route registration.
- `web/tests/assistantState.test.ts`
  - Logic tests for conversation creation, starter insertion, send flow, loading replacement, and retry behavior.
- `web/tests/assistantPageChrome.test.ts`
  - Source-level assertions for the empty-state copy and the fact that the writing-page `ChatPanel.vue` remains untouched.

## Architecture Notes

- Do **not** add a new global Pinia store for v1. This page does not yet need cross-route shared state or persistence, and `web/AGENTS.md` explicitly asks us to avoid introducing extra state layers unless existing options are insufficient.
- Keep the page frontend-only by using `assistantMock.ts` instead of a real `src/api/*` endpoint. This honors the current requirement to finish the frontend shape before designing backend orchestration.
- Keep all assistant UI under `web/src/components/assistant/` so it does not leak into the writing module.

## Task 1: Add the New Entry Point

**Files:**
- Modify: `web/src/layouts/AppLayout.vue`
- Modify: `web/src/router/index.ts`
- Test: `web/tests/assistantRouting.test.ts`

- [ ] **Step 1: Write the failing route/nav test**

```ts
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appLayoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')

assert.ok(appLayoutSource.includes("label: '学习助手'"))
assert.ok(appLayoutSource.includes("to: '/app/assistant'"))
assert.ok(routerSource.includes("path: 'assistant'"))
assert.ok(routerSource.includes("name: 'LearningAssistant'"))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
node web/tests/assistantRouting.test.ts
```

Expected: assertion failure because the nav item and route do not exist yet.

- [ ] **Step 3: Add the new nav item in `AppLayout.vue`**

Implementation target:

```ts
const navLinks = [
  { to: '/app/writing', label: '写作' },
  { to: '/app/assistant', label: '学习助手' },
  { to: '/app/vocabulary', label: '单词' },
  { to: '/app/listening', label: '听力' },
  { to: '/app/speaking', label: '口语' },
]
```

- [ ] **Step 4: Add the new assistant route in `router/index.ts`**

Implementation target:

```ts
{
  path: 'assistant',
  name: 'LearningAssistant',
  component: () => import('@/pages/app/AssistantPage.vue'),
},
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
node web/tests/assistantRouting.test.ts
```

Expected: script exits successfully.

- [ ] **Step 6: Commit**

```bash
git add web/src/layouts/AppLayout.vue web/src/router/index.ts web/tests/assistantRouting.test.ts
git commit -m "feat(ui): 新增学习助手导航与路由入口"
```

## Task 2: Build the Page State and Mock Chat Engine

**Files:**
- Create: `web/src/pages/app/assistantState.ts`
- Create: `web/src/pages/app/assistantMock.ts`
- Test: `web/tests/assistantState.test.ts`

- [ ] **Step 1: Write the failing state test**

```ts
import test from 'node:test'
import assert from 'node:assert/strict'
import { createAssistantState } from '../src/pages/app/assistantState'

test('starter fills the draft without sending', () => {
  const state = createAssistantState()
  state.applyStarter('帮我把这句话润色得更高级')
  assert.equal(state.composerText.value, '帮我把这句话润色得更高级')
  assert.equal(state.activeConversation.value.messages.length, 0)
})
```

- [ ] **Step 2: Expand the same test file with send-flow expectations**

Add cases for:

- creating a blank conversation
- appending a user message
- inserting a loading assistant placeholder
- replacing the placeholder with the mock reply
- surfacing a retry state on mock failure

- [ ] **Step 3: Run test to verify it fails**

Run:

```bash
node --test web/tests/assistantState.test.ts
```

Expected: module import failure because `assistantState.ts` does not exist yet.

- [ ] **Step 4: Create `assistantMock.ts` with typed mock data**

Include:

- `AssistantMessage`
- `AssistantConversation`
- starter prompt constants
- `buildMockAssistantReply(input: string): Promise<string>`

Keep the reply generator deterministic enough for tests, for example:

```ts
export async function buildMockAssistantReply(input: string): Promise<string> {
  return `这是学习助手的前端占位回复：\n\n${input}`
}
```

- [ ] **Step 5: Create `assistantState.ts` with a page-local state factory**

Expose:

- `conversations`
- `activeConversationId`
- `activeConversation`
- `composerText`
- `searchText`
- `applyStarter`
- `createConversation`
- `selectConversation`
- `sendMessage`
- `retryLastMessage`

Implementation constraints:

- no localStorage/sessionStorage in v1
- no Pinia store
- no coupling to writing stores

- [ ] **Step 6: Run state tests to verify they pass**

Run:

```bash
node --test web/tests/assistantState.test.ts
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add web/src/pages/app/assistantState.ts web/src/pages/app/assistantMock.ts web/tests/assistantState.test.ts
git commit -m "feat(ui): 增加学习助手页面状态与本地 mock 回复"
```

## Task 3: Build the Assistant Page Shell and Empty State

**Files:**
- Create: `web/src/pages/app/AssistantPage.vue`
- Create: `web/src/components/assistant/AssistantSidebar.vue`
- Create: `web/src/components/assistant/AssistantConversationList.vue`
- Create: `web/src/components/assistant/AssistantChatView.vue`
- Create: `web/src/components/assistant/AssistantStarterCards.vue`
- Test: `web/tests/assistantPageChrome.test.ts`

- [ ] **Step 1: Write the failing page chrome test**

```ts
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantPageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const chatPanelSource = readFileSync(new URL('../src/components/writing/panels/ChatPanel.vue', import.meta.url), 'utf8')

assert.ok(assistantPageSource.includes('今天想练什么？'))
assert.ok(assistantPageSource.includes('学习助手'))
assert.ok(chatPanelSource.includes('AI 对话（本地 mock，后续接 GPT）'))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
node web/tests/assistantPageChrome.test.ts
```

Expected: import/read failure because `AssistantPage.vue` does not exist yet.

- [ ] **Step 3: Implement `AssistantSidebar.vue`**

Include:

- new conversation button
- search input
- grouped conversation rendering via `AssistantConversationList.vue`
- mobile-safe layout but desktop-first structure

- [ ] **Step 4: Implement `AssistantStarterCards.vue`**

Each starter card should emit its text upward via:

```ts
defineEmits<{
  choose: [prompt: string]
}>()
```

- [ ] **Step 5: Implement `AssistantChatView.vue`**

Render two states:

- empty state with heading, subtitle, starter cards
- conversation state with left/right aligned messages

Also reserve inline areas for:

- loading bubble
- retry notice

- [ ] **Step 6: Implement `AssistantPage.vue` to compose the shell**

Wire:

- sidebar selection
- starter click -> `applyStarter`
- chat view props
- shared page-level state from `createAssistantState()`

- [ ] **Step 7: Run the page chrome test to verify it passes**

Run:

```bash
node web/tests/assistantPageChrome.test.ts
```

Expected: script exits successfully.

- [ ] **Step 8: Commit**

```bash
git add web/src/pages/app/AssistantPage.vue web/src/components/assistant/AssistantSidebar.vue web/src/components/assistant/AssistantConversationList.vue web/src/components/assistant/AssistantChatView.vue web/src/components/assistant/AssistantStarterCards.vue web/tests/assistantPageChrome.test.ts
git commit -m "feat(ui): 搭建学习助手页面骨架与空态"
```

## Task 4: Add the Composer and Send Interaction

**Files:**
- Create: `web/src/components/assistant/AssistantComposer.vue`
- Modify: `web/src/pages/app/AssistantPage.vue`
- Modify: `web/src/components/assistant/AssistantChatView.vue`
- Test: `web/tests/assistantState.test.ts`

- [ ] **Step 1: Extend the state test with composer-driven expectations**

Add assertions for:

- enter/send adds the user message
- empty input cannot send
- send button disables during loading when appropriate
- retry reuses the last failed user prompt

- [ ] **Step 2: Run state tests to verify they fail on the missing composer wiring**

Run:

```bash
node --test web/tests/assistantState.test.ts
```

Expected: failing assertions around send-flow behavior not yet exposed through the page shell.

- [ ] **Step 3: Implement `AssistantComposer.vue`**

Component contract:

- `modelValue`
- `loading`
- emits `update:modelValue`
- emits `send`

UI constraints:

- pure text only
- multiline textarea
- submit button
- no attachments or model switcher

- [ ] **Step 4: Wire the composer into `AssistantPage.vue`**

Behavior:

- starter click fills composer only
- send button and enter shortcut call `sendMessage()`
- loading state reflects current send status

- [ ] **Step 5: Update `AssistantChatView.vue` for inline error + retry affordance**

Use props such as:

- `errorMessage`
- `canRetry`
- emit `retry`

- [ ] **Step 6: Re-run the state test suite**

Run:

```bash
node --test web/tests/assistantState.test.ts
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add web/src/components/assistant/AssistantComposer.vue web/src/pages/app/AssistantPage.vue web/src/components/assistant/AssistantChatView.vue web/tests/assistantState.test.ts
git commit -m "feat(ui): 打通学习助手纯文本发送与重试交互"
```

## Task 5: Verify the Whole Frontend Slice

**Files:**
- Modify if needed: any files touched above

- [ ] **Step 1: Run focused frontend source tests**

Run:

```bash
node web/tests/assistantRouting.test.ts
node web/tests/assistantPageChrome.test.ts
node --test web/tests/assistantState.test.ts
```

Expected: all scripts pass.

- [ ] **Step 2: Run the frontend build**

Run:

```bash
npm run build
```

Working directory:

```bash
cd web
```

Expected: `vue-tsc && vite build` completes successfully.

- [ ] **Step 3: Manually smoke test the new page**

Check:

- nav shows `学习助手`
- `/app/assistant` opens correctly
- empty state starter cards fill the composer
- new conversation works
- sending text adds user + assistant messages
- writing page and its existing `AI Chat` still render unchanged

- [ ] **Step 4: Commit any final fixes**

```bash
git add web/src/layouts/AppLayout.vue web/src/router/index.ts web/src/pages/app/AssistantPage.vue web/src/pages/app/assistantState.ts web/src/pages/app/assistantMock.ts web/src/components/assistant web/tests/assistantRouting.test.ts web/tests/assistantPageChrome.test.ts web/tests/assistantState.test.ts
git commit -m "test(ui): 完成学习助手页面前端校验"
```

## Notes for Execution

- Keep the implementation strictly frontend-only in this plan.
- Do not modify `web/src/components/writing/panels/ChatPanel.vue` unless a regression forces a minimal compatibility fix.
- Do not add persistence in the first pass. It is explicitly deferred in the approved design.
- Do not add agent tabs, file uploads, or context injection during implementation, even if the page structure makes them feel easy to add.

## Follow-up Architecture Notes

The learning assistant now has a Python Agents SDK orchestrator behind the assistant page.

- Frontend assistant requests can include `studyStage`, sourced from the existing `stageCache`.
- The assistant API adapter sends this as the multipart form field `study_stage`.
- The Python `/chat` endpoint forwards `study_stage` into `AssistantAgentService.chat`.
- The service builds a user-context prefix before calling the Agents SDK runner:
  - `用户画像上下文`
  - normalized `学段` label, such as `考研`, `雅思`, `四级`
  - guidance to match answer depth, examples, scoring criteria, and practice advice to that stage
- Agent instructions include a shared policy that treats this context as personalization input without exposing the context label to the user.

This keeps stage personalization outside the visible chat transcript while allowing router and specialist agents to tailor their responses.

### Assistant page persistence

The assistant page stores its chat shell state in browser `localStorage` under `peai:assistant:state:v1`.

Persisted fields:
- active conversation id
- conversation list metadata
- completed user and assistant text messages

Non-persisted fields:
- in-flight loading messages
- composer draft text
- failed retry state
- uploaded `File` objects and attachment previews

Attachment files are intentionally not restored after refresh because browser `File` objects cannot be safely reconstructed from `localStorage`. Text history remains available, and future server-side history can replace this local-only storage when the assistant needs cross-device sync.
