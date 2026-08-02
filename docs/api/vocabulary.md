---
title: 单词沉淀 API
status: active
owner: backend
last_updated: 2026-08-02
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - web/src/api/vocabulary.ts
related_docs:
  - docs/architecture/vocabulary-deposition.md
  - docs/ai/vocabulary-image-recognition.md
  - docs/runbooks/environment-variables.md
---

# 单词沉淀 API

## 当前结论

单词沉淀由“统一导入分析、候选复核、批量捕获、异步生成”组成。导入分析接受文本、图片或混合输入，只返回候选，不直接写卡片；用户确认后再调用捕获接口。所有公开接口都要求当前登录用户，卡片、主题、来源与事件均按 `userId` 隔离。

## 统一导入分析

### Endpoint

```http
POST /api/vocabulary/import-analyses
Content-Type: multipart/form-data
Authorization: Bearer <access_token>
```

### Request

multipart 字段：

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `text` | 与 `file` 至少一个 | 最多 20,000 字符；服务端统一换行为 `\n` 并 trim |
| `file` | 与 `text` 至少一个 | 最多一个；JPG、PNG 或 WEBP；非空且不超过 10 MiB |
| `inputFingerprint` | 是 | 64 位小写 SHA-256 十六进制字符串 |

输入指纹由 Web 和 Java 独立计算：`normalizedText UTF-8 + 0x00 + 原始图片字节`。Java 在额度检查和 Python 调用前重新计算；不一致立即拒绝，防止界面当前输入与提交候选错位。

### Response

```json
{
  "code": "0",
  "data": {
    "contractVersion": 1,
    "traceId": "vocab-import-<opaque-id>",
    "inputFingerprint": "<64 lowercase hex>",
    "rawText": "package scrutinize",
    "warnings": [],
    "items": [
      {
        "itemId": "item-1",
        "observedText": "scrutinize",
        "normalizedTerm": "scrutinize",
        "status": "accepted",
        "suggestions": [],
        "contextText": null,
        "confidence": 0.98,
        "evidence": "text_image"
      }
    ],
    "generation": {
      "provider": "openai",
      "model": "<configured-model>",
      "promptVersion": "vocabulary-import-analysis-v1",
      "modelCallCount": 1,
      "traceId": "vocab-import-<opaque-id>",
      "usage": null
    }
  }
}
```

`evidence` 只能是 `text`、`image` 或 `text_image`。Web 只有在响应指纹、请求起始指纹和当前输入指纹全部一致时才接收候选；否则丢弃迟到响应并要求重新分析。

### 错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `400053` | 文本、图片或 multipart 结构不符合导入约束 |
| 400 | `400054` | 输入指纹与服务端重新计算结果不一致 |
| 429 | `429010` | 本月 AI token 额度已用完 |
| 502 | `502051` | Python 或模型结果无法通过统一导入结构化契约校验 |
| 503 | `503051` | Python、内部鉴权或模型上游暂时不可用 |
| 504 | `504051` | Python 的 45 秒共享模型预算耗尽，或 Java 调用 Python 超时 |

## 图片识别

### Endpoint

```http
POST /api/vocabulary/image-recognitions
Content-Type: multipart/form-data
Authorization: Bearer <access_token>
```

### Request

multipart 只允许一个名为 `file` 的 part，不接受客户端指定模型、Prompt 或 trace。文件限制：

- MIME：`image/jpeg`、`image/png`、`image/webp`。
- 扩展名必须与 MIME 一致：`.jpg/.jpeg`、`.png`、`.webp`。
- 文件非空，最大 10 MiB。
- 同名多个 `file` part、缺失 part、格式不符或超限均拒绝。

### Response

