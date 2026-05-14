# AI 调试端 / Agent Debug Center Trae 实现题目

## 背景

当前项目需要建设一个独立的 AI 调试端，用于排查学习助手和 Agent 工作流。

业务管理员端继续负责用户、作文、题库、Rubric、审计和业务数据看板；AI 调试端负责查看 Agent 请求、路由决策、Prompt、模型输入输出、usage、trace 和 eval case。

首期路径约定：

```text
/ops/agent/*
```

首期只有项目 owner 使用，不要求新增复杂权限系统，先复用现有管理员登录校验。

设计原则：

- AI 调试端不是普通业务后台，要更像 AI workflow observability / AI Ops 控制台。
- 首期先让请求链路、Prompt、模型输出、usage 能看见。
- 不要重写现有 Agent 编排。
- 不要让线上请求依赖 Langfuse、DeepEval 等外部平台成功。
- Debug Recorder 失败不能影响用户正常使用学习助手。

---

## 题目 1：AI 调试端前端信息架构

难度：中等

目标：建立 `/ops/agent/*` 的独立前端入口，让 AI 调试端和业务管理员端在信息架构上分开。

### 小题 1A：新增 AI 调试端布局与路由

#### Prompt

请在前端新增 AI 调试端入口，路径使用 `/ops/agent/*`。

要求：

1. 新增独立布局，例如 `OpsAgentLayout.vue`。
2. 新增路由：
   - `/ops/agent/runs`
   - `/ops/agent/runs/:id`
   - `/ops/agent/prompts`
   - `/ops/agent/eval-cases`
3. 这些路由复用现有管理员登录校验。
4. 暂时不要新增新的权限字段。
5. AI 调试端布局应和业务管理员端区分开，但可以复用现有 admin 样式变量。
6. 在业务管理员端侧边栏提供 `AI Ops` 入口。
7. AI 调试端内要能返回业务管理员端和主站。

#### 验收要求

- 登录管理员账号后可以访问 `/ops/agent/runs`。
- 未登录访问 `/ops/agent/runs` 会跳转登录。
- 非管理员账号不能访问 `/ops/agent/*`。
- `/admin/dashboard` 不受影响。
- 业务管理员端侧边栏存在 `AI Ops` 入口。
- `npm run build` 通过。

### 小题 1B：新增四个空壳页面并明确状态

#### Prompt

为 AI 调试端新增四个空壳页面。

页面要求：

1. `/ops/agent/runs`
   - 标题：Agent 请求记录。
   - 展示筛选区占位。
   - 展示列表表头占位。
2. `/ops/agent/runs/:id`
   - 标题：Agent Run 详情。
   - 展示 RouteRequest、RoutingDecision、Steps 占位区域。
3. `/ops/agent/prompts`
   - 标题：Prompt 调试。
   - 展示 Prompt key、model、agent 筛选占位。
4. `/ops/agent/eval-cases`
   - 标题：Eval Cases。
   - 展示 route_eval、scoring_eval、feedback_eval、out_of_scope_eval 四类卡片。
5. 页面必须明确提示当前是空壳，后续接 Debug Recorder 数据。

#### 验收要求

- 四个页面都能直接刷新打开。
- 页面不会出现空白、报错或未捕获异常。
- 空态文案清楚说明“真实数据接入后展示”。
- 页面布局在 1440px 和 390px 宽度下都不明显溢出。
- `npm run build` 通过。

---

## 题目 2：Agent Debug Recorder 数据模型与后端查询 API

难度：困难

目标：在后端建立 AI 调试端的数据基础，让未来每次 Agent 请求都能被保存、查询和回放。

### 小题 2A：设计并落地 Debug Recorder 数据表

#### Prompt

请为 Agent Debug Recorder 增加数据库表和安全建表脚本。

建议表：

1. `agent_debug_run`
   - 一次完整 Agent 请求。
2. `agent_debug_step`
   - 一次 run 内的 RouteAgent、目标 Agent、tool call、workflow 步骤。
3. `agent_prompt_snapshot`
   - 每次模型调用实际使用的 Prompt。
4. `agent_eval_case`
   - 从真实请求沉淀的 eval case。
5. `agent_eval_run`
   - eval 执行结果。

要求：

1. SQL 放在后端资源目录中，使用 safe variant，不强依赖外键。
2. 字段要覆盖：
   - run id
   - user id
   - conversation id
   - trace id
   - status
   - route request JSON
   - routing decision JSON
   - model
   - usage
   - latency
   - error message
   - created_at / updated_at
3. JSON 字段使用 MySQL JSON 或 text，按项目现有习惯选择。
4. 不要修改现有用户、作文、助手消息表结构。

#### 验收要求

- SQL 可重复执行，不会因为表已存在失败。
- 执行后能创建所有 Debug Recorder 表。
- 表名、字段名语义清晰。
- 不影响现有后端启动。
- 后端测试或至少 `./mvnw.cmd -q test` 通过；如果环境原因不能跑，需要说明原因。

