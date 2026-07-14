# Python Vocabulary Card Generation Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move vocabulary-card model generation from Java to a dedicated Python Agents SDK workflow while Java retains dictionary truth, generation jobs, revision conflicts, validation, and persistence.

**Architecture:** Java keeps the durable generation queue and assembles the trusted dictionary core, then calls a versioned internal FastAPI endpoint. Python validates the typed request, optionally fills missing core fields, generates theme Markdown with dedicated structured-output agents, and returns a typed candidate; Java validates again and persists the revision plus generation metadata. A provider seam keeps the existing Java generator available only as an explicit rollout rollback path.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring WebClient, MyBatis, MySQL 8, Python 3.11, FastAPI, Pydantic, OpenAI Agents SDK 0.17.6, `unittest`, JUnit 5, Mockito, VitePress.

## Global Constraints

- Follow `docs/superpowers/specs/2026-07-14-vocabulary-python-generation-workflow-design.md` as the source of truth.
- Java owns authentication, dictionary lookup, job leases/retries, revision activation, conflict handling, validation, and all business persistence.
- Python must not read or write the vocabulary database, consume generation jobs, use chat sessions, route through the Assistant router, or return UI layout instructions.
- The internal endpoint is exactly `POST /internal/v1/vocabulary/card-generations` and uses a dedicated internal service token.
- The request and response contract versions are exactly `contractVersion: 1` and `coreSchemaVersion: 1`.
- Existing non-empty dictionary core values are immutable; Python may only fill missing phonetics, senses, meanings, or definitions.
- A usable core contains the requested term, at least one non-blank phonetic, and at least one sense with a non-blank part of speech and one non-blank English or Chinese definition.
- Dictionary-complete requests use one model call; incomplete requests use at most two model calls.
- Only validated core plus failed Markdown may return `outcome: partial`; invalid or unavailable core is an error.
- Markdown remains non-empty, at most 20,000 characters, and contains no raw HTML.
- Python provider calls bypass the legacy seven-day Java generation cache; that cache remains only in the explicit `java` rollback provider.
- Prompt version is selected by Python and returned in generation metadata; Java does not send a Prompt version.
- Agents SDK runs use no session and set `trace_include_sensitive_data=false`.
- No silent provider fallback is allowed inside a job attempt.
- Keep `VOCABULARY_GENERATION_PROVIDER=java` as the rollout-safe default until the deployment step explicitly switches it to `python`.
- Do not change the Web card API or notebook detail rendering contract in this plan.

---

## File Responsibility Map

- `backend/src/main/resources/db/migrate_add_vocabulary_generation_metadata.sql`: idempotent historical-schema migration for revision generation metadata.
- `backend/src/main/resources/db/schema.sql`: fresh-schema definition including nullable generation metadata.
- `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`: Java persistence field for metadata JSON.
- `backend/src/main/resources/mapper/VocabularyCardMapper.xml`: revision result-map and insert mapping.
- `python/ai_orchestrator/schemas/vocabulary_card.py`: strict cross-service and agent output schemas.
- `python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md`: core-completion Prompt asset.
- `python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md`: theme Markdown Prompt asset.
- `python/ai_orchestrator/agents/vocabulary_card.py`: typed capability Agent factories.
- `python/ai_orchestrator/workflows/vocabulary_card_generation.py`: deterministic generation orchestration and output validation.
- `python/ai_orchestrator/services/vocabulary_card_generation.py`: environment configuration and workflow service boundary.
- `python/ai_orchestrator/app.py`: internal-auth HTTP adapter and health exposure.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonClient.java`: typed Java-to-Python HTTP client.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java`: dictionary-first orchestration and provider selection.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/JavaVocabularyGenerationProvider.java`: temporary current Java model implementation.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java`: Python candidate adapter.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`: revision metadata serialization without changing lease semantics.
- `.env.example`, `backend/src/main/resources/application.yml`, and `docker-compose.yml`: rollout configuration shared by both services.
- `docs/architecture/vocabulary-deposition.md`, `docs/ai/vocabulary-theme-prompts.md`, and `docs/runbooks/environment-variables.md`: current architecture, Prompt ownership, and deployment configuration.

---

### Task 1: Persist Typed Generation Metadata on Vocabulary Revisions

**Files:**
- Create: `backend/src/main/resources/db/migrate_add_vocabulary_generation_metadata.sql`
- Create: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyGenerationMetadataMigrationMySqlTest.java`
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java`
- Modify: `backend/src/main/resources/mapper/VocabularyCardMapper.xml`
- Modify: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyDepositionSchemaTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyMapperContractTest.java`

