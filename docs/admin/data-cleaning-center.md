# 数据清洗中心词典入库

## 目标

数据清洗中心用于把外部学习资料先纳入管理员可治理的入口。词典链路覆盖上传或登记词典文件来源、探查 MDX/MDD/XLSX 元信息、登记词库元数据、异步全量解析正文、写入正式词典内容表，并给管理员展示导入进度、词条样例和失败样例。

后台“已安装词库”分两层状态：

- `词库元数据已登记`：上传探查成功后写入 `dictionary_library`，管理员可以看到项目已经有哪些词库包。
- `正文内容已入库`：管理员点击“开始正文入库”后，后端创建 `dictionary_import_job` 异步任务，调用 Python worker 分批解析 MDX/MDD/XLSX，再由 Java 写入 `dictionary_entry`、`dictionary_pronunciation`、`dictionary_sense`、`dictionary_example`、`dictionary_phrase`、`dictionary_resource` 等正式内容表。只有这一步成功后，用户侧单词页才能查询到本地牛津词典正文。

## 管理员入口

- 页面：`/admin/data-cleaning`
- 权限：
  - `admin.data_cleaning.read`：查看数据清洗源和任务。
  - `admin.data_cleaning.write`：上传或登记词典源、创建探查任务。
- 角色：
  - `super_admin` 默认拥有读写权限。
  - `content_admin` 默认拥有读写权限，便于内容运营整理词典数据源。

## 数据表

### data_cleaning_source

登记外部数据源。首版用于词典文件来源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `source_uid` | VARCHAR(96) | 业务唯一 ID |
| `source_type` | VARCHAR(32) | 数据源类型，首版固定为 `dictionary` |
| `source_code` | VARCHAR(96) | 管理员定义的来源编码，例如 `oald9` |
| `display_name` | VARCHAR(255) | 展示名称 |
| `license_status` | VARCHAR(32) | 授权状态：`unknown`、`internal_only`、`licensed`、`blocked` |
| `mdx_path` | VARCHAR(1000) | 后端服务器可访问的 MDX 路径 |
| `mdd_path` | VARCHAR(1000) | 后端服务器可访问的 MDD 路径 |
| `examples_path` | VARCHAR(1000) | 后端服务器可访问的例句 XLSX 路径 |
| `cover_image_path` | VARCHAR(1000) | 后端服务器可访问的封面图路径 |
| `metadata_json` | JSON | 探查出的词典元信息 |
| `status` | VARCHAR(32) | `registered`、`probed`、`imported`、`disabled` |
| `created_by` | BIGINT | 创建管理员用户 ID |
| `created_at` / `updated_at` | DATETIME | 创建和更新时间 |

### data_cleaning_job

记录数据清洗任务。首版实现 `dictionary_probe`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `job_uid` | VARCHAR(96) | 任务唯一 ID |
| `source_uid` | VARCHAR(96) | 关联 `data_cleaning_source.source_uid` |
| `job_type` | VARCHAR(64) | `dictionary_probe`，后续可扩展 `dictionary_import` |
| `status` | VARCHAR(32) | `pending`、`running`、`completed`、`failed` |
| `progress_total` | INT | 本次需要探查的文件数 |
| `progress_done` | INT | 已完成文件数 |
| `result_json` | JSON | 探查结果 |
| `error_message` | TEXT | 失败原因 |
| `created_by` | BIGINT | 创建管理员用户 ID |
| `started_at` / `finished_at` | DATETIME | 任务开始和结束时间 |
| `created_at` / `updated_at` | DATETIME | 创建和更新时间 |

### dictionary_library

记录项目中已安装或待导入的词库，作用类似欧路词典的“已安装词库”列表。上传并探查成功后会按 `source_code` 幂等写入或更新。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `dictionary_uid` | VARCHAR(96) | 词库业务唯一 ID |
| `source_uid` | VARCHAR(96) | 关联 `data_cleaning_source.source_uid` |
| `dictionary_code` | VARCHAR(96) | 词库编码，例如 `oald9` |
| `display_name` | VARCHAR(255) | 词库展示名称 |
| `description` | TEXT | 词库描述或 MDX header 摘要 |
| `format` | VARCHAR(64) | 词库格式，当前主要为 `Mdict` |
| `engine_version` / `required_engine_version` | VARCHAR(64) | MDX header 中的引擎版本信息 |
| `encoding` | VARCHAR(64) | 词库正文编码 |
| `entry_count` | INT | MDX 探查出的词条数 |
| `resource_count` | INT | MDD 或资源探查数量，首版为空或 0 |
| `mdx_file_name` / `mdd_file_name` | VARCHAR(255) | 原始文件名 |
| `mdx_file_size` / `mdd_file_size` | BIGINT | 原始文件大小 |
| `examples_count` | INT | XLSX 例句数量 |
| `cover_image_path` | VARCHAR(1000) | 封面图路径 |
| `license_status` | VARCHAR(32) | 授权状态 |
| `storage_type` | VARCHAR(32) | `local`、`object_storage` 等 |
| `enabled` | TINYINT | 是否启用展示 |
| `status` | VARCHAR(32) | `installed`、`importing`、`imported`、`failed`、`disabled` |
| `metadata_json` | JSON | 完整探查结果 |
| `created_at` / `updated_at` | DATETIME | 创建和更新时间 |

