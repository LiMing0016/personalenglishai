---
title: 单词沉淀图片识别导入工作区设计
status: draft
owner: project
last_updated: 2026-07-21
review_cycle: on-change
related_code:
  - web/src/views/VocabularyView.vue
  - web/src/components/vocabulary/VocabularyCapturePanel.vue
  - web/src/components/vocabulary/VocabularyThemeShelf.vue
  - web/src/api/vocabulary.ts
  - backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - python/ai_orchestrator/app.py
  - python/ai_orchestrator/workflows/
related_docs:
  - docs/architecture/vocabulary-deposition.md
  - docs/superpowers/specs/2026-07-10-vocabulary-deposition-core-design.md
  - docs/superpowers/specs/2026-07-12-vocabulary-theme-markdown-card-design.md
  - docs/superpowers/specs/2026-07-14-vocabulary-python-generation-workflow-design.md
---

# 单词沉淀图片识别导入工作区设计

## 当前结论

单词沉淀页采用“文本录入 / 图片识别”双模式工作区。页面删除重复的英文眉题、长说明和底部主题提示，把视觉重点收敛到“导入、校对、生成”三步。文本录入保持现有沉淀能力；图片识别支持单张 JPG、PNG、WEBP 上传和移动端拍照，并把识别结果汇入同一份可编辑候选词列表。

图片识别不调用本地 PaddleOCR，也不复用写作模块的手写作文识别接口。调用链固定为前端上传图片、Java 鉴权与校验、Python Agent 调用图片大模型并校验结构化输出。模型负责识别视觉标记、整理词条和发现疑似拼写错误，但不得静默修正。疑似错误必须由用户选择采用建议、保留原词或删除，处理完成后才能生成卡片。

Java 继续拥有用户鉴权、公开 API、单词沉淀业务和来源持久化；Python 继续拥有 Prompt、模型调用、结构化输出校验和模型 trace。浏览器不得直接持有模型密钥或调用 Python 内部接口。

## 背景与问题

当前单词沉淀页同时展示页面眉题、标题、说明、录入标题、主题说明、主题卡片、来源语境说明和底部提示。信息层级重复，导入区占据较大空间，但主要任务仍只有输入单词和生成卡片。按钮文案把主题名称和零计数完整写出，也增加了无效阅读负担。

当前录入仅支持文本。用户希望通过图片或拍照导入单词，并让模型识别图片中的格式、圈选或高亮内容，同时发现可能拼错的词。项目已有 Java、Python Agent 和图片模型调用能力，因此应沿用现有跨服务边界，新增专用的单词图片识别工作流，而不是让前端直连模型或把写作业务接口改造成通用 OCR。

## 目标与非目标

### 目标

- 减少单词沉淀页的重复标题、说明和弱提示。
- 让文本录入和图片识别拥有清晰、并列的入口。
- 支持单张图片上传和移动端拍照。
- 由图片大模型返回结构化候选词、上下文和疑似拼写错误。
- 让正常词和图片词进入同一份可编辑候选列表，并复用现有卡片沉淀接口。
- 对疑似错误执行显式用户确认，避免错误词被静默写入单词库。
- 把单次图片候选词控制在可快速审阅的范围，并提供批量选择能力。
- 对模型标记的疑似错误执行词典二次验证，降低生僻词误判。
- 为识别延迟、候选采用、沉淀转化、首次学习和模型成本建立可观测指标。
- 保持主题管理页、卡片生成任务和卡片详情契约不变。
- 为前端、Java、Python 和完整链路提供可重复验证的边界。

### 非目标

- 不在首版支持多图、PDF、批量相册或剪贴板图片。
- 不永久保存原始图片，也不建设图片资源管理功能。
- 不在首版重做下方单词卡列表、筛选和分页。
- 不让图片模型直接生成单词卡 core JSON 或主题 Markdown。
- 不把 PaddleOCR 与图片大模型串成双阶段识别链路。
- 不自动采用模型纠错建议。
- 不把图片识别接入通用聊天路由、聊天历史或对话记忆。

