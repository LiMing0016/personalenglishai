# 学习助手对话框图片与文件上传任务拆分

本文把 `/app/assistant` 的「对话框内图片上传 + `+` 菜单添加文件」能力拆成 9 个适合交给 Trae 分步实现的题目。

## 总体目标

在学习助手页面实现类似 ChatGPT 的输入体验：

- `+` 菜单可以从电脑选择图片和文件。
- 对话输入框本身支持粘贴截图或复制来的图片。
- 对话输入框本身支持拖拽图片进入。
- 发送后用户消息里直接显示图片缩略图或文件卡片。
- AI 能读取本次发送的图片或文件并回答。
- 同一台电脑、同一浏览器内，刷新页面或重新打开后仍能显示已发送消息里的图片/文件。
- 第一版不做账号级附件云存储，不改数据库结构。

## 全局约束

- 前端技术栈保持 Vue 3 + TypeScript，不新增状态库或组件库。
- 后端保持 Spring Boot + MyBatis，不新增文件存储服务，不改数据库结构。
- Python 侧继续复用现有 `/chat` 的 `files` 入参和 `build_input_items()` 适配。
- 带附件消息必须确保模型实际读取图片/文件内容，不能只验证文件上传成功。
- 附件持久化第一版只做浏览器本地持久化：`localStorage` 存 metadata，`IndexedDB` 存 Blob。
- 最多 5 个项目。
- 单个文件最大 10MB。
- 图片类型：`image/png`、`image/jpeg`、`image/webp`。
- 文件类型：`application/pdf`、`text/plain`、`.doc`、`.docx`。
- 粘贴和拖拽只接收图片；`+` 菜单可接收图片和文件。
- 纯文本聊天必须保持兼容。

---

## 题目 1：前端附件模型与校验收口

### 目标

先把待发送图片/文件的校验逻辑收口，避免后续在 `AssistantComposer.vue`、`assistantState.ts` 和 API 层重复判断。

### 建议修改范围

- `web/src/pages/app/assistantMock.ts`
- `web/src/pages/app/assistantState.ts`
- 可新增小工具文件，例如 `web/src/pages/app/assistantAttachmentRules.ts`

### Prompt

```text
请在 personalenglishai 项目中实现学习助手待发送图片/文件的前端校验收口。

背景：
- 学习助手页面位于 web/src/pages/app/AssistantPage.vue。
- 当前附件类型定义在 web/src/pages/app/assistantMock.ts。
- 当前 addAttachments 位于 web/src/pages/app/assistantState.ts。
- 第一版限制：最多 5 个项目，单个最大 10MB。
- 图片支持 image/png、image/jpeg、image/webp。
- 文件支持 PDF、txt、doc、docx。
- 粘贴/拖拽只接受图片；+ 菜单接受图片和文件。

要求：
1. 保持现有 AssistantAttachment 结构兼容，必要时只新增字段，不破坏现有消费方。
2. 将文件数量、大小、类型校验集中到一个可测试的 helper 中。
3. addAttachments 接收来源参数，例如 picker / paste / drop，用来区分是否允许非图片文件。
4. 校验失败时返回明确原因，页面可以用 toast 展示。
5. 不改 UI，不改后端，不改 API 请求。
6. 不新增依赖。

请按现有代码风格实现，并补充最小测试或至少保证 TypeScript build 能覆盖类型问题。
```

### 验收方案

- 选择或传入 6 个文件时，第 6 个被拒绝，并有明确错误原因。
- 单个文件超过 10MB 时被拒绝。
- `paste` / `drop` 来源传入 PDF 时被拒绝。
- `picker` 来源传入 PDF 时允许。
- 不支持类型如 `.exe`、`image/gif` 被拒绝。
- 现有纯文本发送逻辑不受影响。
- `npm run build` 通过。

---

## 题目 2：输入框内粘贴图片与拖拽图片体验

### 目标

让用户可以直接在对话输入框区域粘贴截图或拖拽图片，不需要打开文件选择器。

### 建议修改范围

- `web/src/components/assistant/AssistantComposer.vue`
- `web/src/pages/app/assistantState.ts`

### Prompt

