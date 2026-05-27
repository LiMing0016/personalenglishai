# 前端 Playwright E2E 登录态支持 Trae 实现题目

## 背景

当前前端 Playwright 测试访问受保护页面时，会被前端路由守卫重定向到 `/login`，导致写作页相关 E2E 无法稳定进入真实页面验证。

目标是补齐 Playwright 标准登录态方案：

```text
auth setup 登录一次
  ↓
保存 storageState
  ↓
受保护页面 E2E 复用 storageState
  ↓
直接进入 /app/writing 等业务页面
```

本题重点是测试基础设施，不允许改生产登录逻辑，也不允许通过 IP、User-Agent 或硬编码浏览器特征绕过鉴权。

## 账号与环境变量

测试账号通过环境变量传入：

```bash
E2E_TEST_EMAIL=admin01@admin.com
E2E_TEST_PASSWORD=<测试账号密码>
```

不要把测试账号密码硬编码进生产代码。若为了本地临时验收设置默认值，也必须限制在 Playwright 测试文件中，不能进入 `src` 或生产构建产物。

## 非目标

- 不改 `web/src/views/LoginFormView.vue` 的生产登录流程。
- 不改 `web/src/router/index.ts` 的生产鉴权守卫语义。
- 不通过 IP、User-Agent、浏览器 channel、viewport 等特征绕过鉴权。
- 不在生产代码中写入 mock token、固定 JWT 或后门路由。
- 不要求本题重写所有历史 E2E，只先让受保护页面默认可复用登录态。

---

## 题 1：新增 Playwright auth setup

### 发给 Trae 的 Prompt

请为前端 Playwright 测试新增认证 setup。要求在测试运行前使用测试账号登录一次，保存 `storageState` 到 `playwright/.auth/user.json`，后续需要访问受保护页面的测试默认复用该状态。不要修改生产登录逻辑，不要通过 IP、User-Agent 或浏览器特征绕过鉴权。完成后运行指定 E2E 验证。

### 小题 A：新增 auth setup 文件

#### 题目 Prompt（完成）

请新增 Playwright auth setup，用于在 E2E 测试前登录测试账号并保存 storageState。

要求：

1. 在 `web/tests/auth.setup.ts` 新增认证 setup。
2. 使用 `E2E_TEST_EMAIL` 和 `E2E_TEST_PASSWORD` 读取测试账号。
3. 登录成功后保存 storageState 到 `web/playwright/.auth/user.json`。
4. auth setup 需要能在本地 dev server 下运行。
5. 不要修改生产登录页、生产路由守卫或生产 API 客户端。
6. 不要使用 IP、User-Agent、浏览器特征、viewport 等方式绕过鉴权。

#### 题目难度

中等

#### 验收标准

- auth setup 可以单独运行并生成 `playwright/.auth/user.json`。
- storageState 中包含后续进入业务页面所需的登录态。
- 未登录状态访问 `/app/writing` 仍会跳转 `/login`。
- 生产登录页功能不受影响。
- `web/src` 中不存在测试 mock token 或鉴权绕过逻辑。

### 小题 B：Playwright 配置复用 storageState

#### 题目 Prompt

请调整 `web/playwright.config.ts`，让需要访问业务页面的浏览器项目默认依赖 auth setup，并复用保存的 storageState。

要求：

1. 在 `projects` 中新增 `auth setup` project。
2. `chromium` 项目依赖 `auth setup`。
3. `chromium` 使用 `storageState: 'playwright/.auth/user.json'`。
4. 如果保留 firefox / webkit / Chrome / Edge 项目，也要评估是否需要同样复用 storageState。
5. `playwright/.auth/` 不能提交到 git。

#### 题目难度

简单

#### 验收标准

- `npx playwright test --project=chromium` 会先执行 auth setup。
- Chromium 测试访问 `/app/writing` 不再停在 `/login`。
- `playwright/.auth/user.json` 不会被 git 跟踪。
- Playwright 配置不影响生产构建。

---

## 题 2：后端登录不稳定时的 E2E 专用 mock auth

### 发给 Trae 的 Prompt

如果当前本地后端无法稳定完成真实登录，请提供 test/e2e 环境专用 mock auth。该能力必须由环境变量显式开启，例如 `E2E_MOCK_AUTH=1`，并且只能存在于 Playwright 测试代码中，生产代码不可用、生产构建不可包含。

### 小题 A：显式环境变量开启 mock auth

#### 题目 Prompt

请为 Playwright auth setup 增加 E2E 专用 mock auth fallback，但必须显式环境变量开启。

要求：

1. 仅当 `E2E_MOCK_AUTH=1` 时允许启用 mock auth。
2. mock auth 只能写在 `web/tests/**` 或 Playwright support 文件中。
3. mock auth 只能写入测试浏览器上下文需要的 storageState。
4. mock auth 不得修改 `web/src` 生产代码。
5. mock token 的过期时间要足够短，只用于当前 E2E 会话。
6. 文件内注释说明：这是后端本地登录不稳定时的 E2E fallback，不是生产鉴权能力。

#### 题目难度

中等