## 页面与交互设计

### 页面标题

页面标题只保留“单词沉淀”。删除：

- `WORD CARDS` 英文眉题。
- “单词卡中心”旧标题。
- “手动录入和词典收藏的单词会沉淀在这里；更多来源后续接入”说明。

导航已经表达当前模块，页面不再重复解释功能。下方单词卡总数继续由列表区域展示，不在标题旁增加第二套统计口径。

### 导入工作区

导入区标题改为“导入单词”，右侧使用 `文本录入` 和 `图片识别` 两个标签页。标签页切换只改变输入方式，不改变当前主题和已经整理出的候选词。

主题选择从横向主题卡片改为紧凑下拉框，显示 `主题：<名称>`，旁边保留“管理主题”入口。新建主题继续在主题管理页完成，不在导入工作区放置大尺寸“新建主题”卡片。

来源语境默认折叠为“添加来源语境”。展开后显示单行输入框。删除“所选主题仅用于本次沉淀”。主按钮统一为“生成卡片”；有候选词时显示数量，例如“生成 3 张卡片”，没有候选词时禁用。

### 文本录入模式

文本模式继续支持换行、逗号和分号分隔。解析后的候选词在输入区下方或输入区内以可编辑词条形式展示。用户可以删除词条并继续输入，现有 `parseCaptureTerms` 去重和规范化规则继续作为提交前边界。

文本模式不显示 OCR 状态、图片预览或纠错确认控件。原有文本沉淀路径不得因为新增图片模式而增加额外请求。

### 图片识别模式

空状态提供“上传图片”和“拍照”两个明确操作。桌面端“拍照”可退化为系统图片选择；移动端使用 `capture="environment"` 请求后置摄像头。首版只接受一张 JPG、PNG 或 WEBP 图片，默认最大 10 MB。前端和 Java 都执行类型、大小和空文件校验。

选择图片后显示真实预览、文件名和“开始识别”。识别过程中显示单一进度状态并禁用重复提交。识别完成后显示：

- 已识别候选词总数。
- 待确认错误词数量。
- 可编辑候选词列表。
- 默认折叠的“查看识别原文”。
- “重新识别”和“更换图片”。

单次图片最多展示 30 个候选词，并提供“全选”和“清空”操作。模型发现更多内容时只返回优先级最高的 30 个候选，同时显示“已优先提取 30 个单词”；不得把超量候选隐藏在滚动区域继续提交。

图片切换或重新识别不得清空文本模式中已经手工整理的词。新识别结果与当前候选词按大小写不敏感规则去重，保持首次出现顺序。

### 图片词提取规则

图片模型按以下优先级提取候选词：

1. 单独成行或具有明显列表结构的英文词、短语。
2. 圈选、高亮、下划线、箭头指向或其他视觉标记的英文内容。
3. 如果没有列表和视觉标记，从段落中提取去重后的实义词，过滤常见冠词、代词、介词、连词、助动词等虚词。

模型只能返回图片中有视觉证据的原词，不得凭主题补充图片中不存在的词。候选短语应保留自然空格；纯数字、标点、网址碎片和无法解释的 OCR 噪声不进入候选列表。

### 疑似错误处理

模型输出 `suspected_typo` 时，界面同时显示图片原词和纠正建议，例如 `recieve -> receive`。用户必须选择：

- 采用建议：候选词改为所选建议，并保留原词审计信息。
- 保留原词：以图片原词生成卡片。
- 删除：从本次候选列表移除。

未处理的疑似错误不计入可生成数量，并阻止提交。普通候选词默认选中且可以删除。模型的纠错建议只是候选，不得覆盖用户决定。

## 组件设计

前端组件职责收敛为：

