# 管理员端数据地图 Trae 题目

## 背景

管理员端需要新增“数据地图”模块，用于解释系统核心数据表、字段、敏感级别、表级健康状态和对应业务排查入口。

该模块不是数据库客户端，不提供任意 SQL 查询，不展示密码、token、验证码、密钥等敏感值。首版目标是完成 **P1 数据字典基础版**，并为后续数据健康、业务跳转和脱敏样例留好边界。

参考文档：

- `docs/admin/data-catalog-design.md`
- `docs/admin/index.md`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/mapper/AiTokenUsageMapper.xml`
- `web/src/layouts/AdminLayout.vue`
- `web/src/api/admin.ts`

核心原则：

- 自动读取数据库结构，人工维护业务语义。
- 后端接口只读。
- 首版只给 `super_admin` 或 `admin.data_catalog.read`。
- 不做 SQL 输入框。
- 不做全表数据浏览。
- 不返回敏感字段原值。

---

## 题目 1：设计并实现数据地图后端 DTO 与配置模型

难度：中等

### A 小题：定义后端响应 DTO

请新增数据地图后端响应模型，覆盖表列表和表详情。

要求：

1. 表列表 DTO 至少包含：
   - `tableName`
   - `title`
   - `module`
   - `rowCount`
   - `sensitivity`
   - `latestAt`
   - `adminRoute`
   - `description`
2. 表详情 DTO 至少包含：
   - 表摘要字段
   - `columns`
   - `indexes`
   - `foreignKeys`
   - `sensitiveColumns`
   - `securityNotes`
3. 字段 DTO 至少包含：
   - `name`
   - `type`
   - `nullable`
   - `primaryKey`
   - `sensitive`
   - `comment`
4. DTO 字段命名保持前端友好，使用 camelCase。

### B 小题：定义业务语义配置模型

请新增 `admin-data-catalog.yml` 的加载模型，用于维护表中文名、模块、敏感级别和业务入口。

要求：

1. 配置文件路径建议：

```text
backend/src/main/resources/admin-data-catalog.yml
```

2. 每张表支持配置：
   - `title`
   - `module`
   - `sensitivity`
   - `adminRoute`
   - `timeColumn`
   - `description`
   - `sensitiveColumns`
   - `securityNotes`
3. 未配置的表也能出现在列表中，但中文名、模块和说明可为空或使用默认值。
4. YAML 解析失败时，后端启动应给出清晰错误。

### 验收标准

- DTO 能表达列表页和详情页需要的全部字段。
- 配置模型能加载至少 `users`、`ai_token_usage_event`、`user_ai_token_usage_daily`、`user_ai_token_usage_monthly`。
- 未配置表不会导致接口失败。
- 没有新增数据库迁移。

---

## 题目 2：实现 MySQL 元数据查询与数据地图聚合服务

难度：中等偏难

### A 小题：读取 `information_schema` 元数据

请新增 MyBatis Mapper 查询 MySQL 元数据。

需要读取：

1. `information_schema.tables`
   - 表名
   - 表注释
   - 近似行数 `table_rows`
2. `information_schema.columns`
   - 字段名
   - 字段类型
   - nullable
   - 默认值
   - 字段注释
3. `information_schema.statistics`
   - 主键
   - 唯一索引
   - 普通索引
4. `information_schema.key_column_usage`
   - 外键关系

要求：

- 只查询当前业务数据库。
- 不对业务大表执行实时 `COUNT(*)`。
- Mapper SQL 不拼接用户输入表名。

### B 小题：实现聚合服务

请新增 `AdminDataCatalogService`，将数据库元数据和 `admin-data-catalog.yml` 合并。

要求：

1. 表列表支持筛选：
   - `keyword`
   - `module`
   - `sensitivity`
   - `hasAdminRoute`
2. 表详情支持通过 `tableName` 查询。
3. `latestAt` 根据配置的 `timeColumn` 计算；如果没有配置或字段不存在，返回 `null`。
4. 敏感字段由配置标记，并在字段列表中体现 `sensitive=true`。
5. 未找到表时返回清晰业务错误。

### 验收标准

- 能返回数据库现有表列表。
- 能查看 `users` 表详情。
- 能查看 `ai_token_usage_event` 字段、索引和外键。
- `password_hash`、token、验证码类字段能被标记为敏感。
- 服务层没有直接暴露 SQL 查询能力。

---

## 题目 3：实现 Admin 数据地图 API 与权限控制

难度：中等

### A 小题：实现只读 Controller

请新增 Admin 数据地图接口：

```text
GET /api/admin/data-catalog/tables
GET /api/admin/data-catalog/tables/{tableName}
```

要求：

1. 接口统一走现有 Admin 鉴权体系。
2. 列表接口支持筛选参数。
3. 详情接口校验 `tableName` 合法性。
4. 不接受 SQL、where、orderBy 等自由查询参数。
5. 返回结构与题目 1 DTO 对齐。

### B 小题：接入权限

请为数据地图接入权限控制。

要求：

1. 首版允许 `super_admin` 查看。
2. 如果项目已有权限枚举或权限表，新增：

```text
admin.data_catalog.read
```

3. 普通管理员无权限时应返回明确的 403。
4. 前端导航是否展示不能替代后端权限校验。

### 验收标准

- `super_admin` 能访问数据地图接口。
- 无权限账号访问返回 403。
- 接口不支持任意 SQL 查询。
- 表名非法或不存在时返回稳定错误。

建议测试：

```powershell
cd backend
.\mvnw.cmd -q test
```

---

## 题目 4：实现管理员端数据地图列表页与导航入口

难度：中等

### A 小题：新增前端 API 与路由

请在前端新增数据地图 API 和路由。

要求：

1. 在 `web/src/api/admin.ts` 增加类型和方法：
   - `listDataCatalogTables`
   - `getDataCatalogTable`
2. 新增路由：

```text
/admin/data-catalog
/admin/data-catalog/:tableName
```

3. 在 Admin 导航中加入“数据地图”入口。
4. 入口权限使用 `admin.data_catalog.read` 或 `super_admin` 对应能力。

### B 小题：实现表列表页

请实现 `/admin/data-catalog` 页面。

要求：

1. 页面使用现有 Admin 视觉风格。
2. 筛选项包括：
   - 关键词
   - 所属模块
   - 敏感级别
   - 是否有业务入口
3. 表格字段包括：
   - 表名
   - 中文名
   - 所属模块
   - 行数
   - 敏感级别
   - 最近更新时间
   - 管理员入口
4. 空状态、加载态、错误态都要有。
5. 点击表名进入详情页。

### 验收标准

- `/admin/data-catalog` 能正常打开。
- 能按关键词和模块筛选。
- 表格不展示任何原始行数据。
- 有业务入口的表可以跳转到对应管理员页面。
- 无权限时不展示入口或显示无权限状态。

建议测试：

```powershell
cd web
npm run build
```

---

## 题目 5：实现数据地图表详情页

难度：中等

### A 小题：实现表详情基础信息

请实现 `/admin/data-catalog/:tableName` 页面顶部和摘要区。

要求：

1. 展示：
   - 表名
   - 中文名
   - 所属模块
   - 敏感级别
   - 行数
   - 最近更新时间
   - 业务说明
2. 提供返回列表按钮。
3. 如果有 `adminRoute`，提供“进入业务页面”按钮。
4. 页面不使用营销式 hero，不使用装饰渐变。

### B 小题：实现字段、索引、外键和安全说明

请在详情页展示结构信息。

要求：

1. 字段列表展示字段名、类型、nullable、主键、敏感标记、字段注释。
2. 索引列表展示索引名、字段、唯一性。
3. 外键列表展示来源字段、目标表、目标字段。
4. 安全说明展示 `securityNotes`。
5. 敏感字段需要明显标记，但不展示字段原值。

### 验收标准

- 能查看 `users` 表详情。
- 能查看 `ai_token_usage_event` 表详情。
- 敏感字段有标记。
- 页面不展示任何业务表行数据。
- 字段、索引、外键为空时有空状态。

建议测试：

```powershell
cd web
npm run build
```

---

## 题目 6：补测试、文档和验收说明

难度：中等

### A 小题：补后端和前端测试

请为数据地图补基础测试。

后端测试至少覆盖：

1. YAML 配置加载。
2. 表列表聚合。
3. 表详情聚合。
4. 未配置表的默认行为。
5. 非法表名或不存在表的错误。
6. 无权限访问 403。

前端测试至少覆盖：

1. Admin 路由存在。
2. 导航包含数据地图入口。
3. 列表页能渲染 mock 数据。
4. 详情页能渲染字段和敏感标记。

### B 小题：同步文档与验收步骤

请同步更新相关文档。

要求：

1. 如实现范围与 `docs/admin/data-catalog-design.md` 不一致，需要更新文档。
2. 在管理员端当前产品说明中补充数据地图状态。
3. 在最终说明中列出实际运行的验证命令。
4. 如果未实现脱敏样例，文档中必须明确它仍属于后续阶段。

### 验收标准

- 后端测试通过或明确说明失败原因。
- 前端构建通过。
- 文档站构建通过。
- 文档没有把 SQL 查询器、全表浏览、脱敏样例写成首版已实现能力。

建议测试：

```powershell
cd backend
.\mvnw.cmd -q test

cd ..\web
npm run build

cd ..\docs
npm run build
```
