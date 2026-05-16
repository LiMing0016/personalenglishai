# OpenAI Platform 可观测接入 Trae 题目

## 背景

当前 PEAI 多 Agent 工作流已经开始把 run、trace、路由、模型和业务上下文写入 OpenAI Agents SDK trace metadata。第一阶段目标不是先做完整自研调试平台，而是先让当前工作流能在 OpenAI Platform Traces 中被搜索、过滤和复盘。

核心链路：

```text
用户发送学习助手消息
-> AssistantAgentService 生成 run_id / trace_id
-> RouteAgent 产出 RoutingDecision
-> Target Agent 执行
-> OpenAI Platform Trace 展示 workflow / agent / usage / metadata
-> PEAI 调试端后续用同一批 id 做映射
```

约束：

- metadata 只放短索引字段，不放完整作文、完整 prompt、token、cookie 或隐私文本。
- 不改变用户最终回复。
- trace 写入失败不能影响学习助手正常回复。
- 先保证 OpenAI Platform 可观测，再接 PEAI 自己的 Debug Center。

---

## 题目 1：OpenAI Platform Trace Metadata 验收与补字段

难度：中等偏难

目标：确认每次学习助手请求在 OpenAI Platform Traces 中都能看到关键 metadata。

### 1A：生成真实 trace 并检查 metadata

**Prompt**

请重启 Python agent 服务，发送至少 5 条学习助手测试消息，并在 OpenAI Platform Traces 中检查每条 trace 的 metadata 是否完整。

测试消息建议覆盖：

1. `帮我润色这句话：I very like English.`
2. `帮我分析这篇作文能得多少分：I think mobile phone is important...`
3. `我现在是考研英语二，帮我讲一下 depend on 的用法`
4. `给我设计一组介词搭配练习`
5. `你好`

重点检查字段：

- `run_id`
- `trace_id`
- `conversation_id`
- `client_message_id`
- `model`
- `mode`
- `intent`
- `study_stage`
- `target_exam`
- `source_page`
- `route_type`
- `workflow`
- `target_agent`
- `route_confidence`

**验收标准**

- OpenAI Platform Traces 中能按 `run_id`、`conversation_id`、`model` 搜索或定位请求。
- RouteAgent 顶层 trace 能看到学习上下文字段。
- Target Agent span 能看到 `target_agent`、`route_type`、`workflow`。
- 不出现完整作文、完整 prompt、Authorization、Cookie、access token。
- 问候类消息允许 `target_agent` 为空，但必须能看到 `intent=free_chat` 或对应路由结果。

### 1B：补齐缺失字段和单元测试

**Prompt**

如果验收中发现字段缺失，请补齐 Python agent metadata 构造逻辑，并为缺失字段补充单元测试。

要求：

1. metadata 构造必须经过统一清洗函数。
2. 长字段需要截断，避免 OpenAI metadata 过大。
3. 布尔值和数字需要稳定序列化，便于 OpenAI Platform 过滤。
4. 不允许把完整 `RouteRequest` 或 `RoutingDecision` 大 JSON 放进 metadata。

**验收标准**

- Python 单元测试覆盖 top-level workflow metadata。
- Python 单元测试覆盖 target agent metadata。
- Python 单元测试覆盖空字段、长字段、布尔字段。
- `pytest python/ai_orchestrator/tests -q` 通过。

---

## 题目 2：PEAI 调试端与 OpenAI Trace 映射

难度：困难

目标：让 PEAI 调试端后续可以从本地 run 反查 OpenAI Platform trace。

### 2A：调试端展示 trace 关联字段

**Prompt**

请在 AI 调试端的 Agent Runs 列表和 Run 详情中展示 OpenAI trace 关联字段。

建议字段：

- `runId`
- `traceId`
- `conversationId`
- `clientMessageId`
- `model`
- `targetAgent`
- `routeType`
- `workflow`
- `openaiResponseId`

要求：

1. 列表页显示 `runId`、`traceId`、`model`、`targetAgent`。
2. 详情页展示完整 metadata JSON。
3. 提供复制 `runId` 和 `traceId` 的按钮。
4. 如果没有 OpenAI trace 链接，先展示 trace id，不硬编码平台 URL。

**验收标准**

