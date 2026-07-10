# Task 10 Report

## RED

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts`
  - Failed with exit code 1 because `src/features/vocabulary/captureTerms.ts` and `src/api/vocabulary.ts` did not exist.
- After review found nullable conflict revision identifiers, the same command failed with exit code 1 because `VocabularyConflictResponse` did not allow `null` for `currentRevisionUid`.

## GREEN

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts`
  - Passed: 3 tests, 0 failures.
- `cd web; npm run build`
  - Passed: `vue-tsc && vite build` exited 0.

## Self Review

- Matched the committed backend controller and DTOs: regenerate/retry return `VocabularyGenerationJobResponse`; revisions return `VocabularyRevisionListResponse`; detail and summary include `candidateRevisionUid` and `conflictStatus`.
- Reused the existing authenticated Axios `http` client. The local envelope helper returns `data`, rejects missing data, and maps error code `409030` to `VocabularyConflictError` with typed conflict data.
- Kept scope to the Task 10 data layer and tests. No project documentation update is required because no architecture, deployment, or external interface behavior changed.

## Changed Files

- `web/src/api/vocabulary.ts`
- `web/src/features/vocabulary/captureTerms.ts`
- `web/tests/vocabularyCaptureTerms.test.ts`
- `web/tests/vocabularyApiContract.test.ts`

## Fix Agent Addendum

### RED

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts`
  - Failed as expected on the baseline: duplicate terms were retained, the one-hundred-term cap was applied before deduplication, and a successful delete envelope without `data` was rejected.
- The `409030` conflict mapping test passed on the baseline, confirming its existing real envelope parsing behavior before retaining it as regression coverage.

### GREEN

- `cd web; npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts`
  - Passed: 4 tests, 0 failures.

### Files

- `web/src/features/vocabulary/captureTerms.ts`
- `web/tests/vocabularyCaptureTerms.test.ts`
- `web/src/api/vocabulary.ts`
- `web/tests/vocabularyApiContract.test.ts`

### Self Review

- `parseCaptureTerms` trims, removes blanks, deduplicates exact terms in first-seen order, then caps the unique result at one hundred.
- Only `deleteVocabularyCard` accepts a success envelope with missing or `null` `data`; other vocabulary calls continue to reject missing data.
- API contract tests call the actual API functions through the shared Axios instance and verify both `409030` payload preservation and void delete success.
- No project documentation update is needed; this is a small client-contract correction and is suitable to merge into `main` after the required build and diff checks.
