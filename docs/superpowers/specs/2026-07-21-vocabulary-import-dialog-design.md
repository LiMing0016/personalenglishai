---
title: 单词沉淀统一 AI 导入对话框设计
status: accepted
owner: project
last_updated: 2026-07-21
review_cycle: on-change
related_code:
  - web/src/components/vocabulary/VocabularyCapturePanel.vue
  - web/src/components/vocabulary/VocabularyImageCapture.vue
  - web/src/components/vocabulary/VocabularyTermReview.vue
  - web/src/views/VocabularyView.vue
  - backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - python/ai_orchestrator/app.py
  - python/ai_orchestrator/workflows/
related_docs:
  - docs/superpowers/specs/2026-07-21-vocabulary-image-import-workspace-design.md
  - docs/superpowers/specs/2026-07-21-vocabulary-navigation-simplification-design.md
---

# 单词沉淀统一 AI 导入对话框设计

## 当前结论

单词沉淀页采用“紧凑入口栏 + 居中任务对话框”。页面常态只显示“导入单词”入口和未完成候选摘要，单词列表继续作为页面主体。

对话框不再区分“文本录入”和“图片识别”。用户在同一个类似 GPT 的多模态输入框中输入或粘贴文本、粘贴图片，或通过附件按钮选择图片，再显式点击“AI 分析”。AI 将文本和图片统一整理为候选词；用户确认、编辑和排序候选词后选择主题并生成卡片。

“AI 分析”和“生成卡片”是两个独立阶段：前者只提取候选、标记疑似拼写错误和保留来源证据，后者继续按主题 Prompt 生成结构化核心字段与 Markdown 卡片内容。

## 目标

- 空闲时让已沉淀的单词资产成为页面主体。
- 用一个输入框同时承载文本和图片，消除重复模式入口。
- 允许用户输入单词、句子、段落或提取要求，由 AI 统一整理候选词。
- 让 AI 纠错保持“建议”语义，不静默覆盖图片或文本中的原词。
- 在生成卡片前保留明确的人工确认步骤。
- 保留现有主题、去重、来源合并、异步生成和卡片详情能力。

## 页面常态

卡片列表上方只显示一条紧凑导入栏：

- 左侧显示“导入单词”和简短说明“文本、图片或粘贴内容”。
- 右侧主按钮使用 Lucide `Plus` 图标和“导入单词”文字。
- 没有草稿时不显示 `0 个候选词`；存在未提交候选时显示 `N 个待确认`。
- 不在页面常态展示独立图片快捷按钮、主题、空输入框、空候选区或来源语境。

点击主按钮打开导入对话框。列表搜索、筛选、排序、分页和卡片行保持现状。

## 对话框结构

桌面端对话框宽度为 `min(800px, calc(100vw - 32px))`，最大高度为 `80vh`。标题区和底部操作区固定，中间内容独立滚动。

内容顺序固定为：

1. 标题“导入单词”、简短说明和关闭按钮。
2. 统一多模态输入框。
3. AI 分析状态或错误信息。
4. 候选词复核区与排序控件。
5. 底部主题选择、取消和“生成卡片”按钮。

本轮删除来源语境入口和字段。模型可从本次输入文本及图片中提取与候选词直接相关的上下文，但用户不再额外填写独立来源语境。

## 统一多模态输入框

输入框允许：

- 输入或粘贴单词、短语、句子、段落和简短提取要求。
- 使用 `Ctrl/Cmd + V` 粘贴剪贴板图片。
- 点击 Lucide `Paperclip` 图标选择 JPG、PNG 或 WEBP 图片。
- 同时提交文本和一张图片，也允许只提交其中一种。

首版文本最大 20,000 字符，只允许一张图片，图片最大 10 MB。选择或粘贴第二张图片时替换第一张；不支持多图队列、PDF 和拖拽上传。

已添加图片在输入框顶部显示文件卡片，包含 Lucide `FileImage` 图标、文件名、大小和移除按钮。附件按钮必须提供 `aria-label="添加图片"` 和悬停提示。输入框提示文案为“输入单词、句子或段落，也可以直接粘贴图片”。

输入框底部右侧提供“AI 分析”按钮。文本和图片都为空、分析正在进行或图片校验失败时按钮禁用。分析只在用户显式点击后发生，不因输入、粘贴或关闭对话框自动调用模型。

## AI 分析规则

每次点击“AI 分析”发起一次统一请求：

- 纯单词或短语列表：按首次出现顺序保留有效项目并去重。
- 句子或段落：提取对英语学习有价值的实义词、短语和固定表达，过滤常见虚词。
- 图片：优先提取列表项、圈选、高亮、下划线或箭头指向内容；没有视觉标记时再提取段落实义词。
- 文本包含提取要求时，模型可以据此收窄范围，但不得补充输入中没有文本或视觉证据的词。
- 同一候选同时出现在文本和图片中时只保留一项，并标记为 `text_image`。
- 单次最多返回 30 个候选；超出时截断并返回稳定 warning `CANDIDATE_LIMIT_REACHED`。
- 疑似拼写错误保留原词，返回最多 3 个修正建议，必须由用户采用建议、保留原词或删除。