**Interfaces:**
- Produces: nullable `VocabularyCardRevision.generationMetadataJson` mapped to `vocabulary_card_revision.generation_metadata_json JSON NULL`.
- Consumes: no later-task code; this task establishes the persistence field used by Task 7.

- [ ] **Step 1: Write failing schema and mapper contract tests**

Assert that fresh schema, migration, result map, revision column list, and insert statement include `generation_metadata_json`. The MySQL migration test must run against a uniquely named disposable schema, execute the migration twice, and prove the JSON column exists exactly once.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=VocabularyDepositionSchemaTest,VocabularyMapperContractTest,VocabularyGenerationMetadataMigrationMySqlTest" test
```

Expected: schema/mapper assertions fail because the column and migration do not exist; the MySQL test may skip only when its documented disposable-MySQL prerequisites are absent.

- [ ] **Step 3: Add the idempotent migration and persistence mapping**

Use `information_schema.columns` and prepared SQL so the historical migration is repeatable. Add the nullable JSON field to the fresh schema, entity, revision result map, column list, and insert statement. Do not expose it through the public API yet.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: all available focused tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/db/migrate_add_vocabulary_generation_metadata.sql backend/src/main/resources/db/schema.sql backend/src/main/resources/mapper/VocabularyCardMapper.xml backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyCardRevision.java backend/src/test/java/com/personalenglishai/backend/db
git commit -m "feat(vocabulary): 保存单词卡生成元数据"
```

---

### Task 2: Define Python Schemas, Prompt Assets, and Typed Agents

**Files:**
- Create: `python/ai_orchestrator/schemas/vocabulary_card.py`
- Create: `python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md`
- Create: `python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md`
- Create: `python/ai_orchestrator/agents/vocabulary_card.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_card_schemas.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_card_agents.py`
- Modify: `python/ai_orchestrator/prompts/agents.py`

**Interfaces:**
- Produces: `VocabularyCardGenerationRequest`, `VocabularyCardGenerationResponse`, `VocabularyCore`, `VocabularyThemeSnapshot`, `VocabularyGenerationMetadata`, `VocabularyCoreFallbackOutput`, and `VocabularyMarkdownOutput`.
- Produces: `create_vocabulary_core_fallback_agent(model)` and `create_vocabulary_card_markdown_agent(model)` using Pydantic `output_type`.
- Consumes: the prompt resolver and Agents SDK already used by other Python capability agents.

- [ ] **Step 1: Write failing Pydantic contract tests**

Cover camelCase aliases, forbidden extra fields, exact contract/core versions, bounded `term`, `sourceContext`, `timeoutBudgetMs`, allowed theme strategy keys, response outcome enum, metadata shape, and serialization aliases. Use `ConfigDict(extra="forbid", populate_by_name=True)` for cross-service models.

- [ ] **Step 2: Write failing Prompt and Agent factory tests**

Assert that both prompt keys resolve from repository assets; core instructions forbid changing non-empty dictionary truth; Markdown instructions treat purpose/context as data, forbid raw HTML, and require theme-specific Markdown. Assert both Agent factories set the expected `output_type` and model.

- [ ] **Step 3: Run focused Python tests and confirm RED**

Run from repository root:

```powershell
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_schemas python.ai_orchestrator.tests.test_vocabulary_card_agents -v
```

Expected: imports or prompt-key lookups fail because the assets and types do not exist.

