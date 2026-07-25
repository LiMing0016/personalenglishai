# 单词卡编辑区扩展实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单词卡编辑态改造成占据详情页主体的大尺寸 Markdown 文档工作区，同时保持阅读态和数据协议不变。

**Architecture:** 仅调整现有 `VocabularyCardInspector` 与 `VocabularyMarkdownEditor` 的响应式布局规则，不新增状态、组件或依赖。阅读态继续限制为 `840px`，编辑态使用可用宽度；文本框通过视口相关的 `clamp()` 提供稳定的桌面高度，并为移动端设置独立下限。

**Tech Stack:** Vue 3、TypeScript、CSS、Node test runner、Vite

## Global Constraints

- 保持核心 JSON 加扩展 Markdown 的现有数据结构。
- 不修改保存、冲突处理、主题切换和删除流程。
- 不引入富文本编辑器、双栏预览、新依赖或新的持久化状态。
- 阅读态继续保持适合长文阅读的窄版宽度。
- `390px` 宽移动视口不得出现横向溢出。

---

### Task 1: 扩大单词卡 Markdown 编辑工作区

**Files:**
- Modify: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Modify: `web/src/components/vocabulary/VocabularyMarkdownEditor.vue`
- Test: `web/tests/vocabularyCardInspector.test.ts`

**Interfaces:**
- Consumes: `VocabularyMarkdownEditor` 现有 `v-model<string>` 接口和 `editing` 页面状态。
- Produces: 不改变组件接口，只提供编辑态全宽布局和响应式编辑器高度。

- [ ] **Step 1: 写入失败的布局契约测试**

在 `web/tests/vocabularyCardInspector.test.ts` 的编辑器样式测试中增加以下断言：

```ts
const markdownEditor = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyMarkdownEditor.vue', import.meta.url),
  'utf8',
)

assert.match(inspector, /\.card-inspector__document\s*\{[^}]*max-width:\s*840px/s)
assert.match(inspector, /\.card-inspector__editor-document\s*\{[^}]*width:\s*100%[^}]*max-width:\s*none/s)
assert.match(markdownEditor, /min-height:\s*clamp\(420px,\s*calc\(100vh - 430px\),\s*720px\)/)
assert.match(markdownEditor, /@media \(max-width:\s*767px\)[\s\S]*min-height:\s*360px/)
```

- [ ] **Step 2: 运行测试并确认当前布局不满足契约**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyCardInspector.test.ts
```

Expected: FAIL，提示编辑态仍继承 `max-width: 840px` 或编辑器仍使用 `min-height: 220px`。

- [ ] **Step 3: 拆分阅读态和编辑态宽度规则**

在 `VocabularyCardInspector.vue` 中将共享规则改为：

```css
.card-inspector__document,
.card-inspector__editor-document {
  min-width: 0;
  width: 100%;
}
.card-inspector__document { max-width: 840px; margin: 0 auto; }
.card-inspector__editor-document { max-width: none; margin: 22px auto 0; }
```

保持现有页面内边距、顶部信息和工具栏不变。

- [ ] **Step 4: 增加桌面和移动端稳定高度**

在 `VocabularyMarkdownEditor.vue` 中将文本框高度规则改为：

```css
.markdown-editor textarea {
  min-height: clamp(420px, calc(100vh - 430px), 720px);
  max-height: 820px;
  padding: 18px 20px;
  font-size: 14px;
  line-height: 1.65;
}

@media (max-width: 767px) {
  .markdown-editor textarea {
    min-height: 360px;
    padding: 14px;
  }
}
```

保留 `resize: vertical`、字符上限、错误提示和原始 Markdown 文本行为。

- [ ] **Step 5: 运行前端测试和生产构建**

Run:

```powershell
cd web
npx tsx --test "tests/vocabulary*.test.ts"
npm run build
```

Expected: 所有词汇测试通过，Vite 生产构建退出码为 `0`。

- [ ] **Step 6: 在真实详情页执行响应式验收**

打开：

```text
http://127.0.0.1:5177/app/vocabulary/cards/card_54435988709b420e99f1b6da40fc996e
```

桌面端进入编辑态，确认编辑区使用详情页主体宽度且默认高度不少于约 `520px`。切换到 `390 x 844` 视口，确认页面无横向溢出、工具栏可换行、编辑器和保存按钮可用；再切回阅读态，确认文档仍保持窄版排版。

- [ ] **Step 7: 提交实现**

```powershell
git add web/src/components/vocabulary/VocabularyCardInspector.vue web/src/components/vocabulary/VocabularyMarkdownEditor.vue web/tests/vocabularyCardInspector.test.ts docs/superpowers/specs/2026-07-21-vocabulary-card-editor-layout-design.md docs/superpowers/plans/2026-07-21-vocabulary-card-editor-layout.md
git commit -m "feat(ui): 扩大单词卡编辑工作区"
```
