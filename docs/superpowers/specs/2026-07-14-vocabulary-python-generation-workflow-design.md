---
title: Python 主题单词卡生成工作流设计
status: approved
owner: ai
last_updated: 2026-07-14
review_cycle: on-change
related_code:
  - python/ai_orchestrator/app.py
  - python/ai_orchestrator/agents/
  - python/ai_orchestrator/workflows/
  - python/ai_orchestrator/schemas/
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - backend/src/main/resources/db/schema.sql
related_docs:
  - docs/architecture/vocabulary-deposition.md
  - docs/ai/vocabulary-theme-prompts.md
  - docs/superpowers/specs/2026-07-12-vocabulary-theme-markdown-card-design.md
  - docs/superpowers/specs/2026-07-13-vocabulary-card-notebook-detail-design.md
---

# Python 主题单词卡生成工作流设计

## 当前结论

单词卡的模型生成能力从 Java 后端迁移到 `python/ai_orchestrator`，采用专用、无状态、版本化的内部 HTTP 工作流。Java 继续拥有鉴权、词典查询、生成任务、租约、重试、版本冲突、最终校验和数据持久化；Python 只拥有 Prompt、模型调用、缺失 core 补全、主题 Markdown 生成和模型 trace。

Java 的 `VocabularyGenerationWorker` 仍领取现有 generation job，并调用 Python 的 `POST /internal/v1/vocabulary/card-generations`。Python 不直接读取业务数据库，不管理卡片 revision，不决定候选 revision 是否激活，也不经过通用聊天路由或对话会话。

前端继续只读取 Java 单词卡 API。核心 JSON、Markdown 和 revision 元数据保持稳定，因此模型服务迁移不会把单词卡页面绑定到 Python 实现，也不会阻碍后续章节、练习和复习界面演进。

## 背景与问题

当前 Java 已经实现词典优先的 core 构建、结构化模型 fallback、主题 Prompt、Markdown 生成、缓存和降级，但模型调用与业务任务代码处于同一运行时。项目的长期 AI 主线已经集中在 Python `ai_orchestrator` 和 OpenAI Agents SDK；继续在 Java 中扩展 Prompt、结构化输出和模型 trace 会形成两套 AI 运行边界。

本次迁移不是重写单词沉淀业务。现有卡片、主题、任务、revision、冲突和页面契约已经可用，应保留 Java 的业务所有权，只替换生成候选内容的模型执行边界。

## 目标与非目标

### 目标

- 让 Python 成为单词卡 Prompt、模型调用和生成工作流的唯一新增承载位置。
- 保留 Java 对可信词典数据、generation job、revision 和数据库事务的所有权。
- 使用显式 Pydantic 与 Java DTO 契约，避免跨服务自由 JSON。
- 词典 core 完整时只调用一次模型；core 不足时最多调用两次。
- 保留 core 成功、Markdown 失败时的 partial 降级能力。
- 让 Java 和 Python 的 trace、错误类型和重试预算可关联、可测试。
- 保持前端 Java API、core JSON 和 Markdown 内容契约兼容。
- 为生成 revision 保存可审计的 provider、model 和 Prompt version 元数据。

### 非目标

- 不让 Python 直接消费 MySQL generation job 或写卡片表。
- 不把单词卡生成接入通用 Assistant router、聊天 session 或 Vocab Agent。
- 不在本次新增消息队列、事件总线或新的 agent runtime。
- 不让 Python 返回颜色、组件名称、布局或其他前端渲染指令。
- 不修改用户录入、词典收藏、卡片去重和 revision 冲突语义。
- 不在模型失败时静默切换 Java 实现并重复调用模型。

## 架构决策

### 服务边界

Java 负责：

- 用户鉴权与业务权限。
- 词典查询和可信初始 core 构建。
- 主题 UID/version 解析与快照冻结。
- generation job 领取、租约、幂等、重试和终态。
- Python 请求组装、响应校验和错误映射。
- revision 追加、候选冲突、激活 guard 和最终落库。

Python 负责：

- 单词卡请求与响应 Pydantic schema。
- core 缺失判断后的结构化 fallback 模型调用。
- 主题 Prompt 解析和 Markdown 模型调用。
- 模型输出的第一层 schema、长度和安全校验。
- OpenAI Agents SDK workflow trace、模型用量和生成元数据。

Python 不依赖业务数据库。所有生成输入都由 Java 请求显式携带，所有生成结果都通过响应返回。

### 工作流而不是聊天路由

新增 `vocabulary_card_generation` workflow，内部调用受控的 capability Agent。该工作流不使用会话历史、路由 Agent、handoff 或聊天记忆，因为 generation job 已经提供完整上下文，且同一请求必须得到可重复校验的候选结果。

