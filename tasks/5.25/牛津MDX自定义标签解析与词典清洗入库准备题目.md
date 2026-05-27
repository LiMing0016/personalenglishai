# 牛津 MDX 自定义标签解析与词典清洗入库准备 Trae 题目

## 背景

当前管理员数据清洗中心已经具备词典源上传、路径登记和轻量探查能力。Python 侧也已经开始具备词典清洗核心，可以通过 `readmdict + python-lzo` 读取 `.mdx` 正文，并解析 `.mdd` 资源列表。

实际抽样发现，牛津高阶英汉双解词典第 9 版的 `.mdx` 正文不是普通网页结构，而是大量自定义标签，例如：

- `h`：词头
- `phon`：音标
- `pos`：词性
- `def`：英文释义
- `chn`：中文释义或例句译文
- `x`：英文例句
- `idm-g`：习语或固定表达组
- `unbox`：用法说明、辨析框、学习提示

本轮目标不是直接把词典发布到用户侧，而是先把“牛津 MDX 自定义标签 -> 结构化词典数据”的清洗能力做扎实，为后续 staging 入库、管理员审核和正式词库发布做准备。

## 总体要求

1. 解析 `.mdx` 中牛津自定义标签，输出稳定结构化结果。
2. 保留已有普通 HTML/class 解析兼容能力。
3. 支持从 `.mdd` 查看资源 key 和资源类型，但第一版不要求全量导出图片。
4. 清洗结果先作为 Python 中间产物，不直接发布到正式用户词库。
5. 不绕过管理员鉴权，不修改生产登录逻辑。
6. 不把 `.mdx/.mdd/.xlsx` 原始文件提交到 Git。
7. 所有新增解析能力必须有单元测试或最小真实样本验证。

## 非目标

- 不做正式词库发布。
- 不做用户单词页接入。
- 不做 DeepSeek 学习价值提取。
- 不做 MDD 图片资源全量落库。
- 不做大型异步任务队列。
- 不把 MDX 二进制原文件直接塞进业务词条表。

---

## 题目 1：完善 Python 词典清洗 Schema 与解析契约

### 发给 Trae 的 Prompt（完成）

请完善 Python 侧词典清洗 schema 和解析契约，让牛津 MDX 词条可以被稳定表示为结构化数据。重点支持词头、词性、音标、义项、英文释义、中文释义、例句、习语/固定表达和用法说明。不要直接写数据库，也不要改管理员鉴权。

### 小题 A：扩展词典清洗 Schema

#### 题目 Prompt

请在 `python/ai_orchestrator/schemas/dictionary_cleaning.py` 中完善词典清洗结构。

字段至少支持：

1. `DictionaryRawEntry`
   - `headword`
   - `html`
   - `source_entry_id`
2. `DictionaryCleanEntry`
   - `word`
   - `part_of_speech`
   - `phonetics`
   - `senses`
   - `phrases`
   - `usage_notes`
   - `clean_text`
   - `source_entry_id`
3. `DictionarySense`
   - `definition_en`
   - `definition_zh`
   - `examples`
4. `DictionaryExample`
   - `headword`
   - `text_en`
   - `text_zh`
   - `source`
5. `DictionaryPhrase`
   - `text`
   - `definition_en`
   - `definition_zh`
   - `examples`

要求：

1. 使用 Pydantic schema。
2. 列表字段必须使用 `default_factory=list`。
3. 字段命名要能直接映射未来数据库 staging 表。
4. 不传自由 dict 作为主要结果。

#### 题目难度

简单

#### 验收标准

- schema 可以被 Python 单元测试直接 import。
- `DictionaryCleanEntry` 能承载词条、释义、例句、短语和用法说明。
- 旧的词典清洗测试不因 schema 变更失败。
- 不引入新的 agent runtime 或无关依赖。

### 小题 B：定义牛津标签到结构化字段的映射文档

#### 题目 Prompt

请在 `docs/admin/data-cleaning-center.md` 或合适的数据清洗文档中补充牛津 MDX 标签映射说明。

至少说明：

