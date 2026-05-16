# AI 调试端 P0 真实 Run 闭环 Trae 题目

## 背景

当前 AI 调试端页面已经有基础入口，但还不能真正排查 Agent 请求。下一阶段不先做复杂图表、Langfuse、DeepEval 或 Prompt 管理平台，而是先把最小闭环跑通：

```text
用户发学习助手消息
-> Python Agent 执行
-> 生成 agent_debug_run
-> 记录 RouteRequest / RoutingDecision / target_agent / model / usage / status
-> /ops/agent/runs 看到新记录
-> /ops/agent/runs/:id 看到完整 JSON 和执行链路
```

核心目标：让项目 owner 能回答这些问题：

- 这次用户到底发了什么？
- 后端传给 RouteAgent 的结构化输入是什么？
- RouteAgent 输出了什么路由决策？
- 实际走到了哪个 target agent / workflow？
- 实际用了哪个模型？
- token、latency、status、error 是什么？
- 实际渲染后的 prompt 是什么？

约束：

- 不重写现有 Agent 编排。
- 不改变用户最终响应结构。
- Debug Recorder 失败不能影响学习助手正常回复。
- 首期复用现有管理员登录校验，不新增复杂权限。
- 不接入 Langfuse UI，不做 DeepEval 在线运行。

---

## 题目 1：Agent Debug Run 数据表与只读 API

难度：困难

目标：让后端具备保存和查询 Agent run 的数据基础。

### 1A：新增 Agent Debug P0 数据表

**Prompt**

请为 AI 调试端 P0 增加最小可用的数据表，用于记录一次 Agent 请求和请求内的执行步骤。

建议至少包含：

1. `agent_debug_run`
   - 一次完整 Agent 请求。
   - 保存 user input、RouteRequest、RoutingDecision、targetAgent、workflow、model、usage、status、latency、traceId、error。
2. `agent_debug_step`
   - 一次 run 内的 RouteAgent、目标 Agent、tool call、handoff、model generation 等步骤。
3. `agent_prompt_snapshot`
   - 每次模型调用实际渲染后的 system / developer / user prompt 和 variables。

要求：

1. SQL 放在后端资源目录中，遵循项目现有数据库脚本风格。
2. 建表脚本可重复执行。
3. JSON 字段按项目现有习惯选择 MySQL JSON 或 text。
4. 不修改现有用户、作文、助手消息、订阅表结构。
5. 字段命名要方便前端直接排查，不要只保存一坨不可读 blob。

**验收标准**

- 数据库可创建 `agent_debug_run`、`agent_debug_step`、`agent_prompt_snapshot`。
- 建表脚本重复执行不失败。
- `agent_debug_run` 能保存：
  - run id
  - user id
  - conversation id
  - raw user message
  - route request JSON
  - routing decision JSON
  - intent
  - route type
  - workflow
  - target agent
  - model
  - usage JSON
  - status
  - latency ms
  - trace id
  - error message
- `agent_debug_step` 能表达 step 顺序、step 类型、agent name、input JSON、output JSON、usage JSON、error。
- `agent_prompt_snapshot` 能保存 prompt key、agent name、model、system prompt、developer prompt、user prompt、variables JSON、prompt hash。
- 不影响后端启动和现有测试。

### 1B：新增 Agent Debug 只读查询 API

**Prompt**

请新增 AI 调试端后端只读 API，用于前端查询真实 Agent run。

建议接口：

```text
GET /api/ops/agent/runs
GET /api/ops/agent/runs/{runId}
GET /api/ops/agent/runs/{runId}/steps
GET /api/ops/agent/runs/{runId}/prompts
```

要求：

1. 复用现有 JWT 和管理员身份校验。
2. 暂不新增细粒度权限。
3. 列表接口支持分页。
4. 列表接口支持基础筛选：
   - status
   - intent
   - targetAgent
   - model
   - userId
   - conversationId
   - createdFrom
   - createdTo