```text
请为学习助手输入框实现对话框内图片粘贴和拖拽上传体验。

背景：
- 输入组件是 web/src/components/assistant/AssistantComposer.vue。
- 当前组件已有隐藏 file input、+ 菜单和 attachment-strip。
- 附件状态由父级 assistantState.ts 管理。

要求：
1. 在输入框区域支持 paste 事件读取 clipboard 图片。
2. 在输入框区域支持 dragover / dragleave / drop，drop 只接收图片。
3. 粘贴或拖拽成功后，图片直接进入待发送列表，显示缩略图。
4. 拖拽悬停时给输入区一个清晰但克制的 hover 状态。
5. 不要让拖拽图片触发浏览器直接打开图片。
6. 粘贴普通文本仍保持 textarea 原有输入体验。
7. 校验失败时通过现有 toast 或父级错误回调提示。
8. 不支持粘贴/拖拽 PDF、doc、docx。
9. 不新增依赖。

请保持现有视觉风格，不要重做整个 Composer。
```

### 验收方案

- 在输入框中粘贴截图，图片缩略图出现在输入框区域。
- 复制一张网页图片后粘贴，图片缩略图出现在输入框区域。
- 拖拽 PNG/JPG/WebP 到输入区，图片缩略图出现。
- 拖拽 PDF 到输入区，被拒绝并提示。
- 粘贴普通文字时，文字正常进入 textarea。
- 拖拽图片不会导致浏览器跳转到图片地址。
- 图片可以删除。
- `npm run build` 通过。

---

## 题目 3：`+` 菜单添加照片和文件

### 目标

保留 `+` 菜单作为从电脑选择本地图片和文件的入口，菜单文案和预览体验对齐产品目标。

### 建议修改范围

- `web/src/components/assistant/AssistantComposer.vue`
- `web/src/pages/app/assistantState.ts`

### Prompt

```text
请升级学习助手输入框的 + 菜单，使其支持从电脑添加照片和文件。

背景：
- AssistantComposer.vue 当前 + 菜单已有“上传照片和文件”入口。
- 用户希望 + 菜单负责从电脑选择图片、PDF、txt、doc/docx。
- 对话输入框本身负责粘贴和拖拽图片。

要求：
1. 菜单项文案调整为“添加照片和文件”。
2. file input 设置合理 accept，支持图片、PDF、txt、doc、docx。
3. 通过 + 菜单选择的图片显示缩略图。
4. 通过 + 菜单选择的非图片文件显示文件卡片，展示文件名和大小。
5. 每个预览项都可以删除。
6. 达到数量、大小、类型限制时阻止加入，并展示明确提示。
7. 不改变考试模式菜单项原有行为。
8. 不新增依赖。

请保持现有布局，只做必要的交互和样式调整。
```

### 验收方案

- 点击 `+` 后可以看到 `添加照片和文件`。
- 选择本地图片后出现缩略图。
- 选择 PDF/txt/doc/docx 后出现文件卡片。
- 不支持类型被拒绝。
- 超过 5 个项目被拒绝。
- 单文件超过 10MB 被拒绝。
- 删除按钮可以移除单个项目。
- 考试模式开关仍能正常使用。
- `npm run build` 通过。

---

## 题目 4：前端发送 FormData 并保持纯文本兼容

### 目标

有图片/文件时用 `multipart/form-data` 发送；没有图片/文件时保持现有 JSON 请求，降低兼容风险。

### 建议修改范围

- `web/src/api/assistant.ts`
- `web/src/pages/app/assistantState.ts`

### Prompt

```text
请让学习助手前端在发送图片/文件时使用 multipart/form-data，同时保持纯文本聊天的 JSON 请求兼容。

背景：
- API 文件是 web/src/api/assistant.ts。
- 当前 assistantChat(payload) 遇到 attachments 会直接 throw。
- 后端目标接口仍是 /assistant/conversations/{conversationId}/messages。

要求：
1. 移除“当前版本暂不支持通过后端保存附件对话”的前端阻断。
2. payload.attachments 为空时，继续使用现有 JSON 请求结构。
3. payload.attachments 非空时，使用 FormData：
   - message
   - studyStage
   - assistantMode
   - files，多文件重复 append 到 files 字段。
4. 使用 attachment.file 作为上传内容，保留原文件名。
5. 返回仍解析为 AssistantConversationDto，并兼容 assistantChat() 返回 reply + conversation。
6. 发送成功后清空待发送图片/文件。
7. 发送失败时保留图片/文件用于重试。
8. 不新增依赖。

请补充必要的类型处理，确保 npm run build 通过。
```

### 验收方案

