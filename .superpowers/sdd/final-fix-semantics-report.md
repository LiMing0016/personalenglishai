# Vocabulary Final Semantics Fix Report

## Scope

- Important #2: every revision activation CAS now copies template and theme identity from the activated revision to the card. This covers user edits, `use_ai`, `keep_current`, `merge_fields`, worker activation, and bodyless regeneration using the active revision's frozen theme.
- Important #3: `vocabulary_card.conflict_candidate_revision_uid` explicitly binds a real stale CAS candidate. Partial generation has no candidate, and conflict resolution no longer infers one from status or history order.
- Important #5: `generation_outcome` and `warning` persist on generation jobs and flow through card summaries/details to the frontend. The partial Markdown warning remains reachable after `error_message` is cleared on success.
- Important #6: card search retains term matching and adds MySQL 8 matching for bilingual core meanings plus legacy definitions.

## TDD Evidence

- Backend tests first failed to compile against the missing CAS theme arguments, candidate binding, and generation outcome fields, then passed after implementation.
- Frontend contract/inspector tests initially failed because partial state still depended on `generationError`; they pass with `generationOutcome=partial`, `warning=markdown_unavailable`, and `generationError=null`.
- MySQL search first passed lowercase English, Chinese, legacy, and term cases but failed uppercase `DURABLE`; explicit `utf8mb4_unicode_ci` collation made the case-insensitive contract pass.
- Frozen regenerate template assertion failed with `expected exam, was basic`; regeneration now takes the complete frozen theme, including template key, from the active revision.
- Fresh-schema RED produced one missing-column assertion plus `Unknown column 'conflict_candidate_revision_uid'` and `Unknown column 'generation_outcome'`; after adding the fields to the initial tables, all three MySQL 8 tests passed.

## Migration

Fresh databases receive all three nullable columns directly from `migrate_create_vocabulary_deposition_tables.sql`:

- `vocabulary_card.conflict_candidate_revision_uid`
- `vocabulary_generation_job.generation_outcome`
- `vocabulary_generation_job.warning`

`backend/src/main/resources/db/migrate_add_vocabulary_review_semantics.sql` remains additive for historical databases, using `information_schema` guards and prepared statements. Existing `needs_review` rows are intentionally not backfilled by guessing a candidate.

The fresh-schema MySQL 8 test executes only the README new-database path (initial migration plus required theme migration, without the review-semantics upgrade), verifies all three columns, and executes real card mapper select plus generation-job mapper insert/select SQL.

Deploy the additive migration before application code that selects the new columns.

## Verification

- Focused backend revision/card/finalizer/worker/mapper/schema tests: 88 tests passed.
- Fresh-schema MySQL 8 path: 3 tests passed with no review-semantics migration.
- Final focused MySQL/schema/docs verification: 15 tests, 0 failures, 0 errors, 0 skipped.
- All backend vocabulary tests with search and semantics MySQL integrations enabled: 205 tests, 0 failures, 0 errors, 4 skipped lease-only scenarios.
- Backend full suite on the final implementation: 599 tests, 0 failures, 0 errors, 4 skipped lease-only scenarios.
- Frontend vocabulary tests: 53 passed.
- Chromium E2E `vocabularyDepositionFlow.spec.ts`: 12 passed, including the production-consistent partial fixture.
- `npm run build`: passed (`vue-tsc` and Vite); only the existing large-chunk warning remains.
- VitePress documentation build after the migration-order update: passed.
- `git diff --check`: passed before the implementation commit.

## Commits

- `cdb9c93b fix(vocabulary): 收敛版本冲突与生成结果语义`
- This report is committed separately as `docs(vocabulary)`; its hash is reported in the task summary.
- Fresh-schema follow-up commit: `fix(vocabulary): 补齐新库审核语义字段`.

## Risks And Merge Assessment

- Pre-migration `needs_review` rows have a null explicit candidate and therefore expose no conflict candidate. They must be regenerated or edited to create a precise new candidate; this is safer than binding an unrelated historical revision.
- E2E uses mocked API responses; backend behavior is covered separately by unit, mapper-contract, and MySQL integration tests.
- The mapper query depends on MySQL 8 JSON path support and was verified against the configured MySQL 8 integration instance.
- Fresh databases are complete after the initial and theme migrations. Historical databases must still run the additive review-semantics migration before the updated backend. The change is suitable to merge to `main` with that deployment order.
