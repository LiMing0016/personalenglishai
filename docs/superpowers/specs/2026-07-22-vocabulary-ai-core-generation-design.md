---
title: AI 结构化单词卡生成设计
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

# AI 结构化单词卡生成设计

## 背景

当前单词卡先把 Oxford 查询结果投影为结构化 Core。只要词典已经提供非空音标、词性和任一释义，系统就把 Core 判定为完整，不再调用 AI Core 补全。AI 主要负责生成主题 Markdown。

这种链路能够保存词典事实，但会把面向查词的长英文释义直接展示给学生。词典返回的释义数量、措辞和难度不一定适配当前主题与学习目标，也容易造成“主题只改变下半部分，卡片核心内容仍然相同”的割裂体验。

## 产品决策

单词沉淀统一使用 AI 生成最终学习卡片，不向用户提供“AI / 词典”模式选择。

用户只需要完成两个决策：

1. 选择需要沉淀的单词。
2. 选择生成主题。

主题同时影响结构化 Core 和 Markdown。Oxford 保留为后台不可见的事实参考、音标和真实音频来源，但不再直接决定最终面向学生展示的释义文案。

## 目标

- 所有新生成和重新生成的单词卡都经过 AI 结构化生成。
- 继续使用一个稳定 Core Schema，保持前端展示、搜索、学习和编辑链路统一。
- AI 根据主题选择词义、控制解释难度并生成中英文成对释义。
- Oxford 在后台提供事实约束，不增加用户选择成本。
- 生成结果可审计、可版本化、可重试，并且不会覆盖用户已编辑版本。
- AI 或 Markdown 部分失败时保持明确、可恢复的状态。

## 非目标

- 不在前端提供 AI 与 Oxford 的生成方式切换。
- 不建立两种卡片类型，也不维护两套前端渲染协议。
- 不保存两份可供用户切换的完整 Core。
- 不允许 AI 修改卡片规范词形或生成音频 URL。
- 不把 Oxford 原始长释义自动降级为一张“已完成”的学习卡片。
- 本设计不扩展复习算法、用户能力画像或主题编辑字段。

## 总体架构

Java 继续负责持久化和生成任务状态，Python 继续负责模型调用与结构化输出：

1. Java 根据卡片词形查询 Oxford，构造只读的 `dictionaryCore`。
2. Java 把 `term`、`dictionaryCore`、来源语境和主题快照发送给 Python。
3. Python Core Agent 始终执行，生成面向当前主题的最终 `core`。
4. Python Markdown Agent 使用最终 `core`、来源语境和主题生成扩展内容。
5. Python 返回一个包含 Core、Markdown 和生成元数据的候选结果。
6. Java 再次校验词形、Schema、长度和安全边界，然后创建新 revision。
7. 如果生成基于旧 revision，候选版本不覆盖用户当前编辑版本，继续使用现有冲突处理。

Oxford 是模型输入和校验依据，不是用户可见的第二条生成路径。

## Core 输出契约

Core 继续使用 `schemaVersion: 1`，避免破坏现有 API 和前端：