- 纯文本发送仍然走 JSON，聊天正常。
- 带一张图片发送时，请求为 multipart。
- 带多个文件发送时，FormData 包含多个 `files` 字段。
- `studyStage` 和 `assistantMode` 仍会传给后端。
- 成功后输入框和预览区清空。
- 失败后预览区保留，重试可再次发送。
- `npm run build` 通过。

---

## 题目 5：Java 后端接收 multipart 并转发附件

### 目标

后端在不破坏现有 JSON 消息接口的前提下，新增同一路径 multipart 接收能力，并把文件传给 Python orchestrator。

### 建议修改范围

- `backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- `backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java`
- 可新增内部附件 DTO/record
- 对应测试文件

### Prompt

```text
请为 Java 后端学习助手消息接口增加 multipart/form-data 附件接收和转发能力。

背景：
- Controller 是 backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java。
- 现有 JSON 接口：POST /api/assistant/conversations/{conversationUid}/messages。
- Service 是 AssistantConversationService。
- Python 转发客户端是 PythonAssistantClient。
- Python /chat 已经支持 multipart 的 files 字段。

要求：
1. 保留现有 JSON @RequestBody 接口，不破坏纯文本聊天。
2. 新增同路径 consumes = multipart/form-data 的 Controller 方法。
3. multipart 字段：
   - message
   - studyStage
   - assistantMode
   - files
4. 服务端再次校验：
   - 最多 5 个文件
   - 单个最大 10MB
   - 允许 png、jpeg、webp、pdf、txt、doc、docx
5. Service 仍只把用户文本消息和助手回复保存到数据库；第一版不保存文件。
6. PythonAssistantClient 将文件作为 multipart files 转发到 /chat。
7. Authorization header 继续透传给 Python。
8. 对上游失败仍使用现有 ASSISTANT_UPSTREAM_UNAVAILABLE。
9. 不改数据库结构，不新增依赖。

请补充 Controller 或 Client 层测试，覆盖 multipart 字段和文件转发。
```

### 验收方案

- JSON 纯文本接口仍可用。
- multipart 请求能被 Controller 接收。
- Service 能收到 message、studyStage、assistantMode、files。
- 超过 5 个文件被拒绝。
- 单文件超过 10MB 被拒绝。
- 不支持类型被拒绝。
- PythonAssistantClient 发给 Python 的请求包含 `files`。
- 数据库消息仍只保存文本，不需要保存文件路径。
- 相关后端测试通过，条件允许时 `mvn test` 通过。

---

## 题目 6：Python orchestrator 附件链路回归

### 目标

确认 Python 侧能接收 Java 转发来的文件，并生成正确的 OpenAI Responses input items。

### 建议修改范围

- `python/ai_orchestrator/app.py`
- `python/ai_orchestrator/adapters/openai_input_items.py`
- `python/ai_orchestrator/tests/test_openai_input_items_adapter.py`
- `python/ai_orchestrator/tests/test_assistant_service.py`

### Prompt

```text
请为 Python ai_orchestrator 的学习助手附件链路补充回归验证。

背景：
- FastAPI 入口是 python/ai_orchestrator/app.py。
- /chat 已经接收 files: list[UploadFile]。
- openai input item 适配器是 adapters/openai_input_items.py。
- AssistantAgentService.chat 在有 attachments 时会禁用 session，并调用 build_input_items。

要求：
1. 确认图片会生成 input_image，使用 data URL。
2. 确认 PDF/txt/doc/docx 会生成 input_file。
3. 确认有附件时 use_session=False。
4. 确认 study_stage / assistant_mode 上下文仍注入到 input_text。
5. 如现有逻辑已经满足，不做不必要重构，只补或调整测试。
6. 不新增 agent，不改 prompt，不引入新框架。

请运行相关 unittest，至少覆盖 test_openai_input_items_adapter.py 和 test_assistant_service.py。
```

### 验收方案

- 图片附件 input item 类型为 `input_image`。
- 文件附件 input item 类型为 `input_file`。
- 有附件时不使用 SQLite session。
- 学段和考试模式上下文仍在第一条 `input_text` 中。
- Python 相关测试通过。

---

## 题目 7：端到端验收与文档更新

### 目标

把用户体验、接口限制、非持久化边界写清楚，并完成端到端验收。

### 建议修改范围

- `docs/agent/learning-assistant-architecture.md`
- 可选：`web/README.md` 或项目主 README 中的学习助手说明
- 不应改业务代码，除非验收发现缺陷

### Prompt

```text
请为学习助手图片/文件上传第一版补充文档，并执行端到端验收。

