# 单词端前后端接入设计 Trae 题目

## 背景

当前 `/app/vocabulary` 单词端页面已经完成了一个接近目标稿的前端界面，并且页面上已经标记了接口接入状态：

- `GET /api/dictionary/lookup`：已接入，顶部搜索框真实调用 Oxford 词典后端接口。
- `learning_raw_candidate / learning_evidence`：后端表已落地，但单词页还没有用户侧读取 API。
- 重点单词列表、每日复习队列、学习操作、学习统计：仍是静态数据或本地状态。

本轮 Trae 题目目标是把“单词学习页”从静态展示推进到真实数据闭环：

```text
assistant_message
-> learning_raw_candidate / learning_evidence
-> 用户侧单词学习 API
-> /app/vocabulary 前端展示
-> 加入复习 / 标记掌握 / 收藏
-> 后续 learning_review_queue
```

参考文件：

- `web/src/views/VocabularyView.vue`
- `web/src/api/dictionary.ts`
- `backend/src/main/java/com/personalenglishai/backend/service/learning/`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/migrate_create_learning_capture_tables.sql`
- `docs/agent/数据清洗/对话词句采集清洗方案.md`
- `web/AGENTS.md`
- `backend/AGENTS.md`

核心原则：

- 单词页不能继续让真实数据和 mock 数据混在一起而无标记。
- 先接“只读列表和详情”，再接“写操作和复习队列”。
- 用户侧 API 必须只返回当前登录用户的数据。
- 后端候选池 `learning_raw_candidate` 不等同最终学习项，前端默认读取 `learning_evidence` 或经过服务层聚合后的 DTO。
- 前端必须保留接口接入状态标记，直到对应接口真实接入并通过验收。

---

## 题目 1：梳理单词端现状与接口缺口

难度：中等

### A 小题：梳理当前前端数据源

请阅读 `web/src/views/VocabularyView.vue`，梳理页面内每一块数据当前来自哪里。

至少覆盖：

1. 顶部搜索框。
2. 学习概览指标。
3. 重点单词列表。
4. 单词详情面板。
5. 今日学习计划。
6. 复习队列。
7. 学习建议。
8. 学习成就。
9. 加入复习、标记掌握、收藏按钮。

### B 小题：梳理后端已有能力与缺口

请阅读后端单词、词典、学习采集相关代码，输出后端已有 API 和缺失 API。

至少覆盖：

1. `GET /api/dictionary/lookup` 是否已接入。
2. `learning_extraction_run`、`learning_raw_candidate`、`learning_evidence` 是否已落库。
3. 是否存在用户侧读取单词候选的 API。
4. 是否存在每日复习队列 API。
5. 是否存在加入复习、标记掌握、收藏 API。
6. 是否存在学习统计 API。

### 给 Trae 的 Prompt

请基于当前代码梳理 `/app/vocabulary` 单词端的前后端接入现状。不要写新功能，只输出一份清单：每个页面模块的数据来源、已接 API、未接 API、后端已有表和缺失接口。要求结论能直接指导后续开发。

### 验收标准

- 能明确指出只有 Oxford 查询已真实接入。
- 能明确指出重点单词列表、复习队列、学习操作仍未接真实用户侧 API。
- 能列出相关前端文件、后端 controller/service/mapper/table。
- 不修改业务代码。

---

## 题目 2：设计用户侧单词学习 API 契约

难度：中等偏难

### A 小题：设计只读 API

请设计用户侧只读 API，用于支撑单词页首屏。

建议接口：

1. `GET /api/learning/vocabulary/overview`
2. `GET /api/learning/vocabulary/items`
3. `GET /api/learning/vocabulary/items/{itemId}`
4. `GET /api/learning/vocabulary/review-queue`

要求说明：

- 请求参数。
- 响应 DTO。
- 分页字段。
- 排序规则。
- 字段如何从 `learning_evidence` / `learning_raw_candidate` 聚合。
- 哪些字段第一版可为空或后续补齐。

### B 小题：设计写操作 API

请设计用户操作 API。

建议接口：

1. `POST /api/learning/vocabulary/items/{itemId}/review`
2. `POST /api/learning/vocabulary/items/{itemId}/mastery`
3. `POST /api/learning/vocabulary/items/{itemId}/favorite`
4. `DELETE /api/learning/vocabulary/items/{itemId}/favorite`
5. `POST /api/learning/vocabulary/review-sessions`

要求说明：

- 幂等性规则。
- 权限规则。
- 错误码。
- 写入哪些表。
- 如果 `learning_review_queue` 尚未实现，第一版如何降级处理。

### 给 Trae 的 Prompt

请设计单词学习模块用户侧 API 契约，不写实现代码。API 要支撑 `/app/vocabulary` 当前页面，包括概览、重点单词列表、详情、复习队列、加入复习、标记掌握、收藏。说明 DTO 字段与 `learning_evidence`、`learning_raw_candidate` 的映射关系。

### 验收标准

- 输出 API 列表、请求参数、响应 JSON 示例。
- 能区分 P0 必须字段和 P1 可后补字段。
- 明确当前登录用户隔离规则。
- 明确与现有采集清洗表的关系。

---

## 题目 3：实现单词学习只读后端 API

难度：困难

### A 小题：实现 overview 与 items 列表

请实现用户侧只读接口：

- `GET /api/learning/vocabulary/overview`
- `GET /api/learning/vocabulary/items`

要求：

1. 新增 controller、service、mapper、DTO，遵守现有后端分层。
2. 从当前登录用户的 `learning_evidence` 和 `learning_raw_candidate` 聚合数据。
3. `items` 支持：
   - `type`
   - `status`
   - `keyword`
   - `page`
   - `size`
4. 默认优先返回 `status = pending` 且高分的 evidence。
5. 不返回其他用户数据。

### B 小题：实现详情 API

请实现：

- `GET /api/learning/vocabulary/items/{itemId}`

要求：

1. 返回单词/短语/句子的详情 DTO。
2. 包含来源消息、来源摘录、出现次数、分数、候选类型、例句。
3. 如果 item 来自 `learning_evidence`，需要能回溯到 candidate。
4. 没有权限或不存在时返回合理错误。

### 给 Trae 的 Prompt

请实现单词学习模块 P0 只读后端 API。数据源来自 `learning_evidence` 和 `learning_raw_candidate`，面向当前登录用户返回可学习词句。不要实现复习队列和写操作。补齐 mapper XML、DTO、controller/service 测试。

### 验收标准

- 新增接口能通过 MockMvc 或 service 测试。
- `items` 能按用户、状态、分数、分页查询。
- 详情接口不会泄露其他用户数据。
- `mvn test` 通过。
- 不改动聊天主链路。

---

## 题目 4：前端接入重点单词列表 API

难度：中等偏难

### A 小题：新增前端 API wrapper 与类型

请新增前端 API 封装，例如：

- `web/src/api/learningVocabulary.ts`

要求：

1. 使用现有 `http` 客户端。
2. 定义 overview、items、detail 的 TypeScript 类型。
3. 保持字段命名与后端 DTO 对齐。
4. 不在组件里直接拼 URL。

### B 小题：替换重点单词列表静态数据

请在 `VocabularyView.vue` 中接入真实列表接口。

要求：

1. 页面加载时请求 `GET /api/learning/vocabulary/items`。
2. loading、empty、error 状态可见。
3. 接口失败时可以展示 mock fallback，但必须显示“接口失败，当前为本地示例”。
4. 接入成功后，把“重点单词列表”标记从 `未接入` 改为 `已接入`。

### 给 Trae 的 Prompt

请把 `/app/vocabulary` 的重点单词列表从静态数据改为真实 API 数据。新增 `learningVocabulary` API wrapper 和类型定义，在页面中处理 loading、empty、error，并保留 fallback 标记。不要一次性接入复习队列和写操作。

### 验收标准

- `web/src/api/learningVocabulary.ts` 存在并使用共享 `http`。
- `VocabularyView.vue` 不再直接依赖静态 `words` 作为主数据源。
- 接口成功时页面显示真实数据并标记“已接入”。
- 接口失败时页面明确显示 fallback。
- `npm run build` 通过。

---

## 题目 5：实现单词详情真实数据接入

难度：中等偏难

### A 小题：后端详情字段补齐

请检查 `GET /api/learning/vocabulary/items/{itemId}` 是否能支撑详情面板。

详情字段至少包括：

- `id`
- `type`
- `text`
- `normalizedText`
- `meaning`
- `partOfSpeech`
- `example`
- `translation`
- `sourceExcerpt`
- `sourceMessageIds`
- `occurrenceCount`
- `score`
- `status`
- `createdAt`

如果部分字段当前没有可靠来源，需要返回 `null` 并在 DTO 注释中说明。

### B 小题：前端详情面板接入 API

请把右侧详情面板从静态对象改成按选中 item 请求详情。

要求：

1. 点击列表项后请求详情 API。
2. Oxford 查询结果仍然只由顶部搜索触发，不要和详情接口混在一起。
3. 详情 loading、empty、error 状态可见。
4. 详情卡片标记从“详情静态 + 词典查询已接”更新为真实状态。

### 给 Trae 的 Prompt

请完成单词详情真实数据接入。后端详情 DTO 要覆盖前端详情面板所需字段，前端点击列表项后加载详情。注意区分“学习详情 API”和“Oxford 词典查询 API”，不要把两者混成一个接口。

### 验收标准

- 点击不同单词能展示不同详情。
- 详情接口失败不会导致整个页面崩溃。
- Oxford 查询仍可独立使用。
- `npm run build` 和后端相关测试通过。

---

## 题目 6：设计并落地学习操作状态

难度：困难

### A 小题：设计学习状态与收藏状态落库

请设计学习操作需要的后端状态。

可选方案：

1. 新增 `learning_vocabulary_item_state` 用户状态表。
2. 复用 `learning_evidence.status`，但扩展能力有限。
3. 等 `learning_review_queue` 完成后统一落状态。

需要给出推荐方案，并说明：

- `favorite`
- `mastery`
- `reviewStatus`
- `lastReviewedAt`
- `ignored`
- `sourceEvidenceUid`

如何存储。

### B 小题：实现 P0 写操作 API

请实现：

- 加入复习
- 标记掌握
- 收藏 / 取消收藏

要求：

1. 操作必须按当前用户隔离。
2. 重复点击需要幂等。
3. 前端乐观更新失败时要回滚或提示。
4. 页面标记从“本地模拟”变为“已接入”。

### 给 Trae 的 Prompt

请设计并实现单词页的 P0 学习操作状态。优先用独立用户状态表保存收藏、掌握、加入复习等状态，不要直接污染原始候选池。补后端 API、前端调用和失败提示。

### 验收标准

- 重复加入复习不会生成重复状态。
- 标记掌握后刷新页面仍保留状态。
- 收藏/取消收藏可持久化。
- 操作失败时前端有明确提示。
- 后端和前端测试通过。

---

## 题目 7：设计并实现每日复习队列 P0

难度：困难

### A 小题：新增 `learning_review_queue` 表和生成规则

请新增每日复习队列表。

建议字段：

- `queue_uid`
- `user_id`
- `item_state_uid`
- `evidence_uid`
- `review_date`
- `priority_score`
- `review_type`
- `status`
- `due_at`
- `completed_at`
- `created_at`
- `updated_at`

第一版生成规则：

1. 用户主动加入复习的内容优先。
2. 高分 pending evidence 可进入候选。
3. 每天限制数量，避免一次性塞太多。
4. 暂不实现完整 SRS，只预留字段。

### B 小题：接入前端复习队列卡片

请把右侧“复习队列”从静态数据改为读取 API。

要求：

1. 页面加载时读取今日队列。
2. 支持空状态。
3. “开始复习”按钮如果流程未实现，需要显示“复习流程未接入”。
4. 队列卡片状态标记从“未接 learning_review_queue”改为真实状态。

### 给 Trae 的 Prompt

请实现单词页每日复习队列 P0，包括数据库表、队列生成/读取 API、前端右侧队列卡片接入。第一版不要追求完整 SRS，只要能展示今天应该复习的词句，并能和“加入复习”操作打通。

### 验收标准

- `learning_review_queue` 表存在。
- 加入复习后能在今日队列中看到。
- 今日队列接口只返回当前用户数据。
- 队列为空时页面有明确空状态。
- `mvn test`、`npm run build` 通过。

---

## 题目 8：接入学习概览与统计

难度：中等

### A 小题：实现学习概览 API

请实现：

- `GET /api/learning/vocabulary/overview`

返回字段建议：

- `todayReviewCount`
- `newItemCount`
- `masteredCount`
- `streakDays`
- `pendingEvidenceCount`
- `reviewQueueCount`

要求：

1. 统计当前登录用户。
2. 能兼容复习队列表尚未完全实现的情况。
3. 不要在 controller 中写 SQL 统计逻辑。

### B 小题：前端接入 metric cards

请把页面顶部四个指标卡接入 overview API。

要求：

1. loading 时显示骨架或轻量占位。
2. 接口失败时保留本地示例并显示 fallback 标记。
3. 接入成功后把指标卡从“本地模拟”改为“已接入”。

### 给 Trae 的 Prompt

请实现单词页学习概览统计 API，并把 `/app/vocabulary` 顶部四个 metric card 改为真实数据。注意统计当前用户，不要跨用户聚合。接口失败时允许 fallback，但必须明确标记。

### 验收标准

- 概览 API 有后端测试。
- 前端指标卡显示真实统计。
- 无数据用户能正常显示 0 值。
- `npm run build` 和 `mvn test` 通过。

---

## 题目 9：补齐接口接入状态验收面板

难度：中等

### A 小题：让状态面板由代码常量驱动

请整理 `VocabularyView.vue` 中的接口接入状态。

要求：

1. 所有状态项集中在 `apiStatusItems` 或独立配置中。
2. 每个状态必须包含：
   - 名称
   - endpoint
   - 状态
   - 说明
3. 不要在模板中散落硬编码状态说明。

### B 小题：根据真实接入自动更新状态

请让状态面板能反映真实接入情况。

要求：

1. Oxford 查询保持 `已接入`。
2. 列表 API 成功后显示 `已接入`，失败 fallback 显示 `接口失败` 或 `本地示例`。
3. 复习队列 API 未实现时显示 `未接入`。
4. 学习操作 API 未实现时显示 `本地模拟`。

### 给 Trae 的 Prompt

请把单词页顶部“接口接入状态”做成可维护验收面板。它不是装饰 UI，而是开发验收工具。每接入一个真实 API，就能清楚看到状态从未接入/本地模拟变为已接入。

### 验收标准

- 页面能清楚显示每个模块接入状态。
- 状态配置集中，后续容易维护。
- 测试覆盖关键文案。
- `npm run build` 通过。

---

## 题目 10：端到端验收真实对话词句进入单词页

难度：困难

### A 小题：准备后端验收数据链路

请设计并实现一个可重复的验收流程，验证用户对话数据能进入单词页。

流程建议：

```text
发送一轮包含英文表达的学习助手对话
-> assistant_message / user message 落库
-> LearningCaptureService 写入 extraction run
-> local extractor 写入 raw candidate
-> evidence builder 写入 learning_evidence
-> 单词页 items API 读取
```

要求：

1. 提供 SQL 查询脚本或测试辅助方法。
2. 验收数据必须可清理。
3. 不依赖真实 DeepSeek 调用。

### B 小题：前端页面验收

请用 Playwright 或等价方式验证 `/app/vocabulary`。

要求：

1. 登录或 mock 登录态。
2. 打开 `/app/vocabulary`。
3. 能看到从测试对话中提取的词或短语。
4. 能看到接口状态为已接入。
5. Oxford 查询仍可独立触发。

### 给 Trae 的 Prompt

请补齐“真实对话词句进入单词页”的端到端验收。不要只测静态页面，要从后端 learning evidence 数据出发，让单词页读取并展示出来。DeepSeek 可跳过，使用 local extractor 或测试数据即可。

### 验收标准

- 有可重复执行的验收步骤。
- 能证明数据不是前端 mock。
- 能证明用户隔离生效。
- 能证明词典查询和学习列表互不影响。
- 验收后能清理测试数据。

