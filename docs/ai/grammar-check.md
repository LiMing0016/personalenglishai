# 实时语法检查 + AI 隐形错误检测

## 重大事故：分支管理失误导致功能代码全部丢失

### 事故经过

2026 年 3 月 5 日，项目在开发实时语法检查功能时，发生了一次严重的代码丢失事故，导致前一天完成的全部功能代码不可恢复。

**时间线：**

1. **功能开发阶段**：在 feature 分支上开发了完整的语法检查功能，包括：
   - 后端 LanguageTool + Sapling 并行检查、合并去重
   - 后端 GPT 隐形错误检测（`WritingChatService` 真实实现）
   - 前端 GrammarCheckPanel 错误卡片 + AI 建议
   - 前端 EditorShell debounce 触发、替换、自动重检
   - 前端 buildHighlightedHtml 错误下划线渲染

2. **分支切换时未提交代码**：开发过程中多次在不同分支间切换调试，部分改动没有及时 `git commit` 或 `git stash`。切换分支时 Git 默认丢弃未跟踪和未暂存的改动，**大量代码被静默丢弃**。

3. **分支混乱加剧问题**：当时本地有 **8 个分支**，远端有 **4 个分支**，还有 **3 个 stash**。功能代码分散在不同分支的不同状态中，难以追踪哪个分支有最新代码。

4. **强制推送覆盖远端**：尝试整理时发现本地 main 和远端 main 的 commit hash 不一致（内容相同但历史分叉）。为了统一，执行了 `git push --force-with-lease`，**将远端 main 覆盖为本地版本**。

5. **代码不可恢复**：由于关键代码从未 commit 过，`git reflog`、`git fsck --lost-found` 均无法找回。以下代码永久丢失：
   - `WritingChatServiceImpl.java`（GPT 真实对话实现，替代 Mock）
   - 语法检查的多项 bug 修复（CRLF、span 偏移、矛盾检测等）
   - 前端多处联调改动

### 事故根因分析

| 原因 | 说明 |
|------|------|
| **未及时 commit** | 开发过程中长时间不提交，大量改动停留在 working directory |
| **分支过多且无规范** | 8 个本地分支缺乏命名规范和清理机制，不知道哪个是最新的 |
| **切换分支前未检查状态** | `git checkout` 前没有执行 `git status`，未暂存的改动被丢弃 |
| **强制推送** | `--force-with-lease` 虽然比 `--force` 安全，但仍覆盖了远端历史 |
| **缺乏 Code Review 流程** | 一人开发无 PR 机制，分支合并全靠本地操作，无远端备份 |

### 恢复过程

事故发生后，采取了以下步骤恢复项目：

**第一步：清理分支混乱**
- 将本地 8 个分支清理为 2 个（`main` + `feature/admin-panel`）
- 将远端 4 个分支清理为 1 个（`main`）
- 清空 3 个过期 stash
- 临时关闭 GitHub 分支保护规则，执行 force push 统一历史，然后重新开启保护

**第二步：从零重建丢失的功能**
- 根据记忆和会话记录，逐一重建所有丢失的代码
- 后端：重写 Sapling 缓存逻辑、合并去重、矛盾检测、CRLF 归一化
- 前端：重写 EditorShell 语法检查状态管理、替换逻辑、面板联动
- 新建 `WritingSuggestionsService`（GPT 专用隐形错误检测，替代丢失的真实 ChatService）

**第三步：逐步测试修复新发现的 Bug**
- 重建过程中发现并修复了 6 个 Bug（详见下方 Bug 清单）
- 每个 Bug 都是在实际测试中暴露的，不是从旧代码复制的

### 教训与预防措施

| 措施 | 具体操作 |
|------|----------|
| **频繁小提交** | 每完成一个逻辑单元立即 `git commit`，宁可 commit 多不可不 commit |
| **切换前必检查** | 切换分支前必须 `git status`，有改动先 `git stash` 或 commit |
| **分支精简** | 同时维护不超过 3 个分支，feature 完成后立即合并删除 |
| **禁止 force push** | 除非万不得已，永远不用 `--force`；开启 GitHub 分支保护 |
| **远端备份** | 开发中的 feature 分支也推送到远端，即使未完成 |
| **CLAUDE.md 规范** | 在项目规范中明确要求：编码前确认需求、编码后询问是否提交 |

---

## 功能概述

Grammarly 风格的实时语法检查系统，用户打字时自动检测错误，右侧面板实时展示错误卡片，支持逐条替换和一键全改。硬性错误修完后自动调用 GPT 检测隐形语法问题（搭配不当、中式英语等）。

## 架构设计