### 小题 2B：新增后端只读查询 API

#### Prompt

请新增 AI 调试端后端查询 API，先只做只读接口。

建议接口：

```text
GET /api/ops/agent/runs
GET /api/ops/agent/runs/{runId}
GET /api/ops/agent/runs/{runId}/steps
GET /api/ops/agent/runs/{runId}/prompts
```

要求：

1. 复用现有 JWT 和管理员校验。
2. 暂不新增细粒度权限。
3. 列表接口支持分页。
4. 列表接口至少支持以下筛选：
   - status
   - intent
   - targetAgent
   - model
   - userId
   - conversationId
   - createdFrom
   - createdTo
5. 详情接口返回 run 基础信息、route request、routing decision、usage、trace id。
6. steps 和 prompts 需要按时间或 step_order 排序。

#### 验收要求

- 未登录请求返回 401。
- 非管理员请求返回 403 或被现有守卫拦截。
- 管理员请求能拿到分页结构。
- 空数据时返回空列表，不报错。
- 请求参数非法时返回明确错误。
- 不影响现有 `/api/admin/*` 接口。

---

## 题目 3：Python Agent Debug Recorder 接入

难度：困难

目标：在 Python Agent 编排层记录真实的 RouteAgent、目标 Agent、模型输出和 usage。

### 小题 3A：实现 DebugRecorder 抽象

#### Prompt

请在 Python Agent 编排服务中实现一个 DebugRecorder 抽象，用于统一记录 Agent run、step 和 prompt snapshot。

要求：

1. 新增 DebugRecorder 类或模块。
2. 提供以下能力：
   - `start_run(...)`
   - `record_step(...)`
   - `record_prompt_snapshot(...)`
   - `finish_run(...)`
   - `fail_run(...)`
3. Recorder 写入失败不能影响主请求。
4. Recorder 内部需要捕获异常并记录 warning。
5. 支持环境变量关闭，例如：
   - `AI_AGENT_DEBUG_RECORDER_ENABLED=false`
6. 首期可以通过 HTTP 调后端 API，也可以先写入后端数据库；选择一种即可，但要保持边界清晰。

#### 验收要求

- Recorder 关闭时，Agent 请求仍正常。
- Recorder 写入失败时，Agent 请求仍正常。
- Recorder 开启时，会为一次请求生成 run 记录。
- 代码中没有把数据库细节散落到各个 Agent。
- Python 单元测试或最小脚本验证通过。

### 小题 3B：接入 RouteAgent 与目标 Agent 执行链路

#### Prompt

请把 DebugRecorder 接入现有 Agent 工作流。

记录要求：

1. RouteAgent 执行前记录 RouteRequest。
2. RouteAgent 执行后记录 RoutingDecision。
3. 记录 RouteAgent 使用的模型。
4. 记录 OpenAI response id / trace id / usage，如果 SDK 可取到。
5. 目标 Agent 或 workflow 执行前记录输入。
6. 目标 Agent 或 workflow 执行后记录输出。
7. 失败时记录 error message 和 failed 状态。

约束：

- 不要改变现有路由决策 schema。
- 不要改变用户最终响应结构。
- 不要让调试记录逻辑污染 Agent prompt。

#### 验收要求

- 发送一条学习助手消息后，能看到至少一个 run。
- run 中能看到 RouteRequest 和 RoutingDecision。
- 如果进入目标 Agent，能看到 target agent step。
- 失败请求能记录 failed 状态和错误原因。
- 学习助手原有功能不退化。

---

## 题目 4：AI 调试端真实数据接入

难度：困难

目标：把 `/ops/agent/*` 空壳页面接入真实 API，让项目 owner 可以实际排查 Agent 请求。

### 小题 4A：请求记录列表接入真实数据

#### Prompt

请将 `/ops/agent/runs` 接入后端真实数据。

要求：

1. 新增前端 API 模块，例如 `src/api/opsAgent.ts`。
2. 列表页接入分页查询。
3. 支持筛选：
   - status
   - intent
   - targetAgent
   - model
   - userId
   - conversationId
   - time range
4. 表格展示：
   - createdAt
   - userMessage 摘要
   - intent
   - workflow / targetAgent
   - model
   - totalTokens
   - latencyMs
   - status
5. 点击行进入 `/ops/agent/runs/:id`。
6. 空数据、加载中、加载失败都要有明确状态。

#### 验收要求

- 有数据时能看到真实 run 列表。
- 无数据时显示空态，不报错。
- 筛选条件改变后会重新查询。
- 分页可用。
- 点击行能进入详情页。
- 列表页不显示超长原文，长文本需要截断。

### 小题 4B：Run 详情页接入真实数据

#### Prompt

请将 `/ops/agent/runs/:id` 接入真实 run 详情。

详情页展示：

1. 基础信息：
   - run id
   - trace id
   - user id
   - conversation id
   - status
   - model
   - latency
2. RouteRequest JSON。
3. RoutingDecision JSON。
4. Steps 时间线。
5. Prompt snapshots。
6. Model input / output。
7. Usage。
8. 错误信息。

