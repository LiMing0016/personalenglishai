# 单词图片导入工作区实施计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐项执行本计划。各步骤使用复选框（`- [ ]`）跟踪。

**目标：** 构建一个紧凑的单词沉淀工作区，支持输入单词或拍摄/上传一张图片；通过 Python 视觉工作流提取并标记候选词，要求用户明确处理疑似拼写错误，并将确认后的词条连同可审计的逐词来源一起沉淀。

**架构：** 浏览器只保存页面会话级草稿状态，并将图片发送到需要鉴权的 Java multipart 接口。Java 负责执行用户配额和文件策略，调用独立且需要鉴权的 FastAPI 接口，重新校验严格响应，仅通过现有词典核验疑似拼写错误，并返回增强后的公共契约。确认后的候选词继续复用现有 `/api/vocabulary/captures` 链路，该链路以增量方式扩展逐词来源覆盖能力；Python 负责版本化 Prompt、单次模型调用的视觉工作流、结构化输出、数据清洗和模型追踪。

**技术栈：** Vue 3、TypeScript 5.5、TanStack Vue Query 5、Axios、使用 `tsx` 的 Node 测试运行器、Playwright 1.58、Java 17、Spring Boot 3.2、WebClient、MyBatis、MySQL 8、JUnit 5、Mockito、Python 3.11、FastAPI 0.115、Pydantic、OpenAI Agents SDK 0.18.3、pytest 9、VitePress。

## 全局约束

- 以 `docs/superpowers/specs/2026-07-21-vocabulary-image-import-workspace-design.md` 作为唯一事实来源。
- 沉淀页标题必须严格为 `单词沉淀`；移除 `WORD CARDS`、`单词卡中心` 和现有的长副标题。
- 导入工作区严格包含两种模式：`文本录入` 和 `图片识别`；仅当 `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=true` 时显示图片模式。
- 只接受一张非空 JPG、PNG 或 WEBP 图片，大小上限为 10 MiB（`10 * 1024 * 1024` 字节）。
- 公共接口严格为 `POST /api/vocabulary/image-recognitions`，multipart 字段为 `file`。
- 内部接口严格为 `POST /internal/v1/vocabulary/image-recognitions`，multipart 字段为 `contractVersion=1`、`traceId`、`language=en` 和 `file`。
- Java 到 Python 的 Bearer 鉴权复用 `VOCABULARY_GENERATION_INTERNAL_TOKEN`；不得暴露或记录该令牌。
- Python 使用 `VOCABULARY_IMAGE_RECOGNITION_MODEL`；浏览器不得传入模型名称、服务商、Prompt 或 API Key。
- Prompt 版本严格为 `vocabulary-image-recognition-v1`。
- Python 最多返回 30 个候选词，每个疑似拼写错误最多返回 3 个建议。
- Python 内部建议保持为 `string[]`；Java 公共建议为 `{ term: string, dictionaryVerified: boolean }[]`。
- 顶层警告仅允许 `CANDIDATE_LIMIT_REACHED` 和 `DICTIONARY_VERIFICATION_UNAVAILABLE`。
- 图片识别不得调用 PaddleOCR、手写识别、Assistant 路由、聊天会话或单词卡生成工作流。
- 不得持久化图片字节、图片 base64、完整 `rawText`、模型原始响应或 Prompt 文本。
- 不得静默纠正疑似拼写错误；未处理的疑似错误必须阻止沉淀。
- 浏览器超时为 60 秒，Java 到 Python 超时为 55 秒，Python 模型调用预算为 45 秒。
- AI 配额操作键使用 `vocabulary.image_recognition`；图片识别与逐词卡片生成继续记录为两次独立用量。
- `VocabularyCaptureRequest.itemSources` 一旦存在，长度必须与 `terms` 完全一致；文本沉淀不传该字段。
- `ocr_image` 来源元数据只能包含 `recognitionTraceId`、`fileName`、`provider`、`model`、`promptVersion`、`observedText` 和 `resolution`。
- `resolution` 只能是 `accepted`、`suggestion_applied` 或 `original_kept`。
- 导入状态保留在 `VocabularyCapturePanel` 中；不得新增 Pinia store 或图片持久化。
- 不得新增前端图标依赖。上传、拍照、重试、替换、全选和清空沿用现有视觉语言与文本命令。
- 产品事件不得包含文件名、词条、识别原文、上下文文本、完整识别文本、卡片 Markdown 或图片数据。
- 在用户明确授权本次验证所使用的浏览器前，不得运行 Playwright 或浏览器自动化。
- 先部署 Python 和 Java，再启用前端功能开关；回滚方式是关闭 `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED`。

---

## 文件职责映射

