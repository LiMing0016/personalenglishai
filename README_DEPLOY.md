# Personal English AI - 部署文档

本文档提供完整的 Docker 化部署指南，适用于阿里云 ECS 生产环境。

## 📋 目录

- [前置要求](#前置要求)
- [本地验证](#本地验证)
- [ECS 环境准备](#ecs-环境准备)
- [阿里云 ACR 配置](#阿里云-acr-配置)
- [ECS 部署](#ecs-部署)
- [Nginx 反向代理（可选）](#nginx-反向代理可选)
- [常见问题排查](#常见问题排查)

---

## 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 阿里云 ECS 实例（Ubuntu 22.04 或 Alibaba Cloud Linux）
- 阿里云 ACR 容器镜像服务
- 数据库：MySQL 8.0+（可使用阿里云 RDS 或容器化 MySQL）

---

## 本地验证

### 1. 构建 Docker 镜像

```bash
# 在项目根目录执行
docker build -t personal-english-ai-backend:latest .
```

**预期输出：**
- 构建成功，显示镜像 ID
- 无错误信息

### 2. 运行容器（仅后端，需要外部数据库）

```bash
# 设置环境变量
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=your-db-host
export DB_PORT=3306
export DB_NAME=personal_english_ai
export DB_USER=root
export DB_PASSWORD=your-password
export JWT_SECRET=your-jwt-secret-at-least-32-bytes-long

# 运行容器
docker run -d \
  --name backend-test \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE \
  -e DB_HOST=$DB_HOST \
  -e DB_PORT=$DB_PORT \
  -e DB_NAME=$DB_NAME \
  -e DB_USER=$DB_USER \
  -e DB_PASSWORD=$DB_PASSWORD \
  -e JWT_SECRET=$JWT_SECRET \
  personal-english-ai-backend:latest
```

### 3. 查看日志

```bash
# 查看容器日志
docker logs -f backend-test

# 预期看到：
# - "Started BackendApplication" 表示启动成功
# - "Active profiles: prod" 表示生产环境配置已加载
# - 无数据库连接错误
```

### 4. 健康检查

```bash
# 测试 API 端点
curl http://localhost:8080/api/ping

# 预期返回：ok
```

### 5. 清理测试容器

```bash
docker stop backend-test
docker rm backend-test
```

---

## ECS 环境准备

### 1. 安装 Docker

#### Ubuntu 22.04

```bash
# 更新系统
sudo apt-get update

# 安装依赖
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# 添加 Docker 官方 GPG 密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 添加 Docker 仓库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 启动 Docker 服务
sudo systemctl enable docker
sudo systemctl start docker

# 验证安装
docker --version
docker compose version
```

#### Alibaba Cloud Linux

```bash
# 安装 Docker
sudo yum install -y docker

# 启动 Docker 服务
sudo systemctl enable docker
sudo systemctl start docker

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 验证安装
docker --version
docker-compose --version
```

### 2. 配置 ECS 安全组

在阿里云控制台配置安全组规则，开放以下端口：

| 端口 | 协议 | 说明 | 源地址 |
|------|------|------|--------|
| 22 | TCP | SSH | 你的 IP 地址 |
| 80 | TCP | HTTP（Nginx） | 0.0.0.0/0 |
| 443 | TCP | HTTPS（Nginx） | 0.0.0.0/0 |
| 8080 | TCP | 后端 API（如不使用 Nginx） | 0.0.0.0/0 |

**注意：** 如果使用 Nginx 反向代理，8080 端口可以只开放给内网或 Nginx 容器。

---

## 阿里云 ACR 配置

### 1. 创建 ACR 实例

1. 登录 [阿里云容器镜像服务控制台](https://cr.console.aliyun.com/)
2. 创建个人版或企业版实例
3. 记录以下信息：
   - **Registry 地址**：如 `registry.cn-hangzhou.aliyuncs.com`
   - **命名空间**：如 `your-namespace`
   - **用户名和密码**：用于登录

### 2. 本地登录 ACR

```bash
# 登录 ACR（替换为你的 Registry 地址）
docker login registry.cn-hangzhou.aliyuncs.com

# 输入用户名和密码
```

### 3. 推送镜像到 ACR

```bash
# 标记镜像
docker tag personal-english-ai-backend:latest \
  registry.cn-hangzhou.aliyuncs.com/your-namespace/personal-english-ai-backend:latest

# 推送镜像
docker push registry.cn-hangzhou.aliyuncs.com/your-namespace/personal-english-ai-backend:latest
```

### 4. 在 ECS 上拉取镜像

```bash
# 登录 ACR
docker login registry.cn-hangzhou.aliyuncs.com

# 拉取镜像
docker pull registry.cn-hangzhou.aliyuncs.com/your-namespace/personal-english-ai-backend:latest
```

---

## ECS 部署

### 方案 A：仅后端服务（推荐生产环境）

适用于使用阿里云 RDS 或外部 MySQL 数据库的场景。

#### 1. 准备环境变量文件

在 ECS 上创建 `.env` 文件（**不要提交到 Git**）：

```bash
# 在项目目录创建 .env 文件
cat > .env << 'EOF'
# ACR 配置
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
ACR_NAMESPACE=your-namespace

# Spring Profile
SPRING_PROFILES_ACTIVE=prod

# 数据库配置（使用外部 RDS）
SPRING_DATASOURCE_URL=jdbc:mysql://your-rds-endpoint:3306/personal_english_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-secure-password

# 数据库环境变量（兼容配置）
DB_HOST=your-rds-endpoint
DB_PORT=3306
DB_NAME=personal_english_ai
DB_USER=root
DB_PASSWORD=your-secure-password

# JWT 配置
JWT_SECRET=your-jwt-secret-must-be-at-least-32-bytes-long-for-security
JWT_EXPIRE_SECONDS=86400

# Resend API Key（可选，未配置不影响启动）
RESEND_API_KEY=
EOF

# 设置文件权限（仅所有者可读）
chmod 600 .env
```

#### 2. 部署服务

```bash
# 拉取最新镜像
docker compose pull

# 启动服务
docker compose up -d

# 查看日志
docker compose logs -f backend
```

#### 3. 验证部署

```bash
# 检查容器状态
docker compose ps

# 测试 API
curl http://localhost:8080/api/ping

# 预期返回：ok
```

### 方案 B：后端 + MySQL 容器（快速验证）

适用于快速验证或开发测试环境。

#### 1. 修改 docker-compose.yml

取消 `docker-compose.yml` 中 MySQL 服务的注释，并注释掉后端服务的端口映射（如果使用 Nginx）。

#### 2. 准备环境变量

```bash
cat > .env << 'EOF'
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
ACR_NAMESPACE=your-namespace
SPRING_PROFILES_ACTIVE=prod

# MySQL 容器配置
MYSQL_ROOT_PASSWORD=your-secure-root-password
MYSQL_DATABASE=personal_english_ai
MYSQL_USER=appuser
MYSQL_PASSWORD=your-secure-app-password

# 数据库环境变量（指向 MySQL 容器）
DB_HOST=mysql
DB_PORT=3306
DB_NAME=personal_english_ai
DB_USER=root
DB_PASSWORD=your-secure-root-password

JWT_SECRET=your-jwt-secret-must-be-at-least-32-bytes-long
JWT_EXPIRE_SECONDS=86400
EOF

chmod 600 .env
```

#### 3. 部署服务

```bash
# 启动所有服务（包括 MySQL）
docker compose up -d

# 查看日志
docker compose logs -f

# 等待 MySQL 初始化完成（约 30 秒）
# 然后验证后端服务
curl http://localhost:8080/api/ping
```

---

## Nginx 反向代理（可选）

### 1. 部署 Nginx

```bash
# 使用包含 Nginx 的配置启动
docker compose -f docker-compose.yml -f docker-compose.nginx.yml up -d

# 查看 Nginx 日志
docker compose logs -f nginx
```

### 2. 验证反向代理

```bash
# 通过 Nginx 访问 API
curl http://localhost/api/ping

# 预期返回：ok
```

### 3. HTTPS 证书配置

#### 方式 1：使用 Let's Encrypt（推荐）

```bash
# 安装 Certbot
sudo apt-get install -y certbot python3-certbot-nginx

# 申请证书（替换为你的域名）
sudo certbot --nginx -d api.yourdomain.com

# 证书会自动配置到 Nginx
# 证书路径：/etc/letsencrypt/live/api.yourdomain.com/
```

#### 方式 2：使用阿里云 SSL 证书

1. 在 [阿里云 SSL 证书控制台](https://yundun.console.aliyun.com/?p=cas) 申请证书
2. 下载证书文件（Nginx 格式）
3. 上传到 ECS：`/etc/nginx/ssl/`
4. 修改 `deploy/nginx/nginx.conf`，取消 HTTPS 配置注释
5. 更新证书路径
6. 重启 Nginx 容器

```bash
docker compose restart nginx
```

---

## Docker Compose 常用命令

### 启动服务

```bash
# 后台启动
docker compose up -d

# 前台启动（查看日志）
docker compose up
```

### 停止服务

```bash
# 停止服务
docker compose stop

# 停止并删除容器
docker compose down

# 停止并删除容器、网络、卷
docker compose down -v
```

### 更新服务

```bash
# 拉取最新镜像
docker compose pull

# 重新创建并启动容器
docker compose up -d --force-recreate
```

### 查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f backend

# 查看最近 100 行日志
docker compose logs --tail=100 backend
```

### 查看状态

```bash
# 查看容器状态
docker compose ps

# 查看资源使用
docker stats
```

---

## 常见问题排查

### 1. 容器启动失败

**症状：** `docker compose ps` 显示容器状态为 `Exited`

**排查步骤：**

```bash
# 查看容器日志
docker compose logs backend

# 查看容器详细信息
docker inspect personal-english-ai-backend

# 检查环境变量是否设置
docker compose config
```

**常见原因：**
- 环境变量未设置或格式错误
- 数据库连接失败
- 端口被占用

### 2. 端口占用

**症状：** `Error: bind: address already in use`

**排查步骤：**

```bash
# 查看端口占用
sudo netstat -tlnp | grep 8080
# 或
sudo lsof -i :8080

# 停止占用端口的进程
sudo kill -9 <PID>

# 或修改 docker-compose.yml 中的端口映射
```

### 3. 数据库连接失败

**症状：** 日志显示 `Communications link failure` 或 `Access denied`

**排查步骤：**

```bash
# 检查环境变量
docker compose config | grep -A 10 DATASOURCE

# 测试数据库连接（在 ECS 上）
mysql -h <DB_HOST> -P <DB_PORT> -u <DB_USER> -p

# 检查安全组规则（RDS）
# 确保 ECS 的 IP 已添加到 RDS 白名单

# 检查数据库用户权限
```

**解决方案：**
- 确认数据库地址、端口、用户名、密码正确
- 检查 RDS 白名单配置
- 确认数据库用户有远程连接权限

### 4. 环境变量未生效

**症状：** 应用使用默认配置而非环境变量值

**排查步骤：**

```bash
# 检查 .env 文件是否存在
ls -la .env

# 检查环境变量格式（不能有空格）
cat .env

# 查看容器内的环境变量
docker compose exec backend env | grep -E "DB_|JWT_|SPRING_"

# 重新加载配置
docker compose down
docker compose up -d
```

**解决方案：**
- 确保 `.env` 文件在 `docker-compose.yml` 同级目录
- 环境变量值不要有引号（除非值本身包含空格）
- 重启容器以加载新环境变量

### 5. 镜像拉取失败

**症状：** `Error response from daemon: pull access denied`

**排查步骤：**

```bash
# 检查是否已登录 ACR
docker login registry.cn-hangzhou.aliyuncs.com

# 检查镜像地址是否正确
docker compose config | grep image

# 手动拉取镜像测试
docker pull <your-image-url>
```

**解决方案：**
- 重新登录 ACR
- 检查 ACR 命名空间和镜像名称是否正确
- 确认 ACR 实例状态正常

### 6. 应用无法访问

**症状：** `curl` 返回 `Connection refused` 或超时

**排查步骤：**

```bash
# 检查容器是否运行
docker compose ps

# 检查端口映射
docker compose port backend 8080

# 检查防火墙
sudo ufw status

# 检查安全组规则
# 在阿里云控制台确认端口已开放

# 测试容器内部
docker compose exec backend wget -qO- http://localhost:8080/api/ping
```

**解决方案：**
- 确保容器正在运行
- 检查 ECS 安全组规则
- 检查防火墙设置
- 确认端口映射正确

### 7. 日志中看不到 prod profile

**症状：** 日志显示默认配置而非生产配置

**排查步骤：**

```bash
# 检查环境变量
docker compose exec backend env | grep SPRING_PROFILES_ACTIVE

# 查看应用启动日志
docker compose logs backend | grep -i profile
```

**解决方案：**
- 确保 `.env` 文件中设置了 `SPRING_PROFILES_ACTIVE=prod`
- 重启容器

### 8. Resend API Key 未配置导致启动失败

**症状：** 应用启动失败，提示 Resend 相关错误

**说明：** 根据要求，未配置 `RESEND_API_KEY` 时应用应能正常启动。如果出现此问题，请检查代码中是否有强制要求该配置的逻辑。

---

## 生产环境检查清单

部署前请确认：

- [ ] Docker 和 Docker Compose 已安装
- [ ] ECS 安全组端口已开放（22, 80, 443, 8080）
- [ ] ACR 镜像已推送并可在 ECS 上拉取
- [ ] `.env` 文件已创建且包含所有必需配置
- [ ] 数据库连接信息正确且可访问
- [ ] JWT_SECRET 已设置为安全的随机字符串（至少 32 字节）
- [ ] 容器日志显示 `Active profiles: prod`
- [ ] `/api/ping` 端点返回 `ok`
- [ ] 如使用 Nginx，反向代理配置正确
- [ ] HTTPS 证书已配置（如使用）

---

## 后续优化建议

1. **监控和日志**
   - 集成阿里云日志服务（SLS）
   - 配置应用性能监控（APM）

2. **高可用**
   - 使用负载均衡（SLB）
   - 多实例部署
   - 数据库主从复制

3. **安全加固**
   - 定期更新基础镜像
   - 使用密钥管理服务（KMS）管理敏感信息
   - 配置 WAF 防护

4. **CI/CD 优化**
   - 添加自动化测试
   - 镜像安全扫描
   - 蓝绿部署策略

---

## 技术支持

如遇到问题，请检查：
1. 本文档的 [常见问题排查](#常见问题排查) 部分
2. Docker 和 Spring Boot 官方文档
3. 阿里云产品文档

---

**最后更新：** 2026-01-25
