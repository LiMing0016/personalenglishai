# PaddleOCR 本地扫描服务完整实现题目

## 背景

AI 精读工作台需要把扫描版 PDF、图片教材、截图资料中的内容提取成可定位、可清洗、可进入 Agent 问答的结构化数据。当前后端已经预留 `APP_OCR_PROVIDER=paddle` 和 `PaddleTranslationOcrService` 调用本地 HTTP OCR 服务，但真正的 PaddleOCR 本地扫描服务尚未实现。

本题单要求实现一个可独立部署的 PaddleOCR 服务，而不是最小 demo。目标是让另一台电脑不需要手动安装 PaddleOCR，只要拉取项目并通过 Docker 启动，即可提供 OCR 能力给当前 Spring Boot 后端调用。

总体目标：

```text
PDF / 图片输入
  -> PaddleOCR SDK 识别
  -> 文本块、bbox、置信度、页码
  -> 公式检测与 LaTeX 识别
  -> 质量诊断与 warnings
  -> 统一 HTTP JSON 响应
  -> 后端转 DocumentBlock / DocumentElement / DocumentAsset / KnowledgeChunk
```

参考代码：

- `backend/src/main/java/com/personalenglishai/backend/service/translation/PaddleTranslationOcrService.java`
- `backend/src/main/java/com/personalenglishai/backend/service/translation/TranslationDocumentParseService.java`
- `backend/src/main/java/com/personalenglishai/backend/service/translation/TranslationDocumentKnowledgePipeline.java`
- `docs/architecture/文档知识提取管线设计.md`
- `docs/product/AI精读工作台完整产品方案.md`
- `docker-compose.local.yml`

总体原则：

1. OCR 服务必须独立部署，不和 Spring Boot 主服务耦合。
2. 必须使用 PaddleOCR 官方 Python SDK，不通过 shell 命令拼结果。
3. 服务默认支持 Docker 化，另一台电脑可以直接启动。
4. 输出必须结构化，不只返回纯文本。
5. 文本 OCR 成功不应被公式识别失败阻塞。
6. 所有能力必须有清晰开关和降级路径。
7. 后端只依赖稳定 HTTP 契约，不依赖 PaddleOCR SDK 原始返回格式。

---

## 题目 1：PaddleOCR 服务工程骨架

题目类型：python + service + docker

难度：困难

### A 小题：创建独立 OCR 服务目录与 FastAPI 入口

#### Prompt

请在项目中创建 `services/paddle-ocr/` 独立服务，使用 FastAPI 实现 OCR HTTP Server，并提供 `GET /health` 健康检查接口。服务代码应按职责拆分为 API、schema、OCR engine、PDF renderer、quality 等模块，避免把所有逻辑写进一个文件。

#### 难度

中等

#### 验收标准

- 创建 `services/paddle-ocr/app/main.py`。
- 创建 `services/paddle-ocr/app/schemas.py`。
- 创建 `services/paddle-ocr/app/ocr_engine.py`。
- 创建 `services/paddle-ocr/app/pdf_renderer.py`。
- 创建 `services/paddle-ocr/app/quality.py`。
- `GET /health` 返回服务状态、provider、SDK 是否加载、版本信息。
- 服务启动不依赖 Spring Boot 后端。
- 有最小单元测试或 smoke test 覆盖 `/health`。

### B 小题：定义稳定的 OCR 请求与响应 Schema

#### Prompt

请定义 PaddleOCR 服务的请求与响应结构，支持 PDF base64、图片 base64、页范围、语言、是否启用文本 OCR、是否启用公式识别、最大页数限制等参数。响应必须包含 `status`、`provider`、`pages`、`warnings` 和 `elapsedMs`。

#### 难度

偏难

#### 验收标准