- [ ] **Step 4: Implement strict schemas and Prompt-backed Agent factories**

Keep the HTTP response wrapper separate from the two model output schemas. Register both prompt keys in `_PROMPT_FILES` and `_STRUCTURED_OUTPUT_ONLY_AGENT_KEYS`. Add an explicit background-job prompt-key set so `load_agent_instructions` omits the shared handoff and user-context preamble for these non-conversational agents as well as the conversational Markdown policy.

The Markdown Agent output remains structured:

```python
class VocabularyMarkdownOutput(BaseModel):
    content_markdown: str = Field(serialization_alias="contentMarkdown")
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git add python/ai_orchestrator/schemas/vocabulary_card.py python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md python/ai_orchestrator/prompts/agents.py python/ai_orchestrator/agents/vocabulary_card.py python/ai_orchestrator/tests/test_vocabulary_card_schemas.py python/ai_orchestrator/tests/test_vocabulary_card_agents.py
git commit -m "feat(prompt): 增加单词卡生成结构化 Agent"
```

---

### Task 3: Implement the Stateless Python Generation Workflow

**Files:**
- Create: `python/ai_orchestrator/workflows/vocabulary_card_generation.py`
- Create: `python/ai_orchestrator/services/vocabulary_card_generation.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py`

**Interfaces:**
- Consumes: Task 2 schemas and Agent factories.
- Produces: `VocabularyCardGenerationService.from_env()`, `is_configured()`, and async `generate(request)`.
- Produces: stable `VocabularyCardGenerationError(code, retryable, message)` used by Task 4.

- [ ] **Step 1: Write failing workflow tests for call count and core ownership**

Mock `agents.Runner.run`. Prove that a complete core calls only the Markdown Agent; an incomplete core calls fallback then Markdown; the fallback merge cannot change existing phonetics, senses, definitions, schema version, or term; and no path exceeds two model calls.

- [ ] **Step 2: Add failing validation and degradation tests**

Cover core completeness, invalid fallback, empty/20,001-character/raw-HTML Markdown, unknown strategy, timeout budget exhaustion, model timeout, model upstream failure, and `partial` only when core is valid and Markdown fails.

- [ ] **Step 3: Add failing trace/privacy tests**

Assert `RunConfig.workflow_name == "PEAI Vocabulary Card Generation"`, no session is used, `trace_include_sensitive_data` is false, and the business trace/request IDs appear only in safe metadata.

- [ ] **Step 4: Run the workflow tests and confirm RED**

```powershell
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_workflow -v
```

Expected: workflow/service imports fail.

- [ ] **Step 5: Implement the minimum deterministic workflow**

Build explicit helpers for `is_core_complete`, immutable core merge, Markdown validation, Prompt input serialization, and remaining-budget checks. Use `Runner.run` with typed Agent outputs and no SQLite session. Resolve the active model only from Python environment configuration.

Use these stable outcomes:

```text
complete
partial + markdown_unavailable
```

