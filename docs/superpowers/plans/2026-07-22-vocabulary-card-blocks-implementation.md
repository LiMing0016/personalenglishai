# Vocabulary Card Blocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace newly generated vocabulary-card Markdown with editable, typed Card Blocks while preserving historical cards and stable lexical facts.

**Architecture:** Python produces strict Lexical Core Schema 2 and Card Blocks Schema 1. Java validates and persists both JSON documents in each revision, projects historical Markdown as a read-only legacy block, and exposes backward-compatible API fields. Vue renders and edits typed blocks while retaining the existing Markdown renderer for historical revisions.

**Tech Stack:** Python 3, Pydantic, OpenAI Agents SDK, Spring Boot 3, Java 17, MyBatis, MySQL JSON, Vue 3, TypeScript, lucide-vue-next.

## Global Constraints

- Work only on `codex/vocabulary-deposition-core` in the existing worktree.
- Do not overwrite the current uncommitted inspector/header work; integrate with it.
- No new runtime dependency or framework.
- New cards use Lexical Core Schema 2 and Card Blocks Schema 1.
- Historical Core Schema 1 and `contentMarkdown` remain readable without background rewrites.
- User-edited or locked blocks are never silently replaced.
- All production-code behavior changes follow red-green-refactor.

---

### Task 1: Python structured generation contract

**Files:**
- Modify: `python/ai_orchestrator/schemas/vocabulary_card.py`
- Modify: `python/ai_orchestrator/agents/vocabulary_card.py`
- Modify: `python/ai_orchestrator/workflows/vocabulary_card_generation.py`
- Create: `python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_blocks.md`
- Modify: `python/ai_orchestrator/tests/test_vocabulary_card_schemas.py`
- Modify: `python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py`
- Modify: `python/ai_orchestrator/tests/test_vocabulary_card_agents.py`

**Interfaces:**
- Produces `VocabularyCardGenerationResponse` containing `core`, `cardBlocks`, `cardBlocksSchemaVersion`, `outcome`, `warning`, and `generation`.
- `core.schemaVersion` is `2`; every sense and meaning has a stable opaque ID.
- Card block types are `exampleList`, `collocationList`, `usageBoundary`, `contrastTable`, `memoryTip`, and `note`.

- [x] **Step 1: Write schema tests that require Core IDs and reject invalid blocks**

Add tests that validate this shape and reject unknown block types, duplicate IDs, dangling `meaningRefs`, raw HTML in Markdown notes, and structured content with the wrong fields:

```python
{
    "schemaVersion": 1,
    "blocks": [{
        "id": "block_examples_01",
        "type": "exampleList",
        "title": "常用例句",
        "meaningRefs": ["meaning_1"],
        "format": "structured",
        "content": {"items": [{"sentence": "An anthropic explanation...", "translation": "..."}]},
        "source": "ai",
        "sourceRef": None,
        "sortOrder": 10,
        "userEdited": False,
        "locked": False,
    }],
}
```

- [x] **Step 2: Run the schema tests and confirm RED**

Run: `python -m unittest ai_orchestrator.tests.test_vocabulary_card_schemas -v`

Expected: failures because Card Blocks models and Core IDs do not exist.

- [x] **Step 3: Implement strict Pydantic models**

Use discriminated block models with `extra="forbid"`, validate unique IDs and references against Core meaning IDs, and keep Markdown validation only inside `note` blocks.

- [x] **Step 4: Run schema tests and confirm GREEN**

Run: `python -m unittest ai_orchestrator.tests.test_vocabulary_card_schemas -v`

- [x] **Step 5: Write workflow and agent tests for two structured calls**

Cover successful Core + Blocks generation, Blocks failure returning `warning="card_blocks_unavailable"`, theme data reaching only the Blocks input, and AI-provided audio URLs being rejected or replaced by trusted dictionary audio.

- [x] **Step 6: Run workflow tests and confirm RED**

Run: `python -m unittest ai_orchestrator.tests.test_vocabulary_card_generation_workflow ai_orchestrator.tests.test_vocabulary_card_agents -v`

- [x] **Step 7: Replace the Markdown agent with the Card Blocks agent**

The Core agent receives term, dictionary facts, and source context. The Blocks agent receives final Core, source context, and theme. Both use strict output types. The response carries no newly generated `contentMarkdown`.

- [x] **Step 8: Run Python vocabulary-card tests and confirm GREEN**

Run: `python -m unittest discover -s ai_orchestrator/tests -p 'test_vocabulary_card*.py' -v`

---

