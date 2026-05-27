# 学习助手 GPT 式归档功能 Trae 实现题目

## 背景

当前学习助手已经有“归档”菜单项，后端也有归档接口：

- `POST /assistant/conversations/{conversationUid}/archive`
- `POST /assistant/conversations/{conversationUid}/restore`

但现有体验不完整：用户点“归档”后，对话从最近列表消失，但侧边栏没有像 ChatGPT 一样清晰的“归档”入口，也缺少对已归档对话的查看、取消归档和删除管理能力。

本题目标是把学习助手归档做成接近 GPT 的交互：

```text
普通对话列表
  ↓ 点击归档
对话移入归档列表
  ↓ 打开归档
可以查看归档对话
  ↓ 菜单操作
可以取消归档 / 删除 / 分享
```

## 非目标

- 不做“导出当前对话为 Markdown 文件”。
- 不做本地文件系统归档。
- 不新增后端数据库表。
- 不新增新的前端状态库。
- 不改变现有分享、删除、重命名、移动到文件夹的接口语义。

---

## 题 1：补齐学习助手侧边栏归档视图

### 发给 Trae 的 Prompt

请实现学习助手侧边栏的 GPT 式归档入口。当前项目已经有归档接口和 `archivedConversations` 状态，但侧边栏没有清晰的“归档”分组。请修改 `AssistantSidebar.vue` 和 `AssistantConversationList.vue`，新增可折叠的“归档”分组，展示已归档对话数量和列表；归档列表复用现有对话列表组件，但菜单只保留“分享 / 取消归档 / 删除”。普通对话菜单保持不变。完成后请运行 `cd web && npm run build` 验证。

### 小题 A：在侧边栏增加“归档”分组

#### 题目 Prompt

请在学习助手侧边栏中增加一个和“最近”“文件夹”风格一致的“归档”分组，用于展示已归档对话。

要求：

1. 修改 `web/src/components/assistant/AssistantSidebar.vue`。
2. 新增 `archivedGroups` 入参，结构和当前 `groups` 保持一致。
3. 在侧边栏底部或“最近”下方展示“归档”分组。
4. “归档”分组需要可折叠。
5. 分组标题右侧显示归档对话数量。
6. 当没有归档对话时，显示“暂无归档对话”。
7. UI 风格要和当前侧边栏一致，不要引入新的组件库。

#### 题目难度

简单

#### 验收标准

- 侧边栏能看到“归档”入口。
- 点击“归档”可以展开和收起。
- 有归档对话时能看到对话列表。
- 无归档对话时显示空状态。
- 搜索历史对话时，归档列表也能按关键字过滤。
- `npm run build` 通过。

### 小题 B：归档列表复用现有对话列表组件

#### 题目 Prompt

请让“归档”分组复用现有 `AssistantConversationList.vue`，但归档列表里的菜单操作要和普通列表有所区别。

要求：

1. 修改 `web/src/components/assistant/AssistantConversationList.vue`。
2. 增加可选 prop：`archived?: boolean`。
3. 普通列表保持原有菜单：
   - 分享
   - 重命名
   - 移动到文件夹
   - 置顶聊天 / 取消置顶
   - 归档
   - 删除
4. 归档列表菜单只保留：
   - 分享
   - 取消归档
   - 删除
5. 归档列表点击对话时仍然能打开查看。
6. 不要让归档对话继续显示“归档”“置顶”“移动到文件夹”“重命名”等普通操作。

#### 题目难度

中等

#### 验收标准

- 普通对话菜单不受影响。
- 归档对话菜单显示“取消归档”。
- 点击“取消归档”会触发 `restore` 事件。
- 点击“删除”仍然触发原有 `delete` 事件。
- 点击“分享”仍然触发原有 `share` 事件。
- TypeScript 类型没有使用 `any` 绕过事件定义。
- `npm run build` 通过。

---

## 题 2：打通归档状态与页面交互

### 发给 Trae 的 Prompt

请把学习助手页面中的已归档对话接入侧边栏。当前 `createAssistantState` 已经返回 `archivedConversations` 和 `restoreConversation`，请在 `AssistantPage.vue` 中构建 `archivedConversationGroups`，分组规则和普通会话一致：今天、最近 7 天、更早。搜索框需要同时过滤普通对话和归档对话。请新增 `handleRestoreConversation`，把“取消归档”事件从侧边栏接到状态层，成功后提示“已取消归档”，失败时提示错误。完成后请运行 `cd web && npm run build` 验证。

### 小题 A：前端页面接入 archivedConversations

#### 题目 Prompt

请在学习助手页面中把 `archivedConversations` 接入侧边栏，让用户能看到并打开已归档对话。

要求：

1. 修改 `web/src/pages/app/AssistantPage.vue`。
2. 从 `createAssistantState` 中取出 `archivedConversations`。
3. 基于 `archivedConversations` 构建 `archivedConversationGroups`。
4. `archivedConversationGroups` 使用和普通会话相同的分组规则：
   - 今天
   - 最近 7 天
   - 更早
