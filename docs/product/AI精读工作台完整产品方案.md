# AI 精读工作台完整产品方案

## 1. 产品定位

AI 精读工作台不是普通翻译器，也不是通用 PDF AI 问答工具。

它的核心定位是：

> 面向中文英语学习者，把用户上传的英文文章、PDF、考试材料、技术文档，转化为可精读、可提问、可笔记、可复习、可沉淀的长期学习资产。

产品主链路：

```text
上传文章 / PDF / 题目
→ 高质量 PDF 解析 / OCR / 文档结构化
→ AI 生成段落级精读材料
→ 用户在左侧阅读原文和译文
→ 右侧 Agent 围绕当前段落回答问题、整理笔记、沉淀知识点
→ 自动生成生词、短语、句型、语法点、复习卡、文章学习报告
→ 回到 Hub 持续复习
```

## 2. 产品核心差异

普通翻译工具：

```text
输入文本 → 输出译文
```

普通 PDF AI：

```text
上传 PDF → 总结 / 问答
```

AI 精读工作台：

```text
上传英文材料
→ AI 加工成精读材料
→ 段落级双语学习
→ Agent 辅助理解
→ 自动沉淀学习资产
→ 生成复习计划和学习报告
```

核心差异：

- 面向英语学习，而不是通用文档处理。
- 面向中文学习者，解释方式适配高考、四六级、考研、外刊精读和专业英语。
- 每次阅读都会沉淀生词、短语、句型、语法、笔记和复习卡。
- Agent 不是通用聊天框，而是知道当前文章、当前段落、当前选区和用户学习目标的翻译学习助手。
- Hub 承接素材发现、继续学习、学习资产和持续复习闭环。

## 3. 用户与场景

### 3.1 目标用户

- 高中生：高考阅读、长难句、作文表达积累。
- 大学生：四六级阅读、外刊精读、词汇短语积累。
- 考研用户：阅读理解、题干定位、选项干扰、长难句拆解。
- 职场用户：英文报告、技术文档、行业材料阅读。
- 自学用户：外刊、论文、英文书籍、技术文章精读。

### 3.2 核心场景

#### 外刊精读

用户上传或选择外刊文章，AI 生成段落译文、表达拆解、可迁移句型、写作素材。

#### 考试精读

用户上传阅读题或真题 PDF，AI 生成题干关键词、定位句、干扰项分析、答案依据和错题卡。

#### 技术文档精读

用户上传论文或技术文档，AI 生成术语解释、中文技术译法、段落逻辑和术语库。

#### 普通沉浸阅读

用户上传任意英文材料，AI 帮助翻译、解释、整理笔记和生成复习资产。

## 4. 信息架构

```text
翻译中心 Hub
├── 新建精读
│   ├── 上传 PDF / DOCX / TXT / MD
│   ├── 粘贴文本
│   ├── 从素材库选择
│   └── 选择 Agent 模式
├── 继续学习
│   ├── 进行中的材料
│   └── 最近打开
├── 素材库
│   ├── 外刊
│   ├── 考试材料
│   ├── 技术文档
│   └── 用户导入
├── 我的学习资产
│   ├── 生词
│   ├── 短语
│   ├── 句型
│   ├── 语法点
│   ├── 笔记
│   ├── 复习卡
│   └── 学习报告
└── AI 精读工作台
    ├── 文档阅读区
    ├── 翻译学习 Agent
    ├── 学习资产栏
    └── 学习画布 / 知识图谱
```

## 5. 核心页面

### 5.1 翻译中心 Hub

Hub 是入口和学习资产中心，不承载复杂工作。

Hub 展示：

- 新建精读入口。
- 最近继续学习。
- 素材库。
- 我的翻译 / 精读记录。
- 今日推荐。
- 学习资产统计。
- 最近笔记。
- 复习提醒。

Hub 的目标：

- 让用户知道可以从哪里开始。
- 让用户看到自己已经沉淀了哪些学习资产。
- 让用户能回到未完成材料继续学习。

### 5.2 新建精读弹窗

