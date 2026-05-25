# 作文归档与作文资产库 Trae 实现题目

## 背景

当前写作页已经支持作文创建、编辑、评分、语法、润色、范文、素材、翻译和写作教练等能力。下一步需要把用户写过的作文沉淀为“作文资产”，让用户可以把重要作文归档，并在用户中心集中查看和继续编辑。

本题中的“归档”不是删除、隐藏或冻结，而是资产化标记：

```text
写作编辑器 / 写作历史
  ↓
用户点击归档
  ↓
documents.status = 2
  ↓
用户中心 > 作文资产集中展示
  ↓
用户仍可继续编辑、评分、取消归档
```

数据库中已有 `documents.status` 字段，其中 `2` 表示 archived。本题应复用该字段，不新增表结构。

## 总体要求

1. 使用现有 `documents.status=2` 作为归档状态。
2. 不改变生产登录逻辑、写作创建流程、评分流程和 Dashboard 统计逻辑。
3. 归档作文仍保留在普通写作历史中，只额外展示“已归档”标识。
4. 归档作文从用户中心打开后仍可编辑，编辑后保持归档状态。
5. 用户中心新增独立 Tab“作文资产”，不要替换现有“我的作文”。
6. 归档前需要保存当前作文内容，避免用户刚编辑的内容没有进入资产库。
7. 不通过 mock token、IP、User-Agent 或浏览器特征绕过鉴权。

## 非目标

- 不做复杂文件夹、标签、多级分类。
- 不把归档作文从普通历史列表或 Dashboard 中移除。
- 不把归档作文变成只读。
- 不改生产登录、路由守卫或权限模型。
- 不新增数据库迁移。
- 不重写写作页主链路。

---

## 题 1：后端文档归档接口

### 发给 Trae 的 Prompt

请为作文文档新增归档和取消归档能力，复用 `documents.status=2` 表示已归档。接口必须校验当前用户是文档 owner，不允许归档或恢复他人作文。不改变现有删除、恢复、评分和写作会话逻辑。

### 小题 A：DocumentService 增加归档能力

#### 题目 Prompt

请在后端 `DocumentService` 中新增文档归档能力。

要求：

1. 新增 `archiveDocument(tenantId, workspaceId, publicDocId, userId)`。
2. 新增 `unarchiveDocument(tenantId, workspaceId, publicDocId, userId)`。
3. 查找文档时复用当前租户、工作区和 `publicDocId`。
4. 文档不存在时抛出 `DOC_NOT_FOUND`。
5. 当前用户不是 owner 时抛出 `DOC_FORBIDDEN`。
6. 归档时更新 `status=2`。
7. 取消归档时更新 `status=1`。
8. 不影响 `softDelete` / `restore` 的语义。

#### 题目难度

简单

#### 验收标准

- owner 可以归档自己的作文。
- owner 可以取消归档自己的作文。
- 非 owner 归档返回 forbidden。
- 文档不存在返回 not found。
- 单元测试覆盖成功和 forbidden 场景。

### 小题 B：DocumentController 暴露归档 API

#### 题目 Prompt

请在现有文档 API 下新增归档接口。

建议接口：

```http
PATCH /api/docs/{docId}/archive
PATCH /api/docs/{docId}/unarchive
```

要求：

1. 从当前请求中读取 `userId`。
2. 调用 `DocumentService.archiveDocument` / `unarchiveDocument`。
3. 成功返回 `204 No Content`。
4. 不改现有 `POST /api/docs`、`GET /api/docs/{docId}`、`DELETE /api/docs/{docId}` 行为。
5. `GET /api/docs/{docId}` 返回中补充：
   - `status`
   - `archived`

#### 题目难度

简单

#### 验收标准

- `PATCH /api/docs/{docId}/archive` 成功返回 204。
- `PATCH /api/docs/{docId}/unarchive` 成功返回 204。
- forbidden 能按现有全局异常处理返回 403。
- 获取文档内容时能看到 `archived: true/false`。

