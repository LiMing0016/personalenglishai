# PEAI Agent 编排文档化 Trae 题目

## 背景

当前 PEAI 学习助手已经实现了基于 OpenAI Agents SDK 的多 Agent 编排雏形，包括：

- Router Agent / Route Decision 的路由判断。
- Specialist Agents 的任务分发。
- Runtime Learning Context 的运行时学习上下文。
- Dynamic Instructions 的动态系统指令注入。
- 本地 prompt 与远程 OpenAI Platform prompt 的解析策略。
- Agent 运行链路的基础可观测能力。

本轮目标不是继续扩展新 Agent，而是把“已经实现了什么、为什么这样设计、还有哪些缺陷和风险”沉淀成可维护文档，方便后续继续做 Function Tools、Retrieval、Eval Harness 和远程 prompt 同步。

核心原则：

- 文档必须基于当前代码实现，不写理想化架构。
- 流程图要能解释真实请求链路。
- 风险分析要明确到模块和改进方向。
- 不要暴露用户隐私、完整 prompt 内容或 OpenAI 密钥。

---

## 题目 1：梳理当前 Agent 编排实现

难度：中等

### Prompt

请阅读当前 Python Agent 编排相关代码，梳理 PEAI 学习助手目前已经实现的 Agent 工作流。

重点覆盖：

1. 用户请求如何进入 `AssistantAgentService`。
2. `AssistantRunContext` 中保存了哪些运行时上下文。
3. Router / Route Decision 如何判断目标任务。
4. Specialist Agents 如何承接具体学习任务。
5. `resolve_agent_prompt_kwargs` 如何决定本地 prompt、远程 prompt 和动态 instructions。
6. Runtime Learning Context 如何进入 Agent instructions 或 user message。

### 验收标准

- 输出当前真实链路，不要只描述目标设计。
- 能指出主要代码入口和模块职责。
- 能区分“已经实现”和“尚未实现”。
- 不要求修改业务逻辑。

---

## 题目 2：分析当前 Agent 工作流稳定性风险

难度：中等偏难

### Prompt

请基于当前代码实现，分析 PEAI Agent 编排工作流的稳定性风险，并给出改进优先级。

重点检查：

1. Router Agent 与 Route Decision 是否职责重叠。
2. Runtime Learning Context 是否可能重复注入。
3. 本地 prompt 与远程 OpenAI Platform prompt 是否可能漂移。
4. Dynamic Instructions 在 `local`、`hybrid`、`remote` prompt source 下是否行为一致。
5. Specialist Agents 的中间结果是否足够结构化。
6. SDK 兼容性和 tracing 失败是否会影响主流程。
7. 是否缺少 eval harness 验证路由稳定性。

### 验收标准

- 风险需要分 P0 / P1 / P2 或同等优先级。
- 每个风险都要说明影响、触发条件和建议修复方向。
- 不要把未实现能力写成已实现能力。
- 能明确当前工作流是否适合生产高稳定场景。

---

## 题目 3：补充 Agent 编排设计文档

难度：中等

### Prompt

请把当前已经实现的 Agent 编排任务写入项目文档。

建议更新：

- `docs/ai/learning-assistant-architecture.md`
- 如内容过长，可新建 `docs/ai/prompt-management.md` 或相近文档，并在 `docs/ai/index.md` 增加入口。

文档建议包含：

1. 当前实现概览。
2. 关键模块职责。
3. 已实现能力清单。
4. Dynamic Instructions 设计。
5. Runtime Learning Context 设计。
6. 本地 prompt / 远程 prompt 的关系。
7. 已知缺陷和后续路线。

### 验收标准

- 文档能让新开发者理解当前 Agent 编排链路。
- 文档中的文件路径、类名、函数名与代码一致。
- 不包含完整密钥、完整用户输入或敏感日志。
- 更新后能通过 docs 构建。

建议验证：

```bash
npm run build
```

---

## 题目 4：绘制 Agent 编排流程图

难度：中等

### Prompt

请在 Agent 架构文档中补充 Mermaid 流程图，展示当前 PEAI 学习助手的真实请求链路。

至少包含两张图：

1. 用户请求到最终回复的主流程。
2. Runtime Learning Context 与 Dynamic Instructions 的注入流程。

主流程建议覆盖：

```text
用户请求
-> AssistantAgentService
-> AssistantRunContext
-> Router / Route Decision
-> Specialist Agent
-> 结果汇总
-> 用户回复
```

动态上下文流程建议覆盖：

```text
用户画像 / 页面上下文 / 考试模式
-> Runtime Learning Context
-> Dynamic Instructions
-> Agent instructions 或 user message
```

### 验收标准

- Mermaid 语法合法，VitePress 可渲染。
- 图中的节点名称和当前代码概念一致。
- 图不要画尚未接入的 Retrieval / Function Tools 为已完成链路。
- 能区分本地 prompt 与远程 prompt 的差异。

---

## 题目 5：整理后续优化路线

难度：中等

### Prompt

请在文档中整理 Agent 编排后续优化路线，明确哪些能力还没有实现，以及它们应该解决什么问题。

至少覆盖：

1. Function Tools：按需读取考试标准、rubric、用户画像。
2. File Search / Retrieval：按语义检索知识库标准片段。
3. Handoff / Agent as Tool：在多意图任务中更稳定地组合专家能力。
4. Eval Harness：验证路由、上下文注入、输出格式和多轮追问。
5. Prompt 版本管理：本地 prompt 与 OpenAI Platform prompt 的同步策略。
6. Observability：run、trace、route decision、target agent 的排查链路。

### 验收标准

- 路线图按优先级排序。
- 每个优化项都说明解决的问题和建议落点。
- 不要求在本题中实现这些能力。
- 文档读者能据此拆出下一轮开发任务。