- `VocabularyCapturePanel`：编排模式、主题、来源语境、候选词和最终提交。
- `VocabularyTextCapture`：文本输入、解析结果和手工编辑。
- `VocabularyImageCapture`：图片选择、拍照、预览、识别请求和识别状态。
- `VocabularyTermReview`：统一展示正常词、疑似错误和用户处理结果。
- `VocabularyThemeSelect`：当前主题下拉选择和主题管理入口。

`VocabularyThemeShelf` 继续服务主题管理或其他需要主题卡片的界面；导入工作区不再直接渲染完整主题 shelf。候选词状态在 `VocabularyCapturePanel` 内保持单一真源，子组件只通过 props 和事件读写，不各自复制一份长期状态。

首版不新增 Pinia store。导入状态只属于当前页面会话，刷新后无需恢复。服务端数据和请求生命周期继续使用现有 TanStack Query mutation 边界。

## 服务边界与调用链

### 浏览器到 Java

新增公开接口：

```text
POST /api/vocabulary/image-recognitions
Content-Type: multipart/form-data
```

请求字段：

- `file`：单张 JPG、PNG 或 WEBP，最大 10 MB。

Java 负责：

- 用户鉴权和配额入口。
- 文件为空、MIME、扩展名和大小校验。
- 生成业务 `traceId`。
- 调用 Python 专用内部接口。
- 校验 Python 响应 contract version、trace 和字段边界。
- 对 `suspected_typo` 的原词与建议执行现有词典服务二次验证。
- 使用现有 AI 配额边界记录 `vocabulary.image_recognition` 用量并拒绝超额请求。
- 把内部错误映射为稳定的公开错误码。

浏览器不提交模型名称、Prompt 内容或 provider。模型选择由 Python 环境配置决定。

### Java 到 Python

新增内部接口：

```text
POST /internal/v1/vocabulary/image-recognitions
Content-Type: multipart/form-data
Authorization: Bearer <internal-service-token>
```

请求字段：

- `contractVersion=1`
- `traceId`
- `language=en`
- `file`

Python 负责：

- 内部服务认证。
- 图片解码和模型允许的格式校验。
- 调用专用 vocabulary image recognition workflow。
- 使用仓库 Prompt 资产约束提取优先级和错误词行为。
- 使用 Pydantic `output_type` 校验模型结构化输出。
- 清理重复项、空项、越界长度和非法置信度。
- 返回 provider、model、Prompt version、model call count 和 trace。

该 workflow 无状态，不读写业务数据库，不调用卡片生成 workflow，也不携带聊天历史。

## 结构化输出契约

Python 成功响应：

```json
{
  "contractVersion": 1,
  "traceId": "vocab-image-abc123",
  "rawText": "recieve\npackage\n...",
  "warnings": [],
  "items": [
    {
      "itemId": "item-1",
      "observedText": "recieve",
      "normalizedTerm": "recieve",
      "status": "suspected_typo",
      "suggestions": ["receive"],
      "contextText": "I recieve the package",
      "confidence": 0.94
    },
    {
      "itemId": "item-2",
      "observedText": "package",
      "normalizedTerm": "package",
      "status": "accepted",
      "suggestions": [],
      "contextText": null,
      "confidence": 0.98
    }
  ],
  "generation": {
    "provider": "openai",
    "model": "configured-vision-model",
    "promptVersion": "vocabulary-image-recognition-v1",
    "modelCallCount": 1,
    "traceId": "vocab-image-abc123",
    "usage": {
      "inputTokens": 1240,
      "outputTokens": 286
    }
  }
}
```

字段规则：

