# Ability + Context Prompt 验收步骤（本地）

本文档合并两部分内容：
- Ability Prompt（能力画像注入）验收
- Context v1.2（上下文注入/指代识别/清洗）验收

## 0) 开启验收日志（默认关闭）

在 `backend/.env` 或运行环境中设置：

```properties
AI_PROMPT_DEBUG=true
```

说明：
- 关闭时（默认 `false`）不会输出 `[ABILITY_PROFILE]` / `[ABILITY_PROMPT]` / `[PROMPT_BUILD]`
- 开启后用于验证 Ability 与 Context 链路是否生效

## 1) Ability 验收（user_ability_profile）

### 1.1 插入测试能力画像（user_id=9999，四级，语法弱项）

```sql
INSERT INTO user_ability_profile (
  user_id, stage,
  task_score, coherence_score, grammar_score, vocabulary_score, structure_score, variety_score,
  assessed_score, sample_count, model_version, rubric_version
) VALUES (
  9999, 2,
  68.00, 62.00, 35.00, 64.00, 66.00, 61.00,
  59.00, 5, 'v1', 'v1'
)
ON DUPLICATE KEY UPDATE
  stage = VALUES(stage),
  task_score = VALUES(task_score),
  coherence_score = VALUES(coherence_score),
  grammar_score = VALUES(grammar_score),
  vocabulary_score = VALUES(vocabulary_score),
  structure_score = VALUES(structure_score),
  variety_score = VALUES(variety_score),
  assessed_score = VALUES(assessed_score),
  sample_count = VALUES(sample_count),
  model_version = VALUES(model_version),
  rubric_version = VALUES(rubric_version);
```

### 1.2 调一次 Chat 接口（必须让后端解析到 userId=9999）

前提：
- 使用能映射到 `userId=9999` 的 JWT（推荐）
- 或在本地开发链路里使用固定 mock 用户（若当前实现支持）

请求示例（`POST /api/ai/command`）：

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "帮我润色这句话并解释为什么这样改",
  "constraints": {
    "selectedText": "The chart show many people's choose restaurant habit."
  }
}
```

期望日志（开启 `AI_PROMPT_DEBUG=true`）：

```text
[ABILITY_PROFILE] userId=9999 stage=2 sampleCount=5 loaded=true
[ABILITY_PROMPT] userId=9999 stage=四级 gated=false weaknessTop=语法/连贯
[PROMPT_BUILD] userId=9999 abilityInjected=true ... policy=CHAT_V1_0218
```

### 1.3 将 sample_count 改为 0（触发 Ability 降级策略）

```sql
UPDATE user_ability_profile
SET sample_count = 0
WHERE user_id = 9999;
```

再次调用同样的 Chat 接口。

期望日志：

```text
[ABILITY_PROFILE] userId=9999 stage=2 sampleCount=0 loaded=true
[ABILITY_PROMPT] userId=9999 stage=四级 gated=true weaknessTop=
[PROMPT_BUILD] userId=9999 abilityInjected=true ... policy=CHAT_V1_0218
```

当前固定策略（已实现）：
- `sample_count <= 0` 时仍注入 Ability（`abilityInjected=true`）
- 进入“中性降级版”：不输出弱项/六维详情，仅输出样本不足提示 + 学段指导 + 控制规则

### 1.4 调整弱项维度并验证 weaknessTop 跟随变化

```sql
UPDATE user_ability_profile
SET sample_count = 5,
    grammar_score = 30.00,
    coherence_score = 28.00,
    task_score = 70.00,
    vocabulary_score = 66.00,
    structure_score = 68.00,
    variety_score = 65.00
WHERE user_id = 9999;
```

再次调用 Chat 接口，期望日志变化为：

```text
[ABILITY_PROMPT] userId=9999 stage=四级 gated=false weaknessTop=连贯/语法
```

### 1.5 无记录场景（必须不报错）

```sql
DELETE FROM user_ability_profile WHERE user_id = 9999;
```

再次调用 Chat 接口，期望日志：

```text
[ABILITY_PROFILE] userId=9999 stage=null sampleCount=null loaded=false
[PROMPT_BUILD] userId=9999 abilityInjected=false ... policy=CHAT_V1_0218
```

并且接口正常返回，不报错。

---

## 2) Context v1.2 验收（指代识别 + 清洗 + 三开关）

### 2.1 当前实现要点（便于验收对照）

已实现：
- `ContextDecision` 三开关：
  - `injectSelectedContext`
  - `injectConversationContext`
  - `injectDraftContext`
- `ReferenceResolver` 指代识别（规则版）：
  - `ABOVE`
  - `LAST_ASSISTANT`
  - `PARAGRAPH_N`
  - `SENTENCE_N`
  - `THIS_WORD`
- `recentMessages` 清洗：
  - 过滤低信息消息（如 `ok/thanks/好的/谢谢/嗯/收到`）
  - 过滤连续重复消息（同 role + 同内容）
- Context 来源优先级提示：
  - `当前轮用户输入 > selectedText > recentMessages > draftText`
- Context/Task 操作边界提示（`target=selectedText` 时）

### 2.2 Context 相关配置项（默认值）

```properties
AI_PROMPT_CONTEXT_SELECTED_TEXT_MAX=1200
AI_PROMPT_CONTEXT_RECENT_EACH_MAX=200
AI_PROMPT_CONTEXT_RECENT_TURNS=8
AI_PROMPT_CONTEXT_DRAFT_MAX=1600
```

### 2.3 日志字段（`[PROMPT_BUILD]`）

开启 `AI_PROMPT_DEBUG=true` 后，重点看这些字段：
- `contextInjected`
- `injectSelectedContext`
- `injectConversationContext`
- `injectDraftContext`
- `contextLen`
- `taskIntent`
- `target`
- `referenceType`
- `referenceConfidence`
- `policy`

---

## 3) Context v1.2 验收用例（Postman）

### 3.1 用例A：上文指代（应注入 recentMessages）

请求：

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "翻译一下上面的内容",
  "constraints": {
    "recentMessages": [
      {"role": "user", "content": "请解释这段话"},
      {"role": "assistant", "content": "This paragraph mainly describes how consumer preferences changed over time."}
    ]
  }
}
```

