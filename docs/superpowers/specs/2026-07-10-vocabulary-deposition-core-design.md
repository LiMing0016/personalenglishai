---
title: 单词沉淀内核设计
status: draft
owner: product
last_updated: 2026-07-10
review_cycle: on-change
related_code:
  - web/src/views/VocabularyView.vue
  - web/src/api/dictionary.ts
  - backend/src/main/java/com/personalenglishai/backend/controller/DictionaryController.java
  - backend/src/main/java/com/personalenglishai/backend/service/dictionary/DictionaryWordStateService.java
related_docs:
  - docs/architecture/dictionary-oxford.md
  - docs/superpowers/specs/2026-06-24-learning-asset-canvas-design.md
---

# 单词沉淀内核设计

## 当前结论

第一阶段只建设单词模块内部的沉淀闭环。用户在 `/app/vocabulary` 中手动输入、批量粘贴或收藏词典单词后，系统立即保存原始词条，随后在后台完成标准化、去重、词典补全和 AI 模板生成。用户可以离开页面，稍后回来查看、编辑、重新生成或处理真实冲突。

第一阶段只启用 `manual` 和 `dictionary` 两种来源。AI 会话、PDF、网页划词、浏览器扩展和系统分享菜单不在本阶段实现，但后续必须通过同一个捕获接口进入沉淀管线。

产品原则是：用户负责选择单词，系统负责生产和维护单词卡。正常单词不要求用户逐项确认；只有无法安全自动处理的输入或版本冲突才进入“需要处理”。

## 背景

当前单词页已经具备在线查词、词典收藏、收藏列表和前端模板演示，但还存在以下断点：

- 词典收藏只是用户状态，不是一张可版本化、可编辑的学习卡。
- 收藏列表可能缺少音标、释义预览和结构化模板内容。
- 模板字段、标题和背词计划主要保存在前端内存，刷新后不能作为可靠资产恢复。
- AI 整理是前端演示行为，没有持久化任务、失败重试和版本保护。
- 重复单词只能依赖前端归一化，无法跨来源合并语境和来源记录。

沉淀内核要先解决这些资产问题，再接入 AI 会话、PDF 和外部网页。否则每增加一个入口都会复制一套不完整的数据逻辑。

## 目标与非目标

### 目标

- 在单词模块内部完成手动输入、批量粘贴和词典收藏沉淀。
- 用户提交后立即得到可靠的已保存状态，不等待 AI。
- 后台异步生成结构化单词卡，失败可重试且不丢原始词。
- 同一用户的同一标准词条自动去重，只增加来源，不新增重复卡。
- 提供多种内置模板、默认模板和单卡重新生成能力。
- 用户编辑内容不被后台任务静默覆盖。
- 为后续 AI 会话、PDF 和外部平台预留统一来源契约。

### 非目标

- AI 会话选词和批量生词建议。
- PDF、文章和网页划词。
- 浏览器扩展、系统分享扩展和第三方平台连接器。
- 闪卡复习、间隔重复、拼写训练和学习统计。
- 用户创建模板、模板市场和模板分享。
- 自动合并不同词性、不同义项或形态变化词。
- iOS 端实现和跨端离线同步。

## 用户体验原则

| 原则 | 规则 |
| --- | --- |
| 捕获优先 | 单个词沉淀不超过一次主要点击；提交不等待 AI 返回 |
| 自动处理优先 | 正常输入自动入库，只有异常和真实冲突需要用户选择 |
| 原始数据不丢 | AI、词典或网络失败时仍保留用户输入和来源 |
| 用户内容优先 | 用户编辑过的字段永远不会被后台生成结果静默覆盖 |
| 来源可追溯 | 每次捕获都记录来源类型、上下文和时间 |
| 版本可恢复 | AI 生成、重新生成、用户编辑和冲突合并形成版本记录 |
| 状态可理解 | 使用“正在生成、已完成、需要处理、生成失败”等用户语言，不暴露任务实现细节 |

## 第一阶段用户流程

### 手动新增

1. 用户进入“单词沉淀”。
2. 点击 `新增单词`。
3. 输入一个单词、短语，或按换行/英文逗号粘贴多个词条。
4. 系统默认使用用户上次选择的模板，用户可以临时切换。
5. 用户点击 `加入单词库`。
6. 后端在一个事务中保存捕获记录、创建或命中单词卡，并创建生成任务。
7. 前端立即显示“已收下”，列表中出现 `正在生成` 状态。
8. 后台完成词典补全和 AI 生成后，卡片自动更新为 `已完成`。

