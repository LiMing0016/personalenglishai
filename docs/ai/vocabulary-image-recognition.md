---
title: 单词图片识别 AI 契约
status: active
owner: ai
last_updated: 2026-07-21
review_cycle: on-change
related_code:
  - python/ai_orchestrator/agents/vocabulary_image_recognition.py
  - python/ai_orchestrator/workflows/vocabulary_image_recognition.py
  - python/ai_orchestrator/schemas/vocabulary_image_recognition.py
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyImageRecognitionService.java
related_docs:
  - docs/api/vocabulary.md
  - docs/architecture/vocabulary-deposition.md
  - docs/runbooks/environment-variables.md
---

# 单词图片识别 AI 契约

## 当前结论

图片识别由 Python OpenAI Agents SDK 服务负责一次多模态结构化调用，Java 负责鉴权、额度、文件校验、词典增强和公开错误映射。它不是传统 OCR 全文归档：目标是提取可沉淀的英语词汇候选，并让用户在写入卡片前复核。

## 输入与输出

Python 内部接口接收 Java 生成的 `contractVersion=1`、安全 trace、`language=en`、文件名、MIME 和图片字节。支持 JPEG、PNG、WEBP，最大 10 MiB。

模型输出使用严格 Pydantic 契约：

- `rawText`：最多 20,000 字符，仅用于即时复核。
- `items`：模型最多返回 100 项，工作流按规范词形稳定去重并截为 30 项。
- 每项包含 `observedText`、`normalizedTerm`、`status`、最多 3 个建议、可选语境和 0 到 1 的置信度。
- `accepted` 不得带建议；`suspected_typo` 必须至少有一个非空建议。
- 最终响应补充稳定 `itemId`、warning 与 `generation` 元数据。

## Prompt 与调用预算

Prompt 版本固定为 `vocabulary-image-recognition-v1`，由 agent Prompt resolver 加载。用户输入只要求从图片提取可见英语词汇并返回结构化输出，不允许模型选择持久化主题或直接创建卡片。

正常路径只调用模型一次。仅当结构化输出出现 `ModelBehaviorError`、Pydantic 校验失败或类型错误时允许一次结构重试，因此总调用次数最多 2 次。网络、鉴权、上游故障和超时不进行结构重试。

`VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS` 是两次尝试共享的单调时钟总预算，默认且最大为 45,000 ms，不是每次调用各 45 秒。Java HTTP timeout 默认 55,000 ms，为传输、词典增强和错误映射留出余量。

## 模型配置

| 配置 | 约束 |
| --- | --- |
| `VOCABULARY_IMAGE_RECOGNITION_MODEL` | 必须是已验证支持图片输入的模型；Python 与 Java 必须完全相同 |
| `OPENAI_API_KEY` | 只注入 Python；不得进入响应或日志 |
| `VOCABULARY_GENERATION_INTERNAL_TOKEN` | Java 与 Python 共用的内部鉴权 token |
| `VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS` | Python 总预算，1-45,000 ms |
| `VOCABULARY_IMAGE_RECOGNITION_PYTHON_TIMEOUT_MS` | Java 调 Python timeout，默认 55,000 ms |

Python 不提供业务模型默认值；模型、凭据、timeout 或 Prompt 配置无效时返回未配置，不切换到其他模型。Java 使用同一模型变量作为产品事件的 exact allowlist，避免客户端伪造模型维度。

## 词典增强

Python 保留模型对拼写的判断；Java 对 `suspected_typo` 做只读共享词典查询：

1. 原词命中词典时改为 `accepted`，不再要求用户处理。
2. 原词未命中时，按建议顺序标记 `dictionaryVerified`，已核验建议排在前面。
3. 任一词典调用不可用时，整个响应放弃部分增强，恢复所有模型原始 typo 状态，并只追加一次 `DICTIONARY_VERIFICATION_UNAVAILABLE`。

词典增强不读取用户收藏、查询次数或个人卡片。

## 隐私与可观测性

- `trace_include_sensitive_data=false`。
- 图片字节、base64、`rawText`、词条、文件名、上下文和 Prompt 不得写入日志或产品事件。
- Python 日志只记录安全 trace、图片字节数、候选计数、疑似错误计数、调用次数、provider、model、Prompt version、耗时和稳定错误码。
- 公开响应可以临时返回 `rawText` 供当前页面复核；离开页面后不持久化。
- 捕获只保存白名单 OCR 元数据、逐项 `observedText`、`resolution` 与可选逐项语境，不保存整图或识别全文。

## 失败模式

| 失败 | Python 稳定码 | Java 公开结果 | 重试 |
| --- | --- | --- | --- |
| 请求或图片无效 | `INVALID_IMAGE_REQUEST` 等 | 400 / `400052` | 修正输入 |
| 结构化输出连续失败 | `MODEL_OUTPUT_INVALID` | 502 / `502050` | 可由用户重新发起 |
| 模型或服务未配置 | `IMAGE_RECOGNITION_NOT_CONFIGURED` | 503 / `503050` | 修复配置 |
| 上游不可用 | `MODEL_UPSTREAM_UNAVAILABLE` | 503 / `503050` | 稍后重试 |
| 总预算耗尽 | `MODEL_TIMEOUT` | 504 / `504050` | 缩小图片或检查延迟 |

## 评估与验收

确定性测试验证 schema、Prompt 注册、稳定去重、30 项截断、一次结构重试、45 秒总预算、内部鉴权和错误映射。真实模型测试仅在下列条件同时满足时运行：

```text
RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE=1
OPENAI_API_KEY=<secret>
VOCABULARY_IMAGE_RECOGNITION_MODEL=<vision-capable-model>
VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE=<local-opt-in-image>
```

真实冒烟至少覆盖单词列表、含拼写错误的笔记和无标记段落，并核对正常一次调用、结构重试最多两次、P50/P95 延迟、词典标记、OCR 来源、卡片 ready、事件关联和无敏感日志。