| MDX 标签 | 含义 | 目标字段 |
| --- | --- | --- |
| `h` | 词头 | `word` |
| `phon` | 音标 | `phonetics` |
| `pos` | 词性 | `part_of_speech` |
| `def` | 英文释义容器 | `definition_en` |
| `chn` | 中文释义或译文 | `definition_zh` / `text_zh` |
| `x` | 英文例句容器 | `text_en` |
| `idm-g` | 习语/固定表达组 | `phrases` |
| `unbox` | 用法说明/辨析框 | `usage_notes` |

要求：

1. 明确这只是第一版解析范围。
2. 说明 `.mdd` 第一版只做资源识别，不做全量图片发布。
3. 说明 Python 清洗结果后续需要进入 staging 表。

#### 题目难度

简单

#### 验收标准

- 文档包含牛津 MDX 标签映射表。
- 文档明确当前不直接发布正式词库。
- 文档明确后续需要 staging 入库和审核发布。

---

## 题目 2：实现牛津 MDX 自定义标签解析

### 发给 Trae 的 Prompt（完成）

请完善 `python/ai_orchestrator/tools/dictionary/entry_parser.py`，支持解析牛津高阶 MDX 的自定义标签。解析器要从 `<h>`、`<phon>`、`<pos>`、`<def>`、`<chn>`、`<x>`、`<idm-g>`、`<unbox>` 中抽取结构化字段，同时保留已有普通 HTML class 解析能力。

### 小题 A：解析词头、词性、音标和义项

#### 题目 Prompt

请实现牛津 MDX 基础字段解析。

要求：

1. `<h>` 抽取为 `word`。
2. `<pos>` 抽取为 `part_of_speech`。
3. `<phon>` 抽取为 `phonetics`，去重。
4. `<sn-g>` 或等价义项块中读取义项。
5. `<def>` 中英文部分抽取为 `definition_en`。
6. `<def>` 内嵌 `<chn>` 抽取为 `definition_zh`。
7. `script/style` 内容必须清除。
8. 标签匹配不能把 `<phon-blk>` 错当成 `<phon>`。

#### 题目难度

中等

#### 验收标准

- 给定牛津 MDX HTML 片段时，能抽出正确 `word`、`part_of_speech`、`phonetics`。
- `def` 内的中文不会混进英文释义。
- 英文释义不会包含 `chnsep`、音频图标或脚本内容。
- 旧的普通 HTML class 测试仍通过。

### 小题 B：解析例句、习语和用法说明

#### 题目 Prompt（完成）

请继续完善牛津 MDX 的例句、习语和用法说明解析。

要求：

1. `<x>` 抽取为英文例句 `text_en`。
2. `<x>` 内嵌 `<chn>` 抽取为例句中文 `text_zh`。
3. `<idm-g>` 抽取为 `DictionaryPhrase`。
4. `<idm>` 抽取为短语文本。
5. `<idm-g>` 内的 `<def>/<chn>` 抽取为短语释义。
6. `<unbox>` 抽取为 `usage_notes`。
7. 例句和短语需要保留来源 `source="entry_html"` 或等价标记。

#### 题目难度

中等偏难

#### 验收标准

- 能从牛津 HTML 中抽出英文例句和中文译文。
- 能从 `idm-g` 中抽出习语文本、英文释义和中文释义。
- 能从 `unbox` 中抽出可读用法说明。
- 不会把例句中文混进英文例句。
- 单元测试覆盖 `x`、`idm-g`、`unbox`。

---

## 题目 3：接入真实 MDX/MDD 样本的最小验证

### 发给 Trae 的 Prompt

请为牛津 MDX/MDD 解析补充最小真实样本验证。目标是确认当前 Python 环境可以读取 MDX 正文和 MDD 资源列表，并能用解析器抽出一个真实词条的核心结构。不要把真实词典文件提交到 Git。

### 小题 A：完善 MDX reader 适配与依赖说明

#### 题目 Prompt（完成）

请完善 `python/ai_orchestrator/tools/dictionary/mdict_reader.py` 的使用说明和错误提示。

要求：

1. 使用 `readmdict` 读取 `.mdx`。
2. 支持可选 `limit`，便于抽样。
3. 对 bytes key/value 做编码解码。
4. 如果缺少 `readmdict` 或 `lzo`，错误信息要明确说明需要安装依赖。
5. 文档中说明 Windows 下 `.mdx` 可能需要 `python_lzo-*-cp312-cp312-win_amd64.whl`。

#### 题目难度

中等

#### 验收标准