```json
{
  "code": "0",
  "data": {
    "contractVersion": 1,
    "traceId": "vocab-image-<opaque-id>",
    "rawText": "package recieve",
    "warnings": [],
    "items": [
      {
        "itemId": "item-1",
        "observedText": "recieve",
        "normalizedTerm": "recieve",
        "status": "suspected_typo",
        "suggestions": [
          { "term": "receive", "dictionaryVerified": true }
        ],
        "contextText": "receive customer feedback",
        "confidence": 0.91
      }
    ],
    "generation": {
      "provider": "openai",
      "model": "<configured-model>",
      "promptVersion": "vocabulary-image-recognition-v1",
      "modelCallCount": 1,
      "traceId": "vocab-image-<opaque-id>",
      "usage": { "inputTokens": 120, "outputTokens": 40 }
    }
  }
}
```

`rawText` 仅用于本次复核展示，禁止写入捕获请求、数据库、产品事件和日志。`items` 最多 30 项；`usage` 在提供商未返回用量时为 `null`。

状态与警告：

| 值 | 说明 |
| --- | --- |
| `accepted` | 可直接选择并沉淀 |
| `suspected_typo` | 必须采用建议、保留原词或删除后才能提交 |
| `CANDIDATE_LIMIT_REACHED` | 模型结果超过 30 项，已稳定截断 |
| `DICTIONARY_VERIFICATION_UNAVAILABLE` | 词典不可用，保留模型原始 typo 状态和未核验建议 |

### 错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `400001` | 缺少 multipart part 或请求格式错误 |
| 400 | `400052` | 图片为空、超过 10 MiB、MIME 不支持、扩展名与 MIME 不匹配，或 `file` part 数量不为 1 |
| 401 | `401001` | 未登录或 token 无效 |
| 429 | `429010` | 本月 AI token 额度已用完；`vocabulary.image_recognition` 不再接受新的模型调用 |
| 502 | `502050` | Python 或模型响应已返回，但无法通过图片识别结构化契约校验 |
| 503 | `503050` | Python 服务未配置、不可达、内部鉴权失败，或模型上游不可用 |
| 504 | `504050` | Python 的 45 秒共享模型预算耗尽，或 Java 调用 Python 超时 |

## 捕获与逐词来源

```http
POST /api/vocabulary/captures
Content-Type: application/json
Authorization: Bearer <access_token>
```

OCR 请求示例：

```json
{
  "clientRequestId": "<stable-request-id>",
  "terms": ["package", "receive"],
  "language": "en",
  "themeUid": "theme_user_product",
  "source": {
    "type": "ocr_image",
    "sourceRef": "recognition:vocab-image-<opaque-id>",
    "sourceTitle": "图片识别",
    "contextText": "产品发布图片笔记",
    "metadata": {
      "recognitionTraceId": "vocab-image-<opaque-id>",
      "fileName": "words.png",
      "provider": "openai",
      "model": "<configured-model>",
      "promptVersion": "vocabulary-image-recognition-v1"
    }
  },
  "itemSources": [
    {
      "contextText": "package release notes",
      "metadata": { "observedText": "package", "resolution": "accepted" }
    },
    {
      "contextText": "receive customer feedback",
      "metadata": { "observedText": "recieve", "resolution": "suggestion_applied" }
    }
  ]
}
```

`itemSources` 是兼容性新增字段；省略时旧的 `manual`、`dictionary` 请求保持原语义。OCR 请求必须让 `itemSources.length === terms.length`。`resolution` 只允许 `accepted`、`suggestion_applied`、`original_kept`。metadata 使用严格白名单，禁止 `rawText`、图片字节、base64、Markdown 或额外字段。`clientRequestId` 在同一次重试中保持不变；重复词形返回 `source_merged`，不会创建第二张卡。

## 按词形解析当前用户单词卡

搜索页需要判断当前词是否已有沉淀卡片时，使用精确解析接口；不要使用带 `keyword` 的卡片列表第一页代替，因为列表是模糊搜索并按最近沉淀排序。

```http
GET /api/vocabulary/cards/resolve?term=Wonder&language=en
Authorization: Bearer <access_token>
```

服务端使用与捕获链路相同的词形规范化规则，并按“当前用户 + language + normalized_term”精确查找未软删除的卡片。接口只返回稳定身份，不复制详情读取逻辑。