- 定义 `OcrPdfRequest`、`OcrImageRequest`、`OcrResponse`。
- 每页响应包含 `pageNumber`、`text`、`blocks`、`formulas`、`confidence`、`warnings`。
- 文本块包含 `text`、`bbox`、`confidence`、`order`。
- 公式块包含 `latex`、`bbox`、`confidence`、`imageRef`。
- 错误响应结构稳定，不直接返回 Python 异常栈。
- 字段命名与后端 `PaddleTranslationOcrService` 兼容。

---

## 题目 2：PDF 与图片输入处理

题目类型：python + document processing

难度：困难

### A 小题：实现 PDF 渲染与页范围控制

#### Prompt

请实现 PDF 输入处理能力，将 PDF base64 解码后按页渲染为图片，并支持 `pageStart`、`pageEnd`、`maxPages` 限制。渲染结果应保留页码、图片尺寸和临时文件路径，供 PaddleOCR SDK 识别使用。

#### 难度

偏难

#### 验收标准

- 支持 PDF base64 输入。
- 支持指定页范围。
- 支持最大页数限制，超出时返回 warning。
- 空 PDF、损坏 PDF、页码越界有明确错误。
- 每页渲染结果包含 `pageNumber`、`width`、`height`。
- 临时文件或内存对象有清理策略。
- 有测试覆盖页范围、空文件、损坏文件。

### B 小题：实现图片 OCR 输入

#### Prompt

请实现 `POST /ocr/image`，支持图片 base64 输入，使用同一套 PaddleOCR engine 输出文本块、bbox、confidence 和质量诊断。

#### 难度

中等

#### 验收标准

- 支持 PNG、JPG、JPEG。
- 非图片输入返回可读错误。
- 图片 OCR 输出结构与 PDF 单页输出一致。
- 图片识别结果包含整页合并文本。
- 有测试覆盖正常图片、空图片和错误格式。

---

## 题目 3：PaddleOCR SDK 文本识别

题目类型：python + PaddleOCR SDK

难度：困难

### A 小题：封装 PaddleOCR 文本识别引擎

#### Prompt

请使用 PaddleOCR 官方 Python SDK 封装 `TextOcrEngine`，负责加载模型、执行文本识别、解析 SDK 返回结果，并转换为项目统一的 block 结构。不要让 API 层直接依赖 PaddleOCR 原始返回格式。

#### 难度

困难

#### 验收标准

- 使用 PaddleOCR SDK 初始化文本识别能力。
- 支持中英文混排，默认语言配置为 `ch` 或适合中英文场景的配置。
- 输出文本块包含 bbox、text、confidence、order。
- 能合并整页文本。
- SDK 初始化失败时服务仍可启动，但 `/health` 应标记不可用。
- 有 mock SDK 测试覆盖结果转换逻辑。

### B 小题：实现文本块排序与整页文本合并

#### Prompt

请实现文本块排序和整页文本合并规则。文本块应按页面阅读顺序排序，整页文本应尽量保留自然换行，避免把不同区域文本混成不可读长句。

#### 难度

偏难

#### 验收标准

- blocks 按从上到下、从左到右排序。
- 支持同一行文本合并。
- 保留合理换行。
- 低置信度文本块可保留，但需要标记 warning 或 quality。
- 空页返回空文本和 warning。
- 有测试覆盖多列文本、普通段落和低置信度块。

---

## 题目 4：公式检测与 LaTeX 识别

题目类型：python + PaddleOCR SDK + formula recognition

难度：很困难

### A 小题：接入公式识别引擎

#### Prompt

请基于 PaddleOCR 生态的公式识别能力封装 `FormulaRecognitionEngine`，支持从页面图片或公式区域图片识别 LaTeX。该能力必须通过配置开关启用，默认可以关闭，避免普通 OCR 被公式模型加载成本拖慢。

#### 难度

很困难

#### 验收标准

- 提供 `enableFormula` 请求参数。
- 提供服务级配置 `PADDLE_OCR_ENABLE_FORMULA`。
- 公式识别失败不影响文本 OCR 成功返回。
- 公式结果包含 `latex`、`bbox`、`confidence`、`imageRef`。
- 未安装或无法加载公式模型时返回 warning。
- 有 mock engine 测试覆盖成功、失败、禁用三种路径。