5. 详情接口返回 run 基础信息、RouteRequest、RoutingDecision、usage、traceId、error。
6. steps 和 prompts 按执行顺序或创建时间排序。

**验收标准**

- 未登录访问返回 401 或走现有登录拦截。
- 非管理员访问返回 403 或被现有管理员守卫拦截。
- 管理员访问 `GET /api/ops/agent/runs` 返回分页结构。
- 空数据时返回空列表，不报错。
- 不存在的 `runId` 返回 404 或明确错误。
- 请求参数非法时返回明确错误。
- 不影响现有 `/api/admin/*` 和学习助手接口。

---

## 题目 2：Python Debug Recorder 最小接入

难度：困难

目标：让一次真实学习助手请求能产生 debug run。

### 2A：实现 DebugRecorder 抽象

**Prompt**

请在 Python Agent 编排层实现 `DebugRecorder` 抽象，用于统一记录 Agent run、step、prompt snapshot。

要求：

1. 提供最小方法：
   - `start_run(...)`
   - `record_step(...)`
   - `record_prompt_snapshot(...)`
   - `finish_run(...)`
   - `fail_run(...)`
2. Recorder 可以通过 HTTP 调后端 API 或写入数据库，选择一种实现即可，但边界要清晰。
3. Recorder 写入失败不能影响主请求。
4. Recorder 内部捕获异常并记录 warning。
5. 支持环境变量关闭，例如 `AI_AGENT_DEBUG_RECORDER_ENABLED=false`。
6. 不要把 Debug Recorder 逻辑写进 prompt。
7. 不要让各个 Agent 自己拼数据库 SQL。

**验收标准**

- Recorder 关闭时，学习助手请求正常。
- Recorder 开启时，一次请求会创建 run 记录。
- Recorder 写入失败时，学习助手仍能正常返回。
- Python 代码中有清晰的 DebugRecorder 边界。
- 至少有最小单元测试或脚本验证 `start_run -> record_step -> finish_run`。

### 2B：接入 RouteAgent 和目标 Agent 执行链路

**Prompt**

请把 DebugRecorder 接入现有 RouteAgent 和目标 Agent 执行链路。

记录要求：

1. RouteAgent 执行前记录 RouteRequest。
2. RouteAgent 执行后记录 RoutingDecision。
3. 记录 RouteAgent 实际使用模型。
4. 记录目标 agent / workflow。
5. 目标 Agent 执行前记录输入。
6. 目标 Agent 执行后记录输出摘要。
7. 能取到 usage 时记录 input tokens、cached input tokens、output tokens、requests。
8. 能取到 trace id / response id 时记录。
9. 失败时记录 failed 状态和 error message。

约束：

- 不改变现有路由决策 schema。
- 不改变用户最终响应结构。
- 不把完整敏感凭证写入 debug 记录。

**验收标准**

- 发送 `帮我润色这句话：I very like English.` 后能生成一条 run。
- run 中能看到 raw user message。
- run 中能看到 RouteRequest。
- run 中能看到 RoutingDecision。
- run 中能看到 targetAgent 或 workflow。
- run 中能看到 model。
- run 中能看到 usage 或明确为空。
- 失败请求能记录 failed 状态和错误原因。

---

## 题目 3：Agent Runs 列表接真实数据

难度：中等偏难

目标：让 `/ops/agent/runs` 成为 AI 调试端默认入口，而不是空壳页。

### 3A：接入真实 Runs 列表 API

**Prompt**

请将 `/ops/agent/runs` 接入 `GET /api/ops/agent/runs`。

页面要求：

1. 展示真实 run 列表。
2. 支持分页。
3. 支持筛选：
   - status
   - intent
   - targetAgent
   - model
   - userId
   - conversationId
   - time range