- `observedText` 必须尽量保留图片原词，不执行静默纠正。
- `normalizedTerm` 只做大小写、首尾空格和标点边界规范化；疑似错误时仍等于规范化后的原词。
- `status` 只允许 `accepted` 或 `suspected_typo`。
- `suggestions` 最多 3 个；`suspected_typo` 必须至少有 1 个建议。
- `contextText` 只返回图片中与该词直接相关的短句，不允许扩写。
- `confidence` 范围为 0 到 1，仅用于提示和诊断，不直接决定是否生成。
- 单次最多返回 30 个候选词；超出时按提取优先级截断并返回稳定 warning `CANDIDATE_LIMIT_REACHED`。
- `warnings` 是顶层字符串数组；Python 可以返回 `CANDIDATE_LIMIT_REACHED`，Java 可以追加 `DICTIONARY_VERIFICATION_UNAVAILABLE`，未知 warning 必须被跨服务契约拒绝。
- `rawText` 最大 20,000 字符，只用于用户展开核对，不进入卡片生成 Prompt。
- `generation.usage` 记录 provider 可用的输入与输出 token；provider 不返回用量时字段为 `null`，不得估算后伪装成真实值。

Python 与 Java 都执行契约校验。Java 的再次校验属于跨服务信任边界，不复制 Prompt 或提取业务。

## 词典二次验证

Java 只对模型标记为 `suspected_typo` 的项目执行词典查询，避免对全部正常候选增加延迟。验证规则为：

1. 原词能命中词典时，将项目降级为 `accepted`，不再要求用户处理模型建议。
2. 原词未命中、一个或多个建议命中时，保持 `suspected_typo`，只把命中的建议排在前面并标记为已验证。
3. 原词和建议都未命中时，保持 `suspected_typo`，允许用户保留原词或删除；未验证建议不得被自动采用。

词典结果不能证明图片识别一定正确，因此即使建议命中，仍由用户确认。词典不可用时不阻断整个识别响应，保留模型状态并返回 warning `DICTIONARY_VERIFICATION_UNAVAILABLE`。

Python 内部响应继续使用 `suggestions: string[]`。Java 的公开响应把每个建议转换为 `{ "term": string, "dictionaryVerified": boolean }`，从而让前端可以区分已命中词典与仅由模型提出的建议，同时不把 Java 的词典职责反向泄漏到 Python contract。

## 沉淀与来源记录

图片候选词确认完成后，前端继续调用现有：

```text
POST /api/vocabulary/captures
```

每批请求使用用户选定主题，来源类型新增 `ocr_image`。`VocabularyCaptureRequest.Source.type`、Java 支持类型集合和相关校验正则同步增加该枚举值。

为保证批量沉淀中的每个词都能保留自己的上下文和决策，`VocabularyCaptureRequest` 增加可选 `itemSources` 数组。数组存在时必须与 `terms` 等长；每项只包含该词的 `contextText` 和 `metadata`。Java 在创建 `VocabularyCardSource` 时把批次级 `source.metadata` 与对应的 `itemSources[index].metadata` 合并，并优先使用该项的 `contextText`。文本录入不发送 `itemSources`，保持现有请求兼容。

来源 metadata 至少记录：

- `recognitionTraceId`
- `fileName`
- `provider`
- `model`
- `promptVersion`
- `observedText`
- `resolution`：`accepted`、`suggestion_applied` 或 `original_kept`

当用户把图片候选词显式编辑为不同于 `observedText` 的终稿时，记录为 `suggestion_applied`；该值表示用户采用了纠正后的词形，不要求终稿必须来自模型建议数组。`accepted` 仅表示终稿与正常识别结果一致，`original_kept` 仅表示用户明确保留疑似错误原词。

metadata 不保存图片 base64、完整原始识别文本或模型原始响应。卡片去重、来源合并、异步生成、主题版本和 revision 冲突逻辑继续使用现有实现。

## 错误与状态处理

公开错误按稳定类型映射：

| 场景 | HTTP | 前端行为 | 是否可重试 |
| --- | --- | --- | --- |
| 类型、大小或空文件不合法 | 400/422 | 就地提示并要求更换图片 | 否 |
| 未登录或无权限 | 401/403 | 使用全局鉴权流程 | 否 |
| AI 配额不足 | 429 | 保留图片，提示当前额度不可用 | 否 |
| 模型超时 | 504 | 保留图片和候选编辑状态，显示重新识别 | 是 |
| 模型服务不可用 | 503 | 保留图片，显示重新识别 | 是 |
| 模型输出不符合 schema | 502 | 显示识别结果不可用，不展示半成品 | 是 |
| 图片中没有可用候选词 | 200，空 `items` | 显示“未识别到可导入单词”并允许换图 | 是 |