### Task 2: Java validation, response contract, and revision persistence

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardBlocksCodec.java`
- Create: `backend/src/main/resources/db/migrate_add_vocabulary_card_blocks.sql`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodec.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`
- Modify: `backend/src/main/resources/mapper/VocabularyRevisionMapper.xml`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodecTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardBlocksCodecTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProviderTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java`

**Interfaces:**
- Revision columns: `card_blocks_json JSON NULL`, `card_blocks_schema_version INT NULL`.
- New generated revisions store Core 2 + Blocks 1; `content_markdown` remains null.
- Compatibility JSON may remain for old consumers but is not the source of truth.

- [x] **Step 1: Add failing codec and provider tests**

Tests must reject duplicate block IDs, invalid meaning references, unsupported block types, invalid note Markdown, mismatched schema versions, and malformed Python response fields.

- [x] **Step 2: Run focused backend tests and confirm RED**

Run: `mvn -Dtest=VocabularyCoreContentCodecTest,VocabularyCardBlocksCodecTest,PythonVocabularyGenerationProviderTest,VocabularyGenerationWorkerTest test`

- [x] **Step 3: Implement Core 2 and Card Blocks validation**

Keep Core 1 read compatibility. Generate deterministic IDs for dictionary/legacy Core projections, validate Core 2 strictly, and validate every Blocks reference against the persisted Core.

- [x] **Step 4: Add the idempotent SQL migration and MyBatis mappings**

Use `information_schema.columns` guards matching the existing vocabulary migrations. Add entity getters/setters and mapper select/insert columns.

- [x] **Step 5: Adapt Python request/response and worker persistence**

Use contract version 2, Core Schema 2, Card Blocks Schema 1, and `card_blocks_unavailable` partial warning. Validate before writing a revision.

- [x] **Step 6: Run focused backend tests and confirm GREEN**

Run: `mvn -Dtest=VocabularyCoreContentCodecTest,VocabularyCardBlocksCodecTest,PythonVocabularyGenerationProviderTest,VocabularyGenerationWorkerTest test`

---

### Task 3: Backward-compatible card API and editing

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardDetailResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyRevisionResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/UpdateVocabularyCardRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`

**Interfaces:**
- Detail/revision responses add `cardBlocks` and `cardBlocksSchemaVersion`; existing `markdown` remains for legacy cards.
- Update requests accept `cardBlocks`; new block edits create a user revision and preserve Core.
- Legacy Markdown is projected to one read-only API block with `type="legacyMarkdown"`, `source="legacy"` without database rewrite.

- [x] **Step 1: Add failing service/controller tests**

Cover generated block response, legacy Markdown projection, user-authored block update revision, and stale base revision conflict. Regeneration protection for locked blocks remains in the generation service/worker tests.

- [x] **Step 2: Run API tests and confirm RED**

Run: `mvn -Dtest=VocabularyCardServiceTest,VocabularyControllerTest test`

- [x] **Step 3: Implement API projections and updates**

Prefer stored Blocks. When absent and Markdown exists, return a synthetic legacy block while retaining `markdown` for old clients. Validate update Blocks against the selected Core before appending the revision.

- [x] **Step 4: Run API tests and confirm GREEN**

Run: `mvn -Dtest=VocabularyCardServiceTest,VocabularyControllerTest test`

---

### Task 4: Vue Card Blocks reading and editing experience

**Files:**
- Modify: `web/src/api/vocabulary.ts`
- Create: `web/src/components/vocabulary/VocabularyCardBlocks.vue`
- Create: `web/src/components/vocabulary/VocabularyCardBlocksEditor.vue`
- Modify: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Create: `web/tests/vocabularyCardBlocks.test.ts`
- Modify: `web/tests/vocabularyCardInspector.test.ts`
- Modify: `web/tests/vocabularyApiContract.test.ts`

**Interfaces:**
- TypeScript discriminated union mirrors Card Blocks Schema 1.
- Reading view renders each block type without repeating Lexical Core.
- Editing view supports block editing, deletion, insertion of Markdown note blocks, and ordering. Saving sends the complete ordered Blocks document with `baseRevisionUid`.

- [x] **Step 1: Add failing API and component tests**

Use real typed fixtures for all six Block types plus `legacyMarkdown`. Assert that Core fields do not appear inside block content and that user notes serialize as Markdown blocks.

- [x] **Step 2: Run frontend tests and confirm RED**

Run: `npx tsx --test tests/vocabularyCardBlocks.test.ts tests/vocabularyCardInspector.test.ts tests/vocabularyApiContract.test.ts`

- [x] **Step 3: Implement typed renderer and editor**

Reuse existing typography, spacing, buttons, Markdown renderer, and lucide icons. Keep block actions quiet in reading mode. Do not add a new state store; the inspector keeps the current revision draft as its single edit source.

- [x] **Step 4: Run frontend tests and build**

Run: `npx tsx --test tests/vocabularyCardBlocks.test.ts tests/vocabularyCardInspector.test.ts tests/vocabularyApiContract.test.ts`

Run: `npm run build`

---

### Task 5: Cross-layer verification and documentation

**Files:**
- Modify: `docs/architecture/vocabulary-deposition.md`
- Modify: `docs/superpowers/specs/2026-07-22-vocabulary-ai-core-generation-design.md` only if implementation reveals a contract correction.

- [x] **Step 1: Update the architecture data flow and migration instructions**

Document Core 2, Blocks 1, legacy projection, database columns, Python/Java contract versions, and local migration command/path.

- [x] **Step 2: Run focused cross-layer suites**

Run Python vocabulary-card tests, focused Maven vocabulary tests, frontend Node tests, and `npm run build`.

- [x] **Step 3: Inspect the live desktop and mobile states**

Use the existing user-selected in-app browser at desktop and mobile widths. Verify the generated card, legacy card, edit mode, note insertion, block ordering, and no overlap/cropping.

Acceptance result: the idempotent migration was applied to the approved local test database. A real `gpt-5.4-mini` regeneration upgraded `anthropic` to Core 2 and six Card Blocks 1 entries. Reading, editing, note insertion, block ordering, legacy fallback, and 390px mobile layout were verified in the user-selected in-app browser without saving test-only edits.

- [x] **Step 4: Review the complete diff and merge readiness**

Run `git diff --check`, confirm no unrelated file was reverted, and list any unrun external-model or MySQL integration checks.

Review result: `git diff --check` is clean and pre-existing UI/spec edits remain untouched. The migration and real-model desktop/mobile acceptance are complete; the scoped implementation is ready for final review and merge after the intended files are committed.