### dictionary_import_job

记录词典正文入库任务。当前按钮默认创建全量入库任务，`import_limit=0` 表示不限制词条数。任务先进入 `queued`，随后由后端异步线程执行；HTTP 请求不会等待大文件全量解析完成。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `import_job_uid` | VARCHAR(96) | 入库任务唯一 ID |
| `dictionary_uid` | VARCHAR(96) | 关联 `dictionary_library.dictionary_uid` |
| `source_uid` | VARCHAR(96) | 关联原始数据源 |
| `status` | VARCHAR(32) | `queued`、`running`、`completed`、`completed_with_warnings`、`failed` |
| `import_limit` | INT | 本次最多导入词条数；`0` 表示全量导入 |
| `processed_entries` / `imported_entries` / `failed_entries` | INT | 处理、成功和失败计数 |
| `imported_examples` / `imported_phrases` | INT | 入库例句和短语计数 |
| `result_json` | JSON | worker 结果摘要 |
| `error_message` | TEXT | 失败原因 |
| `created_by` | BIGINT | 创建管理员用户 ID |
| `started_at` / `finished_at` | DATETIME | 任务开始和结束时间 |
| `created_at` / `updated_at` | DATETIME | 创建和更新时间 |

### 正式词典内容表

正式词典内容表用于保存 Python 清洗 worker 的结构化结果：