Core failure must raise `CORE_CONTENT_UNAVAILABLE`; unsupported strategy/request version must be non-retryable; timeouts/upstream failures must be retryable.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 4 command. Expected: all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add python/ai_orchestrator/workflows/vocabulary_card_generation.py python/ai_orchestrator/services/vocabulary_card_generation.py python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py
git commit -m "feat(ai): 实现单词卡生成工作流"
```

---

### Task 4: Expose the Versioned Internal FastAPI Endpoint

**Files:**
- Create: `python/ai_orchestrator/tests/test_vocabulary_card_generation_endpoint.py`
- Modify: `python/ai_orchestrator/app.py`
- Modify: `python/ai_orchestrator/tests/test_app_cors.py`

**Interfaces:**
- Consumes: Task 3 `VocabularyCardGenerationService` and error type.
- Produces: authenticated `POST /internal/v1/vocabulary/card-generations` and additive `/health.vocabularyCardGenerationConfigured`.

- [ ] **Step 1: Write failing endpoint authentication and contract tests**

Using `TestClient`, cover missing token, wrong token, valid token, Pydantic 422, complete response, partial response, and response-model filtering. Patch the module-level service with a capturing fake, following existing endpoint tests.

- [ ] **Step 2: Write failing error-mapping tests**

Prove that invalid request/contract maps to 400, service auth to 401/403, retryable upstream to 503, timeout to 504, and unexpected internal errors to a sanitized 500 response. No exception message may expose Prompt or model output.

- [ ] **Step 3: Run focused tests and confirm RED**

```powershell
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_endpoint python.ai_orchestrator.tests.test_app_cors -v
```

- [ ] **Step 4: Add the thin HTTP adapter and health state**

Read `VOCABULARY_GENERATION_INTERNAL_TOKEN` in the service configuration. Compare the bearer token using `hmac.compare_digest`. Keep endpoint logic limited to authentication, schema parsing, workflow call, and error conversion.

- [ ] **Step 5: Run focused and full Python tests**

```powershell
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_endpoint python.ai_orchestrator.tests.test_app_cors -v
python -m unittest discover -s python/ai_orchestrator/tests -p "test_*.py"
```

Expected: focused and full Python suites pass.

- [ ] **Step 6: Commit**

```powershell
git add python/ai_orchestrator/app.py python/ai_orchestrator/tests/test_vocabulary_card_generation_endpoint.py python/ai_orchestrator/tests/test_app_cors.py
git commit -m "feat(api): 暴露内部单词卡生成接口"
```

---

### Task 5: Add the Typed Java-to-Python Client

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonClient.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationMetadata.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonClientTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Task 2 HTTP contract.
- Produces: `VocabularyGenerationPythonClient.generate(request)` returning a typed response or throwing `VocabularyGenerationException` with stable retryability.

- [ ] **Step 1: Write failing request serialization tests**

Inject a `WebClient` with a capturing `ExchangeFunction`. Assert the exact endpoint, bearer header, contract/core versions, request/trace IDs, timeout budget, core, source context, and frozen theme fields. Assert no Prompt version is sent.

- [ ] **Step 2: Write failing response and error tests**

Cover complete/partial response parsing, unknown outcome rejection, core term mismatch, malformed JSON, 400/422 non-retryable mapping, 401/403 non-retryable infrastructure mapping, and 500/503/504/connection-timeout retryable mapping.

- [ ] **Step 3: Run focused Java tests and confirm RED**

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=VocabularyGenerationPythonClientTest" test
```

- [ ] **Step 4: Implement the client and configuration**

Add configuration under:

```yaml
vocabulary:
  generation:
    provider: ${VOCABULARY_GENERATION_PROVIDER:java}
    python:
      base-url: ${VOCABULARY_GENERATION_PYTHON_BASE_URL:${ASSISTANT_ORCHESTRATOR_BASE_URL:http://127.0.0.1:8011}}
      internal-token: ${VOCABULARY_GENERATION_INTERNAL_TOKEN:}
      timeout-ms: ${VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS:60000}
```

Use a constructor overload accepting `WebClient` and `Duration` for deterministic tests. Never log the token, request core, context, or generated Markdown.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonClient.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonRequest.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationMetadata.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonClientTest.java backend/src/main/resources/application.yml
git commit -m "feat(vocabulary): 增加 Python 生成客户端"
```

---

### Task 6: Introduce the Provider Seam Without Changing Java Behavior

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationProvider.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationInput.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/JavaVocabularyGenerationProvider.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/JavaVocabularyGenerationProviderTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java`

**Interfaces:**
- Produces: `VocabularyGenerationProvider.key()` and `generate(VocabularyGenerationInput)`.
- Produces: high-level `VocabularyCardGenerator` that assembles dictionary core/context once and routes to exactly one configured provider.
- Consumes: existing Java OpenAI fallback/Markdown/cache implementation, moved intact into the `java` provider.

- [ ] **Step 1: Write failing provider-selection and dictionary-boundary tests**