4. 表格字段至少包含：
   - createdAt
   - raw user message 摘要
   - intent
   - routeType
   - workflow / targetAgent
   - model
   - totalTokens
   - latencyMs
   - status
5. 点击行进入 `/ops/agent/runs/:id`。

**验收标准**

- 有数据时能看到真实 run 列表。
- 无数据时显示清楚空态。
- 加载中有 loading 状态。
- 接口失败有错误提示。
- 筛选条件变化后重新查询。
- 分页可用。
- 用户输入长文本被截断，不撑破表格。
- 点击行能进入对应详情页。

### 3B：补齐列表页排查体验

**Prompt**

请完善 Agent Runs 列表页的排查体验，让 owner 能快速发现异常请求。

要求：

1. failed / partial 状态有明显标识。
2. 高 latency 或高 token 请求可以被识别。
3. 列表支持复制 run id。
4. 列表支持复制 trace id；没有 trace id 时不显示复制按钮。
5. 表格在 1440px 和 390px 宽度下不明显溢出。
6. 保留返回业务后台入口。

**验收标准**

- failed 请求在列表中容易识别。
- latency 和 tokens 字段不会显示 `NaN`。
- run id 可复制。
- trace id 可复制。
- 移动端宽度下页面可横向滚动或合理折叠。
- `/admin/dashboard` 和 `/app` 返回入口可用。

---

## 题目 4：Run 详情页接真实数据

难度：困难

目标：让单次 Agent 请求可以完整复盘。

### 4A：展示 RouteRequest / RoutingDecision / Steps

**Prompt**

请将 `/ops/agent/runs/:id` 接入真实 run 详情、steps 和 prompts 数据。

详情页排查顺序：

1. 基础信息：
   - run id
   - trace id
   - user id
   - conversation id
   - status
   - model
   - latency
   - createdAt
2. RouteRequest JSON。
3. RoutingDecision JSON。
4. Steps 时间线。
5. Usage。
6. Error。

交互要求：

1. JSON 使用可读格式展示。
2. 支持复制 RouteRequest。
3. 支持复制 RoutingDecision。
4. 支持复制完整 debug JSON。
5. 不存在的 run id 显示 404 或明确空态。

**验收标准**

- 真实 run id 能打开详情页。
- RouteRequest JSON 可读。
- RoutingDecision JSON 可读。
- 能看到 intent、routeType、workflow、targetAgent、confidence。
- steps 按执行顺序展示。
- usage 显示 input tokens、cached input tokens、output tokens、requests。
- failed run 显示 error message。
- 复制完整 debug JSON 可用。

### 4B：展示 Prompt Snapshot 和 Model IO

**Prompt**

请在 Run 详情页展示本次请求相关的 Prompt Snapshot 和 Model IO。

要求：

1. Prompt Snapshot 展示：
   - promptKey
   - promptVersion
   - promptHash
   - agentName
   - model
   - systemPrompt
   - developerPrompt
   - userPrompt
   - variablesJson
2. Model IO 展示：
   - model
   - input 摘要或 JSON
   - output 摘要或 JSON
   - response id
   - usage
3. 长 prompt 默认折叠，可以展开。
4. 支持复制单个 prompt snapshot。
5. 敏感字段需要脱敏。

**验收标准**

- Run 详情能看到真实 prompt snapshot。
- prompt 为空时显示明确空态。
- 长 prompt 不撑破页面。
- 能复制单个 prompt snapshot。
- 能看到模型字段。
- 能看到 response id；没有时显示为空态。
- 页面不展示 API key、Authorization header、cookie、token 原文。

---

## 题目 5：Prompt 调试页从静态占位改为真实查询

难度：中等

目标：让 `/ops/agent/prompts` 能查询真实渲染后的 prompt snapshot。

### 5A：Prompt Snapshot 列表查询

**Prompt**

请将 `/ops/agent/prompts` 接入真实 prompt snapshot 查询。

筛选项：

