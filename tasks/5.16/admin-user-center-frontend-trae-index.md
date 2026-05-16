# Admin 用户中心前端 Trae 题目索引

## 背景

本组任务把 Admin 用户中心一期视觉方案拆成可交给 Trae 独立实现的前端题目。设计目标延续用户中心方案中的一句话：

```text
列表轻、抽屉快、详情深、模块独立
```

Figma 设计稿：

<https://www.figma.com/design/etopP4oRFZh65MKkszGaR3>

相关设计文档：

- `docs/admin/user-center-design.md`

## 交付顺序

建议按以下顺序执行，避免页面之间互相阻塞：

1. `admin-user-center-list-trae-tasks.md`
2. `admin-user-center-drawer-trae-tasks.md`
3. `admin-user-center-detail-trae-tasks.md`
4. `admin-user-center-ai-usage-trae-tasks.md`

## 通用前端约束

- 技术栈沿用 `web/` 现有 Vue 3、TypeScript、Vite、Vue Router。
- 优先复用 `web/src/api/admin.ts`、Admin 布局、Admin 通用按钮/表格/卡片样式。
- 不引入新的 UI 框架。
- 不做营销式 hero，不做装饰性大渐变。
- 后台页面优先服务扫描、筛选、排查和治理。
- 所有空字段显示 `-`，不出现 `null`、`undefined`、`NaN`。
- 请求失败必须有错误提示和可恢复路径。
- 未授权或权限不足时不能展示敏感入口。

## 通用验证

每个题目完成后至少执行：

```powershell
cd web
npm run build
```

涉及路由、筛选、抽屉或详情页交互的题目，还需要本地打开页面做浏览器验收：

```powershell
cd web
npm run dev -- --host 127.0.0.1 --port 5173
```

验收入口：

```text
http://127.0.0.1:5173/admin/users
```

## 不在本组前端题目的范围

- Python usage 回传 Java 的后端闭环。
- 新建 AI token 用量数据库表。
- 管理员权限模型重构。
- 完整导出能力。
- 原始 prompt 或用户完整输入的敏感查看能力。