新建弹窗负责收集精读任务配置，避免工作台页面被配置项打扰。

配置项：

- 材料来源：上传、粘贴、素材库。
- 文件类型：PDF、DOCX、TXT、MD。
- Agent 模式：沉浸精读、外刊精读、考试精读、技术文档。
- 输出目标：段落翻译、长难句、短语、生词、语法、复习卡。
- PDF 解析方式：自动解析、OCR 识别、手动校正。

创建后进入解析流程。

### 5.3 AI 解析中页面

解析中页面让用户感知系统正在加工材料。

步骤：

```text
1. 上传文件
2. 识别文档类型
3. PDF 高质量解析
4. OCR 识别扫描内容
5. 文档结构化
6. 段落和句子切分
7. AI 段落翻译
8. 提取生词、短语、句型、语法点
9. 生成初始复习卡
10. 进入精读工作台
```

解析状态：

- 等待中。
- 处理中。
- 需要用户确认。
- 完成。
- 失败可重试。

### 5.4 AI 精读工作台

统一使用一个工作台页面，不为外刊、考试、技术文档分别做不同页面。

页面结构：

```text
顶部工具栏
左侧 PDF 大纲 / 页码导航
中间 PDF 原文学习画布 / 精读文本
右侧翻译学习 Agent / 当前页笔记
底部学习资产栏
可选：学习画布 / 知识图谱
```

工作台第一屏采用类似 IDE 的三栏结构：

- 左侧是当前文档的大纲和页码导航，按 `pageNumber` 聚合解析后的 `DocumentBlock`，点击页码或段落后同步中间 PDF 页和右侧 Agent 上下文。
- 中间是主工作区，默认展示 PDF 原貌画布，保留 PDF 图片、表格和原始版式；精读文本作为辅助视图，用于查看解析后的结构化段落。
- 右侧是 Agent Console，不再只是翻译结果列表，而是围绕当前页、当前段落或当前选区进行解释、翻译、提炼表达和整理笔记。
- 当前页学习笔记放在右侧 Agent 区，按文档和页码持久化，避免破坏 PDF 原文，也避免让中间阅读区变窄。

#### 顶部工具栏

内容：

- 材料名。
- 文件类型。
- AI 解析状态。
- 当前 Agent 模式。
- 学习进度。
- 全局操作。

示例：

```text
The Economist · AI and Jobs.pdf
AI 已生成精读材料
模式：沉浸精读
进度：42%

[翻译全文] [生成复习卡] [生成学习报告] [完成学习]
```

#### Agent 模式切换

同一套工作台支持多种模式。

- 沉浸精读。
- 外刊精读。
- 考试精读。
- 技术文档。

切换模式后：

- 左侧文档结构不变。
- 右侧 Agent 的分析重点变化。
- 底部学习资产类型变化。

#### 左侧精读材料区

中间是主阅读区，左侧大纲只负责定位，不承载主要阅读内容。

PDF 材料支持双视图，但默认入口是 PDF 学习画布：

- PDF 学习画布：使用 pdf.js 渲染当前会话上传的 PDF，保留图片、表格和原始版式；画布上叠加文本层、批注层、页边笔记和 Agent 选区入口。
- 精读文本：展示解析后的结构化段落，作为辅助视图，用于逐段翻译、复制、提问和校正解析结果。

PDF 学习画布分为三层：

- `canvas layer`：渲染 PDF 原貌，尽量接近用户在 Acrobat/浏览器中看到的版式。
- `text layer`：承载 PDF 内置文本或后端 OCR/解析文本，支持用户复制、选中和发送给 Agent。
- `annotation layer`：承载高亮、页边笔记、选区卡片和后续 bbox 锚点，不直接破坏原 PDF 文件。

当前版本已经引入 `pdfjs-dist`。如果 PDF 原始文本层不可用，工作台仍会通过结构化文本视图兜底，保证用户至少可以复制、做笔记和问 Agent。后续需要补齐 bbox 后，批注可以精确锚定到页面坐标。

