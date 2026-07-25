# Task 7 Report: Frontend Theme and Card Content API Layer

## Status

- Implementation: COMPLETE
- TDD RED/GREEN: COMPLETE
- Focused frontend contracts: PASS
- Production build: PASS
- Branch: `codex/vocabulary-deposition-core`
- Runtime dependencies added: none

## RED Evidence

Initial contract command:

```powershell
cd web
npx tsx --test tests/vocabularyApiContract.test.ts tests/vocabularyThemeApiContract.test.ts
```

Result: failed as expected with exit code `1`. The four new contract tests failed because `VocabularyCoreContent`, versioned card content fields, theme DTOs/endpoints, and `useVocabularyThemes` did not exist. The four pre-existing API tests remained green.

A second focused RED cycle required distinct public create/update request type names:

```powershell
npx tsx --test tests/vocabularyThemeApiContract.test.ts
```

Result: failed as expected with exit code `1`; the implementation exposed only the shared `UpsertVocabularyThemeRequest` name.

## GREEN Evidence

Focused contract command:

```powershell
cd web
npx tsx --test tests/vocabularyApiContract.test.ts tests/vocabularyThemeApiContract.test.ts
```

Result: PASS, exit code `0`; `8` tests passed with no failures, errors, or skips. This includes the existing runtime assertion that legacy `templateKey` regeneration is sent unchanged.

Production build command:

```powershell
npm.cmd run build
```

Result: PASS, exit code `0`; `vue-tsc` completed and Vite transformed `3245` modules. Vite emitted the repository's existing warning for chunks larger than 500 kB.

## Implementation

- Added typed core JSON, theme catalog, theme snapshot, create/update payload, and all theme management API functions matching `VocabularyController` paths.
- Capture and regenerate requests accept optional `themeUid` while retaining optional legacy `templateKey`; regenerate also accepts `useLatestThemeVersion`.
- Card detail and revision contracts add `theme`, `themeVersion`, `core`, `markdown`, and `contentFormatVersion` while preserving legacy `content`.
- Card update requests accept authoritative `core`/`markdown` and keep legacy `content` compatibility.
- Added `useVocabularyThemes` with the sole server-state key `['vocabulary', 'themes']`. Every theme mutation invalidates it; default, disable, and delete also invalidate cards.
- Theme data is not mirrored into Pinia, `localStorage`, or `sessionStorage`.
- `useVocabularyCards` forwards either legacy or themed regeneration payloads. Successful capture also invalidates themes because the backend updates recent theme usage.

## Files

- `web/src/api/vocabulary.ts`
- `web/src/composables/useVocabularyThemes.ts`
- `web/src/composables/useVocabularyCards.ts`
- `web/tests/vocabularyApiContract.test.ts`
- `web/tests/vocabularyThemeApiContract.test.ts`
- `.superpowers/sdd/task-7-report.md`

## Risks and Merge Assessment

- Contract tests validate source shape and selected runtime behavior; authenticated browser integration remains for the later UI/end-to-end tasks.
- `VocabularyCoreContent.schemaVersion` is intentionally fixed to literal `1`; a future format requires an additive union or versioned type.
- No dependency, deployment, or unrelated state-flow change was introduced.
- Existing design and implementation-plan documents already describe these API and cache contracts, so no additional project documentation update is required.
- The change is suitable as a Task 7 commit on the feature branch. It should remain on `codex/vocabulary-deposition-core` until the remaining tasks and final branch review complete, rather than merging independently to `main`.
