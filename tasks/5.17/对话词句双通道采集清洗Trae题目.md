# 对话词句双通道采集清洗 Trae 题目

## 背景

我们要把学习助手每轮对话中有价值的英语单词、短语、句子和句型沉淀到数据库，为后续“每日学习包”“单词模块”“讲解模块”提供数据底座。

当前方案采用 **双通道候选提取 + 对比评估 + Evidence Builder**：

```text
assistant_message 落库
-> Local Extractor 本地规则提取
-> DeepSeek Async Extractor 便宜模型异步提取
-> learning_raw_candidate 候选池
-> Candidate Comparator 对比 overlap / local_only / deepseek_only / type_conflict
-> Evidence Builder 生成 evidence packet
```

参考文档：

- `docs/agent/数据清洗/对话词句采集清洗方案.md`
- `docs/agent/Agent能力清单.md`
- `docs/agent/路由Agent设计.md`
- `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- `backend/src/main/resources/db/schema.sql`
- `backend/AGENTS.md`

核心原则：

- 聊天主链路不能被采集失败影响。
- Local 和 DeepSeek 两套提取都先进入 raw candidate，不直接进入最终学习项。
- 所有候选必须保留来源 message、conversation、extractor、source excerpt 和信号。
- DeepSeek 提取必须异步，不阻塞用户收到助手回复。
- 第一版只做到候选池、对比结果、evidence packet，不做完整每日学习包 UI。

---

## 题目 1：新增 `learning_extraction_run` 数据表

难度：中等

### A 小题：编写 migration 与 schema

请新增 `learning_extraction_run` 表。

要求：

1. 新增 migration SQL，并同步更新 `backend/src/main/resources/db/schema.sql`。
2. 字段至少包含：
   - `run_uid`
   - `user_id`
   - `conversation_uid`
   - `message_uid`
   - `extractor_type`
   - `status`
   - `model`
   - `input_token_count`
   - `output_token_count`
   - `result_json`
   - `error_message`
   - `created_at`
   - `updated_at`
3. `extractor_type` 支持 `local`、`deepseek`。
4. `status` 支持 `pending`、`processing`、`completed`、`failed`。
5. 增加唯一键：`message_uid + extractor_type`。

### B 小题：补索引与约束

请补齐查询所需索引和基础约束。

要求：

1. 为 `user_id + created_at` 建索引。
2. 为 `message_uid` 建索引。
3. 为 `extractor_type + status` 建索引。
4. `run_uid` 全局唯一。
5. 外键策略按项目现有风格处理；如果不加外键，需要在注释中说明原因。

### 给 Trae 的 Prompt

请新增 `learning_extraction_run` 数据表，用于记录 Local 和 DeepSeek 两套候选提取任务。只做数据库 migration 和 schema 更新，不写业务服务。注意唯一键、索引、状态枚举注释和字段含义。

### 验收标准

- migration 和 `schema.sql` 都包含 `learning_extraction_run`。
- 表字段覆盖本题要求。
- 同一 `message_uid + extractor_type` 有唯一约束。
- 索引满足按用户时间、消息、提取器状态查询。
- 不涉及 Java 业务代码。

---

## 题目 2：新增 `learning_raw_candidate` 数据表

难度：中等

### A 小题：编写 candidate 主表结构

请新增 `learning_raw_candidate` 表。

要求字段至少包含：

- `candidate_uid`
- `user_id`
- `conversation_uid`
- `message_uid`
- `source_role`
- `candidate_type`
- `text`
- `normalized_text`
- `extractor_type`
- `extraction_run_uid`
- `source_excerpt`
- `source_heading`
- `local_signals_json`
- `model_confidence`
- `comparison_status`
- `local_prefilter_score`
- `occurrence_count`
- `first_seen_at`
- `last_seen_at`
- `created_at`
- `updated_at`

### B 小题：补唯一键与查询索引

要求：

1. 唯一键：`user_id + candidate_type + normalized_text + extractor_type`。
2. 索引：
   - `message_uid`
   - `extractor_type`
   - `comparison_status`
   - `last_seen_at`
   - `user_id + candidate_type + last_seen_at`
3. `candidate_type` 注释说明可取：
   - `word`
   - `phrase`
   - `sentence`
   - `sentence_pattern`
4. `comparison_status` 注释说明可取：
   - `overlap`
   - `local_only`
   - `deepseek_only`
   - `type_conflict`

### 给 Trae 的 Prompt

请新增 `learning_raw_candidate` 表，用于保存 Local 和 DeepSeek 提取到的全部原始候选项。该表是候选池，不代表最终学习项。只做数据库 migration 和 schema 更新，注意唯一键、索引、JSON 字段和枚举注释。

### 验收标准

- migration 和 `schema.sql` 都包含 `learning_raw_candidate`。
- 唯一键能区分 local/deepseek 两套来源。
- 支持按 message、extractor、comparisonStatus、用户时间查询。
- 字段注释能说明 candidate 类型和 comparison 状态。

---

## 题目 3：新增 `learning_evidence` 数据表

难度：中等

### A 小题：编写 evidence 表结构

请新增 `learning_evidence` 表。

字段至少包含：

- `evidence_uid`
- `candidate_uid`
- `user_id`
- `evidence_type`
- `text`
- `score`
- `signals_json`
- `model_judgement_json`
- `extractor_sources_json`
- `comparison_status`
- `source_message_ids_json`
- `status`
- `created_at`
- `updated_at`

### B 小题：补索引与状态设计

要求：

1. `status` 支持 `pending`、`consumed`、`ignored`。
2. `evidence_type` 支持：
   - `user_focus`
   - `key_expression`
   - `alternative_expression`
   - `sentence_pattern`
   - `practice_sentence`
3. 索引：
   - `user_id + status + score`
   - `candidate_uid`
   - `comparison_status`
4. 同一个 candidate 默认只能生成一条 active evidence。

### 给 Trae 的 Prompt

请新增 `learning_evidence` 表，用于保存准备喂给消费者模型的高价值证据。只做数据库 migration 和 schema 更新，不实现 Evidence Builder。注意 status、evidence_type、score 排序和 candidate 关联。

### 验收标准

- migration 和 `schema.sql` 都包含 `learning_evidence`。
- 能支持按用户、状态、分数排序查询 pending evidence。
- 字段覆盖 evidence packet 所需信息。
- 不涉及业务服务实现。

---

## 题目 4：实现 Extraction Run Entity / Mapper

难度：中等

### A 小题：新增 Entity 与 Mapper 接口

请新增：

- `LearningExtractionRun`
- `LearningExtractionRunMapper`
- `LearningExtractionRunMapper.xml`

Mapper 至少支持：

- `insert`
- `findByRunUid`
- `findByMessageAndExtractor`
- `updateProcessing`
- `updateCompleted`
- `updateFailed`

### B 小题：补 Mapper 单元测试

要求：

1. 能插入 local run。
2. 能插入 deepseek run。
3. 同一 `messageUid + extractorType` 不重复创建。
4. 能更新 completed，并保存 `result_json` 和 token 数。
5. 能更新 failed，并保存 `error_message`。

### 给 Trae 的 Prompt

请为 `learning_extraction_run` 实现 Entity、MyBatis Mapper、XML 和测试。重点验证同一 message/extractor 不重复创建，以及 run 状态从 pending/processing 到 completed/failed 的更新。

### 验收标准

- Mapper 方法覆盖题目要求。
- 测试覆盖 insert、find、update completed、update failed。
- JSON 字段作为字符串处理。
- `.\mvnw.cmd -q test` 通过新增测试。

---

## 题目 5：实现 Raw Candidate Entity / Mapper

难度：中等偏难

### A 小题：新增 Entity 与 Mapper 接口

请新增：

- `LearningRawCandidate`
- `LearningRawCandidateMapper`
- `LearningRawCandidateMapper.xml`

Mapper 至少支持：

- `insertOrUpdateOccurrence`
- `selectByMessageUid`
- `selectByMessageUidAndExtractor`
- `selectByUserAndDateRange`
- `updateComparisonStatus`

### B 小题：补去重与查询测试

要求：

1. 同一 `userId + candidateType + normalizedText + extractorType` 重复插入时更新 `occurrence_count`。
2. local 和 deepseek 同一文本可以分别存在。
3. 能按 message 查询候选。
4. 能更新 comparison status。
5. 能按用户和日期范围查询。

### 给 Trae 的 Prompt

请为 `learning_raw_candidate` 实现 Entity、Mapper、XML 和测试。重点是候选池去重逻辑：同一 extractor 内去重计数，local/deepseek 跨来源分别保留，后续由 comparator 标记关系。

### 验收标准

- Mapper 方法覆盖题目要求。
- 去重计数逻辑通过测试。
- local/deepseek 同文本能分别入库。
- comparison_status 可更新。
- `.\mvnw.cmd -q test` 通过新增测试。

---

## 题目 6：实现 Evidence Entity / Mapper

难度：中等

### A 小题：新增 Entity 与 Mapper

请新增：

- `LearningEvidence`
- `LearningEvidenceMapper`
- `LearningEvidenceMapper.xml`

Mapper 至少支持：

- `insert`
- `findByEvidenceUid`
- `findActiveByCandidateUid`
- `selectPendingByUserAndDate`
- `updateStatus`

### B 小题：补 evidence 查询测试

要求：

1. 同一 candidate 默认只能有一个 active evidence。
2. 能按用户、日期、status 查询 pending evidence。
3. 查询按 `score DESC, updated_at DESC` 排序。
4. 能更新 `consumed`、`ignored` 状态。

### 给 Trae 的 Prompt

请为 `learning_evidence` 实现 Entity、Mapper、XML 和测试。Evidence 是消费者模型的输入池，不是最终学习包。重点验证 pending 查询、score 排序和状态更新。

### 验收标准

- Mapper 方法覆盖题目要求。
- pending evidence 查询按 score 排序。
- 状态更新测试通过。
- `.\mvnw.cmd -q test` 通过新增测试。

---

## 题目 7：实现 Local Extractor 基础英文提取

难度：困难

### A 小题：实现 word / phrase 提取

请新增 `LocalCandidateExtractor`。

要求：

1. `word`：
   - 英文 token 长度 3 到 30。
   - 排除停用词、URL、邮箱、文件名、纯缩写噪声。
2. `phrase`：
   - 连续 2 到 8 个英文词。
   - 排除纯停用词组合。
   - 支持 normalized text。
3. 每个候选返回：
   - `candidateType`
   - `text`
   - `normalizedText`
   - `sourceExcerpt`
   - `localSignals`
   - `localPrefilterScore`

### B 小题：实现 sentence / sentence_pattern 提取

要求：

1. `sentence`：
   - 至少 5 个英文词。
   - 有句号、问号、感叹号，或是填空练习句。
2. `sentence_pattern`：
   - 包含 `+` 连接符。
   - 或包含 `sb`、`sth`、`...`、`名词`、`动词原形`、`从句` 等模板标记。
3. 超长句、格式残缺句要降权或跳过。

### 给 Trae 的 Prompt

请实现 Local Candidate Extractor 的基础英文提取能力，覆盖 word、phrase、sentence、sentence_pattern。该服务只返回内存结果，不接数据库、不接 AssistantConversationService。注意停用词、normalizedText、sourceExcerpt 和 localPrefilterScore。

### 验收标准

- 能从截图类似内容中提取 `global perceptions of`。
- 能提取 `help + 动词原形 + perceptions of + 名词`。
- 能提取填空练习句。
- 不提取常见停用词和明显噪声。
- 单元测试覆盖四类候选。

---

## 题目 8：增强 Local Extractor 的 Markdown 教学结构信号

难度：困难

### A 小题：识别 Markdown 标题和教学区域

请增强 `LocalCandidateExtractor`，识别教学结构。

要求识别这些区域：

- `重点表达`
- `更自然的同类表达`
- `你可以这样记`
- `练习一下`
- `句子结构`
- `常见误用`

命中后写入 signals：

- `under_heading:key_expression`
- `under_heading:alternative_expression`
- `under_heading:memory_note`
- `under_heading:practice`

### B 小题：识别 Markdown 格式信号

要求识别：

- 项目符号：`markdown_bullet`
- 加粗文本：`markdown_bold`
- 代码块或模板块：`code_block`

并据此调整 `localPrefilterScore`。

### 给 Trae 的 Prompt

请增强 Local Candidate Extractor 的 Markdown 教学结构识别。它需要知道候选项来自哪个教学区域，并记录 signals。不要简单全文正则乱抓，要尽量保留 sourceHeading 和 sourceExcerpt，方便后续 reason/evidence 引用。

### 验收标准

- “更自然的同类表达”下的短语带 `under_heading:alternative_expression`。
- “你可以这样记”下的句型带 `under_heading:memory_note`。
- “练习一下”下的填空句带 `under_heading:practice`。
- 项目符号、加粗、代码块信号能被识别。
- 单测覆盖 Markdown 标题和格式信号。

---

## 题目 9：接入 AssistantConversationService 的 Local 提取触发

难度：困难

### A 小题：普通回复链路接入

请在 `sendAgentMessage` / 普通 assistant 回复保存后触发 local extraction。

要求：

1. 保存 assistant message 后创建 local extraction run。
2. 执行 `LocalCandidateExtractor`。
3. 写入 `learning_raw_candidate(extractor_type=local)`。
4. run 成功标记 completed。
5. 失败标记 failed，但不影响聊天返回。

### B 小题：流式回复链路接入

请在 `writeAgentMessageStream` 保存 assistant message 后触发 local extraction。

要求：

1. 不改变 SSE 输出。
2. 不阻塞流式响应结束。
3. 同一 message 不重复创建 local run。
4. 日志记录 candidate 数量和耗时。

### 给 Trae 的 Prompt

请把 Local Candidate Extractor 接入 AssistantConversationService 的 assistant message 保存后触发点。普通回复和流式回复都要覆盖。采集失败不能影响聊天成功返回，也不能改变 SSE 协议。

### 验收标准

- 普通助手回复保存后创建 local run 和 raw candidates。
- 流式助手回复保存后也能触发 local 提取。
- 提取失败时聊天仍正常。
- 同一 message 重复触发不会重复创建 local run。
- Service 测试覆盖普通和流式触发。

---

## 题目 10：实现 DeepSeek 配置与安全 Client

难度：中等偏难

### A 小题：新增配置属性

请新增 DeepSeek 提取配置。

建议配置：

```properties
LEARNING_EXTRACT_DEEPSEEK_ENABLED=false
LEARNING_EXTRACT_DEEPSEEK_BASE_URL=
LEARNING_EXTRACT_DEEPSEEK_API_KEY=
LEARNING_EXTRACT_DEEPSEEK_MODEL=
LEARNING_EXTRACT_DEEPSEEK_TIMEOUT_MS=15000
```

要求：

1. API key 不进入日志。
2. timeout 有默认值。
3. enabled=false 时 client 不发请求。
4. 配置缺失时错误清晰。

### B 小题：实现 DeepSeek Client 契约

请新增 client，支持发送提取请求并解析响应。

要求：

1. 请求 prompt 要求模型只返回 JSON。
2. 响应解析为 extraction result DTO。
3. 非法 JSON 抛出明确异常。
4. 单条 message 最多允许 20 个 item。
5. 测试使用 mock HTTP，不调用真实网络。

### 给 Trae 的 Prompt

请实现 DeepSeek 提取配置和安全 client。注意 API key 脱敏、timeout、enabled 开关、严格 JSON 解析和 mock 测试。不要接入 AssistantConversationService，不要写异步任务。

### 验收标准

- 配置类能读取 enabled/baseUrl/model/timeout。
- API key 不会出现在异常消息和日志输出。
- mock 合法 JSON 可解析。
- mock 非法 JSON 抛出明确异常。
- enabled=false 时不会发请求。

---

## 题目 11：实现 DeepSeek Async Extraction Run 调度

难度：困难

### A 小题：创建 pending deepseek run

请在 assistant message 保存后创建 DeepSeek extraction run。

要求：

1. 仅当 `LEARNING_EXTRACT_DEEPSEEK_ENABLED=true` 时创建。
2. `extractor_type=deepseek`。
3. 初始状态 `pending`。
4. 同一 `messageUid + deepseek` 不重复创建。
5. 不阻塞聊天主链路。

### B 小题：异步执行与状态流转

请实现异步执行服务。

要求：

1. pending -> processing -> completed。
2. 失败时 -> failed。
3. 保存 error_message。
4. 保存 input/output token 统计，取不到时允许为空。
5. 失败不影响 local run 和聊天消息。

### 给 Trae 的 Prompt

请实现 DeepSeek Async Extraction Run 调度。assistant message 保存后只创建 pending run，然后由异步服务执行。注意幂等、状态流转、失败隔离和日志。

### 验收标准

- enabled=false 不创建或不执行 deepseek run，行为有测试覆盖。
- enabled=true 创建 pending run。
- 异步成功后 run completed。
- 异步失败后 run failed。
- 聊天返回不等待 DeepSeek 完成。

---

## 题目 12：实现 DeepSeek 结果入 raw candidate

难度：困难

### A 小题：解析 DeepSeek items

请把 DeepSeek 返回 items 转为 `LearningRawCandidate`。

要求：

1. 支持 `word`、`phrase`、`sentence`、`sentence_pattern`。
2. 缺失 `normalizedText` 时后端补齐。
3. `confidence` 写入 `model_confidence`。
4. `reason` 保存到 signals 或 result_json 中。
5. `sourceExcerpt` 不能为空，缺失时用 text 兜底。

### B 小题：写入 raw candidate 并补测试

要求：

1. 写入 `extractor_type=deepseek`。
2. 关联 `extraction_run_uid`。
3. 同来源重复候选去重计数。
4. DeepSeek 返回超过 20 个 item 时截断。
5. 非法 item 跳过，不让整个 run 失败，除非 JSON 整体非法。

### 给 Trae 的 Prompt

请实现 DeepSeek 提取结果到 raw candidate 的落库逻辑。注意 item 级别容错、normalizedText 兜底、confidence、sourceExcerpt 和去重计数。不要实现 comparator。

### 验收标准

- 合法 DeepSeek items 能写入 raw candidate。
- 超过 20 个 item 会截断。
- 缺 normalizedText 能自动生成。
- 单个非法 item 被跳过。
- 同一 deepseek 候选重复时 occurrence_count 增加。

---

## 题目 13：实现 Candidate Comparator

难度：困难

### A 小题：实现 exact match 与 type conflict

请新增 `CandidateComparisonService`。

要求：

1. 同一 message 下读取 local 和 deepseek candidates。
2. `normalized_text` 完全一致且类型一致 -> `overlap`。
3. `normalized_text` 完全一致但类型不同 -> `type_conflict`。
4. 只有 local -> `local_only`。
5. 只有 deepseek -> `deepseek_only`。

### B 小题：实现 soft match 与统计

要求：

1. 短语包含关系支持 soft match。
2. sentence/source excerpt 高重合支持 soft match。
3. 输出统计：
   - `overlapCount`
   - `localOnlyCount`
   - `deepseekOnlyCount`
   - `typeConflictCount`
4. Comparator 可重复执行且结果稳定。

### 给 Trae 的 Prompt

请实现 Candidate Comparator，比较同一 message 下 local 与 deepseek 两套候选，更新 comparison_status，并返回对比统计。重点覆盖 exact match、soft match、type conflict 和幂等执行。

### 验收标准

- `global perceptions of` 双方命中时标记 overlap。
- local 独有候选标记 local_only。
- deepseek 独有候选标记 deepseek_only。
- 同 normalizedText 不同类型标记 type_conflict。
- 重复执行 comparator 结果稳定。
- 单测覆盖四种状态和统计结果。

---

## 题目 14：实现 Evidence Builder 基础升级与 Packet

难度：困难

### A 小题：候选升级为 Evidence

请实现 Evidence Builder 基础规则。

要求：

1. `comparison_status=overlap` 优先升级 evidence。
2. `deepseek_only` 可以升级，但分数低于 overlap。
3. `local_only` 只有本地信号强时升级。
4. `type_conflict` 默认不升级。
5. 同一 candidate 不重复创建 active evidence。

### B 小题：构建 Evidence Packet

请实现 evidence packet 服务。

要求：

1. 按用户和日期读取 pending evidence。
2. 默认最多 80 条。
3. 输出：
   - `date`
   - `userId`
   - `studyStage`
   - `dailyLimit`
   - `evidence[]`
4. 每个 evidence 包含：
   - `evidenceId`
   - `type`
   - `text`
   - `score`
   - `comparisonStatus`
   - `extractorSources`
   - `signals`
   - `sourceMessageIds`
   - `sourceExcerpts`
5. 不输出完整 assistant message content。

### 给 Trae 的 Prompt

请实现 Evidence Builder 的基础升级规则和 Evidence Packet 构建服务。第一版不接 embedding 和 Learning Value Judge，只基于 comparator 结果和本地信号生成 pending evidence。Packet 是后续消费者模型输入，不能包含整段聊天全文。

### 验收标准

- overlap 候选能升级为 evidence。
- deepseek_only 候选能以较低分升级。
- type_conflict 默认不升级。
- Evidence Packet 数量限制生效。
- Packet 不包含完整聊天原文。
- 单测覆盖升级规则和 packet 契约。

---

## 题目 15：实现查询 API、调试统计与回归 fixture

难度：困难

### A 小题：新增只读查询与统计 API

请新增 API：

```http
GET /api/learning/candidates
GET /api/learning/evidence
GET /api/learning/evidence/packet?date=YYYY-MM-DD
GET /api/learning/extraction-runs
GET /api/learning/extraction-comparison/summary
```

要求：

1. 只能查询当前登录用户的数据。
2. candidates 支持 `candidateType`、`extractorType`、`comparisonStatus`、`dateFrom`、`dateTo`。
3. evidence 支持 `evidenceType`、`status`、`minScore`。
4. extraction runs 返回状态、耗时、token、错误。
5. comparison summary 返回四类对比统计。
6. 不返回 API key、完整 prompt、过长原文。

### B 小题：新增回归 fixture 和端到端服务测试

请新增测试样例，覆盖：

1. “更自然的同类表达”短语列表。
2. “你可以这样记”句型。
3. “练习一下”填空句。
4. 普通寒暄或错误提示。

端到端服务测试要求：

1. Local extractor 生成候选。
2. DeepSeek mock 生成候选。
3. Comparator 生成四种 comparison_status。
4. Evidence Builder 生成 pending evidence。
5. Evidence Packet 契约正确。

### 给 Trae 的 Prompt

请实现学习采集的只读 API、调试统计接口和回归 fixture。API 必须按当前登录用户隔离数据，支持分页和筛选。测试不依赖真实 DeepSeek 网络调用，用 mock 输出覆盖 overlap、local_only、deepseek_only、type_conflict。

### 验收标准

- 当前用户只能查询自己的 candidates/evidence/runs。
- comparison summary 统计正确。
- Evidence Packet API 返回结构稳定。
- fixture 覆盖短语、句子、句型、填空练习、低价值回复。
- 端到端服务测试覆盖 Local + DeepSeek mock + Comparator + Evidence Builder。
- `.\mvnw.cmd -q test` 通过新增测试。

---

## 建议实施顺序

1. 题目 1-3：数据库 migration。
2. 题目 4-6：Entity / Mapper。
3. 题目 7-8：Local Extractor。
4. 题目 9：聊天后 local 触发。
5. 题目 10-12：DeepSeek 异步提取。
6. 题目 13：Candidate Comparator。
7. 题目 14：Evidence Builder。
8. 题目 15：API、调试统计、回归 fixture。

建议每 1-2 道题一个小 PR。不要把数据库、异步模型、对比逻辑、API 和测试全部混到一个 PR。
