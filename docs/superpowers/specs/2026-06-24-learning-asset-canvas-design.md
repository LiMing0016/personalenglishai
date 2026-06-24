---
title: 学习资产画布设计
status: draft
owner: product
last_updated: 2026-06-24
review_cycle: on-change
related_code:
  - web/src/pages/app/AssistantPage.vue
  - web/src/components/assistant/AssistantChatView.vue
  - web/src/views/VocabularyView.vue
  - web/src/api/dictionary.ts
  - backend/src/main/java/com/personalenglishai/backend/controller/DictionaryController.java
related_docs:
  - docs/architecture/assistant-conversation-management.md
  - docs/ai/assistant-output-format.md
  - docs/ios-integration/ai-assistant-api-contract.md
---

# 学习资产画布设计

## 当前结论

学习助手新增“学习资产画布”：用户在 AI 回复中选中有价值的内容后，可以把它沉淀为可编辑、可保存、可复习的学习资产。底层按通用画布设计，首版只开放 `vocabulary` 类型，对用户呈现为“新建单词卡”。

首版采用固定右侧画布、Markdown 正文编辑和 AI 默认模板整理。草稿自动保留在当前对话中，用户确认保存后进入全局学习资产库，并在 `/app/vocabulary` 中展示为单词笔记。

## 背景

当前学习助手已经支持对话、Markdown 回复、结构化学习组件和对话管理。用户希望在阅读 AI 回复时，能选中里面的单词或短语，并实时整理成自己的单词笔记。

这个需求的核心不是“收藏一个单词”，而是把对话中的学习价值沉淀为长期资产。后续还可能扩展到句子整理、语法树、写作表达积累和模板仓库。因此底层不应写死为 `WordCard`，而应设计为通用学习资产画布。

## 范围

本文覆盖：

- 学习资产画布的首版产品形态。
- 学习助手中选词新建单词卡的用户流程。
- 画布草稿、保存和全局学习资产的状态边界。
- 首版数据模型、前后端职责和 API 方向。
- AI 整理、优化格式和预览确认规则。
- 后续句子、语法、表达和模板仓库扩展方式。

本文不覆盖：

- 完整模板仓库。
- 语法树编辑器。
- 句子整理工作台。
- 富文本所见即所得编辑器。
- 背词算法、复习计划和记忆曲线。
- iOS 端实现。

## 产品原则

| 原则 | 说明 | 影响 |
| --- | --- | --- |
| 入口具体，底层通用 | 用户看到“新建单词卡”，底层使用学习资产画布 | 降低首版认知成本，同时减少后续返工 |
| Markdown 优先 | 正文使用 Markdown 编辑，不做一堆字段卡片 | 保持编辑自由，适配 AI 输出和后续模板 |
| AI 辅助但不覆盖用户 | AI 优化格式先生成预览，用户确认后才替换正文 | 保护用户已写内容，避免误覆盖 |
| 保存前是草稿，保存后是资产 | 当前对话草稿自动保留，点击保存后进入全局学习资产库 | 区分临时编辑和长期复习资产 |
| 首版只做 vocabulary | 先跑通选词、整理、编辑、保存闭环 | 不把模板仓库、语法树和富文本编辑器塞进首版 |

## 用户流程

1. 用户在学习助手中阅读 AI 回复。
2. 用户选中 AI 回复里的英文单词或短语。
3. 选区旁出现小工具条，首版只提供 `新建单词卡`。
4. 用户点击后，右侧固定打开学习资产画布。
5. 画布标题使用选中的词或短语，类型为 `vocabulary`。
6. 系统按默认单词卡模板生成 Markdown 草稿。
7. 用户可以直接编辑 Markdown，也可以点击 `AI 整理` 或 `优化格式`。
8. `优化格式` 先生成候选预览，用户确认后才替换正文。
9. 草稿自动绑定当前对话，刷新或切回对话时仍可恢复。
10. 用户点击 `保存为学习资产` 后，资产进入全局学习资产库，并可在 `/app/vocabulary` 中查看。

## 页面结构

### 学习助手对话区

助手回复仍按现有 Markdown 渲染。首版只在助手消息内容区域支持选区动作，避免影响用户消息、侧栏和输入框。

选区工具条规则：

- 只在选中非空文本时出现。
- 首版只对助手回复内容生效。
- 工具条显示在选区附近，按钮文案为 `新建单词卡`。
- 用户点击页面空白处、滚动或清空选区时隐藏。
- 如果选区跨多个复杂节点，首版只使用 `Selection.toString()` 作为选中文本。

### 右侧学习资产画布

画布固定在助手页面右侧。桌面端左侧继续展示对话，右侧持续编辑当前资产草稿。移动端后续可退化为抽屉或全屏编辑，不进入首版重点。

