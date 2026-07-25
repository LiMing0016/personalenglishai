# 原始单 Agent 消融环境实施计划

> 目标：在不重构现有多 Agent 环境的前提下，增加可选择的 `single_agent_raw` 运行模式。该模式不设置 PEAI Prompt、工具、handoff、路由或结构化输出，只使用同一基础模型与独立的 Agents SDK Session。

## 约束

- 默认模式继续为 `multi_agent`。
- 原始模式失败时不得回退到多 Agent。
- 两套模式共享 HTTP/SSE 协议，但 Session 命名空间必须隔离。
- 原始模式正常请求只提交当前用户输入，由 SDK Session 提供历史。
- 原始模式不生成学习卡片，`parts` 始终为空。
- 前端切换模式必须创建新对话，避免两套历史混用。

## 任务 1：Schema 与模式解析

涉及文件：

- 修改 `python/ai_orchestrator/schemas/assistant_request.py`
- 新增 `python/ai_orchestrator/services/assistant_runtime_mode.py`
- 新增 `python/ai_orchestrator/tests/test_assistant_runtime_mode.py`

测试先行：

1. 未传 `agentMode` 时选择环境默认值，未配置环境变量时为 `multi_agent`。
2. 允许覆盖时，`single_agent_raw` 生效。
3. 禁止覆盖时忽略请求值。
4. Pydantic 拒绝未知模式。
5. Session key 分别为 `multi:<conversationId>` 和 `single-raw:<conversationId>`。

实现：

```python
AgentMode = Literal["multi_agent", "single_agent_raw"]

class AssistantRuntimeModeResolver:
    def resolve(self, requested: AgentMode | None) -> AgentMode: ...

def build_session_key(mode: AgentMode, conversation_id: str) -> str: ...
```

验证：

```powershell
python -m unittest python.ai_orchestrator.tests.test_assistant_runtime_mode
```

## 任务 2：原始 Agent 与原始输入

涉及文件：

- 新增 `python/ai_orchestrator/agents/raw_single.py`
- 新增 `python/ai_orchestrator/adapters/raw_openai_input_items.py`
- 新增 `python/ai_orchestrator/tests/test_raw_single_agent.py`
- 新增 `python/ai_orchestrator/tests/test_raw_openai_input_items.py`

测试先行：

1. Agent 没有 instructions、tools、handoffs 和 output type。
2. 文本输入逐字保留，不注入 mode、intent、studyContext 或 Markdown 规范。
3. 显式重放历史时按原角色顺序转换。
4. 图片与文件沿用安全模型输入格式，但不添加业务解释。

实现：

```python
def create_raw_single_agent(model: str):
    return Agent(name="Raw Single Agent", model=model)

def build_raw_input(request: AssistantRequest, *, include_history: bool) -> str | list[dict]: ...
```

纯文本且无显式历史/附件时返回字符串，以便 Agents SDK Session 接管上下文；多模态或显式历史返回 Responses input items，并禁用 Session。

验证：

```powershell
python -m unittest `
  python.ai_orchestrator.tests.test_raw_single_agent `
  python.ai_orchestrator.tests.test_raw_openai_input_items
```

## 任务 3：Raw 服务与运行时入口

涉及文件：

- 新增 `python/ai_orchestrator/raw_assistant_service.py`
- 新增 `python/ai_orchestrator/assistant_runtime.py`
- 修改 `python/ai_orchestrator/app.py`
- 修改 `python/ai_orchestrator/assistant_service.py`
- 新增 `python/ai_orchestrator/tests/test_raw_assistant_service.py`
- 新增 `python/ai_orchestrator/tests/test_assistant_runtime.py`
- 修改 `python/ai_orchestrator/tests/test_assistant_run_endpoint.py`

测试先行：

