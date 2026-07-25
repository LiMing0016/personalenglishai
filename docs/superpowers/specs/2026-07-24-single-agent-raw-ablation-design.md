# 原始单 Agent 消融环境设计

**日期：** 2026-07-24  
**状态：** 已实现，待合并

**目标分支：** `codex/single-agent-raw-ablation-design`

> 当前产品方向已调整：不增加第三套联网 Agent。`WebSearchTool` 直接加入
> 现有 `single_agent_raw`，让“原始模型”成为逐步增加工具能力的单 Agent
> 路线。详见[原始单 Agent 能力扩展](../../agent/原始单Agent能力扩展.md)。

## 1. 背景

当前学习助手已经具备一套多 Agent 运行环境，包括 RouteDecision、Router Agent、多个 Specialist Agent、handoff、专项 Prompt、任务状态和结构化学习工作流。

本设计不重构、删除或逐步关闭现有实现，而是在同一服务中并行增加一套完全独立的原始单 Agent 环境。开发者可以通过运行模式开关在两套环境之间选择，并使用固定对话集观察基础模型在没有 PEAI 路由、工具、专项 Prompt 和专业 Agent 时的原生能力范围。

这是一项架构消融实验，不是对现有多 Agent 架构的替换决定。

## 2. 实验问题

第一阶段只回答以下问题：

> 使用同一个模型时，移除 PEAI 应用级 Prompt、RouteDecision、Specialist Agent、handoff、工具和工作流后，模型仅依靠会话历史能够达到怎样的通用对话与上下文理解能力？

第一阶段不回答：

- 单 Agent 英语专项 Prompt 能否替代多个 Specialist Prompt。
- 单 Agent 加业务工具后能否替代现有学习工作流。
- 哪种架构更适合正式生产。
- 跨对话、跨设备长期记忆是否有效。

如果原始单 Agent 已经表现出足够好的上下文能力，后续可以增加 `single_agent_english` 作为第三组，单独测量英语专项 Prompt 的收益。

## 3. 运行模式

系统支持两个稳定模式代码：

| 模式 | 含义 |
| --- | --- |
| `multi_agent` | 当前完整多 Agent 环境，保持现有行为 |
| `single_agent_raw` | 无应用级 Prompt、带托管网页搜索工具、无路由的单 Agent 环境 |

环境变量定义默认模式：

```env
AI_ASSISTANT_AGENT_MODE=multi_agent
```

未配置时必须默认使用 `multi_agent`，保持现有部署向后兼容。

本地和开发环境允许请求携带 `agentMode` 覆盖默认值。生产环境只有管理员或受控实验请求可以覆盖，普通用户提交的模式值不得直接生效。

未知模式必须返回明确的请求校验错误，不允许静默选择另一种模式。

## 4. 总体架构

```text
Assistant HTTP Endpoint
          |
          v
AssistantRuntimeResolver
          |
          +--------------------------+
          |                          |
          v                          v
AssistantAgentService        RawSingleAgentService
现有 multi_agent             新增 single_agent_raw
          |                          |
          v                          v
现有 Route/Agents/Tools      Raw Single Agent
                                     +
                              WebSearchTool
          |                          |
          +-------------+------------+
                        |
                        v
       现有 AssistantReply / StreamEvent 协议
```

### 4.1 AssistantRuntimeResolver

入口选择器只负责：

1. 解析有效运行模式。
2. 应用环境默认值与受控请求覆盖规则。
3. 返回对应服务实现。
4. 把最终模式写入 Trace 和运行元数据。

入口选择器不得承担业务路由、意图识别或上下文判断。

### 4.2 现有多 Agent 环境

`AssistantAgentService` 保持现有内部实现，不为单 Agent 模式增加散落的条件分支。

现有以下代码继续只属于 `multi_agent`：

- RouteDecisionRunner。
- Router Agent。
- Specialist Agent。
- handoff 和 Agent-as-tool。
- ActiveTaskState 与 continuation classifier。
- 结构化练习工作流。
- 学习卡片与业务工具。
- 现有 Prompt Resolver 和动态学习标准。

### 4.3 原始单 Agent 环境

新增 `RawSingleAgentService` 和独立 Agent factory。它们与现有服务共享 HTTP 契约和通用运行基础设施，但不复用多 Agent 的路由与 Prompt 构建逻辑。