- `python/ai_orchestrator/schemas/vocabulary_image_recognition.py`：严格定义模型输出、内部响应、警告、用量和请求值对象。
- `python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md`：版本化的提取与拼写错误识别 Prompt 资产。
- `python/ai_orchestrator/agents/vocabulary_image_recognition.py`：类型化视觉能力的 Agent 工厂。
- `python/ai_orchestrator/workflows/vocabulary_image_recognition.py`：单次调用视觉编排、有限重试、规范化、去重、截断、用量和安全追踪元数据。
- `python/ai_orchestrator/services/vocabulary_image_recognition.py`：环境配置与应用服务边界。
- `python/ai_orchestrator/app.py`：鉴权 multipart 适配器、文件边界、健康状态和稳定的 HTTP 错误映射。
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java`：类型化的 Java 到 Python multipart 客户端与传输错误映射。
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java`：公共用例、配额、文件校验、用量记录、词典核验、日志和公共响应组装。
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java`：面向浏览器的增强识别契约。
- `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`：增量扩展的 `ocr_image` 与逐项来源契约。
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`：按索引合并来源，不改变卡片标识、任务或版本行为。
- `web/src/features/vocabulary/imageRecognition.ts`：文件策略、候选词 reducer、去重、拼写决策、沉淀批次构建和功能开关。
- `web/src/components/vocabulary/VocabularyCapturePanel.vue`：导入模式、主题、语境、候选词、识别和分组沉淀提交的唯一状态来源。
- `web/src/components/vocabulary/VocabularyTextCapture.vue`：文本输入适配器。
- `web/src/components/vocabulary/VocabularyImageCapture.vue`：文件/相机输入、预览、识别生命周期、原文展开、重试和替换。
- `web/src/components/vocabulary/VocabularyTermReview.vue`：可编辑的已选候选词与明确的拼写错误处理。
- `web/src/components/vocabulary/VocabularyThemeSelect.vue`：紧凑型主题选择器与管理入口。
- `backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql`：为度量漏斗提供幂等事件持久化。
- `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java`：事件白名单、隐私边界、幂等性及服务端沉淀/卡片就绪事件。
- `web/src/features/vocabulary/productEvents.ts`：页面会话事件 ID 与浏览器批量上报。
- `docs/api/vocabulary.md`、`docs/ai/vocabulary-image-recognition.md`、`docs/architecture/vocabulary-deposition.md` 和 `docs/runbooks/environment-variables.md`：长期维护的 API、Prompt、架构、发布与冒烟测试文档。

---

### 任务 1：锁定 Python 识别 Schema 与 Prompt 资产

**文件：**
- 新建：`python/ai_orchestrator/schemas/vocabulary_image_recognition.py`
- 新建：`python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md`
- 新建：`python/ai_orchestrator/agents/vocabulary_image_recognition.py`
- 修改：`python/ai_orchestrator/prompts/agents.py`
- 新建：`python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py`
- 新建：`python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py`

**接口：**
- 产出：`VocabularyImageRecognitionRequest`、`VocabularyImageRecognitionModelOutput`、`VocabularyImageRecognitionResponse`、`VocabularyImageRecognitionItem`、`VocabularyImageRecognitionGeneration` 和 `build_vocabulary_image_recognition_agent(model: str) -> Agent`。
- 产出：常量 `PROMPT_VERSION = "vocabulary-image-recognition-v1"`、`MAX_CANDIDATES = 30`、`MAX_IMAGE_BYTES = 10 * 1024 * 1024` 和 `MAX_MODEL_CALLS = 2`。
- 依赖：现有 Prompt 解析器的 `resolve_agent_prompt_kwargs` 和 OpenAI Agents SDK 的 `Agent`。

- [ ] **步骤 1：编写预期失败的严格 Schema 测试**

编写测试，证明别名序列化结果完全准确、未知字段会导致失败、`confidence` 有明确边界、疑似拼写错误必须包含 1 至 3 个建议、已接受词条不得包含建议、响应追踪 ID 必须一致，并且警告列表拒绝未知值：

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

- [ ] **步骤 2：运行 Schema 测试并确认处于 RED 阶段**

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py -q
```

预期：测试收集失败，因为 `python.ai_orchestrator.schemas.vocabulary_image_recognition` 尚不存在。

- [ ] **步骤 3：实现严格 Schema 与常量**

使用严格的 Pydantic 模型，并将面向模型的条目与响应条目分离，使 `itemId` 成为确定性的应用层输出：

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

补全响应条目、生成元数据、可空用量、严格警告列表、不透明追踪 ID 校验，以及要求 `generation.traceId == traceId` 的响应模型校验器。

- [ ] **步骤 4：编写并运行预期失败的 Agent/Prompt 测试**

测试必须断言 Prompt 包含三项提取优先级、可见证据规则、禁止静默纠错、禁止生成释义、禁止 Markdown，以及 30 个候选词/3 个建议的上限。替换解析器并断言 Agent 使用 `VocabularyImageRecognitionModelOutput` 作为 `output_type`。同时断言 `prompts/agents.py` 已在 `_PROMPT_FILES`、`_STRUCTURED_OUTPUT_ONLY_AGENT_KEYS` 和 `_BACKGROUND_JOB_AGENT_KEYS` 中注册 `vocabulary_image_recognition`，确保该 Prompt 不会接收聊天交接或 Markdown 策略。

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py -q
```

预期：在 Prompt 资产和工厂实现前测试失败。

- [ ] **步骤 5：实现 Prompt 资产与类型化 Agent 工厂**

Prompt 必须明确包含 `Goal`、`Extraction order`、`Spelling policy`、`Output` 和 `Prohibitions` 五个章节。工厂实现如下：

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

- [ ] **步骤 6：运行聚焦测试并提交**

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py -q
```

预期：所有测试通过。

提交：

```powershell
git add python/ai_orchestrator/schemas/vocabulary_image_recognition.py python/ai_orchestrator/prompts/agent_instructions/vocabulary_image_recognition.md python/ai_orchestrator/prompts/agents.py python/ai_orchestrator/agents/vocabulary_image_recognition.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py
git commit -m "feat(python): 新增单词图片识别结构化契约"
```

---

### 任务 2：实现 Python 视觉工作流与内部 Multipart 接口

**文件：**
- 新建：`python/ai_orchestrator/workflows/vocabulary_image_recognition.py`
- 新建：`python/ai_orchestrator/services/vocabulary_image_recognition.py`
- 新建：`python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py`
- 新建：`python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py`
- 新建：`python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py`
- 修改：`python/ai_orchestrator/app.py`

**接口：**
- 依赖：任务 1 的 Schema、Prompt、Agent 工厂，以及现有的 `build_input_items` 和 `extract_usage`。
- 产出：`VocabularyImageRecognitionService.recognize(request: VocabularyImageRecognitionRequest) -> VocabularyImageRecognitionResponse`。
- 产出：`POST /internal/v1/vocabulary/image-recognitions` 和健康检查键 `vocabularyImageRecognitionConfigured`。
- 产出：稳定的服务错误码 `INVALID_IMAGE_REQUEST`、`UNSUPPORTED_IMAGE_TYPE`、`IMAGE_TOO_LARGE`、`IMAGE_RECOGNITION_NOT_CONFIGURED`、`MODEL_OUTPUT_INVALID`、`MODEL_UPSTREAM_UNAVAILABLE` 和 `MODEL_TIMEOUT`。

- [ ] **步骤 1：编写预期失败的工作流测试**

替换 `agents.Runner.run`，覆盖单次模型调用、图片 data URL 输入、稳定去重、边界标点规范化、确定性的 `item-1` ID、超过 30 项时截断并发出警告、用量提取、空结果、Schema 失败后仅重试一次、超时和取消传播：

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

- [ ] **步骤 2：运行工作流测试并确认处于 RED 阶段**

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py -q
```