---

## 题 2：写作文档列表支持作文资产查询

### 发给 Trae 的 Prompt

请扩展 `/api/writing/documents`，让它支持查询已归档作文资产，同时保持默认行为兼容：不传参数时仍返回全部未删除作文，传 `archived=true` 时只返回已归档作文。

### 小题 A：Mapper 支持归档过滤

#### 题目 Prompt

请扩展 `DocumentMapper` 和 `DocumentMapper.xml`，让按 owner 查询文档列表和 count 时支持可选归档过滤。

要求：

1. `listByOwnerUserId` 增加可选参数 `archived`。
2. `countByOwnerUserId` 增加可选参数 `archived`。
3. 当 `archived=true` 时增加 `status = 2` 条件。
4. 当 `archived` 为空或 false 时保持当前查询行为，不过滤归档作文。
5. 继续过滤 `deleted_at IS NULL`。
6. 排序仍按 `updated_at DESC`。

#### 题目难度

中等

#### 验收标准

- 默认列表仍返回全部未删除作文。
- `archived=true` 只返回 `status=2` 作文。
- count 和 list 的过滤条件一致。
- 不影响已有写作历史分页。

### 小题 B：WritingController 返回 archived 字段

#### 题目 Prompt

请扩展 `GET /api/writing/documents` 响应。

要求：

1. 新增 query 参数 `archived?: boolean`。
2. 不传 `archived` 时走原有全部列表。
3. `archived=true` 时只查询归档作文。
4. 每个 item 增加 `archived` 字段，值为 `status === 2`。
5. 保留现有字段：
   - `docId`
   - `title`
   - `taskPrompt`
   - `initialScore`
   - `latestScore`
   - `submitCount`
   - `status`
   - `createdAt`
   - `updatedAt`

#### 题目难度

简单

#### 验收标准

- `/api/writing/documents` 默认兼容旧前端。
- `/api/writing/documents?archived=true` 只返回归档作文。
- 响应 item 包含 `archived: true/false`。
- Controller 测试覆盖默认列表和归档过滤。

---

## 题 3：写作编辑器右侧新增“归档”工具

### 发给 Trae 的 Prompt

请在写作编辑器右侧工具栏新增“归档”入口。点击后打开归档面板，用户可以把当前作文归档到作文资产库。归档前要保存当前编辑内容，归档成功后不退出编辑器，用户可以继续编辑。

### 小题 A：扩展 ToolRail 和 PanelMode

#### 题目 Prompt

请为写作编辑器右侧工具栏增加 `archive` 面板模式。

要求：

1. 在 `ToolRail.vue` 的 `PanelMode` 增加 `archive`。
2. 在 `toolRailState.ts` 中增加工具项：
   - label: `归档`
   - title: `归档作文`
3. 在 `panelStore.ts` 的合法 panel 列表和标题映射中加入 `archive`。
4. 如果还有其它 `Record<PanelMode, string>` 映射，也要补齐 `archive`，避免 TypeScript build 失败。
5. 图标风格与现有右侧工具栏一致。

#### 题目难度

简单

#### 验收标准

- 写作编辑器右侧出现“归档”按钮。
- 点击“归档”可以打开右侧面板。
- `npm run build` 不出现 `PanelMode` 类型遗漏。

### 小题 B：新增 WritingArchivePanel

#### 题目 Prompt

请新增写作归档面板组件。

建议文件：

```text
web/src/components/writing/panels/WritingArchivePanel.vue
```

要求：

1. 展示当前作文标题。
2. 展示当前归档状态：
   - 未归档
   - 已归档
3. 未归档时显示“归档当前作文”按钮。
4. 已归档时显示“取消归档”按钮。
5. 说明归档不会隐藏作文，也不会影响继续编辑。
6. 没有 `docId` 时禁用操作，并提示保存后才能归档。
7. 样式保持写作右侧面板风格，不要引入新 UI 库。

#### 题目难度

中等

