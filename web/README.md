# Personal English AI - Web Frontend

Vue 3 + TypeScript + Vite 前端工程，用于登录/注册/邮箱验证流程。

## 🚀 快速开始

### 环境要求

- Node.js >= 18.0.0
- npm >= 9.0.0

### 安装依赖

```bash
npm install
```

### 配置环境变量

创建 `.env` 文件（参考 `env.example`）：

```bash
# Windows PowerShell
Copy-Item env.example .env

# Linux/Mac
cp env.example .env
```

编辑 `.env` 文件，设置后端 API 地址：

```env
VITE_API_BASE_URL=http://localhost:8080
```

### 本地开发

```bash
npm run dev
```

应用将在 `http://localhost:3000` 启动。

### 构建生产版本

```bash
npm run build
```

构建产物在 `dist` 目录。

### 预览生产构建

```bash
npm run preview
```

## 📁 项目结构

```
web/
├── src/
│   ├── api/              # API 接口封装
│   │   ├── http.ts      # HTTP 客户端
│   │   └── authApi.ts   # 认证相关 API
│   ├── components/       # 可复用组件
│   │   ├── Button.vue
│   │   ├── Input.vue
│   │   └── Card.vue
│   ├── pages/           # 页面组件
│   │   ├── Login.vue
│   │   ├── Register.vue
│   │   ├── CheckEmail.vue
│   │   ├── VerifyEmail.vue
│   │   └── Home.vue
│   ├── router/          # 路由配置
│   │   └── index.ts
│   ├── styles/          # 全局样式
│   │   └── main.css
│   ├── utils/           # 工具函数
│   │   ├── validation.ts
│   │   └── toast.ts
│   ├── App.vue
│   └── main.ts
├── .env.example         # 环境变量示例
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

## 🛣️ 路由说明

- `/` - 首页（占位页）
- `/login` - 登录页面
- `/register` - 注册页面
- `/check-email` - 邮箱验证提示页
- `/verify-email?token=xxx` - 邮箱验证处理页

## 🔌 API 接口

所有 API 请求通过 `VITE_API_BASE_URL` 环境变量配置基础地址。

### 认证接口

- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/resend-verification` - 重新发送验证邮件
- `GET /api/auth/verify-email?token=xxx` - 验证邮箱

详细接口契约见代码注释。

## 🔒 安全与鉴权

### 401/403 边界

| 状态码 | 含义         | 前端行为 |
|--------|--------------|----------|
| **401** | 未认证       | 清除本地 token，并跳转登录页 |
| **403** | 已认证无权限 | 仅提示「无权限」，不跳转、不清 token |

统一在 `src/api/http.ts` 响应拦截器中处理，业务层无需再区分 401/403。

### 其他安全特性

- ✅ 统一错误提示，不暴露邮箱是否存在（防止邮箱枚举）
- ✅ 密码长度验证（至少8位）
- ✅ 邮箱格式验证
- ✅ API 错误集中处理

## 📦 部署

### Vercel

1. 将项目推送到 Git 仓库
2. 在 Vercel 中导入项目
3. 配置环境变量 `VITE_API_BASE_URL`
4. 部署

**重写规则（自动配置）：**

Vercel 会自动识别 Vue Router history 模式，无需额外配置。

### Netlify

1. 将项目推送到 Git 仓库
2. 在 Netlify 中导入项目
3. 配置环境变量 `VITE_API_BASE_URL`
4. 构建命令：`npm run build`
5. 发布目录：`dist`

**重写规则（`netlify.toml`）：**

```toml
[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

### Cloudflare Pages

1. 将项目推送到 Git 仓库
2. 在 Cloudflare Pages 中导入项目
3. 配置环境变量 `VITE_API_BASE_URL`
4. 构建命令：`npm run build`
5. 构建输出目录：`dist`

**重写规则（`_redirects` 文件，放在 `public` 目录）：**

```
/*    /index.html   200
```

### Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;
    root /path/to/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

## 🧪 验收标准

- ✅ `npm install` 正常
- ✅ `npm run dev` 正常启动
- ✅ 5 个页面路由全部可访问
- ✅ 表单校验生效
- ✅ Loading 状态显示
- ✅ 错误提示统一
- ✅ verify-email 页面根据 status 渲染不同 UI
- ✅ 构建后可直接部署（无 404/白屏）

## 📝 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **TypeScript** - 类型安全
- **Vite** - 下一代前端构建工具
- **Vue Router** - 官方路由管理器（history 模式）

## 🔄 后续集成

当前前端已按 API 契约实现，后续接入 Spring Boot 后端时：

1. 确保后端接口返回格式与前端期望一致
2. 配置正确的 `VITE_API_BASE_URL`
3. 处理 CORS 跨域问题（后端配置）
4. 接入 Resend 邮件服务后，邮箱验证流程自动生效

## 📄 License

MIT