AI 分析不生成释义、音标、词性、例句或完整卡片，也不写入业务数据库。

## 统一分析接口

浏览器调用 Java 公开接口：

```text
POST /api/vocabulary/import-analyses
Content-Type: multipart/form-data
```

请求字段：

- `text`：可选，最大 20,000 字符。
- `file`：可选，单张 JPG、PNG 或 WEBP，最大 10 MB。

`text` 和 `file` 至少提供一个。Java 负责用户鉴权、AI 配额、文件与文本边界校验、业务 `traceId`、公开错误映射、词典二次验证和产品事件。

统一分析使用独立配额 operation key `vocabulary.import_analysis`。每次用户点击“AI 分析”最多产生一次正常模型调用；结构化输出失败时最多进行一次受总超时约束的重试。卡片生成继续独立计量，不能把分析调用重复计入每一张候选卡片。

Java 调用 Python 内部接口：

```text
POST /internal/v1/vocabulary/import-analyses
Content-Type: multipart/form-data
Authorization: Bearer <internal-service-token>
```

Python 使用版本化 Prompt `vocabulary-import-analysis-v1` 和支持当前输入模态的配置模型。没有图片时只提交文本；存在图片时提交文本和图片。workflow 无状态，不读写业务数据库，不生成卡片，也不携带聊天历史。

成功响应沿用当前图片识别的结构化字段，并为每个候选增加证据来源：

```json
{
  "contractVersion": 1,
  "traceId": "vocab-import-abc123",
  "warnings": [],
  "items": [
    {
      "itemId": "item-1",
      "observedText": "scrutinize",
      "normalizedTerm": "scrutinize",
      "status": "accepted",
      "suggestions": [],
      "contextText": "We need to scrutinize the benefits.",
      "confidence": 0.98,
      "evidence": "text_image"
    }
  ],
  "generation": {
    "provider": "configured-provider",
    "model": "configured-model",
    "promptVersion": "vocabulary-import-analysis-v1",
    "modelCallCount": 1,
    "traceId": "vocab-import-abc123",
    "usage": {
      "inputTokens": 900,
      "outputTokens": 180
    }
  }
}
```

`evidence` 只允许 `text`、`image` 或 `text_image`。Python 和 Java 都执行契约校验；浏览器不得直连 Python 或持有模型密钥。

现有 `/api/vocabulary/image-recognitions` 和对应 Python 内部接口在统一接口上线后的一个发布周期内保留，供快速回滚和旧前端兼容；新版前端不再调用它们。统一接口稳定后再通过独立维护改动删除旧入口，不在本次功能开发中同时移除。

## 候选词复核与排序

AI 分析成功后显示候选词复核区。候选词状态在前端保持单一真源，后续编辑、选择、纠错和删除均通过候选 ID 定位。

复核区提供两段式排序控件：

- `录入顺序`：默认，按模型返回的首次证据顺序展示。
- `A–Z`：按当前候选词值执行不区分大小写的稳定升序排序。

排序只改变展示顺序，不重排原始候选数组，不修改候选 ID、证据来源、分析 trace 或提交分组。在 `A–Z` 模式编辑词形后，列表按新词形立即重新排序。

候选行展示当前词形和简短来源标签“文本”“图片”或“文本 + 图片”。模型置信度不作为醒目主信息，只用于调试和必要的低置信提示。

## 卡片生成与来源

用户完成候选确认并选择主题后，继续调用现有 `POST /api/vocabulary/captures` 生成卡片。卡片生成 Prompt、统一 JSON 核心字段和 Markdown 扩展内容保持现状。

为了保持现有来源筛选兼容：

- `evidence=text` 使用来源类型 `manual`。
- `evidence=image` 或 `text_image` 使用来源类型 `ocr_image`。
- 每个来源 metadata 记录 `analysisTraceId`、`evidence`、provider、model、Prompt version、observed text 和用户纠错决定。

文本和图片候选按来源类型分批提交，但使用同一主题。卡片去重、重复词来源合并、异步生成和 revision 冲突规则不变。

## 状态与关闭规则

`VocabularyCapturePanel` 继续拥有对话框开关、输入文本、图片附件、候选词、排序模式、主题、分析状态和提交状态。子组件不得复制长期状态。

