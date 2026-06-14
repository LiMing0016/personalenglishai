# 第三方 PDF 解析 Provider 与可替换 OCR 架构题目

## 背景

AI 精读工作台需要把用户上传的 PDF 转换成稳定、可阅读、可翻译、可提问、可沉淀学习资产的结构化材料。

当前系统已经具备基础 PDFBox / Tesseract 解析入口，但 PDF 是最复杂的一手资料来源，扫描件、双栏论文、试卷、表格、页眉页脚都会影响阅读体验。

本题目标不是从零自研 OCR 模型，而是先建立一套可替换的 PDF / OCR Provider 架构：

```text
PDF 上传
→ DocumentParseOrchestrator
→ DocumentParseProvider
→ UnifiedDocumentNode
→ LearningChunk
→ AI 精读工作台
```

第一阶段可以优先接第三方高质量解析服务，后续再逐步切换到 PaddleOCR、Marker/Surya、自研 PDF 结构化解析或云 OCR 兜底。

---

## 题目 1：设计并实现可替换 DocumentParseProvider 架构

### A 小题：抽象统一 Provider 接口

#### 题目 Prompt（完成）

请在后端翻译模块中设计统一的文档解析 Provider 接口，使 PDF 解析能力可以在本地解析、第三方服务、自部署 OCR 和未来自研解析之间切换。

要求：

1. 新增统一接口，例如：

```java
public interface DocumentParseProvider {
    boolean supports(DocumentParseRequest request);
    DocumentParseProviderType providerType();
    DocumentParseResult parse(DocumentParseRequest request);
}
```

2. `DocumentParseRequest` 至少包含：
   - 原始文件名
   - contentType
   - 文件 bytes
   - 文件类型
   - 解析模式：standard / high_quality
   - 用户选择的 Agent 模式：immersive / exam / foreign / technical

3. `DocumentParseResult` 不直接暴露第三方原始结构，而是输出系统自己的统一结构。
4. 现有 PDFBox / Tesseract 解析能力要封装为一个本地 Provider，例如 `LocalPdfBoxDocumentParseProvider`。
5. Provider 选择逻辑不能写死在 Controller 里，应由 Orchestrator 负责。

#### 难度

中等

#### 验收标准

- 后端存在清晰的 `DocumentParseProvider` 抽象。
- 现有本地 PDF 解析能力被迁移或包装为 Provider。
- Controller 不直接依赖具体解析实现。
- 新增单元测试覆盖：
  - standard 模式优先选择本地 provider。
  - high_quality 模式可以选择第三方 provider。
  - provider 不支持当前文件时能进入 fallback。
- 不破坏现有 `/api/translation/documents/import` 基础上传流程。

---

### B 小题：实现 DocumentParseOrchestrator 与 fallback 机制

#### 题目 Prompt（完成）

请实现 `DocumentParseOrchestrator`，统一负责选择 Provider、执行解析、处理失败和 fallback。

要求：

1. Orchestrator 根据配置和请求选择解析 Provider。
2. 支持配置项：

```yaml
app:
  document-parse:
    default-provider: local-pdfbox
    high-quality-provider: third-party-layout
    fallback-provider: local-pdfbox
    max-pages: 80
```

3. 解析失败时：
   - high_quality provider 失败后尝试 fallback provider。
   - fallback 也失败时返回明确错误。
   - 错误信息不能泄露第三方密钥或内部堆栈。

4. 解析结果中记录：
   - provider
   - fallbackUsed
   - parseMode
   - pageCount
   - warnings
   - elapsedMs

5. 支持后续异步任务迁移，但本题可以先保持同步返回。

#### 难度

中等偏高

#### 验收标准

- 有 `DocumentParseOrchestrator` service。
- Provider 选择逻辑可测试、可配置、可替换。
- 第三方 provider 抛错时可以 fallback 到本地 provider。
- 返回结果包含 provider 元信息和 warnings。
- 有测试覆盖 provider 选择、fallback 和失败路径。

---

## 题目 2：定义 UnifiedDocumentNode 与 LearningChunk

### A 小题：升级文档结构输出模型

#### 题目 Prompt（完成）

请定义 AI 精读工作台统一文档结构模型，使不同来源的 PDF/OCR/Word/Markdown 解析结果都能转换成同一种结构。

要求新增或扩展结构：

```json
{
  "id": "node_001",
  "parentId": null,
  "type": "paragraph",
  "text": "AI is reshaping work faster than we think.",
  "order": 1,
  "depth": 0,
  "pageNumber": 1,
  "bbox": [120, 240, 520, 310],
  "confidence": 0.98,
  "sectionPath": ["Agent Trace Quality Platform", "Product Positioning"],
  "provider": "third-party-layout"
}
```

