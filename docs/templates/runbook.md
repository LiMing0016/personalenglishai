---
title: Runbook 标题
status: draft
owner: ops
last_updated: YYYY-MM-DD
review_cycle: monthly
related_code: []
related_docs: []
---

# Runbook 标题

## 适用场景

说明什么时候使用这篇 runbook。

## 影响范围

- 影响服务：
- 影响用户：
- 风险等级：低 / 中 / 高。

## 快速判断

```powershell
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs --tail=100 backend
```

判断标准：

- 正常现象：
- 异常现象：

## 处理步骤

1. 确认当前版本和环境。
2. 查看健康检查。
3. 查看关键日志。
4. 执行修复操作。
5. 验证恢复。

```powershell
# 填写操作命令
```

## 回滚或恢复

```powershell
# 填写回滚命令
```

回滚条件：

- 条件 1。
- 条件 2。

## 验收方式

```powershell
curl -i http://localhost/health
curl -i http://localhost/api/ping
```

通过标准：

- 健康检查恢复。
- 关键接口恢复。
- 日志无持续错误。

## 升级处理

遇到以下情况时升级处理：

- 数据丢失或疑似数据损坏。
- 登录、支付、订阅等核心链路不可用。
- 5xx 持续超过约定时间。
- 无法确认回滚是否安全。

## 事后复盘

- 根因：
- 时间线：
- 用户影响：
- 修复措施：
- 后续预防：
