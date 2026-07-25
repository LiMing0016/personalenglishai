# Task 9 Report

## Scope

- Added a compact vocabulary theme shelf with the active default theme first, deduplicated active recent themes, and a maximum of three theme choices plus the fixed create action.
- Routed both "管理全部主题" and "新建主题" to `/app/vocabulary/themes` without query-based cross-page selection.
- Moved capture draft selection to `selectedThemeUid`, initialized it from `defaultThemeUid`, preserved valid manual choices across catalog refetches, and fell back to the default when a choice became unavailable.
- Changed the new capture UI path to submit `themeUid` and added themed submit copy plus loading, error, empty, and pending guards. The API compatibility type still accepts legacy `templateKey`.
- Kept the shelf responsive with fixed-height controls, a four-column desktop grid, and a two-column mobile grid.
- Treats a theme query error as blocking only when no cached catalog exists, so a failed background refetch does not hide themes or disable capture.

## TDD

- Red: the focused suite reported 7 failures because the shelf did not exist, capture still used template selection, and the view did not load the theme catalog.
- Green: the focused suite passed after the theme shelf and capture integration were implemented.
- Follow-up red/green: added coverage requiring the fixed create action to remain reachable during loading, error, and empty catalog states, observed the expected failure, then updated the shelf and restored the suite to green.
- Review red/green: added coverage for `isError=true` with and without cached TanStack Query data, observed two expected failures from the raw error binding, then centralized the blocking error calculation in `VocabularyView`.

## Validation

- Passed: `web\npx.cmd tsx --test tests/vocabularyThemeShelf.test.ts tests/vocabularyDepositionWorkspace.test.ts` (12 tests).
- Passed: `web\npm.cmd run build` (`vue-tsc` and Vite; 3258 modules transformed).
- Passed: `git diff --check` (Git emitted existing LF-to-CRLF conversion notices only).
- Not run: browser-based visual or end-to-end interaction testing.
- Existing build warning: several unrelated application chunks exceed Vite's 500 kB warning threshold.

## Documentation And Merge Assessment

- The existing vocabulary theme design already documents the shelf, `themeUid` migration, and `/app/vocabulary/themes`; no architecture or API documentation update was needed beyond this task report.
- The change is limited to the vocabulary capture UI and focused contract tests, adds no dependency or state source, and is suitable for merge review.
- Merge depends on the preceding theme catalog/composable and theme library work from Tasks 7 and 8 already present on this branch.