预期：因工作流尚不存在而导入失败。

- [ ] **步骤 3：实现确定性的工作流编排**

创建不可变请求，包含 `trace_id`、`language`、`file_name`、`content_type` 和 `content`。使用一条文本指令和一个 `input_image` data URL 构建 Agents SDK 输入。使用 `RunConfig(workflow_name="Vocabulary Image Recognition", trace_include_sensitive_data=False, trace_metadata={"trace_id": trace_id})`。

核心规范化与重试结构如下：

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

传输、超时和限流异常必须映射为稳定错误，且不得包含服务商消息。运行时日志只能包含追踪 ID、字节数、候选词数量、疑似错误数量、调用次数、服务商、模型、Prompt 版本、耗时毫秒数和稳定错误码。

- [ ] **步骤 4：编写预期失败的服务与接口测试**

使用 FastAPI `TestClient`，覆盖 Bearer Token 缺失或错误、模型配置缺失、multipart 别名严格匹配、空文件、错误 MIME、扩展名与 MIME 不一致、10 MiB 边界、文件超限、成功响应、空条目、502 Schema 错误、503 服务商错误和 504 超时。验证响应与日志文本绝不包含测试图片标记。

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py -q
```

预期：由于接口和服务尚不存在，测试失败。

- [ ] **步骤 5：实现服务配置与内部接口**

读取 `VOCABULARY_IMAGE_RECOGNITION_MODEL`、`VOCABULARY_GENERATION_INTERNAL_TOKEN` 和 `VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS`，后者默认值为 `45000`。拒绝 `1..45000` 范围之外的超时配置。

新增接口适配器：

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

将无效输入映射为 400/422，将模型输出失败映射为 502，将不可用/未配置映射为 503，将超时映射为 504。不得返回异常详情。

- [ ] **步骤 6：新增按需启用的真实模型冒烟测试**

除非同时满足以下四个条件，否则跳过该测试：`RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE=1`、`OPENAI_API_KEY`、`VOCABULARY_IMAGE_RECOGNITION_MODEL`，以及指向本地现有 JPG/PNG/WEBP 文件的 `VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE`。断言契约、追踪信息、最多 30 个条目、Prompt 版本和调用次数，且不得打印文件路径或输出文本。

- [ ] **步骤 7：运行 Python 测试并提交**

运行：

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py -q
```

预期：确定性测试通过；除非明确启用，真实模型冒烟测试显示为跳过。

提交：

```powershell
git add python/ai_orchestrator/app.py python/ai_orchestrator/workflows/vocabulary_image_recognition.py python/ai_orchestrator/services/vocabulary_image_recognition.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py
git commit -m "feat(python): 接入单词图片识别工作流"
```

---

### 任务 3：构建严格的 Java 到 Python Multipart 客户端

**文件：**
- 新建：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonResponse.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionException.java`
- 新建：`backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClientTest.java`
- 修改：`backend/src/main/resources/application.yml`

**接口：**
- 依赖：任务 2 的 Python multipart 接口及严格的内部 JSON 契约。
- 产出：`VocabularyImageRecognitionPythonClient.recognize(String traceId, MultipartFile file) -> VocabularyImageRecognitionPythonResponse`。
- 产出：配置键 `vocabulary.image-recognition.python.base-url`、`.internal-token` 和 `.timeout-ms`，分别映射编排服务 URL、`VOCABULARY_GENERATION_INTERNAL_TOKEN` 和 `VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS=55000`。

- [ ] **步骤 1：编写预期失败的客户端契约测试**

复用 `VocabularyGenerationPythonClientTest` 中现有的捕获型 `ExchangeFunction` 模式。断言路径、Bearer Token、multipart 部件、55 秒上限、严格字段集合、追踪信息一致、警告白名单、用量解析和状态映射。公共客户端错误码如下：

```java
PYTHON_IMAGE_REQUEST_REJECTED
PYTHON_IMAGE_AUTH_FAILED
PYTHON_IMAGE_OUTPUT_INVALID
PYTHON_IMAGE_NOT_CONFIGURED
PYTHON_IMAGE_UPSTREAM_UNAVAILABLE
PYTHON_IMAGE_TIMEOUT
PYTHON_IMAGE_TRANSPORT_FAILED
```

400/422 响应不可重试，401/403 是不可重试的基础设施错误，502 表示输出无效，503 表示不可用/未配置，504 或客户端超时表示超时。消息中不得包含响应正文。

- [ ] **步骤 2：运行测试并确认处于 RED 阶段**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionPythonClientTest test
```

预期：测试编译失败，因为客户端类型尚不存在。

- [ ] **步骤 3：实现严格的响应 record**

