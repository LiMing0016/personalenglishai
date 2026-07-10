# Task 11 Report

## RED

- Added `web/tests/vocabularyDepositionWorkspace.test.ts` before production code.
- Ran `cd web; npx tsx --test tests/vocabularyDepositionWorkspace.test.ts`.
- Confirmed the expected failure: `VocabularyCapturePanel.vue` was absent (`ENOENT`).

## GREEN

- Added TanStack Query orchestration for template, card-list, selected-detail, capture invalidation, and generation-only polling.
- Added bulk capture and persisted card-list components.
- Replaced the collection view's mock card workspace with the Task 10 vocabulary card API workflow and `cardUid` selection.
- Removed the obsolete mock collection state and its unused favorite-list request path.

## Verification

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts` passed: 8/8 tests.
- `cd web; npm run build` passed (`vue-tsc` and Vite production build).
- `git diff --check` passed.

## Self Review

- Capture retries retain their `clientRequestId`; successful submissions issue a new ID.
- The list supports status/source/search/page filters and loading, empty, and error states. Polling is enabled only for `generating` cards.
- Task 10 API and backend code were not changed. No project documentation update is required; this task report records the implementation evidence.
- The change is isolated on `codex/vocabulary-deposition-core` and is suitable to merge into `main` after normal review.

## Changed Files

- `web/src/composables/useVocabularyCards.ts`
- `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- `web/src/components/vocabulary/VocabularyCardList.vue`
- `web/src/views/VocabularyView.vue`
- `web/tests/vocabularyDepositionWorkspace.test.ts`

## Follow-up: Legacy Word-Card Route Compatibility

### RED

- Added a source-contract regression test for the retained `VocabularyWordCard` route.
- Ran `cd web; npx tsx --test tests/vocabularyDepositionWorkspace.test.ts` on baseline `cd0f01f`.
- Confirmed the new test failed because `VocabularyView.vue` did not inspect `route.name` or `route.params.word` for the legacy route.

### GREEN

- Kept `VocabularyWordCard` as a compatibility route that opens the `collection` workspace.
- Normalized `route.params.word` into the persisted-card list `keyword` filter and cleared `selectedCardUid`; the legacy word is never used as a card UID.
- Expanded the route watcher to react to route name, legacy word parameter, and tab query changes.

### Verification

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts` passed: 9/9 tests.
- `cd web; npm run build` passed. Vite reported only the repository's existing large-chunk warning.
- `git diff --check` passed.

### Scope and Merge Assessment

- Changed only the allowed view, workspace test, and this Task 11 report. No API, composable, or component changes were made.
- No project documentation update is required: this is a backwards-compatible client route interpretation, not an architecture or external API change.
- The isolated fix is suitable to merge into `main` after normal review.