1. Runtime 把 `multi_agent` 请求交给原服务，把 `single_agent_raw` 交给 Raw 服务。
2. Raw 服务调用共享 `run_agent_session`/`stream_agent_session`，不调用任何路由。
3. 纯文本使用 `single-raw:<conversationId>` Session。
4. 显式历史/附件路径禁用 Session，避免重复历史。
5. Raw 非流式和流式协议兼容，元数据含 `agentMode`，`parts=[]`。
6. Raw 异常原样失败，不调用多 Agent。

实现：

```python
class AssistantRuntime:
    async def run_assistant_request(...): ...
    async def stream_assistant_request(...): ...
    async def route_assistant_request(...):  # 始终委托现有多 Agent debug
        ...
```

现有多 Agent 元数据补充默认 `agentMode="multi_agent"`，其余字段与行为不变。

验证：

```powershell
python -m unittest `
  python.ai_orchestrator.tests.test_raw_assistant_service `
  python.ai_orchestrator.tests.test_assistant_runtime `
  python.ai_orchestrator.tests.test_assistant_run_endpoint `
  python.ai_orchestrator.tests.test_assistant_service
```

## 任务 4：Java 转发与历史单一来源

涉及文件：

- 修改 `backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantRequest.java`
- 修改 `backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantRunMetadataResponse.java`
- 修改 `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- 修改 `backend/src/test/java/com/personalenglishai/backend/service/assistant/AssistantConversationServiceTest.java`
- 修改 `backend/src/test/java/com/personalenglishai/backend/controller/AssistantControllerTest.java`

测试先行：

1. JSON `agentMode` 能进入 Java DTO 并转发。
2. `multi_agent` 继续附加最近历史。
3. `single_agent_raw` 把 `conversationHistory` 清空，让 Python SDK Session 成为唯一历史来源。
4. 流式与非流式行为一致。
5. 响应元数据保留 `agentMode`。

验证：

```powershell
.\mvnw.cmd -q -Dtest=AssistantConversationServiceTest,AssistantControllerTest test
```

## 任务 5：前端实验开关

涉及文件：

- 修改 `web/src/types/assistantRequest.ts`
- 修改 `web/src/api/assistantRequestBuilder.ts`
- 修改 `web/src/api/assistant.ts`
- 修改 `web/src/pages/app/assistantState.ts`
- 修改 `web/src/pages/app/AssistantPage.vue`
- 新增 `web/src/api/assistantRequestBuilder.test.ts`
- 新增或修改 `web/src/pages/app/assistantState.test.ts`

测试先行：

1. 请求构造器正确输出 `agentMode`。
2. 状态层保存当前实验模式。
3. 切换模式创建新对话，不能在已有消息的对话中混用。

界面：

- 在学习助手页头增加仅开发环境可见的双选开关：
  - `多 Agent`
  - `原始模型`
- 切换后立即创建新对话，并显示轻量提示“已切换到原始模型，新建了实验对话”。

验证：

```powershell
npx vue-tsc --noEmit
npx vite build
```

## 任务 6：回归与真实模型基线

自动验证：

```powershell
python -m unittest discover -s python/ai_orchestrator/tests
.\mvnw.cmd test
npm run build
```

真实模型验证使用同一新对话依次发送：

1. `hive 是什么意思？`
2. `再给两个例句。`
3. `帮我解释量子纠缠。`
4. `回到刚才的 hive，它还有哪些常见搭配？`
5. `简单一点。`

检查：

- 第 2、4、5 轮正确引用相关历史。
- 第 3 轮能自然切换新话题。
- Trace 中只有 `Raw Single Agent`，无 RouteAgent、handoff 和工具。
- `agentMode=single_agent_raw`。
- `parts=[]`。

若本机没有可用 API Key，只交付自动测试结果，并明确标记真实模型验证未运行。

## 提交建议

按可回滚边界提交：

1. `test(agent): 定义原始单 Agent 模式契约`
2. `feat(agent): 接入原始单 Agent 运行环境`
3. `feat(api): 转发 Agent 运行模式并隔离历史`
4. `feat(ui): 增加原始模型实验开关`
5. `docs(agent): 补充原始模型验证方法`
