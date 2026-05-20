# Langfuse 接入 OpenAI Agents SDK 追踪 Trae 题目

## 题目 1：Langfuse 可选初始化

### 1A：新增 Observability 初始化模块

**Prompt**

请在 `python/ai_orchestrator/` 下新增一个轻量 observability 模块，用于按环境变量启用 Langfuse 对 OpenAI Agents SDK 的追踪。要求默认关闭，配置不完整时不启用，初始化失败不能影响 FastAPI 启动或用户请求。

支持环境变量：

```text
LANGFUSE_ENABLED=false
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
LANGFUSE_BASE_URL=https://cloud.langfuse.com
```

兼容旧变量 `LANGFUSE_HOST`：如果 `LANGFUSE_BASE_URL` 为空但 `LANGFUSE_HOST` 有值，则映射到 `LANGFUSE_BASE_URL`。

启用时使用 Langfuse 官方 OpenInference 接入方式：

```python
from openinference.instrumentation.openai_agents import OpenAIAgentsInstrumentor

OpenAIAgentsInstrumentor().instrument()
```

不要使用 `set_trace_processors()` 替换 OpenAI Agents SDK 默认 processor，因为需要继续保留 OpenAI Platform Traces。

**验收标准**

- 默认未配置 Langfuse 时，不启用、不报错。
- `LANGFUSE_ENABLED=true` 但 key 或 base url 不完整时，不启用、不报错，并输出 warning。
- 配置完整时会调用 `OpenAIAgentsInstrumentor().instrument()`。
- 初始化函数幂等，重复调用不会重复 instrument。
- Langfuse 初始化失败不会影响应用启动。

### 1B：增加 Langfuse flush 能力

**Prompt**

请在 observability 模块中增加 `flush_observability()`，用于在请求结束后 flush Langfuse 客户端。该函数只在 Langfuse 已成功初始化后执行；如果 Langfuse 未启用或未配置，直接返回。

flush 失败时只记录 warning，不向外抛异常。

**验收标准**

- Langfuse 未启用时调用 `flush_observability()` 不产生副作用。
- Langfuse 已启用时会尝试调用 Langfuse client 的 `flush()`。
- flush 失败不会影响 assistant 请求。
- 不在 token streaming 的每个 delta 中 flush。

---

## 题目 2：FastAPI 启动接入与健康检查

### 2A：启动时初始化 Langfuse

**Prompt**

请修改 `python/ai_orchestrator/app.py`，在加载 orchestrator 环境变量后初始化 observability。

顺序应保持为：

```text
load_orchestrator_env()
configure_observability()
FastAPI app 初始化
```

不要改变 `/chat`、`/assistant/run`、`/assistant/run/stream`、`/prompt-sheet/chat`、`/prompt-sheet/generate` 的业务行为。

**验收标准**

- 应用启动时会执行 observability 初始化。
- Langfuse 默认关闭时应用正常启动。
- Langfuse 配置不完整时应用正常启动。
- 现有 assistant 和 prompt sheet 接口不受影响。

### 2B：健康检查返回 Langfuse 状态

**Prompt**

请扩展 `/health` 响应，增加 Langfuse tracing 是否已配置成功的字段，例如：

```json
{
  "ok": true,
  "configured": true,
  "promptSheetConfigured": true,
  "model": "gpt-5.4-mini",
  "langfuseTracing": false
}
```

只允许新增字段，不要删除或重命名已有字段。

**验收标准**

- 未启用 Langfuse 时 `/health.langfuseTracing=false`。
- Langfuse 配置完整且初始化成功时 `/health.langfuseTracing=true`。
- `/health` 现有字段仍然保留。

---

## 题目 3：请求结束后的 Trace Flush

### 3A：Assistant workflow flush Langfuse

**Prompt**

请修改 `python/ai_orchestrator/assistant_service.py`，在现有 OpenAI Agents SDK trace flush 后，同步调用 `flush_observability()`。

保留现有逻辑：

```python
from agents import flush_traces

flush_traces()
```

然后再调用：

```python
from python.ai_orchestrator.observability import flush_observability

flush_observability()
```

Langfuse flush 失败时不能影响请求。

**验收标准**

- `flush_traces()` 仍然会被调用。
- assistant run 结束后会尝试 flush Langfuse。
- Langfuse flush 异常只记录 warning。
- 用户请求响应不因 Langfuse flush 失败而失败。

### 3B：RouteAgent debug flush Langfuse

**Prompt**

请修改 `python/ai_orchestrator/services/route_decision_runner.py`，在 RouteAgent 单独运行并 flush OpenAI trace 后，同步调用 `flush_observability()`。

不要改变 `flush_trace=False` 时的行为：正式 assistant workflow 内部调用 RouteAgent 时，不应让 RouteAgent 自己提前 flush。

**验收标准**

- `/assistant/route/debug` 这类单独 RouteAgent 调用结束后会 flush OpenAI trace 和 Langfuse。
- 正式 `/assistant/run` 中 RouteAgent 仍可通过 `flush_trace=False` 延迟到外层 workflow 统一 flush。
- Langfuse flush 失败不影响路由结果返回。

---

## 题目 4：收敛 Trace Metadata

### 4A：限制 Assistant workflow metadata 字段数量

