# 写作教练可应用编辑动作 Trae 实现题目

## 背景

目标是把写作教练做成类似 Codex / Cursor 的交互：

```text
聊天面板提出需求
  ↓
模型理解作文、选区和用户意图
  ↓
生成可应用编辑动作
  ↓
用户确认
  ↓
右侧作文编辑器写入、替换或追加内容
```

当前项目已经有一部分基础：

- `web/src/components/writing/DocEditor.vue`：右侧 TipTap 作文编辑器。
- `web/src/components/writing/EditorShell.vue`：写作页主编排，已有 `onReplaceSelectionWith` 和 `onWritingCoachApply`。
- `web/src/components/writing/panels/WritingCoachPanel.vue`：写作教练聊天面板，已有建议卡和 `apply-suggestion` 事件。
- `python/ai_orchestrator/schemas/writing_coach.py`：写作教练结构化输出 schema。
- `python/ai_orchestrator/prompts/agent_instructions/writing_coach_stage.md`：写作教练阶段 prompt。

## 总体方案

第一版不要让模型静默改正文，而是让模型返回结构化 `editActions`，由用户点击确认后应用到编辑器。

建议第一版支持 3 个动作：

| 动作 | 含义 | 适用场景 |
| --- | --- | --- |
| `replace_selection` | 替换当前选中的句子或段落 | 用户选中一句后说“改得更正式” |
| `insert_after_selection` | 在当前选区后插入一句 | 用户选中一句后说“后面补一句理由” |
| `append_paragraph` | 在正文末尾追加新段落 | 用户说“帮我加一段理由/结尾” |

安全规则：

1. 有选区时，优先使用 `selected_range`，这是最稳定的。
2. 没有选区时，允许 `append_paragraph` 直接生成候选动作。
3. 没有选区但模型想替换或插入时，必须先做 `semantic_match` 定位，并展示给用户确认。
4. 匹配不到、匹配多个、置信度低时，不自动应用，提示用户先选中对应句子。

推荐结构：

```ts
type WritingCoachEditAction =
  | {
      type: 'replace_selection'
      title: string
      text: string
      reason: string
      target?: EditTarget
      confidence?: number
    }
  | {
      type: 'insert_after_selection'
      title: string
      text: string
      reason: string
      target?: EditTarget
      confidence?: number
    }
  | {
      type: 'append_paragraph'
      title: string
      text: string
      reason: string
      confidence?: number
    }

type EditTarget =
  | { mode: 'selected_range'; start: number; end: number; selectedText: string }
  | { mode: 'semantic_match'; originalText: string; matchCount?: number }
```

## 非目标

- 不做全文自动 diff patch。
- 不做多文件代码编辑器能力。
- 不做复杂的“第 3 段第 2 句”稳定定位系统。
- 不允许模型绕过用户确认直接覆盖正文。
- 不引入新的前端状态库。

## 推荐执行顺序

```text
题 1：定义结构化编辑动作协议
  ↓
题 2：后端写作教练生成 editActions
  ↓
题 3：前端展示可应用动作卡
  ↓
题 4：编辑器应用 replace / insert / append
  ↓
题 5：无选区语义定位与降级提示
  ↓
题 6：端到端测试与文档同步
```

---

## 题 1：定义写作教练编辑动作协议

### 小题 A：后端 Pydantic Schema

#### 题目 Prompt（完成）

请为写作教练新增结构化编辑动作 schema，用于让模型返回可应用到作文编辑器的动作。

要求：

1. 在 `python/ai_orchestrator/schemas/writing_coach.py` 中新增或扩展 schema。
2. 定义 `WritingCoachEditAction`，支持：
   - `replace_selection`
   - `insert_after_selection`
   - `append_paragraph`
3. 每个 action 至少包含：
   - `type`
   - `title`
   - `text`
   - `reason`
   - `confidence`
4. 支持 `target`：
   - `selected_range`
   - `semantic_match`
5. 使用严格 Pydantic 配置，禁止额外字段。
6. 写作教练总输出中包含：
   - `message`
   - `editActions`

#### 题目难度

中等

#### 验收标准

- Pydantic 能 parse 有 3 种 action 的样例。
- 多余字段会被拒绝。
- `type` 只能是允许的 3 个动作。
- `confidence` 是 0 到 1 之间的数字。
- `target.mode` 只能是 `selected_range` 或 `semantic_match`。
- 新增单元测试覆盖正常样例和非法 action type。

### 小题 B：前端 TypeScript 类型（完成）

#### 题目 Prompt

请在前端补齐写作教练编辑动作类型，让 UI 可以类型安全地展示和应用模型返回的动作。

要求：

1. 在 `web/src/types/assistantRequest.ts` 或更合适的写作教练类型文件中定义：
   - `WritingCoachEditAction`
   - `WritingCoachEditTarget`
   - `WritingCoachStructuredResponse`
