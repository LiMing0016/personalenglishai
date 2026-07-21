---
title: AI 结构化单词卡与增量沉淀设计
status: approved
owner: vocabulary
last_updated: 2026-07-22
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreContentCodec.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java
  - python/ai_orchestrator/workflows/vocabulary_card_generation.py
  - python/ai_orchestrator/schemas/vocabulary_card.py
  - python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md
  - python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md
  - web/src/components/vocabulary/VocabularyCaptureDialog.vue
  - web/src/components/vocabulary/VocabularyCardInspector.vue
related_docs:
  - docs/architecture/vocabulary-deposition.md
  - docs/architecture/dictionary-oxford.md
  - docs/ai/vocabulary-theme-prompts.md
---

# AI 结构化单词卡与增量沉淀设计

## 背景

当前单词卡先把 Oxford 查询结果投影为结构化 Core。只要词典已经提供非空音标、词性和任一释义，系统就把 Core 判定为完整，不再调用 AI Core 补全。AI 主要负责生成一整段主题 Markdown。

这种链路能够保存词典事实，但会把面向查词的长英文释义直接展示给学生。词典返回的释义数量、措辞和难度不一定适配当前主题与学习目标，也容易造成“主题只改变下半部分，卡片核心内容仍然相同”的割裂体验。

## 产品决策

单词沉淀统一使用 AI 生成可编辑的基础学习卡片，不向用户提供“AI / 词典”模式选择。单词卡是用户持续维护的学习资产，AI 负责起草，不能被视为最终权威或自动替用户沉淀内容。

用户只需要完成两个决策：

1. 选择需要沉淀的单词。
2. 选择生成主题。

升级后的单词卡统一拆分为 `Lexical Core JSON + Card Blocks JSON`。Lexical Core 保存稳定词汇事实，Card Blocks 保存由主题生成且可以持续编辑、增删和排序的学习内容。用户笔记不再是独立数据层，而是 `type=note`、`format=markdown` 的 Card Block。

Oxford 保留为后台不可见的事实参考、音标和真实音频来源，但不再直接决定最终面向学生展示的释义文案。主题只决定 Card Blocks 的模块组合、学习侧重点和表达难度，不改变单词、音标、词性和核心词义等稳定事实。

第一次沉淀只生成适合入门使用的基础卡片。用户以后在学习助手中再次遇到同一单词时，系统先展示已有卡片和本次补充建议；只有用户显式点击“添加到单词卡”，才把新增词义、例句、搭配或笔记合并到原卡片。系统不为同一个用户重复创建同词卡片，也不为了补充一个知识点重新整理整张卡片。

## 目标

- 所有新生成和用户主动重新生成的单词卡都经过 AI 结构化生成。
- 使用稳定的 Lexical Core Schema 和 Card Blocks Schema，保持前端展示、搜索、学习、编辑和增量沉淀链路统一。
- 初次生成由 AI 提供 2 至 3 个高频或来源语境相关词义，并生成中英文成对释义；主题负责生成对应的 Card Blocks。
- Oxford 在后台提供事实约束，不增加用户选择成本。
- 生成结果可审计、可版本化、可重试，并且不会覆盖用户已编辑版本。
- AI Core 或 Card Blocks 部分失败时保持明确、可恢复的状态。
- 学习助手通过显式、增量、可审计的操作完善已有卡片，而不是静默写入或重新生成整卡。

## 非目标

- 不在前端提供 AI 与 Oxford 的生成方式切换。
- 不建立两种卡片类型，也不维护两套前端渲染协议。
- 不保存两份可供用户切换的完整 Core。
- 不把全部卡片内容重新合并为一个不可拆分的 Markdown 字符串。
- 不允许 AI 修改卡片规范词形或生成音频 URL。
- 不把 Oxford 原始长释义自动降级为一张“已完成”的学习卡片。
- 本设计不扩展复习算法、用户能力画像或主题编辑字段。
- 不自动把对话中出现、解释或翻译过的单词写入单词卡。
- 不要求初次卡片覆盖完整词典或所有罕见义。

## 总体架构

Java 继续负责持久化和生成任务状态，Python 继续负责模型调用与结构化输出：