**Prompt**

请检查并收敛 `AssistantAgentService` 中传给 OpenAI Agents SDK `trace()` 的 metadata。OpenAI trace metadata 字段数量有限制，不要把所有业务字段都塞进去。

顶层 assistant workflow trace 建议只保留这些索引字段：

```text
environment
component
run_id
trace_id
conversation_id
client_message_id
model
mode
intent
scope
study_stage
target_exam
source_page
attachment_count
has_selection
route_decision_enabled
```

完整业务调试数据应继续由内部 Agent Debug Center / recorder 保存，不放进 trace metadata。

**验收标准**

- 顶层 workflow trace metadata 字段数量不超过 16。
- metadata 不包含完整作文、选中文本、附件内容、authorization、email、手机号。
- OpenAI trace 导出不再因为 metadata 字段过多出现 400。

### 4B：限制 Target Agent metadata 字段数量

**Prompt**

请检查并收敛目标 agent run 的 `trace_metadata`。目标 agent metadata 也应只保留必要索引字段，不要复用一个过大的 metadata dict 后再追加字段。

目标 agent trace 建议只保留：

```text
environment
component
run_id
trace_id
conversation_id
client_message_id
model
mode
intent
scope
study_stage
target_exam
source_page
agent_name
route_type
target_agent
```

**验收标准**

- target agent trace metadata 字段数量不超过 16。
- `agent_name`、`route_type`、`target_agent` 能正常记录。
- 不删除内部 debug metadata 或最终返回给后端的 run metadata。

---

## 题目 5：依赖与环境变量

### 5A：更新 Python 依赖

**Prompt**

请更新 `python/ai_orchestrator/requirements.txt`，加入 Langfuse 和 OpenInference OpenAI Agents SDK instrumentation 依赖。

建议固定版本，避免未来 beta 或 breaking 版本影响启动。例如：

```text
langfuse==3.14.6
openinference-instrumentation-openai-agents==1.4.2
```

如果版本不可用，请选择当前 PyPI 稳定版本，并说明原因。

**验收标准**

- `pip install -r python/ai_orchestrator/requirements.txt` 成功。
- 可以导入：

```python
from langfuse import get_client
from openinference.instrumentation.openai_agents import OpenAIAgentsInstrumentor
```

- 不引入 LangChain、LangSmith、Phoenix、Braintrust、W&B 等额外观测平台。

### 5B：更新环境变量示例和 Docker Compose

**Prompt**

请更新 `.env.example` 和 `docker-compose.yml`，让本地和容器部署都能配置 Langfuse。

`.env.example` 增加：

```text
LANGFUSE_ENABLED=false
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
LANGFUSE_BASE_URL=https://cloud.langfuse.com
```

`docker-compose.yml` 的 `assistant-orchestrator.environment` 透传：

```text
LANGFUSE_ENABLED
LANGFUSE_PUBLIC_KEY
LANGFUSE_SECRET_KEY
LANGFUSE_BASE_URL
```

**验收标准**

- 本地 `.env` 可以打开或关闭 Langfuse。
- Docker Compose 可以通过环境变量启用 Langfuse。
- 默认配置仍为关闭，不影响未配置 Langfuse 的环境。

---

## 题目 6：文档与测试

### 6A：更新项目文档

**Prompt**

请更新 Langfuse 接入相关文档，至少包括：

```text
README.md
docs/runbooks/environment-variables.md
docs/agent/Agent可观测性与调试中心.md
```

文档需要说明：

- Langfuse 默认关闭。
- 配置完整才启用。
- Langfuse 不替代 OpenAI Platform Traces。
- Langfuse 不替代内部 Agent Debug Center。
- `LANGFUSE_HOST` 只是兼容旧变量，推荐使用 `LANGFUSE_BASE_URL`。

**验收标准**

- README 能看到 Langfuse 环境变量入口。
- runbook 能说明每个 Langfuse 环境变量含义。
- observability 文档说明 Langfuse 作为外部 tracing/prompt/eval 平台，不接管业务链路。

### 6B：补充单元测试和验证命令

**Prompt**

请新增或更新 Python orchestrator 测试，覆盖 Langfuse 默认关闭、配置不完整、配置完整、幂等初始化、health 状态和 metadata 字段数量限制。

建议涉及：

```text
python/ai_orchestrator/tests/test_observability.py
python/ai_orchestrator/tests/test_app_cors.py
python/ai_orchestrator/tests/test_assistant_service.py
```

验证命令：

```powershell
python\ai_orchestrator\.venv\Scripts\python.exe -m pip install -r python\ai_orchestrator\requirements.txt
python\ai_orchestrator\.venv\Scripts\python.exe -m unittest discover -s python\ai_orchestrator\tests
python\ai_orchestrator\.venv\Scripts\python.exe -c "from langfuse import get_client; from openinference.instrumentation.openai_agents import OpenAIAgentsInstrumentor; print('langfuse-openai-agents-import-ok')"
```

**验收标准**

- 新增 observability 单元测试通过。
- 全量 `python/ai_orchestrator/tests` 通过。
- Langfuse/OpenInference 导入验证通过。
- 测试不依赖真实 Langfuse key。
- 测试不向真实 Langfuse 服务发送数据。
