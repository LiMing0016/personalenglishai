# 管理员词库管理与 Python 词典正文入库 Trae 题目

## 背景

当前项目已经有管理员“数据清洗中心”，支持词典源登记、词典包上传、MDX/MDD/XLSX 轻量探查。接下来需要把这个能力升级为真正可治理的“词库管理 + 小批量正文入库”闭环。

本轮目标：

- 上传或重新探查词典后，项目中要形成“已安装词库”记录。
- 管理员能在后台看到当前项目已经有哪些词库，类似欧路词典的词库管理列表。
- 管理员点击“开始正文入库”后，后端创建入库任务并调用 Python worker。
- Python worker 解析 MDX 词典正文、MDD 资源索引，清洗成结构化词条。
- Java 后端批量写入正式词典内容表。
- 后台能展示入库进度、样例词条、失败样例，方便先验证解析质量。

核心原则：

- 不绕过管理员鉴权。
- 不改生产登录逻辑。
- 不把词典文件提交到 Git。
- 未确认授权前，词典内容只用于内部验证，不发布到用户侧。
- Java 负责管理员鉴权、任务状态、数据库写入和前端 API。
- Python 负责 MDX/MDD 深度解析、HTML 清洗、字段结构化。
- 第一版先做小批量同步入库，后续再升级异步队列。

参考文件：

- `backend/src/main/java/com/personalenglishai/backend/controller/admin/AdminDataCleaningController.java`
- `backend/src/main/java/com/personalenglishai/backend/controller/admin/AdminDictionaryLibraryController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/admin/AdminDataCleaningService.java`
- `backend/src/main/java/com/personalenglishai/backend/mapper/admin/AdminDataCleaningMapper.java`
- `backend/src/main/resources/mapper/AdminDataCleaningMapper.xml`
- `backend/src/main/resources/db/schema.sql`
- `python/ai_orchestrator/tools/dictionary/mdict_reader.py`
- `python/ai_orchestrator/tools/dictionary/entry_parser.py`
- `python/ai_orchestrator/workflows/dictionary_cleaning/workflow.py`
- `web/src/api/admin.ts`
- `web/src/pages/admin/AdminDataCleaningPage.vue`
- `docs/admin/data-cleaning-center.md`

---

## 题目 1：实现已安装词库落库与管理接口

难度：困难

### A 小题：新增词库管理表与内容表

请在数据库中新增词库管理和正式词典内容表。

至少包含：

- `dictionary_library`：项目已安装词库表。
- `dictionary_entry`：词条主表。
- `dictionary_pronunciation`：音标/发音索引表。
- `dictionary_sense`：释义表。
- `dictionary_example`：例句表。
- `dictionary_phrase`：短语/习语表。
- `dictionary_resource`：MDD 图片、CSS、音频等资源索引表。
- `dictionary_import_job`：词典正文入库任务表。

要求：

1. `dictionary_library.dictionary_code` 唯一。
2. `dictionary_entry` 支持按 `normalized_headword` 查询。
3. 词条、释义、例句、短语、资源都要有业务 UID。
4. `dictionary_import_job` 需要记录处理数、成功数、失败数、例句数、短语数和结果 JSON。
5. schema 和 migration 文件都要同步更新。

### B 小题：探查成功后写入已安装词库

扩展现有词典探查链路。

要求：

1. 上传并探查成功后，自动写入或更新 `dictionary_library`。
2. `dictionary_library` 的展示名称、格式、编码、词条数、MDD 文件名、文件大小、授权状态等字段来自探查结果。
3. 重复探查同一个 `sourceCode` 时应幂等更新，不重复创建词库。
4. 不影响原有 `data_cleaning_source` 和 `data_cleaning_job` 行为。

### 给 Trae 的 Prompt

请为管理员数据清洗中心新增“已安装词库”落库能力。上传或探查词典成功后，把词典元信息写入 `dictionary_library`，并预留正式词典内容表。要求保持现有数据源和探查任务链路兼容，不改生产登录逻辑，不绕过管理员鉴权。

### 验收标准

- 数据库存在 `dictionary_library` 和正式词典内容表。
- 上传或探查成功后，`dictionary_library` 有对应词库记录。
- 同一个 `sourceCode` 重复探查不会产生重复词库。
- 后端测试覆盖词库落库和幂等更新。
- 原有词典源列表和探查任务仍可正常工作。

---

## 题目 2：实现后台词库管理 API 与页面展示