1. Java 根据卡片词形查询 Oxford，构造只读的 `dictionaryCore`。
2. Java 把 `term`、`dictionaryCore`、来源语境和主题快照发送给 Python。
3. Python Core Agent 始终执行，生成稳定的 `lexicalCore`。
4. Python Card Blocks Agent 使用最终 `lexicalCore`、来源语境和主题生成结构化学习模块。
5. Python 返回一个包含 Lexical Core、Card Blocks 和生成元数据的候选结果。
6. Java 再次校验词形、Schema、长度和安全边界，然后创建新 revision。
7. 如果生成基于旧 revision，候选版本不覆盖用户当前编辑版本，继续使用现有冲突处理。

Oxford 是模型输入和校验依据，不是用户可见的第二条生成路径。

### 增量沉淀链路

1. 学习助手识别当前对话涉及的规范词形，但只进行查询，不产生写操作。
2. 后端按 `userId + language + normalizedTerm` 查询唯一主卡。
3. 如果主卡存在，学习助手展示当前卡片摘要以及本次建议增加的内容。
4. 如果主卡不存在，学习助手展示一张待创建的基础卡片建议。
5. 用户点击“添加到单词卡”后，学习助手提交带 `baseRevisionUid` 的增量命令。
6. 后端检查重复、权限和 revision 冲突，确定性地把增量应用到当前卡片并创建新 revision。
7. 写入成功后，对话内卡片组件更新为最新摘要；用户没有点击时不创建、不更新，也不记录待沉淀数据。

“添加到单词卡”是本次写入的唯一确认动作。补充建议在按钮出现前已经展示清楚，因此不再增加第二个确认弹窗。

## 卡片资产与所有权

单词卡分为稳定事实和用户学习内容，但仍以一张主卡和同一套 revision 历史呈现：

- `term`、可信音标、真实音频、基础词性和核心词义属于 Lexical Core。
- 例句、搭配、使用边界、易混辨析、记忆提示和个人笔记属于 Card Blocks。
- AI 可以起草或建议用户学习内容，只有初次明确沉淀或后续显式添加后才成为卡片资产。
- 用户可以编辑、删除、新增和调整 Card Blocks；用户笔记作为 Markdown 格式的普通 Block 插入任意位置。
- 用户编辑过的 Block 标记为 `userEdited=true` 并默认锁定，局部或整卡重新生成不能静默覆盖。
- 学习助手只能追加或修改本次明确确认的范围，不能顺带重写其他章节。
- 历史 revision 记录变更来自 `initial_ai`、`user_edit`、`assistant_addition` 或 `user_regeneration`。

同一用户、语言和规范词形只有一张主卡。来源可以有多条，对话中的再次添加会关联已有卡片，而不是再次执行初次建卡流程。

## 增量命令契约

学习助手不直接提交一份自由改写后的完整卡片，而是提交有限的结构化增量命令。例如：

```json
{
  "cardUid": "card_xxx",
  "baseRevisionUid": "revision_xxx",
  "sourceType": "assistant",
  "sourceRef": "assistant-message-opaque-id",
  "operations": [
    {
      "type": "addMeaning",
      "clientRef": "new_meaning_1",
      "partOfSpeech": "noun",
      "definitionEn": "a formal accusation that someone has committed a crime",
      "definitionZh": "指控；控罪"
    },
    {
      "type": "addExample",
      "meaningRef": "new_meaning_1",
      "sentence": "He was arrested on a charge of theft.",
      "translation": "他因盗窃罪名被捕。"
    }
  ]
}
```

首版支持 `addMeaning`、`addExample`、`addCollocation` 和 `addNote`。同一请求新增词义及其学习内容时，后续操作可以通过 `clientRef` 引用该词义；后端在创建 revision 时把它转换为稳定的 `meaning.id`。后端负责应用命令、校验边界和创建 revision；模型不能直接执行数据库写入。

重复判断分两层：

- 先按规范化后的词性、释义、例句或搭配做确定性精确去重。
- 疑似语义重复但文本不同的内容不自动覆盖，学习助手在提交前提示用户已有相近内容。

`sourceRef` 使用不包含对话原文的稳定不透明标识，并参与幂等判断，避免重复点击产生两次相同写入。

## Lexical Core 输出契约

新 revision 使用 `schemaVersion: 2`，为词义增加稳定标识，供 Card Blocks 精确关联：

