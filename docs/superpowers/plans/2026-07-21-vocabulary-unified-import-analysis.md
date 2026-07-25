# 统一单词导入分析稳定性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在统一文本/图片 AI 导入链路中实现请求竞态保护、三层超时预算和输入指纹一致性校验，确保旧结果不能覆盖或生成当前输入的卡片。

**Architecture:** 浏览器负责规范化输入、计算 SHA-256 指纹、取消旧请求并只接受最新响应；Java 公开接口重新计算指纹后再调用 Python，承担鉴权、配额、公开错误和 55 秒客户端超时；Python 使用结构化输出，在 45 秒总预算内完成一次调用和最多一次结构重试。旧图片识别接口继续保留一个发布周期，新前端只调用统一导入分析接口。

**Tech Stack:** Vue 3、TypeScript、Axios、TanStack Query、Web Crypto、Spring Boot、Java 17、WebClient、FastAPI、Pydantic、OpenAI Agents SDK、Node test runner、JUnit 5、pytest、Playwright

## Global Constraints

- 浏览器 Axios 硬超时固定为 `60_000 ms`。
- Java 到 Python HTTP 超时必须不超过 `55_000 ms`。
- Python workflow 总预算固定为 `45_000 ms`，包含最多一次结构重试。
- 输入指纹算法固定为 `SHA-256(normalizedText UTF-8 + 0x00 + imageBytes)`；文本统一 CRLF/CR 为 LF 后 `trim()`，无图片时仍包含零字节。
- 前端只有在“响应指纹 = 请求开始指纹 = 当前输入指纹”时才能启用生成卡片。
- 新分析、输入变化、图片变化和组件卸载都会取消旧请求并递增本地 `requestId`。
- 已取消请求不显示错误；迟到响应不得更新候选词。
- 首轮不新增移动端独立拍照按钮、不新增埋点体系、不扩展普通日志中的原文或图片内容。
- 旧 `/api/vocabulary/image-recognitions` 与 Python 对应接口保留，新前端不再调用。

---

### Task 1: Python 统一分析契约与 45 秒预算

**Files:**
- Create: `python/ai_orchestrator/schemas/vocabulary_import_analysis.py`
- Create: `python/ai_orchestrator/agents/vocabulary_import_analysis.py`
- Create: `python/ai_orchestrator/workflows/vocabulary_import_analysis.py`
- Create: `python/ai_orchestrator/services/vocabulary_import_analysis.py`
- Create: `python/ai_orchestrator/prompts/agent_instructions/vocabulary_import_analysis.md`
- Modify: `python/ai_orchestrator/prompts/agents.py`
- Modify: `python/ai_orchestrator/app.py`
- Test: `python/ai_orchestrator/tests/test_vocabulary_import_analysis_schemas.py`
- Test: `python/ai_orchestrator/tests/test_vocabulary_import_analysis_workflow.py`
- Test: `python/ai_orchestrator/tests/test_vocabulary_import_analysis_endpoint.py`

**Interfaces:**
- Consumes: multipart `text`, optional image bytes, `inputFingerprint`, `traceId` and internal bearer token.
- Produces: `VocabularyImportAnalysisResponse` with aliases `contractVersion`, `traceId`, `inputFingerprint`, `warnings`, `items`, and `generation`.
- Item fields: `itemId`, `observedText`, `normalizedTerm`, `status`, `suggestions`, `contextText`, and `evidence` where evidence is `text | image | text_image`.

- [ ] **Step 1: Write failing schema and workflow tests**