参照 `VocabularyGenerationPythonResponse`，使用带紧凑构造器和严格 JSON 字段校验的 record。内部类型如下：

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

强制契约版本为 1、最多 30 个条目、最多 3 个建议、原始文本最多 20,000 字符、状态与警告必须已知、置信度为 `0..1`、追踪信息一致、服务商/模型/Prompt 非空、调用次数为 `1..2`，且可空用量一旦存在必须非负。

- [ ] **步骤 4：实现 multipart 客户端**

使用字符串字段和 `ByteArrayResource` 构建 `MultipartBodyBuilder`，其中 `getFilename()` 返回清洗后的原始文件名。向 `/internal/v1/vocabulary/image-recognitions` 发起 POST 请求，设置 Bearer 鉴权，调用 `.timeout(Duration.ofMillis(55000))`，通过严格响应工厂解析，并拒绝追踪信息与请求不一致的响应。内部警告解析器只接受 `CANDIDATE_LIMIT_REACHED`；`DICTIONARY_VERIFICATION_UNAVAILABLE` 只能由 Java 公共服务添加。

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

- [ ] **步骤 5：运行测试并提交**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionPythonClientTest test
```

预期：所有客户端测试通过。

提交：

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClient.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionException.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionPythonClientTest.java backend/src/main/resources/application.yml
git commit -m "feat(api): 新增图片识别 Python 客户端"
```

---

### 任务 4：提供 Java 公共识别用例

**文件：**
- 新建：`backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java`
- 新建：`backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionServiceTest.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java`
- 修改：`backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`
- 修改：`backend/src/test/java/com/personalenglishai/backend/common/web/GlobalExceptionHandlerTest.java`

**接口：**
- 依赖：任务 3 的客户端、`VocabularyDictionaryEnricher.lookupWithoutUserState`、`SubscriptionService.assertAiTokenQuotaAvailable`、`AiUsageContextHolder` 和 `AiUsageRecorder`。
- 产出：`VocabularyImageRecognitionService.recognize(Long userId, MultipartFile file) -> VocabularyImageRecognitionResponse`。
- 产出：需要鉴权的 `POST /api/vocabulary/image-recognitions`。
- 产出：公共建议类型 `Suggestion(String term, boolean dictionaryVerified)`，以及去重且保持顺序的警告列表。

- [ ] **步骤 1：编写覆盖校验、配额和词典核验的预期失败服务测试**

覆盖空输入、恰好 10 MiB 时接受、10 MiB + 1 字节时拒绝、允许的 MIME/扩展名、两者不匹配时拒绝、调用客户端前的配额拒绝、原词命中词典、已核验建议排序、词典无命中、词典不可用警告、用量记录，以及输出/错误正文的隐私边界。

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

- [ ] **步骤 2：运行服务测试并确认处于 RED 阶段**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyImageRecognitionServiceTest test
```

预期：测试编译失败，因为公共 DTO/服务尚不存在。

- [ ] **步骤 3：实现文件策略、配额、模型用量和词典增强**

公共条目契约如下：

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

服务执行顺序必须严格如下：

1. 校验用户、字节内容、MIME 和扩展名。
2. 调用 `subscriptionService.assertAiTokenQuotaAvailable(userId)`。
3. 生成 `vocab-image-<32 lowercase hex>` 格式的追踪 ID。
4. 进入 `AiUsageContext(userId, "vocabulary.image_recognition", traceId)`。
5. 调用 Python 一次，并使用 `providerRequestId=traceId` 记录返回的用量。
6. 只对 `suspected_typo` 条目查询词典。
7. 返回增强后的建议与警告。

不得捕获订阅异常。仅在词典处理阶段捕获词典服务可用性异常；添加一次 `DICTIONARY_VERIFICATION_UNAVAILABLE` 警告，并保留所有模型给出的拼写错误状态。

- [ ] **步骤 4：编写预期失败的控制器与全局错误测试**

使用 `MockMultipartFile`，断言匿名请求返回 401、有效 multipart 返回 200、空文件/无效文件返回 400、配额不足返回 429、Schema 错误返回 502、服务不可用返回 503、超时返回 504。验证控制器只负责委托给服务。

- [ ] **步骤 5：实现接口与稳定错误映射**

新增：

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

新增带数字前缀的错误码，以驱动现有处理器：

```java
VOCABULARY_IMAGE_INVALID("400052", "图片格式或大小不符合要求"),
VOCABULARY_IMAGE_OUTPUT_INVALID("502050", "图片识别结果不可用"),
VOCABULARY_IMAGE_UNAVAILABLE("503050", "图片识别服务暂时不可用"),
VOCABULARY_IMAGE_TIMEOUT("504050", "图片识别超时"),
```

为 `GlobalExceptionHandler.resolveStatus` 增加 `502 -> BAD_GATEWAY` 和 `504 -> GATEWAY_TIMEOUT` 映射。

- [ ] **步骤 6：运行聚焦 Java 测试并提交**

运行：

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-image-32-bytes'
mvn -Dtest=VocabularyImageRecognitionServiceTest,VocabularyControllerTest,GlobalExceptionHandlerTest test
```

预期：所有测试通过。