使用 OpenAI Agents SDK `output_type` 生成 Pydantic 结构化结果。Prompt 仍以仓库内正式资产为权威源，并通过现有 prompt resolver 规则接入；不在 endpoint、workflow 或测试内拼接大型 Prompt。

## 组件设计

### Python

- `schemas/vocabulary_card.py`：请求、主题快照、core、响应和错误模型。
- `agents/vocabulary_card.py`：创建 core fallback Agent 和主题 Markdown Agent。
- `prompts/agent_instructions/vocabulary_core_fallback.md`：仅补全缺失核心事实。
- `prompts/agent_instructions/vocabulary_card_markdown.md`：按主题生成 Markdown 扩展内容。
- `workflows/vocabulary_card_generation.py`：确定调用次数、执行 Agent、校验结果并组装响应。
- `services/vocabulary_card_generation.py`：配置、模型适配和 workflow 调用边界。
- `app.py`：增加内部 endpoint、服务认证和 HTTP 错误映射，不承载生成规则。

core fallback 与 Markdown 分为两个 Agent 调用边界：

- `dictionaryCore` 已包含有效音标或 sense 时，不调用 fallback，只调用 Markdown Agent。
- `dictionaryCore` 不足时，先调用结构化 fallback Agent，再调用 Markdown Agent。
- fallback 只能补充缺失字段，不能改写 Java 提供的非空可信字段，`term` 始终由请求覆盖。

### Java

- 新增 `VocabularyGenerationPythonClient`，专用于内部生成接口，不复用 `PythonAssistantClient`。
- 新增 typed request/response DTO 和稳定错误映射。
- 在现有生成器边界下增加 provider 接口，`python` 为新实现，`java` 暂时作为显式回滚实现。
- `VocabularyGenerationWorker`、`VocabularyGenerationFinalizer` 和 revision 写入语义保持不变。
- Java 对 Python 返回的 core 和 Markdown执行现有最终校验；Python 成功响应不构成直接落库许可。
- `vocabulary_card_revision` 增加可空的 `generation_metadata_json`，保存 provider、model、Prompt version、模型调用次数和业务 trace ID；旧 revision 继续兼容空值。

provider 由 `VOCABULARY_GENERATION_PROVIDER` 显式选择。Python 调用失败时不得在同一个 job attempt 内自动转用 Java，以免产生双重模型费用和不同 Prompt 候选。

## 内部接口契约

### Endpoint

```text
POST /internal/v1/vocabulary/card-generations
```

请求由 Java Worker 发起，使用 `Authorization: Bearer <internal-service-token>` 或等价专用内部认证头。浏览器和普通用户 token 不得调用该接口。生产环境若已有 service mesh 或 mTLS，可替换共享令牌，但不改变业务 payload。

### Request

```json
{
  "contractVersion": 1,
  "coreSchemaVersion": 1,
  "requestId": "job_123:attempt_1",
  "traceId": "vocab-job_123-attempt_1",
  "timeoutBudgetMs": 45000,
  "term": "supposed",
  "dictionaryCore": {
    "schemaVersion": 1,
    "term": "supposed",
    "phonetics": [],
    "senses": []
  },
  "sourceContext": "It is supposed to be easy.",
  "theme": {
    "uid": "theme_system_exam",
    "version": 1,
    "name": "Exam",
    "purpose": "用于考试词义、搭配和易错点学习",
    "promptStrategyKey": "exam-markdown-v1",
    "contentFormatVersion": 1
  }
}
```

约束：

- `contractVersion`、`coreSchemaVersion`、`requestId`、`traceId` 和 `timeoutBudgetMs` 必填。
- `term` 是卡片身份，响应 core 的 `term` 必须完全一致。
- `dictionaryCore` 是可信输入；Python 只能填补缺失内容。
- `sourceContext` 是数据，不是指令，必须使用安全分隔和长度上限。
- 主题 UID/version 是不可变快照，Python 不查询“最新主题”。
- Prompt version 由 Python 的策略注册表决定并通过响应返回，Java 不指定 Python 应运行哪个 Prompt 资产版本。

### Success Response

