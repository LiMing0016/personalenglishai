# Agent Prompt 解析与动态上下文注入 Trae 题目

## 背景

当前 Python Agent 编排已经从“每个 Agent 直接读取固定 prompt”升级为 **Agent Prompt 解析与运行时上下文注入机制**。

本轮代码改动的核心不是新增 Specialist Agent，也不是重写 Router，而是把 Agent 创建时的 prompt 策略收口到统一入口：

```text
Agent 创建
-> resolve_agent_prompt_kwargs(agent_key, dynamic=True/False)
-> local / hybrid / remote prompt resolution
-> dynamic instructions 按运行时上下文注入 Runtime Learning Context
```

本轮需要验证和完善的功能包括：

- 本地 Markdown prompt 加载。
- OpenAI Platform 远程 prompt 引用。
- `local` / `hybrid` / `remote` 三种 prompt source。
- 本地模式下的 Dynamic Instructions。
- `AssistantRunContext` 中学段、考试模式的运行时注入。
- Router、Specialist、Attachment、RouteDecision、PromptSheet 工作流接入统一 resolver。

核心原则：

- Agent 构造点不要各自判断环境变量。
- Prompt 策略统一由 `prompts.resolver.resolve_agent_prompt_kwargs` 决定。
- 本地动态 instructions 和远程 prompt reference 不要混用。
- 结构化输出 Agent 不应被注入 Markdown 输出规范。

---

## 题目 1：实现统一 Prompt Resolver

难度：中等

### Prompt

请实现 Agent prompt 的统一解析入口 `resolve_agent_prompt_kwargs(agent_key, dynamic=False)`。

要求：

1. 支持 `AI_ASSISTANT_PROMPT_SOURCE=local | hybrid | remote`。
2. `local` 模式返回本地 Markdown instructions。
3. `hybrid` 模式优先使用远程 prompt，未配置时回退本地 prompt。
4. `remote` 模式必须使用远程 prompt，缺配置时抛出清晰错误。
5. 远程 prompt 只允许在 OpenAI Platform base URL 下启用。
6. 支持每个 Agent 独立配置 prompt id、version、variables。

建议环境变量格式：

```text
AI_PROMPT_<AGENT_KEY>_ID
AI_PROMPT_<AGENT_KEY>_VERSION
AI_PROMPT_<AGENT_KEY>_VARIABLES_JSON
```

### 验收标准

- `local` 模式返回 `{"instructions": ...}`。
- `hybrid` 配置远程 prompt 时返回 `{"prompt": {"id": ..., "version": ...}}`。
- `hybrid` 未配置远程 prompt 时回退本地 instructions。
- `remote` 未配置远程 prompt 时抛 `PromptResolutionError`。
- 非 OpenAI Platform base URL 下启用 `remote` 会抛错。
- variables JSON 非 object 时抛错。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_prompt_resolver
```

---

## 题目 2：实现 Dynamic Instructions 与 Runtime Learning Context

难度：中等偏难

### Prompt

请实现本地 prompt 模式下的 Dynamic Instructions，使 Agent 能根据运行时上下文动态调整系统指令。

要求：

1. 新增或完善 `AssistantRunContext`，至少包含：
   - `conversation_id`
   - `study_stage`
   - `assistant_mode`
2. 新增 `build_runtime_learning_context()`，根据学段和模式生成上下文片段。
3. `study_stage` 需要标准化为面向学习助手可读的学段标签。
4. `assistant_mode=exam` 或 `exam_boost` 时需要注入考试模式要求。
5. `load_dynamic_agent_instructions(agent_key)` 返回 callable instructions。
6. callable instructions 从 `RunContextWrapper.context` 读取 `AssistantRunContext`。

### 验收标准

- 没有运行时上下文时返回原始 instructions。
- 有 `study_stage` 时注入对应学段输出标准。
- 有考试模式时注入考试导向要求。
- 不向用户显式暴露“Runtime Learning Context”内部标签。
- `dynamic=True` 时本地模式返回 callable `instructions`。
- 远程 prompt 模式下不拼接本地 dynamic instructions。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_user_context python.ai_orchestrator.tests.test_prompt_resolver
```