节点类型至少支持：

- heading
- paragraph
- list
- list_item
- table
- quote
- code
- image
- question
- option

设计要求：

1. 前端展示原文时优先基于 `DocumentNode`，不要只依赖扁平 `P1/P2/P3`。
2. `sectionPath` 用于右侧 Agent 展示当前上下文。
3. PDF/OCR 必须保留 pageNumber。
4. 如果 Provider 能提供坐标，必须保留 bbox。
5. 如果 Provider 能提供置信度，必须保留 confidence。

#### 难度

中等

#### 验收标准

- 后端响应中包含统一文档节点结构。
- PDF 本地解析结果能转换为 `DocumentNode`。
- 旧版 `TranslationDocumentBlockDto` 能兼容或迁移到新模型。
- 前端能读取 `sectionPath`、`pageNumber`、`type`。
- 有测试覆盖 heading、paragraph、list/table 至少三类节点。

---

### B 小题：生成 AI 使用的 LearningChunk

#### 题目 Prompt（完成）

请在后端或前端数据层生成 `LearningChunk`，用于 AI 翻译、Agent 问答和学习资产沉淀。

注意：`LearningChunk` 是 AI 处理单元，不等于页面展示单元。页面展示必须尽量保留用户原始文档结构。

建议结构：

```json
{
  "id": "chunk_001",
  "nodeIds": ["node_001", "node_002"],
  "text": "AI is reshaping work faster than we think...",
  "sectionPath": ["Product Positioning"],
  "pageRange": [1, 2],
  "chunkType": "section",
  "tokenEstimate": 860
}
```

要求：

1. 短段落可以一个 node 对应一个 chunk。
2. 长段落可以内部切 chunk，但 UI 仍显示完整段落。
3. 列表、表格可以作为整体 chunk。
4. chunk 必须保留 `nodeIds`，方便 Agent 回跳原文。
5. 后续真实 AI 翻译时，应使用 chunk 而不是随意拼接全文。

#### 难度

中等偏高

#### 验收标准

- 能从 `DocumentNode` 生成 `LearningChunk`。
- 长文本 chunk 不会破坏 UI 展示结构。
- Agent 当前上下文能拿到 chunk 对应的 sectionPath 和 nodeIds。
- 有测试覆盖：
  - 普通段落 chunk。
  - 长段落 chunk。
  - 列表或表格 chunk。

---

## 题目 3：接入第三方高质量 PDF 解析 Provider

### A 小题：实现 ThirdPartyLayoutDocumentParseProvider 骨架

#### 题目 Prompt（完成）

请实现第三方高质量 PDF 解析 Provider 的后端骨架，用于后续接入 Marker/Surya、Datalab API、Google Document AI、AWS Textract 或 Azure Document Intelligence。

要求：

1. 新增 `ThirdPartyLayoutDocumentParseProvider`。
2. 通过配置启用或禁用：

```yaml
app:
  document-parse:
    third-party:
      enabled: false
      provider: datalab
      endpoint: ""
      api-key: ""
      timeout-seconds: 120
```

3. API key 只能从环境变量或受控配置读取，不能硬编码。
4. 第三方返回结构必须转换成自己的 `DocumentNode`，不能直接透传给前端。
5. 第三方原始响应可以暂存为 debug 字段，但默认不返回前端。

#### 难度

中等

#### 验收标准

- 存在第三方 Provider 骨架。
- 未配置 API key 时不会影响本地解析。
- 配置禁用时不会选择第三方 Provider。
- 第三方 Provider 返回模拟结果时能进入统一节点转换。
- 有测试覆盖启用/禁用、缺少配置、模拟成功响应。

---

### B 小题：实现 Provider 原始响应适配层

#### 题目 Prompt（完成）

请实现第三方解析响应到 `DocumentNode` 的适配层。

要求：

1. 先支持一种通用中间格式：

```json
{
  "pages": [
    {
      "pageNumber": 1,
      "blocks": [
        {
          "type": "heading",
          "text": "Document Title",
          "bbox": [80, 90, 500, 130],
          "confidence": 0.99
        }
      ]
    }
  ]
}
```

2. 不同第三方返回的数据先转换为该中间格式，再转换为 `DocumentNode`。
3. 适配层必须处理：
   - 缺失 bbox
   - 缺失 confidence
   - 空文本
   - 未知 block type
   - 页码缺失

4. 未知 block type 默认降级为 paragraph，并写入 warning。

#### 难度

中等偏高

