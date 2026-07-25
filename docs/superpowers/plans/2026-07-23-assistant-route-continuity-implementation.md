# Assistant Route Continuity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不增加 RouteDecision 模型调用次数的前提下，让学习助手可靠区分新任务、继续、恢复、修改、确认与歧义，并在低置信度时直接澄清。

**Architecture:** 扩展现有 `RouteRequest -> RouteDecision -> AssistantAgentService` 主链路。Adapter 负责构建有界的近期历史和交互摘要，Route Agent 在一次结构化调用中返回任务关系与执行路由，纯函数策略统一应用置信度规则，Assistant Service 对澄清结果直接响应并保留现有故障回退。

**Tech Stack:** Python 3、Pydantic v2、OpenAI Agents SDK、`unittest`、现有 Assistant 流式事件协议。

## Global Constraints

- 实现必须遵守根目录、`python/AGENTS.md` 与 `python/ai_orchestrator/AGENTS.md`。
- 当前工作区存在大量不属于本需求的改动；每次提交只允许包含本任务列出的文件，不得使用无范围的 `git add .`。
- 不新增第二次连续性模型调用，不引入关键词正则路由，不新增 XState、数据库表或长期记忆。
- 不修改旧 `ContinuationClassifier` 与内存 `ActiveTaskState`；它们不属于学习助手主链路本次范围。
- 不改变前端请求必填字段；新增字段必须有默认值，保持现有调用方兼容。
- 明确 `interaction` 的业务动作优先级高于模型置信度，但模型仍只能选择 Schema 中已注册的 Agent 与 workflow。
- 每个任务都先写失败测试，再做最小实现，再运行该任务的定向测试。

---

## Task 1: 扩展任务连续性结构化契约

**Files:**

- Modify: `python/ai_orchestrator/schemas/routing.py`
- Create: `python/ai_orchestrator/tests/test_routing_continuity_contract.py`
- Modify: `python/ai_orchestrator/tests/test_route_decision_runner.py`

### 1.1 先写契约测试

- [ ] 新建 `test_routing_continuity_contract.py`，覆盖六种 relation、默认值、澄清字段和非法组合。

```python
import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.routing import RoutingDecision


class RoutingContinuityContractTests(unittest.TestCase):
    def _decision(self, **overrides: object) -> RoutingDecision:
        payload: dict[str, object] = {
            "relation": "new_task",
            "intent": "vocab",
            "route_type": "run_workflow",
            "workflow": "specialist_single_turn",
            "target_agent": "vocab",
            "confidence": 0.9,
            "required_inputs": [],
            "missing_inputs": [],
            "normalized_inputs": {},
            "reason": "测试",
        }
        payload.update(overrides)
        return RoutingDecision.model_validate(payload)

    def test_all_task_relations_are_supported(self) -> None:
        relations = (
            "new_task",
            "continue_active",
            "resume_prior",
            "modify_previous",
            "confirm_action",
        )
        for relation in relations:
            with self.subTest(relation=relation):
                self.assertEqual(self._decision(relation=relation).relation, relation)

        unclear = self._decision(
            relation="unclear",
            intent="free_chat",
            route_type="ask_clarification",
            workflow=None,
            target_agent=None,
            missing_inputs=["task_reference"],
            clarification_question="你想继续哪个任务？",
        )
        self.assertEqual(unclear.relation, "unclear")

    def test_existing_payload_defaults_to_new_task(self) -> None:
        decision = self._decision()
        self.assertEqual(decision.relation, "new_task")
        self.assertIsNone(decision.clarification_question)

    def test_ask_clarification_requires_question(self) -> None:
        with self.assertRaises(ValidationError):
            self._decision(
                relation="unclear",
                intent="free_chat",
                route_type="ask_clarification",
                workflow=None,
                target_agent=None,
                missing_inputs=["task_reference"],
                clarification_question=None,
            )

    def test_unclear_requires_ask_clarification(self) -> None:
        with self.assertRaises(ValidationError):
            self._decision(relation="unclear")

    def test_non_clarification_rejects_question(self) -> None:
        with self.assertRaises(ValidationError):
            self._decision(clarification_question="你想继续哪个任务？")


if __name__ == "__main__":
    unittest.main()
```

- [ ] 运行测试并确认失败原因是 `relation` 与 `clarification_question` 尚不存在。

Run:

```powershell
python -m unittest python.ai_orchestrator.tests.test_routing_continuity_contract -v
```