```json
{
  "schemaVersion": 2,
  "term": "anthropic",
  "phonetics": [
    {
      "region": "uk",
      "text": "anˈθrɒpɪk",
      "audioUrl": "https://example.com/audio.mp3"
    }
  ],
  "senses": [
    {
      "id": "sense_1",
      "partOfSpeech": "adjective",
      "meanings": [
        {
          "id": "meaning_1",
          "definitionEn": "related to human existence or human influence",
          "definitionZh": "与人类存在或人类影响有关的"
        }
      ]
    }
  ]
}
```

约束如下：

- `term` 必须与请求词形完全一致。
- `sense.id` 和 `meaning.id` 在 revision 创建时由后端固化，后续 Block 更新和用户编辑不得改变现有标识。
- 音标与真实音频优先复制可信词典值；AI 不得编造 `audioUrl`。
- 词典没有音标时，AI 可以补充音标文本，但 `audioUrl` 必须为空。
- AI 选择适合初次学习的主要词义，不机械复制词典全部释义。
- 初次卡片默认生成 2 至 3 个重点词义；存在来源语境时，必须优先保留与来源语境匹配的词义，即使该词义并不高频。
- `definitionEn` 使用适合目标学生的简明英文。
- `definitionZh` 必须提供自然、准确的中文释义。
- 词典提供参考时，AI 不得生成与可信词性或词义范围冲突的内容。
- 不确定的专业义、罕见义或来源语境义不能冒充常见义。
- 初次卡片不是完整词典；未出现的专业义或罕见义由用户后续手工补充，或通过学习助手生成增量建议后显式加入。

## Card Blocks 输出契约

Card Blocks 使用独立的 `schemaVersion: 1`。所有主题学习内容和用户笔记都存放在同一个有序 Block 列表中，但不同 Block 保留明确类型、来源和内容结构：

```json
{
  "schemaVersion": 1,
  "blocks": [
    {
      "id": "block_examples_01",
      "type": "exampleList",
      "title": "常用例句",
      "meaningRefs": ["meaning_1"],
      "format": "structured",
      "content": {
        "items": [
          {
            "sentence": "The anthropic principle concerns the conditions required for observers to exist.",
            "translation": "人择原理关注观察者存在所需的条件。"
          }
        ]
      },
      "source": "ai",
      "sourceRef": null,
      "sortOrder": 10,
      "userEdited": false,
      "locked": false
    },
    {
      "id": "block_note_01",
      "type": "note",
      "title": "我的笔记",
      "meaningRefs": ["meaning_1"],
      "format": "markdown",
      "content": "这个词在环境科学文章中也常表示**人为造成的**。",
      "source": "user",
      "sourceRef": null,
      "sortOrder": 20,
      "userEdited": true,
      "locked": true
    }
  ]
}
```

首版 Block 类型限定为：

- `exampleList`：中英成对例句。
- `collocationList`：搭配及中文解释。
- `usageBoundary`：适用语境和不适用边界。
- `contrastTable`：近义词、易混词或语义侧重点对比。
- `memoryTip`：记忆方法和学习提示。
- `note`：用户或学习助手补充的自由笔记，内容允许 Markdown。

`legacyMarkdown` 仅用于历史数据的读取兼容，不能由 AI 或新建接口生成。

共同约束如下：

- `id` 在卡片生命周期内稳定，更新现有 Block 时不得重新生成。
- `meaningRefs` 只能引用当前 Lexical Core 中存在的词义标识；与全部词义相关时允许为空数组。
- `source` 仅允许 `ai`、`user`、`assistant` 和 `legacy`。
- AI 初次生成的 Block 默认 `userEdited=false`；用户首次修改后设置为 `true` 并默认 `locked=true`。
- 重新生成默认只替换未锁定的 AI Block。锁定 Block 必须由用户明确选择后才能替换。
- 用户可以新增、删除、编辑和排序所有 Block；删除与排序也创建 revision。
- 去重至少使用 `type + meaningRefs + normalized content fingerprint`。疑似语义重复不能自动覆盖。
- Card Blocks 不存储 Lexical Core 已有的单词、音标、词性和核心释义，避免详情页重复展示。

## 主题影响范围

主题定义 Card Blocks 的模块组合、学习侧重点和表达难度，但不能改变 Lexical Core。

### Basic

- 默认生成常用例句、搭配和基础学习提示。
- 内容使用直接表达，并优先关联高频、通用词义。

### Exam