提交：

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyImageRecognitionResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionServiceTest.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java backend/src/test/java/com/personalenglishai/backend/common/web/GlobalExceptionHandlerTest.java
git commit -m "feat(api): 提供单词图片识别接口"
```

---

### 任务 5：在沉淀链路中保留逐词 OCR 来源决策

**文件：**
- 修改：`backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java`
- 修改：`backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java`
- 修改：`backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java`
- 修改：`backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`

**接口：**
- 产出：在 `VocabularyCaptureRequest` 上增量新增 `List<ItemSource> itemSources`。
- 产出：新增来源类型 `ocr_image`，同时保留 `manual` 和 `dictionary`。
- 产出：`ItemSource(String contextText, Map<String,Object> metadata)`，按与 `terms` 相同的零基索引选择。
- 依赖：现有卡片标识、幂等性、主题解析、来源插入和生成任务逻辑，且不改变这些契约。

- [ ] **步骤 1：编写预期失败的请求与服务测试**

覆盖不含 `itemSources` 的向后兼容 JSON、六参数 Java 构造器兼容性、itemSources 长度不匹配、`ocr_image` 缺少 itemSources、严格元数据白名单、resolution 枚举、批次/条目元数据合并、条目语境覆盖、禁止 `rawText`/`imageBase64`，以及来源列表/筛选器可见性。

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

- [ ] **步骤 2：运行聚焦测试并确认处于 RED 阶段**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest,VocabularyControllerTest test
```

预期：测试失败，因为 `ocr_image` 和 `itemSources` 被拒绝或尚不存在。

- [ ] **步骤 3：新增向后兼容的请求结构**

将 record 修改为：

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

将来源正则扩展为 `manual|dictionary|ocr_image`。

- [ ] **步骤 4：校验并合并逐项来源**

在 `VocabularyCaptureService.validate` 中，当 itemSources 非空时要求 `itemSources.size() == terms.size()`；对于 `ocr_image`，要求提供完整 itemSources 列表。在开启条目事务前，校验严格的批次键、逐项键和标量边界。

在 `VocabularyCaptureItemService` 中，于插入前解析来源：

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

将解析后的来源传入 `newSource`。不得改变幂等键或生成任务的请求索引。

- [ ] **步骤 5：运行聚焦测试并提交**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyCaptureServiceTest,VocabularyCaptureItemServiceTest,VocabularyControllerTest,VocabularyCardServiceTest test
```

预期：所有测试通过，包括现有的手动/词典沉淀测试。

提交：

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCaptureRequest.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemService.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureServiceTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureItemServiceTest.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java
git commit -m "feat(api): 支持图片识别逐词来源"
```

---

### 任务 6：构建前端识别 API 与候选词状态模型

**文件：**
- 新建：`web/src/features/vocabulary/imageRecognition.ts`
- 新建：`web/tests/vocabularyImageRecognition.test.ts`
- 修改：`web/src/api/vocabulary.ts`
- 修改：`web/src/composables/useVocabularyCards.ts`
- 修改：`web/env.example`

**接口：**
- 产出：带 60 秒超时的 `recognizeVocabularyImage({ file, signal }): Promise<VocabularyImageRecognitionResponse>`。
- 产出：`ImportCandidate`、`mergeRecognitionCandidates`、`applySuggestion`、`keepOriginal`、`removeCandidate`、`updateCandidateTerm`、`selectedReadyCandidates` 和 `buildCaptureBatches`。
- 产出：`isVocabularyImageRecognitionEnabled(): boolean`，仅当配置值严格等于字符串 `true` 时返回 true。
- 依赖：任务 4 的公共响应和任务 5 的沉淀请求。

- [ ] **步骤 1：编写预期失败的 API 与纯状态测试**

覆盖文件策略、功能开关解析、已接受词条的默认状态、未处理拼写错误阻止提交、已核验/未核验建议、明确的拼写处理操作、不区分大小写的稳定去重、30 项警告、全选/清空、手动与 OCR 混合分组、逐项元数据，以及沉淀载荷中不含 rawText。

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

- [ ] **步骤 2：运行测试并确认处于 RED 阶段**

运行：

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts
```

预期：测试导入失败，因为功能模块/类型尚不存在。

- [ ] **步骤 3：新增严格的 API 与沉淀类型**

新增：

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

将 `VocabularyCaptureSource.type` 修改为 `'manual' | 'ocr_image'`，新增可选的 `itemSources`，并使用 `{ timeout: 60_000, signal }` 提交 `FormData`。

- [ ] **步骤 4：实现不可变候选词 reducer 与批次构建器**

候选词结构如下：

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

`buildCaptureBatches` 按 `manual` 和每个 OCR 追踪批次分组，筛选已选择且已处理的候选词，保持展示顺序，为每个批次创建一个客户端请求 ID，将公共识别元数据放入 `source.metadata`，并将 `observedText`/`resolution` 与逐词语境放入 `itemSources`。

`updateCandidateTerm` 保持手动候选词的来源为 manual。对于 OCR 候选词，若词条与规范化后的识别文本相同，则保持 `accepted`（若用户已明确执行保留原词，则为 `original_kept`）；若用户确认了不同词条，则标记为 `suggestion_applied`，即使该词条是手动输入而非从模型建议列表中选择。

- [ ] **步骤 5：新增 TanStack mutation 并运行测试/构建**

在 `useVocabularyCards` 中新增 `imageRecognitionMutation`，其 mutation 变量为 `{ file: File; signal: AbortSignal }`。识别过程尚未持久化任何数据，因此不得使卡片查询失效。

运行：

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts tests/vocabularyApiContract.test.ts
npm run build
```

预期：测试及 TypeScript/Vite 构建通过。

- [ ] **步骤 6：提交**

```powershell
git add web/src/api/vocabulary.ts web/src/composables/useVocabularyCards.ts web/src/features/vocabulary/imageRecognition.ts web/tests/vocabularyImageRecognition.test.ts web/env.example
git commit -m "feat(ui): 建立图片候选状态模型"
```

---

### 任务 7：重构紧凑型导入工作区界面

