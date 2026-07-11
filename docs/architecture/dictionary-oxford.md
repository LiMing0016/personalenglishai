---
title: Oxford 词典集成
status: active
owner: backend
last_updated: 2026-07-11
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/dictionary/
  - web/src/api/dictionary.ts
related_docs:
  - docs/architecture/vocabulary-deposition.md
---

# 单词模块第一版设计方案：Oxford Dictionaries API 接入

## 目标

单词模块第一版以“在线查词”为核心能力，在现有 `/app/vocabulary` 页面接入 Oxford Dictionaries API，完成从用户输入英文单词到展示英文释义、音标、发音和例句的闭环。

第一版的目标是把词典能力稳定接入本地项目，而不是一次性完成完整背单词系统。当前已包含查词、用户收藏和查询次数；复习计划、记忆曲线、中文翻译和写作页选词查询仍在后续阶段。

## 范围

本版本包含：

- 后端代理 Oxford Dictionaries API。
- 前端改造 `/app/vocabulary` 为查词页。
- 展示 Oxford 返回的英文原文内容。
- 支持音标、音频、词性、英文释义、英文例句。
- 支持用户收藏单词、记录查询次数，并在“我的收藏 / 生词本”展示收藏列表。
- 处理未找到、凭据错误、额度耗尽、超时和服务异常等错误状态。
- 通过环境变量配置 Oxford 凭据和 base URL。

本版本不包含：

- 中文释义或 AI 翻译。
- 写作页选词查询。
- 复习队列。
- Redis 或本地缓存。
- Oxford 原始 JSON 透传给前端。

## 总体方案

采用“后端代理 + 前端词典页”的方式。

前端只请求本地接口：

```http
GET /api/dictionary/lookup?word=apple&language=en-gb
```

后端读取 Oxford 配置和凭据，调用 Oxford Dictionaries API：

```http
GET {OXFORD_BASE_URL}/words/{language}/{word}
```

后端将 Oxford 原始响应解析为项目内稳定 DTO 后返回给前端。前端不保存、不读取、不传递 Oxford App ID 或 App Key。

## 设计原则

1. **密钥只在后端**

   Oxford App ID / App Key 只能通过后端环境变量读取，不能写入前端代码、后端源码、测试样例、文档或提交记录。

2. **后端归一化外部响应**

   Oxford 响应层级较深，字段存在缺失可能。后端负责解析和归一化，前端只消费稳定 DTO。

3. **保持第一版小范围**

   本次只完成查词闭环。不引入数据库、Redis、新状态层或额外前端依赖，避免把单词模块第一版扩大成完整学习系统。

4. **不影响写作主链路**

   单词模块独立落在 `/app/vocabulary`，不改写作页、评分、语法检查、润色或 AI 对话链路。

5. **便于后续扩展**

   后续可在当前接口基础上扩展中文辅助解释、收藏、学习计划、缓存或写作页选词查询。

## Oxford API 选择

后端优先使用 Oxford v2 的 Words endpoint：

```http
GET {OXFORD_BASE_URL}/words/{language}/{word}
```

选择 Words endpoint 的原因：

- 适合常规查词场景。
- 可接受 headword 和部分 inflected forms，比 Entries 更适合用户直接输入。
- 能返回 definitions、examples、pronunciations、lexical data。

Sandbox 默认 base URL：

```properties
OXFORD_BASE_URL=https://od-api-sandbox.oxforddictionaries.com/api/v2
```

Oxford Sandbox 有调用次数限制，且英文 Sandbox 通常只支持以 `a` 开头的词。产品页面按正式查词体验设计，不在主界面强调 Sandbox 限制；本地验收时使用 `apple`、`ability` 等 `a` 开头单词。

## 环境配置

后端 `.env` 增加：

```properties
OXFORD_APP_ID=your_oxford_app_id_here
OXFORD_APP_KEY=your_oxford_app_key_here
OXFORD_BASE_URL=https://od-api-sandbox.oxforddictionaries.com/api/v2
OXFORD_LANGUAGE=en-gb
OXFORD_TIMEOUT_MS=8000
```

