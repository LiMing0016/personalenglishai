# Vocabulary Image Import Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compact vocabulary deposition workspace that accepts typed words or one photographed/uploaded image, uses a Python vision workflow to extract and flag candidate terms, requires explicit typo decisions, and deposits the confirmed terms with auditable per-term sources.

**Architecture:** The browser owns only page-session draft state and sends the image to an authenticated Java multipart endpoint. Java enforces user quota and file policy, calls a dedicated authenticated FastAPI endpoint, revalidates the strict response, verifies only suspected typos against the existing dictionary, and returns an enriched public contract. Confirmed candidates continue through the existing `/api/vocabulary/captures` path, extended additively with per-term source overrides; Python owns the versioned Prompt, one-model-call vision workflow, structured output, sanitization, and model trace.

**Tech Stack:** Vue 3, TypeScript 5.5, TanStack Vue Query 5, Axios, Node test runner with `tsx`, Playwright 1.58, Java 17, Spring Boot 3.2, WebClient, MyBatis, MySQL 8, JUnit 5, Mockito, Python 3.11, FastAPI 0.115, Pydantic, OpenAI Agents SDK 0.18.3, pytest 9, VitePress.

## Global Constraints

- Follow `docs/superpowers/specs/2026-07-21-vocabulary-image-import-workspace-design.md` as the source of truth.
- The collection page title is exactly `单词沉淀`; remove `WORD CARDS`, `单词卡中心`, and the existing long subtitle.
- The import workspace has exactly two modes: `文本录入` and `图片识别`; the image mode is hidden unless `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=true`.
- Accept exactly one non-empty JPG, PNG, or WEBP image with a maximum size of 10 MiB (`10 * 1024 * 1024` bytes).
- The public endpoint is exactly `POST /api/vocabulary/image-recognitions` with multipart field `file`.
- The internal endpoint is exactly `POST /internal/v1/vocabulary/image-recognitions` with multipart fields `contractVersion=1`, `traceId`, `language=en`, and `file`.
- Reuse `VOCABULARY_GENERATION_INTERNAL_TOKEN` for Java-to-Python bearer authentication; never expose or log it.
- Python uses `VOCABULARY_IMAGE_RECOGNITION_MODEL`; no model name, provider, Prompt, or API key comes from the browser.
- Prompt version is exactly `vocabulary-image-recognition-v1`.
- Python returns at most 30 candidates and at most 3 suggestions per suspected typo.
- Python internal suggestions remain `string[]`; Java public suggestions are `{ term: string, dictionaryVerified: boolean }[]`.
- Top-level warnings are limited to `CANDIDATE_LIMIT_REACHED` and `DICTIONARY_VERIFICATION_UNAVAILABLE`.
- Do not call PaddleOCR, writing handwriting recognition, the Assistant router, chat sessions, or the vocabulary card generation workflow from image recognition.
- Do not persist image bytes, image base64, full `rawText`, model raw responses, or Prompt text.
- Do not silently correct suspected typos; unresolved suspected typos block capture.
- The browser timeout is 60 seconds, Java-to-Python timeout is 55 seconds, and the Python model budget is 45 seconds.
- Use AI quota operation key `vocabulary.image_recognition`; image recognition and per-word card generation remain separate usage records.
- `VocabularyCaptureRequest.itemSources`, when present, is exactly as long as `terms`; text capture omits it.
- `ocr_image` source metadata contains only `recognitionTraceId`, `fileName`, `provider`, `model`, `promptVersion`, `observedText`, and `resolution`.
- `resolution` is exactly one of `accepted`, `suggestion_applied`, or `original_kept`.
- Import state stays in `VocabularyCapturePanel`; do not add a Pinia store or image persistence.
- Do not add a frontend icon dependency. Use the existing visual language and text commands for upload, camera, retry, replace, select all, and clear.
- Product events never contain a file name, term, observed text, context text, raw recognition text, card Markdown, or image data.
- Do not run Playwright or browser automation until the user explicitly authorizes the chosen browser for that verification run.
- Deploy Python and Java before enabling the frontend flag; rollback is disabling `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED`.

---

## File Responsibility Map

- `python/ai_orchestrator/schemas/vocabulary_image_recognition.py`: strict model-output, internal-response, warning, usage, and request value objects.
- `python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md`: versioned extraction and typo Prompt asset.
- `python/ai_orchestrator/agents/vocabulary_image_recognition.py`: typed vision capability Agent factory.
- `python/ai_orchestrator/workflows/vocabulary_image_recognition.py`: one-call vision orchestration, bounded retry, normalization, deduplication, truncation, usage, and safe trace metadata.
- `python/ai_orchestrator/services/vocabulary_image_recognition.py`: environment configuration and application-service boundary.
- `python/ai_orchestrator/app.py`: authenticated multipart adapter, file bounds, health status, and stable HTTP error mapping.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java`: typed multipart Java-to-Python client and transport mapping.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java`: public use case, quota, file validation, usage recording, dictionary verification, logs, and public response assembly.
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java`: browser-facing enriched recognition contract.
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`: additive `ocr_image` and per-item source contract.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`: per-index source merge without changing card identity, job, or revision behavior.
- `web/src/features/vocabulary/imageRecognition.ts`: file policy, candidate reducer, deduplication, typo decisions, capture batch construction, and feature flag.
- `web/src/components/vocabulary/VocabularyCapturePanel.vue`: single source of truth for import mode, theme, context, candidates, recognition, and grouped capture submission.
- `web/src/components/vocabulary/VocabularyTextCapture.vue`: text input adapter.
- `web/src/components/vocabulary/VocabularyImageCapture.vue`: file/camera input, preview, recognition lifecycle, raw-text disclosure, retry, and replacement.
- `web/src/components/vocabulary/VocabularyTermReview.vue`: editable selected candidates and explicit typo resolution.
- `web/src/components/vocabulary/VocabularyThemeSelect.vue`: compact theme select and management link.
- `backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql`: idempotent event persistence for the measured funnel.
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java`: event allowlist, privacy boundary, idempotency, and server-side capture/ready events.
- `web/src/features/vocabulary/productEvents.ts`: page-session event IDs and batched browser reporting.
- `docs/api/vocabulary.md`, `docs/ai/vocabulary-image-recognition.md`, `docs/architecture/vocabulary-deposition.md`, and `docs/runbooks/environment-variables.md`: long-lived API, Prompt, architecture, rollout, and smoke-test documentation.

---

### Task 1: Lock the Python Recognition Schema and Prompt Asset

**Files:**
- Create: `python/ai_orchestrator/schemas/vocabulary_image_recognition.py`
- Create: `python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md`
- Create: `python/ai_orchestrator/agents/vocabulary_image_recognition.py`
- Modify: `python/ai_orchestrator/prompts/agents.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py`