**文件：**
- 新建：`web/src/components/vocabulary/VocabularyTextCapture.vue`
- 新建：`web/src/components/vocabulary/VocabularyImageCapture.vue`
- 新建：`web/src/components/vocabulary/VocabularyTermReview.vue`
- 新建：`web/src/components/vocabulary/VocabularyThemeSelect.vue`
- 修改：`web/src/components/vocabulary/VocabularyCapturePanel.vue`
- 修改：`web/src/views/VocabularyView.vue`
- 修改：`web/tests/vocabularyDepositionWorkspace.test.ts`
- 修改：`web/tests/vocabularyThemeShelf.test.ts`

**接口：**
- 依赖：任务 6 的 API mutation 与候选词 reducer/批次构建器。
- 产出：一个紧凑工作区，包含模式标签、主题选择、折叠的来源语境、文本输入、图片输入、复核列表和 `生成 N 张卡片`。
- 触发：所有批次均得到最终结果后触发现有 `captured` 事件；移除成功批次，失败批次保留并可继续编辑。

- [ ] **步骤 1：更新源码级 UI 契约测试并确认处于 RED 阶段**

锁定页面文案与组件边界：

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

运行：

```powershell
cd web
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyThemeShelf.test.ts
```

预期：针对旧标题、主题陈列区和底部提示的断言失败。

- [ ] **步骤 2：实现主题选择器与文本适配器**

`VocabularyThemeSelect` 渲染带标签的原生选择器，列出启用中的主题，并提供指向 `/app/vocabulary/themes` 的路由链接/按钮。加载中、阻断性错误和空状态分别提供明确且可访问的文本。`VocabularyTextCapture` 渲染文本框并触发解析后的原始字符串，但不持有候选词状态。

- [ ] **步骤 3：实现图片选择、拍照、预览与仅采用最新请求**

`VocabularyImageCapture` 只持有当前 `File`、对象 URL、活动请求 ID 和 AbortController，并将识别响应发送给父组件。已选图片状态展示真实预览、清洗后的文件名及 `开始识别`；识别完成状态展示候选词总数、未处理拼写错误数量、`重新识别`、`更换图片` 和折叠的识别原文。

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

替换图片和组件卸载时撤销旧对象 URL。使用两个隐藏文件输入：普通上传和 `capture="environment"`；两者均接受 `image/jpeg,image/png,image/webp`。通过 `aspect-ratio`、`object-fit: contain` 和最大高度保持预览尺寸稳定。识别原文使用默认折叠的 `<details>`。重试或识别替换图片时，按不区分大小写的词条将最新响应合并到父组件候选词列表，且不得移除此前准备好的手动候选词。

- [ ] **步骤 4：实现候选词复核与明确的拼写错误决策**

普通候选词显示复选框、可编辑词条和删除操作。疑似拼写错误显示识别文本、建议，并在已核验时显示 `词典已验证` 标签，同时提供 `采用`、`保留原词` 和 `删除`。组件只触发 reducer 命令，不得修改 props。“全选”只选择已处理候选词；“清空”取消选择全部候选词。30 个候选词的上限警告显示在滚动区域之外。

- [ ] **步骤 5：将 `VocabularyCapturePanel` 重组为唯一状态持有者**

面板持有 `mode`、`rawTerms`、`candidates`、`selectedThemeUid`、`sourceContext`、请求 ID、结果和错误。切换模式时保留候选词；离开图片模式时取消进行中的图片请求。将文本候选词解析到统一列表中，且不得删除 OCR 候选词。顺序提交各批次，并保留失败批次：

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

按钮规则：没有已选择且可提交的词条、存在已选择但未处理的拼写错误、缺少主题、存在阻断性主题错误、正在沉淀或正在识别时禁用。按钮文案为 `生成卡片`、`生成 N 张卡片` 和 `生成中...`；不得包含主题名称。

- [ ] **步骤 6：精简页面标题并接入功能开关与 mutation**

将沉淀页页头替换为仅包含 `<h1>单词沉淀</h1>`。向面板传入 `imageRecognitionEnabled` 和 `imageRecognitionMutation`。不得修改卡片列表、筛选器、分页或持久化卡片路由。

- [ ] **步骤 7：运行前端测试与构建**

运行：

```powershell
cd web
npx tsx --test tests/vocabularyImageRecognition.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyThemeShelf.test.ts tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts
npm run build
```

预期：所有测试与构建通过；不存在文本溢出或类型错误。

- [ ] **步骤 8：提交**

```powershell
git add web/src/components/vocabulary/VocabularyTextCapture.vue web/src/components/vocabulary/VocabularyImageCapture.vue web/src/components/vocabulary/VocabularyTermReview.vue web/src/components/vocabulary/VocabularyThemeSelect.vue web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/views/VocabularyView.vue web/tests/vocabularyDepositionWorkspace.test.ts web/tests/vocabularyThemeShelf.test.ts
git commit -m "feat(ui): 重构单词沉淀导入工作区"
```

---

### 任务 8：记录符合隐私要求的漏斗与卡片就绪事件