画布顶部只保留：

- 标题：选中的单词或短语。
- 类型标识：首版为 `单词卡` 或 `vocabulary`。
- 保存按钮：`保存为学习资产`。
- 关闭按钮。

画布正文：

- 使用 Markdown 文本编辑区。
- 支持预览切换。
- 不拆成词性、释义、例句等字段卡片。
- 词性、释义、原句、例句、搭配和我的笔记都由 AI 模板写入 Markdown 正文。

画布操作：

- `AI 整理`：基于选中词、上下文和默认模板生成正文。
- `优化格式`：基于当前正文生成格式更清晰的候选 Markdown。
- `预览`：渲染当前 Markdown。
- `保存为学习资产`：保存到后端。

## 默认 Markdown 模板

首版使用固定模板，不做模板仓库和用户自定义模板。

```md
# {{title}}

**词性：** {{partOfSpeech}}

**中文释义：** {{chineseMeaning}}

**English meaning：** {{englishMeaning}}

**原句：** {{sourceSentence}}

**AI 例句：** {{exampleSentence}}

**常见搭配：** {{collocations}}

## 我的笔记

{{userNotePlaceholder}}
```

字段缺失时，AI 可以省略对应行或使用自然语言补足。前端不需要解析这些字段，保存时只保存完整 Markdown。

## 数据模型

底层使用通用学习资产对象：

```ts
export type LearningAssetType = 'vocabulary' | 'sentence' | 'grammar' | 'expression'

export interface LearningAssetDraft {
  id: string
  type: LearningAssetType
  title: string
  contentMarkdown: string
  structuredPayload?: unknown
  sourceConversationId?: string
  sourceMessageId?: string
  sourceText?: string
  status: 'draft' | 'saved'
  updatedAt: string
}
```

后端建议新增通用表：

```text
learning_note
- id
- note_uid
- user_id
- type
- title
- content_markdown
- structured_payload
- source_conversation_uid
- source_message_uid
- source_text
- status
- created_at
- updated_at
- deleted_at
```

首版只创建：

```text
type = vocabulary
title = 选中的单词或短语
content_markdown = Markdown 正文
structured_payload = null
```

`structured_payload` 为后续语法树、句子结构、表达标签等结构化资产预留。

## API 方向

### 学习资产保存与读取

```text
POST /api/learning-notes
GET /api/learning-notes?type=vocabulary
GET /api/learning-notes/{noteUid}
PUT /api/learning-notes/{noteUid}
DELETE /api/learning-notes/{noteUid}
```

首版前端至少需要：

- 创建学习资产。
- 按 `type=vocabulary` 查询列表。
- 更新已保存资产。

鉴权沿用当前登录用户体系。用户只能访问自己的学习资产。

### AI 整理接口

建议新增明确接口，而不是伪装成普通聊天消息：

```text
POST /api/assistant/learning-canvas/organize
```

请求字段：

```json
{
  "type": "vocabulary",
  "title": "nuanced",
  "selectedText": "nuanced",
  "contextText": "A nuanced answer considers different sides of an issue.",
  "currentMarkdown": "",
  "mode": "create"
}
```

`mode`：

- `create`：按默认模板生成完整 Markdown。
- `format`：优化当前 Markdown 的结构和排版，尽量保留用户原意。

响应字段：

```json
{
  "candidateMarkdown": "# nuanced\n\n**词性：** adjective\n..."
}
```

如果首版需要更快落地，可以先在前端复用现有 assistant 能力拼接 prompt；但正式架构仍以专用接口为目标。

## 状态和持久化

### 当前对话草稿

草稿用于保存用户还没有提交到全局学习资产库的内容。首版建议由前端按 `conversationId` 分桶持久化，后续可迁移到后端。

建议 storage key：

```text
peai:assistant:learning-asset-drafts:{conversationId}
```

规则：

- 一个对话可以有多个草稿。
- 新建单词卡后立即创建草稿。
- 用户编辑 Markdown 时自动保存草稿。
- 保存为学习资产成功后，草稿状态改为 `saved` 或从草稿列表移除。
- 用户关闭画布不删除草稿。

### 全局学习资产

保存后的学习资产以后端为真源。`/app/vocabulary` 后续读取 `type=vocabulary` 的学习资产，并与现有词典收藏区分展示。

首版可以在词库页增加一个轻量分区：

- `我的单词笔记`：学习资产画布保存的 Markdown 单词卡。
- `词典收藏`：现有收藏词条。

## 组件职责

