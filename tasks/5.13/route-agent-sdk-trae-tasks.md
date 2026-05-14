# 路由 Agent 按 OpenAI Agents SDK 落地 Trae 实现题目

## 背景

现在需要先落地多 Agent 工作流的第一层：`RouteAgent`。

本轮目标不是实现完整写作评分，也不是拆出很多专门 Agent，而是先让系统具备一个稳定的结构化路由入口。

RouteAgent 第一版只做一件事：

```text
用户输入 + 产品上下文
-> 结构化路由决策 RoutingDecision
-> 后端根据 decision 进入对应 workflow
```

核心原则：

- RouteAgent 只负责判断本轮请求应该进入哪个 workflow。
- RouteAgent 不直接生成评分、诊断、润色、练习或教学内容。
- 用户 `message` 是 RouteAgent 的输入，不应该被原样返回到输出 JSON。
- 当前使用什么模型由运行配置决定，不由 RouteAgent 输出。
- 缺少关键输入时，RouteAgent 应返回 `ask_clarification`，而不是强行进入 workflow。
- 非英语学习请求应返回 `out_of_scope`。

---

## 题目 1：定义路由输入输出 Schema

### Prompt

实现 RouteAgent 第一版所需的输入输出 schema。

要求：

1. 定义 `RouteRequest`。
2. 定义 `RouteRequestContext`。
3. 定义 `RoutingDecision`。
4. 定义 `RoutingNormalizedInputs`。
5. 定义标准 intent 枚举。
6. 定义 route type 枚举。
7. 定义 workflow 名称枚举。
8. 定义 target agent 名称枚举。
9. 字段命名使用 Python 风格。
10. `confidence` 必须限制在 `0.0` 到 `1.0`。
11. list 字段必须使用安全默认值，不能使用共享可变默认值。

第一版 intent：

```text
writing_evaluation
writing_live_coach
topic_analysis
polish
grammar_help
vocab_help
practice_generation
learning_plan
general_chat
unknown
```

第一版 route type：

```text
run_workflow
ask_clarification
answer_direct
out_of_scope
```

`RouteRequest` 至少包含：

```text
message
conversation_id
user_id
study_stage
assistant_mode
context
```

`RouteRequestContext` 至少包含：

```text
selected_text
essay_text
topic_prompt
current_page
active_task
```

`RoutingDecision` 至少包含：

```text
intent
workflow
route_type
target_agent
confidence
required_inputs
missing_inputs
normalized_inputs
reason
```

### 验收标准

- 能创建合法 `RouteRequest`。
- 能创建合法 `RoutingDecision`。
- 非法 intent 校验失败。
- 非法 route type 校验失败。
- `confidence < 0` 或 `confidence > 1` 校验失败。
- `missing_inputs`、`required_inputs` 默认是独立 list。
- 字段能表达“作文评分缺正文”“审题缺题目”“润色缺选中文本”等情况。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_agent_schema
```

---

## 题目 2：设计 RouteAgent Prompt

### Prompt

为 RouteAgent 新增独立 prompt 资产。

要求：

1. Prompt 角色是 `RouteAgent`。
2. 目标是输出结构化 `RoutingDecision`。
3. 明确 RouteAgent 不生成用户可见学习内容。
4. 明确 RouteAgent 不负责评分、诊断、润色、练习生成。
5. 明确 RouteAgent 不决定模型。
6. 明确 RouteAgent 不把原始 message 原样放进输出。
7. 明确 route type 判断规则：
   - 信息足够：`run_workflow`
   - 缺关键输入：`ask_clarification`
   - 简单英语学习问题：`answer_direct`
   - 非英语学习范围：`out_of_scope`
8. 明确缺输入规则：
   - 作文评分必须有 `essay_text`
   - 审题必须有 `topic_prompt`
   - 润色至少需要 `selected_text` 或 `essay_text`
9. Prompt 必须和 `RoutingDecision` 字段对齐。
10. Prompt 不应包含大量评分、润色、诊断业务细节。

### 验收标准

- 有独立 RouteAgent prompt。
- prompt 能被 prompt loader 正常加载。
- prompt 包含 `RoutingDecision`、`route_type`、`missing_inputs`、`confidence`。
- prompt 明确“不生成用户可见学习内容”。
- prompt 不要求输出自然语言正文。
- prompt 不要求模型自己选择后续模型。
- 有测试覆盖 prompt 的关键约束。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_agent_prompt
```

