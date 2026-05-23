---
title: 写作路由与 Patch 方案
status: implementing
owner: ai
last_updated: 2026-05-23
review_cycle: monthly
related_code:
  - python/ai_orchestrator/
  - web/src/components/writing/
related_docs:
  - docs/agent/写作教练Agent设计.md
  - docs/agent/写作教练Schema设计.md
  - docs/agent/路由Agent设计.md
---

# 写作路由与 Patch 方案

本文档定义写作教练从“聊天回复”升级为“可控编辑协作”的实现方案。目标是接近 Codex / Cursor 的体验：模型负责理解意图和提出修改，程序负责定位、校验、预览和应用，用户确认后才真正改正文。

## 设计目标

1. 普通问题只回答，不出现“应用到正文”卡片。
2. 润色、续写、替换、插入、生成终稿等修改请求，输出可校验的 patch。
3. Patch 应用器负责确定性修改正文，避免模型直接改正文。
4. 找不到目标、匹配多个位置、疑似重复插入时，不自动乱改。
5. 上下文按需加载，避免每次都把题目、全文、rubric、历史消息全部传给模型。

## 总体架构

```text
用户消息
↓
WritingIntentPlanner
轻量判断：普通回答 / 修改正文 / 需要哪些上下文
↓
ContextBuilder
按需加载：题目、选区、全文、rubric、历史对话
↓
WritingCoachAgent
正式回答，必要时输出 reply + patches
↓
WritingPatchApplicator
定位、校验、生成预览
↓
前端确认卡片
↓
用户确认后写入作文编辑器
```

## 当前落地状态

截至 2026-05-23，已完成第一阶段和第二阶段的基础接入：

1. 前端已新增 `WritingPatch` 类型和 `WritingPatchApplicator`，应用正文修改时由确定性代码负责定位、校验和写入。
2. 现有 `WritingCoachEditAction` 已升级为携带 `patch`，兼容旧的 Markdown 代码块提取方式。
3. Python 侧已新增 `WritingCoachRouteDecision`、`WritingCoachRouteAgent` 和 `WritingCoachRouteRunner`。
4. 服务层已接入写作路由：显式按钮阶段直接进入对应阶段 Agent；普通 `coach` 消息先经过写作路由，再决定是否进入 `analyze/outline/next/topic/polish/draft`。
5. 第三阶段“后端直接返回 `WritingCoachResponse { reply, patches }`”尚未完成，当前前端仍从可编辑代码块提取候选 patch。

## Agent 边界

### WritingCoachRouteAgent

轻量 route agent，只服务写作教练，不做最终回答。

职责：

- 判断本轮是普通问答还是正文修改。
- 判断是否进入某个写作阶段。
- 判断本轮建议的编辑意图。
- 判断第二阶段需要哪些上下文。

不负责：

- 生成作文内容。
- 评分。
- 修改正文。
- 调用编辑器写入。

### WritingCoachStageAgent

写作阶段 agent，负责正式回答用户问题。当前阶段仍通过 Markdown 可编辑代码块给前端提取 patch；后续会升级为直接输出 `reply + patches`。

职责：

- 解释写作问题。
- 审题、提纲、偏题检查。
- 润色选区、续写段落、补例子、生成终稿。
- 输出 `reply + patches`。

不负责：

- 直接写入编辑器。
- 自行绕过 patch 校验。

### WritingPatchApplicator

确定性代码，不是 agent。

职责：

- 根据 patch 定位目标文本。
- 校验 range、anchor、search text。
- 检测多匹配、找不到、重复插入。
- 生成预览。
- 在用户确认后应用到正文。

## 写作路由 Schema

实际 Python schema 位于 `python/ai_orchestrator/schemas/writing_coach.py`：

```py
class WritingCoachContextPolicy(BaseModel):
    include_topic: bool
    include_rubric: bool
    include_selection: bool
    include_draft: bool
    include_recent_messages: bool


class WritingCoachRouteDecision(BaseModel):
    route_type: Literal["run_stage", "answer_direct", "ask_clarification"]
    target_action: Literal["analyze", "outline", "next", "topic", "polish", "draft"] | None
    edit_intent: Literal[
        "none",
        "replace_selection",
        "insert_after_selection",
        "append_paragraph",
        "replace_document",
    ]
    context_policy: WritingCoachContextPolicy
    confidence: float
    missing_inputs: list[str]
    reason: str
}
```

## 路由规则

### 普通回答

用户只是问解释、原因、写法建议、语法说明时：

```json
{
  "routeType": "answer_direct",
  "targetAction": null,
  "editIntent": "none",
  "contextPolicy": {
    "includeTopic": true,
    "includeRubric": false,
    "includeSelection": true,
    "includeDraft": false,
    "includeRecentMessages": true
  },
  "confidence": 0.94,
  "reason": "用户在询问词义解释，没有要求修改正文。"
}
```

普通回答必须满足：

- `patches` 为空。
- 前端不展示应用卡片。
- 不调用 patch applicator。

### 提出修改

用户明确要求润色、替换、续写、插入、补例子、生成可应用内容时：