- 重点生成考试搭配、易错点、易混辨析和题目风格例句。
- 通过 `meaningRefs` 关联常见考义，不改写 Lexical Core。

### Reading

- 重点生成语境判断、同义改写和长句理解提示。
- 通过 `meaningRefs` 关联阅读材料中的语境义，不改写 Lexical Core。

### Custom

- 主题用途只能影响学习侧重点和表达难度。
- 主题文本作为数据输入，不能覆盖系统规则、Schema 或卡片词形。

## Oxford 的后台职责

Oxford 保留以下职责：

- 校验输入词形是否存在。
- 提供可信音标和真实发音地址。
- 提供词性与词义范围，作为 AI 生成的事实依据。
- 为质量评估提供 `dictionaryVerified` 状态。

Oxford 不再承担以下职责：

- 直接决定学生看到的最终释义文案。
- 直接决定 Core 中保留多少个词义。
- 提供主题例句或学习提示。
- 在 AI 失败时自动生成一张已完成卡片。

## 生成元数据

在现有 generation metadata 中记录两层内容契约版本：

```json
{
  "provider": "openai",
  "model": "configured-model",
  "promptVersion": "vocabulary-card-blocks-v1",
  "coreSchemaVersion": 2,
  "cardBlocksSchemaVersion": 1,
  "modelCallCount": 2,
  "traceId": "trace-id",
  "dictionaryVerified": true,
  "dictionarySource": "oxford"
}
```

`dictionaryVerified` 表示本次生成是否获得有效词典参考，不表示 Oxford 文案被原样展示。字段只用于来源页、历史页、质量统计和排障，不在详情页头部强调。

## 前端交互

### 导入弹窗

- 保留文本、图片、候选词和主题选择。
- 不增加生成来源选择器。
- 主按钮继续使用“生成 N 张卡片”。
- 提交后列表显示稳定状态：正在生成、已就绪、待完善、生成失败。

### 单词卡详情

- 头部只展示 Lexical Core 中的单词、发音入口、音标、词性、常见释义、状态、主题和版本，不在正文重复这些字段。
- 释义区域使用“重点释义”，不暗示当前卡片覆盖全部词典内容。
- 提供“补充词义”入口，用户可以手工输入，也可以携带句子请求 AI 生成候选内容。
- Card Blocks 使用类型化阅读视图展示，支持按 Block 编辑、增删、排序和局部重新生成。
- `note` Block 使用 Markdown 编辑器；其他 Block 使用与内容结构匹配的表单，不暴露整卡 JSON。
- 阅读态弱化编辑控件，悬停或进入编辑态后显示单块操作；用户编辑过的 Block 显示已保护状态。
- 主界面不显示“Oxford 模式”或“AI 模式”。
- 来源页可以显示“AI 生成”和“已通过词典校验”。

### 学习助手

- 对话中解释单词不会自动修改单词卡。
- 已有卡片时，展示原卡片摘要和“本次将新增”的差异内容。
- 没有新增内容时显示“已在单词卡”，不提供重复写入按钮。
- 用户点击“添加到单词卡”后立即提交本次增量，不再弹出第二次确认。
- 成功后显示“已添加到单词卡”，并允许打开更新后的详情页。
- 写入失败时保留对话中的建议内容，允许重试，不静默丢失。

### 重新生成

- 用户选择主题后重新生成未锁定的 Card Blocks；Lexical Core 默认不随主题重新生成。
- 用户可以只重新生成当前 Block，也可以明确选择重新生成全部未锁定 Block。
- 重新生成创建新 revision。
- 当前版本包含用户编辑时，迟到的 AI 结果不能直接覆盖当前版本。
- 历史页保留主题、模型、Prompt 版本和词典校验状态。

## 失败与降级

