# Task 11 Report

## Scope

- Branch/worktree: `codex/vocabulary-deposition-core` at `F:\personalenglishai\.worktrees\vocabulary-deposition-core`.
- Added Task11 migration, E2E acceptance, architecture, Prompt, navigation, rollback, and verification evidence.
- Did not merge or push. Existing port `5176` was owned by PID `60884`; all Task11 browser runs used worktree code on `127.0.0.1:5177`.

## E2E RED and GREEN

The extended `web/tests/vocabularyDepositionFlow.spec.ts` covers:

1. Create a custom theme, set it as default, observe it selected in the shelf, and capture two words with its `themeUid`.
2. Edit a theme from v1 to v2, verify an existing card remains frozen at v1, and regenerate with `{ themeUid, useLatestThemeVersion: true }` only after confirmation.
3. Simulate Markdown generation failure, keep validated core visible with `needs_review`, and reject visible technical error strings.
4. Open a legacy `basic` card through the compatibility projection and regenerate it into the themed format.
5. Verify 1440x900 and 390x844 layouts have no document/body horizontal overflow and no red toast, page error, console error, or technical exception text.

Initial Chromium RED: 8/13 passed and 5 failed. Four failures were test-state/locator defects introduced by the new core + Markdown DOM. After fixing those, 12/13 passed; the remaining stable failure proved the app exposed `AI output failed structured validation...` instead of a user-facing partial-card message.

Production fix commit `76e5f84a` (`fix(ui): 隐藏单词卡生成技术错误`) maps `needs_review` to `主题内容待完善，可重新生成。` and terminal generation errors to a generic retry message. Focused Chromium passed 2/2 including auth setup, and `vocabularyCardInspector.test.ts` passed 10/10.

The complete vocabulary suite then found one stale legacy assertion for `重新生成模板`. Test-only fix commit `1008cba6` (`test(ui): 更新主题化单词卡验收断言`) updates it to the theme selector/latest-version contract.

Final Chromium result: 13/13 passed, including auth setup and 12 vocabulary scenarios.

## Migration Evidence

- MySQL client/server: local MySQL 8.0 at `127.0.0.1:3306`; credentials were read without printing secrets from the main worktree's ignored `backend/.env`.
- Prefix audit before creation: `PREEXISTING_TASK11_SCHEMAS=0`.
- Disposable schema: `peai_task11_vocabulary_20260713_154010_56044`.
- Applied in order:
  - `migrate_create_vocabulary_deposition_tables.sql`: PASS.
  - `migrate_add_vocabulary_themes_and_markdown_cards.sql`: PASS.
- Verification from `DATABASE()`:
  - target table count: `3`.
  - target column count: `5`.
  - columns: `content_format_version`, `content_markdown`, `core_json`, `theme_uid`, `theme_version`.
- Cleanup: queried the exact schema name before `DROP DATABASE`, got the same case-sensitive value, dropped only that schema, and verified remaining count `0`.
- A first script attempt failed before `CREATE DATABASE` because an empty query result was trimmed as null. A prefix audit proved no schema had been created before the corrected run.

## Verification

- Backend full suite with brief JWT: `584` tests across `156` Surefire suites; `0` failures, `0` errors, `0` skipped.
- Backend docs contract: `4/4` passed.
- All `tests/vocabulary*.test.ts`: `52/52` passed.
- Web production build: PASS (`vue-tsc` + Vite, 3264 transformed modules).
- Chromium E2E: `13/13` passed with `E2E_MOCK_AUTH=1` on port `5177`.
- Docs build: PASS with VitePress `2.0.0-alpha.17`.
- Build warnings are existing non-blocking warnings: web/docs large chunks, docs `env` highlighting fallback, and VueUse PURE annotation cleanup.

## Screenshots

- Desktop 1440x900: `F:\personalenglishai\.worktrees\vocabulary-deposition-core\web\test-results\vocabulary-deposition-desktop.png` (`96,995` bytes).
- Mobile 390x844: `F:\personalenglishai\.worktrees\vocabulary-deposition-core\web\test-results\vocabulary-deposition-mobile.png` (`92,208` bytes).

The files are Playwright artifacts under ignored `test-results`; they are not staged.

## Documentation

- Architecture now covers theme ownership/version freezing, core JSON + Markdown boundaries, legacy template mapping, migration order/evidence, partial cards, deployment, and rollback.
- AI guide documents the four strategy keys, escaped `<theme-purpose>` data delimiter, 20,000-character limit, dictionary precedence, cache/model bounds, logging fields, and failure modes.
- Root index, AI index, and VitePress AI sidebar link the current guide; implementation plans remain outside navigation.

## Residual Risk and Merge Assessment

- E2E uses mocked vocabulary APIs and mock JWT auth; it verifies browser orchestration and rendering, not a live backend login/API round trip.
- Migration proof is from local MySQL 8.0 and does not replace a staging backup/restore rehearsal.
- No real model call was made; Prompt behavior is covered by backend unit/contracts and mocked browser acceptance.
- Changes are isolated, migrations are additive, rollback is non-destructive, and the branch is suitable to merge into `main` after final diff review. No merge or push was performed.