期望日志（示例）：

```text
[PROMPT_BUILD] ... contextInjected=true injectConversationContext=true injectSelectedContext=false injectDraftContext=false target=lastAssistantAnswer referenceType=ABOVE referenceConfidence=0.70 ...
```

### 3.2 用例B：无关问题（不应强行引用上下文）

请求：

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "新年的主题词 英文",
  "constraints": {
    "recentMessages": [
      {"role": "assistant", "content": "This is a previous essay analysis."}
    ],
    "draftText": "The pie chart illustrates ..."
  }
}
```

期望日志（示例）：

```text
[PROMPT_BUILD] ... contextInjected=false injectConversationContext=false injectDraftContext=false ... referenceType=NONE referenceConfidence=0.00 ...
```

### 3.3 用例C：选区改写（优先 selectedText）

请求：

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "改写这段，更学术",
  "constraints": {
    "selectedText": "The chart show many people's choose restaurant habit.",
    "draftText": "The chart show many people's choose restaurant habit. It also ..."
  }
}
```

期望日志（示例）：

```text
[PROMPT_BUILD] ... contextInjected=true injectSelectedContext=true injectConversationContext=false injectDraftContext=false taskIntent=rewrite target=selectedText ...
```

并且 Prompt 中（无需打印全文）应包含操作边界提示：
- `若 target=selectedText，仅处理选中文本，不扩写全文。`

### 3.4 用例D：rewrite 无选区（应注入 draft）

请求：

```json
{
  "apiVersion": 1,
  "intent": "rewrite",
  "instruction": "帮我整体改写得更自然",
  "constraints": {
    "draftText": "The pie chart illustrates ..."
  }
}
```

期望日志（示例）：

```text
[PROMPT_BUILD] ... contextInjected=true injectSelectedContext=false injectConversationContext=false injectDraftContext=true taskIntent=rewrite target=fullDraft ...
```

### 3.5 用例E：解释上一条回复（命中 LAST_ASSISTANT）

请求：

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "解释上一条回复",
  "constraints": {
    "recentMessages": [
      {"role": "assistant", "content": "Use a more formal connector such as 'Moreover'."},
      {"role": "assistant", "content": "Use a more formal connector such as 'Moreover'."},
      {"role": "user", "content": "好的"}
    ]
  }
}
```

期望日志（示例）：

```text
[PROMPT_BUILD] ... injectConversationContext=true target=lastAssistantAnswer referenceType=LAST_ASSISTANT ...
```

说明：
- `好的` 会被视为低信息消息过滤
- 连续重复 assistant 消息会去重（仅保留一条）

---

## 设计原则（当前阶段）

- AI Chat 自由回答；上下文范围由前端显式信号（contextScope/actionOrigin）+ 后端决策控制；recentMessages 处理已接口化，当前默认 rule，后续可替换 LangChain 实现。

---

## 4) 版本记录（建议维护）

- Ability Prompt 验收：已并入本文件
- Context v1.2：已并入本文件（规则化指代识别、recent 清洗、来源优先级提示、日志字段）
- Conversation Memory（Phase 2B）：
  - `redis + context-sidecar`（Docker）已启动
  - backend 已可切 `hybrid` 模式接入 conversation memory（Python sidecar + rule fallback）
  - 已确认 `[CTX_PROCESS] ... processor=python action=append ok=true`（conversation memory append 打通）
  - 已修复 `append 422 body missing`（Java -> sidecar 调用改为 HTTP/1.1 + `application/json; charset=utf-8` + byte[] JSON body）
- 链路可观测性：
  - 已确认可看到 `[AI_TRACE] ai.prompt.debug = true ... component=PromptAssembler`

后续若做 `Context v1.5 / v2.0`，建议继续在本文件追加章节（避免文档分散）。