找到卡片：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "found": true,
    "cardUid": "card_wonder"
  }
}
```

未找到或卡片已软删除仍返回 HTTP 200，避免把正常缺失误判为服务故障：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "found": false,
    "cardUid": null
  }
}
```

命中后使用 `GET /api/vocabulary/cards/{cardUid}` 读取当前有效版本。未登录返回 HTTP 401；缺少或规范化后为空的 `term`/`language` 返回现有参数错误响应；数据库与服务器异常保持错误状态，客户端不得把这些异常缓存为“没有笔记”。词典的 `en-gb` 和 `en-us` 由 Web 映射为卡片语言 `en`。

## 产品事件

该接口只接收单词沉淀漏斗事件。它不会接收任意埋点，也不能携带图片、识别原文、单词或卡片内容。

### Endpoint 与鉴权

```http
POST /api/vocabulary/product-events/batch
Content-Type: application/json
Authorization: Bearer <access_token>
```

接口要求有效的 Bearer token。未登录或 token 无效返回 HTTP 401、错误码 `401001`。请求体 `events` 必填，必须是包含 1..50 个 `Event` 的数组；空数组、超过 50 项、数组中包含 `null` 或字段校验失败均返回 HTTP 400。

### Event 字段

| 字段 | 必填 | 类型与长度 | 格式与语义 |
| --- | --- | --- | --- |
| `eventUid` | 是 | string，1..128 字符 | 事件幂等键，只接受下列专属格式 |
| `eventName` | 是 | string，1..64 字符 | 只能是本文列出的 6 个事件名 |
| `traceId` | 否 | string，最多 128 字符 | 为空时写入 `null`；非空时只接受图片识别或捕获 trace 格式 |
| `sessionId` | 是 | string，1..128 字符 | 客户端会话 ID；服务端事件固定使用 `server` |
| `cardUid` | 否 | string，最多 64 字符 | 为空时写入 `null`；非空时必须是卡片 UID |
| `occurredAt` | 是 | 不带时区的 ISO-8601 本地日期时间 | 对应 Java `LocalDateTime`，例如 `2026-07-21T15:30:45.123`；不要附加 `Z` 或时区偏移 |
| `properties` | 否 | JSON object | 省略或传 `null` 时按空对象 `{}` 保存；键和值必须通过对应事件白名单 |

专属 ID 格式中的十六进制字符只能使用 `0-9a-f`：

- `eventUid`：`vocabulary-event:<32 位小写十六进制>`、`vocabulary-event:<小写 UUID>`、`vocabulary-capture-submitted:<64 位小写十六进制>`，或 `vocabulary-cards-ready:rev_<32 位小写十六进制>`。
- `sessionId`：`server`、`vocabulary-session:<32 位小写十六进制或小写 UUID>`。
- `traceId`：`vocab-image-<32 位小写十六进制>` 或 `capture:<64 位小写十六进制>`。
- `cardUid`：`card_<32 位小写十六进制>`。

请求示例：

```json
{
  "events": [
    {
      "eventUid": "vocabulary-event:0123456789abcdef0123456789abcdef",
      "eventName": "vocabulary_image_recognition_completed",
      "traceId": "vocab-image-0123456789abcdef0123456789abcdef",
      "sessionId": "vocabulary-session:0123456789abcdef0123456789abcdef",
      "cardUid": null,
      "occurredAt": "2026-07-21T15:30:45.123",
      "properties": {
        "sourceType": "ocr_image",
        "durationMs": 3200,
        "candidateCount": 2,
        "suspectedCount": 1,
        "provider": "openai",
        "model": "<VOCABULARY_IMAGE_RECOGNITION_MODEL 的精确值>",
        "promptVersion": "vocabulary-image-recognition-v1",
        "modelCallCount": 1,
        "warningCodes": [],
        "outcome": "success"
      }
    }
  ]
}
```

### 事件与属性白名单

每个事件的 `properties` 字段都可省略；一旦提供，只允许下表中的键：