Prove that `VocabularyCardGenerator` queries the dictionary once, builds immutable initial core, captures only the first source context, selects exactly one provider by key, rejects an unknown provider, and validates the provider result before returning it.

- [ ] **Step 2: Move existing Java behavior tests to the legacy provider**

Move Prompt, cache, poisoned-cache, fallback, partial, and raw-HTML cases from `VocabularyCardGeneratorTest` to `JavaVocabularyGenerationProviderTest` before moving implementation. Keep assertions unchanged so this is a behavior-preserving refactor.

- [ ] **Step 3: Run the focused tests and confirm RED at the new seam**

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=VocabularyCardGeneratorTest,JavaVocabularyGenerationProviderTest,VocabularyCoreFallbackGeneratorTest,VocabularyMarkdownPromptBuilderTest,VocabularyGenerationCacheTest" test
```

- [ ] **Step 4: Refactor the current generator behind the provider interface**

`VocabularyCardGenerator` keeps dictionary enrichment and final validation. `JavaVocabularyGenerationProvider` receives already assembled core and retains current OpenAI calls and seven-day cache. Add nullable typed generation metadata to `GeneratedVocabularyCard` without changing existing convenience constructors.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all existing Java behavior remains green.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationProvider.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationInput.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/JavaVocabularyGenerationProvider.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/JavaVocabularyGenerationProviderTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java backend/src/test/java/com/personalenglishai/backend/support/VocabularyTestFixtures.java
git commit -m "refactor(vocabulary): 分离单词卡生成提供方"
```

---

### Task 7: Integrate the Python Provider and Persist Its Metadata

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProviderTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`

**Interfaces:**
- Consumes: Task 5 Python client, Task 6 provider/input types, and Task 1 revision metadata field.
- Produces: provider key `python`, complete/partial `GeneratedVocabularyCard`, and serialized revision generation metadata.

- [ ] **Step 1: Write failing Python-provider tests**

Assert exact request mapping, core completeness behavior, frozen theme fields, request/trace ID creation, timeout budget, complete/partial mapping, term immutability, final Markdown validation, no Java cache interaction, and stable client-error propagation.

- [ ] **Step 2: Write failing Worker metadata and lease tests**

Extend Worker tests so a Python result persists valid JSON containing provider, model, Prompt version, model call count, and trace ID. Prove metadata is not persisted for invalid output, lost lease, or failed generation. Add a card-service assertion that user-authored revisions keep the field null. Keep complete/partial/conflict finalization assertions unchanged.

- [ ] **Step 3: Run focused tests and confirm RED**

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=PythonVocabularyGenerationProviderTest,VocabularyCardGeneratorTest,VocabularyGenerationWorkerTest,VocabularyGenerationFinalizerTest" test
```

- [ ] **Step 4: Implement the Python provider and metadata serialization**

The provider must not use `VocabularyGenerationCache`, `OpenAiClient`, or Java Prompt classes. Set Java's final core term from card identity, validate core/Markdown again, serialize only typed metadata, and preserve the current `generationOutcome`/`warning` contract.

- [ ] **Step 5: Run focused and full backend tests**

```powershell
.\mvnw.cmd -q "-Dtest=PythonVocabularyGenerationProviderTest,VocabularyCardGeneratorTest,VocabularyGenerationWorkerTest,VocabularyGenerationFinalizerTest" test
.\mvnw.cmd -q test
```

Expected: focused and full backend suites pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProviderTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizerTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java
git commit -m "feat(vocabulary): 接入 Python 单词卡生成"
```

---

### Task 8: Wire Deployment Configuration, Documentation, and Real-Model Acceptance

**Files:**
- Create: `python/ai_orchestrator/tests/test_vocabulary_card_generation_real_smoke.py`
- Modify: `.env.example`
- Modify: `backend/.env.example`
- Modify: `docker-compose.yml`
- Modify: `docs/architecture/vocabulary-deposition.md`
- Modify: `docs/ai/vocabulary-theme-prompts.md`
- Modify: `docs/runbooks/environment-variables.md`
- Modify: `backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: shared internal token/provider/model configuration, migration order, rollout/rollback instructions, and an opt-in real-model smoke test.