**文件：**
- 新建：`backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql`
- 修改：`backend/src/main/resources/db/schema.sql`
- 新建：`backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyProductEvent.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyProductEventMapper.java`
- 新建：`backend/src/main/resources/mapper/VocabularyProductEventMapper.xml`
- 新建：`backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchRequest.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchResponse.java`
- 新建：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java`
- 新建：`backend/src/test/java/com/personalenglishai/backend/db/VocabularyProductEventSchemaTest.java`
- 新建：`backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventServiceTest.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizer.java`
- 修改：`backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularySourceMapper.java`
- 修改：`backend/src/main/resources/mapper/VocabularySourceMapper.xml`
- 新建：`web/src/features/vocabulary/productEvents.ts`
- 新建：`web/tests/vocabularyProductEvents.test.ts`
- 修改：`web/src/api/vocabulary.ts`
- 修改：`web/src/components/vocabulary/VocabularyCapturePanel.vue`
- 修改：`web/src/views/VocabularyView.vue`

**接口：**
- 产出：`POST /api/vocabulary/product-events/batch`，每批最多接收 50 个幂等事件。
- 产出：允许的事件名称 `vocabulary_image_recognition_started`、`vocabulary_image_recognition_completed`、`vocabulary_image_candidates_confirmed`、`vocabulary_capture_submitted`、`vocabulary_cards_ready` 和 `vocabulary_learning_started`。
- 产出：服务端记录沉淀与卡片就绪事件；浏览器端记录开始、完成、确认和开始学习事件。
- 依赖：识别 `traceId`、沉淀来源元数据、生成任务 `sourceUid` 和卡片 UID。

- [ ] **步骤 1：编写预期失败的 Schema 与服务隐私测试**

数据表必须包含 `event_uid`、`user_id`、`event_name`、`trace_id`、`session_id`、`card_uid`、`properties_json`、`occurred_at` 和时间戳字段，并设置唯一约束 `(user_id, event_uid)`，以及 `(event_name, occurred_at)`、`(trace_id, occurred_at)` 和 `(card_uid, occurred_at)` 索引。

服务测试必须证明重复事件 ID 只插入一次、未知事件名会被拒绝、严格执行最多 50 个事件的限制，以及禁止的属性键/值会被拒绝。只允许标量/短数组属性和以下键：

```text
sourceType, durationMs, candidateCount, suspectedCount, selectedCount,
editedCount, removedCount, resolutionCount, successCount, failedCount,
provider, model, promptVersion, modelCallCount, warningCodes, outcome
```

禁止的键包括 `fileName`、`term`、`observedText`、`contextText`、`rawText`、`content`、`markdown`、`image` 和 `base64`，匹配时不区分大小写。

- [ ] **步骤 2：运行后端事件测试并确认处于 RED 阶段**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyProductEventSchemaTest,VocabularyProductEventServiceTest test
```

预期：测试失败，因为迁移脚本、mapper 和服务尚不存在。

- [ ] **步骤 3：实现幂等事件存储与批量接口**

使用 `INSERT IGNORE` 并返回已接受/重复事件数量。请求结构如下：

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

控制器将已鉴权的批次委托给 `VocabularyProductEventService.acceptBatch`。

- [ ] **步骤 4：记录由服务端负责的沉淀与卡片就绪事件**

`VocabularyCaptureService.capture` 得到响应后，触发一个 `vocabulary_capture_submitted` 事件，包含来源类型及成功/失败数量。OCR 使用 `recognitionTraceId`；手动对照组使用 `clientRequestId` 作为追踪标识。若沉淀响应条目因合并到现有可读卡片而已处于 `ready` 状态，则立即同时触发其幂等 `vocabulary_cards_ready` 事件。使用 `REQUIRES_NEW` 进行尽力写入并捕获失败，确保分析事件不会导致沉淀事务回滚。

新增 `VocabularySourceMapper.findBySourceUid`。在 `VocabularyGenerationFinalizer` 中，当 AI 版本成为活动的 `ready` 版本后，从现有任务请求 JSON 读取 `sourceUid`，加载该来源，并触发幂等 `vocabulary_cards_ready` 事件；事件包含 `eventUid="vocabulary-cards-ready:" + revisionUid`、`cardUid`、来源类型和来源识别追踪信息。事件写入失败时记录警告，但不得回滚卡片完成流程。

- [ ] **步骤 5：编写预期失败的前端事件测试**

覆盖稳定的会话 ID、随机事件 UID、载荷白名单、不含敏感值、完成耗时、候选词确认数量，以及每张卡片每个页面会话只记录一次的学习事件。

运行：

```powershell
cd web
npx tsx --test tests/vocabularyProductEvents.test.ts
```

预期：由于事件辅助模块尚不存在，导入失败。

- [ ] **步骤 6：实现浏览器事件上报**

将 `vocabulary.productEventSessionId` 存储在 `sessionStorage` 中；使用 `Set` 保存当前页面会话中已上报的卡片 ID。尽力提交事件批次；分析事件失败不得阻塞识别、沉淀或导航。

触发规则：

- 在 mutation 之前立即触发 `started`。
- 成功或失败时触发 `completed`，包含耗时、结果和数量，但不含文件名。
- 在第一个 OCR 沉淀批次前立即触发 `candidates_confirmed`。
- 首次渲染具有可读内容的持久化卡片详情时触发一次 `learning_started`。

- [ ] **步骤 7：运行事件测试并提交**

运行：

```powershell
cd backend
mvn -Dtest=VocabularyProductEventSchemaTest,VocabularyProductEventServiceTest,VocabularyCaptureServiceTest,VocabularyGenerationFinalizerTest,VocabularyControllerTest test
cd ..\web
npx tsx --test tests/vocabularyProductEvents.test.ts tests/vocabularyImageRecognition.test.ts tests/vocabularyDepositionWorkspace.test.ts
npm run build
```

预期：所有测试和构建通过。

提交：

```powershell
git add backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql backend/src/main/resources/db/schema.sql backend/src/main/java/com/personalenglishai/backend/entity/vocabulary/VocabularyProductEvent.java backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularyProductEventMapper.java backend/src/main/resources/mapper/VocabularyProductEventMapper.xml backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchRequest.java backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyProductEventBatchResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventService.java backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCaptureService.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationFinalizer.java backend/src/main/java/com/personalenglishai/backend/mapper/vocabulary/VocabularySourceMapper.java backend/src/main/resources/mapper/VocabularySourceMapper.xml backend/src/test/java/com/personalenglishai/backend/db/VocabularyProductEventSchemaTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyProductEventServiceTest.java web/src/features/vocabulary/productEvents.ts web/src/api/vocabulary.ts web/src/components/vocabulary/VocabularyCapturePanel.vue web/src/views/VocabularyView.vue web/tests/vocabularyProductEvents.test.ts
git commit -m "feat(vocabulary): 记录图片沉淀漏斗事件"
```