#### 验收标准

- 面板能正确显示标题和状态。
- 未归档作文可点击归档。
- 已归档作文可点击取消归档。
- loading 状态下按钮不可重复点击。
- 空 `docId` 不会发请求。

### 小题 C：归档前保存当前内容

#### 题目 Prompt

请在 `EditorShell.vue` 中接入归档操作。

要求：

1. 归档前调用现有保存 revision 的 API，保存当前 `draftText`。
2. 保存成功后调用归档 API。
3. 归档成功后更新当前 draft store 的 `archived` 状态。
4. 取消归档成功后更新当前 draft store 的 `archived` 状态。
5. 归档/取消归档后不退出编辑器。
6. 归档失败要 toast 提示，不要清空草稿。
7. 如果父页面有作文列表缓存，需要同步当前 doc 的归档状态。

#### 题目难度

中等

#### 验收标准

- 编辑器里新增文字后点击归档，后端保存的是最新内容。
- 归档成功后面板状态变为“已归档”。
- 取消归档成功后面板状态变为“未归档”。
- 归档失败不会丢失当前编辑内容。
- 返回写作历史后该作文显示正确归档状态。

---

## 题 4：写作历史保留归档作文并增加操作

### 发给 Trae 的 Prompt

请让普通写作历史继续显示归档作文，并增加“已归档”标识和“归档 / 取消归档”菜单项。不要把归档作文从普通历史中隐藏。

### 小题 A：写作历史卡片展示归档状态

#### 题目 Prompt

请在 `WritingPage.vue` 的写作历史卡片中展示归档状态。

要求：

1. `WritingDocumentItem` 类型增加 `archived?: boolean`。
2. 状态判断优先使用 `archived`，兼容 `status === 2`。
3. 已归档时状态 pill 文案显示 `已归档`。
4. 已归档样式与已有状态 pill 保持一致。
5. 不改变普通作文、题目草稿、已评分、待评分等状态逻辑。

#### 题目难度

简单

#### 验收标准

- 归档作文在写作历史仍可见。
- 归档作文显示“已归档”。
- 未归档作文原状态展示不变。

### 小题 B：写作历史菜单增加归档操作

#### 题目 Prompt

请在写作历史卡片更多菜单中增加归档操作。

要求：

1. 未归档作文显示“归档”。
2. 已归档作文显示“取消归档”。
3. 点击后调用对应 API。
4. 成功后更新当前卡片的 `status` 和 `archived`。
5. 操作失败 toast 提示。
6. 不影响现有重命名和删除操作。

#### 题目难度

简单

#### 验收标准

- 卡片菜单可以归档作文。
- 卡片菜单可以取消归档作文。
- 成功后 UI 状态立即更新。
- 重命名、删除仍可用。

---

## 题 5：用户中心新增“作文资产”Tab

### 发给 Trae 的 Prompt

请在用户中心新增独立 Tab“作文资产”，展示已归档作文。该入口用于沉淀用户的核心写作资产，不替换现有“我的作文”。

### 小题 A：PersonalCenterPage 增加资产入口

#### 题目 Prompt

请在用户中心侧边导航新增 `作文资产`。

要求：

1. `SectionKey` 增加 `assets`。
2. `SECTION_KEYS` 增加 `assets`。
3. 导航项增加：
   - key: `assets`
   - label: `作文资产`
4. 支持 `/app/me?tab=assets` 直接打开。
5. 不影响已有 `overview`、`essays`、`radar`、`subscription`、`referral`、`settings`。

#### 题目难度

简单

#### 验收标准

- 用户中心侧边栏出现“作文资产”。
- 点击后切换到作文资产视图。
- 刷新 `/app/me?tab=assets` 后仍停留在资产视图。

### 小题 B：新增 WritingAssetsSection

#### 题目 Prompt

请新增用户中心作文资产列表组件。

建议文件：

```text
web/src/components/personal-center/WritingAssetsSection.vue
```

要求：