```
用户输入 → draftText 变化
    → debounce 800ms
    → POST /api/writing/grammar-check
    → LanguageTool + Sapling 并行检查 → 合并去重 → 矛盾检测 → 返回 errors
    → 语法面板显示错误卡片 + 编辑器显示下划线

用户点"替换" → 文本替换 → draftText 变化
    → debounce 800ms → 自动重检
    → 有新错误 → 继续修
    → 无错误 → 自动触发 GPT 隐形错误检测

GPT 检测完成 → 显示"改进建议 Suggestions"
    → 支持逐条替换 / 一键全部替换
```

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 硬性检查 | LanguageTool (本地) | 规则引擎，语法/拼写/标点 |
| 硬性检查 | Sapling AI (API) | 神经网络，主谓一致/介词/时态等 |
| 隐形检测 | OpenAI GPT (API) | 搭配不当/中式英语/不自然表达 |
| 前端面板 | GrammarCheckPanel.vue | 统一错误展示 + AI 建议 |

## 关键接口

### POST /api/writing/grammar-check

轻量语法检查，不涉及 GPT。

```json
// Request
{ "text": "She have a apple." }

// Response
{
  "errors": [
    {
      "id": "gc1",
      "type": "subject_verb",
      "category": "error",
      "severity": "major",
      "span": { "start": 4, "end": 8 },
      "original": "have",
      "suggestion": "has",
      "reason": "语法错误：主谓不一致"
    }
  ]
}
```

### Trinka Lite / Power 模式

- `grammar-check` 现在支持 `trinkaMode`：
  - `lite` -> Trinka `pipeline=basic`
  - `power` -> Trinka `pipeline=advanced`
- 前端默认使用 `lite`
- `evaluate` 评分链路的错误统计默认与 Lite 口径对齐

```json
// Request
{
  "text": "She have a apple.",
  "docId": "doc-1",
  "trinkaMode": "lite"
}
```

### Trinka 顶层分类与建议收口

Trinka 原始返回会保留到 `raw_engine_meta`，包括：

- `type`
- `error_category`
- `lang_category`
- `critical_error`
- `pipeline`
- `top_category_id`
- `top_category_name`

系统不会直接按 Trinka 英文顶层类展示给用户，但会用它来做二次分类：

- `Correctness` -> `error`
- `Clarity / Fluency / Style / Style Guide Compliance / Inclusivity` -> `suggestion`

因此：

- 真正基础错误继续进入“问题”
- 表达优化、风格增强类命中进入“建议与改进”
- 顶部“问题数”只统计 `error`

### trusted rewrite 与 Trinka 重复建议抑制

为避免 `advanced / perfect` 高档润色后的句子又被 Trinka 重复挑刺，系统增加了 `trustedRewriteSegments`。

边界：

- 只对白名单档位生效：`advanced / perfect`
- `basic / steady` 不进入 trusted
- 只抑制 `engine=trinka` 的重复建议类命中
- 不抑制 Trinka 的硬错误
- 不影响 LanguageTool / Sapling / TextGears 等其他引擎

trusted rewrite 进入条件：

1. 用户在 `RewritePanel` 点击替换
2. 后端用 `Lite/basic` 对替换后的句子做一次基础检错
3. 只有当该句没有硬错误时，才登记为 trusted

右侧语法面板与编辑器高亮会共同遵守这套规则：

- trusted 句子中的 Trinka 建议类命中默认隐藏
- trusted 句子中的 Trinka 硬错误继续显示
- 面板会给出“已隐藏重复建议”的轻提示，并允许用户清空 trusted 状态

### POST /api/writing/suggestions

GPT 专用隐形错误检测，不做通用对话。

```json
// Request
{ "text": "I want to improve my English level." }

// Response
{
  "suggestions": [
    {
      "id": "sg1",
      "type": "chinglish",
      "original": "improve my English level",
      "suggestion": "improve my English",
      "reason": "中式英语：英语不说"英语水平""
    }
  ]
}
```

## 遇到的重大 Bug 及解决方案

### Bug 1：Sapling 段落缓存 span 偏移错误

**现象**：编辑器下划线只覆盖半个单词（如 "lear" 而不是 "learn"），越靠后的段落偏移越严重。

**根因**：`checkSaplingWithCache()` 中的 span 计算有三个错误：

1. **Fresh 结果没转全文 span**：Sapling 返回段落相对 span（如 {4,8}），直接加入 allErrors 没有加段落偏移，前端在全文错误位置画下划线。
2. **缓存存储 span 计算为负数**：代码 `e.getSpan().getStart() - base`，用段落相对值减去全文偏移，得到负数。
3. **Sapling 收到含 `\n` 的文本**：发送 `para`（含尾部 `\n`）但缓存 key 用 `trimmed`，span 基准不一致。

**解决方案**：重写 `checkSaplingWithCache()`：
- 用 `trimmed` 调 Sapling API（与缓存 key 一致）
- 计算 `trimmedBase`（trimmed 在全文中的精确位置）
- Fresh 结果：`span + trimmedBase` 转全文后加入 allErrors
- 缓存存储：直接存 Sapling 原始 span（基于 trimmed）
- 缓存读取：`缓存span + trimmedBase` 转全文

### Bug 2：CRLF 换行符导致 span 偏移

**现象**：多行文本中后面的错误下划线位置偏移。

**根因**：后端将 `\r\n` 归一化为 `\n` 后计算 span，但前端文本可能仍含 `\r\n`，每个换行符后 span 偏移 1 字符。