#### 验收标准

- 第三方模拟响应可以转换为 `DocumentNode`。
- 缺失字段不会导致解析失败。
- 未知类型会降级并产生 warning。
- 保留 pageNumber、bbox、confidence。
- 有测试覆盖正常响应、缺字段响应、未知类型响应。

---

## 题目 4：前端解析模式与解析质量展示

### A 小题：新建翻译弹窗支持解析模式

#### 题目 Prompt（完成）

请在翻译 Hub 的“新建翻译”弹窗中增加 PDF 解析模式选择。

要求：

1. 用户上传 PDF 时显示解析模式：
   - 标准解析
   - 高质量解析

2. 上传 TXT / MD / DOCX 时不强制展示复杂 OCR 配置。
3. 默认选择标准解析。
4. 高质量解析文案强调：
   - 适合扫描 PDF、试卷、论文、双栏、表格。
   - 可能消耗更多时间或额度。
5. 创建任务时把 parseMode 传给后端。

#### 难度

中等

#### 验收标准

- PDF 上传时可选择标准/高质量解析。
- 非 PDF 上传不展示无关 OCR 配置。
- 请求后端时带上 parseMode。
- 页面不因为配置项过多破坏沉浸感。
- 有前端数据层或组件测试覆盖 parseMode。

---

### B 小题：工作台展示解析质量与 Provider 信息

#### 题目 Prompt（完成）

请在 AI 精读工作台中展示必要但不打扰的解析状态信息。

要求：

1. 顶部状态显示：
   - 解析完成 / 需要校正 / OCR 低置信度 / fallback 已使用
2. 当前段落或节点可以显示：
   - pageNumber
   - confidence 低于阈值时的提示
   - 当前 provider 来源
3. 右侧 Agent 当前上下文显示 sectionPath。
4. 不要把 provider 技术细节作为主视觉，避免干扰阅读。
5. 后续可支持“查看原 PDF 对照”和“重新高质量解析”。

#### 难度

中等

#### 验收标准

- 工作台能显示当前文档解析状态。
- 当前节点能显示页码和低置信度提示。
- Agent 当前上下文包含 sectionPath。
- fallback 使用时有轻量提示。
- UI 保持 IDE 风格，不做大面积说明文字。

---

## 题目 5：成本、权限与安全控制

### A 小题：记录解析成本与调用审计

#### 题目 Prompt

请为第三方 PDF 解析增加调用审计和成本记录。

要求记录：

- documentId
- userId
- provider
- parseMode
- pageCount
- elapsedMs
- estimatedCost
- fallbackUsed
- status
- errorCode
- createdAt

成本可先粗略估算，例如：

```text
estimatedCost = pageCount * providerPagePrice
```

#### 难度

中等

#### 验收标准

- 每次第三方解析调用都有记录。
- 失败调用也有记录。
- 不记录 API key。
- 能按 provider 和 parseMode 查询调用数量。
- 有测试覆盖成功、失败、fallback 三种情况。

---

### B 小题：限制单次 PDF 解析页数和高质量解析额度

#### 题目 Prompt

请为 PDF 解析增加基础限流和额度控制。

要求：

1. 限制单次上传最大页数。
2. 高质量解析可以配置用户每日/每月额度。
3. 超过额度时返回明确提示。
4. 标准解析不应被高质量额度限制影响。
5. 后续可接会员权益系统。

#### 难度

中等偏高

#### 验收标准

- 超页数 PDF 被拒绝或要求用户拆分。
- 高质量解析超额度时不会调用第三方服务。
- 标准解析仍可使用。
- 错误提示面向用户，不暴露内部配置。
- 有测试覆盖页数限制、额度不足、额度充足。

---

## 推荐实施顺序

```text
1. Provider 接口和 Orchestrator
2. LocalPdfBoxProvider 迁移
3. UnifiedDocumentNode 模型
4. LearningChunk 生成
5. ThirdPartyLayoutProvider 骨架
6. 第三方响应适配层
7. 前端 parseMode 和解析质量展示
8. 成本与额度控制
```

## 总体验收标准

- 后端解析能力不再绑定具体 OCR 实现。
- PDF 可以按标准解析 / 高质量解析走不同 Provider。
- 第三方 Provider 失败时可以 fallback。
- 所有 Provider 输出统一 `DocumentNode`。
- AI 翻译和 Agent 问答使用 `LearningChunk`，不破坏页面原始结构。
- 前端能展示 PDF 原文结构、页码、上下文路径和解析质量提示。
- 后续可以替换为 PaddleOCR、自研解析或云 OCR，而不需要重写工作台。