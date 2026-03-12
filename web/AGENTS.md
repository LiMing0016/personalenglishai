# 前端技术栈

前端主要技术栈：

- Vue 3
- TypeScript
- Pinia
- TanStack Query (`@tanstack/vue-query`)
- TipTap
- Vue Router
- Axios
- ECharts
- Vite

## 核心职责

前端主要负责：

- 写作页与作文列表交互
- 富文本写作编辑器
- 评分、语法、润色、改写、翻译面板
- 文档状态恢复与本地持久化
- 选区、错误高亮、替换与忽略交互
- 个人中心与历史评估展示

## 编码原则

1. 优先做小范围、安全、可验证的修改。
2. 除非明确要求，不要重写写作页主链路。
3. 优先保持现有 API 和前端数据结构兼容。
4. 状态流要先收敛再扩展，避免继续叠加新的状态来源。
5. 不要为了修单个 UI 问题破坏编辑器、评分或语法链路。
6. 引入新的前端框架扩展、插件或状态层之前，先确认现有栈是否已经足够

## 框架扩展规则

在考虑新增前端框架扩展、编辑器扩展、状态库、组件库或工具库前，必须先评估：

1. 现有 Vue 3 / Pinia / TanStack Query / TipTap / Vite 是否已经能完成需求
2. 新扩展解决的是真实工程缺口，还是只是为了规避当前状态设计问题
3. 对包体积、首屏性能、心智负担、样式一致性、调试复杂度的影响
4. 对写作页主链路、编辑器插件、高亮逻辑、缓存恢复的兼容性

如果决定引入新扩展，必须说明：

- 为什么现有实现不足
- 为什么选这个扩展而不是收敛当前状态/组件设计
- 是否会增加新的持久化层、状态源或运行时依赖
- 是否需要同步更新构建、懒加载、文档或环境说明

## 目录与职责约束

写作主链路主要集中在：

- `src/pages/app/WritingPage.vue`
- `src/components/writing/EditorShell.vue`
- `src/components/writing/DocEditor.vue`
- `src/components/writing/RightPanel.vue`
- `src/components/writing/panels/*`
- `src/stores/*`
- `src/composables/useEvaluateSubmission.ts`

职责要求：

WritingPage
- 负责页面级路由与文档列表/入口状态
- 不承载复杂编辑器状态机

EditorShell
- 负责写作页主编排
- 负责连接编辑器、面板、store、缓存恢复与提交流程
- 避免继续向下透传过多状态 props

DocEditor
- 负责 TipTap 编辑器行为与高亮渲染
- 不承载评分、语法、润色业务决策

Panels
- 只负责各自展示与交互
- 不要在面板内部复制主状态源

Store
- 负责跨组件共享状态与持久化边界
- 不要让多个组件分别维护同一份核心状态

## 状态管理规则

写作页状态属于高风险区域，必须优先保证“单一真源”。

### Pinia 与 TanStack Query 的边界

- Pinia 负责客户端状态、编辑器状态、面板状态、持久化状态。
- TanStack Query 负责服务端请求、轮询、缓存与请求生命周期。
- 不要让同一份核心数据同时长期存在于 `props + ref + store + storage + query` 五个地方。
- Query 返回结果进入页面后，如需跨刷新恢复，应收口到 store，不要在多个组件各存一份镜像。

### 当前高风险状态

新增 `store`、`storage key`、组件内镜像状态或独立 `composable` 前，必须先确认现有 `writingDraftStore`、`grammarStore`、`evaluateStore`、`panelStore` 是否已经可以承载；没有充分理由，不要再新增一层状态源。

以下状态修改时必须先梳理来源与去向：

- draftText / docId / writingMode / taskPrompt
- evaluateResult / evaluatedText / submitCount / examMaxScore
- grammarErrors / grammarReChecked / fixedIds / dismiss 状态
- polishSuggestions / rewriteSuggestions / translateResult
- 当前面板、选中句子、选区、高亮状态