Expected: 新增测试失败，错误明确指向 RoutingDecision 缺少连续性契约。

### 1.2 实现 Schema

- [ ] 在 `routing.py` 增加稳定的语言无关枚举与最小交互摘要。

```python
TaskRelation = Literal[
    "new_task",
    "continue_active",
    "resume_prior",
    "modify_previous",
    "confirm_action",
    "unclear",
]


class RouteInteractionContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source: Literal["composer", "quick_action", "response_action", "activity_action"]
    ui_intent: str | None = None
    action_id: str | None = None
    active_activity_id: str | None = None
    exercise_type: str | None = None
    topic: str | None = None
    difficulty: str | None = None
```

- [ ] 在 `RouteRequestContext` 中增加可选字段。

```python
interaction: RouteInteractionContext | None = None
```

- [ ] 在 `RoutingDecision` 中增加向后兼容字段。

```python
relation: TaskRelation = "new_task"
clarification_question: str | None = None
```

- [ ] 扩展现有 `model_validator`，保留原校验并增加以下规则。

```python
if self.relation == "unclear" and self.route_type != "ask_clarification":
    raise ValueError("relation=unclear requires route_type=ask_clarification")

if self.route_type == "ask_clarification":
    if not self.missing_inputs:
        raise ValueError("ask_clarification requires missing_inputs")
    if not (self.clarification_question or "").strip():
        raise ValueError("ask_clarification requires clarification_question")
elif self.clarification_question is not None:
    raise ValueError("clarification_question is only valid for ask_clarification")
```

- [ ] 更新 `test_route_decision_runner.py` 中的断言，让 Runner 的结构化结果包含默认 `relation="new_task"`，不改现有业务预期。

### 1.3 验证并提交

- [ ] 运行契约与 Runner 测试。

```powershell
python -m unittest python.ai_orchestrator.tests.test_routing_continuity_contract python.ai_orchestrator.tests.test_route_decision_runner -v
```

Expected: 全部通过。

- [ ] 只提交本任务文件。

```powershell
git add -- python/ai_orchestrator/schemas/routing.py python/ai_orchestrator/tests/test_routing_continuity_contract.py python/ai_orchestrator/tests/test_route_decision_runner.py
git commit --only -m "feat(agent): 扩展路由连续性结构化契约" -- python/ai_orchestrator/schemas/routing.py python/ai_orchestrator/tests/test_routing_continuity_contract.py python/ai_orchestrator/tests/test_route_decision_runner.py
```

---

## Task 2: 构建自适应路由历史并传递 interaction

**Files:**

- Modify: `python/ai_orchestrator/adapters/route_request_adapter.py`
- Modify: `python/ai_orchestrator/tests/test_route_request_adapter.py`

### 2.1 先补 Adapter 失败测试

- [ ] 将原“最多 6 条、每条 600 字符”断言替换为新预算契约，并增加以下测试：

```python
def test_route_history_keeps_up_to_thirty_recent_messages_within_budget(self) -> None:
    history = [
        ConversationMessage(role="user" if index % 2 == 0 else "assistant", content=f"message-{index}")
        for index in range(35)
    ]
    request = self._assistant_request(conversation_history=history)

    route_request = build_route_request(request, user_id="user-1")
    routed = route_request.context.conversation_history

    self.assertLessEqual(len(routed), 30)
    self.assertEqual(routed[-1].content, "message-34")
    self.assertNotIn("message-0", [item.content for item in routed])

def test_long_history_message_keeps_head_and_tail(self) -> None:
    content = "HEAD:" + ("a" * 2500) + ":TAIL"
    request = self._assistant_request(
        conversation_history=[ConversationMessage(role="assistant", content=content)]
    )

    route_request = build_route_request(request, user_id="user-1")
    routed = route_request.context.conversation_history[0].content

    self.assertTrue(routed.startswith("HEAD:"))
    self.assertTrue(routed.endswith(":TAIL"))
    self.assertIn("中间内容已省略", routed)
    self.assertLessEqual(len(routed), 2000)

def test_route_history_respects_total_character_budget(self) -> None:
    history = [
        ConversationMessage(role="assistant", content=f"{index}:" + ("x" * 3000))
        for index in range(30)
    ]
    request = self._assistant_request(conversation_history=history)

    route_request = build_route_request(request, user_id="user-1")
    total = sum(len(item.content) for item in route_request.context.conversation_history)

    self.assertLessEqual(total, 20_000)

def test_interaction_is_mapped_to_route_context(self) -> None:
    request = self._assistant_request(
        interaction={
            "source": "response_action",
            "uiIntent": "show_learning_card",
            "actionId": "create_vocab_card",
            "context": {"topic": "hive", "difficulty": "medium"},
        }
    )

    route_request = build_route_request(request, user_id="user-1")

    self.assertEqual(route_request.context.interaction.action_id, "create_vocab_card")
    self.assertEqual(route_request.context.interaction.topic, "hive")
```