**Interfaces:**
- Produces: `VocabularyImageRecognitionRequest`, `VocabularyImageRecognitionModelOutput`, `VocabularyImageRecognitionResponse`, `VocabularyImageRecognitionItem`, `VocabularyImageRecognitionGeneration`, and `build_vocabulary_image_recognition_agent(model: str) -> Agent`.
- Produces: constants `PROMPT_VERSION = "vocabulary-image-recognition-v1"`, `MAX_CANDIDATES = 30`, `MAX_IMAGE_BYTES = 10 * 1024 * 1024`, and `MAX_MODEL_CALLS = 2`.
- Consumes: `resolve_agent_prompt_kwargs` from the existing Prompt resolver and OpenAI Agents SDK `Agent`.

- [ ] **Step 1: Write failing strict-schema tests**

Create tests that prove aliases serialize exactly, unknown fields fail, `confidence` is bounded, a suspected typo requires 1-3 suggestions, accepted terms have no suggestions, response trace IDs match, and warnings reject unknown values:

```python
def test_suspected_typo_requires_suggestions() -> None:
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionModelItem(
            observedText="recieve",
            normalizedTerm="recieve",
            status="suspected_typo",
            suggestions=[],
            contextText=None,
            confidence=0.9,
        )


def test_response_contract_uses_exact_public_aliases() -> None:
    response = VocabularyImageRecognitionResponse(
        contractVersion=1,
        traceId="vocab-image-123",
        rawText="package",
        warnings=[],
        items=[accepted_item()],
        generation=generation("vocab-image-123"),
    )
    payload = response.model_dump(by_alias=True, mode="json")
    assert set(payload) == {"contractVersion", "traceId", "rawText", "warnings", "items", "generation"}
    assert payload["generation"]["promptVersion"] == "vocabulary-image-recognition-v1"
```

- [ ] **Step 2: Run the schema tests and confirm RED**

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py -q
```

Expected: collection fails because `python.ai_orchestrator.schemas.vocabulary_image_recognition` does not exist.

- [ ] **Step 3: Implement the strict schemas and constants**

Use strict Pydantic models and separate the model-facing item from the response item so `itemId` is deterministic application output:

```python
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator

PROMPT_VERSION = "vocabulary-image-recognition-v1"
MAX_CANDIDATES = 30
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_MODEL_CALLS = 2

RecognitionStatus = Literal["accepted", "suspected_typo"]
PythonRecognitionWarning = Literal["CANDIDATE_LIMIT_REACHED"]


class StrictRecognitionModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class VocabularyImageRecognitionModelItem(StrictRecognitionModel):
    observed_text: str = Field(alias="observedText", min_length=1, max_length=200)
    normalized_term: str = Field(alias="normalizedTerm", min_length=1, max_length=200)
    status: RecognitionStatus
    suggestions: list[str] = Field(max_length=3)
    context_text: str | None = Field(default=None, alias="contextText", max_length=2_000)
    confidence: float = Field(ge=0, le=1)

    @model_validator(mode="after")
    def validate_suggestion_state(self) -> "VocabularyImageRecognitionModelItem":
        if self.status == "suspected_typo" and not self.suggestions:
            raise ValueError("suspected_typo requires at least one suggestion")
        if self.status == "accepted" and self.suggestions:
            raise ValueError("accepted items must not include suggestions")
        return self


class VocabularyImageRecognitionModelOutput(StrictRecognitionModel):
    raw_text: str = Field(alias="rawText", max_length=20_000)
    items: list[VocabularyImageRecognitionModelItem] = Field(max_length=100)


class VocabularyImageRecognitionUsage(StrictRecognitionModel):
    input_tokens: int = Field(alias="inputTokens", ge=0)
    output_tokens: int = Field(alias="outputTokens", ge=0)


