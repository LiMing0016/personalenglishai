# Vocabulary Docker Schema Final Fix Report

## Scope

- Synchronize `backend/src/main/resources/db/schema.sql` with the current vocabulary theme, core-content, conflict-candidate, and generation-result schema.
- Gate the real Docker first-initialization path, which executes only `schema.sql`.
- Verify representative theme, revision, card search, card read, and generation-job mapper SQL against a disposable MySQL 8 schema.
- Update README and architecture contracts for Docker initialization, core + legacy search, and summary `generationOutcome`/`warning` fields.
- Do not modify card service or mapper implementation.

## Root Cause

`docker-compose.local.yml` mounts `backend/src/main/resources/db/schema.sql` as `/docker-entrypoint-initdb.d/001_schema.sql`, but the vocabulary section of that script still contained only the original five-table shape. Theme tables and seeds existed only in the additive theme migration, while theme/core/review columns existed across later migrations.

The existing fresh-schema MySQL test did not reproduce Docker initialization because it executed the initial vocabulary migration followed by the theme migration. It therefore allowed `schema.sql` to drift without failing.

## Changes

- Added the three current theme tables, generated uniqueness columns, indexes, foreign keys, and Basic/Exam/Reading seed rows directly to the single vocabulary block in `schema.sql`.
- Added all current theme references, core/Markdown format columns, `conflict_candidate_revision_uid`, `generation_outcome`, and `warning` to their existing table definitions. No duplicate vocabulary table definition was appended.
- Changed `VocabularyFreshSchemaMySqlTest` to execute only `db/schema.sql`, assert the complete table/column/index shape, and execute representative MyBatis SQL for system themes, new-format revision insertion, core and legacy card search, card reads, and generation-job writes/reads.
- Documented that a fresh Docker data volume runs only `schema.sql`; the ordered migration path remains for non-Docker module setup and historical upgrades.
- Documented that keyword search covers both `core_json` and legacy `content_json`, and that list summaries include `generationOutcome` and `warning` alongside task status/error fields.

## TDD Evidence

- Docker-schema RED: 3 tests ran with 1 assertion failure and 2 SQL errors. `vocabulary_theme` was absent, and card/job mapper setup failed with `Unknown column 'theme_uid' in 'field list'`.
- Docker-schema GREEN: after synchronizing the existing vocabulary definitions, all 3 tests passed against disposable MySQL 8 schemas created and dropped by the test.
- Docs RED: 5 tests ran with 2 failing test methods and 4 missing contract assertions covering Docker initialization, core + legacy search, and generation outcome/warning summaries.
- Docs GREEN: all 5 documentation contract tests passed after the README and architecture updates.

## Verification

- Combined MySQL/schema/mapper/docs gate: 37 tests, 0 failures, 0 errors, 0 skipped.
- Included MySQL tests: fresh Docker schema, review-semantics migration, lease migration scenarios, and card search.
- Included static/contract tests: vocabulary deposition schema, vocabulary mapper contracts, and vocabulary documentation contracts.
- VitePress build: passed with the repository's existing syntax-highlighting, Rollup annotation, and large-chunk warnings.
- Disposable MySQL: `mysql:8.0`, no persistent volume, host port `33316`; every integration test used a guarded random schema and removed it in `finally`.

## Documentation And Merge Assessment

README and `docs/architecture/vocabulary-deposition.md` required updates because the fix changes the authoritative fresh-database deployment path and documents current API search/summary behavior. No navigation or sidebar change is required because no document was added or moved.

This is a bounded fresh-install correction with automated MySQL and docs coverage. It is suitable to merge to `main` after the task commit, while preserving separately owned parallel card service/mapper changes outside this commit.