- [ ] 按 `AssistantRequest` 当前的 Pydantic alias 实际写法调整测试构造，但断言语义不得变化。
- [ ] 运行测试，确认它因旧 6/600 预算和 interaction 未映射而失败。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_request_adapter -v
```

Expected: 新增预算与 interaction 测试失败。

### 2.2 实现预算常量与长消息裁剪

- [ ] 用集中常量替换旧限制。

```python
ROUTE_HISTORY_MESSAGE_LIMIT = 30
ROUTE_HISTORY_MESSAGE_CHARS = 2_000
ROUTE_HISTORY_TOTAL_CHARS = 20_000
ROUTE_HISTORY_HEAD_CHARS = 700
ROUTE_HISTORY_TAIL_CHARS = 1_300
ROUTE_HISTORY_TRUNCATION_MARKER = "\n...[中间内容已省略]...\n"
```

- [ ] 实现保持开头与结尾的纯函数；标记本身计入长度。

```python
def _truncate_route_history_content(content: str, limit: int) -> str:
    normalized = content.strip()
    if len(normalized) <= limit:
        return normalized

    marker = ROUTE_HISTORY_TRUNCATION_MARKER
    available = max(0, limit - len(marker))
    head_chars = min(ROUTE_HISTORY_HEAD_CHARS, available)
    tail_chars = available - head_chars
    if tail_chars > ROUTE_HISTORY_TAIL_CHARS:
        extra = tail_chars - ROUTE_HISTORY_TAIL_CHARS
        head_chars += extra
        tail_chars = ROUTE_HISTORY_TAIL_CHARS
    return f"{normalized[:head_chars]}{marker}{normalized[-tail_chars:]}"
```

- [ ] 从最新消息向前分配总预算，再恢复时间顺序。只纳入非空 `user`/`assistant` 消息。

```python
def _build_route_history(messages: list[ConversationMessage]) -> list[RouteConversationHistoryMessage]:
    selected: list[RouteConversationHistoryMessage] = []
    remaining = ROUTE_HISTORY_TOTAL_CHARS

    for message in reversed(messages[-ROUTE_HISTORY_MESSAGE_LIMIT:]):
        content = (message.content or "").strip()
        if not content or remaining <= 0:
            continue
        limit = min(ROUTE_HISTORY_MESSAGE_CHARS, remaining)
        cropped = _truncate_route_history_content(content, limit)
        if not cropped:
            continue
        selected.append(RouteConversationHistoryMessage(role=message.role, content=cropped))
        remaining -= len(cropped)

    selected.reverse()
    return selected
```

### 2.3 映射最小 interaction 摘要

- [ ] 增加协议转换函数，不在 Adapter 内判断业务意图。

```python
def _build_route_interaction(request: AssistantRequest) -> RouteInteractionContext | None:
    interaction = request.interaction
    if interaction is None:
        return None
    context = interaction.context
    return RouteInteractionContext(
        source=interaction.source,
        ui_intent=interaction.ui_intent,
        action_id=interaction.action_id,
        active_activity_id=interaction.active_activity_id,
        exercise_type=context.exercise_type if context else None,
        topic=context.topic if context else None,
        difficulty=context.difficulty if context else None,
    )
```

- [ ] 把结果写入 `RouteRequestContext.interaction`；当前 `request.message` 保持完整，不进入历史裁剪函数。

### 2.4 验证并提交

- [ ] 运行 Adapter 与契约测试。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_request_adapter python.ai_orchestrator.tests.test_routing_continuity_contract -v
```

Expected: 全部通过，历史总字符数不超过 20000，最新消息和长消息尾部均可见。

- [ ] 提交。

```powershell
git add -- python/ai_orchestrator/adapters/route_request_adapter.py python/ai_orchestrator/tests/test_route_request_adapter.py
git commit --only -m "feat(agent): 扩大路由上下文并传递交互动作" -- python/ai_orchestrator/adapters/route_request_adapter.py python/ai_orchestrator/tests/test_route_request_adapter.py
```

