# Assistant Local Attachment Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep sent assistant message attachments visible after refresh or reopening the same browser.

**Architecture:** Store attachment metadata in the existing assistant local state and store Blob content in IndexedDB by attachment id. During local restore and remote conversation refresh, hydrate matching user-message attachments from IndexedDB and merge them back into the current conversation.

**Tech Stack:** Vue 3, TypeScript, browser IndexedDB, Node built-in test runner, Vite build.

---

### Task 1: Attachment Store

**Files:**
- Create: `web/src/pages/app/assistantAttachmentStore.ts`
- Test: `web/src/pages/app/assistantAttachmentStore.test.ts`

- [ ] Write failing tests for serializing attachment metadata and saving/loading/deleting Blob records through an injected store.
- [ ] Implement a small IndexedDB-backed store with in-memory fallback hooks for tests.
- [ ] Run `node --test src\pages\app\assistantAttachmentStore.test.ts`.

### Task 2: Persisted State Metadata

**Files:**
- Modify: `web/src/pages/app/assistantState.ts`
- Modify: `web/src/pages/app/assistantMock.ts`
- Test: `web/src/pages/app/assistantConversationMerge.test.ts`

- [ ] Persist attachment metadata on done user messages without storing Blob/base64 in localStorage.
- [ ] Hydrate restored conversations from IndexedDB after state creation and after remote detail refresh.
- [ ] Preserve local attachments when remote messages overwrite the active conversation.
- [ ] Delete IndexedDB blobs when a conversation is deleted locally.

### Task 3: Docs and Verification

**Files:**
- Modify: `docs/题目/assistant-inline-upload-trae-tasks.md`
- Modify: `docs/agent/learning-assistant-architecture.md`

- [ ] Document local-only persistence boundaries.
- [ ] Run focused node tests and `npm run build`.
- [ ] Run `git diff --check`.
