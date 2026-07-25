# Vocabulary Conflict Candidate CAS Fix Report

## Scope

- Fixed conflict resolution in `VocabularyCardService` and `VocabularyCardMapper` only.
- Added service, transaction, and mapper SQL contract coverage.
- Did not change schema, public API, deployment configuration, or unrelated modules.

## Root Cause

`resolveConflict` validated the current conflict candidate before creating a resolution revision, but the final card update only compared `active_revision_uid`. If another transaction replaced `conflict_candidate_revision_uid` after that read while leaving the active revision unchanged, the old request could still activate its resolution and unconditionally clear the newer candidate.

## Fix

- Conflict resolution now carries the candidate revision observed during validation into the final mapper call as `expectedCandidate`.
- `updateActiveRevision` supports a candidate-aware overload while retaining the existing nine-argument entry point for revision writer and generation finalizer callers.
- When `expectedCandidate` is present, the update WHERE clause compares both `active_revision_uid` and `conflict_candidate_revision_uid`.
- A successful update still clears `conflict_candidate_revision_uid` atomically with activation. A candidate mismatch updates zero rows, raises `VocabularyRevisionConflictException`, and rolls back the inserted resolution revision.
- Existing `@Transactional` boundaries and row-lock behavior are unchanged.

## TDD Evidence

- The new transaction test first failed with: `Expected VocabularyRevisionConflictException to be thrown, but nothing was thrown.` This reproduced the stale resolver committing after the candidate was replaced.
- The mapper contract first failed because the rendered update SQL did not contain `AND conflict_candidate_revision_uid = ?`.
- After the fix, the race test verifies the old request does not activate, the newer candidate remains, and the resolving transaction records one rollback and no commit.
- Normal conflict resolution tests verify every choice passes `rev_candidate` as the expected candidate while synchronizing the activated revision theme.

## Verification

- Focused service/transaction/mapper/finalizer tests: 74 tests, 0 failures, 0 errors, 0 skipped.
- Backend full suite: 605 tests, 0 failures, 0 errors, 9 skipped.
- `git diff --check`: passed for the implementation diff; only line-ending conversion warnings were reported by Git.

## Documentation And Merge Assessment

No project documentation update is required because the public API, schema, deployment path, and user-visible workflow are unchanged. This is an internal concurrency guarantee.

The scoped CAS fix is suitable to merge to `main`. Parallel schema/documentation work was committed separately as `90b0a1e0` and is not part of this fix commit.