| 组件 | 职责 | 备注 |
| --- | --- | --- |
| `AssistantPage.vue` | 编排助手页、当前对话、学习资产画布开关和草稿恢复 | 页面级状态入口 |
| `AssistantChatView.vue` | 渲染消息，捕获助手回复中的文本选区，发出新建学习资产事件 | 不直接保存资产 |
| `LearningAssetCanvas.vue` | 右侧画布，负责标题、Markdown 编辑、预览、AI 操作和保存 | 新增组件 |
| `learningAssetDraftStore` | 管理当前对话草稿和本地持久化 | 新增前端状态边界 |
| `learningNotesApi` | 调用学习资产保存、更新和查询接口 | 新增 API 文件 |
| Backend `LearningNoteController` | 提供学习资产 CRUD | 新增后端入口 |
| Backend `LearningCanvasOrganize` | 提供 AI 整理和格式优化 | 可第一版或第二阶段实现 |

## 失败模式

| 场景 | 用户影响 | 系统行为 | 处理方式 |
| --- | --- | --- | --- |
| 选区为空 | 无法创建资产 | 不显示工具条 | 用户重新选择 |
| AI 整理失败 | 无法自动生成 Markdown | 保留空白或当前正文，显示错误 | 用户手写或重试 |
| 优化格式失败 | 无候选预览 | 不替换正文，提示失败 | 用户重试 |
| 保存失败 | 资产未进入全局库 | 草稿继续保留，提示保存失败 | 用户稍后重试 |
| Markdown 预览失败 | 预览显示异常 | 回退到纯文本或错误提示 | 不影响原始正文 |
| 未登录 | 无法保存正式资产 | 返回登录提示 | 引导登录 |

## 设计取舍

### 为什么不做纯单词卡组件

纯 `WordCard` 会让后续句子整理、语法树和表达积累产生重复画布。通用学习资产画布能复用右侧布局、草稿、保存和 AI 整理能力。

### 为什么不用一堆字段卡片

字段卡片方便结构化，但用户编辑体验像填表。英语学习笔记更需要自由增删、改写和补充个人理解。Markdown 更适合首版。

### 为什么不先做富文本编辑器

富文本编辑器体验更友好，但会引入内容转换、编辑器状态、粘贴、预览和模板兼容问题。首版先用 Markdown 验证学习闭环，后续再评估所见即所得编辑。

### 为什么 AI 优化格式不直接替换

用户会在画布里写自己的理解。AI 直接替换会破坏安全感。候选预览加确认能避免误覆盖。

## 后续扩展

| 扩展 | 做法 |
| --- | --- |
| 句子整理 | 新增 `type=sentence`，入口文案为 `整理句子`，模板输出句子结构、翻译、表达亮点 |
| 语法树 | 新增 `type=grammar`，Markdown 保存讲解，`structuredPayload` 保存树结构 |
| 写作表达积累 | 新增 `type=expression`，模板输出表达、适用场景、替换句和作文用法 |
| 模板仓库 | 每个 `type` 支持多个 AI 输出模板，用户选择模板后生成不同 Markdown |
| 富文本编辑 | 在 Markdown 闭环稳定后，引入所见即所得编辑或 Markdown 分屏预览 |
| 跨端同步 | 草稿从本地迁移到后端，支持 iOS 和 Web 共享 |

## 验收方式

- 在学习助手 AI 回复中选中英文单词或短语后，出现 `新建单词卡`。
- 点击后右侧固定打开学习资产画布，左侧对话不被覆盖。
- 画布标题为选中文本，类型为 `vocabulary`。
- AI 可以按默认模板生成 Markdown 正文。
- 用户可以手动编辑 Markdown。
- `优化格式` 先生成候选预览，确认后才替换正文。
- 未保存草稿刷新或切回对话后仍可恢复。
- 保存成功后，资产进入全局学习资产库。
- `/app/vocabulary` 能展示已保存的 vocabulary 学习资产。
- 前端构建通过。
- 后端学习资产接口测试通过。

## 验证建议

```bash
cd web
npm run build
```

```bash
cd backend
mvn test
```

人工回归：

1. 选中 AI 回复里的单词创建画布。
2. 创建后刷新页面恢复草稿。
3. 编辑 Markdown 后切换对话再切回。
4. AI 整理失败时草稿不丢失。
5. AI 优化格式确认前不覆盖正文。
6. 保存后在全局单词本中可见。
7. 未登录或 token 失效时保存失败提示清晰。

## 文档与合并判断

本设计涉及前端状态、后端数据模型、API 和 AI 整理行为，应保留为当前有效设计文档。后续实现时需要同步更新：

- API 契约文档。
- 数据表和迁移说明。
- AI 整理 Prompt 或输出规范。
- `/app/vocabulary` 产品说明。

该功能属于跨前后端的新能力，不适合直接在已有 OCR 分支混合完成。实现阶段建议新建独立分支，例如 `codex/learning-asset-canvas`。设计文档本身风险较低，可单独提交；代码实现应完成构建、接口测试和人工回归后再评估合并到 `main`。