```python
def test_request_requires_text_or_image():
    with pytest.raises(ValidationError):
        VocabularyImportAnalysisRequest(
            traceId="trace-1",
            inputFingerprint="a" * 64,
            text="",
            imageBytes=None,
            mediaType=None,
        )


@pytest.mark.asyncio
async def test_workflow_retries_structure_once_within_single_deadline():
    runner = SequencedRunner([ValueError("invalid"), valid_model_output()])
    workflow = VocabularyImportAnalysisWorkflow(
        model="gpt-4.1-mini",
        timeout_seconds=45,
        runner=runner,
        clock=FakeClock([0, 5, 5]),
    )
    result = await workflow.analyze(valid_text_request())
    assert result.input_fingerprint == "a" * 64
    assert runner.call_count == 2
```

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m pytest python/ai_orchestrator/tests/test_vocabulary_import_analysis_schemas.py python/ai_orchestrator/tests/test_vocabulary_import_analysis_workflow.py -q`

Expected: collection fails because `vocabulary_import_analysis` modules do not exist.

- [ ] **Step 3: Implement strict schemas, prompt, agent and workflow**

```python
class VocabularyImportAnalysisRequest(StrictImportModel):
    trace_id: str = Field(alias="traceId", min_length=1, max_length=128)
    input_fingerprint: str = Field(alias="inputFingerprint", pattern=r"^[0-9a-f]{64}$")
    text: str = Field(default="", max_length=20_000)
    image_bytes: bytes | None = Field(default=None, alias="imageBytes", exclude=True)
    media_type: Literal["image/jpeg", "image/png", "image/webp"] | None = Field(default=None, alias="mediaType")

    @model_validator(mode="after")
    def require_input(self) -> "VocabularyImportAnalysisRequest":
        if not self.text.strip() and not self.image_bytes:
            raise ValueError("text or image is required")
        return self
```

The workflow must compute one deadline before the first model call, pass the remaining time to each call, retry only structured validation once, preserve the request fingerprint unchanged, and map deadline exhaustion to `MODEL_TIMEOUT` with `retryable=True`.

- [ ] **Step 4: Add the internal FastAPI endpoint**

```python
@app.post(
    "/internal/v1/vocabulary/import-analyses",
    response_model=VocabularyImportAnalysisResponse,
    dependencies=[Depends(_require_vocabulary_import_analysis_internal_token)],
)
async def analyze_vocabulary_import(
    trace_id: Annotated[str, Form(alias="traceId")],
    input_fingerprint: Annotated[str, Form(alias="inputFingerprint")],
    text: Annotated[str, Form()] = "",
    file: UploadFile | None = File(default=None),
) -> VocabularyImportAnalysisResponse:
    request = VocabularyImportAnalysisRequest(
        traceId=trace_id,
        inputFingerprint=input_fingerprint,
        text=text,
        imageBytes=await file.read() if file else None,
        mediaType=file.content_type if file else None,
    )
    return await vocabulary_import_analysis_service.analyze(request)
```

- [ ] **Step 5: Run Python tests and verify GREEN**

Run: `python -m pytest python/ai_orchestrator/tests/test_vocabulary_import_analysis_schemas.py python/ai_orchestrator/tests/test_vocabulary_import_analysis_workflow.py python/ai_orchestrator/tests/test_vocabulary_import_analysis_endpoint.py -q`

Expected: all selected tests pass and the timeout test proves no third model call occurs.

- [ ] **Step 6: Commit Python capability**

```powershell
git add python/ai_orchestrator
git commit -m "feat(prompt): 增加统一单词导入分析"
```

---

### Task 2: Java 指纹校验、公开接口与 55 秒超时

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImportAnalysisResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportFingerprint.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisPythonResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisPythonClient.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisException.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportFingerprintTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisPythonClientTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImportAnalysisServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/vocabulary/import-analyses` multipart fields `text`, `file`, `inputFingerprint`.
- Produces: the Python response after Java dictionary verification, with Java-verified `inputFingerprint`.
- Rejects: fingerprint mismatch before quota reservation or Python call with HTTP 400 and code `VOCABULARY_IMPORT_FINGERPRINT_MISMATCH`.

- [ ] **Step 1: Write failing fingerprint tests**

```java
@Test
void normalizes_line_endings_and_includes_image_separator() {
    assertEquals(
            VocabularyImportFingerprint.calculate("  one\r\ntwo  ", new byte[] {1, 2}),
            VocabularyImportFingerprint.calculate("one\ntwo", new byte[] {1, 2}));
    assertNotEquals(
            VocabularyImportFingerprint.calculate("one", null),
            VocabularyImportFingerprint.calculate("one", new byte[] {0}));
}
```

- [ ] **Step 2: Run fingerprint test and verify RED**

Run: `mvn -Dtest=VocabularyImportFingerprintTest test`

Expected: compilation fails because `VocabularyImportFingerprint` does not exist.

- [ ] **Step 3: Implement the shared Java fingerprint helper**

```java
public static String calculate(String text, byte[] imageBytes) {
    String normalized = Objects.requireNonNullElse(text, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(normalized.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    if (imageBytes != null) digest.update(imageBytes);
    return HexFormat.of().formatHex(digest.digest());
}
```

- [ ] **Step 4: Write failing service and client tests**

