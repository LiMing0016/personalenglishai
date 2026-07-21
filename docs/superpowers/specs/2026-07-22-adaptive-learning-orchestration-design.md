# 学习助手自适应学习编排与互动卡片设计

## 背景

当前学习助手已经具备普通 Markdown 对话、结构化学习块组件和多个英语学习入口，但这些能力仍然以“回答内容”为中心：

- 普通问题和明确练习请求缺少稳定的交互分流规则。
- 现有 `vocab_card`、`sentence_analysis`、`grammar_tree` 等学习块尚未形成可持续扩展的注册机制。
- 后端流式完成事件主要返回文本，结构化 `parts` 尚未成为真实对话主链路的一部分。
- 互动题型没有统一的开始、作答、判分、反馈、下一题和退出生命周期。
- 如果完全依赖模型决定卡片和页面，容易出现误判、结构漂移和无法测试的问题。

产品目标不是把每一次对话都变成练习，而是让学习助手逐步覆盖英语学习的大多数场景，同时保持普通提问的低干扰体验。

本设计采用“自适应学习编排”方案：自然语言意图、用户显式操作、动态下一步建议、结构化学习卡片和 XState 互动活动状态机共同工作；模型负责理解和生成，确定性流程由代码控制。

## 设计结论

1. 普通问答默认使用 Markdown，不强制展示卡片或进入练习。
2. 系统根据用户意图选择最低必要的呈现强度：快速回答、教学增强或互动练习。
3. 用户点击的显式操作优先于模型意图判断。
4. 只读学习内容和互动练习使用不同类型的学习块，但共享统一数据外壳和卡片注册表。
5. 互动练习使用 XState v5 和 `@xstate/vue` 管理前端活动生命周期。
6. 状态机只管理互动活动，不接管普通对话和只读知识卡。
7. 模型不能直接选择任意前端组件或修改活动状态；所有结构化输出必须通过协议校验。
8. 第一阶段只用“重组成句”验证完整互动闭环，不一次开发全部题型。

## 产品目标

### 用户目标

- 用户只想问问题时，可以立即获得简洁回答。
- 用户需要深入理解时，可以自然展开单词、句子或语法卡片。
- 用户明确想练习时，可以进入连续、可退出、可反馈的互动活动。
- 用户不会因为进入活动而被锁定在某个场景中，可以暂停、追问或退出。
- 不同英语学习场景保持一致的开始、作答、反馈和继续方式。

### 平台目标

- 对话成为统一入口，而不是产品全部。
- 卡片成为可扩展的英语学习组件系统，而不是一组硬编码模板。
- 互动题型共享一个可测试的活动生命周期。
- 后续可以增加选择题、图片题、配对、听力和口语，而不重写主渲染器和流程控制。
- 模型输出失败时仍然保留可用的 Markdown 回答。

## 非目标

- 第一阶段不实现完整自适应课程、长期学习路径或能力图谱。
- 第一阶段不持久化学生的练习尝试、分数和跨设备活动状态，只预留活动标识和事件接口。
- 第一阶段不实现语音识别、发音评分、图片生成或外部图片资产管理。
- 不要求所有英语回答返回固定教学结构。
- 不让模型自主控制路由、组件名称或活动状态转换。
- 不使用 XState 替换 Pinia、Vue Query 或普通 Vue 组件状态。

## 渐进式响应

### 快速回答

适用场景：查词、确认表达、简单区别和普通咨询。

输出：普通 Markdown。回答后可以提供一到两个动态下一步操作，例如“再举一个例子”或“查看完整单词卡”。

### 教学增强

适用场景：分析具体单词、句子或语法，结构化卡片能够明显提升理解。

输出：一句简短结论加只读学习卡片。避免同时生成一份完整 Markdown 讲义和重复内容的卡片。

### 互动练习

适用场景：用户明确使用“练习、测试、出题、跟读、陪我学”等意图，或者点击“练一题”等显式操作。

输出：简短过渡文本加互动活动卡片。活动进入 XState 管理的生命周期。

## 入口与路由优先级

### 用户可见入口

