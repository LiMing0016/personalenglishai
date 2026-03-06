# AI 隐形错误检测 — 设计文档

## 定位

LT + Sapling 负责硬性语法错误（拼写、主谓一致、时态等），GPT 负责它们检测不到的**隐形错误**——语法上可能没问题，但母语者不会这么说。

两者互补，不重叠：
```
LanguageTool (规则引擎) ──┐
                          ├── 硬性错误 → 语法面板上半部分
Sapling (神经网络)   ──────┘

GPT (大模型)   ──────────────── 隐形错误 → 语法面板下半部分 "改进建议"
```

## 错误分类与优先级

### 严重（必须改）

| type | 名称 | 说明 | 示例 |
|------|------|------|------|
| `chinglish` | 中式英语 | 从中文直译的表达，母语者完全不会这么说，直接暴露母语干扰 | "improve my English level" → "improve my English" |
| `uncountable` | 不可数名词误用 | 不可数名词加了复数或加了不定冠词，属于明确错误 | "give me some informations" → "give me some information" |

**特征**：不是"写得不好"，而是"写错了"。中国学生高频错误，阅卷扣分项。

### 中等（建议改）

| type | 名称 | 说明 | 示例 |
|------|------|------|------|
| `collocation` | 搭配不当 | 词与词的固定搭配用错，语法没错但组合不存在 | "make homework" → "do homework" |
| `unnatural` | 不自然表达 | 语法正确、意思能懂，但母语者会换一种说法 | "I very like it" → "I really like it" |

**特征**：不算错，但影响地道性和流畅度。改了能明显提升作文档次。

### 轻微（可选改）

| type | 名称 | 说明 | 示例 |
|------|------|------|------|
| `word_choice` | 用词可优化 | 用词不算错，但有更精准、更高级的替代词 | "good" → "excellent"、"big problem" → "significant issue" |

**特征**：不改也不扣分，改了是加分项。属于润色级别的建议。

## 优先级排序规则

面板中的建议按优先级排序展示：

```
严重 (chinglish, uncountable)     ← 排最前，红色标记
  ↓
中等 (collocation, unnatural)     ← 排中间，橙色标记
  ↓
轻微 (word_choice)                ← 排最后，灰色标记
```

同一优先级内按出现位置（在原文中的先后顺序）排列。

## Prompt 设计

### System Prompt

```
You are an expert English writing coach specializing in detecting
subtle language issues that automated grammar checkers miss.

Your ONLY task: analyze the given English essay and find these
specific problems, listed by priority:

**CRITICAL (must fix):**
1. Chinglish (中式英语): expressions directly translated from Chinese
2. Uncountable noun misuse: e.g. "informations", "knowledges"

**MODERATE (should fix):**
3. Collocation errors (搭配不当): unnatural word combinations
4. Unnatural phrasing: grammatically correct but awkward

**MINOR (optional):**
5. Word choice: a better word exists but current one is acceptable

Rules:
- ONLY report issues you are highly confident about
- "original" MUST be an exact substring from the essay
- Keep suggestions minimal — change as few words as possible
- "severity": "critical" | "moderate" | "minor"
- "reason" in Chinese, under 30 chars
- Do NOT flag basic grammar/spelling (handled by other tools)
- Do NOT add markdown fences

JSON format:
{"suggestions":[{
  "id":"sg1",
  "type":"chinglish|uncountable|collocation|unnatural|word_choice",
  "severity":"critical|moderate|minor",
  "original":"exact text",
  "suggestion":"improved text",
  "reason":"中文原因"
}]}
```

### User Prompt

```
Essay to analyze:

{essay_text}
```

### 调用参数

| 参数 | 值 | 原因 |
|------|-----|------|
| temperature | 0.3 | 低温度，保证结果稳定可复现 |
| max_tokens | 1024 | 建议通常不多，够用且不浪费 |

## 前端展示设计

### 触发条件

硬性错误全部修完（`errors.length === 0`）且不在检查中时，自动调用 `/api/writing/suggestions`。

### 面板布局

```
┌─────────────────────────────────┐
│ 改进建议 Suggestions    [↻] [收起]│
│ AI 检测搭配不当、中式英语等隐含问题  │
├─────────────────────────────────┤
│                                 │
│ ● 严重                    [替换] │
│   chinglish                     │
│   improve my English level      │
│   → improve my English          │
│   中式英语：不说"英语水平"        │
│                                 │
│ ● 中等                    [替换] │
│   collocation                   │
│   make homework                 │
│   → do homework                 │
│   搭配错误：homework 搭配 do     │
│                                 │
│ ○ 轻微                    [替换] │
│   word_choice                   │
│   good                          │
│   → excellent                   │
│   用词可提升                     │
│                                 │
│        [一键全部替换]              │
└─────────────────────────────────┘
```

### 颜色标记

| 优先级 | 颜色 | 图标 |
|--------|------|------|
| critical | 红色 `#ef4444` | ● |
| moderate | 橙色 `#f59e0b` | ● |
| minor | 灰色 `#9ca3af` | ○ |

## 接口定义

### POST /api/writing/suggestions

```json
// Request
{ "text": "I want to improve my English level and make homework." }

// Response
{
  "suggestions": [
    {
      "id": "sg1",
      "type": "chinglish",
      "severity": "critical",
      "original": "improve my English level",
      "suggestion": "improve my English",
      "reason": "中式英语：不说"英语水平""
    },
    {
      "id": "sg2",
      "type": "collocation",
      "severity": "moderate",
      "original": "make homework",
      "suggestion": "do homework",
      "reason": "搭配错误：homework 搭配 do"
    }
  ]
}
```

### 后端验证

GPT 返回的每条建议必须通过验证才会返回给前端：

1. `original` 不为空
2. `suggestion` 不为空
3. `original ≠ suggestion`（不是无意义建议）
4. `essayText.contains(original)`（原文中确实存在该片段）

不满足任一条件的建议直接丢弃。

## 与硬性检查的边界

| 维度 | 硬性检查 (LT + Sapling) | 隐形检测 (GPT) |
|------|------------------------|----------------|
| 触发时机 | 用户输入后 800ms | 硬性错误全部清零后 |
| 检查内容 | 拼写、语法、标点、时态 | 中式英语、搭配、不自然表达 |
| 结果位置 | 面板上半部分 | 面板下半部分 "改进建议" |
| 耗时 | ~200ms (本地 + API) | ~3-8s (GPT) |
| 成本 | Sapling 免费额度 / LT 免费 | OpenAI token 费用 |
| 确定性 | 高（规则/模型确定） | 中（GPT 可能漏报或误报） |

Prompt 中明确写了 `Do NOT flag basic grammar/spelling (handled by other tools)`，防止 GPT 与 LT/Sapling 重复报错。