原始 Agent 的定义等价于：

```python
Agent(
    name="Raw Single Agent",
    model=model,
    tools=[WebSearchTool()],
)
```

不设置：

- `instructions`。
- `prompt`。
- `handoffs`。
- `output_type`。
- Agent guardrail 或业务审批流程。

Agent 名称只用于 Trace 和运行元数据，不承载业务指令。

## 5. “零应用级 Prompt”的严格定义

`single_agent_raw` 不得向模型注入任何 PEAI 自定义指令，包括：

- 通用系统上下文。
- 英语学习助手身份。
- Markdown 输出规范。
- 用户画像和学段标准。
- intent、mode 或路由提示。
- Specialist Prompt。
- 卡片、工作流或状态说明。
- “如何处理上下文”的额外说明。
- 运行时拼接的任务规则。

模型只接收：

1. Raw Session 中已有的真实 `user`/`assistant` 消息。
2. 当前用户实际提交的文本。
3. 当前用户实际提交的选中文本、图片或文件内容。

平台和模型提供方自身的安全行为、基础行为和内部默认设置不属于 PEAI 应用级 Prompt，无法通过本实验移除。实验结论必须表述为“无 PEAI 应用级 Prompt”，不得表述为“完全无系统行为的裸模型”。

## 6. 输入构建

现有请求 Schema 保持兼容，前端可以继续发送 `mode`、`intent`、`studyContext`、`interaction` 等字段，但 `single_agent_raw` 必须忽略这些业务字段。

新增原始输入构造器，只转换用户实际提供的输入：

- 当前消息文本。
- 选中文本及其来源内容。
- 用户上传的图片。
- 用户上传或已经解析完成的文件。

原始输入构造器不得：

- 追加用户画像文本。
- 追加学段标准。
- 追加助手模式。
- 追加 intent 提示。
- 追加 interaction 的业务解释。
- 把 ActiveTaskState 写入模型输入。

附件继续沿用现有安全检查、文件权限和模型输入格式，但不得先交给 Attachment Agent 处理。模型不支持的附件仍按现有输入校验规则返回错误。

## 7. Session 与上下文管理

### 7.1 单一上下文来源

`single_agent_raw` 使用服务端 Agents SDK Session 保存会话历史。正常运行时每轮只向 Runner 提交当前用户输入，由 Session 提供此前消息。

禁止同时向模型提交完整 `conversationHistory` 和同一会话的 SDK Session，否则会重复历史内容。

如果某个调用方明确提供一份需要重放的完整历史，必须使用无 Session 的一次性运行；该规则应和现有请求处理策略保持一致。

### 7.2 Session 命名空间

两套环境使用不同命名空间：

```text
multi:{conversationId}
single-raw:{conversationId}
```

当前后端生成的 `conversationId` 是全局唯一且经过用户归属校验，因此第一版以“运行模式命名空间 + conversationId”作为 Session key。这样可以防止不同运行模式共享历史，也不会让两个用户命中同一个会话。若未来允许外部调用方自行指定非全局唯一会话 ID，再升级为显式加入用户标识。

### 7.3 模式切换

第一版不支持在同一对话中途切换运行模式。开发调试界面切换模式时必须新建实验对话。

这样可以避免以下污染：

- 多 Agent 生成的历史被 Raw Agent 当作自身上下文。
- Raw Agent 的回答改变多 Agent 的后续路由。
- 不同模式共享 SDK `previous_response_id` 或 Session。

如需比较相同历史，由评测工具分别创建两个新 Session，按相同顺序重放同一组输入。

### 7.4 长上下文

第一版使用当前 Agents SDK Session 和模型上下文能力，不新增：

- 连续性分类模型。
- 对话摘要 Agent。
- 向量记忆。
- 长期记忆数据库。
- 跨对话任务恢复。

当上下文超过模型限制时，后续再单独设计 compaction 实验。第一版只记录长对话失败、截断和质量下降，不在消融变量中加入新的压缩能力。

## 8. 输出协议

`RawSingleAgentService` 必须兼容现有非流式与流式响应协议。

非流式继续返回 `AssistantReply`。流式继续返回：

1. `run.started`
2. `message.created`
3. 一个或多个 `message.delta`
4. `message.completed`
5. `run.completed`

Raw Agent 的学习 Block 始终为空：