识别失败不得自动回退 PaddleOCR 或手写作文识别，以免同一次操作产生不同选择规则和额外费用。重试由用户显式触发。一次识别请求内部的模型重试必须有界，并受总超时预算控制。

切换到文本模式时，进行中的图片请求可以取消；迟到响应不得覆盖用户后续选择的图片或候选词。使用请求序号或 `AbortController` 保证最新请求获胜。

## Prompt 与模型约束

新增版本化 Prompt 资产 `vocabulary-image-recognition-v1`，只负责：

- 读取图片中实际可见的英文内容。
- 按列表、视觉标记、段落实义词的优先级提取候选。
- 保留原词并标记疑似拼写错误。
- 给出最多 3 个纠正建议。
- 返回相关图片上下文，不补写不存在的句子。
- 严格输出 Pydantic schema，不输出 Markdown 或解释文本。

模型不得查询词典或生成单词释义。疑似错误判断可以使用模型语言能力，但最终卡片事实仍由现有词典优先、AI 补全的卡片生成工作流负责。

默认每张图片只调用一次图片模型。结构化输出解析失败时，Python 最多执行一次受控重试；不得转用第二个模型造成不可预测的费用和输出差异。

## 隐私、成本与可观察性

- 图片只在当前请求期间保存在内存或受控临时文件中，请求结束后清理。
- 不把图片 base64、完整图片内容或模型原始响应写入普通日志。
- 模型 trace 默认不包含敏感图片数据。
- Java 与 Python 使用同一个业务 `traceId`，日志记录耗时、文件大小、候选数量、错误词数量、provider、model、Prompt version 和稳定错误码。
- Java 记录图片识别用量、最终保留候选数和成功沉淀卡片数，用于计算每张成功卡片的增量模型成本；不把供应商单价硬编码进业务代码。
- 图片识别与后续逐词卡片生成分别计量，避免把批量图片调用成本错误归到任意一个单词。
- 图片识别接入现有 AI 配额，使用独立 operation key `vocabulary.image_recognition`；首版不新增第二套余额系统。
- 公开响应不返回内部认证信息、模型原始异常或 Prompt。
- 首版不做后台异步任务。单张图片识别采用同步请求，产品性能目标为 P50 不超过 8 秒、P95 不超过 20 秒；前端硬超时 60 秒，Java 到 Python 预算 55 秒，Python 模型调用预算最多 45 秒。

## 产品指标与竞争力验证

首版必须记录以下匿名业务事件，不记录图片或识别正文：

- `vocabulary_image_recognition_started`
- `vocabulary_image_recognition_completed`
- `vocabulary_image_candidates_confirmed`
- `vocabulary_capture_submitted`
- `vocabulary_cards_ready`
- `vocabulary_learning_started`

事件写入 Java 管理的产品事件表，使用客户端生成的幂等 `eventUid`、页面会话 `sessionId`、业务 `traceId` 和可选 `cardUid` 关联，不保存文件名、原词、识别原文或卡片正文。`vocabulary_capture_submitted` 与 `vocabulary_cards_ready` 由 Java 在真实业务状态变化后记录；其余交互事件由前端批量上报。当前产品中首次打开一张已经可读的持久化单词卡详情，定义为该卡的 `vocabulary_learning_started`，不得在尚未进入具体卡片内容的静态“开始学习”按钮上提前记录。

核心指标定义为：

- 识别耗时：开始识别到候选词可编辑。
- 候选采用率：最终保留候选数除以模型返回候选数。
- 人工修正率：被编辑、删除或执行拼写冲突处理的候选占比。
- 沉淀完成率：成功识别后提交至少一个候选词的会话占比。
- 卡片就绪率：提交后最终进入 `ready` 的卡片占比。
- 24 小时首次学习率：卡片就绪后 24 小时内进入现有学习流程的卡片占比。
- 每张成功卡片增量成本：本次图片识别模型用量成本除以最终就绪卡片数。