背景：
- 学习助手架构文档在 docs/agent/learning-assistant-architecture.md。
- 第一版上传能力不做附件持久化，不改数据库结构。

要求：
1. 文档说明两个入口：
   - + 菜单添加本地照片和文件
   - 输入框粘贴/拖拽图片
2. 文档说明第一版限制：
   - 最多 5 个项目
   - 单个最大 10MB
   - 支持类型
   - 不做刷新后恢复图片/文件
   - 不做分享页附件展示
3. 文档说明调用链路：
   web AssistantComposer -> assistant.ts FormData -> Java AssistantController -> PythonAssistantClient -> Python /chat -> OpenAI input items。
4. 执行或记录以下验证：
   - npm run build
   - 后端相关测试
   - Python 相关测试
   - 浏览器手工上传图片问答
   - 浏览器手工上传 PDF/txt 问答
5. 如果某项验证无法运行，明确记录原因。

请保持文档简洁，重点写边界和验收结果。
```

### 验收方案

- 文档清楚说明用户体验和技术边界。
- 文档没有承诺附件持久化。
- 端到端可以上传图片并让 AI 基于图片回答。
- 端到端可以上传 PDF/txt 并让 AI 基于文件回答。
- 纯文本聊天、历史会话、文件夹、分享、置顶、删除能力不受影响。
- 所有实际运行的验证结果被记录。

---

## 题目 8：修复带附件消息在 Agent handoff 后丢失图片内容

### 目标

解决“前端和后端都已经上传图片，但模型实际回答时没有读取图片内容”的问题。核心验收不是请求里有没有文件，而是最终 Agent 是否真的基于图片/文件回答。

### 建议修改范围

- `python/ai_orchestrator/assistant_service.py`
- `python/ai_orchestrator/agents/`
- `python/ai_orchestrator/prompts/agent_instructions/`
- `python/ai_orchestrator/prompts/agents.py`
- `python/ai_orchestrator/tests/test_assistant_service.py`
- `docs/agent/learning-assistant-architecture.md`

### Prompt

```text
请修复学习助手带图片/文件消息上传成功但模型没有实际读取附件内容的问题。

背景：
- 前端已经能把粘贴/拖拽/+ 菜单选择的图片作为 multipart/form-data 发送。
- Java 后端已经能接收 files 并转发给 Python /chat。
- Python /chat 日志里可以看到 attachment_count=1 和 image/png。
- 但实际回答中，模型只处理了用户输入的短文本，例如“翻译成中文。”，没有读取图片里的英文内容。
- 原因可能是图片进入 Router Agent 后，Router handoff 到 Translation Agent / 其他专业 Agent 时，多模态附件内容没有稳定传递给后续 Agent。

要求：
1. 先用日志或测试确认附件确实到达 Python：attachment_count、attachment_types、input item 类型。
2. 对带附件消息，不要再依赖 Router -> specialist handoff 传递图片。
3. 新增或复用一个专门处理图片/文件的多模态 Agent，例如 Attachment Agent。
4. 有 attachments 时，AssistantAgentService.chat 直接调用该附件 Agent。
5. 无 attachments 时，继续走现有 Router Agent 和 SQLiteSession，不破坏纯文本多 Agent 编排。
6. 带附件时继续 use_session=False，避免把大文件或 data URL 写入 session。
7. 附件 Agent prompt 必须明确：
   - 图片/截图是任务输入的一部分。
   - 先读取图片里的文字、题目或页面内容，再按用户要求翻译、评分、润色或分析。
   - 看不清时明确说明，而不是忽略附件。
8. 补充回归测试：
   - 有附件时使用 Attachment Agent，而不是 Router Agent。
   - 有附件时仍生成 input_image / input_file。
   - 有附件时 use_session=False。
   - 无附件时仍使用 Router Agent 和 session。
9. 更新架构文档，说明带附件消息绕过 Router handoff，直接走附件多模态 Agent。
10. 不改前端 UI，不改 Java multipart 协议，不改数据库结构。

