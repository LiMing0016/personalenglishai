# AI 仿真真题接口与前端流程

本文档描述考试写作页中 `AI 生成` / 题目设计画布链路的当前联调约定。

## 目标

- 用户输入主题、补充要求，或直接粘贴材料 / 数据 / 场景
- 左侧对话区先由 Python `Prompt Sheet Chat Agent` 负责自然交流、追问和判断是否需要改右侧画布
- 只有需要生成 / 修改 / 替换右侧题单时，Chat Agent 才通过 Agents as Tools 调用 Python `Prompt Sheet Canvas Agent` 生成 1 道仿真真题
- Java 后端不再承载题单对话和命题 prompt，只保留业务 API、鉴权、题单落库和文档绑定
- AI 生成成功后，先写入 `writing_prompt_sheet` 题库表
- 用户开始写作时，当前文档通过 `prompt_sheet_id` 关联已落库题单

## 支持范围

- 学段：
  - `primary`
  - `junior`
  - `senior`
  - `highschool`
  - `cet4`
  - `cet6`
  - `postgrad`
  - `ielts`
  - `toefl`
- 题型：
  - `general`
  - `material`
  - `chart`
  - `comic`

## 前端流程

1. 用户在左侧题单设计对话区输入原始需求或题目问题
2. 前端先调用 `POST /api/writing/prompt-sheet/chat`
3. Java 后端代理到 Python orchestrator：`POST /prompt-sheet/chat`
4. Chat Agent 返回自然语言回复和画布动作：
   - `chat_only` / `ask_clarification` / `propose_patch`：只更新左侧对话，不改右侧题单
   - `create_prompt_sheet` / `update_prompt_sheet` / `replace_prompt_sheet`：需要更新右侧题单；优先直接返回 `promptSheet`
5. 如果 `promptSheet` 已返回，前端直接更新右侧画布
6. 如果只返回 `canvasInstruction`，前端把它作为明确生成指令，继续调用 `POST /api/writing/audit-topic` 解析：
   - 主题
   - 题型
   - 体裁
   - 字数范围
   - 关键要求
7. 前端调用 `POST /api/writing/generate-exam-prompt`，Java 代理到 Python `POST /prompt-sheet/generate`
8. Java 后端拿到 Python 结构化题单后立即落库一条 `writing_prompt_sheet`
9. 返回 `paper` 与 `promptSheetId` 给前端
10. 用户可继续在左侧讨论；右侧只有明确修改时才变更
11. 前端调用开始写作接口时带上 `promptSheetId`
12. 最终以 `sourceType=ai_generated` 创建考试写作会话，并把文档关联到对应题单

## POST /api/writing/prompt-sheet/chat

该接口是左侧题单设计对话 Agent。它负责对话、追问和判断是否需要改右侧 Canvas；当用户明确要求改右侧时，Python Chat Agent 会通过工具调用 Canvas Agent，Java 再负责落库。

实现上：

- 前端仍调用 Java：`POST /api/writing/prompt-sheet/chat`
- Java 代理到 Python：`POST /prompt-sheet/chat`
- Python 使用 OpenAI Agents SDK 运行 `Prompt Sheet Chat Agent`
- Chat Agent 需要改右侧时，通过 Agents as Tools 调用 `Prompt Sheet Canvas Agent`；工具入参使用结构化对象，不再把题型、主题、标准和参考信息揉进一个自由文本 prompt 字段
- 如果 Python 返回 `promptSheet`，Java 会落库题单，并把 `paper` / `promptSheetId` 回填到返回体

### 请求体

- `message`：用户当前消息
- `studyStage`：当前学段或考试类型
- `taskType`：当前任务类型，可选，如 `task1` / `task2`
- `promptType`：当前题型，可选，如 `general` / `material` / `chart` / `comic`
- `genre`：当前体裁，可选
- `wordRange`：当前字数范围，可选
- `requirements`：当前写作要求，可选
- `currentTopic`：右侧当前题单主题，可选
- `currentPromptText`：右侧当前题干，可选
- `hasCanvas`：右侧是否已有题单
- `aiProvider`：AI 服务提供商，可选