1. 调用 `getWritingDocuments(0, 50, { archived: true })`。
2. 只展示已归档作文。
3. 卡片展示：
   - 标题
   - 题目摘要
   - 作文类型（考试 / 自由）
   - 最新分数
   - 评分次数
   - 更新时间
4. 提供“编辑”按钮。
5. 提供“取消归档”按钮。
6. 空状态显示“暂无归档作文”。
7. 加载失败 toast 提示。

#### 题目难度

中等

#### 验收标准

- `/app/me?tab=assets` 能加载归档作文列表。
- 无归档作文时显示空状态。
- 点击“取消归档”后该作文从资产列表移除。
- 列表样式与用户中心整体风格一致。

### 小题 C：从资产库进入编辑器

#### 题目 Prompt

请让用户可以从作文资产库继续编辑归档作文。

要求：

1. 点击“编辑”后进入 `/app/writing/editor`。
2. 编辑器打开对应 `docId`。
3. 归档作文打开后仍保持归档状态。
4. 编辑后保存 revision，不自动取消归档。
5. 不直接修改 Vue 内部状态；使用项目现有写作页进入方式。

#### 题目难度

中等

#### 验收标准

- 从作文资产点击“编辑”可以进入写作编辑器。
- 编辑器加载的是对应作文内容。
- 继续修改后作文仍在资产库中。

---

## 题 6：测试与验收

### 发给 Trae 的 Prompt

请为作文归档与作文资产库补齐必要测试，并完成构建验证。要求不修改生产登录逻辑，不引入鉴权绕过，不破坏写作页主链路。

### 小题 A：后端单元测试

#### 题目 Prompt

请补充后端测试，覆盖作文归档核心行为。

要求：

1. `DocumentServiceTest` 覆盖：
   - owner 归档成功
   - 非 owner 归档 forbidden
   - owner 取消归档成功
2. `DocumentControllerTest` 覆盖：
   - archive 接口 204
   - unarchive 接口 204
   - forbidden 返回 403
3. `WritingControllerTest` 覆盖：
   - 默认文档列表返回 `archived` 字段
   - `archived=true` 查询只走归档列表服务

#### 题目难度

中等

#### 验收标准

- 指定测试通过：

```bash
cd backend
./mvnw.cmd -q -Dtest=DocumentServiceTest,DocumentControllerTest,WritingControllerTest test
```

- 完整后端测试通过：

```bash
cd backend
./mvnw.cmd -q test
```

### 小题 B：前端构建验证

#### 题目 Prompt

请运行前端构建，确认新增归档面板、用户中心资产库和类型扩展没有破坏 TypeScript。

要求：

1. 运行：

```bash
cd web
npm run build
```

2. 如 `PanelMode` 新增导致映射遗漏，需要补齐所有 `Record<PanelMode, ...>`。
3. 不允许通过 `any` 或删除类型检查规避错误。

#### 题目难度

简单

#### 验收标准

- `npm run build` 通过。
- 允许存在 Vite chunk size warning。
- 不存在 TypeScript 编译错误。

### 小题 C：人工回归清单

#### 题目 Prompt

请按以下清单做人工验收，确认作文归档符合产品预期。

验收清单：

1. 登录后进入 `/app/writing`。
2. 打开一篇作文进入编辑器。
3. 右侧工具栏出现“归档”。
4. 输入新内容后点击“归档当前作文”。
5. 归档成功后仍停留在编辑器，且状态显示“已归档”。
6. 返回写作历史，该作文仍可见，并显示“已归档”。
7. 进入 `/app/me?tab=assets`，能看到该作文。
8. 从作文资产点击“编辑”，能回到写作编辑器。
9. 编辑归档作文并保存后，作文仍在作文资产中。
10. 在资产库点击“取消归档”，该作文从资产列表移除。

#### 题目难度

简单

#### 验收标准

- 以上 10 项全部通过。
- 现有“我的作文”、写作历史、评分、语法、润色、翻译入口不受影响。
