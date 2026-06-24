---
title: Learning Notes API
status: active
owner: backend
last_updated: 2026-06-24
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/LearningNoteController.java
  - backend/src/main/java/com/personalenglishai/backend/service/learning/LearningNoteService.java
  - backend/src/main/java/com/personalenglishai/backend/service/learning/LearningCanvasOrganizeService.java
  - web/src/api/learningNotes.ts
related_docs:
  - /data/learning-note-schema
  - /ai/assistant-output-format
---

# Learning Notes API

## 当前结论

`Learning Notes API` 用于保存学习助手画布生成的学习资产。底层类型是通用学习笔记，首版前端只开放 `vocabulary` 单词卡。

前端入口：

- 用户在学习助手回复中选中单词或短语。
- 点击 `新建单词卡` 打开右侧学习资产画布。
- 用户编辑 Markdown 正文，保存到 `learning_note`。

## Endpoint

```http
POST /api/learning-notes
GET /api/learning-notes
GET /api/learning-notes/{noteUid}
PUT /api/learning-notes/{noteUid}
DELETE /api/learning-notes/{noteUid}
POST /api/learning-notes/organize
```

## 鉴权和权限

- 鉴权方式：JWT。
- 权限要求：普通登录用户。
- 用户身份来源：后端鉴权链路写入的 `requestAttribute("userId")`。
- 未登录时返回 HTTP `401`，业务码 `401000`。

## Request

### `POST /api/learning-notes`

```json
{
  "type": "vocabulary",
  "title": "nuanced",
  "contentMarkdown": "# nuanced\n\n**中文释义：** 细致入微的",
  "structuredPayload": null,
  "sourceConversationId": "conv-1",
  "sourceMessageId": "msg-1",
  "sourceText": "A nuanced answer considers different sides."
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 学习资产类型。首版前端使用 `vocabulary`，后端预留 `sentence`、`grammar`、`expression`。 |
| `title` | string | 是 | 资产标题。单词卡中即单词或短语。 |
| `contentMarkdown` | string | 是 | 用户可编辑的 Markdown 正文。 |
| `structuredPayload` | string/null | 否 | 预留结构化数据，首版可为空。 |
| `sourceConversationId` | string | 否 | 来源助手会话 ID。 |
| `sourceMessageId` | string | 否 | 来源助手消息 ID。 |
| `sourceText` | string | 否 | 来源上下文文本。 |

### `GET /api/learning-notes`

| Query | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | string | `vocabulary` | 资产类型过滤。 |
| `page` | number | `1` | 页码，从 1 开始。 |
| `size` | number | `20` | 每页数量，后端限制最大值。 |

### `POST /api/learning-notes/organize`

```json
{
  "type": "vocabulary",
  "title": "nuanced",
  "selectedText": "nuanced",
  "contextText": "A nuanced answer considers different sides.",
  "currentMarkdown": "# nuanced\n\nmy own note",
  "mode": "format"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 首版为 `vocabulary`。 |
| `title` | string | 是 | 资产标题。 |
| `selectedText` | string | 否 | 用户从助手回复中选中的文本。 |
| `contextText` | string | 否 | 来源助手消息全文。 |
| `currentMarkdown` | string | 否 | 当前画布 Markdown。`format` 模式必传。 |
| `mode` | string | 是 | `create` 使用默认单词卡模板整理；`format` 只优化格式并保留用户笔记。 |

## Response

### 学习笔记

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "noteUid": "note-1",
    "type": "vocabulary",
    "title": "nuanced",
    "contentMarkdown": "# nuanced",
    "structuredPayload": null,
    "sourceConversationId": "conv-1",
    "sourceMessageId": "msg-1",
    "sourceText": "A nuanced answer considers different sides.",
    "status": "active",
    "createdAt": "2026-06-24T10:00:00",
    "updatedAt": "2026-06-24T10:00:00"
  }
}
```

### 列表响应

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "items": [],
    "total": 0,
    "page": 1,
    "size": 20
  }
}
```

### AI 整理响应

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "candidateMarkdown": "# nuanced\n\n**中文释义：** 细致入微的"
  }
}
```

前端必须把 `candidateMarkdown` 作为候选预览展示，不能自动覆盖用户正在编辑的正文。

## 错误响应

| HTTP 状态 | 错误码 | 场景 | 用户提示 |
| --- | --- | --- | --- |
| 401 | `401000` | 未登录或请求没有用户身份 | 请先登录 |
| 400/500 | 由全局异常处理返回 | 参数非法、AI 调用失败或数据库异常 | 前端展示接口返回 message 或兜底文案 |

## 兼容性约束

- `noteUid`、`type`、`title`、`contentMarkdown` 字段不能删除。
- `contentMarkdown` 是用户可编辑正文，后续模板升级不能破坏已有 Markdown。
- `structuredPayload` 只能作为增强字段使用，不能取代 Markdown 正文。
- 后续新增学习资产类型时，应复用当前接口，不为每种资产单独复制一套 CRUD。

## 验收方式

```powershell
curl -i -X POST http://localhost:18080/api/learning-notes `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d "{\"type\":\"vocabulary\",\"title\":\"nuanced\",\"contentMarkdown\":\"# nuanced\"}"
```

通过标准：

- 未登录请求返回 `401000`。
- 登录后可创建、查询、更新、软删除学习笔记。
- `organize` 返回 `candidateMarkdown`，前端以候选预览形式展示。

