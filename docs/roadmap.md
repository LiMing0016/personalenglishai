Personal English AI — Roadmap

本项目的唯一进度真相源（Single Source of Truth），任何功能是否“完成”，以此文件为准，而不是以感觉或日报为准。

阶段 0：工程基础（已完成 ✅）

目标：建立可持续开发的工程基础，而不是业务功能。

完成标准（Exit Criteria）

 本地 Git 可用（init / commit / push 正常）

 项目采用 monorepo 结构（backend / web / docs / deploy）

 GitHub 私有仓库创建并首次 push 成功

 默认分支规范为 main

 项目基础文档已建立（README / roadmap）

说明：阶段 0 已经完成，为后续开发提供了良好的基础。

阶段 0.5：前端登录态与 API 基础设施（已完成 ✅）

目标：搭建可复用、可扩展的前端登录态与 API 请求基础设施，业务页面不再自行处理登录与鉴权。

任务清单

 统一 API Client：单一 axios 实例，baseURL 为相对路径 /api，所有请求经此发出
 请求拦截器：从 localStorage 读取 token 并注入 Authorization: Bearer \<token>
 响应拦截器：401 清 token 并跳转登录页；403 仅提示「无权限」，不跳转、不清 token
 登录 API 封装：独立 auth 服务层（auth.ts），仅调用接口并返回数据，不处理 token/UI/路由
 登录页：仅收集输入、调用 auth API、成功后保存 token 并跳转业务首页（/app）
 前端路由鉴权：全局路由守卫，公共路由放行，业务路由无 token 则强制跳转 /login
 受保护验收页：AppHome（/app）加载时调用受保护 API（如 GET /api/users/me/profile）验证鉴权与 401 处理

完成标准（Exit Criteria）

 登录成功后刷新页面仍保持登录态
 任意 API 请求自动携带 Authorization Header
 token 失效后用户被自动踢回登录页
 新业务页面无需再关心登录态与鉴权逻辑

**401/403 边界**

| 状态码 | 含义       | 前端行为 |
|--------|------------|----------|
| 401    | 未认证     | 清除本地 token，并跳转登录页 |
| 403    | 已认证无权限 | 仅提示「无权限」，不跳转、不清 token |

说明：本阶段不实现具体业务功能，仅建立基础设施层。

阶段 1：后端骨架（未开始 ⬜）

目标：得到一个「可以部署的最小后端服务」。

任务清单

 在 backend/ 下创建 Spring Boot 项目

 本地启动成功（无报错）

 提供 GET /api/ping 接口，返回 ok

 支持通过配置文件或环境变量修改端口

 至少 1 次后端相关 commit

完成标准（Exit Criteria）

 本地访问 http://localhost:端口/api/ping
 返回 ok

 后端代码已提交到 GitHub（main 分支）

阶段 2：最小可用后端（MVP）（未开始 ⬜）

目标：具备最基础的用户与学习记录能力。

任务清单

 用户注册接口（POST /api/auth/register）

 用户登录接口（POST /api/auth/login，JWT）

 用户数据模型（User）

 创建学习记录接口（POST /api/learning_event）

 查询学习记录接口（GET /api/learning_event）

完成标准

 用户可完成“注册 → 登录 → 创建一条学习记录”

 数据可正确落库

 至少 1 次功能性 commit

 登录成功后返回 JWT，并可以保存到 localStorage（前端）

 用户能够查询自己的学习记录（前后端交互）

阶段 3：第一次部署（未开始 ⬜）

目标：让后端服务第一次在公网稳定运行。

任务清单

 云服务器准备完成

 域名 api.personalenglishai.com 解析完成

 后端服务部署并运行

 配置 HTTPS 和 SSL 证书

 服务可重启自动恢复（如使用 Docker 或 PM2）

完成标准

 通过公网域名访问 /api/ping 成功

 服务重启后仍可访问

 确保访问安全（HTTPS 成功）

阶段 4：前端与成长展示（未开始 ⬜）

目标：让用户能看到自己的学习成长。

任务清单

 使用 Vue 3 和 Vite 构建前端页面

 登录页面：输入用户名 / 邮箱和密码，登录后获得 JWT

 注册页面：输入用户名 / 邮箱和密码，完成注册

 成长展示页面：展示用户的学习记录（根据用户登录信息显示）

 使用 Axios 请求后端接口（登录、注册、学习记录查询）

 本地存储 JWT，并每次请求时添加到请求头

完成标准

 用户能够通过前端登录 / 注册，并成功调用后端接口

 前端展示用户的学习记录

 成长展示页面可视化显示用户进步（如表格、图表等）

 至少 1 次前端与后端对接成功的 commit

---

## Bug 修复记录

### 打开 AI 助手后左侧无法拖拽选区（已修复 ✅）

**现象**：打开 AI 助手后，左侧正文可单点选中，但拖拽选区在 mousemove/pointermove 时立刻消失，无法连续选句子/段落。

**根因**：右侧 AskAiInput 在 `selectedText` 变化时用 `setTimeout(..., 50)` 自动对输入框执行 `focus()`。拖选过程中 selectionchange 会多次触发，多次排队 focus，把焦点移到右侧输入框，导致左侧失去焦点、选区被浏览器折叠。

**修复**：移除「选区出现即自动 focus 输入框」的逻辑，使 AI 助手为非模态 side panel，不抢焦点。用户需要输入时自行点击右侧输入框。

**约束**：AI 助手不得使用全屏 backdrop/overlay 覆盖左侧；不得对 document/body 的 pointermove/mousedown 做 preventDefault；不得在选区更新时自动 focus 右侧控件。

### 右侧 AI 助手输入框持久化（已修复 ✅）

**现象**：右侧 AI 助手 Ask AI 输入框里的文本（instruction / aiNote）刷新后不会恢复，用户每次刷新都需要重新输入指令。

**需求**：为右侧 AI 助手输入框添加独立的 localStorage 持久化，刷新后自动恢复用户输入的指令文本。

**实现**：
- 新增独立 key：`peai:writing:aiNoteDraft`（仅保存右侧 instruction 文本，与左侧正文 `peai:writing:draft` 分离）
- 存储格式：JSON `{ text, updatedAt }`
- 保存策略：`watch(aiNote)` debounce 400ms 写入 localStorage，仅在 aiNote 变化时写，不在 mounted 立即写空覆盖
- 恢复策略：`onMounted` 读取 `peai:writing:aiNoteDraft`，仅当当前 `aiNote` 为空时回填 storage.text，避免覆盖用户刚输入
- 清空行为：点击页面"清空"按钮时，同时清空右侧输入框（`aiNote.value = ''`）并删除 `peai:writing:aiNoteDraft`

**约束**：
- 左侧 `peai:writing:draft` 仅保存正文，保持语义纯净，不混入右侧内容
- 右侧 `peai:writing:aiNoteDraft` 仅保存 instruction 文本，独立管理
- 两个 key 互不干扰，各自独立持久化与恢复