1. 自由输入：用户直接用自然语言提问。
2. 首页快捷操作：查单词、分析句子、讲解语法、批改写作、出题练习、口语陪练。
3. 回答后动态操作：练一题、查看对比卡、再举例、加入复习。
4. 活动内操作：提交、提示、下一题、重试、结束。

首页只保留少量高频入口。新增题型不默认增加永久入口，而是通过动态操作、自然语言和场景工作台进入。

### 路由优先级

```text
用户显式操作
  > 当前活动上下文
  > 自然语言意图候选
  > 普通 Markdown 问答
```

- 显式操作携带确定的 `uiIntent`，不再让模型猜测按钮含义。
- 已进入活动时，卡片内事件优先路由到当前 `activityId`。
- 模型只返回意图候选和置信度，最终呈现方式由策略层决定。
- 意图不确定、结构校验失败或场景不受支持时，回退为 Markdown。

### 请求上下文

```ts
interface AssistantInteractionContext {
  message: string
  source: 'composer' | 'quick_action' | 'response_action' | 'activity_action'
  uiIntent?: string
  activeActivityId?: string
  context?: Record<string, unknown>
}
```

按钮不能只向输入框注入隐藏提示词。按钮必须发送明确的结构化意图，同时允许保留用户可见文本。

## 输出类型

### 普通 Markdown

用于开放问题、简单解释、学习计划和不适合结构化的内容。

### 只读学习块

第一批只读学习块：

- `vocab_card`：单词、音标、发音、词性、语境释义、词形、搭配和分级例句。
- `sentence_analysis`：原句、修改后、差异、句子角色、语块、语法原因、翻译和自然表达。
- `grammar_explanation`：结构公式、使用场景、肯定/否定/疑问形式、常见错误和相似结构对比。

只读学习块不进入活动状态机。

### 互动活动块

第一种互动活动块为 `sentence_reorder`。后续可增加：

- `multiple_choice`
- `image_choice`
- `matching`
- `fill_blank`
- `error_correction`
- `listening_choice`
- `speaking_practice`

## 学习块注册表

当前硬编码的联合类型和总渲染器应演进为注册式架构。每个学习块包包含：

```text
learning-blocks/
├─ registry.ts
├─ vocab-card/
│  ├─ schema.ts
│  ├─ VocabCard.vue
│  ├─ fallback.ts
│  └─ tests/
├─ sentence-analysis/
├─ grammar-explanation/
└─ sentence-reorder/
```

### 通用数据外壳

```ts
interface AssistantLearningBlock<TData = unknown> {
  id: string
  type: string
  version: number
  title?: string
  data: TData
  actions?: AssistantBlockAction[]
  fallbackMarkdown: string
}
```

注册项负责：

- 类型和版本识别。
- Schema 校验和规范化。
- Vue 组件的延迟加载。
- 无效数据的 Markdown 降级。
- 允许操作和事件映射。
- 单元测试与视觉样例。

新增普通卡片时，原则上只新增一个块目录、一条注册记录和相应的后端 Schema，不修改总渲染器的条件分支。

## XState 互动活动状态机

### 技术选型

- 核心库：XState v5。
- Vue 接入：`@xstate/vue`。
- Pinia 继续负责用户、设置、对话列表和普通全局状态。
- XState 只负责单个互动活动的流程状态。

### 通用生命周期

```text
idle
  → preparing
  → awaitingAnswer
  → submitting
  → reviewing
  → preparing | completed

任意活动状态
  → cancelled

异步失败
  → error
  → retry | cancelled
```

### 核心事件

```ts
type LearningActivityEvent =
  | { type: 'START'; block: AssistantLearningBlock }
  | { type: 'LOAD_SUCCESS'; payload: unknown }
  | { type: 'LOAD_ERROR'; error: ActivityError }
  | { type: 'ANSWER_CHANGE'; answer: unknown }
  | { type: 'REQUEST_HINT' }
  | { type: 'SUBMIT' }
  | { type: 'SUBMIT_SUCCESS'; result: ActivityResult }
  | { type: 'SUBMIT_ERROR'; error: ActivityError }
  | { type: 'NEXT' }
  | { type: 'RETRY' }
  | { type: 'EXIT' }
```