当前工作台 v1 已实现：

- 三栏 IDE 布局：PDF 大纲、PDF 学习画布、Agent Console。
- PDF 材料默认进入 PDF 学习画布，非 PDF 保留精读文本。
- PDF 画布支持翻页、缩放、文本层选中、复制当前选区或当前页解析文本、选区高亮和问 Agent。
- 点击左侧大纲页码或段落会同步中间 PDF 页码、当前段落和右侧 Agent 上下文。
- 右侧支持当前页学习笔记，并按文档 ID 和页码保存在本地会话。

材料会被转换为结构化 `DocumentBlock`：

- 标题。
- 小标题。
- 段落。
- 句子。
- 题目。
- 选项。
- 表格。
- 列表。
- 引用。
- 图片说明。

每个 block 都有唯一 ID，用于绑定：

- 原文。
- 译文。
- 句子切分。
- 长难句。
- 生词。
- 短语。
- 语法点。
- 用户笔记。
- Agent 回答。
- 复习卡。

段落交互：

```text
P3
AI is reshaping work faster than we think...

[翻译本段] [长难句] [问 Agent] [收藏表达] [加入笔记] [生成卡片]
```

当前段落展开内容：

- 中文译文。
- 重点句。
- 短语 chips。
- 生词 chips。
- 语法点。
- 我的笔记输入框。
- 已生成复习卡数量。

#### 右侧翻译学习 Agent

右侧不是普通聊天框，而是当前材料的学习 Agent。

Agent 始终知道：

- 当前材料。
- 当前段落。
- 当前选区。
- 当前 Agent 模式。
- 用户历史问题。
- 已沉淀学习资产。

右侧内容：

- 当前上下文。
- Agent 回答区。
- 推荐译文。
- 表达拆解。
- 长难句解析。
- 学习笔记草稿。
- 可沉淀资产。
- 快捷操作。
- 多轮问答输入框。

快捷操作：

- 整理为笔记。
- 加入短语库。
- 加入生词本。
- 加入语法点。
- 生成复习卡。
- 拆解长难句。
- 解释考试依据。
- 生成学习报告片段。

#### 底部学习资产栏

底部展示当前材料沉淀的学习资产。

示例：

```text
生词 23 | 短语 12 | 句型 6 | 语法 5 | 复习卡 8 | 笔记 4
```

点击后打开对应资产列表。

### 5.5 学习画布 / 知识图谱

学习画布不是第一眼的主界面，而是从精读过程自动生成的整理视图。

入口：

- 顶部按钮：生成学习画布。
- 学习完成页：查看知识图谱。
- Agent 回答后：整理为图谱。

画布内容：

- 文章结构节点。
- 段落摘要节点。
- 重点句节点。
- 短语节点。
- 生词节点。
- 语法节点。
- 考试题定位节点。
- 复习卡节点。

画布能力：

- 节点拖拽。
- 自动布局。
- 节点连线。
- AI 一键整理。
- 导出为学习报告。
- 回跳原文段落。

## 6. 完整功能模块

### 6.1 高质量 PDF 解析

目标：

将用户上传的 PDF 转换为结构化可学习文档，而不是简单提取纯文本。

能力：

- 识别标题、小标题、段落、列表、表格。
- 保留页码和段落位置。
- 识别双栏布局。
- 识别页眉页脚并过滤。
- 识别题目和选项。
- 识别图片说明。
- 生成 DocumentBlock 结构。

输出结构：

```json
{
  "documentId": "doc_001",
  "title": "AI and Jobs",
  "sourceType": "pdf",
  "pages": [
    {
      "pageNumber": 1,
      "blocks": [
        {
          "id": "block_001",
          "type": "paragraph",
          "text": "AI is reshaping work faster than we think...",
          "pageNumber": 1,
          "bbox": [120, 240, 520, 310]
        }
      ]
    }
  ]
}
```

失败处理：

- 解析失败可重试。
- 用户可切换 OCR。
- 用户可手动粘贴文本作为兜底。
- 系统保留原 PDF 供用户对照。