2. 类型字段要和后端 Pydantic schema 对齐。
3. 不要把 action 写成 `any`。
4. 保持对现有 `lastChatResult.displayText` 的兼容。

#### 题目难度

简单

#### 验收标准

- TypeScript build 通过。
- 前端可以 import `WritingCoachEditAction`。
- action type 有明确联合类型。
- 不影响现有写作教练普通文本回复展示。

---

## 题 2：让写作教练 Agent 返回可应用动作

### 小题 A：调整写作教练 Prompt（完成）

#### 题目 Prompt

请调整写作教练 Agent 的 prompt，让模型在适合的时候返回 `editActions`。

要求：

1. 修改 `python/ai_orchestrator/prompts/agent_instructions/writing_coach_stage.md`。
2. 明确告诉模型：
   - 用户要求“改写/润色/替换”且有选区时，优先返回 `replace_selection`。
   - 用户要求“在这句后面补一句/展开理由”且有选区时，优先返回 `insert_after_selection`。
   - 用户要求“加一段/补一个主体段/写结尾”时，优先返回 `append_paragraph`。
3. `text` 字段必须是可直接写入正文的英文内容，不要夹杂解释。
4. `reason` 字段用中文解释为什么这样改。
5. 不确定目标时，不要伪造选区，返回说明并要求用户选中文本。

#### 题目难度

中等

#### 验收标准

- prompt 明确区分 `message` 和 `editActions[].text`。
- 模型不会把解释性中文写进 `text`。
- 没有选区时，替换/插入动作不会伪造 `selected_range`。
- 有对应测试或快照覆盖 prompt 关键约束。

### 小题 B：后端输出映射

#### 题目 Prompt（完成）

请让后端写作教练接口能把模型结构化输出里的 `message` 和 `editActions` 返回给前端。

要求：

1. 检查 `python/ai_orchestrator/assistant_service.py`、`agent_session_runner.py` 或当前写作教练响应路径。
2. 保持原有纯文本 `reply` 兼容。
3. 如果有结构化 `editActions`，需要把它们作为前端可读取字段返回。
4. 如果结构化解析失败，要降级为普通文本回复，并记录可定位日志。

#### 题目难度

困难

#### 验收标准

- 前端能拿到 `message` 和 `editActions`。
- 旧的普通聊天回复仍然显示。
- 结构化解析失败不会导致整个聊天失败。
- 单元测试覆盖：
  - 有 action 的回复。
  - 无 action 的回复。
  - 解析失败降级。

---

## 题 3：前端展示可应用动作卡

### 小题 A：写作教练面板渲染 Action Card

#### 题目 Prompt（完成）

请在 `WritingCoachPanel.vue` 中展示写作教练返回的 `editActions`。

要求：

1. 每个 action 渲染成独立卡片。
2. 卡片包含：
   - 标题 `title`
   - 可写入正文的英文 `text`
   - 中文解释 `reason`
   - 置信度或目标状态
3. 每个卡片提供按钮：
   - `应用到正文`
   - `复制`
   - `取消`
4. 如果是 `semantic_match` 且置信度低，按钮文案改为 `先选中句子再应用`，并禁用直接应用。
5. 视觉上要和写作教练浅色代码块保持一致，不要做成弹窗。

#### 题目难度

中等

#### 验收标准

- 有 action 时能看到 action card。
- 没有 action 时只显示普通聊天回复。
- 点击 `复制` 能复制 action 的 `text`。
- 低置信度 action 不允许直接应用。
- 移动端或窄屏不溢出。

### 小题 B：面板事件设计

#### 题目 Prompt（完成）

请设计并实现写作教练面板向父组件发送编辑动作的事件。

要求：

1. `WritingCoachPanel.vue` emit 一个明确事件，例如 `apply-edit-action`。
2. 事件 payload 使用 `WritingCoachEditAction` 类型。
3. 保留旧的 `apply-suggestion` 兼容路径，避免影响已有功能。
4. `RightPanel.vue` 负责把事件继续转发给 `EditorShell.vue`。

#### 题目难度

中等

#### 验收标准

- `WritingCoachPanel.vue` 不直接修改正文。
- `RightPanel.vue` 只做事件转发，不做复杂业务。
- `EditorShell.vue` 收到完整 action payload。
- TypeScript build 通过。

---

## 题 4：实现编辑器应用动作

### 小题 A：实现 replace_selection 与 append_paragraph

#### 题目 Prompt（完成）

请在 `EditorShell.vue` 中实现写作教练 action 的应用逻辑，先完成 `replace_selection` 和 `append_paragraph`。

要求：

1. `replace_selection`：
   - 必须有当前选区或 `selected_range`。
   - 使用现有 `onReplaceSelectionWith` 或等价逻辑。
   - 替换后光标移动到新文本末尾。
2. `append_paragraph`：
   - 追加到正文末尾。
   - 如果正文不为空，前面加段落分隔。
   - 追加后光标移动到末尾。
