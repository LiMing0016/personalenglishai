# 统一文档解析管线与沉浸式翻译 IDE 题目

## 背景

当前 AI 精读工作台已经有 Hub、上传入口、PDF 基础解析和工作台页面雏形，但产品主链路还没有完全跑通：

```text
用户上传 PDF / DOCX / TXT / MD
→ 后端统一解析为 DocumentBlock
→ 左侧像 IDE 一样展示真实原文
→ 用户点击段落或选区
→ 右侧 Agent 围绕当前 block 翻译、解释、整理笔记
→ 学习资产沉淀到底部状态栏和后续复习系统
```

本需求目标不是做高保真 Word / PDF 阅读器，而是做适合 AI 精读的结构化文档阅读器。

参考文件：

- `docs/product/AI精读工作台完整产品方案.md`
- `tasks/6.9/PDF高质量解析与OCR实现题目.md`
- `backend/AGENTS.md`
- `web/AGENTS.md`
- `backend/src/main/java/com/personalenglishai/backend/controller/translation/TranslationDocumentController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/translation/TranslationDocumentParseService.java`
- `web/src/api/translation.ts`
- `web/src/pages/app/TranslationPage.vue`
- `web/src/pages/app/TranslationWorkspacePage.vue`
- `web/src/pages/app/translationWorkspaceData.ts`

总体原则：

1. 先做“可读、可学习、可被 Agent 引用”，不要追求原文件像素级还原。
2. 后端解析结果必须统一成 `DocumentBlock`，前端不按文件类型写多套页面。
3. 左侧是原文工作区，右侧是 Agent 控制台，不要再回到卡片式学习页。
4. 文件解析失败、OCR 不可用、格式暂不支持时，必须有清晰降级提示。
5. 每个阶段都要有测试或可验证标准。

---

## 题目 1：统一文档导入接口与解析管线

题目类型：backend feature

难度：偏难

### A 小题：实现统一文档导入接口

#### Prompt

请将翻译模块的上传解析入口升级为统一文档导入接口。

接口建议：

```http
POST /api/translation/documents/import
Content-Type: multipart/form-data
```

请求字段：

```text
file: 用户上传文件
mode: immersive | exam
```

第一版支持格式：

- PDF
- DOCX
- TXT
- MD

后端需要：

1. 根据文件扩展名和 content type 判断文档类型。
2. 将不同格式分发给不同 parser。
3. 所有 parser 输出统一响应结构。
4. 响应至少包含：
   - `documentId`
   - `fileName`
   - `sourceType`
   - `parseStatus`
   - `ocrStatus`
   - `pageCount`
   - `blockCount`
   - `blocks`
   - `warnings`
5. 暂不支持的格式返回明确错误，不返回系统 500。

#### 难度

偏难

#### 验收标准

- PDF、DOCX、TXT、MD 都可以走同一个导入接口。
- 前端不需要为不同文件类型调用不同 endpoint。
- 不支持的文件类型返回 400 和清晰提示。
- 解析失败返回可展示的 warning 或错误信息。
- 后端 controller 不直接写解析逻辑，只做请求入口。
- 有测试覆盖文件类型识别和 parser 分发。

### B 小题：建立 parser adapter 架构

#### Prompt

请为文档解析建立可扩展的 parser adapter 架构。

建议结构：

```text
TranslationDocumentImportService
├── PdfDocumentParser
├── DocxDocumentParser
├── TextDocumentParser
└── MarkdownDocumentParser
```

要求：

1. 定义统一接口，例如：

```java
interface TranslationDocumentParser {
    boolean supports(UploadedDocument file);
    TranslationDocumentParseResponse parse(UploadedDocument file);
}
```

2. `TranslationDocumentImportService` 负责选择 parser。
3. PDF parser 可以复用已有 PDFBox + OCR 能力。
4. TXT / MD parser 直接读取文本并切分 block。
5. DOCX parser 后续接 Apache POI。
6. parser 之间不要互相依赖。

#### 难度

中等偏难

#### 验收标准

- 新增格式时只需要增加 parser，不需要改 controller 主逻辑。
- PDF 解析能力不被破坏。
- TXT / MD 能生成稳定 block。
- parser 选择逻辑有单元测试。
- 代码符合 backend 分层规则。

---

## 题目 2：DOCX、TXT、MD 转 DocumentBlock

题目类型：backend feature

难度：中等偏难

### A 小题：实现 TXT / MD 文本解析

#### Prompt

请实现 TXT 和 MD 文件解析能力，将文本内容转换为 `DocumentBlock`。

要求：

