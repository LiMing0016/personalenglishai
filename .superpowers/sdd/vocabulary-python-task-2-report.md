# Vocabulary Python Task 2 Report

## Status

Completed Task 2 in `F:\personalenglishai\.worktrees\vocabulary-deposition-core` on branch `codex/vocabulary-deposition-core`.

## Branch Assessment

This task already ran in the dedicated feature worktree and its clean task branch, so no additional branch was created.

## RED

Before production code existed, I created the Task 2 schema and Agent/prompt contract tests and ran:

```powershell
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents -v
```

The command failed as expected because these modules did not yet exist:

- `python.ai_orchestrator.schemas.vocabulary_card`
- `python.ai_orchestrator.agents.vocabulary_card`

## GREEN

Implemented strict cross-service Pydantic schemas, two repository prompt assets, background-job prompt registration, and typed OpenAI Agents SDK factories. The new background prompt keys do not receive the shared handoff preamble, user-context preamble, or conversational Markdown policy.

After installing the existing `python/ai_orchestrator/requirements.txt` into the ignored worktree `.venv`, the Task 2 focused command passed with 11 tests:

```powershell
.\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents -v
```

Final focused verification passed 24 tests:

```powershell
.\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_prompt_resolver python.ai_orchestrator.tests.test_assistant_output_format_prompt -v
git diff --check
```

## Files

- Created `python/ai_orchestrator/schemas/vocabulary_card.py`
- Created `python/ai_orchestrator/agents/vocabulary_card.py`
- Created `python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md`
- Created `python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md`
- Created `python/ai_orchestrator/tests/test_vocabulary_card_schemas.py`
- Created `python/ai_orchestrator/tests/test_vocabulary_card_agents.py`
- Modified `python/ai_orchestrator/prompts/agents.py`

## Documentation Assessment

No documentation file changed. The approved design and implementation plan already define these Task 2 contracts, prompt ownership, and validation boundaries. The later deployment/documentation task remains responsible for public operating documentation.

## Residual Risks

- The default interpreter initially pointed at a Codex Hermes environment without `openai-agents`; the ignored worktree `.venv` now contains the existing declared dependencies. No repository dependency changed.
- The broader `python.ai_orchestrator.tests.test_agent_structure` suite has five failures asserting unrelated existing `router.md` and `polish.md` wording. This task does not modify those assets; the prompt resolver suite itself passes.
- Tasks 3 through 8 still need to wire the schemas and factories into the workflow, internal endpoint, Java client, deployment configuration, and end-to-end acceptance path.

## Merge Assessment

The Task 2 change is small and isolated, but it is one dependency in the planned multi-task vocabulary-generation rollout. Keep it on the feature branch until the remaining workflow, API, Java integration, and release verification tasks are complete.

## Commit

`feat(prompt): 增加单词卡生成结构化 Agent`

---

## Independent Review Remediation (2026-07-14)

### Status

DONE. This follow-up fixes the Task 2 independent-review findings without changing the Task 3 workflow.

### TDD Evidence

Added the missing contract, Markdown boundary, JSON Schema, and Agent resolver tests before changing production code. The focused red run failed against the prior implementation because it accepted missing `audioUrl`, `phonetics`, `senses`, and `meanings`; accepted invalid complete/partial response combinations; emitted schemas without the required wire keys; and accepted raw HTML.

The final focused verification passed 31 tests:

```powershell
.\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_prompt_resolver python.ai_orchestrator.tests.test_assistant_output_format_prompt -v
```

### Contract Remediation

- Made Java core wire fields required in Pydantic while preserving `audioUrl: null` as valid: `audioUrl`, `phonetics`, `senses`, and `meanings` must now be present.
- Added negative required-field and `extra="forbid"` coverage for every nested core model, plus theme and generation metadata models.
- Added JSON Schema assertions for the required core, phonetic, sense, meaning, and structured Markdown output keys.
- Added a response model validator: `complete` requires non-blank validated Markdown with `warning: null`; `partial` requires empty Markdown with `warning: markdown_unavailable`.
- Centralized raw HTML validation for both `VocabularyMarkdownOutput` and `VocabularyCardGenerationResponse`. Tests reject case variants, attributes, closing tags, and self-closing tags while allowing ordinary Markdown comparisons and autolinks.
- Added Agent factory assertions for fixed names and isolated hybrid remote-prompt resolution using both vocabulary prompt IDs. The factory implementation already used the shared resolver, so no factory production change was needed.

### Baseline Failure

`python.ai_orchestrator.tests.test_agent_structure` still fails 5 of 15 tests:

- `test_polish_prompt_defines_flexible_writing_versions`
- `test_prompt_assets_are_loaded_from_prompt_module`
- `test_router_prompt_defines_routing_policy`
- `test_router_prompt_keeps_orchestrator_boundaries`
- `test_router_prompt_uses_chinese_role_labels`

These assertions cover existing `router.md` and `polish.md` wording. This remediation does not change either prompt asset or Task 3 workflow, and the independent review recorded the same five failures at the reviewed baseline.

### Merge Assessment

This is a small, isolated Task 2 contract correction and is ready to commit on the existing feature branch. Keep the branch out of `main` until the remaining vocabulary-generation rollout tasks are integrated and verified together.