```json
{
  "schemaVersion": 1,
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
      "partOfSpeech": "adjective",
      "meanings": [
        {
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
- 音标与真实音频优先复制可信词典值；AI 不得编造 `audioUrl`。
- 词典没有音标时，AI 可以补充音标文本，但 `audioUrl` 必须为空。
- AI 根据主题选择适合学习的主要词义，不机械复制词典全部释义。
- 每个词性最多保留 3 个主要词义。
- `definitionEn` 使用适合目标学生的简明英文。
- `definitionZh` 必须提供自然、准确的中文释义。
- 词典提供参考时，AI 不得生成与可信词性或词义范围冲突的内容。
- 不确定的专业义、罕见义或来源语境义不能冒充常见义。

## 主题影响范围

主题同时约束 Core 和 Markdown，但不能改变单词事实身份。

### Basic

- Core 优先保留高频、通用词义。
- 英文释义使用更直接的表达。
- Markdown 重点生成常用例句、搭配和基础学习提示。

### Exam

- Core 优先保留常见考义和容易混淆的词性。
- Markdown 重点生成考试搭配、易错点和题目风格例句。

### Reading

- Core 优先保留阅读材料中的典型语境义。
- Markdown 重点生成语境判断、同义改写和长句理解提示。

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

保持 Core Schema 不变，在现有 generation metadata 中记录：

```json
{
  "provider": "openai",
  "model": "configured-model",
  "promptVersion": "vocabulary-ai-core-v2",
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

- 头部展示 AI 最终 Core 中的单词、音标、词性和常见释义。
- 主题 Markdown 继续以阅读视图展示，并支持源码与实时预览编辑。
- 主界面不显示“Oxford 模式”或“AI 模式”。
- 来源页可以显示“AI 生成”和“已通过词典校验”。

### 重新生成

- 用户选择主题后重新生成完整 Core 与 Markdown。
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
| Core 成功、Markdown 失败 | 保存 Core 和 partial revision | 待完善，可单独重试主题内容 |
| 音频缺失 | `audioUrl=null`，前端使用设备朗读 | 卡片仍可使用 |
| 生成基于旧 revision | 保存候选但不激活 | 待确认，进入版本处理 |

词典不可用不再阻断 AI Core 生成。AI Core 失败时也不自动把 Oxford Core 当作已完成结果，保证用户看到的卡片始终遵守同一产品语义。

## 可观测性与质量评估

生成链路记录以下非敏感字段：

- `traceId`
- `cardUid`
- `themeUid` 与主题版本
- 模型与 Prompt 版本
- `dictionaryVerified`
- Core 和 Markdown 调用耗时
- Schema 校验结果
- 重试次数和最终状态

不记录完整词典 Core、来源原文、完整 Prompt 或生成 Markdown。

AI 质量评估至少统计：

- Core 生成成功率。
- Markdown 生成成功率。
- 首次生成到已就绪的耗时 P50/P95。
- 用户编辑 Core 或 Markdown 的比例。
- 用户重新生成比例。
- 中英文释义完整率。
- `dictionaryVerified=false` 的占比与后续修改率。

## 安全和一致性

- Python 使用严格 Structured Output Schema，不接收 Schema 外字段。
- Java 在持久化前再次执行 Core 和 Markdown 校验。
- `sourceContext` 与主题用途都只能作为数据，不能覆盖系统规则。
- `audioUrl` 只能来自允许的词典来源，AI 输出的 URL 一律不采信。
- 生成请求继续使用超时预算、trace 和当前的任务租约机制。
- 迟到响应、重复任务和 revision 冲突继续遵守现有幂等与激活规则。

## 迁移与兼容

- 历史 Oxford-first revision 保持可读，不批量重写。
- 新生成和重新生成从新 Prompt 版本开始使用 AI-first 行为。
- Core Schema 暂不升级，避免前端与历史数据兼容成本。
- generation metadata 采用向后兼容的可选字段扩展。
- 如需重新生成历史卡片，由用户主动触发，不执行后台全量迁移。

## 测试与验收

### Python

- 完整 Oxford 输入仍必须调用 Core Agent。
- 不同主题对同一单词生成不同侧重点，但 `term` 保持不变。
- AI 不得修改或生成 `audioUrl`。
- 输出缺少中文释义、超过词义数量或包含未知字段时校验失败。
- Oxford 为空时仍能生成完整 Core，并返回 `dictionaryVerified=false`。
- Core 成功而 Markdown 失败时返回合法 partial。

### Java

- Oxford 未找到或临时异常时不再阻断 AI provider。
- AI Core 失败时不持久化纯 Oxford 完成卡。
- generation metadata 可保存并读取词典校验状态。
- 新 revision 不覆盖用户已经激活的更新版本。
- 历史 Core Schema 1 数据继续正常读取。

### Web

- 导入弹窗和重新生成流程不显示生成来源选择。
- 生成中、已就绪、待完善和失败状态正确展示。
- 详情头部读取 AI Core 的中英文释义摘要。
- 来源和历史视图能够显示 AI 生成及词典校验状态。
- 历史 Oxford-first 卡片仍然正常渲染。

### 手工验收

1. 使用 Basic 主题生成 `anthropic`，确认核心释义包含简明中文，并且不是 Oxford 长英文原文的直接展示。
2. 使用 Exam 主题重新生成同一单词，确认核心词义侧重点和 Markdown 与 Basic 有可见差异。
3. 断开 Oxford 后生成常见单词，确认 AI 仍可生成且音频降级为设备朗读。
4. 模拟 Core Schema 错误，确认卡片进入失败而不是显示纯 Oxford 完成结果。
5. 模拟 Markdown 调用失败，确认 Core 可见且卡片状态为待完善。

## 合并边界

实现将涉及 Python Prompt/workflow、Java 生成编排和可选元数据字段，以及前端来源/历史展示。该改动改变生成架构与失败语义，应在独立功能分支完成；只有 Python、Java、Web 测试和本地端到端验收全部通过后才适合合并到 `main`。