3. 应用成功后显示 toast。
4. 应用失败时给出明确原因，不要静默失败。

#### 题目难度

中等

#### 验收标准

- 选中一句后，`replace_selection` 能稳定替换。
- 未选中文本时，`replace_selection` 显示“请先选中要替换的文本”。
- `append_paragraph` 能在末尾追加新段落。
- 应用后 `draftStore.draftText` 更新。
- 应用后编辑器光标位置正确。

### 小题 B：实现 insert_after_selection

#### 题目 Prompt（完成）

请实现 `insert_after_selection`，让写作教练可以在当前选中句子后插入一句。

要求：

1. 优先使用 `selectedSpanPinned.end` 作为插入点。
2. 插入前根据上下文自动补空格或段落分隔：
   - 同一段内插入一句时，用一个空格衔接。
   - 如果 action 文本是完整段落，可用段落分隔。
3. 插入后光标移动到插入文本末尾。
4. 没有选区时不应用，提示用户先选中目标句子。

#### 题目难度

中等

#### 验收标准

- 选中一句后，能在该句后插入新句。
- 不会把新句插到全文末尾。
- 不会吞掉原选区文本。
- 插入后正文文本顺序正确。
- 有单元测试或组件级测试覆盖字符串拼接边界。

---

## 题 5：无选区时的语义定位与降级

### 小题 A：实现 semantic_match 定位（完成）

#### 题目 Prompt

请实现无选区情况下的基础语义定位能力，让模型可以用 `target.originalText` 指向正文中的句子。

要求：

1. 在前端实现一个纯函数，例如 `resolveEditTarget(draftText, action)`。
2. 如果 `originalText` 在正文中精确出现 1 次，返回 start/end。
3. 如果出现 0 次，返回 `not_found`。
4. 如果出现多次，返回 `ambiguous`。
5. 第一版只做精确文本匹配，不做复杂语义 embedding。

#### 题目难度

中等

#### 验收标准

- 精确匹配 1 次时能得到正确 start/end。
- 匹配 0 次时不会应用。
- 匹配多次时不会应用。
- 函数有单元测试。
- 不引入新依赖。

### 小题 B：降级确认体验

#### 题目 Prompt

请完善 semantic_match 的前端降级体验。

要求：

1. 如果 `not_found`，action card 显示：`没有找到对应句子，请先在右侧选中目标句子`。
2. 如果 `ambiguous`，action card 显示：`找到多个相似句子，请先选中要修改的句子`。
3. 如果 `confidence < 0.75`，不允许直接应用。
4. 用户重新选中句子后，可以再次点击应用。

#### 题目难度

简单

#### 验收标准

- 不会在定位不明确时自动改正文。
- 错误提示对用户可理解。
- 用户选中目标句子后可以继续应用该 action。
- 不影响有明确选区的正常流程。

---

## 题 6：测试、文档与端到端验收

### 小题 A：自动化测试

#### 题目 Prompt

请为写作教练可应用编辑动作补测试。

要求：

1. 后端测试覆盖 schema parse 和 action 约束。
2. 前端测试覆盖：
   - `replace_selection`
   - `insert_after_selection`
   - `append_paragraph`
   - `semantic_match`
   - 低置信度不允许应用
3. 如果项目当前没有合适前端测试框架，至少把纯函数抽出并用现有测试方式验证。

#### 题目难度

困难

#### 验收标准

- Python schema 测试通过。
- 前端纯函数测试通过。
- `npm run build` 通过。
- 测试样例覆盖有选区和无选区两类路径。

### 小题 B：文档和手工验收

#### 题目 Prompt

请同步写作教练文档，并完成端到端手工验收。

要求：

1. 更新 `docs/agent/写作教练Schema设计.md` 或相关文档。
2. 文档说明：
   - 为什么要用 `editActions`
   - 三种 action 的用途
   - 为什么第一版需要用户确认后应用
   - 无选区时如何降级
3. 手工验收至少覆盖：
   - 选中一句，让模型替换。
   - 选中一句，让模型在后面插入一句。
   - 不选中，让模型追加一段。
   - 不选中但要求替换，确认系统会要求用户先选中。

#### 题目难度

中等

#### 验收标准

- 文档与代码字段一致。
- 手工验收路径全部通过。
- 失败路径有明确提示。
- 不会自动覆盖用户正文。

## 建议验证命令

```bash
python -m pytest python/ai_orchestrator/tests/test_writing_coach_schemas.py -q
```

```bash
npx tsx --test web/src/components/assistant/markdown.test.ts
```

```bash
cd web
npm run build
```

## 最终交付

完成后请给出：

- 修改文件列表。
- 新增 schema 示例。
- 3 个 action 的真实 UI 截图或说明。
- 已运行测试命令和结果。
- 哪些场景仍要求用户手动选中文本。