#### 验收标准

- 默认不启用 mock auth。
- 未设置 `E2E_MOCK_AUTH=1` 时仍走真实登录。
- 设置 `E2E_MOCK_AUTH=1` 时可以生成 storageState。
- `npm run build` 后的 `dist` 中不存在 `E2E_MOCK_AUTH`、mock token、测试账号密码等字符串。
- `web/src` 中没有新增鉴权绕过代码。

---

## 题 3：修复写作页 Agent 菜单 E2E（完成）

### 发给 Trae 的 Prompt

请修复 `aiNotePanelAgentMenuInteraction.spec.ts`，让它可以进入 `/app/writing`，创建或进入写作编辑器，打开右侧写作教练，并真实验证 Agent 菜单交互。测试应该验证菜单打开、能力项可见、点击后当前 Agent/placeholder 状态发生变化。

### 小题 A：补齐写作页 E2E 数据稳定性

#### 题目 Prompt

请让 `aiNotePanelAgentMenuInteraction.spec.ts` 稳定进入写作页。受保护页面使用 auth setup 的 storageState，写作页依赖的业务 API 可在测试内做最小 mock，但不要 mock 登录守卫本身。

要求：

1. 测试入口访问 `/app/writing`。
2. 确认页面没有停在 `/login`。
3. 如后端作文列表、用户学段、创建写作会话不稳定，可在 spec 或 support 文件中 mock：
   - `/api/users/me/profile`
   - `/api/writing/documents`
   - `/api/writing/stats`
   - `/api/writing/start-session`
   - `/api/docs/{docId}`
   - `/api/writing/documents/{docId}/metadata`
4. mock 只用于业务数据稳定，不用于绕过鉴权。
5. 测试流程应从写作页真实点击进入编辑器，不要直接篡改 Vue 内部状态。

#### 题目难度

中等

#### 验收标准

- 测试能访问 `/app/writing`。
- URL 不匹配 `/login`。
- 能点击“新建作文”并进入 `/app/writing/editor`。
- 写作编辑器页面能正常渲染。

### 小题 B：真实验证 Agent 菜单交互

#### 题目 Prompt

请在 `aiNotePanelAgentMenuInteraction.spec.ts` 中真实验证写作教练 Agent 菜单交互。

要求：

1. 进入写作编辑器后点击左侧工具栏“教练”。
2. 确认右侧写作教练面板出现。
3. 点击 Agent chip 或 `+` 菜单按钮。
4. 确认菜单 `选择写作教练能力` 出现。
5. 点击“审题”或其他能力项。
6. 断言当前 Agent 文案发生变化。
7. 断言输入框 placeholder 随选中能力变化。

#### 题目难度

简单

#### 验收标准

- 菜单打开前不可见，点击后可见。
- 点击“审题”后菜单关闭。
- Agent chip 显示“审题”。
- composer input placeholder 包含“请先帮我审题”。
- 测试不是只验证 DOM 存在，而是验证交互后的状态变化。

---

## 题 4：验证与安全检查

### 发给 Trae 的 Prompt

请完成 Playwright 登录态改造后的验证与安全检查。必须运行指定 Chromium E2E，并确认生产构建中不存在默认鉴权绕过或 mock auth 字符串。

### 小题 A：运行指定 E2E

#### 题目 Prompt

请运行下面的验收命令：

```bash
cd web
npx playwright test tests/aiNotePanelAgentMenuInteraction.spec.ts --project=chromium
```

如果失败，请根据错误信息修复，直到通过。

#### 验收标准

- 命令退出码为 0。
- Playwright 报告中 `auth setup` 和目标 spec 均通过。
- `/app/writing` 不再停在 `/login`。

### 小题 B：生产构建与绕过检查

#### 题目 Prompt

请运行生产构建，并检查构建产物中不存在默认鉴权绕过。

要求：

1. 运行：

```bash
cd web
npm run build
```

2. 搜索 `web/dist`，确认不存在：
   - `E2E_MOCK_AUTH`
   - mock JWT 标识
   - 测试账号密码
   - E2E 专用鉴权绕过逻辑
3. 检查 `web/src` 没有新增测试后门。

#### 验收标准

- `npm run build` 通过。
- `web/dist` 不包含 E2E mock auth 相关字符串。
- `web/src` 不包含新增鉴权绕过。
- 登录页原有功能不受影响。

---

## 总体验收标准

- 新增 Playwright auth setup。
- 使用测试账号登录一次并保存 storageState。
- 需要访问受保护页面的测试默认复用该 storageState。
- 不改生产登录逻辑。
- 不通过 IP、User-Agent 或硬编码浏览器特征绕过鉴权。
- 后端本地无法稳定登录时，允许 E2E 专用 mock auth，但必须由 `E2E_MOCK_AUTH=1` 显式开启。
- `aiNotePanelAgentMenuInteraction.spec.ts` 可以进入 `/app/writing` 并真实验证 Agent 菜单交互。
- `npx playwright test tests/aiNotePanelAgentMenuInteraction.spec.ts --project=chromium` 通过。
- 生产构建中不存在默认鉴权绕过。