- [ ] **Step 1: Write failing documentation and configuration contract tests**

Require both backend and Python containers to receive the same internal token; require Java to receive the Python base URL/provider/timeout; require Python to receive its model and token; require docs to include the new migration, endpoint, provider switch, partial behavior, prompt ownership, and rollback.

- [ ] **Step 2: Add the opt-in real-model smoke test**

The test is skipped unless `RUN_VOCABULARY_REAL_MODEL_SMOKE=1`, `OPENAI_API_KEY`, and `VOCABULARY_GENERATION_INTERNAL_TOKEN` are set. It must call the Python service/workflow with one complete Basic core and one custom theme, then validate complete core, safe Markdown, Prompt version, model, call count, and trace metadata without printing generated content or secrets.

- [ ] **Step 3: Run contract tests and confirm RED**

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q "-Dtest=VocabularyDepositionDocsTest" test
cd ..
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_real_smoke -v
```

Expected: docs/config assertions fail; real smoke is skipped without explicit credentials.

- [ ] **Step 4: Update deployment assets and authoritative docs**

Add:

```text
VOCABULARY_GENERATION_PROVIDER
VOCABULARY_GENERATION_PYTHON_BASE_URL
VOCABULARY_GENERATION_PYTHON_TIMEOUT_MS
VOCABULARY_GENERATION_INTERNAL_TOKEN
```

Document that `java` is the initial rollout default, switching requires Python health/configuration first, and rollback is an explicit provider change. Add the metadata migration after the existing vocabulary migrations. Do not add the implementation plan to VitePress navigation.

- [ ] **Step 5: Run all automated verification**

```powershell
python -m unittest discover -s python/ai_orchestrator/tests -p "test_*.py"

cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-python-32-bytes'
.\mvnw.cmd -q test

cd ..\docs
npm run build

cd ..
git diff --check
```

- [ ] **Step 6: Run real-model and application acceptance when credentials are configured**

```powershell
$env:RUN_VOCABULARY_REAL_MODEL_SMOKE='1'
python -m unittest python.ai_orchestrator.tests.test_vocabulary_card_generation_real_smoke -v
```

Then start Python on `8011` and start the feature-worktree Java backend on an unused port against an explicitly named disposable MySQL schema with `VOCABULARY_GENERATION_PROVIDER=python`. Never apply the migration to the user's existing business schema as part of automated acceptance. Capture a Basic word and a custom-theme word, and verify:

- one complete Basic revision;
- one complete custom-theme revision;
- core contains term, phonetic, part of speech, and meaning;
- Markdown renders safely on the full-page detail;
- `generation_metadata_json` contains provider/model/Prompt version/call count/trace ID;
- job, partial, retry, conflict, and lease behavior remains correct.

If credentials are absent, record the real-model verification as not run; do not claim the model path is production-verified.

- [ ] **Step 7: Commit**

```powershell
git add .env.example backend/.env.example docker-compose.yml python/ai_orchestrator/tests/test_vocabulary_card_generation_real_smoke.py docs/architecture/vocabulary-deposition.md docs/ai/vocabulary-theme-prompts.md docs/runbooks/environment-variables.md backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java
git commit -m "docs(vocabulary): 补充 Python 生成发布验收"
```

---

## Final Review Gate

- [ ] Review `git diff` from the pre-plan implementation base through final HEAD for contract drift, secret exposure, retry amplification, stale-cache use, lease regression, core ownership violations, and public API changes.
- [ ] Confirm every task has an implementation report and focused reviewer verdict before advancing.
- [ ] Re-run full Python, backend, docs, and `git diff --check` verification after all review fixes.
- [ ] Verify `git status --short` is clean.
- [ ] Keep the branch and worktree intact until the user explicitly chooses merge, PR, or cleanup.