当前后端 v1 已实现：

- `POST /api/translation/documents/parse` 支持 `multipart/form-data` 上传 PDF。
- `POST /api/translation/documents/import` 支持统一上传 PDF、DOCX、TXT、MD，并返回同一套 `DocumentBlock` 风格结构。
- PDF 导入已接入 `DocumentParseProvider` / `DocumentParseOrchestrator` 骨架，标准解析走 `local-pdfbox`，高质量解析可通过 `parseMode=high_quality` 预留第三方 Provider。
- 使用 PDFBox 提取文本层，并按页输出有序 `DocumentBlock`。
- 对标题、完整句和段落做基础学习块切分。
- 当 PDF 文本层为空或过少时返回 `parseStatus=NEEDS_OCR`、`ocrStatus=REQUIRED`。
- 文本层不足时会尝试调用当前配置的 OCR Provider；本地 PaddleOCR 是扫描 PDF 主方案，OCR 结果转成同一套 `DocumentBlock`。
- DOCX 通过 Apache POI 解析段落和简单表格，旧版 `.doc` 第一版提示用户转为 DOCX。
- TXT / MD 在后端统一解析，前端不再自行临时拆分文本。
- v1 暂不创建异步 OCR 任务，OCR 引擎不可用时返回 warning 让前端展示兜底入口。

### 6.2 OCR

目标：

处理扫描版 PDF、图片型 PDF、拍照上传材料。

能力：

- 页面图片化检测。
- OCR 识别英文正文。
- OCR 识别题目和选项。
- 置信度标记。
- 低置信度区域提示用户校正。
- OCR 结果进入同一套 DocumentBlock。

OCR 状态：

- 无需 OCR。
- OCR 处理中。
- OCR 完成。
- OCR 低置信度，需要校正。

当前后端 v1 已实现扫描型 PDF 检测、OCR Provider 切换入口和 OCR 结果转 `DocumentBlock`；本地 OCR 主 Provider 采用 PaddleOCR，Tesseract 仅作为兼容兜底。bbox、置信度和任务轮询仍属于下一阶段。

OCR 配置：

- `app.ocr.provider`：OCR Provider，默认 `tesseract`，本地 PaddleOCR 设置为 `paddle`。
- `app.ocr.paddle.base-url`：本地 PaddleOCR 服务地址，默认 `http://127.0.0.1:8090`。
- `app.ocr.paddle.endpoint`：本地 PaddleOCR PDF 识别接口，默认 `/ocr/pdf`。
- `app.ocr.paddle.language`：PaddleOCR 识别语言，默认 `ch,eng`。
- `app.ocr.paddle.timeout-ms`：PaddleOCR 单次调用超时时间，默认 `60000`。
- `app.ocr.tesseract-path`：Tesseract 兼容兜底可执行文件路径，默认 `tesseract`。
- `app.ocr.language`：Tesseract 识别语言，默认 `eng`。
- `app.ocr.dpi`：Tesseract PDF 渲染 DPI，默认 `220`。
- `app.ocr.timeout-seconds`：Tesseract 单次 OCR 超时时间，默认 `45`。

### 6.3 真实 AI 段落翻译

目标：

对每个段落生成适合学习的译文，而不是只生成直译。

输出内容：

- 段落译文。
- 重点句译文。
- 长难句拆解。
- 短语表达。
- 生词解释。
- 语法点。
- 可迁移表达。

翻译模式：

#### 沉浸精读

强调准确理解和语言点整理。

#### 外刊精读

强调表达迁移、写作素材、观点表达。

#### 考试精读

强调题干定位、同义替换、答案依据、干扰项。

#### 技术文档

强调术语一致性、技术译法、逻辑结构。

段落翻译数据结构：

```json
{
  "blockId": "block_001",
  "source": "AI is reshaping work faster than we think.",
  "translation": "AI 正在以超出我们预期的速度重塑工作方式。",
  "keyPhrases": [
    {
      "text": "reshape work",
      "meaning": "重塑工作方式"
    }
  ],
  "vocabulary": [
    {
      "word": "reshape",
      "meaning": "重塑",
      "level": "CET-6"
    }
  ],
  "grammarPoints": [
    {
      "title": "比较结构",
      "explanation": "faster than we think 表示比我们想象得更快"
    }
  ]
}
```