- 首次进入页面时对话框关闭。
- 用户关闭有未提交内容的对话框时保留当前页面会话草稿。
- 关闭再打开时保留文本、附件、候选、主题和排序模式；刷新页面后不恢复。
- 修改已分析的文本或图片后，现有候选保留但标记为“输入已变化”，生成按钮禁用，必须重新执行 AI 分析。
- AI 分析失败时保留文本和图片；旧候选如果与当前输入不一致，不允许提交。
- 所有选中候选成功提交后清空草稿、关闭对话框并刷新列表。
- 部分生成失败或存在未处理拼写问题时保持打开并保留未完成候选。
- 分析或提交进行中禁止关闭，遮罩点击和 `Escape` 与关闭按钮使用同一判断。

对话框打开后焦点进入输入框，关闭后返回“导入单词”触发按钮。使用原生 `dialog` 或等价的可访问模态语义并限制焦点留在内部。

## 组件边界

- `VocabularyCapturePanel`：入口栏、模态状态、草稿单一真源、分析与生成编排。
- 新增 `VocabularyImportComposer`：文本输入、图片选择、剪贴板图片粘贴、附件展示和分析触发。
- `VocabularyTermReview`：候选展示、排序控件和候选命令。
- `VocabularyThemeSelect`：底部主题选择和主题管理入口。
- `VocabularyView`：继续组合导入面板和卡片列表，不保存导入草稿。
- 现有 `VocabularyTextCapture` 和 `VocabularyImageCapture` 不再由导入面板组合；确认无其他调用后删除。

首版不抽取全局 Modal 组件。前端新增 `lucide-vue-next` 并按图标组件单独导入。

## 错误处理

| 场景 | 前端行为 |
| --- | --- |
| 文本和图片都为空 | 禁用“AI 分析” |
| 图片类型、大小或解码不合法 | 就地提示并要求移除或更换图片 |
| AI 配额不足 | 保留草稿，提示额度不可用 |
| 分析超时或服务不可用 | 保留草稿，允许显式重试 |
| 模型输出不符合 schema | 不展示半成品候选，允许重试 |
| 没有提取到候选词 | 显示空结果提示，允许修改输入后重试 |
| 输入在分析后发生变化 | 标记结果已过期，禁用生成，要求重新分析 |
| 卡片部分生成失败 | 移除已完成候选，保留失败候选和错误信息 |

模型失败时不自动回退本地正则解析或 PaddleOCR，避免同一按钮产生不可预测的规则差异。

## 响应式规则

- 大于 620 px：居中宽对话框，标题和底部固定，中间滚动。
- 不大于 620 px：对话框占满视口，不保留悬浮卡片外观。
- 移动端附件、候选行、主题和底部按钮不能横向溢出。
- 对话框打开时页面背景禁止滚动。
- 长文件名、长词组和错误信息必须截断或换行，不覆盖操作按钮。

## 测试与验收

### 前端

- 页面常态只显示紧凑导入栏。
- 输入文本、粘贴图片和附件按钮选择图片都能形成同一份分析请求。
- 第二张图片替换第一张，非法类型和超大图片在请求前被拒绝。
- AI 分析成功后显示候选及“文本 / 图片 / 文本 + 图片”证据标签。
- 输入变化使旧分析结果过期并阻止生成。
- `录入顺序`和 `A–Z`排序稳定，编辑和删除仍命中原候选 ID。
- 完整成功后关闭，分析失败和部分生成失败后保持打开。
- 页面不再出现“文本录入 / 图片识别”模式切换和来源语境。

### Java 与 Python

- 公开和内部 multipart 接口都要求 `text` 或 `file` 至少一个。
- 文本、图片和混合输入分别通过契约测试。
- Prompt 只提取输入中存在证据的候选，并返回合法 `evidence`。
- 疑似拼写错误仍经过词典二次验证，不静默纠正。
- 配额、超时、非法 schema 和空结果映射为稳定行为。
- 日志和产品事件不记录完整文本、图片内容或模型原始响应。

### 端到端

- 文本段落分析、候选确认、主题选择和卡片生成闭环。
- 粘贴图片分析、拼写建议处理和卡片生成闭环。
- 文本加图片混合输入去重后生成正确来源 metadata。
- 桌面 1280 x 900 和移动 390 x 844 下无横向溢出、遮挡和焦点丢失。

## 非目标

- 不支持多图、PDF、拖拽上传和相机拍照入口。
- 不接入聊天会话、网页收藏或 PDF 单词来源。
- 不持久化未提交导入草稿。
- 不修改主题定义、卡片核心 JSON 或 Markdown 渲染。
- 不重构卡片列表和筛选器。

## 文档影响

实施完成后同步更新：

- `docs/architecture/vocabulary-deposition.md`：统一 AI 分析调用链和来源映射。
- 单词公开 API 文档：`/api/vocabulary/import-analyses` 请求、响应和错误码。
- AI 文档：`vocabulary-import-analysis-v1` Prompt 与结构化输出。
- 本地运行文档：统一分析模型配置和真实模型验收步骤。