### 词典收藏

1. 用户在搜索页完成查词。
2. 点击收藏按钮。
3. 系统继续更新现有 `user_dictionary_word_state.favorite`。
4. 同时把词典查询结果作为 `dictionary` 来源提交给沉淀内核。
5. 如果卡片已存在，只追加来源和最新捕获时间。
6. 如果卡片不存在，创建卡片和生成任务。
7. 收藏成功不等待 AI，允许用户立即继续查词。

### 查看与编辑

1. 用户在单词库列表查看生成状态、模板、来源数量和更新时间。
2. 选择单词后，右侧详情展示当前有效版本。
3. 用户可以编辑生成字段并保存，保存产生一个用户版本。
4. 用户可以选择其他模板并重新生成，重新生成产生 AI 候选版本。
5. 如果当前版本包含用户编辑，AI 候选不能直接覆盖；用户选择保留当前、使用新版或合并字段。

## 页面结构

### 单词沉淀主页

沿用当前 `/app/vocabulary?tab=collection`，调整为单词库主页面：

- 顶部主操作：`新增单词`。
- 状态筛选：全部、正在生成、已完成、需要处理、生成失败。
- 来源筛选：全部来源、手动输入、词典收藏；后续追加 AI 对话、PDF、网页等选项。
- 搜索：匹配原始词形、标准词形和释义。
- 排序：最近沉淀、A-Z、最近更新。
- 列表字段：标准词形、音标、核心释义、模板、状态、来源数量、更新时间。

列表采用紧凑行，不为每个单词使用大面积卡片。生成中的词条显示确定的状态和重试入口，不使用“暂无释义”冒充已完成内容。

### 新增单词面板

新增单词使用模态框或右侧抽屉，包含：

- 多行输入框。
- 已识别词条数量。
- 模板选择。
- `加入单词库` 主按钮。

输入规则：

- 单个词或短语可直接提交。
- 多个词条使用换行或英文逗号分隔。
- 去除首尾空白和包围词条的常见标点。
- 空项直接忽略。
- 单项超过 120 个字符时进入 `需要处理`，不自动生成。
- 单次最多提交 100 项，超过时在提交前提示拆分。

### 单词详情面板

右侧详情分为：

- 基础信息：标准词形、展示词形、语言、音标、词性。
- 模板内容：由当前模板定义的结构化字段。
- 来源：所有捕获记录及其时间、上下文和来源类型。
- 版本：当前版本、历史版本和冲突候选。
- 操作：编辑、重新生成、切换模板、重试、删除。

第一阶段不在详情页混入背词计划和学习统计。

## 模板设计

第一阶段提供三种内置模板。模板由后端注册表定义，前端通过接口读取展示信息和字段契约，避免前后端各维护一份模板结构。

### 基础单词卡 `basic`

- 音标
- 词性
- 核心中文释义
- 简明英文释义
- 常用例句及翻译
- 常见搭配
- 我的笔记

### 考试词汇卡 `exam`

- 基础单词卡字段
- 高频义
- 词根与词缀
- 近义词和易混词
- 常见考试陷阱
- 词形变化与词族

### 阅读语境卡 `reading`

- 基础单词卡字段
- 来源语境
- 当前语境中的含义
- 语境搭配
- 可替换表达

模板规则：

- 新用户默认 `basic`。
- 用户最近一次选择保存在后端偏好中，跨刷新恢复。
- 提交时可以临时选择模板。
- 同一单词重复捕获时不自动改变现有模板。
- 切换模板通过重新生成产生候选版本，不破坏当前版本。
- 缺少来源语境时，`reading` 模板保留语境字段为空，不编造来源原句。

## 数据模型

公共词典内容继续由现有 `dictionary_*` 表负责。用户收藏和查询状态继续由 `user_dictionary_word_state` 负责。用户生成的单词卡采用独立表，禁止把 AI 生成内容写入公共词典表。

### `vocabulary_card`

```text
id
card_uid
user_id
language
original_term
normalized_term
display_term
template_key
template_version
status                 captured | generating | ready | needs_review | failed
active_revision_uid
last_captured_at
created_at
updated_at
deleted_at
```

约束：

```text
UNIQUE (user_id, language, normalized_term)
```

### `vocabulary_card_source`

每次捕获都保存为独立来源记录：