### 6.4 Agent 多轮问答

目标：

让右侧 Agent 围绕当前文档、当前段落、当前选区进行上下文问答。

Agent 上下文：

- 文档全文摘要。
- 当前段落。
- 当前选区。
- 当前模式。
- 历史问答。
- 已保存笔记。
- 已生成学习资产。

用户可问：

- 这句话怎么翻译？
- 这个结构怎么拆？
- 为什么这里用这个时态？
- 这个短语怎么用？
- 这句话考试里可能怎么考？
- 这段可以整理成什么笔记？
- 帮我生成复习卡。

Agent 输出：

- 直接回答。
- 推荐译文。
- 结构拆解。
- 学习笔记草稿。
- 可沉淀资产。
- 追问建议。

多轮能力：

- 追问当前段落。
- 对比前后文。
- 引用原文依据。
- 根据用户水平调整解释。
- 保存回答为笔记。

### 6.5 学习资产持久化

目标：

用户每次阅读产生的知识点都沉淀为长期资产。

资产类型：

- 生词。
- 短语。
- 句型。
- 语法点。
- 长难句。
- 段落摘要。
- 文章笔记。
- 复习卡。
- 错题卡。
- 术语。

资产字段：

```json
{
  "id": "asset_001",
  "type": "phrase",
  "text": "reshape work",
  "meaning": "重塑工作方式",
  "sourceDocumentId": "doc_001",
  "sourceBlockId": "block_001",
  "mode": "foreign_reading",
  "createdAt": "2026-06-08T10:00:00Z",
  "reviewStatus": "new"
}
```

资产回流：

- Hub 显示资产统计。
- 资产进入复习系统。
- 资产可导出为学习报告。
- 资产可生成学习画布。

### 6.6 自动生成复习计划

目标：

把阅读中沉淀的学习资产转化为可持续复习任务。

输入：

- 生词。
- 短语。
- 句型。
- 语法点。
- 复习卡。
- 错题卡。
- 用户掌握状态。

计划维度：

- 今日复习。
- 本周复习。
- 薄弱点复习。
- 考试专项复习。
- 文档回顾。

复习算法：

- 简化版间隔重复。
- 根据答题正确率调整复习间隔。
- 根据用户收藏次数和错误次数提高优先级。
- 考试模式下优先复习错题、同义替换、定位句。

复习任务示例：

```text
今日复习
- 生词：12 个
- 短语：8 个
- 长难句：3 个
- 错题卡：2 张
```

### 6.7 文章学习报告

目标：

用户完成一篇材料后，生成完整学习报告。

报告内容：

- 文章信息。
- 阅读用时。
- 完成进度。
- 全文摘要。
- 段落摘要。
- 重点词汇。
- 高频短语。
- 长难句。
- 语法点。
- 我的笔记。
- Agent 总结。
- 复习建议。
- 后续学习任务。

考试模式报告：

- 题目概览。
- 正确答案依据。
- 干扰项分析。
- 同义替换表。
- 错题卡。
- 薄弱点。

技术文档报告：

- 术语表。
- 中文技术译法。
- 章节逻辑。
- 关键概念。
- 可复用表达。

导出格式：

- 页面内报告。
- Markdown。
- Word。
- PDF。

### 6.8 学习画布 / 知识图谱

目标：

将用户在一篇材料中的学习资产可视化为结构图。

生成方式：

- 自动从文章结构生成。
- 从用户笔记生成。
- 从 Agent 问答生成。
- 从复习卡生成。

画布视图：

- 文章结构图。
- 词汇短语图。
- 长难句拆解图。
- 考试答题依据图。
- 技术术语图谱。

节点类型：

- 文章。
- 段落。
- 句子。
- 短语。
- 生词。
- 语法。
- 复习卡。
- 错题。
- 笔记。