| 表 | 说明 |
| --- | --- |
| `dictionary_entry` | 词条主表，保存 headword、normalized headword、HTML 原文、清洗文本、质量分和版本信息 |
| `dictionary_pronunciation` | 音标和发音资源 |
| `dictionary_sense` | 释义、词性、等级、用法标签 |
| `dictionary_example` | 中英文例句、来源和质量信息 |
| `dictionary_phrase` | 短语、习语、固定搭配 |
| `dictionary_resource` | MDD 图片、CSS、音频等资源索引 |

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/data-cleaning/overview` | 查看数据源和任务概览 |
| `GET` | `/api/admin/data-cleaning/sources?sourceType=dictionary` | 查看词典数据源 |
| `POST` | `/api/admin/data-cleaning/dictionary-sources` | 创建词典数据源 |
| `POST` | `/api/admin/data-cleaning/dictionary-uploads` | 上传词典文件并自动创建探查任务 |
| `GET` | `/api/admin/data-cleaning/jobs?jobType=dictionary_probe` | 查看词典探查任务 |
| `POST` | `/api/admin/data-cleaning/dictionary-probe-jobs` | 对已登记数据源创建探查任务 |
| `GET` | `/api/admin/data-cleaning/jobs/{jobUid}` | 查看单个任务 |
| `GET` | `/api/admin/dictionaries` | 查看已安装词库列表 |
| `GET` | `/api/admin/dictionaries/{dictionaryUid}` | 查看单个词库信息 |
| `POST` | `/api/admin/dictionaries/{dictionaryUid}/import-jobs?limit=0` | 创建正文入库任务；`limit=0` 为全量入库 |
| `GET` | `/api/admin/dictionaries/{dictionaryUid}/import-jobs` | 查看词库正文入库任务 |
| `GET` | `/api/admin/dictionaries/{dictionaryUid}/entries/samples?limit=10` | 查看已入库词条样例 |
| `GET` | `/api/admin/dictionaries/import-jobs/{importJobUid}/failures` | 查看入库失败样例 |

## 上传能力

管理员可在“词典源”工作台上传词典包。上传接口接收 `multipart/form-data`：

- 表单字段：`sourceCode`、`displayName`、`licenseStatus`。
- 文件字段：`files`，可多文件上传。
- 支持扩展名：`.mdx`、`.mdd`、`.xlsx`、`.jpg`、`.jpeg`、`.png`、`.zip`。
- ZIP 会按白名单解包，只保留上述词典相关文件，并防止路径穿越。
- 文件默认存储到 `storage/data-cleaning/dictionaries/{sourceCode}-{sourceUid}/`。
- 可用环境变量 `DATA_CLEANING_DICTIONARY_UPLOAD_DIR` 覆盖存储目录。
- 上传完成后会写入 `data_cleaning_source`，并自动创建 `dictionary_probe` 任务。
- 探查成功后会写入或更新 `dictionary_library`，管理员端“已安装词库”区域会展示该词库。
- 上传表单中的 MDX/MDD/XLSX/封面路径由上传结果自动回填；服务器本地路径只用于受控内网导入，不作为页面默认硬编码路径。

为支持较大的 MDX/MDD 文件，默认上传限制调整为：

- `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=512MB`
- `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=768MB`
- `SERVER_TOMCAT_MAX_HTTP_FORM_POST_SIZE=512MB`

这些限制仍应根据实际服务器磁盘、网关和内网环境调整。

## 探查内容

MDX/MDD：

- 文件名、文件大小。
- header 长度。
- root 节点。
- `Title`、`Format`、`Encoding`、`Encrypted`、`CreationDate`。
- 从 `Description` 中提取的 `entryCount`。
- 完整 header attributes。

XLSX 例句表：

- 文件名、文件大小。
- sheet 名称。
- 第一张表的行数、列数。
- 第一行字段名。

## Python 清洗核心

词典深度清洗逻辑放在 `python/ai_orchestrator/`，按业务对象分层：

| 层级 | 路径 | 职责 |
| --- | --- | --- |
| schema | `schemas/dictionary_cleaning.py` | 定义原始词条、清洗后词条、释义、例句、短语、清洗汇总等结构化契约 |
| MDX 读取适配 | `tools/dictionary/mdict_reader.py` | 把 MDX reader 输出标准化为 `headword + html` 原始词条；真实 MDX 解析通过可替换 reader 接入 |
| HTML 词条清洗 | `tools/dictionary/entry_parser.py` | 清理 script/style，抽取 headword、词性、音标、释义、例句、短语 |
| 例句表读取 | `tools/dictionary/xlsx_examples_reader.py` | 从 XLSX 第一张表读取英文例句和中文译文；有 `word/headword` 列时按词头合并，没有词头列时先保留为可导入例句数据 |
| workflow | `workflows/dictionary_cleaning/workflow.py` | 编排 MDX 原始词条、HTML 清洗、外部例句合并和质量汇总 |

当前 Python 清洗核心支持：

- 从已解析的 MDX 词条 HTML 中抽取结构化词条。
- 通过 `python.ai_orchestrator.workflows.dictionary_cleaning.cli` 接收 Java worker 请求，输出结构化 JSON。
- 支持 `entryBatchSize` 批量输出：全量导入时 Python 不把所有词条一次性塞进返回 JSON，而是写入多个批次 JSON 文件，并在结果里返回 `entryBatchPaths`。
- 识别牛津 MDX 自定义标签：`h`、`phon`、`pos`、`def`、`chn`、`x`、`unbox`、`idm-g`。
- 从 XLSX 例句表读取中英双语例句。
- 按 headword 合并外部例句到词条首个释义下。
- 汇总词条数、释义数、例句数、短语数和 warning 数。
- 对真实 `.mdx` / `.mdd` 文件使用 `readmdict` 适配入口；Windows 本地需要同一 Python 环境具备 `readmdict` 和 LZO 支持。
- 如果缺少 `readmdict` 或 LZO 依赖，Python worker 会返回明确失败原因；Java 不会把 0 词条导入标记为 `completed`。
- Java worker 会优先使用仓库内 `python/.venv/Scripts/python.exe` 或 `python/.venv/bin/python`，也可以通过 `app.data-cleaning.python.executable` / `DATA_CLEANING_PYTHON_EXECUTABLE` 显式覆盖。

牛津 MDX 标签到结构化字段的首版映射：

| MDX 标签 | 含义 | 清洗字段 |
| --- | --- | --- |
| `h` | 词头 | `DictionaryCleanEntry.word` |
| `phon` | 音标 | `DictionaryCleanEntry.phonetics` |
| `pos` | 词性 | `DictionaryCleanEntry.part_of_speech` |
| `def` | 英文释义容器 | `DictionarySense.definition_en` / `DictionaryPhrase.definition_en` |
| `chn` | 中文释义或例句译文 | `definition_zh` / `example.text_zh` |
| `x` | 英文例句容器 | `DictionaryExample.text_en` |
| `idm-g` | 习语、固定表达组 | `DictionaryCleanEntry.phrases` |
| `unbox` | 用法说明、辨析框等学习说明 | `DictionaryCleanEntry.usage_notes` |

当前 Python 清洗核心暂不负责：

- 直接写数据库；数据库写入由 Java service 负责。
- 调用 DeepSeek 做学习价值提取。
- 审核、发布、权限控制和用户侧词库查询。

## 边界

- 支持浏览器上传和服务器本地路径登记两种方式。
- 探查任务负责文件元信息和词库元数据登记；正文入库任务负责正式内容表。
- 正文入库已经是后端异步任务；任务状态以 `dictionary_import_job` 为准。
- Python 解析、Java 编排和批量写库是分层职责：Python 输出结构化结果，Java 做状态流转、权限、幂等更新和数据库写入。
- 本链路不写入用户侧个人词库。
- 未确认授权前，数据只能用于内部验证，不应发布到用户侧。

## 后续阶段

1. 增加任务暂停、重试、取消和错误样例归档。
2. 增加字段映射审核台：对不同 MDX 模板的标签映射做可视化确认。
3. 增加更细的清洗规则：派生词、同义词、反义词、搭配、学习标签和质量评分。
4. 增加审核发布：按授权状态、质量评分和版本发布到用户侧单词页面。
