---
title: 学习助手低复杂度任务连续性路由设计
date: 2026-07-23
status: approved-design
scope:
  - python/ai_orchestrator/schemas/routing.py
  - python/ai_orchestrator/adapters/route_request_adapter.py
  - python/ai_orchestrator/prompts/agent_instructions/route_decision.md
  - python/ai_orchestrator/services/route_decision_runner.py
  - python/ai_orchestrator/assistant_service.py
  - python/ai_orchestrator/tests
  - docs/agent/路由Agent设计.md
---

# 学习助手低复杂度任务连续性路由设计

## 1. 背景

当前学习助手主请求已经通过 `RouteDecision` 判断意图、工作流和目标 Agent，也会向目标 Agent 传递完整会话历史。但是，路由层尚未显式判断当前消息与历史任务之间的关系，因此容易出现以下问题：

- 用户回复“可以”“继续”“再给两个”时，被当成普通闲聊或新任务。
- 助手回复较长时，路由历史只保留前 600 个字符，末尾提出的后续动作可能被截断。
- 用户短暂切换话题后再回到先前单词、句子或作文任务时，Router 缺少明确的恢复判断。
- `ask_clarification` 只有缺失字段，没有稳定的用户可见澄清输出路径。
- 前端明确点击产生的 `interaction` 尚未完整进入 RouteRequest，Router 无法稳定区分明确动作和自然语言猜测。

仓库另有一套 `ContinuationClassifier` 与 `ActiveTaskState`，主要用于另一条聊天链路。该实现依赖内存状态和中文关键词预筛，服务重启后状态丢失，而且“可以”等确认表达会被预筛跳过。本设计不把它叠加到当前学习助手主链路，而是在已有单次 `RouteDecision` 中补齐任务连续性能力。

## 2. 目标

第一阶段以低复杂度方式提升同一会话内的路由智能度：

1. 在不增加模型调用次数的前提下识别新任务、继续、恢复、修改、确认和歧义。
2. 保持前端明确动作优先，避免模型重新猜测按钮语义。
3. 让 RouteDecision 获取有界但信息完整的近期上下文，特别保留长回复结尾。
4. 对低置信度或多候选场景直接澄清，不执行可能有副作用的操作。
5. 保持当前 Agent、workflow 和失败回退链路兼容。
6. 为未来独立 Continuity Classifier、长期记忆和跨对话任务恢复保留清晰接口边界，但本阶段不实现这些能力。

## 3. 非目标

本阶段不包含：

- 跨对话、跨平台或跨设备长期记忆。
- 持久化任务注册表、任务栈或新数据库表。
- XState 或其他新的状态机框架。
- 全站统一路由。
- 动态生成 workflow。
- 单词卡沉淀工作流、口语练习等尚未注册的业务能力。
- 大规模多语言数据标注和路由微调。

## 4. 方案选择

### 4.1 采用方案

扩展现有 `RouteDecision`，在一次结构化模型调用中同时完成：

- 任务连续性判断。
- 当前意图判断。
- 执行层级选择。
- workflow 和 target Agent 选择。
- 置信度与澄清判断。

### 4.2 未采用方案

#### 独立 Continuity Classifier

优点是职责清楚、方便独立评测；缺点是每条消息增加一次模型调用和额外延迟。本阶段不采用，但 `relation` 字段可以在未来直接成为独立分类器的输出契约。

#### 规则优先、模型兜底

优点是成本低；缺点是需要枚举“可以”“继续”等自然语言表达，不适合多语言，也会继续积累脆弱规则。本阶段不新增此类关键词规则。

## 5. 路由职责与数据流

```text
前端请求
  ├─ 当前消息
  ├─ conversationHistory
  ├─ interaction
  └─ 页面、选区、作文与学段上下文
          ↓
RouteRequest Adapter
  ├─ 提取明确交互动作
  ├─ 构建有界近期历史
  └─ 对长消息保留开头与结尾
          ↓
一次 RouteDecision
  ├─ task relation
  ├─ intent
  ├─ route type
  ├─ workflow
  ├─ target agent
  └─ confidence / clarification
          ↓
Assistant Service
  ├─ 直接澄清
  ├─ 运行单 Agent 或命名 workflow
  └─ RouteDecision 故障时沿用当前回退
          ↓
目标 Agent
  └─ 接收完整会话历史并生成最终学习内容
```

RouteDecision 只负责决策，不实现词汇、写作、翻译等领域内容。专业 Agent 继续负责生成最终学习回答。

## 6. 三层执行模型

### 6.1 `answer_direct`

适用于普通解释、轻量问答、结束语和不需要固定业务步骤的响应。

### 6.2 `specialist_single_turn`

适用于一个专业 Agent 可以完成的开放式任务，例如：

- 词汇解释。
- 单句结构分析。
- 翻译。
- 表达润色。
- 普通练习设计。

### 6.3 命名 workflow

仅用于预先注册的稳定业务流程。新增 workflow 应满足至少多个以下条件：

- 包含两个以上固定步骤。
- 输入输出有明确结构。
- 调用工具或写入持久化数据。
- 需要失败重试、恢复或幂等控制。
- 会被多个入口复用。
- 单次 Agent 自由回答无法可靠完成。

