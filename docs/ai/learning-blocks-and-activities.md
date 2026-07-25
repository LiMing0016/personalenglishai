---
title: 学习块与互动活动接入指南
status: active
owner: ai
last_updated: 2026-07-22
related_code:
  - web/src/components/assistant/learning-blocks
  - web/src/components/assistant/learning-activities
  - python/ai_orchestrator/schemas/learning_blocks.py
  - backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java
---

# 学习块与互动活动接入指南

学习助手的 Markdown 正文负责回答问题，`parts` 负责可选的学习型展示和互动。普通提问不必生成卡片；显式 UI 操作优先于模型推断。

## 边界

- Vue/注册表：校验不可信 JSON、选择组件、提供 Markdown 降级。
- XState：只管理单张互动活动的作答生命周期。
- Pinia/普通 Vue 状态：继续管理用户、设置、会话列表、输入框和附件。
- Python：根据显式意图生成受 Pydantic 约束的学习内容。
- Java：透传 `interaction`，保存消息 `content + parts_json`，不复制每种卡片字段。

XState 不持久化、不管理会话列表、不保存用户档案。Phase 1 数据库只保存消息级 `parts_json`，不保存答案、尝试、成绩或活动快照。刷新或切换会话后卡片仍存在，但活动从第一题初始状态开始。

## 新增只读学习块

1. 在 `contracts.ts` 增加版本化数据类型。
2. 新建该块的 `schema.ts`，把 `unknown` 规范化为可信数据，并生成 `fallbackMarkdown`。
3. 新建 Vue 展示组件。
4. 在 `registry.ts` 注册 `type + version + kind + loader`，不要向总渲染器增加题型分支。
5. 在 Python 增加对应 Pydantic 输出 Schema 和生成工作流；只有确有产品入口时才接请求意图。
6. 增加 Schema、注册表、未知版本和视觉/可访问性测试。

## 新增互动题型

互动题型除上述步骤外，还需要：

1. 定义题目适配器和确定性 grader；能本地判分的题不要请求模型判分。
2. 复用通用活动事件：`START`、`ANSWER_CHANGE`、`SUBMIT`、`SUBMIT_SUCCESS`、`SUBMIT_ERROR`、`NEXT`、`RETRY`、`EXIT`。
3. 卡片内部处理提交、重试、下一题和退出，不把这些动作重新发送为聊天 prompt。
4. 只有需要生成新模型内容的 response action 才发送 `interaction`，并带 `activeActivityId/actionId`。
5. 覆盖正确、错误、重复提交、最后一题、取消、异常、键盘和 `aria-live` 路径。

## 版本与降级

每个块都必须包含：

```json
{
  "id": "stable-message-local-id",
  "type": "block_type",
  "version": 1,
  "fallbackMarkdown": "当前客户端无法展示卡片时仍可阅读的内容",
  "data": {}
}
```

修改字段语义或交互含义时提升 `version`，不要静默复用旧版本。未知类型、未知版本和非法数据必须通过注册表诊断；有 fallback 就降级展示，没有则跳过块但保留消息正文。

## `sentence_reorder` 参考实现

- 模型只生成题目说明、正确词块、翻译、提示和解释。
- 工作流代码生成稳定 token ID、`acceptedOrders` 和打乱的 `initialOrder`。
- 前端根据 token ID 判分，XState 管理作答、反馈、下一题与退出。
- `items` 当前为 1–3 题；每题 2–12 个词块。

## 最低测试清单

- Python：Pydantic 合同、工作流确定性转换、普通请求不误入互动工作流、流式和非流式结果。
- Java：请求反序列化、SSE 原样透传、`parts_json` 保存/读取、旧行与坏 JSON 降级。
- Web：注册表、grader、状态机、流式解析、历史刷新、未知版本 fallback、390px/768px/桌面布局与键盘路径。