难度：中等偏难

### A 小题：新增管理员词库 API

新增管理员词库管理接口。

建议接口：

```http
GET /api/admin/dictionaries
GET /api/admin/dictionaries/{dictionaryUid}
GET /api/admin/dictionaries/{dictionaryUid}/import-jobs
POST /api/admin/dictionaries/{dictionaryUid}/import-jobs?limit=100
```

要求：

1. 读接口需要 `admin.data_cleaning.read`。
2. 创建入库任务需要 `admin.data_cleaning.write`。
3. 返回结构中包含词库名称、编码、状态、词条数、MDD 资源状态、文件大小、更新时间。
4. 入库任务响应包含 `processedEntries`、`importedEntries`、`failedEntries`、`importedExamples`、`importedPhrases`。

### B 小题：后台展示已安装词库

在 `/admin/data-cleaning` 页面中新增“已安装词库”区域。

页面要求：

1. 类似欧路词典“已安装词库”列表，展示项目中已经落库的词典。
2. 每个词库展示：名称、编码、格式、编码、词条数、MDX/MDD 文件、授权状态、存储类型、状态。
3. 每个词库提供“开始正文入库”按钮。
4. 点击按钮后显示任务创建或完成提示。
5. 页面刷新后仍能看到已安装词库。

### 给 Trae 的 Prompt

请为管理员端实现词库管理 API 和“已安装词库”页面展示。管理员需要像在欧路词典中一样看到项目内已有词库，并能对某个词库发起正文入库任务。页面要符合后台数据治理产品规范，不要做简陋表格堆叠。

### 验收标准

- `GET /api/admin/dictionaries` 可返回已安装词库。
- 无管理员读权限不能访问词库列表。
- 页面显示已安装词库信息。
- 点击“开始正文入库”会调用创建入库任务接口。
- 前端测试覆盖 API 方法和页面文案。

---

## 题目 3：实现 Python MDX/MDD 小批量清洗 worker

难度：困难

### A 小题：实现 Python CLI worker

在 `python/ai_orchestrator/workflows/dictionary_cleaning/` 下新增 CLI 入口。

建议调用方式：

```bash
python -m python.ai_orchestrator.workflows.dictionary_cleaning.cli --input request.json --output result.json
```

输入 JSON 至少包含：

- `dictionaryUid`
- `sourceUid`
- `sourceCode`
- `displayName`
- `limit`
- `mdxPath`
- `mddPath`
- `examplesPath`

输出 JSON 至少包含：

- `status`
- `summary`
- `entries`
- `resources`
- `warnings`

### B 小题：解析 MDX 正文与 MDD 资源索引

复用现有 Python 清洗模块。

要求：

1. MDX 解析输出 `headword + html` 原始词条。
2. HTML 清洗输出结构化字段：词头、音标、词性、释义、例句、短语、用法说明。
3. 支持牛津自定义标签：`h`、`phon`、`pos`、`def`、`chn`、`x`、`unbox`、`idm-g`。
4. MDD 不要求第一版复制二进制文件，但要输出资源索引：resource key、类型、文件名、大小、存储路径。
5. `limit` 控制小批量处理数量，默认先验证 100 条。
6. 解析异常不能让整个 worker 静默成功，必须进入 warnings 或失败结果。

### 给 Trae 的 Prompt

请为词典清洗实现 Python worker CLI。Java 后端会传入词典源路径和处理上限，Python worker 负责解析 MDX 正文、清洗 HTML 字段、索引 MDD 资源，并输出结构化 JSON。第一版先支持小批量处理，便于后台验证清洗质量。

### 验收标准

- 可以通过 `python -m ...cli --input --output` 运行 worker。
- 输出 JSON 包含 `entries`、`resources`、`summary`、`warnings`。
- 单测覆盖 MDX raw entry 标准化、MDD resource 标准化、牛津标签解析、CLI 输出。
- 没安装 `readmdict/lzo` 时能返回清晰错误。
- 不直接在 Python 中写数据库。

---

## 题目 4：实现 Java 调用 Python worker 并写入正式词典表

难度：困难

### A 小题：后端调用 Python worker

实现 Java 侧 worker 调用适配。

要求：

1. 创建 `dictionary_import_job` 后，将任务状态置为 `running`。
2. 生成 worker request JSON。
3. 调用 Python CLI。
4. 读取 worker result JSON。
5. worker 失败时，任务状态置为 `failed`，错误写入 `error_message` 和 `result_json.failures`。
6. Python 可执行路径、项目根目录、临时目录必须可配置。