---

## 题目 3：创建结构化 RouteAgent

### Prompt

用 OpenAI Agents SDK 创建结构化 RouteAgent。

要求：

1. 提供一个工厂函数创建 RouteAgent。
2. Agent 名称使用 `RouteAgent`。
3. Agent model 由调用方传入。
4. Agent instructions 使用题目 2 的 RouteAgent prompt。
5. Agent 使用结构化输出类型 `RoutingDecision`。
6. RouteAgent 第一版不挂载 handoff。
7. RouteAgent 第一版不挂载 specialist tools。
8. RouteAgent 只做路由决策，不做后续 workflow 执行。

示意：

```python
Agent(
    name="RouteAgent",
    model=model,
    instructions=route_prompt,
    output_type=RoutingDecision,
)
```

### 验收标准

- 工厂函数能创建 RouteAgent。
- Agent name 是 `RouteAgent`。
- Agent model 等于传入参数。
- Agent instructions 来自 RouteAgent prompt。
- Agent output type 是 `RoutingDecision`。
- Agent 没有 handoff。
- Agent 没有 tools。
- 有结构测试覆盖以上条件。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_agent_structure
```

---

## 题目 4：实现 RouteRequest 输入装配

### Prompt

实现一个轻量输入装配器，把现有产品请求和页面上下文转换成 `RouteRequest`。

要求：

1. 输入装配器只做字段映射和轻量归一化。
2. 不调用模型。
3. 不执行 workflow。
4. `message` 来自用户本轮输入。
5. `study_stage` 透传。
6. `assistant_mode` 透传。
7. 支持传入：
   - `selected_text`
   - `essay_text`
   - `topic_prompt`
   - `current_page`
   - `active_task`
8. 缺失上下文字段时使用 `None`。
9. `user_id` 可以为空。

### 验收标准

- 能从普通聊天请求构建 `RouteRequest`。
- 能从带选中文本的请求构建 `RouteRequest`。
- 能从写作编辑器上下文构建 `RouteRequest`。
- 能正确映射 `message`、`study_stage`、`assistant_mode`。
- 缺失字段不会报错。
- 输入装配器单测不依赖 OpenAI API。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_request_adapter
```

---

## 题目 5：实现 RouteDecisionRunner

### Prompt

实现 RouteDecisionRunner，用于执行 RouteAgent 并返回结构化 `RoutingDecision`。

要求：

1. Runner 接收 `RouteRequest`。
2. Runner 创建或接收 RouteAgent。
3. Runner 调用 OpenAI Agents SDK。
4. Runner 返回 `RoutingDecision`。
5. Runner 不直接执行后续 workflow。
6. Runner 不生成用户可见回复。
7. Runner 支持模型名注入，方便测试和配置。
8. 测试中允许注入 fake runner 或 fake structured result。
9. 异常路径要清楚，不要吞掉异常后返回伪决策。
10. 不打印、记录或泄漏 API key。
11. 调用 Agents SDK 时必须设置可识别的 trace 配置。
12. trace workflow 名称固定为 `PEAI RouteAgent`。
13. trace group 使用当前 `conversation_id`。
14. trace metadata 至少包含：
   - `component`
   - `agent`
   - `conversation_id`
   - `user_id`
   - `study_stage`
   - `assistant_mode`
   - `current_page`
   - `has_essay_text`
   - `has_topic_prompt`
   - `has_selected_text`
15. 开发验收阶段允许开启 sensitive data trace，方便在 OpenAI Platform 查看完整输入 JSON 和结构化输出 JSON。

