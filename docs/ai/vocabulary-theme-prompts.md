---
title: 主题化单词卡 Prompt
status: active
owner: ai
last_updated: 2026-07-14
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyMarkdownPromptBuilder.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCoreFallbackGenerator.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java
  - python/ai_orchestrator/agents/vocabulary_card.py
  - python/ai_orchestrator/workflows/vocabulary_card_generation.py
  - python/ai_orchestrator/prompts/agent_instructions/vocabulary_core_fallback.md
  - python/ai_orchestrator/prompts/agent_instructions/vocabulary_card_markdown.md
related_docs:
  - docs/architecture/vocabulary-deposition.md
  - docs/architecture/dictionary-oxford.md
---

# 主题化单词卡 Prompt

## Python Prompt ownership and version

`basic-markdown-v1`、`exam-markdown-v1`、`reading-markdown-v1` 和 `custom-markdown-v1` 是主题快照中的策略 key。策略 key 映射到 Python Prompt 资产：`vocabulary_core_fallback` 负责只补齐缺失 core，`vocabulary_card_markdown` 负责按主题生成 Markdown；Java 只冻结主题 UID、版本和策略 key，不持有或拼接 Prompt 正文。

Prompt version 由 Python 根据本次实际解析到的 Prompt 返回，Java 不发送 Prompt version。本地 Prompt 使用仓库版本名；remote/hybrid 命中 OpenAI Prompt 时必须同时固定 Prompt ID 和 version，审计值包含实际 ID/version。Python 在 generation metadata 中返回 provider、model、Prompt version、模型调用次数和安全 trace ID；Java 仅把已验证的 metadata 保存到 `generation_metadata_json`。不要在日志、测试失败输出或人工验收记录中打印 Prompt 正文、词典 core、sourceContext、生成 Markdown 或原始模型输出。

## 当前结论

主题化单词卡把稳定的核心词典 JSON 与自由的主题 Markdown 分成两次受控处理。词典结果优先决定 core；只有词典缺少音标和释义时才调用结构化 core fallback。Markdown 调用只能扩展学习内容，不能修改单词身份或覆盖核心事实。

## 场景和目标

- 输入：规范词形、共享词典 core、第一条有效来源语境、不可变主题 revision。
- 输出：通过校验的 core 与最多 20,000 字符的 Markdown。
- 非目标：让主题用途充当系统指令、让 Markdown 重复核心释义、执行 HTML 或改变卡片身份。

## 四个策略 key

| `prompt_strategy_key` | 主题 | Markdown 侧重点 |
| --- | --- | --- |
| `basic-markdown-v1` | Basic | 常用例句和学习提示，不重复核心释义 |
| `exam-markdown-v1` | Exam | 考试考义、固定搭配、易错点和真题风格例句 |
| `reading-markdown-v1` | Reading | 语境义、句中作用、同义改写和上下文解释 |
| `custom-markdown-v1` | 用户主题 | 围绕用户用途说明，使用固定的基础章节骨架 |

策略 key 属于主题 revision 的不可变字段。增加或改变 Prompt 行为时创建新 key 或新主题版本，不原地改变旧 key 的语义。未知 strategy key 会在模型调用前永久失败，不生成 partial，也不回退到任意策略；修正主题快照后才能重新生成。

## 输入与词典优先级

1. 使用 `lookupWithoutUserState(term, "en-gb")` 查询共享词典，不带收藏和查询次数。
2. 把词典音标、词性和双语释义投影到 `schemaVersion: 1` core，并强制 `term` 等于卡片规范词形。
3. 词典结果优先；core 缺少非空音标，或缺少“非空词性 + 至少一条中英文释义”的 sense 时，才调用结构化 AI fallback。
4. fallback 只输出闭合 JSON schema，不输出 Markdown，也不能增加 schema 外字段。
5. core 通过身份、字段类型、数组数量和标量长度校验后，才进入 Markdown 调用；AI 不得改写 core。

词典查询本身异常时不绕过词典直接生成 core，而是记录 `DICTIONARY_LOOKUP_FAILED` 并按可重试失败处理。这样可区分“词典确实无内容”和“词典服务不可用”。

## Prompt 与安全结构化输入

Python workflow 使用 JSON 序列化模型输入。Markdown Agent 接收可信 `core`、`sourceContext` 和完整主题快照；core fallback Agent 接收请求 term、可信 `dictionaryCore` 与 `sourceContext`。Prompt 明确把 `sourceContext`、theme `purpose`、name 和 strategy key 都视为数据，不允许这些字段覆盖 Prompt、输出 schema、卡片 term 或安全规则。即使用途包含“忽略以上规则”、HTML、JSON 片段或代码围栏，也只能作为 JSON 字符串值参与生成。