```text
id
source_uid
card_uid
user_id
source_type            manual | dictionary | assistant | pdf | web | external
source_ref
source_title
source_url
context_text
raw_term
idempotency_key
captured_at
metadata_json
created_at
```

第一阶段只写入 `manual` 和 `dictionary`。后续入口只能新增来源适配器，不能绕过此表直接写卡片正文。

### `vocabulary_card_revision`

```text
id
revision_uid
card_uid
base_revision_uid
author_type            ai | user | system_merge
template_key
template_version
content_json
change_summary
created_at
```

`content_json` 保存结构化字段，不把整张卡只保存成 Markdown。前端可以按模板渲染，后续也能用于复习、搜索和导出。用户自由笔记作为明确字段保留，不让 AI 猜测或覆盖。

### `user_vocabulary_preference`

```text
id
user_id
default_template_key
created_at
updated_at
```

每个用户只保存一条单词模块偏好。模板不存在或已停用时回退到 `basic`，不影响已经生成的历史版本。

### `vocabulary_generation_job`

```text
id
job_uid
card_uid
base_revision_uid
template_key
template_version
status                 pending | running | succeeded | failed | cancelled
attempt_count
request_json
result_revision_uid
error_code
error_message
available_at
started_at
finished_at
created_at
updated_at
```

捕获事务必须先提交卡片、来源和 `pending` 任务，再向用户返回成功。后台工作线程从数据库领取任务，避免进程重启导致任务丢失。

## 标准化与去重

第一阶段只自动合并高置信度的精确重复，不自动合并词义或形态相似词。

标准化规则：

- Unicode NFKC 规范化。
- 转为小写用于比较。
- 去除首尾空白并折叠连续空格。
- 去除包围词条的引号、括号和句末标点。
- 去除词典展示使用的音节分隔点和软连字符，例如 `in·nova·tive` 归一为 `innovative`。
- 保留词内连字符和撇号。
- 语言默认 `en`，英式/美式词典偏好不拆成两张卡。

去重行为：

- 命中 `(user_id, language, normalized_term)` 时复用现有卡片。
- 命中已软删除卡片时恢复该卡片并追加来源，不创建新的主记录。
- 每次重复捕获都新增来源记录，并更新 `last_captured_at`。
- 来源记录使用调用方提供的 `idempotency_key` 去重；相同捕获请求重试时不重复插入来源。
- 重复捕获不自动重新生成，不修改当前模板和用户编辑。
- 用户可以在详情页主动使用新增语境重新生成候选版本。

以下情况进入 `needs_review`，不自动合并：

- 单项输入超过 120 个字符。
- 无法识别有效英文词或短语。
- 词典和 AI 对标准词形给出冲突结果。
- 未来来源适配器提交的语言不明确。

## 异步生成管线

1. **Capture**：保存原始词和来源。
2. **Normalize**：生成标准比较键，完成精确去重。
3. **Enrich**：优先查询本地词典；本地缺失时复用现有词典查询服务。
4. **Generate**：按模板版本调用 AI，要求返回严格结构化 JSON。
5. **Validate**：校验字段类型、词条一致性和来源语境引用。
6. **Commit revision**：保存 AI 版本；无冲突时设为当前有效版本。
7. **Notify**：更新卡片状态，前端轮询或刷新后可读取最新结果。

卡片状态转换：

```text
captured -> generating -> ready
captured -> needs_review
generating -> failed -> generating
ready -> generating -> ready
```

`ready -> generating` 只发生在用户主动重新生成时；重新生成期间仍展示当前有效版本，并额外显示生成状态。

生成规则：

- 词典已有的音标、词性、释义和例句优先于 AI 编造内容。
- 后台补全调用词典内容服务时不增加用户的 `lookup_count`，避免系统任务污染查词统计。
- AI 主要负责模板扩展字段、内容压缩和学习解释。
- 来源原句只能来自捕获上下文，不允许 AI 伪造为原始来源。
- 生成结果必须通过 JSON Schema 校验后才能成为版本。
- 相同标准词条、模板版本和词典内容版本可以复用缓存，降低成本。

## 冲突与版本规则

系统采用类似 Git 的版本思想，但不向用户展示行级差异或技术术语。

### 自动处理

- 重复捕获：自动追加来源。
- 当前版本没有用户编辑：AI 重新生成成功后可直接成为当前版本。
- 生成任务基于旧版本完成，但当前版本只是另一份 AI 版本：保留新旧版本，选择最新成功任务为当前版本。

