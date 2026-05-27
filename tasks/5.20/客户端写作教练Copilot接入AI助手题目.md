# 客户端写作教练 Copilot 接入 AI 助手 Trae 题目

## 背景

当前写作页右侧已经有“写作教练”面板，但它仍有两个问题：

1. 交互形态更像一组固定快捷按钮，不像 ChatGPT 输入框里选择 Canva 这类工具的体验。
2. 写作教练的快捷动作之前主要依赖前端 mock 或通用 `/api/ai/command`，没有真正复用 AI 助手 conversation 和 Python OpenAI Agents SDK 编排。

本轮目标是把写作教练做成写作窗口里的 Copilot：

```text
写作页右侧 Copilot 输入框
  -> 点击 +
  -> 选择 写作教练 / 审题 / 搭提纲 / 写开头 / 润色表达
  -> 输入框显示已选 Agent chip
  -> 发送到 AI 助手 conversation
  -> intent=first_draft_coach
  -> Python ai_orchestrator / OpenAI Agents SDK
  -> 返回写作教练回复
```

它的产品定位是：**写作教练是 AI 助手在写作窗口里的上下文化入口，不是独立 AI 系统。**

参考文件：

- `web/src/components/writing/panels/WritingCoachPanel.vue`
- `web/src/components/writing/EditorShell.vue`
- `web/src/components/writing/RightPanel.vue`
- `web/src/api/assistant.ts`
- `web/src/api/assistantStream.ts`
- `web/src/types/assistantRequest.ts`
- `python/ai_orchestrator/schemas/assistant_request.py`
- `python/ai_orchestrator/adapters/openai_input_items.py`
- `backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- `web/AGENTS.md`
- `python/AGENTS.md`

核心原则：

- 不新建一套写作教练聊天系统。
- 前端表现为写作页 Copilot，后端复用 AI 助手 conversation。
- 不再让快捷动作直接插入本地 mock 回复。
- 写作教练请求必须携带作文上下文：题目、选区、作文全文、写作模式、学段、近期对话。
- `first_draft_coach` 必须贯通前端类型、后端请求、Python schema 和路由。
- 停止生成要能中断当前 stream。
- 不破坏写作页主链路、选区替换、评分、语法、润色等已有能力。

---

## 题目 1：梳理现有写作教练与 AI 助手链路

难度：中等

### A 小题：梳理前端写作教练现状

请阅读 `WritingCoachPanel.vue`、`RightPanel.vue`、`EditorShell.vue`，梳理当前写作教练的状态流和事件流。

至少说明：

1. 输入框文本来源。
2. 对话消息如何保存和恢复。
3. 快捷按钮现在如何触发。
4. 选区文本如何从正文编辑器传入右侧面板。
5. `lastChatResult` 如何进入写作教练消息列表。
6. Safe Apply 如何替换选区、追加正文或替换全文。

### B 小题：梳理 AI 助手后端链路

请阅读 `assistant.ts`、`AssistantController.java`、`AssistantConversationService.java` 和 Python `ai_orchestrator` 相关 schema。

至少说明：

1. 前端如何创建 AI 助手 conversation。
2. `/api/assistant/conversations/{id}/messages/run/stream` 的请求结构。
3. 后端如何持久化 user message 和 assistant message。
4. Python orchestrator 如何识别 intent。
5. 现有 `AssistantIntent` 是否包含 `first_draft_coach`。
6. 当前写作教练为何还不算真正接入 AI 助手 Agent 编排。

### 给 Trae 的 Prompt

请先梳理写作教练面板和 AI 助手 conversation 的现有链路，明确哪些代码仍在走本地 mock 或 `/api/ai/command`，哪些代码已经可以复用 `/api/assistant/conversations/{id}/messages/run/stream`。本题只做代码阅读和设计说明，不做功能改动。

### 验收标准

- 能清楚指出写作教练当前 mock 的位置。
- 能清楚指出当前真实 AI 助手 stream API 的入口。
- 能说明写作教练为什么应该复用 assistant conversation。
- 能列出后续改造需要影响的前端、后端、Python 文件。
- 不修改业务代码。

---

## 题目 2：把固定快捷按钮改成 `+` Agent 菜单

难度：中等偏难

### A 小题：设计写作教练能力菜单数据结构

在 `WritingCoachPanel.vue` 中建立写作教练能力配置。

至少包含：

1. 写作教练。
2. 审题。
3. 搭提纲。
4. 写开头。
5. 写下一段。
6. 检查偏题。
7. 润色表达。
8. 生成终稿。

每个能力至少包含：

- `key`
- `label`
- `icon`
- `hint`
- `stage`
- `prompt`

### B 小题：实现 `+` 菜单交互

把原来顶部横向快捷按钮收进输入框附近的 `+` 菜单。

要求：

1. 输入框左下角有 `+` 按钮。
2. 点击后弹出能力菜单。
3. 菜单项包含图标、标题、说明。
4. 点击菜单项后关闭菜单。
5. 点击菜单项只填充 prompt 或聚焦输入框，不插入 mock assistant 回复。
6. 菜单不能遮挡发送按钮。

### 给 Trae 的 Prompt

请把写作教练顶部固定快捷按钮改造成类似 ChatGPT 输入框的 `+` Agent 菜单。用户点击 `+` 后可以选择“写作教练、审题、搭提纲、写开头、写下一段、检查偏题、润色表达、生成终稿”。选择后在输入框中填入对应 prompt，但不要再生成本地 mock 回复。

### 验收标准

- 写作教练面板不再显示一排固定快捷按钮。
- 输入框区域可以看到 `+` 按钮。
- 点击 `+` 后出现能力菜单。
- 选择“审题”等能力后输入框出现对应 prompt。
- 选择能力不会直接产生 assistant 假回复。
- `npm run build` 通过。

---

## 题目 3：实现已选 Agent Chip 和输入框视觉

难度：中等

### A 小题：显示已选 Agent Chip

在输入框底部工具栏展示当前选中的 Agent 能力 chip。

要求：

1. 默认显示“写作教练”。
2. 选择“审题”后 chip 显示“审题”。
3. chip 有图标或圆点标识。
4. 点击 chip 可以重新打开 `+` 菜单。
5. chip 不挤压发送按钮。

### B 小题：优化输入框底部工具栏

参考 ChatGPT 的输入框工具区，整理底部布局。

要求：

1. 左侧是 `+`、Agent chip、是否引用作文。
2. 右侧是 provider 或 Thinking 状态，以及发送/停止按钮。
3. 生成中显示 `Thinking`。
4. 输入区、选区 chip、底部工具栏不能重叠。
5. 窄屏下不出现整体横向滚动。

### 给 Trae 的 Prompt

请为写作教练输入框实现类似 ChatGPT Canva 工具的已选 Agent chip。默认 chip 是“写作教练”，用户从 `+` 菜单选择能力后更新 chip。输入框底部工具栏左侧放 `+`、chip、引用作文开关，右侧放 provider/Thinking 和发送按钮。

### 验收标准

- 默认可见“写作教练”chip。
- 选择不同能力后 chip 文案同步变化。
- 生成中右侧显示 `Thinking` 或等价状态。
- 发送按钮始终可见且不被菜单遮挡。
- 选区 chip、输入框文字、底部工具栏没有重叠。

---

## 题目 4：接入 AI 助手 Conversation（完成）

难度：困难

### A 小题：创建或复用 assistant conversation

在写作教练发送前，确保当前写作页绑定真实 AI 助手 conversation。

要求：

1. 如果当前 conversation id 已经是后端 conversation id，直接复用。
2. 如果当前 id 只是本地临时 id，则调用 `assistantApi.createConversation` 创建远程会话。
3. 创建后把真实 conversation id 写回写作草稿 store。
4. 新会话标题应体现写作教练，例如“写作教练：考试写作”。
5. 不影响原本 AI 助手页面的 conversation 逻辑。

### B 小题：发送到 assistant stream API

把写作教练发送链路从 `/api/ai/command` 切换到 AI 助手 stream。

要求：

1. 调用 `assistantChatStream`。
2. 请求 `intent` 使用 `first_draft_coach`。
3. `assistantMode` 按写作模式映射：考试写作为 `exam`，自由写作为 `default`。
4. 有选区时传 `selection`，source 为 `writing_editor`。
5. 没有选区时 scope 为 `message_only`。
6. 有选区时 scope 为 `selection_and_message`。

### 给 Trae 的 Prompt

请把写作教练发送链路接入 AI 助手 conversation。发送前确保有真实后端 conversation id；如果没有就创建一个“写作教练”会话。然后通过 `assistantChatStream` 调 `/api/assistant/conversations/{id}/messages/run/stream`，intent 固定为 `first_draft_coach`，并正确传递选区和写作模式。

### 验收标准

- 写作教练发送不再调用 `/api/ai/command`。
- 第一次发送前会创建后端 assistant conversation。
- 后续发送复用同一个 conversation id。
- 后端 AI 助手会话中可以看到写作教练消息。
- 选中文本后发送，请求中包含 selection。
- 停止生成不会导致前端状态卡死。

---

## 题目 5：构造写作上下文 Prompt

难度：困难

### A 小题：聚合写作上下文

为写作教练构造发送到 Agent 的上下文文本。

至少包含：

1. 入口：`writing_copilot`。
2. 当前能力：例如 `审题`、`搭提纲`、`润色表达`。
3. 写作模式：自由写作或考试写作。
4. 学段/目标。
5. 作文题目。
6. 用户选区。
7. 是否引用作文全文。
8. 写作教练面板近期对话。
9. 用户本轮问题。

### B 小题：控制上下文长度和安全边界

构造上下文时需要避免过长和混淆系统指令。

要求：

1. 作文全文需要截断，避免超长。
2. 近期对话每条需要截断。
3. 用户选区和作文题目要标明是用户数据，不是系统指令。
4. 不把前端内部状态名直接暴露给用户。
5. 上下文结构清晰，便于 Python route decision 和目标 Agent 理解。

### 给 Trae 的 Prompt（完成）

请为写作教练请求构造结构化上下文 prompt。它需要包含写作入口、当前能力、写作模式、学段、作文题目、选区、可选作文全文、近期对话和用户本轮问题。注意控制长度，并明确用户文本只是数据，不是系统指令。

### 验收标准

- 请求中能看到结构化的写作上下文。
- 未勾选“引用作文”时不发送作文全文。
- 勾选“引用作文”时发送截断后的作文全文。
- 有选区时包含选区文本和 range。
- 近期对话不会无限增长。
- 用户本轮问题保留在上下文末尾。

---

## 题目 6：扩展 `first_draft_coach` 类型与 Python schema（完成）

难度：中等偏难

### A 小题：扩展前端 AssistantIntent 类型

在前端 `AssistantIntent` 中增加 `first_draft_coach`。

要求：

1. `web/src/types/assistantRequest.ts` 支持该 intent。
2. `assistant.ts` 调用时可以传该 intent。
3. TypeScript 不需要使用 `as any` 绕过类型。
4. `AssistantRunMetadata.intent` 也能兼容该值。

### B 小题：扩展 Python assistant request schema

在 Python orchestrator 的 `AssistantIntent` 中增加 `first_draft_coach`。

要求：

1. `python/ai_orchestrator/schemas/assistant_request.py` 接受该 intent。
2. `openai_input_items.py` 能给该 intent 一个中文说明。
3. 不破坏已有 `free_chat`、`polish`、`grade_writing` 等 intent。
4. 相关测试通过。

### 给 Trae 的 Prompt

请贯通 `first_draft_coach` intent 类型。前端 `AssistantIntent`、Python `AssistantIntent` schema 和输入构造说明都要支持它，不能用 `any` 绕过类型。完成后运行前端 build 和 Python 相关 assistant request 测试。

### 验收标准

- 前端可以传 `intent: 'first_draft_coach'`，无类型报错。
- Python FastAPI 请求模型能接受 `first_draft_coach`。
- Agent 输入文本中能识别这是“写作初稿教练”类请求。
- 相关 Python assistant request 测试通过。
- `npm run build` 通过。

---

## 题目 7：实现 stream 停止生成

难度：中等偏难（完成）

### A 小题：为 assistant stream 增加 AbortSignal

扩展 `assistantStream.ts` 和 `assistantChatStream`，允许调用方传入 `AbortSignal`。

要求：

1. `streamAssistantEvents` 支持 `options.signal`。
2. fetch 请求携带 signal。
3. abort 后不要 fallback 到非 stream 请求。
4. abort 错误要转换成前端可识别的 canceled 状态。

### B 小题：接入写作教练停止按钮

让写作教练生成中点击停止按钮时中断当前请求。

要求：

1. 生成中按钮显示停止状态。
2. 点击后调用 `AbortController.abort()`。
3. 前端 toast 提示“已停止生成”。
4. `aiGenerating` 最终恢复为 false。
5. 下一次发送仍然可用。

### 给 Trae 的 Prompt

请为写作教练接入 AI 助手 stream 的停止生成能力。`assistantChatStream` 和底层 `streamAssistantEvents` 要支持 `AbortSignal`，写作教练点击停止后能真正中断 fetch，并把状态恢复到可再次发送。

### 验收标准

- 生成中点击停止按钮可以终止请求。
- 停止后不会再追加新的回复。
- 页面不会卡在 generating 状态。
- 停止后可以再次发送。
- abort 不会触发一次 fallback 非流式请求。

---

## 题目 8：回归验证与验收路径

难度：中等

### A 小题：补充自动化或半自动化验证

至少运行并记录：

1. `npm run build`。
2. Python assistant request 相关测试。
3. `git diff --check`。

建议 Python 测试：

```powershell
.\python\ai_orchestrator\.venv\Scripts\python.exe -m pytest `
  python\ai_orchestrator\tests\test_assistant_request_validator.py `
  python\ai_orchestrator\tests\test_assistant_request_input_items.py `
  python\ai_orchestrator\tests\test_assistant_run_endpoint.py
```

### B 小题：补充手动验收清单

请整理写作教练 Copilot 的手动验收步骤。

至少覆盖：

1. 打开写作页。
2. 打开右侧写作教练。
3. 点击 `+` 展开能力菜单。
4. 选择“审题”，确认 chip 和 prompt 变化。
5. 输入问题并发送。
6. 确认请求走 assistant conversation。
7. 选择选区后发送，确认 selection 被带上。
8. 勾选“引用作文”后发送，确认作文全文上下文被带上。
9. 点击停止生成，确认状态恢复。
10. 窄屏下检查菜单和输入区不重叠。

### 给 Trae 的 Prompt

请为写作教练 Copilot 接入 AI 助手的改造补充验证。自动验证至少包括前端 build、Python assistant request 测试和 diff check；手动验收要覆盖 `+` 菜单、Agent chip、发送到 assistant conversation、选区、引用作文和停止生成。

### 验收标准

- 自动验证命令有明确结果。
- 手动验收清单可以被产品或测试直接执行。
- 验收标准能证明写作教练不再依赖本地 mock。
- 验收标准能证明写作教练复用了 AI 助手 conversation。
- 发现的限制或未覆盖项有明确记录。