### B 小题：批量写入正式词典内容表

根据 worker 输出写入正式词典表。

要求：

1. 写入 `dictionary_entry`。
2. 写入 `dictionary_pronunciation`。
3. 写入 `dictionary_sense`。
4. 写入 `dictionary_example`。
5. 写入 `dictionary_phrase`。
6. 写入 `dictionary_resource`。
7. 任务结束后更新处理数、成功数、失败数、例句数、短语数。
8. `dictionary_library.status` 随任务变为 `importing`、`imported` 或恢复 `installed`。
9. 重复导入同一词条时应按 `dictionary_uid + source_entry_id` 幂等更新。

### 给 Trae 的 Prompt

请为管理员词库正文入库实现 Java 调用 Python worker 的链路。管理员点击“开始正文入库”后，后端创建入库任务、调用 Python CLI、读取结构化结果并写入正式词典内容表。第一版同步执行小批量导入，重点是能看到真实解析质量和失败样例。

### 验收标准

- 创建入库任务后会调用 Python worker。
- worker 成功时任务状态为 `completed`。
- worker 失败时任务状态为 `failed`，失败原因可查。
- 词条、释义、例句、短语、资源能写入对应表。
- 入库任务统计字段准确。
- 后端测试覆盖成功入库和失败入库。

---

## 题目 5：后台展示入库进度、样例词条和失败样例

难度：中等偏难

### A 小题：新增样例查询接口

新增管理员查询接口。

建议接口：

```http
GET /api/admin/dictionaries/{dictionaryUid}/entries/samples?limit=10
GET /api/admin/dictionaries/import-jobs/{importJobUid}/failures
```

要求：

1. 需要管理员读权限。
2. 样例词条返回 headword、词性、首条释义、例句数量、短语数量、质量分。
3. 失败样例从 `dictionary_import_job.result_json.failures` 中读取。
4. 不返回完整大字段或完整词典正文，避免后台页面过重。

### B 小题：前端展示入库效果

扩展“已安装词库”卡片。

要求：

1. 展示最近一次入库任务状态。
2. 展示处理数、成功数、失败数、例句数、短语数。
3. 展示 3-5 条样例词条。
4. 展示最多 3 条失败样例。
5. 点击“开始正文入库”后自动刷新词库、任务和样例。
6. 文案要让管理员知道这是“小批量质量验证”，不是全量发布。

### 给 Trae 的 Prompt

请在管理员数据清洗中心展示词典正文入库效果。词库卡片需要显示最近入库任务、处理统计、样例词条和失败样例，让内容管理员能判断 MDX/MDD 解析质量。不要在页面展示完整词典正文。

### 验收标准

- 页面能看到最近入库任务进度。
- 页面能看到样例词条。
- 页面能看到失败样例。
- 入库完成后页面刷新数据。
- 前端测试覆盖样例和失败展示。
- `npm run build` 通过。

---

## 题目 6：补充文档、配置与整体回归

难度：中等

### A 小题：补充文档和配置说明

更新 `docs/admin/data-cleaning-center.md`。

至少说明：

1. 数据清洗中心的定位。
2. 已安装词库的落库流程。
3. Python worker 的职责边界。
4. Java 后端的职责边界。
5. 正式词典内容表的数据字典。
6. 小批量同步入库与后续异步队列的区别。
7. `readmdict/lzo` 等 Python 依赖要求。
8. 未授权词典只能内部验证，不能发布用户侧。

### B 小题：补充回归测试

请补充并运行回归测试。

建议覆盖：

- 后端 service 测试：词库落库、创建入库任务、成功入库、失败入库。
- schema 测试：新增词典表存在。
- Python 测试：MDX/MDD 解析、HTML 清洗、CLI 输出。
- 前端测试：已安装词库、入库按钮、样例展示、失败展示。

### 给 Trae 的 Prompt

请为管理员词库管理和 Python 词典正文入库补充文档与回归测试。文档要说明数据表、接口、worker 边界、依赖和授权边界；测试要覆盖后端、Python 和前端关键路径。

### 验收标准

- `docs/admin/data-cleaning-center.md` 已更新。
- 后端定向测试通过。
- Python 词典清洗测试通过。
- 前端相关测试通过。
- `npm run build` 通过。
- 说明全量测试中是否存在与本需求无关的既有失败。