class VocabularyImageRecognitionRequest(StrictRecognitionModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    trace_id: str = Field(alias="traceId", min_length=1, max_length=128)
    language: Literal["en"]
    file_name: str = Field(alias="fileName", min_length=1, max_length=255)
    content_type: Literal["image/jpeg", "image/png", "image/webp"] = Field(alias="contentType")
    content: bytes = Field(min_length=1, max_length=MAX_IMAGE_BYTES)
```

Complete the file with response item, generation metadata, nullable usage, exact warning list, opaque trace validation, and a response model validator that requires `generation.traceId == traceId`.

- [ ] **Step 4: Write and run failing Agent/Prompt tests**

The tests must assert the prompt contains the three extraction priorities, visible-evidence rule, no silent correction, no definitions, no Markdown, and 30-candidate/3-suggestion limits. Patch the resolver and assert the Agent uses `VocabularyImageRecognitionModelOutput` as `output_type`. Also assert `prompts/agents.py` registers `vocabulary_image_recognition` in `_PROMPT_FILES`, `_STRUCTURED_OUTPUT_ONLY_AGENT_KEYS`, and `_BACKGROUND_JOB_AGENT_KEYS` so the Prompt receives no chat handoff or Markdown policy.

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py -q
```

Expected: FAIL until the Prompt asset and factory exist.

- [ ] **Step 5: Implement the Prompt asset and typed Agent factory**

The Prompt must use explicit sections `Goal`, `Extraction order`, `Spelling policy`, `Output`, and `Prohibitions`. The factory is:

```python
def build_vocabulary_image_recognition_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_image_recognition")
    return Agent(
        name="Vocabulary image recognition",
        model=model,
        output_type=VocabularyImageRecognitionModelOutput,
        **prompt_kwargs,
    )
```

- [ ] **Step 6: Run focused tests and commit**

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py -q
```

Expected: all tests pass.

Commit:

```powershell
git add python/ai_orchestrator/schemas/vocabulary_image_recognition.py python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md python/ai_orchestrator/prompts/agents.py python/ai_orchestrator/agents/vocabulary_image_recognition.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py
git commit -m "feat(python): 新增单词图片识别结构化契约"
```

---

### Task 2: Implement the Python Vision Workflow and Internal Multipart Endpoint

**Files:**
- Create: `python/ai_orchestrator/workflows/vocabulary_image_recognition.py`
- Create: `python/ai_orchestrator/services/vocabulary_image_recognition.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py`
- Create: `python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py`
- Modify: `python/ai_orchestrator/app.py`

**Interfaces:**
- Consumes: Task 1 schemas, Prompt, Agent factory, existing `build_input_items`, and existing `extract_usage`.
- Produces: `VocabularyImageRecognitionService.recognize(request: VocabularyImageRecognitionRequest) -> VocabularyImageRecognitionResponse`.
- Produces: `POST /internal/v1/vocabulary/image-recognitions` and health key `vocabularyImageRecognitionConfigured`.
- Produces stable service error codes `INVALID_IMAGE_REQUEST`, `UNSUPPORTED_IMAGE_TYPE`, `IMAGE_TOO_LARGE`, `IMAGE_RECOGNITION_NOT_CONFIGURED`, `MODEL_OUTPUT_INVALID`, `MODEL_UPSTREAM_UNAVAILABLE`, and `MODEL_TIMEOUT`.

- [ ] **Step 1: Write failing workflow tests**

Patch `agents.Runner.run` and cover one model call, image data-URL input, stable deduplication, boundary punctuation normalization, deterministic `item-1` IDs, 30-item truncation with warning, usage extraction, empty results, schema failure retry exactly once, timeout, and cancellation propagation:

```python
@pytest.mark.asyncio
async def test_recognition_deduplicates_and_assigns_stable_ids() -> None:
    output = model_output(
        item("Package", "package"),
        item("package", "package"),
    )
    result = SimpleNamespace(
        final_output=output,
        context_wrapper=SimpleNamespace(usage=Usage(input_tokens=120, output_tokens=18)),
    )
    with patch("agents.Runner.run", new_callable=AsyncMock, return_value=result) as run:
        response = await workflow().recognize(request())
    assert [item.item_id for item in response.items] == ["item-1"]
    assert response.items[0].normalized_term == "package"
    assert run.await_count == 1
```

- [ ] **Step 2: Run workflow tests and confirm RED**

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py -q
```

Expected: import failure for the missing workflow.

- [ ] **Step 3: Implement deterministic workflow orchestration**

Create an immutable request carrying `trace_id`, `language`, `file_name`, `content_type`, and `content`. Build Agents SDK input with one text instruction and one `input_image` data URL. Use `RunConfig(workflow_name="Vocabulary Image Recognition", trace_include_sensitive_data=False, trace_metadata={"trace_id": trace_id})`.

The core normalization and retry structure is:

```python
async def recognize(self, request: VocabularyImageRecognitionRequest) -> VocabularyImageRecognitionResponse:
    self._validate_request(request)
    last_error: Exception | None = None
    for call_number in range(1, MAX_MODEL_CALLS + 1):
        try:
            result = await asyncio.wait_for(
                Runner.run(self._agent, self._input_items(request), run_config=self._run_config(request)),
                timeout=self._timeout_seconds,
            )
            output = self._require_model_output(result.final_output)
            items, warnings = self._sanitize_items(output.items)
            usage = extract_usage(result)
            return self._response(request, output.raw_text, items, warnings, usage, call_number)
        except asyncio.CancelledError:
            raise
        except (ModelBehaviorError, ValidationError, TypeError, ValueError) as exc:
            last_error = exc
            if call_number == MAX_MODEL_CALLS:
                raise VocabularyImageRecognitionError("MODEL_OUTPUT_INVALID", True) from exc
    raise VocabularyImageRecognitionError("MODEL_OUTPUT_INVALID", True) from last_error
```

Transport, timeout, and rate-limit exceptions must map without including provider messages. Runtime logs contain trace ID, byte count, candidate count, suspected count, call count, provider, model, Prompt version, elapsed milliseconds, and stable code only.

- [ ] **Step 4: Write failing service and endpoint tests**

Use FastAPI `TestClient` and cover missing/wrong bearer token, missing model configuration, exact multipart aliases, empty file, wrong MIME, extension/MIME mismatch, 10 MiB boundary, oversized input, success, empty items, 502 schema error, 503 provider error, and 504 timeout. Verify response/log text never contains a test image marker.

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py -q
```

Expected: FAIL because the endpoint and service do not exist.

- [ ] **Step 5: Implement service configuration and the internal endpoint**

Read `VOCABULARY_IMAGE_RECOGNITION_MODEL`, `VOCABULARY_GENERATION_INTERNAL_TOKEN`, and `VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS` with a default of `45000`. Reject a timeout outside `1..45000`.

Add the endpoint adapter:

```python
@app.post(
    "/internal/v1/vocabulary/image-recognitions",
    response_model=VocabularyImageRecognitionResponse,
    dependencies=[Depends(_require_vocabulary_image_recognition_internal_token)],
)
async def recognize_vocabulary_image(
    contract_version: Annotated[int, Form(alias="contractVersion")],
    trace_id: Annotated[str, Form(alias="traceId")],
    language: Annotated[str, Form()],
    file: Annotated[UploadFile, File()],
) -> VocabularyImageRecognitionResponse:
    content = await file.read(MAX_IMAGE_BYTES + 1)
    request = VocabularyImageRecognitionRequest(
        contractVersion=contract_version,
        traceId=trace_id,
        language=language,
        fileName=file.filename or "image",
        contentType=file.content_type or "application/octet-stream",
        content=content,
    )
    return await vocabulary_image_recognition_service.recognize(request)
```

Map invalid input to 400/422, model output failure to 502, unavailable/not configured to 503, and timeout to 504. Do not return exception details.

- [ ] **Step 6: Add an opt-in real-model smoke test**

The test is skipped unless all four are present: `RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE=1`, `OPENAI_API_KEY`, `VOCABULARY_IMAGE_RECOGNITION_MODEL`, and `VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE` pointing to an existing local JPG/PNG/WEBP file. Assert contract, trace, max 30 items, Prompt version, and call count without printing the path or output text.

- [ ] **Step 7: Run Python tests and commit**

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py -q
```

Expected: deterministic tests pass and the real smoke reports skipped unless explicitly enabled.

Commit:

```powershell
git add python/ai_orchestrator/app.py python/ai_orchestrator/workflows/vocabulary_image_recognition.py python/ai_orchestrator/services/vocabulary_image_recognition.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py
git commit -m "feat(python): 接入单词图片识别工作流"
```

---

### Task 3: Build the Strict Java-to-Python Multipart Client

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionException.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClientTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Python Task 2 multipart endpoint and exact internal JSON contract.
- Produces: `VocabularyImageRecognitionPythonClient.recognize(String traceId, MultipartFile file) -> VocabularyImageRecognitionPythonResponse`.
- Produces: configuration keys `vocabulary.image-recognition.python.base-url`, `.internal-token`, and `.timeout-ms` mapped from the orchestrator URL, `VOCABULARY_GENERATION_INTERNAL_TOKEN`, and `VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS=55000`.

- [ ] **Step 1: Write failing client contract tests**

Use the existing capturing `ExchangeFunction` pattern from `VocabularyGenerationPythonClientTest`. Assert path, bearer token, multipart parts, 55-second bound, exact field set, trace match, warning allowlist, usage parsing, and status mapping. The public client error codes are:

```java
PYTHON_IMAGE_REQUEST_REJECTED
PYTHON_IMAGE_AUTH_FAILED
PYTHON_IMAGE_OUTPUT_INVALID
PYTHON_IMAGE_NOT_CONFIGURED
PYTHON_IMAGE_UPSTREAM_UNAVAILABLE
PYTHON_IMAGE_TIMEOUT
PYTHON_IMAGE_TRANSPORT_FAILED
```

The 400/422 response is non-retryable, 401/403 is a non-retryable infrastructure error, 502 is output invalid, 503 is unavailable/not configured, and 504/client timeout is timeout. No response body is included in messages.

- [ ] **Step 2: Run the test and confirm RED**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionPythonClientTest test
```

Expected: test compilation fails because the client types do not exist.

- [ ] **Step 3: Implement strict response records**

Use records with compact constructors and exact JSON field validation, following `VocabularyGenerationPythonResponse`. The internal types are:

```java
public record VocabularyImageRecognitionPythonResponse(
        int contractVersion,
        String traceId,
        String rawText,
        List<String> warnings,
        List<Item> items,
        Generation generation) {
    public record Item(
            String itemId,
            String observedText,
            String normalizedTerm,
            String status,
            List<String> suggestions,
            String contextText,
            double confidence) {}
    public record Generation(
            String provider,
            String model,
            String promptVersion,
            int modelCallCount,
            String traceId,
            Usage usage) {}
    public record Usage(Integer inputTokens, Integer outputTokens) {}
}
```

Enforce contract version 1, at most 30 items, at most 3 suggestions, raw text at most 20,000, known statuses/warnings, confidence `0..1`, matching traces, nonblank provider/model/Prompt, call count `1..2`, and nonnegative nullable usage.

- [ ] **Step 4: Implement the multipart client**

Build a `MultipartBodyBuilder` with string fields and a `ByteArrayResource` whose `getFilename()` returns the sanitized original file name. Post to `/internal/v1/vocabulary/image-recognitions`, set bearer auth, call `.timeout(Duration.ofMillis(55000))`, parse with the strict response factory, and reject a response trace that differs from the request. The internal warning parser accepts only `CANDIDATE_LIMIT_REACHED`; `DICTIONARY_VERIFICATION_UNAVAILABLE` is added only by the Java public service.

```java
public VocabularyImageRecognitionPythonResponse recognize(String traceId, MultipartFile file) {
    MultipartBodyBuilder parts = new MultipartBodyBuilder();
    parts.part("contractVersion", "1");
    parts.part("traceId", traceId);
    parts.part("language", "en");
    ByteArrayResource resource = new ByteArrayResource(readBytes(file)) {
        @Override public String getFilename() {
            return safeFileName(file.getOriginalFilename());
        }
    };
    parts.part("file", resource)
            .contentType(MediaType.parseMediaType(file.getContentType()));
    return exchange(traceId, parts);
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionPythonClientTest test
```

Expected: all client tests pass.

Commit:

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionException.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClientTest.java backend/src/main/resources/application.yml
git commit -m "feat(api): 新增图片识别 Python 客户端"
```

---

### Task 4: Expose the Java Public Recognition Use Case

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionServiceTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Task 3 client, `VocabularyDictionaryEnricher.lookupWithoutUserState`, `SubscriptionService.assertAiTokenQuotaAvailable`, `AiUsageContextHolder`, and `AiUsageRecorder`.
- Produces: `VocabularyImageRecognitionService.recognize(Long userId, MultipartFile file) -> VocabularyImageRecognitionResponse`.
- Produces: authenticated `POST /api/vocabulary/image-recognitions`.
- Produces public suggestions as `Suggestion(String term, boolean dictionaryVerified)` and warnings as a deduplicated ordered list.

- [ ] **Step 1: Write failing service tests for validation, quota, and dictionary verification**

Cover empty input, exact 10 MiB acceptance, 10 MiB + 1 rejection, allowed MIME/extensions, mismatch rejection, quota rejection before client invocation, original dictionary hit, verified suggestion ordering, no dictionary hits, dictionary outage warning, usage recording, and output/error body privacy.

```java
@Test
void original_dictionary_hit_downgrades_model_typo_to_accepted() {
    when(client.recognize(anyString(), any())).thenReturn(responseWithTypo("colour", List.of("color")));
    when(dictionary.lookupWithoutUserState("colour", "en")).thenReturn(dictionaryHit("colour"));

    VocabularyImageRecognitionResponse response = service.recognize(7L, png("words.png", 20));

    assertEquals("accepted", response.items().getFirst().status());
    assertTrue(response.items().getFirst().suggestions().isEmpty());
    verify(dictionary, never()).lookupWithoutUserState("color", "en");
}
```

- [ ] **Step 2: Run service tests and confirm RED**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionServiceTest test
```

Expected: test compilation fails because the public DTO/service do not exist.

- [ ] **Step 3: Implement file policy, quota, model usage, and dictionary enrichment**

The public item contract is:

```java
public record Item(
        String itemId,
        String observedText,
        String normalizedTerm,
        String status,
        List<Suggestion> suggestions,
        String contextText,
        double confidence) {}

public record Suggestion(String term, boolean dictionaryVerified) {}
```

The service sequence is exact:

1. Validate user, bytes, MIME, and extension.
2. Call `subscriptionService.assertAiTokenQuotaAvailable(userId)`.
3. Generate `vocab-image-<32 lowercase hex>` trace ID.
4. Enter `AiUsageContext(userId, "vocabulary.image_recognition", traceId)`.
5. Call Python once and record returned usage with `providerRequestId=traceId`.
6. Query the dictionary only for `suspected_typo` items.
7. Return enriched suggestions and warnings.

Do not catch the subscription exception. Catch only dictionary service availability failures around the dictionary pass; add one `DICTIONARY_VERIFICATION_UNAVAILABLE` warning and preserve all model typo states.

- [ ] **Step 4: Write failing controller and global error tests**

Use `MockMultipartFile` and assert anonymous 401, valid multipart 200, empty/invalid file 400, quota 429, schema 502, unavailable 503, and timeout 504. Verify the controller only delegates to the service.

- [ ] **Step 5: Implement endpoint and stable error mapping**

Add:

```java
@PostMapping(value = "/image-recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<VocabularyImageRecognitionResponse>> recognizeImage(
        @RequestAttribute(value = "userId", required = false) Long userId,
        @RequestParam("file") MultipartFile file) {
    if (userId == null) {
        return unauthorized();
    }
    return ResponseEntity.ok(ApiResponse.success(imageRecognitionService.recognize(userId, file)));
}
```

Add error codes with numeric prefixes that drive the existing handler:

```java
VOCABULARY_IMAGE_INVALID("400052", "图片格式或大小不符合要求"),
VOCABULARY_IMAGE_OUTPUT_INVALID("502050", "图片识别结果不可用"),
VOCABULARY_IMAGE_UNAVAILABLE("503050", "图片识别服务暂时不可用"),
VOCABULARY_IMAGE_TIMEOUT("504050", "图片识别超时"),
```

Extend `GlobalExceptionHandler.resolveStatus` with `502 -> BAD_GATEWAY` and `504 -> GATEWAY_TIMEOUT`.

- [ ] **Step 6: Run focused Java tests and commit**

Run:

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-image-32-bytes'
mvn -Dtest=VocabularyImageRecognitionServiceTest,VocabularyControllerTest,GlobalExceptionHandlerTest test
```

Expected: all tests pass.

Commit:

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionServiceTest.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java backend/src/test/java/com/personalenglishai/backend/common/web/GlobalExceptionHandlerTest.java
git commit -m "feat(api): 提供单词图片识别接口"
```

---

### Task 5: Preserve Per-Term OCR Source Decisions Through Capture

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`

**Interfaces:**
- Produces: additive `List<ItemSource> itemSources` on `VocabularyCaptureRequest`.
- Produces: source type `ocr_image` while retaining `manual` and `dictionary`.
- Produces: `ItemSource(String contextText, Map<String,Object> metadata)` selected by the same zero-based index as `terms`.
- Consumes: existing card identity, idempotency, theme resolution, source insertion, and generation-job logic without changing those contracts.

- [ ] **Step 1: Write failing request and service tests**

Cover backward-compatible JSON without `itemSources`, six-argument Java constructor compatibility, itemSources length mismatch, missing itemSources for `ocr_image`, exact metadata allowlist, resolution enum, merged batch/item metadata, item context override, forbidden `rawText`/`imageBase64`, and source list/filter visibility.

```java
@Test
void ocr_capture_merges_batch_and_indexed_source_metadata() {
    VocabularyCaptureRequest request = ocrRequest(
            List.of("receive", "package"),
            List.of(
                    itemSource("I receive it", Map.of("observedText", "recieve", "resolution", "suggestion_applied")),
                    itemSource(null, Map.of("observedText", "package", "resolution", "accepted"))));

    service.capture(7L, request);

    VocabularyCardSource first = insertedSources.getFirst();
    assertEquals("ocr_image", first.getSourceType());
    assertEquals("I receive it", first.getContextText());
    assertJsonContains(first.getMetadataJson(), "recognitionTraceId", "trace-1");
    assertJsonContains(first.getMetadataJson(), "observedText", "recieve");
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest,VocabularyControllerTest test
```

Expected: tests fail because `ocr_image` and `itemSources` are rejected/missing.

- [ ] **Step 3: Add the backward-compatible request shape**

Change the record to:

```java
public record VocabularyCaptureRequest(
        @NotBlank @Size(max = 128) String clientRequestId,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 255) String> terms,
        @Size(max = 16) String language,
        String themeUid,
        @Pattern(regexp = "basic|exam|reading") String templateKey,
        @Valid Source source,
        @Valid List<ItemSource> itemSources) {

    public VocabularyCaptureRequest(
            String clientRequestId, List<String> terms, String language,
            String themeUid, String templateKey, Source source) {
        this(clientRequestId, terms, language, themeUid, templateKey, source, List.of());
    }

    public record ItemSource(String contextText, Map<String, Object> metadata) {}
}
```

Extend the source regex to `manual|dictionary|ocr_image`.

- [ ] **Step 4: Validate and merge the per-item source**

In `VocabularyCaptureService.validate`, require `itemSources.size() == terms.size()` when non-empty and require a full itemSources list for `ocr_image`. Validate exact batch keys and per-item keys and scalar bounds before opening item transactions.

In `VocabularyCaptureItemService`, resolve the source before insertion:

```java
private VocabularyCaptureRequest.Source sourceForIndex(VocabularyCaptureRequest request, int index) {
    VocabularyCaptureRequest.Source batch = defaultSource(request.source());
    if (request.itemSources() == null || request.itemSources().isEmpty()) {
        return batch;
    }
    VocabularyCaptureRequest.ItemSource item = request.itemSources().get(index);
    Map<String, Object> metadata = new LinkedHashMap<>(nullToEmpty(batch.metadata()));
    metadata.putAll(nullToEmpty(item.metadata()));
    String context = item.contextText() == null ? batch.contextText() : item.contextText();
    return new VocabularyCaptureRequest.Source(
            batch.type(), batch.sourceRef(), batch.sourceTitle(), batch.sourceUrl(), context, metadata);
}
```

Pass that resolved source into `newSource`. Do not alter idempotency keys or generation job request indexes.

- [ ] **Step 5: Run focused tests and commit**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest,VocabularyControllerTest,VocabularyCardServiceTest test
```

Expected: all tests pass, including existing manual/dictionary capture tests.

Commit:

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java
git commit -m "feat(api): 支持图片识别逐词来源"
```

---

### Task 6: Build the Frontend Recognition API and Candidate State Model

**Files:**
- Create: `web/src/features/vocabulary/imageRecognition.ts`
- Create: `web/tests/vocabularyImageRecognition.test.ts`
- Modify: `web/src/api/vocabulary.ts`
- Modify: `web/src/composables/useVocabularyCards.ts`
- Modify: `web/env.example`

**Interfaces:**
- Produces: `recognizeVocabularyImage({ file, signal }): Promise<VocabularyImageRecognitionResponse>` with 60-second timeout.
- Produces: `ImportCandidate`, `mergeRecognitionCandidates`, `applySuggestion`, `keepOriginal`, `removeCandidate`, `updateCandidateTerm`, `selectedReadyCandidates`, and `buildCaptureBatches`.
- Produces: `isVocabularyImageRecognitionEnabled(): boolean` that is true only for the exact string `true`.
- Consumes: Task 4 public response and Task 5 capture request.

- [ ] **Step 1: Write failing API and pure-state tests**

Cover file policy, feature flag parsing, accepted defaults, unresolved typo blocking, verified/unverified suggestions, explicit typo actions, case-insensitive stable deduplication, 30-item warning, all-select/clear, mixed manual/OCR grouping, per-item metadata, and no rawText in capture payload.

```typescript
test('groups mixed candidates into source-safe capture batches', () => {
  const batches = buildCaptureBatches({
    candidates: [manualCandidate('hello'), acceptedImageCandidate('package', 'trace-1')],
    themeUid: 'theme_system_basic',
    sourceContext: 'chapter 2',
  })
  assert.equal(batches.length, 2)
  assert.equal(batches[0].payload.source.type, 'manual')
  assert.equal(batches[1].payload.source.type, 'ocr_image')
  assert.deepEqual(batches[1].payload.itemSources?.[0].metadata, {
    observedText: 'package',
    resolution: 'accepted',
  })
  assert.equal(JSON.stringify(batches).includes('rawText'), false)
})
```

- [ ] **Step 2: Run tests and confirm RED**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts
```

Expected: test import fails because the feature module/types do not exist.

- [ ] **Step 3: Add exact API and capture types**

Add:

```typescript
export type VocabularyRecognitionStatus = 'accepted' | 'suspected_typo'
export type VocabularyRecognitionWarning =
  | 'CANDIDATE_LIMIT_REACHED'
  | 'DICTIONARY_VERIFICATION_UNAVAILABLE'

export interface VocabularyImageRecognitionSuggestion {
  term: string
  dictionaryVerified: boolean
}

export interface VocabularyImageRecognitionItem {
  itemId: string
  observedText: string
  normalizedTerm: string
  status: VocabularyRecognitionStatus
  suggestions: VocabularyImageRecognitionSuggestion[]
  contextText: string | null
  confidence: number
}

export interface VocabularyCaptureItemSource {
  contextText?: string
  metadata: Record<string, unknown>
}
```

Change `VocabularyCaptureSource.type` to `'manual' | 'ocr_image'`, add optional `itemSources`, and post `FormData` with `{ timeout: 60_000, signal }`.

- [ ] **Step 4: Implement the immutable candidate reducer and batch builder**

The candidate shape is:

```typescript
export type CandidateResolution =
  | 'accepted'
  | 'unresolved'
  | 'suggestion_applied'
  | 'original_kept'

export interface ImportCandidate {
  id: string
  source: 'manual' | 'ocr_image'
  sourceBatchId: string
  observedText: string
  term: string
  status: VocabularyRecognitionStatus
  resolution: CandidateResolution
  selected: boolean
  suggestions: VocabularyImageRecognitionSuggestion[]
  contextText: string | null
  recognition?: VocabularyImageRecognitionResponse['generation'] & {
    traceId: string
    fileName: string
  }
}
```

`buildCaptureBatches` groups by `manual` and each OCR trace, filters selected/resolved candidates, preserves display order, creates one client request ID per batch, puts common recognition metadata on `source.metadata`, and puts `observedText`/`resolution` plus per-word context in `itemSources`.

`updateCandidateTerm` leaves manual candidates as manual. For OCR candidates, a term equal to the normalized observed text remains `accepted` (or `original_kept` after that explicit typo action); a different user-confirmed term is `suggestion_applied`, even when it was typed rather than selected from the model suggestion list.

- [ ] **Step 5: Add the TanStack mutation and run tests/build**

Add an `imageRecognitionMutation` to `useVocabularyCards` whose mutation variables are `{ file: File; signal: AbortSignal }`. It does not invalidate card queries because recognition has not persisted anything.

Run:

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts tests/vocabularyApiContract.test.ts
npm run build
```

Expected: tests and TypeScript/Vite build pass.

- [ ] **Step 6: Commit**

```powershell
git add web/src/api/vocabulary.ts web/src/composables/useVocabularyCards.ts web/src/features/vocabulary/imageRecognition.ts web/tests/vocabularyImageRecognition.test.ts web/env.example
git commit -m "feat(ui): 建立图片候选状态模型"
```

---

### Task 7: Rebuild the Compact Import Workspace UI

**Files:**
- Create: `web/src/components/vocabulary/VocabularyTextCapture.vue`
- Create: `web/src/components/vocabulary/VocabularyImageCapture.vue`
- Create: `web/src/components/vocabulary/VocabularyTermReview.vue`
- Create: `web/src/components/vocabulary/VocabularyThemeSelect.vue`
- Modify: `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`
- Modify: `web/tests/vocabularyThemeShelf.test.ts`

**Interfaces:**
- Consumes: Task 6 API mutation and candidate reducer/batch builder.
- Produces: one compact workspace with tabs, theme select, collapsed source context, text input, image input, review list, and `生成 N 张卡片`.
- Emits: existing `captured` event after every batch has a terminal result; successful batches are removed while failed batches remain editable.

- [ ] **Step 1: Update source-level UI contract tests and confirm RED**

Lock the page copy and component boundaries:

```typescript
test('collection page has one concise heading and the compact import workspace', () => {
  assert.match(view, /<h1>单词沉淀<\/h1>/)
  assert.doesNotMatch(view, /Word Cards|单词卡中心|更多来源后续接入/)
  assert.match(capture, /VocabularyTextCapture/)
  assert.match(capture, /VocabularyImageCapture/)
  assert.match(capture, /VocabularyTermReview/)
  assert.match(capture, /VocabularyThemeSelect/)
  assert.doesNotMatch(capture, /VocabularyThemeShelf/)
  assert.doesNotMatch(capture, /所选主题仅用于本次沉淀/)
})
```

Run:

```powershell
cd web
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyThemeShelf.test.ts
```

Expected: assertions fail against the old heading, shelf, and footer hint.

- [ ] **Step 2: Implement the theme select and text adapter**

`VocabularyThemeSelect` renders a labeled native select with active themes and a router link/button to `/app/vocabulary/themes`. Loading, blocking error, and empty states have distinct accessible text. `VocabularyTextCapture` renders the textarea and emits the parsed raw string without owning candidate state.

- [ ] **Step 3: Implement image selection, camera, preview, and latest-request-wins**

`VocabularyImageCapture` owns only the current `File`, object URL, active request ID, and AbortController. It emits a recognition response to the parent. The selected state displays the real preview and sanitized file name plus `开始识别`; the completed state displays total candidate count, unresolved typo count, `重新识别`, `更换图片`, and the collapsed raw-text disclosure.

```typescript
async function recognize() {
  if (!file.value) return
  controller?.abort()
  controller = new AbortController()
  const requestId = ++latestRequestId
  try {
    const response = await props.mutation.mutateAsync({ file: file.value, signal: controller.signal })
    if (requestId === latestRequestId) emit('recognized', { response, file: file.value })
  } catch (error) {
    if (requestId === latestRequestId && !isAbortError(error)) emit('failed', publicMessage(error))
  }
}
```

Revoke the old object URL on replacement and unmount. Use two hidden file inputs: standard upload and `capture="environment"`; both accept `image/jpeg,image/png,image/webp`. Keep preview dimensions stable with `aspect-ratio`, `object-fit: contain`, and a maximum height. Raw text uses a collapsed `<details>`. Retrying or recognizing a replacement merges the latest response into the parent candidate list by case-insensitive term without removing previously prepared manual candidates.

- [ ] **Step 4: Implement candidate review and explicit typo decisions**

Normal candidates show checkbox, editable term, and delete action. Suspected typos show observed text, suggestions with a visible `词典已验证` label when true, `采用`, `保留原词`, and `删除`. The component emits reducer commands and never mutates props. “全选” selects only resolved candidates; “清空” deselects all. Display the 30-candidate warning outside the scroll area.

- [ ] **Step 5: Recompose `VocabularyCapturePanel` as the single state owner**

The panel owns `mode`, `rawTerms`, `candidates`, `selectedThemeUid`, `sourceContext`, request IDs, outcomes, and error. Switching modes preserves candidates and cancels an in-flight image request when leaving image mode. Parse text candidates into the unified list without deleting OCR candidates. Submit batches sequentially and preserve failed batches:

```typescript
for (const batch of buildCaptureBatches(...)) {
  try {
    const response = await props.captureMutation.mutateAsync(batch.payload)
    outcomes.value.push(...response.items)
    if (isVocabularyCaptureComplete(response)) {
      removeCandidateIds(batch.candidateIds)
    } else {
      failed = true
    }
  } catch (error) {
    failed = true
    requestError.value = publicCaptureMessage(error)
  }
}
```

Button rules: disabled with zero selected ready terms, unresolved selected typos, missing theme, blocking theme error, capture pending, or recognition pending. Labels are `生成卡片`, `生成 N 张卡片`, and `生成中...`; do not include the theme name.

- [ ] **Step 6: Simplify the page heading and wire the feature flag/mutation**

Replace the collection header with only `<h1>单词沉淀</h1>`. Pass `imageRecognitionEnabled` and `imageRecognitionMutation` to the panel. Do not modify the card list, filters, pagination, or persistent card route.

- [ ] **Step 7: Run frontend tests and build**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyThemeShelf.test.ts tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts
npm run build
```

Expected: all tests and build pass; no text overflow/type errors.

- [ ] **Step 8: Commit**

```powershell
git add web/src/components/vocabulary/VocabularyTextCapture.vue web/src/components/vocabulary/VocabularyImageCapture.vue web/src/components/vocabulary/VocabularyTermReview.vue web/src/components/vocabulary/VocabularyThemeSelect.vue web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/views/VocabularyView.vue web/tests/vocabularyDepositionWorkspace.test.ts web/tests/vocabularyThemeShelf.test.ts
git commit -m "feat(ui): 重构单词沉淀导入工作区"
```

---

### Task 8: Record Privacy-Safe Funnel and Readiness Events

**Files:**
- Create: `backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyProductEvent.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyProductEventMapper.java`
- Create: `backend/src/main/resources/mapper/VocabularyProductEventMapper.xml`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/db/VocabularyProductEventSchemaTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventServiceTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizer.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularySourceMapper.java`
- Modify: `backend/src/main/resources/mapper/VocabularySourceMapper.xml`
- Create: `web/src/features/vocabulary/productEvents.ts`
- Create: `web/tests/vocabularyProductEvents.test.ts`
- Modify: `web/src/api/vocabulary.ts`
- Modify: `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- Modify: `web/src/views/VocabularyView.vue`

**Interfaces:**
- Produces: `POST /api/vocabulary/product-events/batch` with at most 50 idempotent events.
- Produces: allowed names `vocabulary_image_recognition_started`, `vocabulary_image_recognition_completed`, `vocabulary_image_candidates_confirmed`, `vocabulary_capture_submitted`, `vocabulary_cards_ready`, and `vocabulary_learning_started`.
- Produces: server-side capture and ready events; browser-side started/completed/confirmed/learning events.
- Consumes: recognition `traceId`, capture source metadata, generation job `sourceUid`, and card UID.

- [ ] **Step 1: Write failing schema and service privacy tests**

The table must contain `event_uid`, `user_id`, `event_name`, `trace_id`, `session_id`, `card_uid`, `properties_json`, `occurred_at`, and timestamps, with unique `(user_id, event_uid)` and indexes on `(event_name, occurred_at)`, `(trace_id, occurred_at)`, and `(card_uid, occurred_at)`.

Service tests must prove duplicate event IDs insert once, unknown names are rejected, max 50 is enforced, and forbidden property keys/values are rejected. Allow only scalar/short array properties and keys:

```text
sourceType, durationMs, candidateCount, suspectedCount, selectedCount,
editedCount, removedCount, resolutionCount, successCount, failedCount,
provider, model, promptVersion, modelCallCount, warningCodes, outcome
```

Forbidden keys include `fileName`, `term`, `observedText`, `contextText`, `rawText`, `content`, `markdown`, `image`, and `base64` case-insensitively.

- [ ] **Step 2: Run backend event tests and confirm RED**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyProductEventSchemaTest,VocabularyProductEventServiceTest test
```

Expected: tests fail because migration, mapper, and service do not exist.

- [ ] **Step 3: Implement idempotent event storage and batch endpoint**

Use `INSERT IGNORE` and return accepted/duplicate counts. The request shape is:

```java
public record VocabularyProductEventBatchRequest(
        @NotEmpty @Size(max = 50) List<@Valid Event> events) {
    public record Event(
            @NotBlank @Size(max = 128) String eventUid,
            @NotBlank @Size(max = 64) String eventName,
            @Size(max = 128) String traceId,
            @NotBlank @Size(max = 128) String sessionId,
            @Size(max = 64) String cardUid,
            @NotNull LocalDateTime occurredAt,
            Map<String, Object> properties) {}
}
```

The controller delegates authenticated batches to `VocabularyProductEventService.acceptBatch`.

- [ ] **Step 4: Record server-owned capture and ready events**

After `VocabularyCaptureService.capture` has a response, emit one `vocabulary_capture_submitted` event with source type and success/failure counts. For OCR, use `recognitionTraceId`; for manual control, use `clientRequestId` as trace. If a capture response item is already `ready` because it merged into an existing readable card, also emit its idempotent `vocabulary_cards_ready` event immediately. Use a `REQUIRES_NEW` best-effort event write and catch failures so analytics cannot roll back capture.

Add `VocabularySourceMapper.findBySourceUid`. In `VocabularyGenerationFinalizer`, after an AI revision becomes active `ready`, read `sourceUid` from the existing job request JSON, load that source, and emit idempotent `vocabulary_cards_ready` with `eventUid="vocabulary-cards-ready:" + revisionUid`, `cardUid`, source type, and source recognition trace. Event write failure logs a warning and must not roll back card finalization.

- [ ] **Step 5: Write failing frontend event tests**

Cover stable session ID, random event UID, payload allowlist, no sensitive values, completion duration, candidate confirmation counts, and once-per-card learning event.

Run:

```powershell
cd web
npx tsx --test tests/vocabularyProductEvents.test.ts
```

Expected: import failure for the missing event helper.

- [ ] **Step 6: Implement browser event reporting**

Store `vocabulary.productEventSessionId` in `sessionStorage`; keep a `Set` of card IDs already reported in the current page session. Post event batches best-effort and never block recognition/capture/navigation on analytics failure.

Emit:

- `started` immediately before mutation.
- `completed` on success or failure with duration/outcome and counts, without file name.
- `candidates_confirmed` immediately before the first OCR capture batch.
- `learning_started` once when a persisted card detail with readable content is first rendered.

- [ ] **Step 7: Run event tests and commit**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyProductEventSchemaTest,VocabularyProductEventServiceTest,VocabularyCaptureServiceTest,VocabularyGenerationFinalizerTest,VocabularyControllerTest test
cd ..\web
npx tsx --test tests/vocabularyProductEvents.test.ts tests/vocabularyImageRecognition.test.ts tests/vocabularyDepositionWorkspace.test.ts
npm run build
```

Expected: all tests and build pass.

Commit:

```powershell
git add backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql backend/src/main/resources/db/schema.sql backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyProductEvent.java backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyProductEventMapper.java backend/src/main/resources/mapper/VocabularyProductEventMapper.xml backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchRequest.java backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizer.java backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularySourceMapper.java backend/src/main/resources/mapper/VocabularySourceMapper.xml backend/src/test/java/com/personalenglishai/backend/db/VocabularyProductEventSchemaTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventServiceTest.java web/src/features/vocabulary/productEvents.ts web/src/api/vocabulary.ts web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/views/VocabularyView.vue web/tests/vocabularyProductEvents.test.ts
git commit -m "feat(vocabulary): 记录图片沉淀漏斗事件"
```

---

### Task 9: Complete Contract E2E, Documentation, Rollout, and Final Verification

**Files:**
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`
- Create: `docs/api/vocabulary.md`
- Create: `docs/ai/vocabulary-image-recognition.md`
- Modify: `docs/api/index.md`
- Modify: `docs/ai/index.md`
- Modify: `docs/architecture/vocabulary-deposition.md`
- Modify: `docs/runbooks/environment-variables.md`
- Modify: `docs/runbooks/local-dev.md`
- Modify: `.env.example`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: Tasks 1-8 complete vertical flow.
- Produces: mocked browser acceptance, real-model opt-in smoke instructions, deploy order, rollback, environment contract, latency/event verification, and current documentation links.

- [ ] **Step 1: Extend the E2E API mock for multipart recognition**

Handle `/api/vocabulary/image-recognitions` before any `postDataJSON()` call. Assert the request content type begins with `multipart/form-data` and return one accepted `package` item plus one suspected `recieve` item with verified `receive`. Continue handling `/api/vocabulary/captures` as JSON and store the request bodies for assertions.

- [ ] **Step 2: Add the complete image import E2E scenario**

The test must:

1. Enable the feature flag for the test server.
2. Open `?tab=collection`.
3. Assert only `单词沉淀` is present as the page heading.
4. Switch to `图片识别` by keyboard.
5. Select an in-memory `words.png` file with `page.setInputFiles`.
6. Start recognition and see `package` plus `recieve`.
7. Apply verified `receive`.
8. Select a theme, expand source context, and generate 2 cards.
9. Assert OCR capture source, per-item observed text/resolution, no raw text/base64, and existing duplicate merge behavior.
10. Replace the image while a delayed recognition is pending and prove the stale response does not render.

Use:

```typescript
await page.getByLabel('选择图片').setInputFiles({
  name: 'words.png',
  mimeType: 'image/png',
  buffer: Buffer.from('mock-image-bytes'),
})
```

- [ ] **Step 3: Update the existing text capture E2E selectors**

Replace shelf selection with `getByLabel('生成主题').selectOption(...)` and replace `按「产品英语」生成 2 张卡片` with exact `生成 2 张卡片`. Keep the original text-capture assertions so image work cannot regress manual capture.

- [ ] **Step 4: Ask for browser authorization, then run Chromium E2E and responsive checks**

After the user authorizes Chromium/Chrome for this verification run, execute:

```powershell
cd web
$env:VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED='true'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps
```

Expected: all vocabulary deposition scenarios pass. Inspect 1280x800 and 390x844 states for text mode, image preview, suspected typo, and 30-item warning; verify no overlap, clipped labels, horizontal scroll, or layout shift. Do not run this step without browser authorization.

- [ ] **Step 5: Write current API, AI, architecture, and runbook documentation**

Document exact request/response fields, warning/error codes, quota key, file limits, timeouts, Prompt behavior, model retry bound, no-image-persistence rule, `itemSources`, product event privacy, deployment order, and rollback. Add environment variables:

```text
VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=false
VOCABULARY_IMAGE_RECOGNITION_MODEL=<configured vision-capable model>
VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS=45000
VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS=55000
RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE=0
VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE=<local image path used only for opt-in smoke>
```

`docker-compose.yml` passes the model/timeout to Python and the base URL/timeout/shared token to Java. Keep the frontend flag false by default.

- [ ] **Step 6: Run deterministic full verification**

Run:

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py -q

cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-image-32-bytes'
mvn test

cd ..\web
npx tsx --test "tests/vocabulary*.test.ts"
npm run build

cd ..\docs
npm run build
```

Expected: Python deterministic tests pass with optional smoke skipped, Maven test suite passes, frontend vocabulary tests/build pass, and VitePress build succeeds with no dead links.

- [ ] **Step 7: Run opt-in real service smoke when credentials are available**

Start Python on `8011`, Java on an unused port against an explicitly named disposable MySQL schema, and the frontend with the feature flag enabled. Set the shared internal token and a vision-capable model. Run one word-list image, one typo note, and one unmarked paragraph. Verify one model call normally, at most two only on structured-output retry, P50/P95 logging fields, dictionary behavior, OCR source metadata, card readiness, event correlation, and absence of sensitive content in logs.

- [ ] **Step 8: Commit E2E and documentation**

```powershell
git add web/tests/vocabularyDepositionFlow.spec.ts docs/api/vocabulary.md docs/ai/vocabulary-image-recognition.md docs/api/index.md docs/ai/index.md docs/architecture/vocabulary-deposition.md docs/runbooks/environment-variables.md docs/runbooks/local-dev.md .env.example docker-compose.yml
git commit -m "docs(vocabulary): 补充图片导入部署验收"
```

- [ ] **Step 9: Review merge readiness**

Confirm `git status --short` is clean, every task commit exists, the frontend flag remains disabled by default, migrations are additive/idempotent, no secret or test image payload is committed, no unrelated work was modified, and the branch is suitable for review before merging to `main`.