---

## Task 3: 定义 Route Agent 连续性 Prompt 与置信度策略

**Files:**

- Modify: `python/ai_orchestrator/prompts/agent_instructions/route_decision.md`
- Create: `python/ai_orchestrator/services/route_decision_policy.py`
- Create: `python/ai_orchestrator/tests/test_route_decision_policy.py`
- Modify: `python/ai_orchestrator/tests/test_route_agent_structure.py`

### 3.1 先写纯策略失败测试

- [ ] 新建 `test_route_decision_policy.py`，用构造好的 `RoutingDecision` 测试阈值，不调用模型。

```python
import unittest

from python.ai_orchestrator.schemas.routing import RoutingDecision
from python.ai_orchestrator.services.route_decision_policy import apply_route_decision_policy


class RouteDecisionPolicyTests(unittest.TestCase):
    def _decision(self, confidence: float, relation: str = "continue_active") -> RoutingDecision:
        return RoutingDecision.model_validate(
            {
                "relation": relation,
                "intent": "vocab",
                "route_type": "run_workflow",
                "workflow": "specialist_single_turn",
                "target_agent": "vocab",
                "confidence": confidence,
                "required_inputs": [],
                "missing_inputs": [],
                "normalized_inputs": {},
                "reason": "测试",
            }
        )

    def test_high_confidence_is_accepted(self) -> None:
        result = apply_route_decision_policy(
            self._decision(0.75), has_explicit_interaction=False, response_language="zh-CN"
        )
        self.assertEqual(result.route_type, "run_workflow")

    def test_medium_confidence_non_ambiguous_route_is_accepted(self) -> None:
        result = apply_route_decision_policy(
            self._decision(0.50), has_explicit_interaction=False, response_language="zh-CN"
        )
        self.assertEqual(result.route_type, "run_workflow")

    def test_low_confidence_becomes_clarification(self) -> None:
        result = apply_route_decision_policy(
            self._decision(0.49), has_explicit_interaction=False, response_language="zh-CN"
        )
        self.assertEqual(result.relation, "unclear")
        self.assertEqual(result.route_type, "ask_clarification")
        self.assertEqual(result.missing_inputs, ["task_reference"])
        self.assertTrue(result.clarification_question)

    def test_explicit_interaction_is_not_downgraded_by_confidence(self) -> None:
        result = apply_route_decision_policy(
            self._decision(0.30), has_explicit_interaction=True, response_language="zh-CN"
        )
        self.assertEqual(result.route_type, "run_workflow")

    def test_english_fallback_question_is_localized(self) -> None:
        result = apply_route_decision_policy(
            self._decision(0.10), has_explicit_interaction=False, response_language="en-US"
        )
        self.assertEqual(result.clarification_question, "Which recent task would you like to continue?")
```

- [ ] 运行测试并确认模块尚不存在。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_decision_policy -v
```

Expected: import failure 或新增断言失败。

### 3.2 实现集中策略

- [ ] 新建纯函数策略模块，不读取全局会话状态，不调用 Agent。

```python
from python.ai_orchestrator.schemas.routing import RoutingDecision

ROUTE_CONFIDENCE_HIGH = 0.75
ROUTE_CONFIDENCE_LOW = 0.50
ROUTE_SIDE_EFFECT_WORKFLOWS: frozenset[str] = frozenset()


def apply_route_decision_policy(
    decision: RoutingDecision,
    *,
    has_explicit_interaction: bool,
    response_language: str,
) -> RoutingDecision:
    if has_explicit_interaction:
        return decision
    if decision.relation != "unclear" and decision.confidence >= ROUTE_CONFIDENCE_HIGH:
        return decision
    if (
        decision.relation != "unclear"
        and decision.confidence >= ROUTE_CONFIDENCE_LOW
        and decision.workflow not in ROUTE_SIDE_EFFECT_WORKFLOWS
    ):
        return decision
    if decision.route_type == "ask_clarification" and decision.clarification_question:
        return decision

    question = (
        "Which recent task would you like to continue?"
        if response_language == "en-US"
        else "你想继续最近的哪个任务？"
    )
    return decision.model_copy(
        update={
            "relation": "unclear",
            "route_type": "ask_clarification",
            "workflow": None,
            "target_agent": None,
            "required_inputs": ["task_reference"],
            "missing_inputs": ["task_reference"],
            "clarification_question": question,
        }
    )