发布后以文本录入用户作为对照。图片导入的沉淀完成率和 24 小时首次学习率不得比文本录入低超过 10 个百分点；若持续低于该边界，优先收紧候选提取与确认步骤，不继续扩展多图或 PDF。

## 测试与验收

### 前端

- 页面只显示“单词沉淀”，不再显示英文眉题和长说明。
- 文本与图片标签页可通过鼠标和键盘切换，焦点样式清晰。
- 文本录入、主题选择、来源语境和现有 capture mutation 正常工作。
- JPG、PNG、WEBP 可选择；非法类型、空文件和超大文件在请求前被拒绝。
- 图片预览比例稳定，不挤压按钮或覆盖文本。
- 正常词默认可生成；疑似错误未处理时按钮禁用。
- 单次最多展示 30 个候选词，超量提示、全选和清空行为正确。
- 采用建议、保留原词和删除都产生正确候选与 metadata。
- 模式切换、重试和换图不会被迟到响应覆盖。
- 620 px 以下按钮、标签页、主题下拉和词条不溢出。

### Java

- 公开接口要求登录，并拒绝非法 multipart。
- Java 只调用配置的 Python 内部接口，不直连模型。
- Java 只对疑似错误执行词典验证，并正确处理原词命中、建议命中和词典不可用。
- AI 配额不足时返回 429，且不会调用 Python。
- 内部 4xx、5xx、超时和非法 schema 映射为稳定公开错误。
- `ocr_image` 作为来源类型可通过 capture 校验、持久化、列表筛选和详情展示。
- metadata 不包含图片 base64 或完整 raw text。

### Python

- 内部接口拒绝缺失或错误的服务认证。
- Pydantic 响应 schema 拒绝未知 status、空错误建议和越界 confidence。
- 超过 30 个候选时按优先级截断并返回 `CANDIDATE_LIMIT_REACHED`。
- Prompt 测试覆盖列表、圈选/高亮、无标记段落、拼写错误、短语和无可用词图片。
- workflow 只调用配置的图片模型，结构化失败最多重试一次。
- 日志和 trace 不包含图片内容。

### 端到端

- 使用模拟模型完成图片上传、候选展示、错误确认、主题选择和卡片沉淀。
- 验证生成后的来源为 `ocr_image`，重复词仍执行现有来源合并。
- 本地使用真实图片模型测试一张单词列表、一张含拼写错误的笔记和一张无标记段落。
- 验证 P50、P95、候选采用、沉淀完成、24 小时首次学习和增量成本事件可以关联到同一业务 trace，但不包含图片正文。
- 文本录入原有 E2E 继续通过。
- 前端运行 `npm run build` 和相关 Playwright 测试；Java、Python 运行各自目标测试。

## 发布与回滚

图片识别入口由前端功能开关控制。Java 和 Python 接口先部署并通过内部契约测试，再打开前端入口。模型或 Python 服务不可用时，文本录入保持可用，图片标签页显示当前不可识别，不影响单词库浏览和卡片生成。

回滚时关闭图片识别功能开关即可。新增的 `ocr_image` 来源值是向后兼容字符串，不需要删除历史来源。前端回滚后，历史图片来源卡片仍可在列表和详情中读取。

## 文档影响

实施完成时需要同步更新：

- `docs/architecture/vocabulary-deposition.md`：增加图片导入调用链和来源类型。
- 单词公开 API 文档：增加图片识别 endpoint 与错误码。
- AI 文档：增加图片识别 Prompt、结构化输出和模型配置。
- 本地运行文档：补充图片模型所需环境变量和真实模型验收步骤。

本设计规格不加入 VitePress 主导航，作为实施计划的审阅依据；架构、API、AI 和运行文档在功能完成后成为长期权威说明。