第一阶段可以省略暂停恢复和远端提交事件，但数据结构要保留可扩展空间。

### 活动上下文

```ts
interface LearningActivityContext {
  activityId: string
  blockType: string
  blockVersion: number
  questionIndex: number
  payload: unknown
  draftAnswer?: unknown
  result?: ActivityResult
  error?: ActivityError
}
```

### 允许操作

前端按钮根据当前状态推导，不在组件中自行猜测：

- `awaitingAnswer`：提交、提示、结束。
- `submitting`：禁用重复提交，可以保留结束入口。
- `reviewing`：下一题、重试、查看解释、结束。
- `error`：重试、结束。
- `completed` / `cancelled`：回到对话、开始新活动。

### 活动适配器

状态机管理通用生命周期，每个题型提供：

- 题目数据 Schema。
- 前端活动卡片。
- 草稿答案格式。
- 判分器或提交器。
- 反馈数据格式。

重组成句第一阶段使用确定性本地判分，支持标准答案和显式配置的可接受答案。不得临时调用模型改变正确答案。

### 普通追问与退出

状态机不能锁住主对话：

- 卡片内“提示、提交、下一题”等事件进入当前活动。
- 与题目相关的解释请求可以保持活动状态不变并追加说明。
- 用户在主输入框提出无关问题时，按普通对话处理；活动保持可返回状态。
- 用户点击结束或明确说“不练了”时发送 `EXIT`。

第一阶段不要求持久化活动快照。刷新页面后允许活动结束，但组件必须安全回到普通对话。

## 前后端与模型边界

### 前端

- 展示 Markdown、只读卡片和互动活动卡片。
- 运行 XState 活动实例并根据快照渲染允许操作。
- 管理拖拽词块、选项高亮等临时界面状态。
- 校验和规范化学习块，失败时显示 `fallbackMarkdown`。
- 发送结构化 UI 意图和活动事件。

### 后端与 Python 编排层

- 复用现有路由结果产生意图候选，避免再增加一次独立模型分类调用。
- 在流式完成事件中返回 `{ content, parts }`。
- 生成符合版本化 Schema 的学习块数据。
- 对未知类型、未知版本和缺失字段返回可诊断错误。
- 后续阶段负责持久化活动、开放题判分、学习记录和跨设备恢复。

### 模型

- 生成解释、例句、题目和开放题反馈。
- 返回受约束的意图候选和结构化内容。
- 不直接返回 Vue 组件名、路由地址或状态机状态。
- 不负责确定性题型的最终状态转换。

## 流式数据与历史

### 流式显示

1. `message.delta` 持续显示普通文本，使用户立即看到回答。
2. `message.completed` 返回经过后端校验的 `parts`。
3. 前端在完成事件后挂载学习块，避免不完整 JSON 导致卡片抖动。
4. 卡片主导的回答只保留简短过渡文本，避免内容重复。

### 历史消息

- 长期目标是历史消息同时保存 `content` 和 `parts`。
- 第一阶段如果旧历史没有 `parts`，继续使用 Markdown，不进行客户端猜测或重建。
- 未知版本使用块内 `fallbackMarkdown`。

## 第一阶段用户流程

### 普通问题

```text
用户：actually 是什么意思？
→ 普通 Markdown 回答
→ 动态操作：再举例 / 查看单词卡
```

### 明确练习

```text
用户点击“练一题”
→ 发送 uiIntent=start_practice
→ 返回 sentence_reorder 块
→ XState: preparing → awaitingAnswer
→ 用户排列词块并提交
→ 本地确定性判分
→ XState: submitting → reviewing
→ 显示正确性、答案和解释
→ 用户选择下一题或结束
```

### 活动中的无关问题

```text
当前存在活动
→ 用户在主输入框提出另一个问题
→ 普通 Markdown 回答
→ 活动不被错误提交或清空
→ 用户可以返回卡片继续或主动结束
```

## 错误处理与降级