### 禁止行为

- 为了修 bug 再新增一层本地 `ref` 镜像而不清理旧状态源
- 在 `watch` 中同时做状态同步、storage 写入、请求触发、toast 提示等多重副作用
- 在多个组件中直接操作同一份 storage key

## 本地持久化规则

项目已大量使用 `localStorage` 与 `sessionStorage`，修改前必须先确认作用域和生命周期。

规则：

1. 持久化键必须尽量按 `docId` 分桶，避免跨作文污染。
2. `sessionStorage` 优先用于当前会话状态；`localStorage` 仅用于明确需要跨刷新保留的内容。
3. store 负责持久化边界，组件不要直接随意写 storage，除非该组件就是存储适配层。
4. `reset in-memory` 与 `clear persisted` 必须分开，禁止一键重置时误清恢复缓存。
5. 修改恢复逻辑时，必须考虑刷新、重进作文、退出放弃、清空内容四种场景。

## TipTap 与富文本编辑器规则

1. 不要轻易改动 TipTap 初始化、文本标准化、selection 映射与错误高亮插件接口。
2. 修改高亮、替换、忽略、span 映射逻辑时，必须考虑：
   - 多段落文本
   - 粘贴来源差异（如 OneNote）
   - 富文本节点结构变化
   - 替换后 offset 漂移
   - 刷新恢复后的重建行为
3. 编辑器内的错误高亮、句子高亮、选区展示必须和右侧面板使用同一套来源数据。
4. 不要在编辑器组件里直接重新发评分、语法、润色请求。

## 评分 / 语法 / 润色规则

这些链路在前端必须保持职责清晰：

评分
- 评分提交、轮询、恢复优先走统一 store + composable 边界。
- 修改评分逻辑时，必须说明对轮询、恢复、历史结果、刷新行为的影响。

语法
- 修改 grammar 逻辑时，必须评估：
  - 错误列表来源
  - dismiss / fix / suppress 状态
  - 刷新恢复
  - 与评分错误回填的关系
  - 高亮和右侧卡片是否一致

润色 / 改写 / 翻译
- 面板内结果如需跨刷新保留，应明确使用哪一层存储。
- 不要把临时展示状态误当成可持久恢复状态。

## API / DTO 兼容规范

1. 修改 `src/api/*`、`src/types/*`、面板消费字段时，优先新增字段而不是直接改名或删除。
2. 后端返回字段变化时，必须检查：
   - store 是否兼容
   - 面板展示是否兼容
   - 缓存恢复是否兼容
   - 历史数据解析是否兼容
3. 不要让组件直接拼接“猜测字段”。

## 性能与包体积规则

1. 写作页是高频页面，新增依赖时先评估首屏影响。
2. 低频面板优先懒加载；首屏必用的编辑器核心保持静态加载。
3. ECharts 等重依赖优先按需导入，避免直接全量导入。
4. 不要为了方便把大库直接塞进 `WritingPage.vue` 首屏依赖。

## UI 与交互一致性规则

1. 保持写作区、右侧面板、工具栏、归档/返回流程的一致性。
2. 修改文案、按钮、交互时，优先保持考试写作与自由写作模式都能成立。
3. 不要只修右侧面板而忽略左侧编辑区联动。
4. 任何“忽略 / 替换 / 一键修正 / 开始润色”交互，都要检查刷新和重进后的状态表现。

## 测试与验证要求

前端代码改动后，至少说明实际运行了哪些验证。

推荐验证：

```bash
npm run build
```

涉及写作页时，优先人工回归以下场景：

1. 新建自由写作 / 考试写作
2. 输入、刷新、重进、退出再进入
3. 提交评分并轮询完成
4. 语法检查、替换、忽略、一键修正
5. 润色、改写、翻译面板切换
6. 高亮是否与右侧卡片一致

如果没有运行某项验证，必须明确说明。