### B 小题：公式区域与普通文本结果整合

#### Prompt

请将公式识别结果整合进每页响应，同时避免公式区域被普通 OCR 文本重复污染。公式区域至少应以 `formulas` 数组返回，并在整页文本中保留可读占位，例如 `[FORMULA: E = mc^2]`。

#### 难度

困难

#### 验收标准

- 每页响应包含 `formulas` 数组。
- 整页文本中保留公式占位。
- 公式识别失败时保留 `imageRef` 和 warning。
- 公式区域 bbox 与页面坐标一致。
- 后续后端可将公式转成 `DocumentElement.type=formula`。
- 有测试覆盖单公式、多公式和公式失败占位。

---

## 题目 5：质量评分与诊断

题目类型：python + quality

难度：偏难

### A 小题：实现页面级质量诊断

#### Prompt

请实现 OCR 页面质量诊断，根据文本块数量、平均置信度、空文本、低置信度块比例、页面渲染失败等因素生成页面级 `confidence` 和 `warnings`。

#### 难度

偏难

#### 验收标准

- 每页都有 `confidence`。
- 空页返回 `EMPTY_PAGE` warning。
- 平均置信度过低返回 `LOW_CONFIDENCE` warning。
- 文本块极少返回 `SPARSE_TEXT` warning。
- 页面处理失败不导致整个 PDF 任务必然失败。
- 有单元测试覆盖质量评分边界。

### B 小题：实现文档级状态聚合

#### Prompt

请根据每页识别结果聚合文档级 `status`、`warnings`、`pageCount`、`recognizedPageCount` 和 `elapsedMs`。部分页面失败时应返回 `PARTIAL`，而不是直接 `FAILED`。

#### 难度

中等

#### 验收标准

- 全部成功返回 `SUCCEEDED`。
- 部分页失败返回 `PARTIAL`。
- 全部失败返回 `FAILED`。
- 返回总页数和成功识别页数。
- 返回服务耗时。
- 文档级 warnings 去重。

---

## 题目 6：Docker 化与跨机器部署

题目类型：docker + ops

难度：困难

### A 小题：提供 PaddleOCR 服务 Dockerfile

#### Prompt

请为 `services/paddle-ocr` 编写 Dockerfile，镜像内安装 PaddleOCR SDK、FastAPI、PDF 渲染依赖和服务运行依赖。目标是另一台电脑无需预装 PaddleOCR，只需构建或拉取镜像即可启动 OCR 服务。

#### 难度

困难

#### 验收标准

- 提供 `services/paddle-ocr/Dockerfile`。
- 提供 `services/paddle-ocr/requirements.txt`。
- 镜像启动命令运行 FastAPI 服务。
- 服务监听 `0.0.0.0:8090`。
- 容器启动后 `GET /health` 可访问。
- README 说明 CPU/GPU 差异和首次模型下载耗时。

### B 小题：接入 docker-compose.local.yml

#### Prompt

请将 PaddleOCR 服务加入项目本地 Docker 编排，服务名为 `paddle-ocr`，默认端口 `8090:8090`，并在后端环境变量中说明如何指向该服务或远程机器 IP。

#### 难度

中等

#### 验收标准

- `docker-compose.local.yml` 增加 `paddle-ocr` service。
- 支持单独启动：`docker compose -f docker-compose.local.yml up paddle-ocr`。
- 后端可通过 `APP_OCR_PADDLE_BASE_URL=http://paddle-ocr:8090` 在 compose 网络访问。
- 另一台电脑部署时文档说明使用 `http://<remote-ip>:8090`。
- 防火墙、端口、内网访问注意事项写入 README 或 runbook。

---

## 题目 7：后端适配结构化 OCR 输出

题目类型：backend + integration

难度：困难

### A 小题：增强 PaddleTranslationOcrService 响应解析

#### Prompt