交互要求：

1. JSON 使用 `<pre>` 或 JSON viewer 风格展示。
2. 支持复制完整 debug JSON。
3. 支持打开 OpenAI trace 链接；没有 trace 时不显示按钮。
4. 敏感字段需要脱敏展示。

#### 验收要求

- 真实 run id 能打开详情页。
- 不存在的 run id 显示 404 或明确空态。
- RouteRequest / RoutingDecision JSON 可读。
- steps 按执行顺序展示。
- Prompt snapshot 不会撑破页面。
- 复制 debug JSON 可用。

---

## 题目 5：Eval Dataset Builder 首版

难度：中等偏难

目标：让项目 owner 能从真实 Agent 请求中挑选样本，沉淀为 eval case。

### 小题 5A：从 Run 详情保存为 Eval Case

#### Prompt

请在 `/ops/agent/runs/:id` 增加“保存为 Eval Case”能力。

要求：

1. 按钮位置放在 Run 详情页操作区。
2. 点击后打开表单或弹窗。
3. 表单字段：
   - caseType
   - title
   - tags
   - inputJson
   - expectedJson
4. 默认从当前 run 填充 inputJson。
5. expectedJson 需要人工编辑，不要自动伪造。
6. 保存到后端 `agent_eval_case`。

#### 验收要求

- 能从一个 run 创建 eval case。
- `caseType` 只能选择支持类型。
- `expectedJson` 不是合法 JSON 时不能保存。
- 保存成功后能跳转或提示进入 Eval Cases 页面。
- 不会修改原始 debug run。

### 小题 5B：Eval Cases 列表与详情编辑

#### Prompt

请完善 `/ops/agent/eval-cases` 页面，展示和管理 eval case。

要求：

1. 列表展示：
   - title
   - caseType
   - tags
   - sourceRunId
   - status
   - createdAt
2. 支持按 caseType、status、tag 搜索。
3. 支持查看详情。
4. 支持编辑：
   - title
   - tags
   - expectedJson
   - status
5. 支持归档，不做物理删除。

#### 验收要求

- 从 Run 详情保存的 case 能在列表中看到。
- 能筛选 route_eval / scoring_eval 等类型。
- 编辑 expectedJson 后能保存。
- 非法 JSON 有前端校验。
- 归档后默认列表不再展示，筛选 archived 时可见。

---

## 题目 6：安全、脱敏与回归验证

难度：中等

目标：确保 AI 调试端不会泄露敏感信息，也不会破坏现有管理员端和学习助手。

### 小题 6A：敏感字段脱敏策略

#### Prompt

请为 AI 调试端增加统一脱敏策略。

需要脱敏：

1. API key。
2. Authorization header。
3. Cookie。
4. refresh token。
5. access token。
6. 邮箱、手机号可按项目需要部分脱敏。
7. 用户作文和选中文本默认可展示，但下载 JSON 时要支持脱敏版本。

要求：

1. 脱敏逻辑尽量放在后端或统一工具中。
2. 前端不要自己猜所有敏感字段。
3. 下载 debug JSON 默认使用脱敏版本。
4. 只有明确需要时才展示完整文本。

#### 验收要求

- Debug JSON 中不出现 `sk-` 开头的 API key。
- Debug JSON 中不出现 Authorization header 原文。
- 下载 JSON 默认脱敏。
- 页面不会把 cookie 或 token 直接展示出来。
- 单元测试覆盖至少 5 类敏感字段。

### 小题 6B：回归测试与手工验收清单

#### Prompt

请为 AI 调试端补充验证。

自动验证建议：

```powershell
cd web
npm run build

cd backend
./mvnw.cmd -q test
```

如新增 Python Recorder：

```powershell
python\ai_orchestrator\.venv\Scripts\python.exe -m pytest
```

手工验收：

1. 管理员登录后访问 `/ops/agent/runs`。
2. 普通用户不能访问 `/ops/agent/runs`。
3. 发送一条学习助手消息。
4. 在 AI 调试端看到新 run。
5. 打开 run 详情，能看到 RouteRequest 和 RoutingDecision。
6. 能复制 debug JSON。
7. 从 run 保存 eval case。
8. 原有 `/admin/dashboard`、`/admin/users`、`/app/assistant` 正常。

#### 验收要求

- 自动构建通过。
- 关键后端测试通过。
- 学习助手消息发送不因为 Debug Recorder 失败而失败。
- 业务管理员端不受影响。
- 记录一份最终手工验收结果。

---

## 最终交付标准

- `/ops/agent/*` 作为 AI 调试端独立存在。
- 业务管理员端 `/admin/*` 不被混入 Agent 调试细节。
- Agent Debug Recorder 能记录真实请求。
- AI 调试端能展示真实 run、steps、prompts 和 usage。
- 能从真实 run 创建 eval case。
- 调试记录失败不影响用户请求。
- 敏感字段不会泄露。
- 前端、后端、Python 相关验证通过或明确说明未运行原因。