### 验收标准

- 有 RouteDecisionRunner 或等价 service。
- 成功路径返回 `RoutingDecision`。
- Runner 与 workflow 执行解耦。
- 支持测试注入 fake result。
- 异常路径有明确错误。
- 不改变现有聊天主链路行为。
- OpenAI Trace 中能按 `PEAI RouteAgent` 搜索到路由调用。
- Trace 能按 conversation group 聚合。
- 测试能断言 Runner 调用时传入了 trace 配置。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_decision_runner
```

---

## 题目 5.1：RouteAgent 输入输出 JSON 可观测性

### Prompt

补充 RouteAgent 的调试可观测性，让开发者能在 OpenAI Platform Traces 中看到路由 Agent 发送给模型的完整输入 JSON，以及模型返回的完整结构化 `RoutingDecision` JSON。

要求：

1. RouteAgent 的输入必须是稳定 JSON 字符串。
2. 输入 JSON 必须包含用户本轮 `message` 和产品上下文。
3. 输入 JSON 必须包含：
   - `has_essay_text`
   - `has_topic_prompt`
   - `has_selected_text`
4. 输出必须由 `RoutingDecision` schema 校验。
5. trace 名称必须易搜索。
6. trace metadata 不应包含 API key。
7. 文档必须说明 sensitive data trace 会包含作文正文、题目和选中文本。
8. 文档必须说明生产环境可以关闭 sensitive data trace。
9. RouteAgent 运行完成后需要主动 flush trace exporter，避免后台批量上传导致调试页面短时间内搜不到。

### 验收标准

- 单测覆盖 trace workflow name。
- 单测覆盖 trace group id。
- 单测覆盖 trace metadata。
- 单测覆盖 RouteAgent 运行后触发 trace flush。
- 单测覆盖输入 JSON 包含用户 message。
- OpenAI Platform Traces 中能看到 RouteAgent 的 generation input/output。
- 如果看不到 trace，排查项包含：
  - Python orchestrator 是否走到 RouteDecisionRunner。
  - `OPENAI_AGENTS_DISABLE_TRACING` 是否关闭了 tracing。
  - `trace_include_sensitive_data` 是否关闭。

---

## 题目 5.2：接入 RouteAgent 到新请求链路

### Prompt

把 RouteAgent 接入 Python orchestrator 的新请求链路，支持开发者直接验收 JSON，并让正式学习助手请求先跑一次 RouteAgent 产生 OpenAI Trace。

要求：

1. 新增一个 debug HTTP endpoint：

```text
POST /assistant/route/debug
```

2. debug endpoint 输入使用现有 `AssistantRequest`。
3. debug endpoint 输出直接返回 `RoutingDecision` JSON。
4. debug endpoint 不执行后续 workflow。
5. debug endpoint 不返回用户可见学习回复。
6. `/assistant/run` 在执行旧学习助手回复链路前，先运行 `RouteDecisionRunner`。
7. `/assistant/run/stream` 在执行旧流式回复链路前，先运行 `RouteDecisionRunner`。
8. 第一阶段不直接用 `RoutingDecision` 替换旧回复链路，避免前端聊天只拿到路由 JSON。
9. 支持配置关闭正式请求的预路由，例如：

```text
AI_ASSISTANT_ROUTE_DECISION_ENABLED=false
```

10. RouteAgent 失败时不要阻断旧回复链路，但 debug endpoint 需要返回明确错误。

### 验收标准

- `POST /assistant/route/debug` 返回 `RoutingDecision` JSON。
- 正式 `/assistant/run` 会调用 RouteAgent。
- 正式 `/assistant/run/stream` 会调用 RouteAgent。
- OpenAI Platform Traces 中能看到 `PEAI RouteAgent`。
- 旧的学习助手回复仍然可以正常返回自然语言内容。
- 单测覆盖 debug endpoint。
- 单测覆盖正式 run 预路由。
- 单测覆盖正式 stream 预路由。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_assistant_run_endpoint python.ai_orchestrator.tests.test_assistant_service
```