`.env.example` 只保留占位符，不能包含真实凭据。

如果真实 App ID 或 App Key 已经出现在截图、日志、聊天记录或公开位置，建议在 Oxford 后台轮换密钥。

## 后端设计

### 模块边界

新增独立词典模块，不混入写作、评分、AI 或语法检查链路。

建议结构：

```text
backend/src/main/java/com/personalenglishai/backend/
  controller/DictionaryController.java
  service/dictionary/DictionaryLookupService.java
  service/dictionary/impl/OxfordDictionaryService.java
  dto/dictionary/DictionaryLookupResponse.java
  dto/dictionary/DictionaryEntryDto.java
  dto/dictionary/DictionaryPhoneticDto.java
  config/OxfordDictionaryProperties.java
```

### Controller 职责

`DictionaryController` 负责：

- 接收 `word` 和可选 `language`。
- 做基础参数校验。
- 调用词典 Service。
- 返回 DTO 或统一错误响应。

Controller 不直接调用 Oxford，不解析 Oxford JSON，也不承载业务分支。

### Service 职责

`OxfordDictionaryService` 负责：

- 从配置读取 Oxford base URL、App ID、App Key、默认 language、timeout。
- 调用 Oxford API。
- 映射 Oxford 状态码。
- 解析 Oxford JSON。
- 返回本地稳定 DTO。

日志要求：

- 可记录 word、language、状态码、耗时。
- 不输出 App Key。
- 外部错误响应体最多截断记录，避免日志过大或泄露外部细节。

## API 设计

### Request