交互：

- 点击节点回到原文。
- 拖拽节点。
- 自动布局。
- 折叠展开。
- Agent 整理布局。
- 导出图片或报告。

## 7. 数据模型

### 7.1 Document

```json
{
  "id": "doc_001",
  "title": "AI and Jobs",
  "sourceType": "pdf",
  "mode": "foreign_reading",
  "status": "ready",
  "progress": 42,
  "createdAt": "2026-06-08T10:00:00Z",
  "updatedAt": "2026-06-08T10:20:00Z"
}
```

### 7.2 DocumentBlock

```json
{
  "id": "block_001",
  "documentId": "doc_001",
  "type": "paragraph",
  "order": 3,
  "text": "AI is reshaping work faster than we think.",
  "pageNumber": 1,
  "bbox": [120, 240, 520, 310]
}
```

### 7.3 TranslationInsight

```json
{
  "id": "insight_001",
  "documentId": "doc_001",
  "blockId": "block_001",
  "translation": "AI 正在以超出我们预期的速度重塑工作方式。",
  "summary": "本段说明 AI 正在快速改变工作方式。",
  "phrases": [],
  "vocabulary": [],
  "grammarPoints": [],
  "cards": []
}
```

### 7.4 AgentMessage

```json
{
  "id": "msg_001",
  "documentId": "doc_001",
  "blockId": "block_001",
  "role": "assistant",
  "content": "这句话可以翻译为...",
  "createdAt": "2026-06-08T10:30:00Z"
}
```

### 7.5 LearningAsset

```json
{
  "id": "asset_001",
  "documentId": "doc_001",
  "blockId": "block_001",
  "type": "phrase",
  "text": "reshape work",
  "meaning": "重塑工作方式",
  "reviewStatus": "new"
}
```

## 8. 技术架构建议

### 8.1 前端

职责：

- Hub。
- 新建精读。
- 解析状态展示。
- 精读工作台。
- Agent 多轮 UI。
- 学习资产展示。
- 学习报告展示。
- 学习画布。

核心组件：

- `TranslationHubPage`
- `NewIntensiveReadingDialog`
- `DocumentParsingProgress`
- `IntensiveReadingWorkspace`
- `DocumentReader`
- `DocumentBlockView`
- `TranslationAgentPanel`
- `LearningAssetBar`
- `LearningReportPage`
- `LearningCanvas`

### 8.2 后端

职责：

- 文件上传。
- PDF 解析。
- OCR。
- 文档结构化。
- AI 翻译任务。
- Agent 多轮上下文。
- 学习资产持久化。
- 复习计划生成。
- 报告生成。

核心服务：

- `DocumentService`
- `FileStorageService`
- `PdfParsingService`
- `OcrService`
- `DocumentStructureService`
- `TranslationInsightService`
- `AgentConversationService`
- `LearningAssetService`
- `ReviewPlanService`
- `LearningReportService`
- `LearningCanvasService`

### 8.3 异步任务

PDF 解析、OCR、全文翻译、报告生成都应走异步任务。

任务状态：

- `PENDING`
- `RUNNING`
- `WAITING_USER_CONFIRMATION`
- `SUCCEEDED`
- `FAILED`

前端通过轮询或 SSE 获取状态。

## 9. 关键接口草案

### 9.1 创建精读任务

当前同步导入入口：

```http
POST /api/translation/documents/import
Content-Type: multipart/form-data
```

请求：

```text
file: PDF / DOCX / TXT / MD 文件
mode: immersive | exam
parseMode: standard | high_quality
```

返回：同一套 `TranslationDocumentParseResponse`，前端据此进入 AI 精读工作台。

PDF 解析响应会额外带上 Provider 元信息：

```json
{
  "provider": "local-pdfbox",
  "parseMode": "standard",
  "fallbackUsed": false,
  "elapsedMs": 120
}
```