The service test must verify a mismatched fingerprint throws before `subscriptionService.requireAiQuota(...)` and before `pythonClient.analyze(...)`. The client test must verify multipart field names, internal endpoint URL, bearer token and constructor rejection for `Duration.ofMillis(55_001)`.

- [ ] **Step 5: Run service/client tests and verify RED**

Run: `mvn -Dtest=VocabularyImportAnalysisServiceTest,VocabularyImportAnalysisPythonClientTest test`

Expected: compilation fails because the unified service and client do not exist.

- [ ] **Step 6: Implement DTO, client and service**

```java
public VocabularyImportAnalysisResponse analyze(
        long userId, String text, MultipartFile file, String suppliedFingerprint) {
    byte[] imageBytes = readAndValidate(file);
    String verifiedFingerprint = VocabularyImportFingerprint.calculate(text, imageBytes);
    if (!MessageDigest.isEqual(
            verifiedFingerprint.getBytes(StandardCharsets.US_ASCII),
            suppliedFingerprint.getBytes(StandardCharsets.US_ASCII))) {
        throw new VocabularyImportAnalysisException(
                "VOCABULARY_IMPORT_FINGERPRINT_MISMATCH", false);
    }
    subscriptionService.requireAiQuota(userId, "vocabulary.import_analysis");
    return verifyDictionaryAndMap(pythonClient.analyze(
            traceIdFactory.create(), text, imageBytes, contentType(file), verifiedFingerprint));
}
```

The WebClient request must call `/internal/v1/vocabulary/import-analyses`, set the existing internal bearer token, use a configurable timeout defaulting to `55s`, and map timeout to a stable retryable public error.

- [ ] **Step 7: Add controller mapping and public error handling**

```java
@PostMapping(value = "/import-analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<VocabularyImportAnalysisResponse> analyzeImport(
        @RequestParam(defaultValue = "") String text,
        @RequestPart(required = false) MultipartFile file,
        @RequestParam String inputFingerprint) {
    return ApiResponse.ok(importAnalysisService.analyze(
            requireUserId(), text, file, inputFingerprint));
}
```

- [ ] **Step 8: Run targeted Java tests and verify GREEN**

Run: `mvn -Dtest=VocabularyImportFingerprintTest,VocabularyImportAnalysisPythonClientTest,VocabularyImportAnalysisServiceTest,VocabularyControllerTest,GlobalExceptionHandlerTest test`

Expected: all selected tests pass, including “mismatch does not consume quota or call Python”.

- [ ] **Step 9: Commit Java bridge**

```powershell
git add backend/src/main backend/src/test
git commit -m "feat(api): 增加统一单词导入分析接口"
```

---

### Task 3: 前端输入指纹与 latest-wins 生命周期

**Files:**
- Create: `web/src/features/vocabulary/importAnalysis.ts`
- Modify: `web/src/api/vocabulary.ts`
- Test: `web/tests/vocabularyImportAnalysis.test.ts`

**Interfaces:**
- Produces: `calculateVocabularyImportFingerprint(text: string, file: File | null): Promise<string>`.
- Produces: `createImportAnalysisLifecycle()` with `begin(fingerprint)`, `isCurrent(requestId, responseFingerprint, currentFingerprint)`, and `invalidate()`.
- API: `analyzeVocabularyImport({ text, file, inputFingerprint, signal })` with 60 second timeout.

- [ ] **Step 1: Write failing frontend unit tests**

```typescript
test('normalizes text and includes the image separator in the SHA-256 fingerprint', async () => {
  const a = await calculateVocabularyImportFingerprint('  one\r\ntwo  ', null)
  const b = await calculateVocabularyImportFingerprint('one\ntwo', null)
  assert.equal(a, b)
  assert.match(a, /^[0-9a-f]{64}$/)
})

test('a late request is rejected after new input invalidates the lifecycle', () => {
  const lifecycle = createImportAnalysisLifecycle()
  const first = lifecycle.begin('a'.repeat(64))
  lifecycle.invalidate()
  assert.equal(lifecycle.isCurrent(first.requestId, 'a'.repeat(64), 'a'.repeat(64)), false)
  assert.equal(first.signal.aborted, true)
})
```

- [ ] **Step 2: Run frontend test and verify RED**

Run: `node --test --experimental-strip-types web/tests/vocabularyImportAnalysis.test.ts`