---

## 题目 3：接入所有 Agent 创建入口

难度：中等

### Prompt

请把正式 Agent 创建入口统一改为使用 `resolve_agent_prompt_kwargs`。

需要覆盖：

1. Router Agent。
2. Specialist Agents。
3. Attachment Agent。
4. RouteDecision Agent。
5. Prompt Sheet Chat / Canvas 工作流。

要求：

- 面向用户输出的 Agent 使用 `dynamic=True`。
- 结构化输出 Agent 根据需要保持静态 prompt。
- Agent 构造点不要直接读取 prompt 文件或环境变量。
- 未知 agent key 应抛出清晰错误。

### 验收标准

- `router.py` 通过 resolver 创建 prompt kwargs。
- `specialists.py` 通过 resolver 创建 prompt kwargs。
- `attachment.py` 通过 resolver 创建 prompt kwargs。
- `route_decision.py` 通过 resolver 创建 prompt kwargs。
- `prompt_sheet_workflow.py` 通过 resolver 创建 prompt kwargs。
- 主要 Agent 在本地模式下可正常创建。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_agent_session_runner python.ai_orchestrator.tests.test_prompt_resolver
```

---

## 题目 4：收敛共享 Markdown 输出规范注入

难度：中等

### Prompt

请检查当前所有 Agent instructions 的共享 Markdown 输出规范注入逻辑，避免结构化输出 Agent 被错误注入面向用户的 Markdown 回复规范。

要求：

1. 普通学习助手 Agent 可以包含共享 Markdown 输出规范。
2. 结构化输出 Agent 不包含 Markdown 输出规范。
3. 至少排除：
   - `route_decision`
   - `prompt_sheet_canvas`
4. 保持共享输出规范只维护一份，避免每个 Agent prompt 重复粘贴。

### 验收标准

- `router` instructions 包含面向用户的 Markdown 输出规范。
- `polish` 等用户回复 Agent 包含 Markdown 输出规范。
- `route_decision` instructions 不包含 `Markdown 输出规范`。
- `prompt_sheet_canvas` instructions 不包含 `message.delta` 等用户回复流式规范。
- 重复 prompt 文本减少，后续修改共享规范只需要改一处。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_assistant_output_format_prompt python.ai_orchestrator.tests.test_route_agent_structure
```

---

## 题目 5：补测试与文档说明

难度：中等

### Prompt

请为 Prompt Resolver 与 Dynamic Instructions 补齐测试和文档说明。

测试至少覆盖：

1. local prompt resolution。
2. hybrid remote prompt resolution。
3. hybrid fallback local。
4. remote strict error。
5. dynamic local instructions 渲染 Runtime Learning Context。
6. remote prompt 模式保留 `Agent.prompt`，不返回本地 callable instructions。
7. 无效 variables JSON 报错。
8. 结构化输出 Agent 不注入 Markdown 输出规范。

文档至少说明：

1. 当前功能名称：Agent Prompt 解析与动态上下文注入。
2. `local` / `hybrid` / `remote` 的行为差异。
3. Dynamic Instructions 只在本地 instructions 模式生效。
4. 远程 prompt 需要通过 variables 或用户输入传入动态上下文。
5. 当前未实现 A/B Test、灰度发布、完整热加载。

### 验收标准

- 相关 Python 单元测试通过。
- AI 架构文档或 Prompt 管理文档包含当前机制说明。
- 文档明确区分“已实现”和“后续规划”。
- 不把 A/B Test、Prompt 热加载写成当前已实现能力。

建议测试：

```bash
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_prompt_resolver python.ai_orchestrator.tests.test_user_context python.ai_orchestrator.tests.test_assistant_output_format_prompt
cd docs
npm run build
```