5. 搜索框关键字要同时过滤普通对话和归档对话。
6. 将 `archivedConversationGroups` 传入 `AssistantSidebar`。

#### 题目难度

中等

#### 验收标准

- 远程加载后，已归档对话会出现在“归档”分组。
- 普通“最近”列表不显示已归档对话。
- 搜索关键字可以命中归档对话标题或摘要。
- 归档分组里的对话点击后，主聊天区域能显示该对话内容。
- 不影响普通对话的新建、发送、重命名和分享。

### 小题 B：接入取消归档操作

#### 题目 Prompt

请在学习助手页面中接入“取消归档”操作，让归档对话可以恢复到普通对话列表。

要求：

1. 修改 `web/src/pages/app/AssistantPage.vue`。
2. 从 `createAssistantState` 中取出 `restoreConversation`。
3. 新增 `handleRestoreConversation(id: string)`。
4. 成功后显示 toast：“已取消归档”。
5. 失败后显示错误 toast：“取消归档失败”或后端错误信息。
6. 将 `restoreConversation` 事件透传给 `AssistantSidebar`。

#### 题目难度

简单

#### 验收标准

- 归档菜单点击“取消归档”后，会调用 restore 接口。
- 恢复成功后，对话从“归档”列表移出。
- 恢复成功后，对话出现在普通最近列表。
- 恢复成功后当前激活对话切换到被恢复的对话。
- 接口失败时不会错误移除本地归档项。

---

## 题 3：支持查看已归档对话

### 发给 Trae 的 Prompt

请修复学习助手归档对话无法像普通对话一样打开查看的问题。修改 `assistantState.ts`：`activeConversation` 需要先查普通 `conversations`，找不到时再查 `archivedConversations`；`selectConversation(id)` 也要支持选择归档对话。如果选中的是远程归档对话，需要调用现有 `assistantApi.getConversation(id)` 加载完整详情，并更新 `archivedConversations` 中对应项，而不是把它移动到普通列表。普通对话选择逻辑必须保持兼容。完成后请运行 `cd web && npm run build` 验证。

### 小题 A：让 activeConversation 能读取归档会话

#### 题目 Prompt

请调整学习助手状态逻辑，让当前激活对话可以来自普通对话列表，也可以来自归档对话列表。

要求：

1. 修改 `web/src/pages/app/assistantState.ts`。
2. `activeConversation` 计算逻辑优先从 `conversations` 找。
3. 如果普通对话列表找不到，再从 `archivedConversations` 找。
4. 仍然保留原有 fallback：找不到时使用第一个普通对话。

#### 题目难度

中等

#### 验收标准

- 点击归档对话后，主聊天区能展示归档对话内容。
- 普通对话展示逻辑不受影响。
- 普通列表为空时不会抛异常。
- TypeScript build 通过。

### 小题 B：selectConversation 支持加载归档会话详情

#### 题目 Prompt

请调整 `selectConversation`，让它既能选择普通对话，也能选择归档对话，并能从远程加载归档对话详情。

要求：

1. 修改 `web/src/pages/app/assistantState.ts`。
2. `selectConversation(id)` 判断 id 是否存在于：
   - `conversations`
   - `archivedConversations`
3. 如果是远程对话，调用现有 `assistantApi.getConversation(id)` 拉取详情。
4. 如果是归档对话，更新 `archivedConversations` 中对应项。
5. 如果是普通对话，保持原来的 `replaceConversation` 行为。
6. 加载失败时，显示“对话加载失败”。

#### 题目难度

中等

#### 验收标准

- 点击归档列表中的远程会话，会加载完整消息。
- 加载归档会话不会把它错误移动到普通列表。
- 点击普通会话仍然走原有逻辑。
- 归档会话加载失败时显示错误信息。
- 不破坏附件恢复逻辑。

---

## 推荐验证流程

### 手工验证

1. 打开学习助手。
2. 新建一个对话并发送一条消息。
3. 在侧边栏对话菜单点击“归档”。
4. 确认对话从“最近”列表消失。
5. 展开“归档”分组。
6. 确认归档对话出现在列表中。
7. 点击归档对话，确认主区域能显示聊天内容。
8. 打开归档对话菜单，确认有“分享 / 取消归档 / 删除”。
9. 点击“取消归档”，确认对话回到“最近”列表。
10. 再次归档后点击“删除”，确认归档列表移除该对话。

### 构建验证

```bash
cd web
npm run build
```

### 验收总结

本题完成后，学习助手归档应达到接近 GPT 的基础体验：

- 归档只是隐藏普通列表，不是导出文件。
- 已归档内容仍然可找回、可查看、可恢复、可删除。
- 普通对话和归档对话在 UI 上清晰分区。
- 不需要新增后端接口，只复用现有 archive / restore / delete / share 能力。