```http
GET /api/dictionary/lookup?word=apple&language=en-gb
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `word` | 是 | 要查询的英文单词 |
| `language` | 否 | Oxford language code，默认使用 `OXFORD_LANGUAGE` |

### Response

建议返回：

```json
{
  "word": "apple",
  "language": "en-gb",
  "source": "oxford",
  "phonetics": [
    {
      "text": "/ˈap(ə)l/",
      "audioUrl": "https://..."
    }
  ],
  "entries": [
    {
      "partOfSpeech": "noun",
      "definitions": [
        "the round fruit of a tree of the rose family..."
      ],
      "examples": [
        "an apple tree"
      ]
    }
  ]
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `word` | 查询词 |
| `language` | 实际使用的 Oxford language code |
| `source` | 固定为 `oxford` |
| `phonetics` | 音标和音频 URL |
| `entries` | 按词性归并后的释义与例句 |
| `partOfSpeech` | 词性 |
| `definitions` | 英文释义 |
| `examples` | 英文例句 |

## 错误处理

后端将 Oxford 错误映射为本地统一语义。

| 场景 | 本地 HTTP 状态 | 前端提示建议 | 开发环境细节 |
| --- | --- | --- | --- |
| `word` 为空 | 400 | 请输入要查询的单词 | `INVALID_WORD` |
| Oxford 404 | 404 | 未找到该单词 | `DICTIONARY_NOT_FOUND` |
| Oxford 403 | 502 或 503 | 词典服务配置不可用 | `OXFORD_FORBIDDEN` |
| Oxford 429 | 429 | 词典服务额度已用完，请稍后再试 | `OXFORD_QUOTA_EXCEEDED` |
| Oxford 5xx | 502 | 词典服务暂时不可用 | `OXFORD_UPSTREAM_ERROR` |
| 请求超时 | 504 | 词典服务响应超时 | `OXFORD_TIMEOUT` |
| 响应无法解析 | 502 | 词典服务返回异常 | `OXFORD_RESPONSE_INVALID` |

前端默认展示产品化文案，不展示 Oxford 原始错误体，也不展示密钥相关信息。本地开发环境可以额外显示状态码和错误 code，用于排查 Sandbox 限制、凭据问题或调用额度问题。

## 前端设计

### API 封装

新增：

```text
web/src/api/dictionary.ts
```

接口类型：

```ts
export interface DictionaryLookupResponse {
  word: string
  language: string
  source: 'oxford'
  phonetics: Array<{
    text?: string
    audioUrl?: string
  }>
  entries: Array<{
    partOfSpeech?: string
    definitions: string[]
    examples: string[]
  }>
}

export function lookupDictionary(
  word: string,
  language?: string,
): Promise<DictionaryLookupResponse>
```

要求：

- 使用现有 `web/src/api/http.ts` 中的 `http` 实例。
- 组件不直接拼 axios 请求。
- 前端不保存 Oxford 凭据。

### 页面改造

改造：

```text
web/src/views/VocabularyView.vue
```

第一版页面采用工作台式查词布局，顶部是搜索区，主体是左右两栏结果区。

搜索区包含：

- 搜索框。
- 语言切换，支持 `en-gb` 和 `en-us`。
- 查询按钮。
- 回车查询。

搜索交互要求：

- 查询前清理输入前后空格。
- 查询中禁用查询按钮，避免重复提交。
- 空输入不发请求，展示“请输入要查询的单词”。

主体左侧为主结果区：

- 单词标题。
- 音标。
- 发音按钮或 HTML audio 控件。
- 按词性分组展示释义。
- 每个词性默认展示前 2-3 条释义。
- 每条释义最多展示 1 条例句。
- 如果 Oxford 返回更多内容，提供“展开更多 / 收起”。

主体右侧为信息区：

- Source: Oxford Dictionaries。
- 当前 language，例如 `en-gb` 或 `en-us`。
- 查询状态或最近查询时间。

第一版不展示“加入单词本 / 认识 / 模糊 / 不认识”等学习动作，避免做成未落库的假功能。

页面需要覆盖：

- 加载态。
- 错误态。
- 空态。
- 正常结果态。

页面按正式查词体验设计，不在主 UI 强调 Sandbox 限制。

## 用户流程

1. 用户进入 `/app/vocabulary`。
2. 页面展示查词输入框、语言切换和空状态。
3. 用户输入英文单词，例如 `apple`，并选择 `en-gb` 或 `en-us`。
4. 用户点击查询或按回车，查询按钮进入加载态。
5. 前端调用 `/api/dictionary/lookup`。
6. 后端调用 Oxford API 并归一化响应。
7. 前端展示音标、发音、词性、释义和例句。
8. 如果某个词性有更多释义，用户可以展开更多或收起。
9. 如果查询失败，页面展示可理解的错误提示；开发环境额外展示状态码和错误 code。

## 测试策略

### 后端测试

后端测试不调用真实 Oxford API，也不依赖真实 App Key。

应覆盖：

- Oxford 示例 JSON 正常解析。
- 缺少音频字段时仍能返回释义。
- 缺少例句字段时仍能返回释义。
- 多个 lexicalEntries 正确归并为 entries。
- 空 `word` 参数返回 400。
- Oxford 404、403、429、5xx、timeout 的错误映射。

### 前端测试与构建

前端至少通过类型检查和构建，必要时补充轻量测试覆盖 API 类型和页面状态。

应覆盖：

- 空状态。
- 查询加载态。
- 正常结果展示。
- `en-gb` / `en-us` 切换。
- 展开更多 / 收起。
- 未找到错误。
- 服务异常错误。
- 缺失音频或例句时页面不崩溃。

## 验收标准

功能验收：

- `/app/vocabulary` 可以完成一次完整查词流程。
- 输入 `apple` 可以展示 Oxford 返回的英文词典结果。
- 用户可以在 `en-gb` 和 `en-us` 之间切换查询语言。
- 页面展示单词、音标、音频、词性、英文释义和英文例句。
- 每个词性默认精简展示，并可展开更多释义。
- 查询不存在的词时，页面展示清晰错误提示。
- 开发环境错误态包含状态码和错误 code。
- 清空输入后查询，前端阻止请求或后端返回 400。
- Oxford 返回缺失字段时页面不崩溃。

安全验收：

- 后端不会把 Oxford App Key 输出到日志。
- 前端不会直接请求 Oxford。
- 前端构建产物中不包含 Oxford App ID 或 App Key。
- `.env.example` 只包含占位符。

工程验收：

- 不修改写作、评分、语法、润色、AI 对话主链路。
- 不新增数据库迁移。
- 不新增 Redis key。
- 不引入新的前端依赖或后端 SDK。
- 当前改动只包含单词模块和必要配置文档。

## 验收命令

后端：

```bash
cd backend
./mvnw.cmd test
```

前端：

```bash
cd web
npm run build
```

本地手工验收：

1. 在后端 `.env` 配置本地 Oxford 凭据。
2. 启动后端和前端。
3. 登录进入 `/app/vocabulary`。
4. 查询 `apple`。
5. 确认页面展示单词、音标、词性、英文释义、英文例句。
6. 查询一个不存在的词，确认错误提示清晰。
7. 清空输入后查询，确认请求被阻止或返回 400。

## 后续版本方向

第一版完成后，单词模块可继续扩展：

- 增加中文辅助解释。
- 增加单词收藏和单词本。
- 增加学习状态：认识、模糊、不认识。
- 增加复习队列和记忆曲线。
- 增加 Redis 或数据库缓存，降低 Oxford 调用次数。
- 在写作页支持选中单词快速查词。
- 将查词结果与用户学习画像关联。

## 用户单词状态扩展

2026-05-28 起，单词模块增加用户维度的收藏和查询次数。公共词典内容仍保存在 `dictionary_*` 表中，用户行为不写入 `dictionary_entry`，而是单独落到 `user_dictionary_word_state`。

这样拆分的原因：

- `dictionary_entry` 是全局词典内容，同一个单词对所有用户一致。
- 收藏、查询次数、后续复习状态都属于用户学习行为，需要按 `user_id + normalized_word` 隔离。
- 后续“我的收藏”“最近查询”“复习队列”可以复用同一张用户状态表。

新增表：

```sql
CREATE TABLE IF NOT EXISTS user_dictionary_word_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    word VARCHAR(255) NOT NULL,
    normalized_word VARCHAR(255) NOT NULL,
    language VARCHAR(32) NULL,
    source VARCHAR(32) NULL,
    favorite TINYINT(1) NOT NULL DEFAULT 0,
    lookup_count INT NOT NULL DEFAULT 0,
    first_lookup_at DATETIME NULL,
    last_lookup_at DATETIME NULL,
    favorited_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_dictionary_word (user_id, normalized_word)
);
```

接口变化：

- `GET /api/dictionary/lookup` 在用户已登录时，成功查询后自动 `lookup_count + 1`，并在响应中返回 `favorite`、`lookupCount`。
- `POST /api/dictionary/words/{word}/favorite` 更新当前用户对该单词的收藏状态，返回最新 `favorite` 和 `lookupCount`。
- `GET /api/dictionary/favorites?keyword=&page=&size=` 返回当前用户收藏单词分页列表，列表项包含 `word`、`phonetic`、`partOfSpeech`、`meaning`、`lookupCount`、`favoritedAt`、`lastLookupAt` 等字段。释义预览从本地 `dictionary_*` 表补充，不复制词典正文到用户状态表。

前端展示：

- 单词详情右上角星标按钮直接调用收藏接口。
- 单词详情展示“查询次数”和“收藏状态”。
- “我的收藏 / 生词本”页从 `/api/dictionary/favorites` 拉取真实收藏数据，支持按单词搜索、分页、取消收藏，并可跳转回搜索页查看完整牛津词典详情。
- 如果用户未登录或接口失败，前端保留当前详情内容并显示错误提示。

## 文档与合并评估

本文件是单词模块第一版设计文档，适合随 Oxford 词典接入需求一起提交。实现完成后应检查：

- `.env.example` 是否同步增加 Oxford 占位配置。
- README 或本地启动文档是否需要补充 Oxford 本地配置说明。
- 当前改动是否只包含词典需求相关文件。
- 后端测试和前端构建是否通过。

## 单词沉淀边界

词典内容、用户词典状态与单词卡已拆分为独立资产。单词卡的数据库迁移、生成任务租约、捕获/编辑冲突契约和工作区路由见[单词沉淀架构](./vocabulary-deposition.md)。
