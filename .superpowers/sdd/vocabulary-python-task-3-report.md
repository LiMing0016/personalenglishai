# Vocabulary Python Task 3 Report

## Scope

Implemented the stateless vocabulary-card generation workflow and service:

- `python/ai_orchestrator/workflows/vocabulary_card_generation.py`
- `python/ai_orchestrator/services/vocabulary_card_generation.py`
- `python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py`

The workflow uses the Task 2 typed agents and schemas only. It has no session or database dependency.

## TDD

RED verification was run before the implementation:

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow -v
```

It failed as expected because `vocabulary_card_generation` service did not exist.

## Behavior

- Complete dictionary core calls only the Markdown agent.
- Incomplete core calls fallback then Markdown, with an absolute two-call limit.
- Merge preserves all non-empty trusted core fields and only fills empty collections.
- Invalid fallback raises retryable `CORE_CONTENT_UNAVAILABLE`; it never produces a partial card.
- Partial is limited to an already-valid core whose Markdown call or validation fails.
- Timeout/upstream fallback errors are retryable; version and strategy errors are non-retryable.
- Every model call checks the remaining monotonic timeout budget before starting.
- Trace configuration has workflow name `PEAI Vocabulary Card Generation`, disables sensitive trace data, omits sessions, and sends only safe IDs/counts in metadata.
- The configured model is read from `VOCABULARY_GENERATION_MODEL`; `is_configured()` exposes missing model/API-key configuration.

## Verification

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents -v
& '.\.venv\Scripts\python.exe' -m compileall -q python\ai_orchestrator\workflows\vocabulary_card_generation.py python\ai_orchestrator\services\vocabulary_card_generation.py python\ai_orchestrator\tests\test_vocabulary_card_generation_workflow.py
git diff --check
```

All 38 unit tests passed. Compilation and whitespace validation passed.

## Documentation And Merge Assessment

No authoritative architecture or API documentation update is needed: this task implements the already-approved workflow design and does not expose the HTTP endpoint (Task 4 owns that boundary). The focused change is suitable to merge into `main` after normal integration review.

---

## Independent Review Remediation (2026-07-14)

### Status

DONE. All five independent-review findings are remediated.

### TDD Evidence

Added the review regression tests before changing production code, then ran:

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow -v
```

The RED run failed as expected for blank terms and unsafe IDs, whole-collection merge behavior, in-flight timeout handling, metadata containing theme fields, and unknown runner-error mapping. A follow-up RED test also demonstrated that a budget expiring between the pre-call check and `Runner.run` was incorrectly reported as `GENERATION_INTERNAL_ERROR`.

### Changes

- Each `Runner.run` is wrapped in `asyncio.wait_for` using the remaining monotonic timeout budget. Pre-call and in-flight exhaustion preserve retryable `MODEL_TIMEOUT`; `CancelledError` propagates.
- Core merging now fills only blank trusted scalar fields. It matches phonetics by region, senses by POS, and meanings by an existing definition before falling back to stable indexes; unmatched fallback structures append. Trusted non-blank values, term, and schema version remain unchanged.
- Request/core terms must be non-blank after stripping while retaining their original value and exact equality.
- `requestId` and `traceId`, including response metadata `traceId`, use the opaque safe-ID syntax `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`. Trace metadata now contains only request/trace IDs and the model-call count, never term, theme, or source data.
- Connection and recognized OpenAI upstream failures retain `MODEL_UPSTREAM_UNAVAILABLE`; timeouts retain `MODEL_TIMEOUT`; unknown runner failures map to retryable `GENERATION_INTERNAL_ERROR` with a stable message only.

### Verification

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow -v
& '.\.venv\Scripts\python.exe' -m compileall -q python\ai_orchestrator
& '.\.venv\Scripts\python.exe' -m pip check
git diff --check
```

The final Task 2 and Task 3 regression suite passed 44 tests. Compilation, dependency checks, and diff whitespace validation passed.

### Merge Assessment

The changes are limited to Task 2 contract tightening, Task 3 workflow behavior, focused tests, and this report. The independent-review findings are resolved; the worktree change is suitable to merge into `main` after normal integration review.

---

## Final P1 Merge Remediation (2026-07-14)

### Status

DONE.

### Root Cause

The fallback merge used a trusted collection index after semantic matching failed. That let a fallback phonetic, sense, or meaning fill an unrelated blank trusted structure, and could make fabricated trusted data appear complete.

### TDD Evidence

Added `test_merge_uses_semantic_keys_without_corrupting_or_duplicating_structures` before changing the workflow, then ran:

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow.VocabularyCardGenerationWorkflowTest.test_merge_uses_semantic_keys_without_corrupting_or_duplicating_structures -v
```

The RED run failed because an unused duplicate `uk` fallback phonetic was appended. The prior implementation used the same positional fallback for phonetics, senses, and meanings.

### Changes

- Removed every positional/index fallback from core merging.
- Phonetics now merge only on equal `region`.
- Senses now merge only on equal non-empty normalized POS or a meaning overlap with an equal non-empty definition on the same language side.
- Meanings now merge only on an equal non-empty English or Chinese definition on the same side.
- Unmatched trusted entries stay unchanged; unused fallback structures append only when they do not duplicate an existing semantic key.

### Coverage

The Task 3 merge tests cover reordered `uk`/`us` phonetics, reordered noun/verb senses, multiple senses and meanings, blank trusted structures with unrelated fallback, POS and definition-overlap scalar completion, fallback append completion, and duplicate prevention.

### Verification

```powershell
& '.\.venv\Scripts\python.exe' -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow -v
& '.\.venv\Scripts\python.exe' -m compileall -q python\ai_orchestrator
& '.\.venv\Scripts\python.exe' -m pip check
git diff --check
```

The Task 2/3 suite passed 45 tests. Compilation, dependency checks, and the diff whitespace check completed successfully.

### Documentation And Merge Assessment

No authoritative architecture or API documentation update is needed because this is an internal safety correction to the already-documented generation workflow. The change is scoped to the Task 3 workflow, Task 3 tests, and this report, and is suitable to merge into `main` after normal integration review.
