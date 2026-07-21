# Vocabulary Navigation Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除词汇模块顶部无功能品牌与用户操作，并删除单词沉淀页重复标题，只保留可工作的四个二级导航入口。

**Architecture:** 保持 `VocabularyView.vue` 现有单视图编排、`activeView` 状态和路由逻辑不变，只收敛模板结构与对应样式。通过现有源码契约测试锁定被删除元素和保留的功能入口，再用构建与浏览器验证确认桌面、移动端布局。

**Tech Stack:** Vue 3、TypeScript、Scoped CSS、Node Test Runner、tsx、Vite

## Global Constraints

- 不调整全局左侧导航。
- 不新增通知、账户或词汇首页功能。
- 不修改路由、接口、数据结构、模型调用或数据库。
- 顶部必须继续保留“搜索单词、背词模式、单词沉淀、学习统计”四个入口及激活状态。
- 单词沉淀主体必须继续保留 `aria-label="单词沉淀"`。

---

## File Responsibility Map

- `web/src/views/VocabularyView.vue`：删除冗余模板节点和专属样式，保留视图导航与业务页面组合。
- `web/tests/vocabularyDepositionWorkspace.test.ts`：锁定单词沉淀页不再渲染重复标题和无功能顶部元素。
- `web/tests/vocabularyLearningPage.test.ts`：锁定四个功能入口仍存在，并移除对“词启”品牌文案的旧要求。

### Task 1: 精简词汇模块顶部结构

**Files:**
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`
- Modify: `web/tests/vocabularyLearningPage.test.ts`
- Modify: `web/src/views/VocabularyView.vue`

**Interfaces:**
- Consumes: `views: Array<{ key: VocabularyViewKey; label: string; icon: string }>` 和 `switchVocabularyView(view.key)`。
- Produces: 仅包含 `nav.vocabulary-nav` 的顶部栏；单词沉淀主体继续由 `VocabularyCapturePanel` 与 `VocabularyCardList` 组成。

- [ ] **Step 1: 编写导航精简的失败测试**

在 `vocabularyDepositionWorkspace.test.ts` 中把标题断言改为：

```ts
test('collection page omits duplicate headings and decorative topbar controls', () => {
  assert.doesNotMatch(view, /<h1>单词沉淀<\/h1>/)
  assert.doesNotMatch(view, /class="collection-header"/)
  assert.doesNotMatch(view, /class="brand-lockup"|class="topbar-actions"/)
  assert.doesNotMatch(view, /aria-label="通知"|aria-label="当前用户"/)
  assert.match(view, /class="vocabulary-nav"/)
  assert.match(view, /VocabularyTextCapture/)
  assert.match(view, /VocabularyImageCapture/)
  assert.match(view, /VocabularyTermReview/)
  assert.match(view, /VocabularyThemeSelect/)
})
```

在 `vocabularyLearningPage.test.ts` 的 `requiredText` 中删除 `'词启'`，并新增：

```ts
for (const navigationLabel of ['搜索单词', '背词模式', '单词沉淀', '学习统计']) {
  assert.ok(pageSource.includes(navigationLabel), `vocabulary navigation should keep ${navigationLabel}`)
}
assert.ok(!pageSource.includes('brand-lockup'), 'vocabulary navigation should not duplicate module branding')
assert.ok(!pageSource.includes('topbar-actions'), 'vocabulary navigation should not expose inactive user actions')
```

- [ ] **Step 2: 运行测试并确认预期失败**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyLearningPage.test.ts
```

Expected: FAIL，失败原因包含仍存在的 `<h1>单词沉淀</h1>`、`brand-lockup` 或 `topbar-actions`。

- [ ] **Step 3: 删除冗余模板节点并收敛顶部样式**

将 `VocabularyView.vue` 顶部模板收敛为：

```vue
<header class="vocabulary-topbar">
  <nav class="vocabulary-nav" aria-label="单词学习页面">
    <button
      v-for="view in views"
      :key="view.key"
      type="button"
      :class="{ active: activeView === view.key }"
      @click="switchVocabularyView(view.key)"
    >
      <span aria-hidden="true">{{ view.icon }}</span>
      {{ view.label }}
    </button>
  </nav>
</header>
```

删除单词沉淀分支中的 `collection-header`。顶部样式改为单列：

```css
.vocabulary-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  min-height: 60px;
  padding: 0 16px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}
```

同时删除只服务于 `brand-lockup`、`brand-mark`、`topbar-actions`、`icon-button`、`avatar` 和 `collection-header` 的 CSS 规则及响应式残留；保留 `.vocabulary-nav` 桌面居中、`1180px` 以下左对齐滚动和 `720px` 以下两列布局。

- [ ] **Step 4: 运行目标测试与构建**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyLearningPage.test.ts
npm run build
```

Expected: 两个测试文件全部 PASS，`vue-tsc` 与 Vite 构建成功。

- [ ] **Step 5: 浏览器验收桌面和移动端**

在 `http://127.0.0.1:5177/app/vocabulary?tab=collection` 验证：

- 桌面 `1423x1272`：只显示四个居中的顶部导航，不显示页面大标题、品牌、通知和头像。
- 移动端 `390x844`：四个导航以两列展示，无横向溢出、遮挡或文字裁切。
- 分别切换“搜索单词”和“单词沉淀”，确认 URL、激活态和页面内容正常。

- [ ] **Step 6: 提交实现**

```powershell
git add web/src/views/VocabularyView.vue web/tests/vocabularyDepositionWorkspace.test.ts web/tests/vocabularyLearningPage.test.ts
git commit -m "refactor(ui): 精简词汇模块顶部导航"
```