```json
{
  "contractVersion": 1,
  "coreSchemaVersion": 1,
  "core": {
    "schemaVersion": 1,
    "term": "supposed",
    "phonetics": [
      {"region": "uk", "text": "səˈpəʊzd", "audioUrl": null}
    ],
    "senses": [
      {
        "partOfSpeech": "adjective",
        "meanings": [
          {"definitionEn": "generally believed or expected", "definitionZh": "一般认为的；预期的"}
        ]
      }
    ]
  },
  "contentMarkdown": "## 考试重点\n...",
  "contentFormatVersion": 1,
  "outcome": "complete",
  "warning": null,
  "generation": {
    "provider": "openai",
    "model": "configured-model",
    "promptVersion": "vocabulary-card-markdown-v1",
    "modelCallCount": 1,
    "traceId": "vocab-job_123-attempt_1"
  }
}
```

`outcome` 只允许：

- `complete`：core 和 Markdown 都通过 Python 校验。
- `partial`：core 有效，但 Markdown 生成或校验失败。

partial 响应的 `contentMarkdown` 为空，`warning` 使用稳定枚举值，例如 `markdown_unavailable`，不得返回异常堆栈或模型原文。

### HTTP 错误

| HTTP | Python code | Java 行为 | 是否重试 |
| --- | --- | --- | --- |
| 400/422 | `INVALID_GENERATION_REQUEST` 或 `UNSUPPORTED_CONTRACT_VERSION` | job 永久失败，不写无效 revision | 否 |
| 401/403 | `INTERNAL_AUTH_FAILED` | 基础设施告警，job 失败 | 否 |
| 503 | `MODEL_UPSTREAM_UNAVAILABLE` | 沿用 generation job 重试 | 是 |
| 504 | `MODEL_TIMEOUT` | 沿用 generation job 重试 | 是 |
| 500 | `GENERATION_INTERNAL_ERROR` | 有界重试并记录稳定原因 | 是 |

模型返回非法 core 时不允许降级为 partial；只有已经验证的 core 才能在 Markdown 失败时降级。

## Prompt 与输出规则

### core fallback

- 输入为 `term`、缺失的 core 和可选来源语境。
- 输出使用 Pydantic `output_type`，不得返回 Markdown。
- 已有非空 phonetic、sense 和 meaning 不得被重写。
- Python 合并后重新执行 schema 和 term 校验。
- 可生成 core 必须包含请求 term、至少一个非空音标，以及至少一个包含非空词性的 sense；该 sense 至少包含一个英文或中文释义非空的 meaning。
- 不满足上述完整性时调用 fallback；fallback 后仍不完整则返回可重试的 `CORE_CONTENT_UNAVAILABLE`，不得生成只有 Markdown 的卡片。

### Markdown

- 输入为已经验证的完整 core、来源语境和主题快照。
- Markdown 只承载主题扩展内容，不重复承担单词身份和核心释义。
- 不允许原始 HTML、图片或代码围栏包裹整个响应。
- 最大长度继续保持 20,000 字符。
- strategy key 采用显式注册表；未知 key 是不可重试输入错误，不使用默认 Prompt 猜测。

Java 和 Python 都执行 Markdown 边界校验。双重校验是跨服务信任边界，不是两套业务规则；长度、HTML 和空内容规则必须由契约测试保持一致。

## 超时、重试与幂等

- 幂等键继续由 Java generation job、job UID 和 attempt 管理。
- Python endpoint 无状态，不保存业务结果，也不自行重放 Java job。
- Python provider 不复用现有 Java 侧 7 天语义缓存。Java 在调用前不知道 Python 实际 Prompt version，继续沿用旧 cache key 会在 Prompt 升级后返回陈旧内容；该缓存只保留给临时 `java` 回滚 provider。
- Java 的单次 HTTP timeout 必须小于 generation lease，并预留 finalizer 和数据库写入时间。
- Python 内部模型重试次数必须有界；Java 和 Python 的组合最坏耗时不得超过单次 attempt 预算。
- Java 把当前 attempt 的剩余预算写入 `timeoutBudgetMs`；Python 不得在该预算结束后继续开始新的模型调用。
- Java 只对明确的 5xx、连接失败和超时重试；4xx、契约不兼容和 schema 错误不重试。
- Python 返回响应后 Java lease 已丢失时，沿用现有 stale worker 防护，不激活迟到结果。

首版不引入消息队列。Java Worker 本身已经提供异步生成、租约和批量领取；同步等待一次内部 Python HTTP 响应是当前规模下更小、更可验证的方案。

## 可观察性与隐私

- Java 与 Python 使用同一个业务 `traceId`，但 OpenAI SDK trace ID 若有格式限制可单独生成，并把业务 `traceId` 放入 `group_id` 或 metadata。
- workflow name 固定为 `PEAI Vocabulary Card Generation`。
- trace metadata 可记录 job UID、主题 UID/version、Prompt version 和 outcome，不记录 API key。
- 来源语境、core 和 Markdown 可能含用户数据；默认设置 `trace_include_sensitive_data=false`。
- 普通日志只记录长度、枚举状态、模型、调用次数、耗时和稳定错误码，不输出 Prompt 或生成正文。