- `iter_mdx_raw_entries(mdx_path, limit=5)` 可以返回 `DictionaryRawEntry`。
- 缺少依赖时有明确错误提示，而不是一大段不可读 traceback。
- 不把真实 `.mdx` 文件纳入测试仓库。

### 小题 B：增加真实样本 smoke 脚本或 notebook 说明

#### 题目 Prompt

请新增一个安全的 smoke 验证方式，用于本地验证真实牛津 MDX/MDD 文件。

建议位置：

- `docs/admin/data-cleaning-center.md`
- 或 `docs/runbooks/dictionary-mdx-smoke-test.md`

说明内容至少包括：

1. 如何安装 `readmdict`。
2. 如何安装 Windows `python-lzo` wheel。
3. 如何读取 `.mdx` 前 5 条。
4. 如何读取 `.mdd` 前 10 个资源 key。
5. 如何解析真实词条，例如 `abandon`。
6. 如何确认输出中包含词头、词性、义项、例句和短语。

#### 题目难度

中等

#### 验收标准

- 文档能指导开发者在本地跑通 MDX/MDD 抽样。
- 文档明确真实词典文件不提交 Git。
- 文档明确 MDD 第一版只确认资源 key，不做资源发布。

---

## 题目 4：为后续 staging 入库准备数据结构

### 发给 Trae 的 Prompt

请基于 Python 清洗结果设计后续词典 staging 入库的数据结构。第一版只做设计文档和字段映射，不要求完成数据库写入。目标是让后续 Java 任务可以把 Python 清洗结果写入可审核的 staging 表，再由管理员发布到正式词库。

### 小题 A：设计词典 staging 表字段

#### 题目 Prompt

请设计词典清洗 staging 表结构文档。

建议表：

1. `dictionary_import_run`
2. `dictionary_entry_staging`
3. `dictionary_sense_staging`
4. `dictionary_example_staging`
5. `dictionary_phrase_staging`
6. `dictionary_usage_note_staging`
7. `dictionary_resource_staging`

字段至少考虑：

- `source_uid`
- `run_uid`
- `entry_uid`
- `headword`
- `normalized_headword`
- `part_of_speech`
- `phonetics_json`
- `definition_en`
- `definition_zh`
- `example_en`
- `example_zh`
- `phrase_text`
- `usage_note_text`
- `raw_html`
- `source_entry_id`
- `quality_score`
- `review_status`
- `created_at`
- `updated_at`

#### 题目难度

中等偏难

#### 验收标准

- 文档包含 staging 表设计。
- 表结构能承载 Python 解析出的所有核心字段。
- 明确 staging 表不是正式用户词库表。
- 明确审核通过后才进入正式词库。

### 小题 B：定义 Python 输出到 Java 入库的 JSON 契约

#### 题目 Prompt

请定义 Python 清洗结果传给 Java 入库任务的 JSON 契约。

要求：

1. 顶层包含 `sourceCode`、`sourceUid`、`runUid`、`summary`、`entries`、`warnings`。
2. `entries` 使用 `DictionaryCleanEntry` 等价结构。
3. 每个 entry 可以包含多个 senses、examples、phrases、usageNotes。
4. 保留 `sourceEntryId` 和 `rawHtml` 或 `cleanText` 追溯字段。
5. 错误条目进入 `warnings`，不能让整个导入直接失败。

#### 题目难度

中等

#### 验收标准

- 文档包含一份示例 JSON。
- Java 后续可以按该 JSON 写 staging 表。
- Python workflow 输出结构和文档一致。
- 异常条目不会导致整个批次不可审计。

---

## 总体验收命令

建议完成后运行：

```powershell
cd F:\personalenglishai
.\python\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_dictionary_cleaning
```

如果修改了前端或后端，再补充：

```powershell
cd backend
.\mvnw.cmd -q test

cd web
npm run build

cd docs
npm run build
```

## 总体验收标准

- Python 能解析牛津 MDX 自定义标签。
- `abandon` 这类真实词条能抽出词头、词性、音标、义项、例句和习语。
- MDD 能读取资源 key 和资源大小。
- 解析结果是结构化 schema，不是大段自由文本。
- 文档说明 MDX/MDD 本地验证方式和 staging 入库路径。
- 真实词典文件不进入 Git。
- 当前功能仍停留在清洗与 staging 准备阶段，不直接发布到用户词库。
