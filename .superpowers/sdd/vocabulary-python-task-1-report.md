# Vocabulary Python Task 1 Report

## Scope

Completed Task 1 database migration, revision entity persistence field, MyBatis revision mapping, and the requested database/mapper tests.

## Branch Assessment

- Worktree: `F:\personalenglishai\.worktrees\vocabulary-deposition-core`
- Branch: `codex/vocabulary-deposition-core`
- A dedicated feature branch already existed and the worktree was clean before work started; no new branch was needed.

## TDD Record

### RED

Added failing tests before production changes:

- Fresh-schema and migration contract for `generation_metadata_json`.
- Revision result-map, revision column list, and `insertRevision` contract.
- Optional MySQL 8 migration test that uses a randomly named schema prefixed with `peai_vocab_generation_metadata_`, applies the migration twice, and asserts the JSON column exists exactly once.

Command:

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=VocabularyDepositionSchemaTest,VocabularyMapperContractTest,VocabularyGenerationMetadataMigrationMySqlTest" test
```

Result: RED, exit code `1`.

- `VocabularyDepositionSchemaTest` failed with `NoSuchFileException` for `migrate_add_vocabulary_generation_metadata.sql`.
- `VocabularyMapperContractTest` failed because the revision result map, column list, and insert SQL did not include `generation_metadata_json`.
- The MySQL test skipped because `VOCABULARY_MYSQL_INTEGRATION_URL` was not configured.

### GREEN

Implemented the smallest additive persistence change:

- Idempotent migration using `information_schema.columns`, `DATABASE()`, and prepared SQL.
- Fresh schema `generation_metadata_json JSON NULL` column.
- Nullable `VocabularyCardRevision.generationMetadataJson` Java field.
- Revision MyBatis result map, column list, and insert binding.

Re-ran the same focused command.

Result: GREEN, exit code `0`.

- Focused suite completed successfully.
- `VocabularyGenerationMetadataMigrationMySqlTest` remained intentionally skipped because no disposable MySQL URL was supplied. The test never targets a business schema; it creates and drops only its randomly named, prefix-guarded temporary schema.
- `git diff --check` and `git diff --cached --check` both completed successfully.

## Modified Files

- `backend/src/main/resources/db/migrate_add_vocabulary_generation_metadata.sql`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`
- `backend/src/main/resources/mapper/VocabularyRevisionMapper.xml`
- `backend/src/test/java/com/personalenglishai/backend/db/VocabularyDepositionSchemaTest.java`
- `backend/src/test/java/com/personalenglishai/backend/db/VocabularyMapperContractTest.java`
- `backend/src/test/java/com/personalenglishai/backend/db/VocabularyGenerationMetadataMigrationMySqlTest.java`

## Review Notes

- The Task 1 brief lists `VocabularyCardMapper.xml`, but revision result mapping, revision column selection, and `insertRevision` are actually owned by `VocabularyRevisionMapper.xml`. The implementation and contract test therefore changed the owning mapper rather than adding unrelated card-mapper SQL.
- No public API or DTO was changed.
- Existing design and implementation-plan documents already describe this additive metadata migration; no authoritative project-document update was necessary for this scoped task.

## Commit

- `c9389ce5 feat(vocabulary): 保存单词卡生成元数据`

## Remaining Issues

- Real MySQL 8 execution was not performed because `VOCABULARY_MYSQL_INTEGRATION_URL` was absent. Run the focused Maven command with the documented disposable-MySQL environment variables to execute the migration test.
- The change is additive and focused, and can be merged into `main` after the broader vocabulary workflow tasks and their integration validation are complete.

## Independent Review Remediation

### Fix Commit

- `fix(vocabulary): 完善生成元数据迁移验收`

### Changes

- Added the generation-metadata migration as the fifth historical upgrade step in `README.md` and the vocabulary architecture runbook. Fresh schemas now use only `schema.sql`; they do not run historical migrations.
- Documented `VOCABULARY_MYSQL_INTEGRATION_URL`, `VOCABULARY_MYSQL_INTEGRATION_USERNAME`, and `VOCABULARY_MYSQL_INTEGRATION_PASSWORD`, required disposable-instance privileges, the random schema prefix, and the no-business-schema/no-system-schema-access safety boundary.
- Extended the disposable MySQL migration test to assert `DATA_TYPE = json` and `IS_NULLABLE = YES`, then execute the real `VocabularyRevisionMapper` insert/select path for valid JSON and `NULL`. Cleanup directly drops the verified prefix schema without `USE mysql`.
- Added documentation contract assertions for migration ordering, fresh-schema behavior, integration-test configuration, permissions, and schema-prefix safety.

### Tests

- RED: `VocabularyDepositionDocsTest` failed before the README and architecture documentation were updated.
- GREEN: `VocabularyDepositionDocsTest` passed after the documentation and contract updates.
- `backend\\mvnw.cmd -q test` passed with `JWT_SECRET=test-jwt-secret-for-vocabulary-python-32-bytes`.
- `docs\\npm run build` passed.
- `git diff --check` passed.

### Not Run

- Real MySQL 8 migration execution was not run: this machine has no `VOCABULARY_MYSQL_INTEGRATION_URL` (or companion credential variables). `VocabularyGenerationMetadataMigrationMySqlTest` was skipped by its documented assumption; no MySQL success claim is made.

### Merge Assessment

- The remediation is scoped to migration acceptance tests and operational documentation. It is suitable to merge into `main` after a disposable MySQL 8 run executes the newly added JSON/NULL mapper round-trip assertions.