| 场景 | 系统处理 | 用户状态 |
| --- | --- | --- |
| Oxford 返回有效结果 | 作为 AI 事实参考和音频来源 | 正常生成 |
| Oxford 未找到单词 | 允许 AI 继续生成，`dictionaryVerified=false` | 正常生成，后台记录未校验 |
| Oxford 服务异常 | 允许 AI 继续生成，记录可观测错误 | 正常生成或按 AI 结果失败 |
| AI Core 调用失败 | 不保存纯 Oxford 完成卡 | 生成失败，可重试 |
| AI Core Schema 校验失败 | 拒绝候选结果 | 生成失败，可重试 |
| AI 修改 `term` | 永久拒绝候选结果 | 生成失败 |
| Core 成功、Card Blocks 失败 | 保存 Core 和 partial revision | 待完善，可单独重试主题内容 |
| 音频缺失 | `audioUrl=null`，前端使用设备朗读 | 卡片仍可使用 |
| 生成基于旧 revision | 保存候选但不激活 | 待确认，进入版本处理 |
| 对话中只解释了单词 | 不执行任何卡片写入 | 对话正常继续 |
| 增量内容与现有内容完全重复 | 返回幂等成功，不创建重复 revision | 已在单词卡 |
| 增量命令基于旧 revision | 不覆盖最新卡片，返回冲突和最新摘要 | 更新后重试添加 |
| 用户未点击“添加到单词卡” | 不创建待办、草稿或来源记录 | 无额外状态 |

词典不可用不再阻断 AI Core 生成。AI Core 失败时也不自动把 Oxford Core 当作已完成结果，保证用户看到的卡片始终遵守同一产品语义。

## 可观测性与质量评估

生成链路记录以下非敏感字段：

- `traceId`
- `cardUid`
- `themeUid` 与主题版本
- 模型与 Prompt 版本
- `dictionaryVerified`
- Core 和 Card Blocks 调用耗时
- Schema 校验结果
- 重试次数和最终状态

不记录完整词典 Core、来源原文、完整 Prompt 或完整 Card Blocks 内容。

AI 质量评估至少统计：

- Core 生成成功率。
- Card Blocks 生成成功率。
- 首次生成到已就绪的耗时 P50/P95。
- 用户编辑 Core 或 Card Blocks 的比例。
- 各 Block 类型的编辑率、删除率和局部重新生成率。
- 用户笔记 Block 的新增率。
- 用户重新生成比例。
- 对话建议到“添加到单词卡”的采用率。
- 增量命令去重率、冲突率和写入成功率。
- 中英文释义完整率。
- `dictionaryVerified=false` 的占比与后续修改率。

## 安全和一致性

- Python 使用严格 Structured Output Schema，不接收 Schema 外字段。
- Java 在持久化前再次执行 Lexical Core 和 Card Blocks Schema 校验。
- `sourceContext` 与主题用途都只能作为数据，不能覆盖系统规则。
- `audioUrl` 只能来自允许的词典来源，AI 输出的 URL 一律不采信。
- 生成请求继续使用超时预算、trace 和当前的任务租约机制。
- 迟到响应、重复任务和 revision 冲突继续遵守现有幂等与激活规则。
- 学习助手的查询工具无副作用；只有“添加到单词卡”对应的写入工具允许创建 revision。
- 对话原文不进入普通日志或 generation metadata；只有用户确认采用的最小内容进入卡片 revision。

## 迁移与兼容

- 历史 Oxford-first revision 保持可读，不批量重写。
- 新生成和重新生成从新 Prompt 版本开始使用 AI-first 行为。
- 新生成的 Lexical Core 使用 Schema 2，为 sense 和 meaning 增加稳定 ID。
- 历史 Core Schema 1 保持可读；读取时使用 `revisionUid + 字段路径` 派生稳定引用，不回写历史 revision。
- 新增 Card Blocks Schema 1 和对应持久化字段；新 revision 不再把主题内容保存为单一 Markdown。
- 历史 `contentMarkdown` 在读取时投影为一个只读的 `legacyMarkdown` Block，用户主动编辑或重新生成后再转换为类型化 Card Blocks。
- 不批量解析或重写历史 Markdown，避免错误拆分和 revision 污染。
- generation metadata 采用向后兼容的可选字段扩展。
- 如需重新生成历史卡片，由用户主动触发，不执行后台全量迁移。

## 分阶段实施

本设计保持一个产品闭环，但按三个可独立验收的阶段交付：

1. **结构化基础卡片**：让完整 Oxford 输入也经过 AI Core Agent，并由 Card Blocks Agent 按主题生成类型化学习模块，完成失败降级、持久化和历史 Markdown 兼容。
2. **模块化编辑与增量写入**：支持 Block 级编辑、增删、排序、锁定和局部重新生成，并增加有限的增量命令、精确去重、幂等和 revision 冲突保护。
3. **学习助手接入**：在对话中展示已有卡片摘要和本次增量，只有用户点击“添加到单词卡”才把内容写入对应 Block 或创建新 Block。