---

### 任务 9：完成契约 E2E、文档、发布与最终验证

**文件：**
- 修改：`web/tests/vocabularyDepositionFlow.spec.ts`
- 新建：`docs/api/vocabulary.md`
- 新建：`docs/ai/vocabulary-image-recognition.md`
- 修改：`docs/api/index.md`
- 修改：`docs/ai/index.md`
- 修改：`docs/architecture/vocabulary-deposition.md`
- 修改：`docs/runbooks/environment-variables.md`
- 修改：`docs/runbooks/local-dev.md`
- 修改：`.env.example`
- 修改：`docker-compose.yml`

**接口：**
- 依赖：任务 1 至 8 的完整纵向链路。
- 产出：模拟浏览器验收、真实模型按需冒烟说明、部署顺序、回滚方案、环境变量契约、延迟/事件验证和最新文档链接。

- [ ] **步骤 1：扩展 E2E API 模拟以支持 multipart 识别**

在任何 `postDataJSON()` 调用之前处理 `/api/vocabulary/image-recognitions`。断言请求 Content-Type 以 `multipart/form-data` 开头，并返回一个已接受的 `package` 条目，以及一个疑似错误的 `recieve` 条目，后者包含已核验建议 `receive`。继续按 JSON 处理 `/api/vocabulary/captures`，并保存请求正文以供断言。

- [ ] **步骤 2：新增完整的图片导入 E2E 场景**

测试必须：

1. 为测试服务器启用功能开关。
2. 打开 `?tab=collection`。
3. 断言页面标题只显示 `单词沉淀`。
4. 使用键盘切换到 `图片识别`。
5. 使用 `page.setInputFiles` 选择内存中的 `words.png` 文件。
6. 开始识别并看到 `package` 和 `recieve`。
7. 采用已核验的 `receive`。
8. 选择主题、展开来源语境并生成 2 张卡片。
9. 断言 OCR 沉淀来源、逐项识别文本/resolution、不含原始文本/base64，以及现有的重复合并行为。
10. 在延迟识别请求尚未完成时替换图片，并证明过期响应不会渲染。

使用：

```typescript
await page.getByLabel('选择图片').setInputFiles({
  name: 'words.png',
  mimeType: 'image/png',
  buffer: Buffer.from('mock-image-bytes'),
})
```

- [ ] **步骤 3：更新现有文本沉淀 E2E 选择器**

将主题陈列区选择替换为 `getByLabel('生成主题').selectOption(...)`，并将 `按「产品英语」生成 2 张卡片` 严格替换为 `生成 2 张卡片`。保留原有文本沉淀断言，确保图片功能不会破坏手动沉淀。

- [ ] **步骤 4：请求浏览器授权后运行 Chromium E2E 与响应式检查**

用户明确授权本次验证使用 Chromium/Chrome 后，执行：

```powershell
cd web
$env:VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED='true'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps
```

预期：所有单词沉淀场景通过。检查 1280x800 和 390x844 视口下的文本模式、图片预览、疑似拼写错误和 30 项警告状态；确认不存在重叠、标签裁切、横向滚动或布局偏移。未获得浏览器授权时不得执行此步骤。

- [ ] **步骤 5：编写最新 API、AI、架构与运行手册文档**

记录严格的请求/响应字段、警告/错误码、配额键、文件限制、超时、Prompt 行为、模型重试上限、禁止图片持久化规则、`itemSources`、产品事件隐私、部署顺序和回滚方案。新增环境变量：

```text
VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=false
VOCABULARY_IMAGE_RECOGNITION_MODEL=<configured vision-capable model>
VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS=45000
VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS=55000
RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE=0
VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE=<local image path used only for opt-in smoke>
```

`docker-compose.yml` 将模型/超时传递给 Python，并将基础 URL/超时/共享令牌传递给 Java。前端功能开关默认保持 false。

- [ ] **步骤 6：运行完整的确定性验证**

运行：

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

预期：Python 确定性测试通过且可选冒烟测试跳过，Maven 测试套件通过，前端单词相关测试/构建通过，VitePress 构建成功且没有失效链接。

- [ ] **步骤 7：凭据可用时运行按需启用的真实服务冒烟测试**

在 `8011` 启动 Python；让 Java 使用未占用端口并连接到明确命名的临时 MySQL Schema；启用功能开关后启动前端。配置共享内部令牌和支持视觉能力的模型。分别测试一张单词列表图片、一张含拼写错误的笔记和一个无标记段落。验证正常情况下只调用一次模型，仅在结构化输出重试时最多调用两次；同时验证 P50/P95 日志字段、词典行为、OCR 来源元数据、卡片就绪、事件关联，以及日志中不含敏感内容。

- [ ] **步骤 8：提交 E2E 与文档**

```powershell
git add web/tests/vocabularyDepositionFlow.spec.ts docs/api/vocabulary.md docs/ai/vocabulary-image-recognition.md docs/api/index.md docs/ai/index.md docs/architecture/vocabulary-deposition.md docs/runbooks/environment-variables.md docs/runbooks/local-dev.md .env.example docker-compose.yml
git commit -m "docs(vocabulary): 补充图片导入部署验收"
```

- [ ] **步骤 9：检查合并就绪状态**

确认 `git status --short` 干净、每个任务提交均存在、前端功能开关默认仍关闭、迁移是增量且幂等的、未提交任何密钥或测试图片载荷、未修改无关内容，并确认该分支适合在合并到 `main` 前进入评审。
