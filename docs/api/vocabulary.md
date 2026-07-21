---
title: 单词沉淀 API
status: active
owner: backend
last_updated: 2026-07-21
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

单词沉淀由“图片识别、候选复核、批量捕获、异步生成”组成。图片识别只返回候选，不直接写卡片；用户确认后再调用捕获接口。所有公开接口都要求当前登录用户，卡片、主题、来源与事件均按 `userId` 隔离。

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
| 400 | `400052` | 图片为空、超限、类型不支持、扩展名不匹配或 file part 数量不为 1 |
| 401 | `401001` | 未登录或 token 无效 |
| 429 | 订阅模块额度码 | `vocabulary.image_recognition` AI 配额不足 |
| 502 | `502050` | Python 或模型返回不符合结构化契约 |
| 503 | `503050` | Python 未配置、不可达、鉴权失败或上游不可用 |
| 504 | `504050` | 图片识别超过服务预算 |

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

## 产品事件

```http
POST /api/vocabulary/product-events/batch
Content-Type: application/json
Authorization: Bearer <access_token>
```

每批最多 50 条，按 `eventUid` 幂等写入，响应为 `{ "accepted": n, "duplicate": n }`。允许事件：

- `vocabulary_image_recognition_started`
- `vocabulary_image_recognition_completed`
- `vocabulary_image_candidates_confirmed`
- `vocabulary_capture_submitted`
- `vocabulary_cards_ready`
- `vocabulary_learning_started`

属性按事件名使用精确白名单。通用允许值包括 `sourceType`、计数字段、`durationMs`、`outcome`、`provider`、`model`、`promptVersion`、`modelCallCount` 和 `warningCodes`，但只有对应事件声明的字段才可写。模型必须等于后端配置的 `VOCABULARY_IMAGE_RECOGNITION_MODEL`。禁止文件名、词条、识别原文、上下文、卡片内容、图片和 base64；未知属性、错误类型和非法 ID 返回 400。

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