```

- [ ] 在测试中补充 `0.74` 与 `0.76` 边界、已有模型澄清问题保持不变、`mixed` 使用中文回退。
- [ ] 代码注释明确：当前已注册 workflow 均无外部副作用，因此 `ROUTE_SIDE_EFFECT_WORKFLOWS` 初始为空；未来新增写入或外部动作 workflow 时必须先加入该集合，避免中置信度直接执行。

### 3.3 升级 Route Agent Prompt

- [ ] 在 `route_decision.md` 增加以下固定章节，避免自然语言关键词枚举：

```markdown
## Task relation

For every request, classify its relation to recent tasks as exactly one of:

- `new_task`: the user starts a new task or clearly changes domain.
- `continue_active`: the user adds to or continues the most recent task.
- `resume_prior`: the user returns to an earlier task after an intervening topic.
- `modify_previous`: the user asks to change the previous output.
- `confirm_action`: the user confirms a concrete action proposed in history or supplied by `context.interaction`.
- `unclear`: multiple task references are equally plausible or no safe reference can be resolved.

Evidence priority:

1. A valid explicit `context.interaction`.
2. A complete and explicit request in the current message.
3. Semantic links to recent conversation history.
4. The client-provided default intent.

Do not infer confirmation from a short acknowledgement unless history or interaction contains a concrete pending action. When relation is `unclear`, return `ask_clarification`, list the unresolved input, and provide one concise `clarification_question` in the requested response language.
```

- [ ] 添加语义示例：`hive -> 再来两个例句`、`hive -> 天气 -> 回到 hive`、多个候选后的“它”、英文确认、单纯“谢谢”。示例只用于模型理解，不在 Python 中实现关键词匹配。
- [ ] 更新 `test_route_agent_structure.py`，断言 Prompt 包含全部 relation、证据优先级和“不得凭空确认动作”的约束。

### 3.4 验证并提交

- [ ] 运行策略和 Prompt 测试。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_decision_policy python.ai_orchestrator.tests.test_route_agent_structure -v
```

Expected: 全部通过。

- [ ] 提交。

```powershell
git add -- python/ai_orchestrator/prompts/agent_instructions/route_decision.md python/ai_orchestrator/services/route_decision_policy.py python/ai_orchestrator/tests/test_route_decision_policy.py python/ai_orchestrator/tests/test_route_agent_structure.py
git commit --only -m "feat(agent): 增加语义连续性路由策略" -- python/ai_orchestrator/prompts/agent_instructions/route_decision.md python/ai_orchestrator/services/route_decision_policy.py python/ai_orchestrator/tests/test_route_decision_policy.py python/ai_orchestrator/tests/test_route_agent_structure.py
```

---

## Task 4: 在 Assistant Service 直接返回路由澄清

**Files:**

- Modify: `python/ai_orchestrator/assistant_service.py`
- Modify: `python/ai_orchestrator/tests/test_assistant_service.py`

### 4.1 先写非流式与流式失败测试

- [ ] 在 `test_assistant_service.py` 新增一个返回 `ask_clarification` 的 fake RouteDecision Runner。
- [ ] 非流式测试断言：回复就是 `clarification_question`，metadata 中 Agent 为 RouteAgent，目标 Agent 的 `run_agent_session` 没有被调用。

```python
async def test_run_returns_route_clarification_without_target_agent(self) -> None:
    route_runner = AsyncMock(
        return_value=RoutingDecision.model_validate(
            {
                "relation": "unclear",
                "intent": "free_chat",
                "route_type": "ask_clarification",
                "workflow": None,
                "target_agent": None,
                "confidence": 0.42,
                "required_inputs": ["task_reference"],
                "missing_inputs": ["task_reference"],
                "clarification_question": "你指的是 hive，还是另一个单词？",
                "normalized_inputs": {},
                "reason": "多个候选",
            }
        )
    )
    service = self._service(route_decision_runner=route_runner)
    service.run_agent_session = AsyncMock()

    reply = await service.run_assistant_request(self._request(message="它呢？"), user_id="user-1")

    self.assertEqual(reply.reply, "你指的是 hive，还是另一个单词？")
    self.assertEqual(reply.run.agent_name, "RouteAgent")
    service.run_agent_session.assert_not_awaited()
```

- [ ] 流式测试断言事件顺序为：`run.started`、`message.created`、`message.delta`、`message.completed`、`run.completed`；目标 Agent stream 没有被调用。
- [ ] 增加低置信度正常路由经 policy 转成澄清的 service 测试。
- [ ] 保留并运行 RouteDecision 抛异常后的现有 fallback 测试。