请增强 Spring Boot 后端的 `PaddleTranslationOcrService`，解析 PaddleOCR 服务返回的 blocks、confidence、warnings、formulas 等结构化字段，并保持对当前简化 pages/text 响应的兼容。

#### 难度

偏难

#### 验收标准

- 兼容旧响应：`pages[].text`。
- 支持新响应：`pages[].blocks`、`pages[].formulas`、`pages[].warnings`。
- OCR 服务返回 `PARTIAL` 时后端仍尽可能使用成功页。
- OCR 服务返回 `FAILED` 时后端返回 `NEEDS_OCR` 和明确 warning。
- 有后端单元测试覆盖成功、部分成功、失败、公式结果。

### B 小题：将公式和文本块进入知识管线

#### Prompt

请将 PaddleOCR 的文本块和公式结果转换为当前文档知识层可消费的数据。文本块进入普通段落或 OCR block；公式进入 `DocumentElement.type=formula` 或 `DocumentAsset.assetType=formula`，并保留 LaTeX、页码、bbox、confidence。

#### 难度

困难

#### 验收标准

- 文本 OCR 结果能生成 `TranslationDocumentBlockDto`。
- 公式结果能进入 `TranslationDocumentElementDto` 或 `TranslationDocumentAssetDto`。
- LaTeX 保存在 `recognizedText` 或等价字段。
- bbox、confidence、provider 可追踪。
- Agent 上下文能看到公式文本或公式占位。
- 不破坏已有 PDFBox 文本层解析链路。

---

## 题目 8：端到端验证与文档

题目类型：testing + docs

难度：困难

### A 小题：提供 OCR 服务测试样例与自动化验证

#### Prompt

请为 PaddleOCR 服务提供测试样例和自动化验证脚本，覆盖图片 OCR、PDF OCR、页范围、低置信度、公式识别禁用/启用等场景。测试应尽量使用小体积样例，避免把大 PDF 提交进仓库。

#### 难度

偏难

#### 验收标准

- 提供服务级单元测试。
- 提供至少一个 smoke test 脚本。
- 测试不依赖外部网络下载大文件。
- 大模型下载相关测试可标记为手动或集成测试。
- CI 或本地验证命令清晰。
- 测试输出能说明识别页数、文本块数量和 warnings。

### B 小题：编写部署与使用文档

#### Prompt

请编写 PaddleOCR 本地扫描服务部署文档，说明本机部署、另一台电脑部署、后端配置、Docker 启动、健康检查、常见错误和性能注意事项。

#### 难度

中等

#### 验收标准

- 文档说明如何启动服务。
- 文档说明如何让 Spring Boot 后端连接 OCR 服务。
- 文档说明另一台电脑部署时如何配置 IP 和防火墙。
- 文档说明 CPU/GPU、首次模型下载、内存占用和大 PDF 页数限制。
- 文档说明公式识别开关。
- 文档包含完整 curl 示例。

---

## 总体验收标准

完成本题单后，应满足：

1. `services/paddle-ocr` 可以独立启动。
2. OCR 服务使用 PaddleOCR 官方 Python SDK。
3. 支持 PDF OCR 和图片 OCR。
4. 支持中英文混排文本识别。
5. 返回文本块、bbox、confidence、页码。
6. 支持公式 LaTeX 识别能力，且可配置关闭。
7. 公式识别失败不影响普通 OCR 返回。
8. 服务可以 Docker 化部署。
9. 另一台电脑可以启动 OCR 服务并被当前项目后端访问。
10. Spring Boot 后端可以消费结构化 OCR 响应。
11. OCR 结果能进入文档知识管线。
12. 有测试覆盖关键路径。
13. 有部署文档和排障说明。

## 非目标

本题单不要求一次性完成：

- 手写公式高准确率识别。
- 复杂表格完整结构化还原。
- 图片语义理解。
- OCR 人工校正 UI。
- 云端 OCR Provider。
- GPU 自动调度和多实例负载均衡。

这些能力可以作为后续迭代。
