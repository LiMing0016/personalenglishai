# Assistant Inline Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-version inline image/file upload support to `/app/assistant`.

**Architecture:** Keep uploads ephemeral. The web composer owns local previews, the web API switches to `FormData` only when files exist, Java receives multipart on the existing message path and forwards file bytes to Python, and Python reuses existing OpenAI input item handling.

**Tech Stack:** Vue 3, TypeScript, Axios, Spring Boot MVC/WebClient, FastAPI, OpenAI input items.

---

### Task 1: Frontend Attachment Rules

**Files:**
- Create: `web/src/pages/app/assistantAttachmentRules.ts`
- Modify: `web/src/pages/app/assistantState.ts`

- [ ] Add pure validation helpers for picker/paste/drop sources.
- [ ] Enforce max 5 items, max 10MB each, allowed picker file types, and image-only paste/drop.
- [ ] Surface validation errors through the existing toast path.
- [ ] Verify with `npm run build`.

### Task 2: Composer Inline Input UX

**Files:**
- Modify: `web/src/components/assistant/AssistantComposer.vue`

- [ ] Change `+` menu label to `添加照片和文件`.
- [ ] Add file input `accept` for images, PDF, txt, doc, docx.
- [ ] Add paste handling for image clipboard items.
- [ ] Add drag hover/drop handling for image files.
- [ ] Keep normal text paste behavior intact.
- [ ] Verify with `npm run build`.

### Task 3: Frontend Multipart API

**Files:**
- Modify: `web/src/api/assistant.ts`
- Modify: `web/src/pages/app/assistantState.ts`

- [ ] Keep JSON request for pure text.
- [ ] Use `FormData` when attachments exist.
- [ ] Append `message`, `studyStage`, `assistantMode`, and repeated `files`.
- [ ] Preserve retry behavior by keeping attachments on failure.
- [ ] Verify with `npm run build`.

### Task 4: Java Multipart Endpoint and Forwarding

**Files:**
- Create: `backend/src/test/java/com/personalenglishai/backend/controller/AssistantControllerTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java`

- [ ] Write failing MVC test for multipart message upload.
- [ ] Add same-path `multipart/form-data` controller method.
- [ ] Add service-side attachment validation.
- [ ] Forward files to Python `/chat`.
- [ ] Run targeted Maven test.

### Task 5: Python Regression and Docs

**Files:**
- Modify: `docs/agent/learning-assistant-architecture.md`
- Optional test updates under `python/ai_orchestrator/tests/`

- [ ] Confirm existing Python attachment tests cover image/file input items and session disabling.
- [ ] Update architecture docs with upload entries, limits, and non-persistence boundary.
- [ ] Run targeted Python unittest.

### Task 6: Final Verification

- [ ] Run `npm run build` in `web`.
- [ ] Run targeted backend tests, and full backend tests if practical.
- [ ] Run targeted Python tests.
- [ ] Report any verification that cannot run and why.