Expected: module import fails because `importAnalysis.ts` does not exist.

- [ ] **Step 3: Implement fingerprint, lifecycle and API request**

```typescript
export async function calculateVocabularyImportFingerprint(text: string, file: File | null) {
  const normalized = text.replace(/\r\n?/gu, '\n').trim()
  const textBytes = new TextEncoder().encode(normalized)
  const imageBytes = file ? new Uint8Array(await file.arrayBuffer()) : new Uint8Array()
  const payload = new Uint8Array(textBytes.length + 1 + imageBytes.length)
  payload.set(textBytes)
  payload.set(imageBytes, textBytes.length + 1)
  const digest = await crypto.subtle.digest('SHA-256', payload)
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}
```

The lifecycle must abort the previous controller before returning a new request, increment `requestId` on every begin/invalidate, and only return true when request id and all three fingerprints match.

- [ ] **Step 4: Run frontend unit test and verify GREEN**

Run: `node --test --experimental-strip-types web/tests/vocabularyImportAnalysis.test.ts`

Expected: all tests pass, including API URL, multipart fields, caller signal and `60_000` timeout assertions.

- [ ] **Step 5: Commit frontend primitives**

```powershell
git add web/src/api/vocabulary.ts web/src/features/vocabulary/importAnalysis.ts web/tests/vocabularyImportAnalysis.test.ts
git commit -m "feat(ui): 增加导入分析一致性控制"
```

---

### Task 4: 统一导入对话框的过期结果与超时交互

**Files:**
- Create: `web/src/components/vocabulary/VocabularyImportDialog.vue`
- Create: `web/src/components/vocabulary/VocabularyImportComposer.vue`
- Modify: `web/src/components/vocabulary/VocabularyCapturePanel.vue`
- Modify: `web/src/components/vocabulary/VocabularyTermReview.vue`
- Modify: `web/src/features/vocabulary/imageRecognition.ts`
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/tests/vocabularyThemeShelf.test.ts`
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`

**Interfaces:**
- `VocabularyImportDialog` owns text, image, candidates, analysis status, request fingerprint and latest successful fingerprint.
- Emits existing capture batches only when selected candidates are ready and fingerprints match.
- Candidate sort is presentation-only: default `input`, optional `alphabetical`, with stable ordering and unchanged candidate IDs.

- [ ] **Step 1: Write failing static/component contract tests**

Add assertions that the capture panel renders a compact “导入单词” entry, the dialog has one text/image composer and explicit “AI 分析”, mode tabs and source-context controls are absent, stale copy exists, and generation is bound to `canGenerateFromCurrentAnalysis`.

- [ ] **Step 2: Run contract tests and verify RED**

Run: `node --test --experimental-strip-types web/tests/vocabularyThemeShelf.test.ts web/tests/vocabularyDepositionWorkspace.test.ts`

Expected: assertions fail because the inline two-mode capture workspace still exists.

- [ ] **Step 3: Implement the dialog state machine**

```typescript
type AnalysisState = 'idle' | 'analyzing' | 'ready' | 'stale' | 'failed'

const canGenerateFromCurrentAnalysis = computed(() =>
  analysisState.value === 'ready'
  && Boolean(lastSuccessfulFingerprint.value)
  && lastSuccessfulFingerprint.value === currentFingerprint.value
  && selectedReadyCandidates(candidates.value).length > 0,
)
```

On any text/image mutation: call `lifecycle.invalidate()`, recompute current fingerprint, keep candidates visible, set `stale` when candidates exist, and disable generation. On submit: capture the request-start fingerprint, begin lifecycle, show `分析中…`, ignore aborted errors, accept only `lifecycle.isCurrent(...)`, then replace candidates and set `ready`.

- [ ] **Step 4: Implement stable candidate sorting**

```typescript
export function sortImportCandidates(
  candidates: readonly ImportCandidate[],
  mode: 'input' | 'alphabetical',
): ImportCandidate[] {
  if (mode === 'input') return [...candidates]
  return candidates
    .map((candidate, index) => ({ candidate, index }))
    .sort((a, b) => a.candidate.term.localeCompare(b.candidate.term, 'en', { sensitivity: 'base' }) || a.index - b.index)
    .map(({ candidate }) => candidate)
}
```

- [ ] **Step 5: Run frontend tests and build**