Agent 不直接返回自由文本：Markdown 调用必须输出 `VocabularyMarkdownOutput` 结构化对象，实际 Markdown 只取 `contentMarkdown`；core fallback 必须输出 `VocabularyCoreFallbackOutput`。两类输出都经 Pydantic 和 Java 最终边界再次校验。

## Markdown 输出契约

- 只接受非空 Markdown；trim 后持久化。
- 最大长度是 20,000 字符，后端生成、缓存读取和用户保存均执行边界校验。
- 禁止原始 HTML 标签、声明和处理指令；前端以源码编辑器显示，不执行脚本、事件属性、iframe 或危险 URL。
- Markdown 不重复承担 term、音标、词性和核心释义；这些事实只从 `core_json` 读取。
- Java 回滚 provider 的生成温度为 `0.2`，最大输出 token 为 `1200`。只有 Java 回滚 provider 使用七天生成缓存：成功结果按主题 UID/version、core 和来源语境生成 cache key；Python provider 不读写该缓存。

## 日志与可观测性

日志记录标识和规模，不得记录原始 purpose 或 sourceContext，也不把完整 core 或 Markdown 写入 vocabulary 业务日志：

| 阶段 | 必备字段 | 说明 |
| --- | --- | --- |
| 词典/core/Markdown 降级 | `traceId`, `reasonType` | `traceId` 仅允许字母、数字、点、下划线和连字符，最长 80 |
| 缓存拒绝 | `traceId`, `themeUid` | 记录失效条目并驱逐，不输出缓存正文 |
| job 失败 | `jobUid`, `cardUid`, `code`, `attempt`, `terminal` | 区分重试与终态，错误消息持久化上限 1,000 字符 |
| lease 丢失 | `jobUid`, `cardUid`, `attempt` | 迟到结果只记录，不激活 revision |

OpenAI 客户端自己的 Prompt debug 必须保持默认关闭；临时启用时仍需遵守现有脱敏规则，不能把用户用途或来源上下文复制到普通业务日志。

## 失败模式

| 失败 | 系统响应 | 卡片状态 | 用户表现 |
| --- | --- | --- | --- |
| 主题 revision 缺失或版本字段不完整 | `INVALID_GENERATION_REQUEST`，永久失败 | 无新 active revision | 生成未完成，可更换有效主题 |
| 未知 strategy key | 模型调用前返回 `UNSUPPORTED_PROMPT_STRATEGY`，永久失败 | 不激活新 revision | 修正或更换有效主题后重新生成 |
| 词典查询异常 | `DICTIONARY_LOOKUP_FAILED`，可重试 | 保留已有卡或生成中 | 不展示技术错误串 |
| 词典不足且 core fallback 失败 | `CORE_CONTENT_UNAVAILABLE`，可重试 | 保留已有卡或生成中 | 生成未完成 |
| core schema 或 term 校验失败 | `INVALID_GENERATED_CONTENT`，永久失败 | 不写入无效 revision | 生成未完成 |
| Markdown 失败、为空、超长或含 HTML | 保留 validated core，标记 partial | `needs_review` | 核心内容可见，显示“主题内容待完善” |
| cache core 与当前词典 core 不一致 | 驱逐 cache 并重新生成 Markdown | 不直接改变状态 | 用户无感 |
| base revision 已被用户编辑 | 保存 AI candidate，不覆盖 active | `needs_review` | 进入版本冲突处理 |

Markdown 失败只降级扩展内容，不会重试整个 core 生成链路，也不会把有效 core 降级成空卡。用户可重新生成或手工编辑 Markdown；前端不得显示模型校验、Java 异常或 Axios 技术串。

## 评估和验收

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-theme-suite-32-bytes'
.\mvnw.cmd -q -Dtest=VocabularyMarkdownPromptBuilderTest,VocabularyCardGeneratorTest,VocabularyGenerationWorkerTest,VocabularyDepositionDocsTest test

cd ..\web
npx tsx --test "tests/vocabulary*.test.ts"
```

验收样例至少覆盖四个策略、JSON 字段提示注入、20,001 字符、原始 HTML、词典 core 优先、fallback、Markdown partial、poisoned cache 和 lease 丢失。

## 相关资料

- [单词沉淀架构](../architecture/vocabulary-deposition.md)
- [Oxford 词典集成](../architecture/dictionary-oxford.md)
- `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyMarkdownPromptBuilderTest.java`
- `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGeneratorTest.java`