---

## 题目 6：新增路由回归样例

### Prompt

新增 RouteAgent 的路由回归样例。第一版不要求真实调用 OpenAI API，可以用固定 expected decision 或 fake structured output 验证 schema 与路由表。

要求：

1. 至少 20 条样例。
2. 覆盖全部 intent：
   - `writing_evaluation`
   - `writing_live_coach`
   - `topic_analysis`
   - `polish`
   - `grammar_help`
   - `vocab_help`
   - `practice_generation`
   - `learning_plan`
   - `general_chat`
   - `unknown`
3. 覆盖全部 route type：
   - `run_workflow`
   - `ask_clarification`
   - `answer_direct`
   - `out_of_scope`
4. 覆盖缺输入：
   - 作文评分缺 `essay_text`
   - 审题缺 `topic_prompt`
   - 润色缺 `selected_text` 和 `essay_text`
5. 覆盖已有上下文：
   - 有作文正文
   - 有题目
   - 有选中文本
6. 样例不要依赖真实 OpenAI API。

### 验收标准

- 样例集中管理。
- 每条样例包含：
  - user message
  - context
  - expected intent
  - expected route type
  - expected workflow
  - expected target agent
  - expected missing inputs
- 测试确保所有 intent 至少被覆盖一次。
- 测试确保所有 route type 至少被覆盖一次。
- 测试确保 `unknown` 和 `out_of_scope` 存在。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_route_decision_policy
```

---

## 题目 7：接入写作评分 Workflow 占位

### Prompt

新增 `writing_evaluation_workflow` 占位，让 RouteAgent 的 `writing_evaluation` 决策能落到一个明确 workflow 上。

要求：

1. Workflow 输入包含：
   - `RouteRequest`
   - `RoutingDecision`
2. 第一版只做占位和输入校验。
3. 如果缺少 `essay_text`，返回明确错误或 clarification payload。
4. 如果有 `essay_text`，返回稳定结构化占位结果。
5. 本题不实现完整评分。
6. 本题不调用 ScoringAgent、PolishAgent、PracticeAgent。
7. 为后续 `WritingCoachAgent v1` 留出接口。

### 验收标准

- 有 `writing_evaluation_workflow` 的运行函数。
- 输入 schema 明确。
- 缺作文正文时不会继续执行。
- 有作文正文时返回稳定结构。
- decision intent 不是 `writing_evaluation` 时拒绝或报错。
- 不影响现有聊天主链路。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_writing_evaluation_workflow
```

---

## 题目 8：总体验证

### Prompt

完成 RouteAgent 第一版落地后，运行总体验证并输出变更说明。

要求：

1. 说明新增了哪些 schema。
2. 说明新增了哪个 RouteAgent。
3. 说明新增了哪些测试。
4. 说明当前写作 workflow 只是占位，不做真实评分。
5. 如果新增配置项，说明配置项含义。
6. 不把任务文档移动到 `docs/`。

### 验收标准

至少运行：

```bash
.\python\.venv\Scripts\python.exe -m unittest discover -s python\ai_orchestrator\tests
```

如果改了 docs，再运行：

```bash
cd docs
npm run build
```

如果只完成部分题目，至少运行对应新增测试，并说明未运行哪些测试。

---

## 推荐执行顺序

1. 题目 1：先做 schema。
2. 题目 2：再做 prompt。
3. 题目 3：创建结构化 RouteAgent。
4. 题目 4：做输入装配。
5. 题目 6：补路由回归样例。
6. 题目 5：做 runner。
7. 题目 7：接写作 workflow 占位。
8. 题目 8：总体验证。

## 暂不做内容

- 不实现完整作文评分。
- 不拆 ScoringAgent / ErrorDiagnosisAgent / PolishAgent / PracticeAgent。
- 不接用户画像和长期记忆。
- 不新增通用 agent runtime。
- 不改变现有学习助手主链路行为，除非后续明确要求。
