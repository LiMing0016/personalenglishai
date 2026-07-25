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

---

## HTML Validator P1 Remediation (2026-07-14)

### Status

DONE. The final Task 2 P1 Markdown HTML-validator finding from the re-review is resolved.

### TDD Evidence

Added the following test-first schema cases before changing production code:

- Reject HTML comments, declarations and doctypes, processing instructions, and opening, closing, and self-closing tags with case variants and attributes.
- Allow tag-shaped literals in closed backtick and tilde fenced code blocks with language identifiers, and in closed inline code.
- Treat unclosed fenced and inline code delimiters as ordinary Markdown text, so they cannot hide raw HTML from validation.

The focused RED command failed as expected against the prior validator: it accepted comments, `<!...>` declarations, and processing instructions, and it rejected tag literals in closed code. After the implementation change, the same three focused tests passed.

### Implementation

Replaced the single raw-tag regular expression with a small standard-library scanner. It skips only verified closed code fences and inline-code spans, then checks the remaining Markdown text for declarations, processing instructions, comments, and tag forms while retaining valid comparisons and Markdown autolinks. Unmatched backtick runs and fences are deliberately not skipped.

### Verification

```powershell
.\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_prompt_resolver python.ai_orchestrator.tests.test_assistant_output_format_prompt -v
```

Result: 34 tests passed. The re-review baseline command covered 31 tests; this remediation adds three schema test methods.

`git diff --check` is run after this report update before commit.

### Documentation And Merge Assessment

No public architecture, API, configuration, or operating documentation changes are needed: this report records the validator contract and verification evidence. The fix is small and isolated on the existing feature branch, but the branch should remain out of `main` until the wider vocabulary-generation rollout is integrated and verified.

---

## CommonMark Scanner P1 Remediation (2026-07-14)

### Status

DONE. The final Task 2 scanner bypass is resolved with the CommonMark parser instead of extending the hand-written scanner.

### TDD Evidence

Before production changes, added regression coverage for the final-review bypasses and ran the focused cases against the former scanner. The run failed as expected with six rejected-HTML assertions missing:

- tab-indented pseudo-fence followed by an unindented `<script>` block;
- four-space-indented pseudo-fence followed by an unindented `<script>` block;
- an odd backslash count before a prospective inline-code opener.

The tests also use `MarkdownIt("commonmark")` directly to assert the parser token contract. One slash yields `html_inline`; two slashes yield `code_inline`, so the odd form is rejected and the even form remains a legitimate code span. The existing closed code-span/fence, multi-backtick, CRLF, ordinary comparison, autolink, comment, doctype, processing-instruction, and tag coverage remains in the schema suite.

During the full suite, the prior expectation for unclosed fenced blocks was corrected to CommonMark semantics: a fence remains a code block until end of document, while an unclosed inline delimiter does not suppress HTML validation.

### Implementation

- Pinned `markdown-it-py==4.2.0` in `python/ai_orchestrator/requirements.txt`.
- Replaced the custom fence, inline-code, autolink, and tag scanner in `schemas/vocabulary_card.py` with `MarkdownIt("commonmark")`.
- Recursively inspect parsed tokens and `children` for `html_inline` or `html_block`; either token rejects raw HTML for both schema entry points.

The added small dependency is preferable to continuing a custom parser because CommonMark block indentation, tab stops, fence precedence, escape parity, autolinks, and inline code spans are part of the Markdown grammar. The parser keeps this security boundary aligned with the rendering language while removing 190+ lines of bespoke grammar handling.

### Verification

Installed the pinned dependency into the ignored worktree `.venv` (initially bootstrapped its missing `pip` with `ensurepip`), without adding virtual-environment files to Git.

```powershell
.\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents python.ai_orchestrator.tests.test_prompt_resolver python.ai_orchestrator.tests.test_assistant_output_format_prompt -v
```

Result: 38 tests passed.

`pip check` and `git diff --check` are run after this report update before commit.

### Documentation And Merge Assessment

This report is the required documentation update for the validation dependency and contract change; no public API or operating documentation changed. The P1 correction is small, isolated, and suitable for merge on the existing feature branch. The branch itself should remain out of `main` until the wider vocabulary-generation rollout is integrated and verified.