1. promptKey。
2. promptHash。
3. agentName。
4. model。
5. time range。

列表字段：

1. createdAt。
2. promptKey。
3. promptHash。
4. agentName。
5. model。
6. sourceRunId。
7. system/developer/user prompt 摘要。

**验收标准**

- 有数据时展示真实 prompt snapshot。
- 无数据时显示空态。
- 能按 promptKey 查询。
- 能按 model 查询。
- 能通过 sourceRunId 跳转到 Run 详情。
- prompt 摘要不会撑破列表。

### 5B：Prompt Snapshot 详情与复制

**Prompt**

请完善 Prompt 调试页详情查看能力。

要求：

1. 点击列表项打开详情区域或抽屉。
2. 详情展示 systemPrompt、developerPrompt、userPrompt、variablesJson。
3. 支持复制完整 prompt snapshot JSON。
4. 支持复制 promptHash。
5. 支持打开对应 Run 详情。

**验收标准**

- 点击一条 prompt snapshot 能看到完整内容。
- variablesJson 可读。
- 复制完整 JSON 可用。
- promptHash 可复制。
- 打开对应 Run 详情可用。
- 页面不展示敏感凭证。

---

## 题目 6：P0 安全、测试与最终验收

难度：中等

目标：确保 AI 调试端可用、可验证、不会泄露敏感信息。

### 6A：敏感字段脱敏

**Prompt**

请为 Agent Debug 记录和展示增加统一脱敏策略。

必须脱敏：

1. OpenAI API key。
2. Authorization header。
3. Cookie。
4. access token。
5. refresh token。
6. 其它包含 `token`、`secret`、`apiKey`、`password` 的字段。

要求：

1. 后端返回给前端的数据默认脱敏。
2. 前端展示和下载 JSON 都使用脱敏版本。
3. 脱敏逻辑尽量集中，不要散落在页面组件里。

**验收标准**

- Debug JSON 中不出现 `sk-` 开头的 API key。
- Debug JSON 中不出现 Authorization header 原文。
- Debug JSON 中不出现 Cookie 原文。
- Debug JSON 中不出现 access token 或 refresh token 原文。
- 单元测试覆盖至少 5 类敏感字段。

### 6B：P0 回归验证

**Prompt**

请完成 AI 调试端 P0 的自动化和手工验收。

自动验证建议：

```powershell
cd backend
.\mvnw.cmd -q test

cd web
npm run build

cd docs
npm run build
```

如果接入 Python Recorder：

```powershell
python\ai_orchestrator\.venv\Scripts\python.exe -m pytest
```

手工验收：

1. 管理员登录后访问 `/ops/agent/runs`。
2. 普通用户不能访问 `/ops/agent/runs`。
3. 发送 `帮我润色这句话：I very like English.`。
4. 在 Runs 列表看到新 run。
5. 打开详情能看到 RouteRequest。
6. 打开详情能看到 RoutingDecision。
7. 打开详情能看到 targetAgent、model、usage。
8. 能看到 Prompt Snapshot。
9. 能复制完整 debug JSON。
10. 原有 `/admin/dashboard`、`/admin/subscriptions`、`/app/assistant` 正常。

**验收标准**

- 后端测试通过或明确说明无法运行原因。
- 前端构建通过。
- 文档构建通过。
- Python Recorder 测试通过或明确说明未接入原因。
- Debug Recorder 失败不会导致学习助手请求失败。
- P0 手工验收结果被记录在最终回复中。

---

## 最终交付标准

- `/ops/agent/runs` 默认展示真实 Agent run。
- `/ops/agent/runs/:id` 能复盘一次请求的完整链路。
- RouteRequest、RoutingDecision、targetAgent、model、usage、status 都能看见。
- Prompt Snapshot 能从真实请求中查询和复制。
- Debug Recorder 不影响用户正常使用。
- 敏感字段不泄露。
- 业务管理员端不混入 AI 调试细节。