每个阶段分别提交、测试和验收。阶段 1 不等待学习助手改造即可上线；阶段 2 的写入契约稳定后，阶段 3 才接入对话界面，避免两个子系统同时改变契约。

## 测试与验收

### Python

- 完整 Oxford 输入仍必须调用 Core Agent。
- 不同主题对同一 Lexical Core 生成不同 Card Blocks，但不能改写 `term`、音标、词性和核心词义。
- AI 不得修改或生成 `audioUrl`。
- 输出缺少中文释义、超过词义数量或包含未知字段时校验失败。
- Oxford 为空时仍能生成完整 Core，并返回 `dictionaryVerified=false`。
- Core 成功而 Card Blocks 失败时返回合法 partial。
- Card Blocks 包含未知类型、无效 `meaningRefs`、重复 Block ID 或不合法内容结构时校验失败。
- `note` Block 允许 Markdown，其他 Block 必须符合对应的结构化内容 Schema。
- 学习助手只输出允许的增量命令类型，不能输出任意数据库操作或完整卡片覆盖命令。

### Java

- Oxford 未找到或临时异常时不再阻断 AI provider。
- AI Core 失败时不持久化纯 Oxford 完成卡。
- generation metadata 可保存并读取词典校验状态。
- 新 revision 不覆盖用户已经激活的更新版本。
- 历史 Core Schema 1 数据继续正常读取，并能为旧词义提供当前 revision 内稳定的派生引用。
- Card Blocks 的编辑、增删、排序和锁定变更均创建 revision。
- 重新生成不覆盖 `userEdited=true` 或 `locked=true` 的 Block。
- 历史 `contentMarkdown` 能读取为 `legacyMarkdown` Block，且不会被后台自动改写。
- 同一用户、语言和规范词形只解析到一张主卡。
- 未确认的对话查询不产生写入。
- 增量命令执行精确去重、幂等检查和 revision 冲突保护。

### Web

- 导入弹窗和重新生成流程不显示生成来源选择。
- 生成中、已就绪、待完善和失败状态正确展示。
- 详情头部读取 AI Core 的中英文释义摘要。
- 详情页不会在 Card Blocks 中重复显示单词、音标、词性和核心词义。
- 用户可以逐块编辑、增删和排序 Card Blocks，并能在任意位置添加 Markdown 笔记 Block。
- 局部重新生成只更新目标 Block；受保护 Block 显示明确状态且不会被覆盖。
- 来源和历史视图能够显示 AI 生成及词典校验状态。
- 历史 Oxford-first 卡片仍然正常渲染。
- 学习助手展示已有卡片摘要、本次增量和单一“添加到单词卡”操作。
- 未点击按钮时不会出现已添加状态，也不会改变卡片版本。
- 重复内容显示“已在单词卡”，不会重复创建内容。

### 手工验收

1. 使用 Basic 主题生成 `anthropic`，确认核心释义包含简明中文，并且不是 Oxford 长英文原文的直接展示。
2. 使用 Exam 主题重新生成同一单词，确认 Lexical Core 不变，Card Blocks 的模块组合和学习侧重点与 Basic 有可见差异。
3. 断开 Oxford 后生成常见单词，确认 AI 仍可生成且音频降级为设备朗读。
4. 模拟 Core Schema 错误，确认卡片进入失败而不是显示纯 Oxford 完成结果。
5. 模拟 Card Blocks 调用失败，确认 Core 可见且卡片状态为待完善。
6. 在学习助手中解释已存在的单词但不点击添加，确认卡片 revision 不变。
7. 点击“添加到单词卡”，确认只增加本次词义或例句，并保留原卡内容。
8. 对同一条对话建议重复点击，确认不会产生重复内容或重复 revision。
9. 编辑一个 AI 例句 Block 后重新生成全部主题内容，确认该 Block 保持原样且显示已保护。
10. 在例句与搭配之间插入 Markdown 笔记，刷新并切换版本后确认内容和排序保持一致。

## 合并边界

实现将涉及 Python Prompt/workflow、Java 生成编排和可选元数据字段，以及前端来源/历史展示。该改动改变生成架构与失败语义，应在独立功能分支完成；只有 Python、Java、Web 测试和本地端到端验收全部通过后才适合合并到 `main`。
