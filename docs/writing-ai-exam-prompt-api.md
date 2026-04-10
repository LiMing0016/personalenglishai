# AI 仿真真题接口与前端流程

本文档描述考试写作页中 `AI 生成` 题目设计链路的当前联调约定。

## 目标

- 用户输入主题、补充要求，或直接粘贴材料 / 数据 / 场景
- 后端先解析用户意图，再按学段生成 1 道仿真真题
- AI 生成成功后，先写入 `writing_prompt_sheet` 题库表
- 用户开始写作时，当前文档通过 `prompt_sheet_id` 关联已落库题单

## 支持范围

- 学段：
  - `highschool`
  - `cet4`
  - `cet6`
  - `postgrad`
- 题型：
  - `general`
  - `material`
  - `chart`
  - `comic`

## 前端流程

1. 用户在 `考试写作 -> AI 生成` 输入原始需求
2. 前端调用 `POST /api/writing/audit-topic` 解析：
   - 主题
   - 题型
   - 体裁
   - 字数范围
   - 关键要求
3. 用户可在确认卡中修正解析结果
4. 前端调用 `POST /api/writing/generate-exam-prompt` 生成完整题目
5. 后端生成成功后立即落库一条 `writing_prompt_sheet`
6. 返回 `paper` 与 `promptSheetId` 给前端
7. 用户可直接编辑题目预览，再进入写作页
8. 前端调用开始写作接口时带上 `promptSheetId`
9. 最终以 `sourceType=ai_generated` 创建考试写作会话，并把文档关联到对应题单

## POST /api/writing/audit-topic

### 请求体新增字段

- `studyStage`：当前学段，用于帮助模型识别题型和要求

### 返回体新增字段

- `promptType`：识别出的题型

### 说明

- 若模型无法完整判断，仍会返回 `need_more_info`
- 若未显式识别出题型，后端会基于原始输入做兜底推断

## POST /api/writing/generate-exam-prompt

### 请求体

- `studyStage`：学段
- `originalInput`：用户原始输入
- `topic`：确认后的主题
- `promptType`：确认后的题型
- `requirements`：关键要求
- `genre`：体裁，可选
- `wordRange`：字数范围，可选
- `maxScore`：满分，可选
- `taskType`：考试模式任务类型，可选，典型值为 `task1` / `task2`

### 返回体

- `sourceType`：固定为 `ai_generated`
- `promptType`
- `topic`
- `promptText`
- `requirements`
- `genre`
- `wordRange`
- `maxScore`
- `taskType`
- `minWords`
- `recommendedMaxWords`
- `paper`：AI 题单稳定标识
- `promptSheetId`：`writing_prompt_sheet.id`
- `materialText`：材料题附加材料
- `chartSpec`：图表题结构化图表数据
- `comicScenes`：漫画题结构化分镜数据
- `attachmentImageUrl`：图表 / 漫画等视觉附件的生成图片地址，可为空

## 开始写作接口补充

`POST /api/writing/start-session`

### 请求体新增字段

- `promptSheetId`：当前写作要关联的 AI 题单主键，可为空

### 行为补充

- 若本次是 AI 生成题单开始写作，前端应带上 `promptSheetId`
- 后端创建或复用文档时，会把 `documents.prompt_sheet_id` 写入当前题单
- 同时会回写 `writing_prompt_sheet.document_id`，形成题单到文档的关联

## 生成约束

- 优先保留用户原始输入中的强约束，不自由改题
- 图表题返回结构化表格 / 图表数据，必要时同时生成视觉附件图
- 漫画题返回结构化分镜，必要时同时生成视觉附件图
- 命题风格会参考同学段真题样本，并落到 AI 题库表而不是公共真题表