Run:

```powershell
python -m unittest python.ai_orchestrator.tests.test_assistant_service -v
```

Expected: 新澄清测试失败，因为 service 仍尝试选择目标 Agent。

### 4.2 在 RouteDecision 返回后应用策略

- [ ] 在 `_maybe_run_route_decision_with_request` 成功取得结构化决策后调用 `apply_route_decision_policy`。

```python
response_language = (
    request.study_context.response_language
    if request.study_context is not None
    else "zh-CN"
)
return apply_route_decision_policy(
    decision,
    has_explicit_interaction=request.interaction is not None,
    response_language=response_language,
)
```

- [ ] 保持 Runner 调用异常时返回 `None` 的当前日志、trace 和 fallback 行为不变。

### 4.3 增加 route-only 响应帮助函数

- [ ] 增加 `_build_route_only_run_metadata`，只记录 RouteAgent 步骤，不伪造目标 Agent usage。
- [ ] 增加 `_build_route_clarification_reply`，统一从 `clarification_question` 生成 `AssistantReply`。
- [ ] 在非流式主路径中，取得 RouteDecision 后、写作教练和目标 Agent 选择前加入短路：

```python
if route_decision and route_decision.route_type == "ask_clarification":
    return self._build_route_clarification_reply(
        request=request,
        route_decision=route_decision,
    )
```

- [ ] 在流式主路径相同位置输出现有前端可识别的五个事件并立即结束 generator。事件字段名、run id 和 message id 复用当前普通流式实现的 helper，避免产生第二套协议。

### 4.4 验证并提交

- [ ] 运行 service、endpoint 和 contract 测试。

```powershell
python -m unittest python.ai_orchestrator.tests.test_assistant_service python.ai_orchestrator.tests.test_assistant_run_endpoint python.ai_orchestrator.tests.test_routing_continuity_contract -v
```

Expected: 全部通过；澄清不调用目标 Agent，路由故障仍可回退。

- [ ] 提交。

```powershell
git add -- python/ai_orchestrator/assistant_service.py python/ai_orchestrator/tests/test_assistant_service.py
git commit --only -m "feat(agent): 直接返回低置信度路由澄清" -- python/ai_orchestrator/assistant_service.py python/ai_orchestrator/tests/test_assistant_service.py
```

---

## Task 5: 建立任务连续性回归集与在线评测入口

**Files:**

- Create: `python/ai_orchestrator/data/route_continuity_eval_cases.json`
- Create: `python/ai_orchestrator/evals/__init__.py`
- Create: `python/ai_orchestrator/evals/run_route_continuity_eval.py`
- Create: `python/ai_orchestrator/tests/test_route_continuity_eval_data.py`

### 5.1 定义 24 条固定回归案例

- [ ] JSON 每条记录包含 `id`、`message`、`history`、可选 `interaction`、`expected`。`expected` 至少包含 `relation`、`intent`、`route_type`，需要执行时还包含 `workflow` 与 `target_agent`。
- [ ] 严格写入以下案例，不用“类似案例”代替：

| id | 场景 | 期望 |
| --- | --- | --- |
| `vocab_new_zh` | “hive 是什么意思？” | `new_task / vocab / specialist_single_turn` |
| `vocab_continue_examples_zh` | hive 解释后“再给两个例句” | `continue_active / vocab` |
| `vocab_confirm_card_zh` | 助手明确提议卡片后“可以” | `confirm_action / vocab` |
| `vocab_confirm_card_en` | 提议卡片后“Sure, make the card.” | `confirm_action / vocab` |
| `vocab_resume_after_weather` | hive、天气、再问“hive 还有什么搭配” | `resume_prior / vocab` |
| `vocab_ambiguous_pronoun` | 历史含 hive 与 swarm 后问“它呢？” | `unclear / ask_clarification` |
| `vocab_modify_simple` | 词汇解释后“简单一点” | `modify_previous / vocab` |
| `vocab_no_false_confirm_thanks` | 卡片提议后“谢谢，先不用了” | 不得为 `confirm_action` |
| `translation_new_after_vocab` | 词汇任务后明确要求翻译句子 | `new_task / translation` |
| `translation_continue_tone` | 翻译后“换得正式一点” | `modify_previous / translation` |
| `sentence_structure_new` | “分析这句话的结构” | `new_task / sentence_structure` |
| `sentence_structure_continue` | 结构分析后“从句再讲一下” | `continue_active / sentence_structure` |
| `polish_new` | “帮我润色这段话” | `new_task / polish` |
| `polish_modify_shorter` | 润色后“缩短到两句” | `modify_previous / polish` |
| `practice_new` | “给我五道过去式练习” | `new_task / practice_design` |
| `practice_continue_more` | 练习后“再来两题” | `continue_active / practice_design` |
| `writing_evaluation_new` | 带作文文本请求评分 | `new_task / writing_evaluation` |
| `writing_feedback_continue` | 作文反馈后询问第二条错误 | `continue_active / writing_evaluation` |
| `free_chat_greeting` | “你好” | `new_task / free_chat / answer_direct` |
| `free_chat_acknowledgement` | 普通解释后“明白了” | 不得误触发有动作 workflow |
| `out_of_scope_weather` | “上海明天天气怎么样？” | `new_task / out_of_scope` |
| `interaction_learning_card` | `response_action/show_learning_card` | interaction 对应的专业路由 |
| `interaction_sentence_reorder` | 明确启动重组成句 interaction | interaction 优先，保持专用入口 |
| `multilingual_resume` | 中文上下文后英文恢复 hive | `resume_prior / vocab` |