| `eventName` | 允许的 `properties` 键 |
| --- | --- |
| `vocabulary_image_recognition_started` | `sourceType` |
| `vocabulary_image_recognition_completed` | `sourceType`, `durationMs`, `candidateCount`, `suspectedCount`, `provider`, `model`, `promptVersion`, `modelCallCount`, `warningCodes`, `outcome` |
| `vocabulary_image_candidates_confirmed` | `sourceType`, `candidateCount`, `suspectedCount`, `selectedCount`, `editedCount`, `removedCount`, `resolutionCount` |
| `vocabulary_capture_submitted` | `sourceType`, `successCount`, `failedCount` |
| `vocabulary_cards_ready` | `sourceType` |
| `vocabulary_learning_started` | `sourceType` |

属性值契约：

| 属性 | 类型与约束 |
| --- | --- |
| `sourceType` | string 枚举：`manual`、`dictionary`、`ocr_image` |
| `candidateCount`、`suspectedCount`、`selectedCount`、`editedCount`、`removedCount`、`resolutionCount`、`successCount`、`failedCount` | 有限非负整数，范围 0..1,000,000 |
| `durationMs` | 有限非负整数，范围 0..86,400,000 |
| `modelCallCount` | 有限非负整数，范围 0..100 |
| `outcome` | string 枚举：`success`、`failed` |
| `provider` | string，只能是 `openai` |
| `model` | 非空 string，最多 200 字符；必须与服务端 `VOCABULARY_IMAGE_RECOGNITION_MODEL` 的精确配置值一致，也就是 `vocabulary.product-events.allowed-image-models` 中的一个精确成员 |
| `promptVersion` | string，只能是 `vocabulary-image-recognition-v1` |
| `warningCodes` | string 数组，最多 10 项；每项只能是 `CANDIDATE_LIMIT_REACHED`、`DICTIONARY_VERIFICATION_UNAVAILABLE`；重复项按首次出现顺序去重 |

`filename`、`term`、`observedText`、`contextText`、`rawText`、`content`、`markdown`、`image`、`base64` 是大小写不敏感的敏感键，任何事件都禁止携带。除 `warningCodes` 的字符串数组外，不接受 object 或 array 形式的嵌套属性值。未知键、敏感键、嵌套值、错误类型、错误事件名或错误 ID 均返回 HTTP 400；服务端不会忽略或自动修正这些输入。

### Response 与幂等

成功写入返回 HTTP 200，并由通用 `ApiResponse` 的 `data` 包装批次结果：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "accepted": 1,
    "duplicate": 1
  }
}
```

当前 `VocabularyProductEventBatchResponse` 的真实字段名是 `accepted` 和 `duplicate`，不是 `acceptedCount` 或 `duplicateCount`。`accepted` 表示本次新写入的事件数，`duplicate` 表示被幂等约束忽略的事件数，两者之和等于请求事件数。

数据库以 `(user_id, event_uid)` 为唯一幂等键。同一用户重复提交相同 `eventUid` 时不新增记录并计入 `duplicate`；不同用户可使用相同 `eventUid`，彼此不冲突。

## 兼容性约束

- `contractVersion=1`、候选状态、warning code 和稳定错误码不能静默改义。
- 可新增可选响应字段，但不能要求旧客户端发送 `itemSources`。
- 旧 `/app/vocabulary/cards/<keyword>` 仍是单词库关键词过滤路由；只有 `card_` UID 进入持久化详情。
- Web 功能开关关闭时，文本沉淀、词典收藏、主题和卡片详情必须继续工作。

## 验收方式

```powershell
cd python
..\.venv\Scripts\python.exe -m pytest ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py -q

cd ..\backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-image-32-bytes'
mvn test

cd ..\web
npx tsx --test "tests/vocabulary*.test.ts"
npm run build
```

真实图片和浏览器 E2E 属于显式 opt-in；未授权或未配置凭据时不得运行，也不能把跳过记为真实链路通过。
