---
title: 测试文档标题
status: draft
owner: project
last_updated: YYYY-MM-DD
review_cycle: on-change
related_code: []
related_docs: []
---

# 测试文档标题

## 当前结论

说明当前测试范围、验收标准和适用发布阶段。

## 测试范围

本文覆盖：

- 范围项 1。
- 范围项 2。

本文不覆盖：

- 非范围项 1。
- 非范围项 2。

## 环境要求

- Node.js：
- Java：
- Python：
- MySQL：
- Redis：
- 环境变量：

## 测试矩阵

| 场景 | 类型 | 命令或步骤 | 通过标准 |
| --- | --- | --- | --- |
| 前端构建 | build | `npm run build` | exit 0 |
| 后端测试 | unit/integration | `.\mvnw.cmd -q test` | 0 failures |

## 冒烟测试

1. 打开首页。
2. 注册账号。
3. 登录。
4. 完成核心业务操作。
5. 检查数据是否持久化。

## 回归测试

| 模块 | 回归点 | 验收方式 |
| --- | --- | --- |
| 模块名 | 行为 | 命令或人工步骤 |

## 压测或容量测试

```powershell
# 填写 k6 或其他压测命令
```

指标：

- P95 延迟：
- 错误率：
- CPU / 内存：
- 数据库连接数：

## 发布准入

- 构建通过。
- 自动测试通过。
- 冒烟测试通过。
- 无 P0/P1 阻断问题。
- 回滚方案可执行。

## 测试记录

| 日期 | 版本 | 执行人 | 结果 | 备注 |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD | commit sha | name | pass/fail | 说明 |