Run: `node --test --experimental-strip-types web/tests/vocabularyImportAnalysis.test.ts web/tests/vocabularyImageRecognition.test.ts web/tests/vocabularyThemeShelf.test.ts web/tests/vocabularyDepositionWorkspace.test.ts`

Run: `npm run build`

Expected: tests pass and Vite production build completes without TypeScript errors.

- [ ] **Step 6: Commit dialog integration**

```powershell
git add web/src web/tests
git commit -m "feat(ui): 接入统一单词导入对话框"
```

---

### Task 5: 跨层回归、文档和浏览器验收

**Files:**
- Modify: `.env.example`
- Modify: `docs/vocabulary-deposition-api.md`
- Modify: `docs/vocabulary-deposition-acceptance-checklist.md`
- Modify: `backend/src/test/java/com/personalenglishai/backend/docs/VocabularyDepositionDocsTest.java`
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`

**Interfaces:**
- Config: `VITE_VOCABULARY_IMPORT_ANALYSIS_ENABLED`, `VOCABULARY_IMPORT_ANALYSIS_MODEL`, `VOCABULARY_IMPORT_ANALYSIS_TIMEOUT_MS=45000`, `VOCABULARY_IMPORT_ANALYSIS_PYTHON_TIMEOUT_MS=55000`.
- Browser acceptance: request race, 60 second timeout UI, stale fingerprint guard and successful generation from current analysis.

- [ ] **Step 1: Write failing E2E cases**

The mocked API must delay request A, return request B first, and assert B remains visible after A resolves. A second case edits input after successful analysis and asserts “输入已变化，请重新分析” appears and “生成卡片” is disabled. A timeout case returns 504 and asserts input/file remain available for explicit retry.

- [ ] **Step 2: Run targeted E2E and verify RED**

Run: `npx playwright test web/tests/vocabularyDepositionFlow.spec.ts --project=chromium --grep "统一导入分析"`

Expected: new scenarios fail before final route mocks and UI state are connected.

- [ ] **Step 3: Update environment and API documentation**

Document the public/internal endpoints, exact multipart fields, fingerprint algorithm, three timeout budgets, stable errors and one-release compatibility window. Update docs tests to assert those exact names and values.

- [ ] **Step 4: Run all targeted suites**

Run: `python -m pytest python/ai_orchestrator/tests/test_vocabulary_import_analysis_schemas.py python/ai_orchestrator/tests/test_vocabulary_import_analysis_workflow.py python/ai_orchestrator/tests/test_vocabulary_import_analysis_endpoint.py -q`

Run: `mvn -Dtest=VocabularyImportFingerprintTest,VocabularyImportAnalysisPythonClientTest,VocabularyImportAnalysisServiceTest,VocabularyControllerTest,GlobalExceptionHandlerTest,VocabularyDepositionDocsTest test`

Run: `node --test --experimental-strip-types web/tests/vocabularyImportAnalysis.test.ts web/tests/vocabularyImageRecognition.test.ts web/tests/vocabularyThemeShelf.test.ts web/tests/vocabularyDepositionWorkspace.test.ts`

Run: `npm run build`

Run: `npx playwright test web/tests/vocabularyDepositionFlow.spec.ts --project=chromium --grep "统一导入分析"`

Expected: all commands exit 0 with no failed tests.

- [ ] **Step 5: Perform visual QA in the user-selected in-app browser**

At desktop and mobile widths verify: no layout overlap, dialog retains text/image on failure, “分析中…” is visible, stale results cannot generate, latest result wins, and the normal successful flow creates cards. Compare the implementation against the approved unified composer visual rather than introducing a new visual direction.

- [ ] **Step 6: Commit docs and acceptance coverage**

```powershell
git add .env.example docs backend/src/test/java/com/personalenglishai/backend/docs web/tests/vocabularyDepositionFlow.spec.ts
git commit -m "test(ui): 覆盖导入分析稳定性场景"
```

---

## Completion Criteria

- A second analysis or any input mutation aborts the previous request and makes its response inert.
- A cancelled request never shows a failure message.
- Browser, Java and Python timeouts are exactly 60s, 55s and 45s.
- Java rejects a mismatched fingerprint before quota or Python invocation.
- Editing text or image after analysis visibly marks candidates stale and disables generation.
- Only current, selected, resolved candidates are captured under the chosen theme.
- Old image recognition endpoints remain operational for compatibility.
- Python targeted tests, Java targeted tests, frontend unit tests, build and focused Chromium E2E all pass.