### 5.2 先写数据完整性测试

- [ ] 新建离线测试，确保无需 API key 也能运行。

```python
import json
import unittest
from pathlib import Path

from python.ai_orchestrator.schemas.routing import RouteRequest


DATA_FILE = Path(__file__).parents[1] / "data" / "route_continuity_eval_cases.json"


class RouteContinuityEvalDataTests(unittest.TestCase):
    def test_dataset_has_unique_complete_cases(self) -> None:
        cases = json.loads(DATA_FILE.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(cases), 20)
        ids = [case["id"] for case in cases]
        self.assertEqual(len(ids), len(set(ids)))
        for case in cases:
            RouteRequest.model_validate(case["request"])
            self.assertIn(case["expected"]["relation"], {
                "new_task",
                "continue_active",
                "resume_prior",
                "modify_previous",
                "confirm_action",
                "unclear",
            })
            self.assertIn("intent", case["expected"])
            self.assertIn("route_type", case["expected"])
```

- [ ] 运行并确认数据文件尚不存在导致失败。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_continuity_eval_data -v
```

Expected: file-not-found failure。

### 5.3 实现在线评测入口

- [ ] 评测脚本逐条调用现有 `RouteDecisionRunner`，只比较 `expected` 中声明的字段，不比较 `reason` 文本。
- [ ] 输出每条 PASS/FAIL、字段差异、总准确率；默认阈值 0.90，低于阈值返回退出码 1。
- [ ] CLI 接受 `--data` 和 `--minimum-accuracy`，不在代码中硬编码 API key。

```python
def compare_expected(decision: RoutingDecision, expected: dict[str, object]) -> dict[str, tuple[object, object]]:
    actual = decision.model_dump(mode="json")
    return {
        field: (actual.get(field), expected_value)
        for field, expected_value in expected.items()
        if actual.get(field) != expected_value
    }