- 从真实学习助手请求进入 `/ops/agent/runs` 后能看到新 run。
- 点击 run 详情能复制 `runId` 和 `traceId`。
- 详情页能看到本地 debug JSON 和 OpenAI trace metadata 的对应关系。
- 空字段显示为 `-`，不能显示 `undefined` 或页面报错。

### 2B：后端 API 保留 trace 字段

**Prompt**

请检查后端 Agent Debug API 的 response DTO，确保不会丢失 Python 返回的 trace 关联字段。

要求：

1. 列表接口保留 `runId`、`traceId`、`model`、`targetAgent`、`status`。
2. 详情接口保留 `routeRequest`、`routingDecision`、`openai`、`usage`、`latencyMs`。
3. 字段命名和前端 TypeScript 类型一致。
4. 如果数据库暂无字段，先通过 metadata JSON 兼容读取。

**验收标准**

- 后端测试覆盖 DTO 映射。
- 前端 TypeScript 类型不需要使用 `any` 才能访问这些字段。
- 真实接口返回字段和页面展示一致。

---

## 题目 3：Model Sandbox 与 Eval Case 元数据预留

难度：困难

目标：为后续“测试其他模型”和“从真实请求生成 eval case”做好最小数据约定。

### 3A：定义 run type 和实验字段

**Prompt**

请为 Agent Debug Run 设计并文档化以下运行类型：

```text
live
replay
model_experiment
eval
```

同时预留实验字段：

- `sourceRunId`
- `experimentScope`
- `candidateModel`
- `baselineModel`
- `evalCaseId`
- `datasetId`

要求：

1. 第一版不需要实现完整 Model Sandbox。
2. 先保证字段命名、含义、存储位置稳定。
3. `model_experiment` 不能写入正式用户会话。
4. `eval` 不能修改用户画像、学习计划或作文记录。

**验收标准**

- 文档中说明每个 `runType` 的用途和边界。
- API schema 或 DTO 中明确这些字段是否可为空。
- 后续实现 Model Sandbox 时不需要重命名字段。

### 3B：Eval Case Builder 的最小样本格式

**Prompt**

请设计从真实 run 保存为 eval case 的最小 JSON 样本格式。

至少包含：

- `caseType`
- `sourceRunId`
- `input`
- `expected`
- `metadata`
- `tags`
- `createdBy`
- `reviewStatus`

建议 `caseType` 支持：

- `route_decision`
- `scoring`
- `feedback_quality`
- `polish`

**验收标准**

- 能从一条真实 run 还原 eval 输入。
- `expected` 支持人工确认，不要求自动完美生成。
- `metadata` 包含学段、考试、模型、route type、target agent。
- 不保存敏感 token 或 cookie。

---

## 题目 4：Trace Metadata 隐私与失败兜底

难度：中等

目标：保证可观测不会变成隐私风险，也不会影响主流程。

### 4A：metadata allowlist 与脱敏规则

**Prompt**

请为 OpenAI trace metadata 增加 allowlist 说明和测试，确保只有允许的短字段可以进入 metadata。

允许字段示例：

- id 类：`run_id`、`trace_id`、`conversation_id`、`client_message_id`
- 配置类：`model`、`mode`、`intent`、`scope`
- 业务短标签：`study_stage`、`target_exam`、`source_page`
- 路由短标签：`route_type`、`workflow`、`target_agent`、`route_confidence`

禁止字段示例：

- `authorization`
- `cookie`
- `access_token`
- `refresh_token`
- `essay_text`
- `full_prompt`
- `raw_user_message`

**验收标准**

- 禁止字段不会出现在 OpenAI Platform metadata。
- 长字段会被截断到固定长度。
- 空值不会导致 trace 创建失败。

### 4B：trace 写入失败不影响用户回复

**Prompt**

请检查 OpenAI trace 创建、flush、metadata 构造失败时的兜底策略。

要求：

1. trace 或 flush 失败只记录日志，不中断学习助手回复。
2. target agent 执行成功时，即使 trace flush 失败也要正常返回用户。
3. 错误日志中保留 `run_id` 和 `trace_id`，方便排查。

**验收标准**

- 单元测试模拟 `flush_traces` 抛错，用户回复仍然成功。
- 日志中能定位失败 trace。
- 不吞掉 target agent 自身的真实业务异常。