模型只能从 `WorkflowName` 枚举中选择，不能创造未注册名称。

本阶段继续使用现有 workflow 集合：

```text
specialist_single_turn
writing_evaluation
first_draft_coach
realtime_sentence_feedback
```

`sentence_reorder` 继续由明确 `interaction` 在现有专用入口处理。`vocab_card_capture` 等能力在真正实现和注册前不得被 Router 选择。

## 7. 路由契约

### 7.1 任务关系

新增 `TaskRelation`：

```text
new_task
continue_active
resume_prior
modify_previous
confirm_action
unclear
```

字段含义：

| relation | 含义 |
| --- | --- |
| `new_task` | 当前消息开启一个新任务或明确切换领域 |
| `continue_active` | 延续最近任务，例如追加例句或继续讲解 |
| `resume_prior` | 中间切换过话题后，恢复近期更早任务 |
| `modify_previous` | 修改上一轮产物，例如简化、提高难度、换一种表达 |
| `confirm_action` | 确认助手上一轮明确提出的可执行后续动作 |
| `unclear` | 无法可靠关联任务或存在多个同样合理的候选 |

`out_of_scope` 继续由已有 `intent=out_of_scope` 和 `route_type=out_of_scope` 表达，不重复作为 task relation。

### 7.2 RoutingDecision 示例

```json
{
  "relation": "confirm_action",
  "intent": "vocab",
  "route_type": "run_workflow",
  "workflow": "specialist_single_turn",
  "target_agent": "vocab",
  "confidence": 0.88,
  "required_inputs": [],
  "missing_inputs": [],
  "clarification_question": null,
  "normalized_inputs": {
    "has_essay_text": false,
    "has_topic_prompt": false,
    "has_selected_text": false,
    "current_page": "/app/assistant"
  },
  "reason": "用户确认上一轮词汇 Agent 提出的继续操作。"
}
```

### 7.3 契约校验

- `run_workflow` 必须包含已注册 `workflow` 和 `target_agent`。
- `ask_clarification` 必须包含非空 `missing_inputs` 与 `clarification_question`。
- `relation=unclear` 必须使用 `route_type=ask_clarification`。
- `relation=confirm_action` 只有在历史或明确 interaction 中存在可确认动作时才成立。
- 找不到待确认动作时，“可以”不得触发工具或有副作用 workflow。
- 非 `ask_clarification` 路由的 `clarification_question` 必须为空。
- 未注册 workflow 和 Agent 继续通过 Pydantic 枚举阻止进入执行层。

## 8. RouteRequest 上下文装配

### 8.1 优先级

路由证据按以下优先级解释：

```text
明确 interaction
> 当前消息中的完整明确请求
> 近期会话关系
> 前端默认 intent
```

前端默认 `intent=free_chat` 不能覆盖 RouteDecision 从当前消息识别出的专业意图。

### 8.2 interaction 摘要

RouteRequest 增加最小交互上下文：

```json
{
  "source": "response_action",
  "ui_intent": "show_learning_card",
  "action_id": "create_vocab_card",
  "active_activity_id": null,
  "exercise_type": null,
  "topic": "hive",
  "difficulty": null
}
```

Adapter 只负责协议转换，不解释业务流程。明确交互仍可在进入 RouteDecision 前由现有专用入口直接处理，例如重组成句。

### 8.3 近期历史预算

第一版采用以下有界策略：

- 最多 10 条非空近期消息，约 5 轮对话。
- 全部路由历史总预算约 6000 字符。
- 单条消息最多约 900 字符。
- 短消息完整保留。
- 超长消息保留开头约 300 字符和结尾约 600 字符，中间使用明确省略标记。
- 从最新消息向前分配总预算，优先保留最近上下文。

该策略不调用额外摘要模型。超过预算的更早历史不在本阶段恢复范围内。

## 9. 平衡型容错

置信度阈值应集中定义并可测试，不散落在 Prompt 或多个 service 中：

- `confidence >= 0.75`：采用 RouteDecision。
- `0.50 <= confidence < 0.75`：当 `relation` 不是 `unclear`、路由契约完整且目标流程无副作用时采用；否则澄清。
- `confidence < 0.50`：进入澄清。
- `relation=unclear`：始终澄清。

模型置信度只作为信号，不能覆盖明确 interaction，也不能使未注册 workflow 获得执行权限。未来新增写入数据或调用外部系统的 workflow 时，中等置信度不得执行；此类 workflow 必须由明确 interaction、完整用户指令或独立确认机制授权。

## 10. 澄清响应

RoutingDecision 新增可选 `clarification_question`。Route Agent 仍不回答学习内容，但在 `ask_clarification` 时可以生成一句用于消除歧义的问题。

示例：

```json
{
  "relation": "unclear",
  "intent": "free_chat",
  "route_type": "ask_clarification",
  "workflow": null,
  "target_agent": null,
  "confidence": 0.42,
  "required_inputs": ["referenced_word"],
  "missing_inputs": ["referenced_word"],
  "clarification_question": "你指的是刚才的 hive，还是另一个单词？",
  "reason": "近期对话包含多个候选单词。"
}
```