```json
{
  "parts": []
}
```

Raw Agent 可以自然输出模型生成的普通文本或 Markdown，但 PEAI 不向它注入任何 Markdown 格式要求。

运行元数据必须包含最终模式：

```json
{
  "agentMode": "single_agent_raw"
}
```

Raw Agent 不生成假的 RouteDecision、目标 Agent 步骤或 handoff。实际发生的
网页搜索会记录为 `tool_calls` 步骤。

## 9. 错误处理与回退

为了保证实验有效：

- Raw Agent 运行失败时直接返回现有 `run.failed` 协议。
- Raw Agent 不得静默回退到多 Agent 环境。
- 多 Agent 运行失败时也不得自动切换到 Raw Agent。
- 未知运行模式返回请求校验错误。
- Session 初始化失败、模型不可用和附件不支持必须分别记录明确错误代码。

重试必须在同一运行模式中完成，不得改变实验组。

## 10. 可观测性

所有运行增加以下维度：

- `agent_mode`
- `conversation_id`
- `model`
- `input_scope`
- `streaming`

至少记录：

- 模型请求次数。
- 输入 Token。
- 缓存输入 Token。
- 输出 Token。
- 总 Token。
- 首个文本 delta 延迟。
- 总运行延迟。
- 最终状态。
- Trace ID。

`single_agent_raw` 不需要搜索时通常只发生一次模型请求；触发搜索时由 SDK
完成工具循环，模型请求数和工具调用数必须从 Trace 中如实记录。

实验日志不得记录完整用户私密文本。固定评测集可以记录 case id 和评分结果。

## 11. 消融实验

### 11.1 公平性约束

两组必须使用：

- 相同模型。
- 相同模型提供方。
- 相同输入顺序。
- 相同附件。
- 相同网络与部署环境。
- 独立且全新的 Session。

因为两组 Prompt 和架构本身不同，第一阶段结论只能比较“当前完整 PEAI 架构”与“原始模型基线”，不能把差异全部归因于 Agent 数量。

### 11.2 固定对话组

#### 跨话题恢复

```text
1. hive 是什么意思？
2. 再给两个例句。
3. 上海明天天气怎么样？
4. 回到刚才的 hive，它还有哪些搭配？
```

#### 修改上一轮输出

```text
1. 分析 important 和 significant 的区别。
2. 简单一点。
3. 再正式一点。
```

#### 多语言切换

```text
1. 帮我翻译这句话。
2. Actually, explain the second phrase in English.
3. 还是用中文说吧。
```

#### 歧义指代

先后讨论 `hive` 与 `swarm`，随后询问：

```text
它还有哪些搭配？
```

记录模型是主动澄清、正确选择，还是错误猜测。

#### 明确新任务

在单词讨论中突然提出完整翻译、写作或编程问题，观察模型能否自然切换而不强行延续旧任务。

#### 长对话

使用至少 20 轮的固定脚本，在后半段恢复早期实体，观察上下文保持、遗漏和错误关联。

#### 原生能力边界

询问实时天气、外部搜索、保存单词、打开文件或执行操作，记录模型是否诚实表达能力边界。因为 Raw Agent 没有约束 Prompt，出现不真实能力声明是合法的实验结果，不应在第一阶段用额外提示修正。

### 11.3 评分维度

人工或模型评审使用统一量表：

| 维度 | 说明 |
| --- | --- |
| 当前请求完成度 | 是否真正回答当前问题 |
| 上下文连续性 | 是否正确继承相关历史 |
| 话题切换 | 是否在明确新任务时停止旧任务 |
| 指代解析 | 是否正确理解或主动澄清 |
| 事实正确性 | 内容是否明显错误 |
| 表达清晰度 | 回答是否自然、易读 |
| 能力诚实性 | 是否虚构搜索、保存或执行能力 |
| 英语学习质量 | 解释、例句、翻译和修改是否有学习价值 |

同时比较模型请求数、Token 和延迟。不得用精确文本匹配评价自然语言质量。

## 12. 测试策略

### 12.1 结构测试

验证 Raw Agent：

- `instructions` 为空。
- `tools` 只包含 `WebSearchTool`。
- `handoffs` 为空。
- 未设置结构化 `output_type`。
- 未调用 Prompt Resolver。

### 12.2 运行模式测试

验证：