```json
{
  "routeType": "run_stage",
  "targetAction": "polish",
  "editIntent": "replace_selection",
  "contextPolicy": {
    "includeTopic": true,
    "includeRubric": true,
    "includeSelection": true,
    "includeDraft": false,
    "includeRecentMessages": true
  },
  "confidence": 0.97,
  "reason": "用户明确要求润色选中句子，需要生成可应用到正文的修改。"
}
```

不确定用户是否要修改正文时，默认 `answer_direct`。

## WritingPatch Schema

```ts
type WritingPatch =
  | {
      op: 'replace_selection'
      range: { start: number; end: number }
      originalText: string
      newText: string
      reason?: string
    }
  | {
      op: 'search_replace'
      searchText: string
      replaceText: string
      reason?: string
    }
  | {
      op: 'insert_after_anchor'
      anchorText: string
      insertText: string
      reason?: string
    }
  | {
      op: 'append_paragraph'
      text: string
      reason?: string
    }
  | {
      op: 'replace_document'
      text: string
      reason?: string
    }
```

## WritingCoachResponse Schema

```ts
interface WritingCoachResponse {
  intent: 'answer_only' | 'propose_edit'
  reply: string
  patches: WritingPatch[]
}
```

普通问题示例：

```json
{
  "intent": "answer_only",
  "reply": "compulsory 表示强制性的、必修的。",
  "patches": []
}
```

修改正文示例：

```json
{
  "intent": "propose_edit",
  "reply": "我建议把这句话改得更自然。",
  "patches": [
    {
      "op": "search_replace",
      "searchText": "making College Chinese compulsory",
      "replaceText": "making College Chinese a compulsory course",
      "reason": "course 前加 a compulsory course 更自然。"
    }
  ]
}
```

## Patch 应用结果

```ts
type PatchApplyResult =
  | {
      status: 'success'
      nextText: string
      preview: {
        before?: string
        after: string
        operationLabel: string
      }
      appliedRange: { start: number; end: number }
    }
  | {
      status: 'not_found'
      message: string
    }
  | {
      status: 'ambiguous'
      message: string
      candidates: Array<{ start: number; end: number; preview: string }>
    }
  | {
      status: 'duplicate'
      message: string
    }
```

## Patch 应用规则

1. `replace_selection`
   - 优先使用 range。
   - range 内文本必须和 `originalText` 一致。
   - 不一致时回退到 `search_replace`。

2. `search_replace`
   - 精确匹配 `searchText`。
   - 找不到时返回 `not_found`。
   - 匹配多个时返回 `ambiguous`，由用户选择。

3. `insert_after_anchor`
   - 精确匹配 `anchorText`。
   - 在 anchor 之后插入 `insertText`。
   - 若 anchor 附近已经存在相似 `insertText`，返回 `duplicate`。

4. `append_paragraph`
   - 追加到文末。
   - 自动补段落分隔。

5. `replace_document`
   - 必须二次确认。
   - 前端应明确提示会覆盖全文。

## 前端交互

前端展示逻辑：

```text
patches.length === 0
↓
只显示普通回答

patches.length > 0
↓
显示普通回答 + patch 确认卡片
```

Patch 卡片内容：

- 操作类型：替换选区、插入到某句后、追加段落、替换全文。
- 修改原因。
- 原文。
- 新文。
- 应用按钮。
- 取消按钮。

应用流程：

```text
用户点击应用
↓
前端调用 WritingPatchApplicator
↓
success：更新 draftText
not_found：提示无法定位
ambiguous：展示候选位置
duplicate：提示可能重复插入
```

## 实施阶段

### 第一阶段：前端 Patch 底座

1. 新增 `WritingPatch` 类型。
2. 新增 `WritingPatchApplicator` 纯函数。
3. 将现有 `WritingCoachEditAction` 改成基于 `WritingPatch`。
4. 升级确认卡片为 patch 预览。
5. 增加单元测试。

### 第二阶段：写作路由

1. 新增 `WritingCoachRouteDecision` schema。已完成。
2. 新增 `WritingCoachRouteAgent` 和 `WritingCoachRouteRunner`。已完成。
3. Python 编排在普通 `coach` 消息前调用写作路由。已完成。
4. 根据 `contextPolicy` 精简第二阶段上下文。待完成。

### 第三阶段：结构化写作输出

1. `WritingCoachAgent` 输出 `WritingCoachResponse`。
2. 前端不再从 Markdown 代码块猜 patch。
3. 所有修改都走 `WritingPatchApplicator`。

### 第四阶段：全局路由

当项目有统一 AI 入口时，再增加 `GlobalRouterAgent`：

```text
GlobalRouterAgent
↓
writing / grammar / vocabulary / translation / scoring / general_chat
↓
WritingIntentPlanner
```

当前写作页内可以默认进入 writing domain，不需要先实现全局路由。

## 验收标准

1. 普通问题不会出现“应用到正文”卡片。
2. 润色选中句子能稳定替换选区。
3. 续写一句能插入到选中句后。
4. 没有选区时，下一段默认追加为新段落。
5. 原文找不到时不自动乱改。
6. 匹配多个位置时要求用户选择。
7. 替换全文必须二次确认。
8. 不再出现重复插入同一句或同一段的问题。