1. 支持 UTF-8 文本读取。
2. TXT：
   - 按空行切分段落。
   - 长文本可按句子兜底切分。
3. MD：
   - `#`、`##`、`###` 识别为 heading。
   - 普通段落识别为 paragraph。
   - 列表识别为 list。
   - 代码块第一版可以作为 paragraph 或 code block。
4. 输出 block 保持原文顺序。
5. 每个 block 有稳定 id、type、order、text。

#### 难度

中等

#### 验收标准

- TXT 上传后左侧能展示真实文本段落。
- MD 上传后标题不会被当成普通正文。
- 空行不会生成空 block。
- order 从 1 开始递增。
- 有测试覆盖 TXT 和 MD 解析。

### B 小题：实现 DOCX 基础解析

#### Prompt

请使用 Apache POI 实现 DOCX 基础解析，将 Word 文档转换为 `DocumentBlock`。

要求：

1. 读取 DOCX 段落。
2. 根据段落样式或文本特征识别：
   - title
   - heading
   - paragraph
   - list
3. 读取简单表格，并转换为 table 类型 block。
4. 不要求还原 Word 版式。
5. 不支持旧版 `.doc`，第一版明确提示“暂不支持 DOC，请转为 DOCX”。

#### 难度

偏难

#### 验收标准

- DOCX 文档能解析出正文段落。
- 标题和普通正文至少能做基础区分。
- 简单表格不会直接丢失。
- 旧版 DOC 上传时返回明确提示。
- DOCX 解析失败时不影响 PDF/TXT/MD 解析。
- 有测试覆盖至少一个 DOCX 内存样例。

---

## 题目 3：统一 DocumentBlock 数据模型

题目类型：backend + frontend contract

难度：中等

### A 小题：定义统一 DocumentBlock DTO

#### Prompt

请定义 AI 精读工作台统一文档结构，后端所有解析器都必须输出这一结构。

建议字段：

```json
{
  "id": "p1-b1",
  "type": "paragraph",
  "order": 1,
  "pageNumber": 1,
  "text": "AI is changing how students read...",
  "level": null,
  "bbox": null,
  "confidence": null,
  "metadata": {}
}
```

字段说明：

- `id`：block 唯一 id。
- `type`：`title | heading | paragraph | list | table | quote | code | question | option`。
- `order`：全文顺序。
- `pageNumber`：PDF/OCR 可提供，TXT/MD/DOCX 可为空。
- `text`：原文内容。
- `level`：标题层级或列表层级。
- `bbox`：PDF/OCR 位置，第一版可为空。
- `confidence`：OCR 置信度，第一版可为空。
- `metadata`：表格、样式、来源 parser 等扩展信息。

#### 难度

中等

#### 验收标准

- PDF、DOCX、TXT、MD 返回同一套 block 字段。
- 前端不需要针对不同 sourceType 写不同字段解析。
- 新字段保持向后兼容。
- 测试中至少覆盖 3 种 block type。

### B 小题：前端适配统一 DocumentBlock

#### Prompt

请更新前端工作台数据模型，让工作台只消费统一 `DocumentBlock`。

要求：

1. `translationWorkspaceData.ts` 支持后端统一导入响应。
2. `TranslationWorkspacePage.vue` 按 block type 渲染：
   - title / heading 显示为标题样式。
   - paragraph 显示为正文。
   - list 显示为列表项。
   - table 第一版可用纯文本表格预览。
3. 当前段落高亮和 Agent 上下文仍基于 block id。
4. 本地草稿兼容旧数据。

#### 难度

中等

#### 验收标准

- PDF/TXT/MD/DOCX 的解析结果都能进入同一个工作台页面。
- 左侧展示不再出现“已上传 xxx，后续显示原文”这种占位文案。
- 点击任意 block 后，右侧 Agent 当前上下文同步变化。
- 旧 localStorage 草稿不导致页面崩溃。
- 前端相关测试通过。

---

## 题目 4：左侧沉浸式文档 Reader

题目类型：frontend feature

难度：中等偏难

### A 小题：实现 IDE 式原文阅读区

#### Prompt

请把 AI 精读工作台左侧升级为 IDE 式原文阅读区。

要求：

1. 左侧只展示原文和轻量定位信息，不塞译文卡片、短语卡片、笔记框。
2. 顶部文档栏显示：
   - 文件名
   - 文件类型
   - 解析状态
   - 页数
   - block 数量
3. 主阅读区按 block 连续展示。
4. 左侧 gutter 显示：
   - P1 / P2
   - Page 1
   - block type