当前 `third-party-layout` Provider 仅保留骨架，未配置第三方服务时不会影响标准解析；后续接入 Marker/Surya、PaddleOCR、Google Document AI、AWS Textract 或自研解析时，应优先实现为新的 Provider，而不是绕过 Orchestrator。

PDF 兼容解析入口：

```http
POST /api/translation/documents/parse
Content-Type: multipart/form-data
```

请求：

```text
file: PDF 文件
```

返回：

```json
{
  "documentId": "uuid",
  "fileName": "article.pdf",
  "sourceType": "PDF",
  "parseStatus": "SUCCEEDED",
  "ocrStatus": "NOT_REQUIRED",
  "pageCount": 1,
  "blockCount": 3,
  "blocks": [
    {
      "id": "p1-b1",
      "type": "paragraph",
      "order": 1,
      "pageNumber": 1,
      "text": "AI is reshaping work faster than we think...",
      "confidence": null
    }
  ],
  "warnings": []
}
```

扫描型或无文本层 PDF 返回：

```json
{
  "parseStatus": "NEEDS_OCR",
  "ocrStatus": "REQUIRED",
  "blocks": [],
  "warnings": ["PDF 文本层为空或过少，需要 OCR 后再进入精读解析。"]
}
```

如果 OCR 引擎可用且识别成功，则返回：

```json
{
  "parseStatus": "SUCCEEDED",
  "ocrStatus": "SUCCEEDED",
  "blocks": [
    {
      "id": "p1-ocr-b1",
      "type": "paragraph",
      "order": 1,
      "pageNumber": 1,
      "text": "OCR recovered text...",
      "confidence": null
    }
  ],
  "warnings": ["PDF 文本层为空，已使用 OCR 结果生成精读材料。"]
}
```

长期异步创建入口：

```http
POST /api/translation/documents
```

请求：

```json
{
  "sourceType": "pdf",
  "mode": "foreign_reading",
  "fileId": "file_001",
  "options": {
    "enableOcr": true,
    "generateCards": true,
    "generateReport": true
  }
}
```

返回：

```json
{
  "documentId": "doc_001",
  "taskId": "task_001"
}
```

### 9.2 查询解析状态

```http
GET /api/translation/documents/{documentId}/tasks/{taskId}
```

### 9.3 获取文档结构

```http
GET /api/translation/documents/{documentId}
```

### 9.4 获取段落精读结果

```http
GET /api/translation/documents/{documentId}/blocks/{blockId}/insight
```

### 9.5 Agent 提问

```http
POST /api/translation/documents/{documentId}/agent/messages
```

请求：

```json
{
  "blockId": "block_001",
  "selection": "faster than we think",
  "message": "这个短语怎么理解？",
  "mode": "foreign_reading"
}
```

### 9.6 保存学习资产

```http
POST /api/translation/documents/{documentId}/assets
```

### 9.7 生成复习计划

```http
POST /api/translation/review-plans
```

### 9.8 生成学习报告

```http
POST /api/translation/documents/{documentId}/report
```

### 9.9 生成学习画布

```http
POST /api/translation/documents/{documentId}/canvas
```

## 10. MVP 与完整版本拆分

### 10.1 MVP

MVP 目标：跑通核心学习闭环。

范围：

- Hub 新建精读。
- TXT / MD / 粘贴文本。
- PDF 上传入口。
- 基础 PDF 文本解析。
- 精读工作台 UI。
- 段落原文 + 译文。
- 右侧 Agent UI。
- 模拟或真实段落翻译。
- 保存笔记、短语、生词、复习卡。
- Hub 展示学习资产统计。

### 10.2 V1

V1 目标：可正式用于学习。

范围：

- 高质量 PDF 解析。
- OCR。
- 真实 AI 段落翻译。
- Agent 多轮问答。
- 学习资产持久化。
- 复习卡生成。
- 学习报告生成。

### 10.3 V2

V2 目标：形成差异化学习系统。

范围：

- 自动复习计划。
- 考试模式深度解析。
- 技术文档术语库。
- 学习画布 / 知识图谱。
- Agent 自动整理笔记。
- 学习报告导出。