### 需要用户选择

当生成任务的 `base_revision_uid` 已不是当前版本，并且当前版本由用户编辑产生时，创建冲突候选。用户看到：

- `保留当前内容`
- `使用 AI 新版本`
- `逐字段合并`

逐字段合并只展示可读字段，例如释义、例句、搭配和笔记，不展示 JSON 差异。默认推荐保留用户版本。解决冲突后创建 `system_merge` 版本，原版本仍可恢复。

## API 契约方向

### 捕获单词

```text
POST /api/vocabulary/captures
```

请求：

```json
{
  "clientRequestId": "client-generated-uuid",
  "terms": ["innovative", "sustainable"],
  "language": "en",
  "templateKey": "basic",
  "source": {
    "type": "manual",
    "sourceRef": null,
    "sourceTitle": "手动输入",
    "sourceUrl": null,
    "contextText": null,
    "metadata": {}
  }
}
```

后端按 `clientRequestId + 输入项序号` 生成来源幂等键。调用方重试同一个请求时必须复用 `clientRequestId`。

响应按输入项返回：

```json
{
  "items": [
    {
      "term": "innovative",
      "cardUid": "card-uid",
      "action": "created",
      "status": "generating"
    }
  ]
}
```

`action` 为 `created`、`source_merged`、`needs_review` 或 `rejected`。

### 卡片和模板

```text
GET    /api/vocabulary/templates
GET    /api/vocabulary/cards?keyword=&status=&sourceType=&page=&size=
GET    /api/vocabulary/cards/{cardUid}
PUT    /api/vocabulary/cards/{cardUid}
DELETE /api/vocabulary/cards/{cardUid}
POST   /api/vocabulary/cards/{cardUid}/regenerate
POST   /api/vocabulary/cards/{cardUid}/retry
GET    /api/vocabulary/cards/{cardUid}/revisions
POST   /api/vocabulary/cards/{cardUid}/conflicts/{revisionUid}/resolve
```

更新卡片时必须携带 `baseRevisionUid`。版本已变化时返回 `409` 和当前版本摘要，前端进入冲突选择，不能最后写入者静默覆盖。

### 词典收藏兼容

保留现有接口：

```text
POST /api/dictionary/words/{word}/favorite
```

收藏成功后由后端服务层调用沉淀捕获服务，使用词典查询结果创建 `dictionary` 来源。取消收藏只更新收藏状态，不自动删除已经生成的单词卡；用户需要在单词库明确删除资产。

## 前后端职责

| 模块 | 职责 |
| --- | --- |
| `VocabularyView.vue` | 页面编排、筛选、详情选择和路由状态；实现时应拆出新增面板、列表和详情组件，避免继续扩大单文件 |
| `VocabularyCapturePanel` | 输入解析预览、模板选择和提交 |
| `VocabularyCardList` | 列表、状态、筛选、分页和空状态 |
| `VocabularyCardInspector` | 详情、编辑、来源、版本和冲突操作 |
| 前端 Query 层 | 卡片列表、详情、轮询和请求生命周期 |
| 前端 Pinia | 仅承载新增面板临时状态和用户模板偏好；服务端卡片不复制为长期本地真源 |
| `VocabularyCaptureController` | 捕获、列表、详情、版本和冲突 API |
| `VocabularyCaptureService` | 事务保存、标准化、幂等去重和来源合并 |
| `VocabularyGenerationWorker` | 领取任务、词典补全、AI 生成、校验、版本提交和重试 |
| `VocabularyTemplateRegistry` | 内置模板定义、版本和 JSON Schema |

## 状态与错误处理

| 场景 | 系统行为 | 用户体验 |
| --- | --- | --- |
| 捕获成功、AI 未完成 | 卡片和任务已持久化 | 立即显示“已收下”，列表显示正在生成 |
| 本地词典缺失 | 尝试现有远程词典，再执行 AI | 卡片继续生成，不要求用户补字段 |
| AI 超时或限流 | 任务失败并记录可重试时间 | 保留原始词，显示失败和重试按钮 |
| 页面刷新 | 从后端恢复卡片和任务状态 | 不丢词，不重复创建任务 |
| 重复提交 | 幂等合并来源 | 显示“已存在，已追加来源” |
| 用户编辑期间 AI 完成 | 保存候选版本，不覆盖当前用户版本 | 显示有一个新版本待选择 |
| JSON 校验失败 | 不提交版本，任务失败 | 显示生成失败，可重试 |
| 批量部分失败 | 每项独立返回结果 | 成功项继续入库，失败项可单独处理 |
| 未登录 | 不创建卡片 | 引导登录并保留输入面板内容 |

