# Task 10 Report

## Scope

- Added `VocabularyCoreSummary` to render the typed v1 term, every regional phonetic, and senses grouped by part of speech with English and Chinese definitions. Empty collections use neutral states and the component does not read legacy content.
- Added `VocabularyMarkdownEditor` as a plain textarea editor. It preserves the input value without trimming or newline normalization, enforces the 20,000-character HTML limit, reports the character count and invalid state, and does not render HTML or add a Markdown dependency.
- Added one pure `projectLegacyVocabularyCore` adapter. The inspector reads `card.core`, then the legacy projection, then a minimal v1 core, and always restores the card's normalized term identity.
- Reworked save to submit `baseRevisionUid`, identity-safe `core`, exact `markdown`, and `changeSummary`, with pending, success, conflict, length-error, and failure states.
- Split conflict presentation by revision format. V1 compares Markdown as a whole and merges only whole `core`/`markdown` values; legacy revisions retain field-level merge behavior.
- Extended stale-revision 409 responses with nullable current/candidate content format versions. The inspector treats the response's current version as authoritative, falls back to cached current-revision metadata only when the additive field is absent, and still requires the current compatibility content to have the real v1 core shape. Candidate metadata never selects the protocol.
- Replaced template regeneration controls with active theme selection backed by the existing `['vocabulary', 'themes']` TanStack Query cache. Blocking errors require no cached catalog, retry refetches the query, and loading/error/empty states disable regeneration.
- Added the required latest-theme confirmation when the selected theme UID or catalog version differs from the frozen card revision, and sends `themeUid` with `useLatestThemeVersion: true`.
- Kept fixed editor dimensions, narrow-screen single-column controls, wrapping text, and bounded conflict previews to avoid horizontal overflow.

## TDD

- RED: `cd web; npx.cmd tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts`
  - Failed with 8 contract failures because the core summary, Markdown editor, legacy projection, v1 save/conflict behavior, and themed regeneration did not exist.
  - After adding a runtime projection assertion, failed at module load because `projectLegacyVocabularyCore` was not exported.
- GREEN: the same focused command passed 10 tests with 0 failures after the minimal implementation.
- Regression: the Task 7-9 API, theme cache, theme shelf, workspace, inspector, and core summary suite passed 30 tests with 0 failures.

## Review Findings Fix

- Draft identity:
  - RED: added tests proving a same-card refetch must not reset editing or Markdown, while a real `cardUid` or `activeRevisionUid` change must reset the draft. The tests failed because the inspector deep-watched the complete card and did not synchronize the saved server card.
  - GREEN: draft reset now depends only on `{ cardUid, activeRevisionUid }`. Same-card polling/refocus data preserves the draft, and a successful save synchronizes Markdown and the new active revision returned by the server.
- Theme fallback:
  - RED: added a pure selection-policy test covering preferred theme, default theme, first active theme, and an empty catalog. It failed because selection fallback only ran when the catalog changed.
  - GREEN: switching cards immediately resolves the active theme from the cached catalog, while a valid manual selection survives same-card refetches.
- Conflict format authority:
  - RED: added cases for current legacy content with a v1 candidate and for legacy content carrying a misleading format marker. They failed because candidate metadata and shape could force the v1 conflict path.
  - GREEN: v1 conflict detection now follows backend semantics using only the current revision's format marker and real compatibility content shape. Candidate and historical metadata cannot select the v1 payload.
- Canonical legacy projection:
  - RED: added boundary and shape tests for 30 total meanings, 2,000-character scalar limits, `unknown` part of speech, bilingual delimiter splitting, empty definition arrays, and ignored object definitions. The tests exposed unbounded values, heuristic language detection, and guessed extension fields.
  - GREEN: the pure projection helper now mirrors backend canonical handling for the supported `phonetic`, `partOfSpeech`, and string-array `definitions` fields, producing a saveable v1 core without guessing unknown fields.
- Stale 409 format authority:
  - RED: backend tests failed to compile against the missing conflict version accessors/constructor; frontend API and inspector tests failed because the fields were absent and a marker plus a term-less lookalike shape was classified as v1.
  - GREEN: `conflictFor` now copies each revision's nullable `contentFormatVersion`; controller JSON covers current v1 `1` plus legacy candidate `null`; the web type is additive and `unwrap` is unchanged.
  - Browser regression: a stale PUT returns current v1 metadata for a revision UID intentionally absent from the revisions cache. The dialog uses the v1 path and resolves with `mergeFields.core` plus `mergeFields.markdown`; a mislabeled legacy shape remains on the legacy path.

## Validation

- Passed: `backend\.\mvnw.cmd -q "-Dtest=VocabularyControllerTest,VocabularyCardServiceTest" test`.
- Passed: `backend\.\mvnw.cmd -q test` (581 tests, 0 failures/errors/skips).
- Passed: `web\npx.cmd tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts tests/vocabularyApiContract.test.ts` (21 tests).
- Passed: `web\npx.cmd playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps -g "stale v1 conflict"` (1 test).
- Passed: `web\npm.cmd run build` (`vue-tsc` and Vite).
- Existing build warning: unrelated application chunks exceed Vite's 500 kB warning threshold.

## Documentation And Merge Assessment

- The existing vocabulary theme/Markdown design and implementation plan already document the core schema, Markdown limit, theme version confirmation, compatibility order, and conflict behavior. The additive 409 metadata does not change architecture, state flow, caching, routes, or deployment, so no architecture document update is required beyond this task report.
- The conflict API type changed additively; all old response fields and `unwrap` behavior remain intact. No dependency, route, store, persistence key, or deployment behavior changed.
- The implementation is suitable for merge review from `codex/vocabulary-deposition-core`; merge depends on the Task 7-9 theme/API work already present on the branch.

## Residual Risk

- The mocked Chromium regression exercises the actual stale conflict dialog and resolve request. It does not replace an authenticated integration run against a live backend or broad visual screenshot regression.
- Legacy projection intentionally handles the established `phonetic`, `partOfSpeech`, and `definitions` shapes. Unknown historical extension fields remain outside the v1 core instead of being guessed in the component.

## Final Review Fix

- A v1 conflict compatibility object is now projected to the exact core keys (`schemaVersion`, `term`, `phonetics`, `senses`), so compatibility-only `markdown` cannot leak into strict core validation.
- Conflict format-version fields remain additive and are optional in the TypeScript DTO, preserving compatibility with older 409 fixtures and servers.
- Fresh validation: 15 focused API/inspector tests passed and the production web build passed.
