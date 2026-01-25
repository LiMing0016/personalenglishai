# ECS 上 Nginx(80) 反代 Spring Boot(8080)

公网只暴露 80（后续 443），8080 仅本机。`/health`、`/api/` 经 Nginx 访问。

---

## 1. Nginx 配置

使用 `ecs-80-only.conf`：

- `GET /health` → `http://127.0.0.1:8080/health`
- `/api/` → `http://127.0.0.1:8080/api/`
- 其他路径 → **404**（推荐）

---

## 2. 安装并启动 Nginx

### Ubuntu 22.04 / 20.04

```bash
sudo apt-get update
sudo apt-get install -y nginx

# 拷贝项目配置
sudo cp deploy/nginx/ecs-80-only.conf /etc/nginx/sites-available/ecs-default
sudo ln -sf /etc/nginx/sites-available/ecs-default /etc/nginx/sites-enabled/

# 删除默认站（可选，避免冲突）
sudo rm -f /etc/nginx/sites-enabled/default

# 检查并重载
sudo nginx -t && sudo systemctl reload nginx
sudo systemctl enable nginx
sudo systemctl status nginx
```

### CentOS 7 / 8、Alibaba Cloud Linux

```bash
sudo yum install -y nginx

# 拷贝项目配置
sudo cp deploy/nginx/ecs-80-only.conf /etc/nginx/conf.d/ecs-default.conf

# 检查并重载
sudo nginx -t && sudo systemctl reload nginx
sudo systemctl enable nginx
sudo systemctl status nginx
```

---

## 3. 验证

将 `<公网IP>` 换成 ECS 公网 IP。

```bash
# 健康检查
curl -i http://<公网IP>/health

# API ping（若存在）
curl -i http://<公网IP>/api/ping
```

**期望：**

- `/health`：`200 OK`，JSON 含 `"status":"ok"`、`"service":"backend"`、`timestamp`
- `/api/ping`：`200 OK`，Body `ok`

---

## 4. 安全组建议

- **入方向**：放行 **80**（以及后续 **443**），来源 `0.0.0.0/0` 或按需限制。
- **不要**对公网放行 **8080**；8080 仅本机 Nginx 访问。

---

## 5. 常见问题

- **502 Bad Gateway**：后端未起或未监听 8080。确认 Spring Boot 已启动：`curl -s http://127.0.0.1:8080/health`。
- **端口占用**：`sudo ss -tlnp | grep :80` 或 `sudo lsof -i :80` 查看占用的进程。