### 返回体

- `reply`：展示给用户的自然语言回复
- `action`：
  - `chat_only`
  - `ask_clarification`
  - `propose_patch`
  - `create_prompt_sheet`
  - `update_prompt_sheet`
  - `replace_prompt_sheet`
- `needsCanvasUpdate`：是否应该更新右侧题单
- `needsConfirmation`：是否需要用户确认后再改右侧
- `canvasInstruction`：给右侧题单生成器的清晰指令；仅在需要更新右侧时使用
- `patch`：结构化修改提示，可包含 `taskType` / `promptType` / `genre` / `wordRange` / `requirements` / `topic`
- `promptSheet`：可选。Chat Agent 已调用 Canvas Agent 时返回完整右侧题单，前端可直接更新画布

### 行为约束

- 用户只是提问、比较、讨论时，返回 `chat_only`，不得改右侧题单。
- 用户明确说“生成 / 修改 / 替换 / 应用到右侧”时，才返回需要更新 Canvas 的动作。
- 用户说“简单点”“更考试一点”“换个方向”这类模糊修改时，优先返回 `propose_patch` 且 `needsConfirmation=true`。
- Chat Agent 调用 Canvas 工具时，必须把结构化字段放入工具参数 `request` 对象：
  - `instruction`：本次对右侧题单的修改意图
  - `topic`：题单主题
  - `taskType`：`task1` / `task2`
  - `genre`：写作体裁或文体，例如 `argumentative essay`、`expository essay`、`letter`、`report`、`picture-based essay`、`图表作文`、`漫画作文`
  - `wordRange`：字数范围
  - `requirements`：写作要求
  - `preserveDetails`：必须保留的实体、指标、时间范围、人物、场景、数据或限制
- `examPromptStandard`、`promptTypeStandard`、`examStyleReference` 由 Python workflow 运行时自动注入 Canvas Agent 输入，不进入工具参数。
- `promptType` 仍是题单落库和右侧画布渲染需要的内部字段，取值保持 `general` / `material` / `chart` / `comic`；Python workflow 会根据 `genre`、用户指令和附件形态推导，不要求 Chat Agent 直接判断。

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

### `chartSpec` 约定

- `displayType` 只使用 `table` 或 `chart`。
- 只有题目本身要求表格时使用 `table`；折线图、柱状图、饼图、趋势图、双轴图都使用 `chart`。
- `columns` 必须包含横轴/分类列和至少一个数据列。
- `rows` 必须提供可渲染数据，不能只给表头；趋势图和双轴图应优先给 5 行以上数据。
- 双轴折线图仍使用 `displayType: "chart"`，通过多数据列表达，例如 `["Year", "GDP (trillion USD)", "Engel coefficient (%)"]`。
- `summary` 只描述图表类型和主要趋势，不替代 `rows`。
- 用户提供真实数据时，优先使用用户数据，并保留指标、单位、时间范围和趋势。
- 用户明确要求真实数据但未提供数值时，Python 侧应优先通过已接入的检索工具、数据查询工具或后端受控数据源获取；如果没有可用工具或查不到可信来源，不能编造真实数据，应提示补充来源或输出待补数据结构。
- 只有用户没有要求真实数据、也没有提供数据时，才允许生成练习用原创数据，且不得声称来自真实统计、真题或官方报告。

### 实现说明

`POST /api/writing/generate-exam-prompt` 是 Java 业务 API，内部代理 Python `POST /prompt-sheet/generate`。Python 侧使用 `Prompt Sheet Canvas Agent` 生成结构化题单；Java 侧负责：

- 解析字数范围
- 补全附件类型
- 将可渲染的 `chartSpec` 生成题单 PNG 图片
- 写入 `writing_prompt_sheet`
- 回填 `paper` / `promptSheetId`

### 图表图片渲染

图表题不再只依赖前端临时 SVG 预览。稳定链路为：

