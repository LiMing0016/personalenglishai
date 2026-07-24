# 单词卡 Typora 式 Markdown 编辑器实施计划

**目标：** 保留核心词义 JSON 和现有版本冲突能力，把 AI 学习内容统一为单页 Markdown 编辑体验，并提供右侧章节目录。

**架构：** 现有 Card Blocks 继续用于历史读取和兼容；进入编辑时通过纯函数转换成 Markdown，保存后创建 Markdown 用户修订。编辑器使用项目已有 TipTap，增加官方 `@tiptap/markdown` 扩展实现 Markdown 双向解析和序列化，不新增状态层。

**范围约束：**

- 核心词义仍由 `VocabularyCoreContent` 提供，学习内容编辑器不能修改单词、音标、词性和核心释义。
- 保留现有 `baseRevisionUid`、操作锁和冲突处理。
- 首期仍由用户点击“保存修改”提交，不在本次引入自动保存请求和额外持久化状态。
- 不删除 Card Blocks 后端兼容代码；只收敛前端编辑主路径。

## Task 1：Markdown 兼容转换

**文件：**
- 新增：`web/src/features/vocabulary/vocabularyLearningMarkdown.ts`
- 新增：`web/tests/vocabularyLearningMarkdown.test.ts`

- [ ] 先写测试，覆盖全部 Card Blocks 类型、顺序、标题、个人笔记和 Markdown 转义。
- [ ] 实现 Card Blocks 到单一 Markdown 文档的纯转换函数。
- [ ] 提取二级标题目录，供编辑器和移动端目录复用。

## Task 2：TipTap 所见即所得编辑器

**文件：**
- 修改：`web/package.json`
- 修改：`web/package-lock.json`
- 修改：`web/src/components/vocabulary/VocabularyMarkdownEditor.vue`
- 修改：`web/tests/vocabularyCoreSummary.test.ts`

- [ ] 增加官方 `@tiptap/markdown`，使用 Markdown 作为输入和输出。
- [ ] 实现单页无边框编辑画布、选区浮动格式栏和高级源码模式。
- [ ] 实现桌面右侧粘性目录、当前章节状态和移动端目录抽屉。
- [ ] 保留 20,000 字限制和无障碍状态提示。

## Task 3：详情页编辑链路收敛

**文件：**
- 修改：`web/src/components/vocabulary/VocabularyCardInspector.vue`
- 修改：`web/tests/vocabularyCardInspector.test.ts`
- 修改：`web/tests/vocabularyCardBlocks.test.ts`

- [ ] 进入编辑时统一加载 Markdown，Card Blocks 卡片先进行兼容转换。
- [ ] 保存时只提交核心词义快照和 Markdown，不再展示块表单编辑器。
- [ ] 保存成功后继续采用服务端返回的新修订，保持冲突和操作锁行为。

## Task 4：验证与文档

**文件：**
- 修改：`docs/architecture/vocabulary-deposition.md`

- [ ] 运行相关 Node 测试。
- [ ] 运行 `npm run build`。
- [ ] 运行 `git diff --check` 并检查未覆盖现有未提交改动。
- [ ] 使用用户已选择的应用内浏览器验证桌面和移动端；不使用 Playwright。
