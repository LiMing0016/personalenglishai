# Task 12 Report

## Scope

- Added the persistent card detail route `vocabulary/card/:cardUid` (`vocabulary-card`) while retaining the legacy `vocabulary/cards/:word` route.
- Added structured card inspector editing, sources/history tabs, generation retry/regenerate, deletion confirmation, and conflict resolution UI.
- Added query mutations for update, delete, regenerate, retry, and conflict resolution. Successful mutations invalidate list, detail, and revisions queries.
- Added Playwright base URL compatibility so this worktree can be verified without reusing another worktree's Vite server.

## TDD

### RED

1. Added `web/tests/vocabularyCardInspector.test.ts` and `web/tests/vocabularyDepositionFlow.spec.ts` before creating the inspector.
2. Ran `npx tsx --test tests/vocabularyCardInspector.test.ts`.
3. Observed the expected `ENOENT` failure for the missing `VocabularyCardInspector.vue`.

### GREEN

1. Implemented `VocabularyCardInspector.vue`, routing, query mutations, and the mocked browser flow.
2. Inspector source test then passed.
3. The final Node suite, production build, and Chromium flow passed.

## Screenshots

- `web/test-results/vocabulary-deposition-desktop.png` (1440x900)
- `web/test-results/vocabulary-deposition-mobile.png` (390x844)

Both Playwright viewport flows assert `document.documentElement.scrollWidth <= window.innerWidth`.

## Verification

Passed:

```powershell
cd web
npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyCardInspector.test.ts
npm run build
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3001'; npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps
```

- Node tests: 10 passed, 0 failed.
- Production build: passed (`vue-tsc && vite build`).
- Playwright: 2 passed, 0 failed (desktop and mobile).

## Self-review

- `term` is read-only; other template fields and `notes` are structured editable controls, not raw JSON.
- Save includes `baseRevisionUid`; merge fields intentionally exclude `term`.
- Regenerate does not expose unsupported template switching.
- Legacy word route behavior remains covered by its existing source-contract test.
- No backend or Task 10 API changes were made.
- Documentation update is limited to this required task report; no public product documentation change is needed.
- This isolated, tested frontend change is suitable for merge to `main` after normal review.

## Review Fix Addendum (2026-07-11)

### Initial failing evidence on `1a3c417`

- Inspector/workspace source tests: 5 passed, 3 failed. Failures covered missing template-driven fields, cancel reset, and persisted `needs_review` initialization.
- Chromium flow: 0 passed, 6 failed. Failures covered all three initial conflict choices, cancel reset, and desktop/mobile nav wrapping.
- Runtime diagnostics then exposed `Cannot access 'mergeableFields' before initialization`; the save flow also attempted to `structuredClone` a Vue reactive proxy before issuing PUT, which produced the pink error toast.

### Fixes

- The parent view now passes the current catalog template definition; Inspector fields and merge choices follow that definition, always include editable notes, exclude `term` from merge payloads, and tolerate fields missing from either revision.
- Persisted conflict metadata opens the resolver immediately, while `VocabularyConflictError` still opens the same resolver for save-time 409 responses.
- Cancel rebuilds the form from `props.card.content`; save snapshots primitive/array values instead of cloning a reactive proxy.
- Deleted card detail/revision queries are removed instead of refetching a deleted resource.
- Mobile nav buttons use `flex: 0 0 auto` and `white-space: nowrap`; the nav owns horizontal scrolling without document overflow.
- Playwright mocks all exercised requests and asserts no `pageerror`, console error, red error toast, or document overflow.

### Final verification

```powershell
cd web
npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyCardInspector.test.ts
# 13 passed, 0 failed

npm run build
# passed: vue-tsc && vite build

$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3001'; npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps
# 6 passed, 0 failed
```

### Screenshot review

- `web/test-results/vocabulary-deposition-desktop.png`: 1440x900, clean navigation and controls, no error toast or overflow.
- `web/test-results/vocabulary-deposition-mobile.png`: 390x844, nav labels remain single-line in a horizontally scrollable nav region; no page overflow, error toast, or control overlap.

No backend or Task 10 API files changed. This focused frontend fix is suitable for merge to `main` after normal review.
