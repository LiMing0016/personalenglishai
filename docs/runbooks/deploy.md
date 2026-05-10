---
status: active
owner: project
last_updated: 2026-05-10
related_code:
  - README_DEPLOY.md
  - docker-compose.yml
  - docker-compose.nginx.yml
  - deploy/
---

# 部署

根目录 `README_DEPLOY.md` 暂时保留为详细部署手册。

这一页作为 VitePress 文档站中的稳定部署入口。后续可以把详细部署内容迁移到这里，或者继续把这里维护为部署资料索引。

常用命令：

```bash
docker compose up -d
docker compose -f docker-compose.yml -f docker-compose.nginx.yml up -d
```