请按 TDD 思路先写失败测试，再实现最小修复，并运行 Python assistant 相关 unittest。
```

### 验收方案

- Python 日志显示附件到达：`attachment_count=1`，`attachment_types=('image/png',)` 或对应文件类型。
- 带图片消息发送后，`run_agent_session` 使用 `Attachment Agent`，不是 `Router Agent`。
- OpenAI input items 中包含 `input_image`。
- 用户发送“翻译成中文。”并附带英文截图时，AI 回复基于截图里的英文内容，而不是只回复“请提供要翻译的文本”。
- 用户发送 PDF/txt 文件并提问时，AI 回复基于文件内容。
- 纯文本请求仍走 Router Agent，历史会话 session 仍可用。
- 相关测试通过：
  - `python.ai_orchestrator.tests.test_assistant_service`
  - `python.ai_orchestrator.tests.test_openai_input_items_adapter`
  - `python.ai_orchestrator.tests.test_input_items`
- 如果本地 Python 服务已启动，修改后必须重启服务再做浏览器验收。

---

## 题目 9：同浏览器刷新和重开后保留已发送附件

### 目标

解决“消息发送后图片能显示，但刷新页面或关闭浏览器重新打开后，历史对话里的图片/文件消失”的问题。第一版只保证同一台电脑、同一浏览器内可恢复，不做账号级云端附件存储。

### 建议修改范围

- `web/src/pages/app/assistantMock.ts`
- `web/src/pages/app/assistantState.ts`
- `web/src/pages/app/assistantConversationMerge.ts`
- 可新增 `web/src/pages/app/assistantAttachmentStore.ts`
- 可新增相关 node test 文件
- `docs/agent/learning-assistant-architecture.md`

### Prompt

```text
请为学习助手已发送附件增加浏览器本地持久化，使刷新页面和重新打开同一浏览器后，历史对话里的图片/文件仍能显示。

背景：
- 后端第一版不保存附件文件，不改数据库结构。
- 当前前端发送消息时，用户消息里有 attachments，可显示图片缩略图。
- 远端会话刷新后，前端已经能临时合并当前页面内的 attachments。
- 但页面刷新或重新打开后，File/Blob 对象丢失，图片预览不再存在。

要求：
1. 不把图片 base64 或 Blob 放进 localStorage。
2. localStorage 只保存附件 metadata，例如：
   - id
   - name
   - size
   - type
   - kind
3. 使用 IndexedDB 保存附件 Blob，key 使用 attachment.id。
4. 发送成功或创建用户消息时，将附件 Blob 写入 IndexedDB。
5. 恢复本地会话时，根据 metadata 从 IndexedDB 读取 Blob，并重建 File 对象。
6. 远端历史刷新时，如果后端消息没有附件，前端要用本地 metadata + IndexedDB Blob 补回同一条用户消息的附件。
7. 删除对话时，清理该对话消息关联的本地附件 Blob。
8. 刷新页面、关闭浏览器再打开同一页面，图片/文件仍显示在历史用户消息中。
9. 换电脑、换浏览器、清除站点数据后不保证恢复；分享页不展示附件。
10. 不改 Java 后端，不改 Python，不改数据库结构，不新增依赖。

请按 TDD 思路实现，至少补充纯函数或 IndexedDB wrapper 的单元测试，并运行 npm run build。
```

### 验收方案

- 发送图片消息后，用户消息内显示图片缩略图。
- 生成完成后图片缩略图仍保留。
- 刷新页面后，同一条历史用户消息仍显示图片缩略图。
- 关闭浏览器或标签页后重新打开，同一浏览器内仍显示图片缩略图。
- 选择历史对话并从后端重新加载详情后，前端仍能补回本地附件。
- 删除对话后，对应 IndexedDB 附件记录被清理。
- localStorage 中只包含附件 metadata，不包含 base64 或 Blob 内容。
- 清除浏览器站点数据后附件可以消失，这是第一版边界。
- `npm run build` 通过。

---

## 推荐执行顺序

1. 题目 1：前端附件模型与校验收口
2. 题目 2：输入框内粘贴图片与拖拽图片体验
3. 题目 3：`+` 菜单添加照片和文件
4. 题目 4：前端发送 FormData 并保持纯文本兼容
5. 题目 5：Java 后端接收 multipart 并转发附件
6. 题目 6：Python orchestrator 附件链路回归
7. 题目 7：端到端验收与文档更新
8. 题目 8：修复带附件消息在 Agent handoff 后丢失图片内容
9. 题目 9：同浏览器刷新和重开后保留已发送附件

## 分支建议

建议 Trae 新建独立分支：

```text
codex/assistant-inline-upload
```

如果需要进一步拆 PR，建议：

- PR 1：前端输入体验和 FormData
- PR 2：Java multipart 转发
- PR 3：Python 附件 Agent、回归和文档
- PR 4：浏览器本地附件持久化