Assistant Service 对 `ask_clarification` 直接返回该问题，不再调用第二个 Agent。流式接口保持当前事件协议，依次输出开始、消息创建、文本增量和完成事件。

下一轮用户补充信息后重新运行 RouteDecision，不建立额外等待状态。

## 11. 失败处理

| 场景 | 行为 |
| --- | --- |
| RouteDecision 超时或调用失败 | 记录日志和 trace，沿用当前 `route_assistant_agent` 回退，不阻断对话 |
| RouteDecision 结构校验失败 | 与调用失败相同，不把内部错误暴露给用户 |
| 未注册 workflow 或 target Agent | Schema 拒绝；记录错误并安全回退 |
| 明确 interaction 已失效 | 返回明确的操作失效提示，不猜测替代动作 |
| `confirm_action` 找不到历史动作 | 改为普通回应或 `unclear`，禁止有副作用执行 |
| 澄清流式输出中断 | 使用现有流式错误事件与前端重试路径 |

## 12. 与现有 Continuation Classifier 的边界

本阶段只升级当前学习助手主请求使用的 RouteDecision 链路：

- 不让主链路再调用额外 Continuation Classifier。
- 不把内存 `ActiveTaskState` 引入主请求。
- 不扩展中文 `_CONTEXTUAL_HINTS`、`_ACKNOWLEDGEMENT_HINTS` 和 `_NEW_TASK_HINTS`。
- 现有旧链路代码是否移除，留待后续独立清理需求；本次不做无关重构。

## 13. 测试设计

### 13.1 Schema 测试

- 六种 relation 均可序列化和反序列化。
- `run_workflow` 缺少 workflow 或 target Agent 时拒绝。
- `ask_clarification` 缺少问题或 missing inputs 时拒绝。
- `unclear` 配合非澄清 route type 时拒绝。
- 未注册 workflow 或 Agent 时拒绝。

### 13.2 Adapter 测试

- 最多保留 10 条近期有效消息。
- 路由历史不超过总预算。
- 超长助手回复同时保留开头和结尾。
- interaction 字段完整映射。
- 空消息不会占用历史预算。

### 13.3 路由回归集

建立约 20 至 30 条最小回归案例，至少覆盖：

1. `hive 是什么意思` -> `new_task / vocab`。
2. `再给两个例句` -> `continue_active / vocab`。
3. 助手提出制作卡片后用户回复“可以” -> `confirm_action / vocab`。
4. 单词、无关话题、再回到 hive -> `resume_prior / vocab`。
5. 词汇任务后明确要求翻译 -> `new_task / translation`。
6. 历史包含多个单词后用户说“它” -> `unclear`。
7. “谢谢”“明白了” -> 普通结束，不误执行确认动作。
8. “简单一点” -> `modify_previous`。
9. `Sure, make the card` 与中文确认语义一致。
10. 前端明确启动重组成句 -> interaction 优先。

回归数据使用语言无关的期望枚举，不以中文关键词匹配实现。

### 13.4 Service 与流式测试

- 成功路由到目标 Agent。
- `ask_clarification` 直接返回问题，不调用目标 Agent。
- RouteDecision 失败后使用现有回退。
- 明确 interaction 的专用入口保持不变。
- 澄清响应产生完整流式事件序列。
- run metadata 和 Route Debug 展示 relation、置信度和原因。

### 13.5 最小在线 smoke

在配置真实模型的环境中，通过 Route Debug 验证至少以下链路：

```text
hive -> 可以制作卡片 -> 可以
hive -> 无关问题 -> 回到 hive
多个单词 -> 它还有什么搭配
English confirmation -> 对应专业 Agent
```

在线 smoke 结果只用于验证真实模型行为，不替代离线 schema、adapter 和 service 测试。

## 14. 验收标准

- RouteDecision 仍只有一次模型调用。
- 明确 interaction 稳定命中已有专用动作或注册 workflow。
- 最小回归集中明确场景的 relation 与 intent 准确率不低于 90%。
- 歧义场景不会执行有副作用的工具。
- RouteDecision 故障不阻断普通对话。
- 中英文确认表达不依赖关键词枚举。
- 长助手回复末尾的明确后续动作可被路由层看到。
- 当前 API 和前端基础请求格式保持向后兼容。

## 15. 文档与后续演进

实现时同步更新 `docs/agent/路由Agent设计.md`，说明：

- task relation 契约。
- 三层执行模型。
- interaction 优先级。
- 上下文预算。
- 澄清与失败回退。
- Route Debug 与回归验证方式。

后续只有在真实数据表明单次 RouteDecision 的连续性判断成为瓶颈时，才拆出独立 Continuity Classifier。跨对话、跨设备长期记忆应作为独立设计，不在本次实现中顺带扩展。

## 16. 分支与合并评估

本设计会修改 Router schema、Prompt、Adapter、Assistant Service 和测试，属于跨模块主链路行为调整。实现阶段建议使用独立 `codex/` 分支。完成全部回归、真实模型 smoke 和文档更新后，可以评估合并到 `main`；设计文档本身可以独立合并。