- 未配置模式时选择 `multi_agent`。
- 显式 `single_agent_raw` 选择新服务。
- 非法模式返回校验错误。
- 生产环境忽略未授权请求覆盖。
- 本地或管理员覆盖可以生效。
- 现有多 Agent 构造和测试不受影响。

### 12.3 Session 测试

验证：

- 两种模式 Session key 不同。
- 不同用户 Session key 不同。
- 切换模式时要求新建对话。
- 使用 SDK Session 时不重复附加 `conversationHistory`。
- 重放历史时使用无 Session 路径。

### 12.4 响应协议测试

验证：

- Raw Agent 非流式回复兼容当前 API。
- Raw Agent 流式事件顺序与现有前端兼容。
- `parts` 始终为空。
- 运行元数据包含 `agentMode`。
- Raw Agent 失败不会进入多 Agent fallback。

### 12.5 真实模型评测

固定对话集必须通过真实 Agent 运行路径执行，不用 mock 结果代替。评测输出至少包含：

- case id。
- 模式。
- Trace ID。
- Token。
- 延迟。
- 回答文本。
- 人工评分字段。

缺少模型 API 配置时，只能运行结构和协议测试，交付记录必须明确标记真实模型评测未运行。

## 13. 安全与兼容

- `multi_agent` 是默认值，现有调用方不需要新增字段。
- Raw Agent 复用现有用户认证、附件授权和请求大小限制。
- 请求覆盖模式只在受控环境开放。
- 模式代码使用稳定的语言无关枚举。
- 前端用户可见文案与模式代码分离。
- 不删除或迁移现有 Session。
- 不修改现有多 Agent Prompt。
- 不把 Raw Agent 暴露为正式生产能力，除非后续实验明确批准。

## 14. 实施边界

第一阶段实施只包括：

1. 运行模式枚举与解析。
2. Raw Agent factory。
3. 原始输入构造器。
4. RawSingleAgentService。
5. Session 命名空间隔离。
6. API 和流式协议接入。
7. 开发调试模式开关。
8. Trace 与指标维度。
9. 结构、协议和固定对话评测入口。

第一阶段明确不包括：

- 除 `WebSearchTool` 以外的业务工具。
- 单词卡片。
- 学习 Block。
- 英语专项 Prompt。
- 长期记忆。
- compaction。
- 线上随机分流。
- 自动统计显著性。
- 替换或重构现有多 Agent 服务。

## 15. 验收标准

- 默认运行模式仍为 `multi_agent`。
- 现有多 Agent 测试与行为保持不变。
- 可以通过受控开关启动 `single_agent_raw`。
- Raw Agent 没有任何 PEAI 应用级 Prompt、路由或 handoff。
- Raw Agent 只声明 `WebSearchTool`，后续工具继续在同一个 Agent 上扩展。
- Raw Agent 不调用 RouteDecisionRunner 或 Specialist Agent。
- 两种模式使用独立 Session。
- 两种模式返回相同的 HTTP 和流式协议。
- Raw Agent 的 `parts` 始终为空。
- Raw Agent 失败不会回退到多 Agent。
- Trace 可以按 `agent_mode` 比较请求数、Token 和延迟。
- 固定上下文对话集可以分别在两种模式下运行。
- 所有未运行的真实模型评测在交付说明中如实记录。

## 16. 实施验证记录

2026-07-24 使用本地 `backend/.env` 中已有的 OpenAI 配置，对
`gpt-5.4-mini` 执行原始单 Agent 真实会话验证。密钥只加载到进程环境，
未写入仓库、日志或评测输出。

同一 SDK Session 内依次验证：

1. 询问 `hive` 的含义。
2. 切换到无关的“光合作用”问题。
3. 通过“回到刚才的英文单词”恢复 `hive` 主题。
4. 通过“再简单一点”修改上一轮回答。

四轮均由 `Raw Single Agent` 单次模型请求完成，运行元数据为
`agentMode=single_agent_raw`，未经过业务路由或 handoff。

随后按产品方向把 `WebSearchTool` 直接加入同一个 `Raw Single Agent`。真实
查询 OpenAI Agents SDK 官方文档和安顺市当天实时天气时，运行元数据仍为
`agentMode=single_agent_raw`，并记录一次 `web_search`；页面仍只展示
“多 Agent”和“原始模型”两个选项。