**解决方案**：
- 后端入口：`text.replace("\r\n", "\n").replace("\r", "\n")`
- 前端发送前：`text.replace(/\r\n/g, '\n')`
- 前端高亮渲染（`buildHighlightedHtml.ts`）入口也做归一化
- 三者统一使用 `\n`，span 完全对齐

### Bug 3：`indexOf` 匹配错误出现（span 重定位 bug）

**现象**：替换一个错误后，其他错误的文本被损坏（如 "learn because" 变成乱码）。

**根因**：`resolveErrorSpan()` 使用 `text.indexOf(err.original)` 定位错误，但文中可能有多个相同字符串，`indexOf` 返回第一个出现位置，而非错误实际所在位置。

**解决方案**：实现 `findClosestMatch()` 算法，在所有匹配中找到最接近原始 span 位置的：

```typescript
function findClosestMatch(text: string, needle: string, hintPos: number): number {
  let bestIdx = -1, bestDist = Infinity, searchFrom = 0
  while (searchFrom <= text.length - needle.length) {
    const idx = text.indexOf(needle, searchFrom)
    if (idx === -1) break
    const dist = Math.abs(idx - hintPos)
    if (dist < bestDist) { bestDist = dist; bestIdx = idx }
    searchFrom = idx + 1
  }
  return bestIdx
}
```

同时在 `buildHighlightedHtml.ts` 的 span 修正逻辑中也使用此算法。

### Bug 4：矛盾错误同时出现

**现象**：同一轮检查中出现矛盾建议——如 "with → 删除" 和 "more → with more"，用户删除 with 后重检又让加回来。

**根因**：LT 和 Sapling（或 Sapling 内部）返回逻辑矛盾的错误。原有去重只检查 span 重叠和文本包含，不检测语义矛盾。

**解决方案**：在 `GrammarCheckServiceImpl` 合并后增加 `removeContradictions()` 步骤：
- 遍历相邻错误对（gap ≤ 3 字符）
- 若错误 A 是删除操作，且错误 B 的建议包含 A 要删除的文本 → 移除 B
- 反之亦然

### Bug 5：删除类错误无法替换

**现象**：suggestion 为空字符串（删除操作）的错误，点击"替换"按钮无反应。

**根因**：`hasValidSuggestion()` 中 `!s` 判断把空字符串当作无效建议拒绝。

**解决方案**：
```typescript
// 空字符串表示删除，是有效操作
if (s === '' && err.original?.trim()) return true
```

### Bug 6：删除订正功能后语法面板不显示评分错误

**现象**：删除订正面板后，评分结果中的错误无处展示。

**根因**：`grammarPanelErrors` 原来只返回实时语法错误，评分错误在已删除的订正面板展示。

**解决方案**：`grammarPanelErrors` 改为合并逻辑——重检完成显示实时结果，否则显示评分错误，最后回落到实时结果：

```typescript
const grammarPanelErrors = computed(() => {
  if (grammarReChecked.value && grammarErrors.value.length > 0)
    return grammarErrors.value
  if (!grammarReChecked.value && evaluateResult.value?.errors?.length)
    return evaluateResult.value.errors.filter(e => e.category !== 'suggestion')
  return grammarErrors.value
})
```

## 文件清单

### 后端
| 文件 | 说明 |
|------|------|
| `GrammarCheckServiceImpl.java` | LT + Sapling 并行检查、合并去重、矛盾检测 |
| `LanguageToolService.java` | 本地规则引擎，ThreadLocal 线程安全 |
| `SaplingService.java` | Sapling HTTP API 调用 + 响应解析 |
| `WritingSuggestionsService.java` | GPT 专用隐形错误检测 |
| `WritingController.java` | `/grammar-check` + `/suggestions` 端点 |

### 前端
| 文件 | 说明 |
|------|------|
| `EditorShell.vue` | 语法检查核心逻辑（debounce、替换、重检） |
| `GrammarCheckPanel.vue` | 错误卡片列表 + AI 建议区块 |
| `buildHighlightedHtml.ts` | 错误下划线渲染（CRLF 归一化 + 最近匹配） |
| `api/writing.ts` | `grammarCheck()` + `fetchWritingSuggestions()` |

## 数据流总结

```
┌─────────────┐    debounce 800ms    ┌──────────────────┐
│  用户输入     │ ──────────────────► │ /grammar-check   │
│  draftText   │                     │ LT + Sapling     │
└─────────────┘                     │ 合并 + 去重 + 矛盾│
                                    └────────┬─────────┘
                                             │ errors
                                             ▼
                                    ┌──────────────────┐
                                    │ GrammarCheckPanel │
                                    │ 错误卡片 + 下划线  │
                                    └────────┬─────────┘
                                             │ 全部修完
                                             ▼
                                    ┌──────────────────┐
                                    │ /suggestions     │
                                    │ GPT 隐形错误检测   │
                                    └────────┬─────────┘
                                             │ suggestions
                                             ▼
                                    ┌──────────────────┐
                                    │ 改进建议区块       │
                                    │ 替换 / 一键替换    │
                                    └──────────────────┘
```