- 模型未返回结构化块：只显示 Markdown。
- 块类型未知或版本不支持：显示 `fallbackMarkdown`，记录诊断日志。
- 块字段不完整：不渲染半成品卡片。
- 流式请求中断：保留已生成文本，提供重试。
- 练习生成失败：退出 `preparing`，显示错误并允许重试或回到对话。
- 判分器异常：不伪造结果，进入 `error`。
- 图片或音频不可用：隐藏相应交互，使用文本替代；后续阶段要求可访问的替代文本。
- 未知状态机事件：忽略并记录，不进行非法转换。

## 可访问性

- 快捷操作、动态操作和卡片操作全部使用按钮语义。
- 词块重组必须支持点击选择和键盘操作，不能只依赖拖拽。
- 提交结果通过可见文本和 `aria-live` 播报，不能只使用颜色。
- 禁用状态和当前活动状态提供可读说明。
- 活动结束后焦点返回触发入口或主输入框。
- 后续图片题必须提供有意义的替代文本；口语题必须提供权限拒绝和无法录音的文本路径。

## 测试策略

### 学习块契约

- 注册表能够解析所有已支持类型和版本。
- 未知类型、未知版本、缺失字段和非法字段正确降级。
- HTML 转义、链接和图片协议白名单继续生效。

### XState

- 每一个允许事件能够到达预期状态。
- 非法事件不会引发状态转换。
- 重复提交被阻止。
- 加载失败、判分失败、重试和退出路径可达。
- 完成与取消状态不会继续接受答案。

### 数据链路

- 显式 `uiIntent` 优先于模型意图。
- 流式完成事件能够携带 `content` 和 `parts`。
- 旧消息没有 `parts` 时保持 Markdown 可用。
- 无效块不影响当前对话文本。

### 交互与视觉

- 重组成句支持鼠标、触屏和键盘。
- 在 `390px`、`768px` 和桌面宽度验证卡片布局。
- 验证提交、反馈、下一题、退出和焦点恢复。
- 修复并覆盖现有移动端三列表格内联节点错位回归。
- 运行前端单元/契约测试和 `npm run build`。

## 分阶段范围

### Phase 1：最小互动闭环

- 安装 XState v5 与 `@xstate/vue`。
- 建立学习块注册表和版本化数据外壳。
- 接通真实对话的 `content + parts` 完成事件。
- 增加显式 UI 意图和回答后动态操作。
- 实现通用活动状态机。
- 只接入 `sentence_reorder`。
- 实现确定性本地判分、反馈、下一题和退出。
- 不持久化尝试和分数。

### Phase 2：互动题型扩展

- 选择、填空、配对和图片选择。
- 统一判分接口、错题事件和难度参数。
- 图片资产来源、版权、缓存和替代文本方案。
- 根据验证结果决定是否持久化活动与尝试。

### Phase 3：口语与自适应

- 麦克风权限、录音、语音识别和发音评分。
- 情景口语的扩展状态和失败恢复。
- 学习档案、薄弱点和跨场景复习。
- 在完成数据标注和评估体系后再引入更强的自适应策略。

## 文档影响

实施时需要同步更新：

- 学习助手输出与 `parts` 协议文档。
- SSE 完成事件和历史消息 Schema。
- 前端学习块注册表与版本策略。
- XState 活动事件和状态定义。
- 新题型接入指南与测试要求。

本设计文档只确认产品和技术方向，不修改现有 API 或运行行为。

## 完成标准

- 普通问题保持 Markdown，不自动进入互动活动。
- 显式“练一题”能够创建并完成一次重组成句活动。
- XState 覆盖准备、作答、提交、反馈、下一题、退出和错误路径。
- 学习块通过注册表解析，未知块安全降级。
- 模型输出失败不影响普通对话。
- 前后端协议、可访问性、自动化测试和视觉回归达到上述要求。

## 参考

- [XState 官方文档](https://stately.ai/docs/xstate)
- [`@xstate/vue` 官方文档](https://stately.ai/docs/xstate-vue)
- [XState 状态机文档](https://stately.ai/docs/machines)
- [OpenAI：A practical guide to building AI agents](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/)
- [Duolingo 学习流程介绍](https://blog.duolingo.com/duolingo-101-how-to-learn-a-language-on-duolingo/)
- [Duolingo Video Call 的可预测对话蓝图](https://blog.duolingo.com/ai-and-video-call/)