```

- [ ] 复用项目现有 Settings、model provider 和 Runner 构建方式；如果真实模型配置缺失，脚本打印清晰错误并返回退出码 2，不吞掉异常。

### 5.4 验证并提交

- [ ] 运行离线数据测试。

```powershell
python -m unittest python.ai_orchestrator.tests.test_route_continuity_eval_data -v
```

Expected: 通过，案例数至少 20 且所有 RouteRequest 可验证。

- [ ] 在已配置真实模型的环境运行在线评测。

```powershell
python -m python.ai_orchestrator.evals.run_route_continuity_eval --minimum-accuracy 0.90
```

Expected: 输出逐案例结果和 `accuracy >= 90%`。若当前环境无 API 配置，在交付记录中明确标记“未运行在线评测”，不能写成通过。

- [ ] 提交。

```powershell
git add -- python/ai_orchestrator/data/route_continuity_eval_cases.json python/ai_orchestrator/evals/__init__.py python/ai_orchestrator/evals/run_route_continuity_eval.py python/ai_orchestrator/tests/test_route_continuity_eval_data.py
git commit --only -m "test(agent): 增加任务连续性路由评测集" -- python/ai_orchestrator/data/route_continuity_eval_cases.json python/ai_orchestrator/evals/__init__.py python/ai_orchestrator/evals/run_route_continuity_eval.py python/ai_orchestrator/tests/test_route_continuity_eval_data.py
```

---

## Task 6: 文档、全量验证与合并评估

**Files:**

- Modify: `docs/agent/路由Agent设计.md`
- Verify only: `python/ai_orchestrator`

### 6.1 更新项目文档

- [ ] 在 `路由Agent设计.md` 增加以下内容：

1. 六种 `TaskRelation` 的定义与边界。
2. `answer_direct`、`specialist_single_turn`、命名 workflow 三层执行模型。
3. interaction、当前消息、近期历史、默认 intent 的证据优先级。
4. 30 条、20000 字符、单条 2000 字符和 700/1300 首尾裁剪预算。
5. 置信度阈值、`ask_clarification` 直接返回和多语言回退。
6. RouteDecision 故障时沿用当前路由回退。
7. Route Debug、离线测试与在线评测命令。
8. 明确非目标：旧 ContinuationClassifier、长期记忆、跨设备任务恢复和全站路由。

### 6.2 运行定向测试套件

- [ ] 运行本需求所有测试文件。

```powershell
python -m unittest `
  python.ai_orchestrator.tests.test_routing_continuity_contract `
  python.ai_orchestrator.tests.test_route_request_adapter `
  python.ai_orchestrator.tests.test_route_decision_policy `
  python.ai_orchestrator.tests.test_route_decision_runner `
  python.ai_orchestrator.tests.test_route_agent_structure `
  python.ai_orchestrator.tests.test_assistant_service `
  python.ai_orchestrator.tests.test_assistant_run_endpoint `
  python.ai_orchestrator.tests.test_route_continuity_eval_data `
  -v
```

Expected: 全部通过，无 unexpected error 或 skipped failure。

### 6.3 运行 Python orchestrator 全量回归

- [ ] 运行完整测试发现。

```powershell
python -m unittest discover -s python/ai_orchestrator/tests -p "test_*.py" -v
```

Expected: 全部通过。若存在与本需求无关的预存失败，记录测试名、失败信息和与本次 diff 无关的证据，不得删除测试规避失败。

### 6.4 手工 Route Debug smoke

- [ ] 在本地应用中验证以下真实对话：

```text
1. 用户：hive 是什么意思？
2. 助手：解释并明确询问是否制作单词卡
3. 用户：可以
4. 期望：confirm_action / vocab，不回答泛化的“当然可以”

5. 用户：上海天气怎么样？
6. 用户：hive 还有哪些搭配？
7. 期望：resume_prior / vocab

8. 先后询问 hive 与 swarm
9. 用户：它还有哪些搭配？
10. 期望：ask_clarification，且不会调用目标 Agent
```

- [ ] 检查 Route Debug 展示 `relation`、`confidence`、`reason`、`workflow` 和 `target_agent`。
- [ ] 检查流式澄清在前端只出现一条完整消息，复制、重试等现有按钮不报错。

### 6.5 提交文档并做工作区审计

- [ ] 提交文档。

```powershell
git add -- docs/agent/路由Agent设计.md
git commit --only -m "docs(agent): 更新任务连续性路由说明" -- docs/agent/路由Agent设计.md
```

- [ ] 检查最终 diff 与提交范围。

```powershell
git status --short
git log --oneline -8
git diff main...HEAD -- python/ai_orchestrator docs/agent/路由Agent设计.md
```

Expected: 本需求提交只包含本计划列出的 Python、测试、评测数据和路由文档；用户原有工作区改动仍原样存在且未被提交。

- [ ] 评估合并到 `main`：只有定向测试、全量回归和真实模型 smoke 均通过，且在线连续性回归准确率不低于 90% 时才建议合并。若在线评测未运行，分支可以交付审查，但暂不声明可直接合并。

## Definition of Done

- [ ] RouteDecision 每次请求仍只调用一次模型。
- [ ] 六种 relation 有结构化 Schema、Prompt 约束和离线测试。
- [ ] 路由可见最多 30 条、20000 字符近期历史，并保留长消息开头与结尾。
- [ ] 明确 interaction 被传入 RouteRequest，且不被低模型置信度覆盖。
- [ ] 低置信度和多候选直接澄清，不调用第二个 Agent。
- [ ] RouteDecision 调用或结构验证失败时不阻断普通对话。
- [ ] 至少 20 条连续性案例可离线校验，并有真实模型评测入口。
- [ ] 项目路由文档已同步，未修改非目标旧路由链路。
- [ ] 所有实际运行和未运行的验证均在交付说明中如实列出。
