# PDF 高质量解析与 OCR 实现题目

## 背景

AI 精读工作台需要支持用户上传 PDF，并把 PDF 转换成可翻译、可提问、可做笔记、可沉淀学习资产的结构化材料。

本阶段优先完成两件事：

1. 高质量 PDF 文本层解析。
2. 扫描型 PDF / 图片型 PDF 的 OCR 识别入口。

参考文件：

- `docs/product/AI精读工作台完整产品方案.md`
- `backend/AGENTS.md`
- `backend/pom.xml`
- `backend/src/main/java/com/personalenglishai/backend/controller/translation/TranslationDocumentController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/translation/TranslationDocumentParseService.java`
- `backend/src/main/java/com/personalenglishai/backend/service/translation/TesseractTranslationOcrService.java`

总体要求：

1. 保持后端分层：controller 只接收上传和返回响应，解析逻辑放在 service。
2. PDF 解析结果必须输出统一 `DocumentBlock` 风格结构，方便后续段落翻译、Agent 问答和笔记沉淀。
3. 文本 PDF 和扫描 PDF 要有明确状态区分。
4. OCR 引擎不可用时不能导致接口 500，要返回可展示的 warning 和兜底状态。
5. 新增功能必须有单元测试或集成测试覆盖。

---

## 题目 1：PDF 上传与文本层结构化解析

题目类型：backend feature

难度：中等偏难（完成）

### A 小题：实现 PDF 上传解析接口

#### Prompt

请实现翻译模块的 PDF 上传解析接口。

接口要求：

```http
POST /api/translation/documents/parse
Content-Type: multipart/form-data
```

请求字段：

```text
file: PDF 文件
```

后端需要：

1. 校验上传文件不能为空。
2. 校验文件类型为 PDF。
3. 使用 PDFBox 读取 PDF。
4. 返回结构化响应，至少包含：
   - `documentId`
   - `fileName`
   - `sourceType`
   - `parseStatus`
   - `ocrStatus`
   - `pageCount`
   - `blockCount`
   - `blocks`
   - `warnings`
5. `blocks` 中每个 block 至少包含：
   - `id`
   - `type`
   - `order`
   - `pageNumber`
   - `text`
   - `confidence`

#### 难度

中等

#### 验收标准

- 上传普通文本 PDF 后接口返回 200。
- 返回结果中 `parseStatus=SUCCEEDED`。
- 返回结果中 `ocrStatus=NOT_REQUIRED`。
- `pageCount` 能正确反映 PDF 页数。
- `blocks` 有稳定顺序，`order` 从 1 开始递增。
- 非 PDF 文件返回参数错误，不返回系统错误。
- 空文件返回参数错误。

### B 小题：优化 PDF 文本层解析质量

#### Prompt（完成）

请优化 PDF 文本层解析质量，让解析结果更适合 AI 精读工作台使用。

要求：

1. 按页提取 PDF 文本。
2. 将提取文本切分为学习 block，而不是简单返回整篇纯文本。
3. 支持基础段落重组：
   - 去掉空行。
   - 合并被 PDF 换行打断的句子。
   - 合并英文断词换行，例如 `trans-` + `lation`。
   - 过滤简单页码行。
4. 尽量识别标题和正文段落：
   - 短文本且不以句号、问号、感叹号结尾，可标记为 `heading`。
   - 普通正文标记为 `paragraph`。
5. 保留每个 block 的页码。

#### 难度

中等偏难

#### 验收标准

- 多段文本 PDF 不会被合并成一个超长 block。
- 换行造成的断句能被合并为自然段。
- 页码行不会作为正文 block 出现。
- 标题和正文能用 `type` 做基础区分。
- 同一 PDF 多次解析时 block 顺序稳定。
- 有测试覆盖至少一个正常文本 PDF 的解析结果。

---

## 题目 2：扫描型 PDF 检测与 OCR fallback

题目类型：backend feature / OCR 集成

难度：偏难（完成）

### A 小题：检测文本层缺失并进入 OCR 流程

#### Prompt

请实现扫描型 PDF 或图片型 PDF 的检测逻辑。

要求：

1. PDFBox 提取文本后，判断文本层是否足够。
2. 如果提取文本为空或过少，不要返回空白文档。
3. 标记该 PDF 需要 OCR。
4. 返回明确状态：
   - `parseStatus=NEEDS_OCR`
   - `ocrStatus=REQUIRED`
5. 返回 `warnings`，说明 PDF 文本层为空或过少，需要 OCR。
6. 保持接口响应结构和文本 PDF 一致。

#### 难度

中等

#### 验收标准

- 空文本层 PDF 不会返回 `SUCCEEDED`。
- 空文本层 PDF 返回 `NEEDS_OCR`。
- `blocks` 为空时必须有 `warnings`。
- 前端可以仅根据 `parseStatus` 和 `ocrStatus` 判断是否展示 OCR 状态。
- 有测试覆盖空文本 PDF 或扫描型 PDF 的状态判断。

### B 小题：接入 Tesseract OCR 并转为 DocumentBlock

#### Prompt（完成）

请实现 OCR fallback 能力：当 PDF 文本层不足时，尝试调用 Tesseract OCR，并把识别结果转换为统一 `DocumentBlock`。

要求：

1. 新增 OCR service 抽象，避免 PDF 解析服务直接依赖具体 OCR 实现。
2. 默认实现可以使用 Tesseract 命令行。
3. 支持配置项：
   - `app.ocr.tesseract-path`
   - `app.ocr.language`
   - `app.ocr.dpi`
   - `app.ocr.timeout-seconds`
4. OCR 流程：
   - 将 PDF 页面渲染为图片。
   - 调用 Tesseract 识别英文文本。
   - 将 OCR 文本按页转换为 block。
5. OCR 成功时返回：
   - `parseStatus=SUCCEEDED`
   - `ocrStatus=SUCCEEDED`
   - `blocks` 中使用 OCR 文本。
6. OCR 引擎不可用、超时或识别失败时：
   - 不抛 500。
   - 返回 `parseStatus=NEEDS_OCR`。
   - 返回 `ocrStatus=REQUIRED`。
   - 返回可展示的 warning。

#### 难度

偏难

#### 验收标准

- 文本层为空但 OCR 成功时，接口返回 `SUCCEEDED`。
- OCR 生成的 block 能保留页码。
- OCR block 使用统一字段结构，后续可以直接进入段落翻译和 Agent 问答。
- Tesseract 未安装时接口不崩溃。
- OCR 超时时能终止进程并返回失败提示。
- 有测试覆盖：
  - OCR service 成功返回文本。
  - OCR 不可用时返回兜底状态。
  - PDF 解析服务能把 OCR 结果转为 block。

---

## 推荐测试命令

```bash
cd backend
./mvnw.cmd -q -Dtest=TranslationDocumentParseServiceTest test
./mvnw.cmd -q test
```

## 交付说明

完成后请说明：

1. 新增了哪些接口。
2. PDFBox 和 OCR 的依赖边界是什么。
3. Tesseract 没有安装时系统如何降级。
4. 哪些测试已经运行。
5. 是否需要前端继续接入上传入口和 OCR 状态展示。