## 隐私、内容与成本边界

- 第一阶段 `manual` 和 `dictionary` 来源不上传整篇文档或整段会话。
- `context_text` 只保存生成卡片所需的最小上下文，并限制长度。
- 后续接入外部来源时，必须让用户明确触发捕获，不做后台全量扫描。
- AI 请求日志不得记录用户 token、账号信息或超出卡片所需的原文。
- 删除卡片时软删除卡片、来源和版本；后台任务必须取消或忽略软删除卡片。
- 使用本地词典优先、生成缓存和模板字段限长控制 AI 成本。

## 验收标准

### 功能

- 用户可以手动输入一个词并使用默认模板沉淀。
- 用户可以一次粘贴多个词条，系统按换行或英文逗号拆分。
- 提交后无需等待 AI，列表立即出现生成状态。
- 刷新页面后生成状态和原始词仍存在。
- 后台完成后，卡片展示完整结构化内容。
- 词典收藏自动创建或合并 `dictionary` 来源。
- 重复添加同一词条不会产生第二张卡。
- 重复来源不会重复插入。
- AI 失败可以重试，重试不会创建第二张卡。
- 用户编辑后，旧任务完成不能静默覆盖用户版本。
- 用户可以切换模板并生成候选版本。
- 用户可以查看来源和历史版本。
- 用户可以解决真实版本冲突。

### 质量

- 单个词沉淀主路径不超过一次主要点击。
- 捕获接口在不等待 AI 的情况下返回。
- 正常输入不需要用户逐字段确认。
- 列表状态不使用虚假释义或演示数据。
- 所有写接口按当前用户隔离数据。
- 批量捕获、生成任务和来源合并具备幂等测试。

### 验证

后端重点测试：

- 标准化和精确去重。
- 捕获事务完整性。
- 词典收藏到来源记录的兼容行为。
- 任务领取、失败重试和进程重启恢复。
- JSON Schema 校验。
- 用户版本优先和 `409` 冲突。
- 用户数据隔离和删除后的任务行为。

前端重点测试：

- 单个和批量输入解析。
- 默认模板恢复和临时切换。
- 生成中、完成、失败和需要处理状态。
- 列表筛选、分页和详情恢复。
- 编辑保存、重新生成和冲突选择。
- 刷新后状态从服务端恢复。

建议验证命令：

```bash
cd backend
./mvnw.cmd -q test
```

```bash
cd web
npm run build
```

## 后续来源接入

沉淀内核稳定后按以下顺序扩展：

1. AI 会话选词：用户主动沉淀，AI 可给出批量候选，但不自动保存全部单词。
2. PDF/文章选词：保存文档、页码、段落和最小语境。
3. 网页划词与浏览器扩展：调用同一捕获 API，来源类型为 `web`。
4. 系统分享菜单和第三方平台：使用短期授权和幂等捕获键。

这些入口不得创建新的单词卡数据模型，也不得绕过版本和来源记录。

## 与现有设计的关系

- `docs/architecture/dictionary-oxford.md` 继续定义公共词典和用户收藏状态；本文新增用户结构化单词卡和异步生成边界。
- `2026-06-24-learning-asset-canvas-design.md` 继续描述通用 AI 学习资产画布。其 AI 会话单词入口推迟到沉淀内核完成后，并应改为调用本文的捕获 API。
- 单词卡不再只以 Markdown 作为唯一真源。结构化 `content_json` 是后续模板渲染、复习、搜索和导出的基础；需要 Markdown 时由结构化内容生成展示结果。

## 文档与合并判断

本设计涉及数据模型、异步任务、API、版本冲突和词典收藏兼容，实施时需要同步更新：

- 数据库 schema 和迁移脚本。
- 单词沉淀 API 契约。
- AI 模板与 JSON Schema 文档。
- `dictionary-oxford.md` 中收藏到单词卡的行为说明。
- 本地启动和 AI Provider 配置说明（如新增配置）。

当前设计文档适合在 `codex/word-card-design` 分支单独提交。代码实现属于跨前后端的高风险功能，必须在该独立分支完成测试和人工回归后再评估合并到 `main`。