### 10.4 V3

V3 目标：形成长期学习平台。

范围：

- 个性化学习路径。
- 跨文档知识图谱。
- 多材料对比学习。
- 复习效果追踪。
- 班级 / 教师场景。
- 素材库推荐系统。

## 11. 验收标准

### 11.1 PDF 解析

- 能上传 PDF。
- 能提取文本。
- 能切分段落。
- 能保留页码。
- 扫描版 PDF 能进入 OCR 流程。
- 解析失败能重试。

### 11.2 精读工作台

- 能展示结构化原文。
- 能展开段落译文。
- 能切换 Agent 模式。
- 能选中句子提问。
- 能保存笔记和学习资产。
- 能回到 Hub 继续学习。

### 11.3 Agent

- 能基于当前段落回答问题。
- 能基于当前选区回答问题。
- 能多轮追问。
- 能把回答保存为笔记。
- 能生成复习卡。

### 11.4 学习资产

- 生词、短语、句型、语法、笔记、复习卡可保存。
- 资产能关联原文段落。
- Hub 能展示资产统计。
- 资产能进入复习计划。

### 11.5 学习报告

- 完成学习后可生成报告。
- 报告包含摘要、重点词汇、短语、语法、笔记、复习建议。
- 报告可导出。

### 11.6 学习画布

- 能从当前文档资产生成图谱。
- 节点能回跳原文。
- Agent 能自动整理布局。
- 能导出图片或报告。

## 12. 风险与取舍

### 12.1 PDF 解析复杂度高

风险：

- 双栏 PDF。
- 扫描 PDF。
- 表格。
- 题目结构。
- 页眉页脚。

策略：

- 第一版先支持常见文本 PDF。
- 扫描 PDF 进入 OCR。
- 复杂表格先转为文本块。
- 提供手动校正入口。

### 12.2 Agent 输出质量不稳定

风险：

- 翻译不稳定。
- 语法解释错误。
- 考试解析不严谨。

策略：

- 不同模式使用不同 prompt。
- 输出结构化 JSON。
- 重要内容引用原文依据。
- 用户可编辑和纠错。

### 12.3 画布容易变复杂

风险：

- 画布会拖慢第一版。
- 用户不一定愿意手动整理。

策略：

- 先做精读工作台。
- 画布作为自动生成视图。
- 不把画布作为第一交互入口。

### 12.4 学习资产过多导致混乱

风险：

- 生词、短语、语法、卡片过多。
- 用户不知道复习什么。

策略：

- Agent 自动筛选重点。
- 资产分优先级。
- 生成今日复习计划。
- 允许用户收藏 / 忽略。

## 13. 推荐实施顺序

```text
阶段 1：重做工作台 UI
阶段 2：TXT / MD / 粘贴文本真实进入精读工作台
阶段 3：基础 PDF 解析
阶段 4：段落级真实 AI 翻译
阶段 5：右侧 Agent 多轮问答
阶段 6：学习资产保存
阶段 7：复习卡和复习计划
阶段 8：高质量 PDF + OCR
阶段 9：学习报告
阶段 10：学习画布 / 知识图谱
```

## 14. 总结

AI 精读工作台的关键不是做一个更强的翻译器，也不是做一个通用 PDF AI。

真正的产品价值是：

> 把英文材料转化成可理解、可提问、可笔记、可复习、可长期沉淀的学习资产。

页面设计应围绕这个核心：

- 左侧是 PDF 大纲和页码导航。
- 中间是 PDF 原貌学习画布和结构化精读文本。
- 右侧是翻译学习 Agent 和当前页笔记。
- 底部是学习资产沉淀。
- Hub 是持续学习和复习中心。

完整能力包括：

- 高质量 PDF 解析。
- OCR。
- 真实 AI 段落翻译。
- Agent 多轮问答。
- 学习资产持久化。
- 自动生成复习计划。
- 文章学习报告。
- 学习画布 / 知识图谱。

最终产品形态：

> 一个面向中文英语学习者的 AI 英语精读 IDE。