5. 当前 block 高亮。
6. 阅读区独立滚动，右侧 Agent 保持固定。

#### 难度

中等偏难

#### 验收标准

- 左侧视觉上像文档 IDE，而不是普通卡片列表。
- 长文滚动时右侧 Agent 不丢失。
- 当前 block 清晰可见。
- hover 时可出现轻操作按钮，例如 Ask / Translate。
- 移动端不重叠、不横向溢出。

### B 小题：增强 block 交互与定位

#### Prompt

请增强左侧 Reader 的交互能力。

要求：

1. 点击 block 后右侧 Agent 切换上下文。
2. 支持快速定位到上一个/下一个 block。
3. 支持从右侧 Agent 消息回跳到来源 block。
4. 支持 block 状态标记：
   - 已翻译
   - 已提取短语
   - 已生成笔记
5. 第一版状态可存在前端本地，后续再持久化。

#### 难度

中等

#### 验收标准

- block 切换不会刷新整个页面。
- 当前 block id 在左右两侧保持一致。
- Agent 消息能带来源 block 信息。
- 学习资产生成后，左侧 block 能看到轻量状态标记。
- 有测试覆盖 active block 切换逻辑。

---

## 题目 5：右侧 Agent Console 与学习资产沉淀

题目类型：frontend + AI integration

难度：偏难

### A 小题：实现围绕当前 block 的 Agent Console

#### Prompt

请将右侧区域设计成真正的 Agent Console，而不是静态解释卡片。

要求：

1. 右侧顶部固定显示当前 block 上下文：
   - P 编号
   - 页码
   - 模式
   - 原文摘要
2. 提供快捷动作：
   - 翻译当前段落
   - 长难句拆解
   - 提取短语/生词
   - 整理为笔记
   - 生成复习卡
3. 输入框默认围绕当前 block 提问。
4. Agent 消息要记录来源 block id。
5. 切换 block 后，Agent 上下文立刻更新。

#### 难度

中等偏难

#### 验收标准

- 右侧不再只是展示卡片，而是可操作控制台。
- 快捷动作能自动带上当前 block。
- 用户输入问题时能看到当前上下文。
- 切换 block 后不会误用旧 block。
- 未选中 block 时有合理空状态。

### B 小题：学习资产沉淀到统一状态栏

#### Prompt

请将 Agent 生成的生词、短语、句型、语法、笔记、复习卡沉淀到底部学习资产状态栏。

要求：

1. 底部状态栏显示资产数量。
2. 用户点击快捷动作后，可以先生成本地资产草稿。
3. 资产需要关联：
   - documentId
   - blockId
   - type
   - text
   - sourceText
4. 底部点击某类资产后，可打开简单列表或弹层。
5. 后续持久化接口未接入前，先保证本地状态可用。

#### 难度

中等

#### 验收标准

- 生成短语/笔记/复习卡后，底部数量会变化。
- 资产能回溯到来源 block。
- 刷新前页面内状态不丢。
- 不影响左侧阅读滚动和右侧 Agent 输入。
- 有测试覆盖资产统计逻辑。

---

## 推荐技术方案

后端：

- Spring Boot `MultipartFile`
- PDF：Apache PDFBox
- OCR：Tesseract 命令行，后续可替换云 OCR
- DOCX：Apache POI
- TXT/MD：Java 原生读取，MD 后续可接 commonmark-java
- MyBatis / MySQL：后续持久化 document 和 block
- Redis 或任务表：后续异步解析和 OCR 任务状态

前端：

- Vue 3 + TypeScript
- Axios API 层
- 普通 Vue 组件实现 Document Reader
- localStorage 临时保存 workspace draft
- 后续如需富文本批注，再考虑 TipTap / ProseMirror

不建议第一版引入：

- PDF.js 高保真 PDF 阅读器
- Word 在线编辑器
- 复杂画布
- 旧版 `.doc` 解析
- 像素级版式还原

---

## 推荐测试命令

后端：

```bash
cd backend
./mvnw.cmd -q test
```

前端：

```bash
cd web
npm run build
```

翻译工作台相关单测：

```bash
node --test web/tests/translationHubData.test.ts web/tests/translationWorkspaceData.test.ts web/tests/translationWorkspacePage.test.ts
```

## 交付说明

完成后请说明：

1. 支持了哪些文件格式。
2. 每种格式如何解析为 `DocumentBlock`。
3. 哪些格式暂不支持，以及用户看到什么提示。
4. 左侧 Reader 如何展示不同 block type。
5. 右侧 Agent 如何绑定当前 block。
6. 实际运行了哪些测试。