## 前端兼容与后续演进

前端继续消费 Java 返回的：

- 统一 core JSON：单词、音标、词性和释义。
- 主题 Markdown：例句、搭配、学习提示和其他扩展章节。
- revision 元数据：主题版本、Prompt 版本、模型和生成结果。

Python 不返回 UI 布局指令，因此后续可以独立增加：

- Markdown 章节导航和章节级组件。
- 例句收藏、标注和朗读。
- 练习生成与答题状态。
- 学习重点和复习状态。
- 不同主题的前端渲染风格。

如果未来需要结构化练习或章节元数据，应以 additive schema/revision 字段单独设计，不把 UI 结构塞入当前 Markdown Prompt，也不改变已有 core 字段含义。

## 测试与验收

### Python

- Pydantic request/response 的合法、缺字段、额外字段和版本不兼容测试。
- core fallback 不改写可信字段、保持 term、拒绝非法输出。
- dictionary core 完整时只调用一次模型；不足时最多两次。
- Basic、Exam、Reading 和 custom strategy Prompt 回归样例。
- Prompt delimiter 注入、原始 HTML、空 Markdown 和 20,001 字符测试。
- complete、partial、模型超时和上游异常 workflow smoke test。
- endpoint 内部认证、HTTP 状态和 response model 测试。

### Java

- Python client 请求字段、内部认证头、超时和响应反序列化测试。
- 400/422（含契约版本不兼容）、401/403、503、504 和连接错误映射测试。
- partial 保存有效 core 并把卡片置为 `needs_review`。
- complete 沿用现有 revision 写入和激活规则。
- lease 丢失、base revision 变化和候选冲突行为不回归。
- provider 配置只选择一个实现，不发生 silent fallback。

### 集成验收

1. 启动 Python orchestrator、Java、MySQL 和 Redis。
2. 健康检查必须显示词汇生成 workflow 已配置，但不暴露密钥。
3. 用真实模型生成一张 Basic 系统主题卡。
4. 创建自定义主题并生成一张卡，确认主题 UID/version 和 Prompt version 冻结。
5. 检查 core、Markdown、generation job、revision、`generation_metadata_json`、model 和 outcome。
6. 在详情页验证章节、编辑、重新生成和历史版本。
7. 模拟 Python 503，确认 Java 有界重试且不写空 revision。
8. 模拟 Markdown partial，确认核心信息可读且页面提供恢复入口。

## 发布与回滚

发布顺序：

1. 先执行 additive 数据库迁移，增加可空的 `generation_metadata_json`；旧代码忽略该列。
2. 部署包含新 schema、workflow、endpoint 和健康状态的 Python 服务。
3. 部署包含 Python client 和 provider 选择的 Java 版本，暂不切换 provider。
4. 运行内部契约 smoke test。
5. 设置 `VOCABULARY_GENERATION_PROVIDER=python` 并重启 Java。
6. 用真实 Basic 和自定义主题卡验收。

回滚只把 provider 显式改回 `java`。回滚不删除新增的可空 metadata 列、Python endpoint 或 Python 已经生成的 revision。迁移稳定后应单独计划删除 Java Prompt 和模型实现，避免永久维护两套生成逻辑。

## 设计取舍

### 未选择通用 Assistant endpoint

通用 Assistant 会引入路由、会话历史和自由文本输出，而后台 generation job 已经有确定输入和输出。专用 workflow 更容易做结构化验证、错误映射和成本控制。

### 未选择 Python 直接消费数据库任务

直接消费会让 Python 同时拥有任务状态、租约和 revision 写入，破坏 Java 的业务事务边界，并增加跨语言数据库一致性风险。

### 未选择消息队列

现有 Java generation job 已经承担持久队列、租约和重试。首版增加消息队列只会复制状态，不解决当前模型边界问题。未来只有在 Python 需要独立水平扩展且 HTTP 等待成为明确瓶颈时再单独评估。

## 参考

- [OpenAI Agents SDK：结构化输出](https://openai.github.io/openai-agents-python/agents/)
- [OpenAI Agents SDK：运行配置](https://openai.github.io/openai-agents-python/running_agents/)
- [OpenAI Agents SDK：Tracing](https://openai.github.io/openai-agents-python/tracing/)
- [FastAPI：Request Body](https://fastapi.tiangolo.com/tutorial/body/)
- [FastAPI：Response Model](https://fastapi.tiangolo.com/tutorial/response-model/)
- [Spring Framework：WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