```text
Python Canvas Agent 生成 chartSpec
-> Java 后端校验并补全题单字段
-> PromptSheetChartImageService 根据 chartSpec 渲染 PNG
-> 写入 attachmentImageUrl
-> 落库 writing_prompt_sheet.attachment_payload_json.imageUrl
-> 前端右侧画布优先展示 attachmentImageUrl
```

约定：

- `chartSpec` 仍然保留，作为后续编辑、复制和复查的数据源。
- `attachmentImageUrl` 指向后端生成的 PNG，例如 `/uploads/prompt-sheets/charts/{hash}.png`。
- 本地开发时 Vite 代理 `/uploads` 到 Java 后端；生产环境应由后端或网关暴露同一路径。
- 只有 `chartSpec.displayType != "table"` 且具备可渲染数据时生成 PNG；表格题继续按表格展示。
- 生成文件名由 `chartSpec` 内容哈希决定，同一份图表数据重复生成会复用同一张图片。
- 前端展示优先级：`attachmentImageUrl` 图片 > `comicScenes` / `chartSpec` 结构化预览 > 文本附件说明。

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
- 图表题返回结构化表格 / 图表数据
- 漫画题返回结构化分镜
- 命题风格会参考同学段真题样本，并落到 AI 题库表而不是公共真题表

## 动态题单标准

Python 题单 workflow 不把具体学段风格和题型细则写死在 `Prompt Sheet Canvas Agent` 通用 prompt 中。Canvas 通用 prompt 只保留题单生成的底线规则、结构化输出要求和用户硬约束保真规则。

运行时会根据 `studyStage` / `promptType` 动态注入：

- `examPromptStandard`：当前学段或考试类型的命题风格标准
- `promptTypeStandard`：当前题型的结构化输出规则，以及当前学段对该题型的覆盖规则

当前资产位置：

- `python/ai_orchestrator/prompts/shared/prompt_sheet_exam_standards.md`
- `python/ai_orchestrator/prompts/shared/prompt_sheet_prompt_type_standards.md`

注入原则：

- `studyStage=toefl` 时只注入 TOEFL 标准，不注入 IELTS 图表题规则。
- `studyStage=ielts` 且 `promptType=chart` 时才注入 IELTS Task 1 图表题专项规则。
- `promptType=chart` 时注入通用 chart 规则，再叠加当前学段的 chart 覆盖规则。
- 题型标准按 `promptType` 和 `promptType:studyStage` 两层加载；每个支持学段都应具备 `general/material/chart/comic` 的学段专项覆盖，避免同一题型在不同学段套用同一套风格。
- 未识别的学段或题型不注入对应标准，保持通用 Canvas 行为。
- 禁止在 Canvas 通用 prompt 中重新加入某个具体考试的专属格式，避免污染其他学段。

## 真题风格参考

Python 题单 workflow 会在运行时按当前 `studyStage` 生成一段 `examStyleReference`，用于约束 Canvas Agent 的命题风格。

当前已接入：

- `postgrad`：基于本地考研英语真题种子数据抽象风格信号

后续可按同一接口扩展：

- K12：参考校内教材/新概念风格题型
- CET4 / CET6：参考四级/六级真题题型
- IELTS / TOEFL：参考国际考试任务风格

使用原则：

- 只抽象题型、题干结构、写作要求、语气风格和生成边界
- 不把真题原文作为 few-shot 示例直接复制给模型
- 不允许生成内容声称来自真实真题或题库样本
- 用户给定的主题、指标、时间范围、材料和题型仍是最高优先级硬约束

链路：

```text
题单请求
-> 根据 studyStage / taskType / promptType / topic 检索同考试样本
-> 生成 examStyleReference
-> 注入 Prompt Sheet Chat / Canvas Agent 输入
-> 生成原创结构化题单
```

当前 Python 侧只为 `postgrad` 配置了本地参考源，默认读取 `backend/src/main/resources/db/postgrad_prompt_seed.sql`；其他学段未配置时不注入题库风格参考，保持原行为。后续如改为查询 MySQL，应优先通过后端受控 API 按 `studyStage` 返回脱敏后的风格参考包，而不是让 Python 直接读取业务库原文。
