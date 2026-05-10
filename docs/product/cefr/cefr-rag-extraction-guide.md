# CEFR Companion Volume 2020 RAG 抽取指南

## 文档定位

本文件说明如何从 `CEFR Companion Volume_eng_2020.pdf` 中提炼适合 PEAI 使用的 RAG 知识。内容采用中文归纳、转述和产品化结构，不是官方原文逐字翻译。

目标是让 RAG 为学习助手回答这些问题：

- 用户当前写作为什么接近 B1 或 B2？
- 从 B1 到 B2 应该训练什么？
- 某个语法问题对 CEFR 等级意味着什么？
- 某个词汇或表达是否适合当前用户主动使用？
- 学习计划如何围绕当前等级和目标等级展开？

## 版权和使用边界

不要把整本 PDF 逐字翻译后放入 RAG。推荐做法是：

- 保存项目化中文摘要。
- 保存结构化知识条目。
- 保存页码和章节引用。
- 不保存大段连续原文。
- 不在产品中声称 PEAI 提供官方 CEFR 认证。

## RAG 条目格式

建议每条知识都按以下结构保存：

```json
{
  "id": "cefr-writing-b2-overview",
  "source": "CEFR Companion Volume 2020",
  "source_ref": "Chapter 3, Written production; Appendix 4",
  "skill": "writing",
  "level": "B2",
  "topic": "overall_writing_ability",
  "content_zh": "中文归纳内容",
  "product_use": ["writing_score_explanation", "learning_plan"],
  "risk_note": "这是项目化转述，不是官方译文。"
}
```

## Collection 设计

### `cefr_level_guidance`

用途：

- 用户能力图。
- 用户自评解释。
- 学习规划开头的能力定位。

建议字段：

| 字段 | 说明 |
| --- | --- |
| `level` | A1-C2 |
| `band` | Basic / Independent / Proficient |
| `summary_zh` | 等级中文说明 |
| `can_do_focus` | 该等级最核心的 can-do 能力 |
| `next_level_gap` | 到下一级的主要差距 |

### `cefr_skill_guidance`

用途：

- Listening / Reading / Speaking / Writing 能力图。

建议字段：

| 字段 | 说明 |
| --- | --- |
| `skill` | listening / reading / spoken_interaction / spoken_production / writing |
| `level` | A1-C2 |
| `descriptor_zh` | 中文转述 |
| `self_assessment_prompt_zh` | 用户自评问题 |
| `evidence_examples` | 可作为证据的学习行为 |

### `cefr_writing_guidance`

用途：

- 写作评分 CEFR 解释层。
- 写作雷达解释。
- 写作学习计划。

建议字段：

| 字段 | 说明 |
| --- | --- |
| `level` | A1-C2 |
| `writing_type` | overall / creative / report_essay / interaction |
| `ability_summary_zh` | 写作能力中文归纳 |
| `typical_strengths` | 常见优势 |
| `typical_limits` | 常见限制 |
| `upgrade_focus` | 到下一级训练重点 |

### `cefr_language_competence_guidance`

用途：

- 词汇、语法、连贯、表达准确度解释。

建议字段：

| 字段 | 说明 |
| --- | --- |
| `competence` | vocabulary_range / grammatical_accuracy / coherence_cohesion / thematic_development |
| `level` | A1-C2 |
| `content_zh` | 中文归纳 |
| `mapped_peai_dimension` | 对应 PEAI 雷达维度 |

## A1-C2 等级知识条目

### A1

```json
{
  "id": "cefr-level-a1-summary",
  "collection": "cefr_level_guidance",
  "level": "A1",
  "content_zh": "A1 表示学习者可以处理非常基础、具体、熟悉的信息。表达通常依赖简单词组和短句，适合个人信息、地点、家庭、日常需求等场景。",
  "product_use": ["ability_profile", "self_assessment", "learning_plan"]
}
```

### A2

```json
{
  "id": "cefr-level-a2-summary",
  "collection": "cefr_level_guidance",
  "level": "A2",
  "content_zh": "A2 表示学习者可以完成简单日常任务，理解和表达高频生活信息。能写短消息、简单说明和基础个人经历，但复杂表达和长文本组织仍明显受限。",
  "product_use": ["ability_profile", "self_assessment", "learning_plan"]
}
```

### B1

```json
{
  "id": "cefr-level-b1-summary",
  "collection": "cefr_level_guidance",
  "level": "B1",
  "content_zh": "B1 是从基础表达进入独立表达的阶段。学习者能围绕熟悉主题表达经历、观点、原因和计划，能写连贯短文，但观点展开、语言准确性和表达成熟度还不稳定。",
  "product_use": ["ability_profile", "writing_score_explanation", "learning_plan"]
}
```

### B2

```json
{
  "id": "cefr-level-b2-summary",
  "collection": "cefr_level_guidance",
  "level": "B2",
  "content_zh": "B2 表示学习者可以较清楚、详细地处理较广泛主题，能支持或反对观点，能写文章或报告并组织理由。这个阶段的重点是论证清楚、连接自然、语法较稳、表达较正式。",
  "product_use": ["ability_profile", "writing_score_explanation", "learning_plan"]
}
```

### C1

```json
{
  "id": "cefr-level-c1-summary",
  "collection": "cefr_level_guidance",
  "level": "C1",
  "content_zh": "C1 表示学习者能处理复杂主题，表达清楚、结构良好，并能根据读者、场景和目的调整风格。写作上通常能展开较长论述，突出重点，并使用较成熟的连接和组织方式。",
  "product_use": ["ability_profile", "writing_score_explanation", "learning_plan"]
}
```

### C2

```json
{
  "id": "cefr-level-c2-summary",
  "collection": "cefr_level_guidance",
  "level": "C2",
  "content_zh": "C2 表示高度熟练的语言使用能力。它不等于母语者水平，而是强调精准、流畅、灵活、风格适切，并能处理复杂信息、细微含义和高要求文本。",
  "product_use": ["ability_profile", "advanced_learning_plan"]
}
```

## 用户能力图知识条目

### 听力 Listening

```json
{
  "id": "cefr-skill-listening-profile",
  "collection": "cefr_skill_guidance",
  "skill": "listening",
  "content_zh": "听力能力应按能否理解熟悉话题、清晰标准语速、讲座或复杂论证、自然语速和隐含关系来分层。第一版 PEAI 可先用用户自评和听力练习结果估计。",
  "product_use": ["ability_radar"]
}
```

### 阅读 Reading

```json
{
  "id": "cefr-skill-reading-profile",
  "collection": "cefr_skill_guidance",
  "skill": "reading",
  "content_zh": "阅读能力应按能否理解简单通知、日常短文、熟悉主题文章、较复杂论证、长难文本和隐含意义来分层。PEAI 可用于推荐阅读材料难度和解释阅读目标。",
  "product_use": ["ability_radar", "resource_recommendation"]
}
```

### 口语互动 Spoken Interaction

```json
{
  "id": "cefr-skill-spoken-interaction-profile",
  "collection": "cefr_skill_guidance",
  "skill": "spoken_interaction",
  "content_zh": "口语互动关注对话中能否提问、回应、澄清、维持交流、表达观点和处理不同正式程度。它和独白式口语表达不同，应作为能力图单独维度。",
  "product_use": ["ability_radar", "speaking_plan"]
}
```

### 口语表达 Spoken Production

```json
{
  "id": "cefr-skill-spoken-production-profile",
  "collection": "cefr_skill_guidance",
  "skill": "spoken_production",
  "content_zh": "口语表达关注学习者能否独立描述经历、说明信息、陈述观点、做演讲或进行有结构的长段表达。它适合后续演讲、复述、口语作文等训练。",
  "product_use": ["ability_radar", "speaking_plan"]
}
```

### 写作 Writing

```json
{
  "id": "cefr-skill-writing-profile",
  "collection": "cefr_skill_guidance",
  "skill": "writing",
  "content_zh": "写作能力关注学习者能否从简单短句发展到连贯短文，再到清楚详细的文章、报告和复杂论述。PEAI 当前最适合先落地写作 CEFR 估计。",
  "product_use": ["ability_radar", "writing_score_explanation", "learning_plan"]
}
```

## 写作 RAG 知识条目

### Writing A1

```json
{
  "id": "cefr-writing-a1",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "A1",
  "content_zh": "A1 写作通常只能写孤立短句或非常简单的个人信息。文本长度短，结构弱，表达依赖基础词汇和记忆句型。",
  "typical_limits": ["只能写非常简单的信息", "长一点的文本容易不连贯", "语法和拼写控制有限"],
  "upgrade_focus": ["基本句型", "个人信息表达", "and / then 等基础连接"]
}
```

### Writing A2

```json
{
  "id": "cefr-writing-a2",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "A2",
  "content_zh": "A2 写作可以用简单句和常见连接词写短消息、短说明或简单经历。文本能表达有限信息，但连贯性和准确性仍容易出问题。",
  "typical_limits": ["连接方式简单", "基本错误较多", "信息展开有限"],
  "upgrade_focus": ["简单经历描述", "because / but 等连接", "基础时态准确性"]
}
```

### Writing B1

```json
{
  "id": "cefr-writing-b1",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "B1",
  "content_zh": "B1 写作可以围绕熟悉或个人兴趣主题写出直接、连贯的文本。能描述经历、表达印象和简单观点，但论证深度、结构控制和语言成熟度有限。",
  "typical_strengths": ["能写连贯短文", "能表达熟悉主题", "能给出简单理由"],
  "typical_limits": ["观点展开不足", "段落组织偏线性", "复杂句和正式表达不稳定"],
  "upgrade_focus": ["段落展开", "观点支撑", "复杂句准确性", "更自然的连接表达"]
}
```

### Writing B2

```json
{
  "id": "cefr-writing-b2",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "B2",
  "content_zh": "B2 写作可以在较广主题上写出清楚、详细的文本，能写文章或报告，传递信息并支持或反对某个观点。文章应有较清晰的结构、理由和细节。",
  "typical_strengths": ["观点较清楚", "能组织理由", "能处理较广主题", "语法控制较稳定"],
  "typical_limits": ["复杂表达可能模式化", "长文可能有跳跃感", "地道性和风格控制仍有限"],
  "upgrade_focus": ["更自然的组织结构", "更准确的语体", "更丰富的论证", "减少复杂句僵硬感"]
}
```

### Writing C1

```json
{
  "id": "cefr-writing-c1",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "C1",
  "content_zh": "C1 写作可以处理复杂主题，文本清楚、结构良好，能较充分地表达观点并突出重点。学习者可以根据读者和体裁调整表达风格。",
  "typical_strengths": ["结构清晰", "观点展开充分", "语体较得体", "能处理复杂主题"],
  "typical_limits": ["幽默、习语和细微风格仍可能不稳定"],
  "upgrade_focus": ["精细语义", "风格灵活性", "更高层次的文本组织"]
}
```

### Writing C2

```json
{
  "id": "cefr-writing-c2",
  "collection": "cefr_writing_guidance",
  "skill": "writing",
  "level": "C2",
  "content_zh": "C2 写作强调高度清晰、流畅、复杂且风格适切。文本结构能帮助读者识别和记住重点，并能处理复杂报告、文章、评论和摘要。",
  "typical_strengths": ["表达精准", "结构高效", "风格适切", "复杂文本处理能力强"],
  "upgrade_focus": ["保持高精度", "复杂文本修辞", "专业和文学性文本处理"]
}
```

## 写作细项到 PEAI 雷达的知识条目

### 词汇丰富

```json
{
  "id": "cefr-competence-vocabulary-range",
  "collection": "cefr_language_competence_guidance",
  "competence": "vocabulary_range",
  "mapped_peai_dimension": "词汇丰富",
  "content_zh": "词汇范围关注学习者能调用多少表达资源，以及是否能从基础生活词汇发展到更广泛、抽象、正式、习语化的表达。写作中不要只看高级词数量，还要看是否服务主题和语体。"
}
```

### 词汇准确

```json
{
  "id": "cefr-competence-vocabulary-control",
  "collection": "cefr_language_competence_guidance",
  "competence": "vocabulary_control",
  "mapped_peai_dimension": "词汇准确",
  "content_zh": "词汇控制关注选词是否准确、搭配是否自然、词义边界是否清楚。B2 以上不仅要词汇量够，还要减少搭配不当和语体不合适。"
}
```

### 语法准确

```json
{
  "id": "cefr-competence-grammatical-accuracy",
  "collection": "cefr_language_competence_guidance",
  "competence": "grammatical_accuracy",
  "mapped_peai_dimension": "语法准确",
  "content_zh": "语法准确关注错误是否影响理解、复杂结构是否稳定、长句中语法控制是否可靠。A2-B1 常见基础错误仍会出现；B2 应避免造成误解的错误；C1-C2 要求复杂形式也保持高控制度。"
}
```

### 连贯衔接

```json
{
  "id": "cefr-competence-coherence-cohesion",
  "collection": "cefr_language_competence_guidance",
  "competence": "coherence_cohesion",
  "mapped_peai_dimension": "连贯衔接",
  "content_zh": "连贯衔接关注句子和段落之间的关系是否清楚。A2 多依赖 and/but/because；B1 能把简单观点串成线性文本；B2 应能用多种连接方式组织较长文本；C1-C2 要能灵活使用组织模式和衔接手段。"
}
```

### 篇章结构

```json
{
  "id": "cefr-competence-thematic-development",
  "collection": "cefr_language_competence_guidance",
  "competence": "thematic_development",
  "mapped_peai_dimension": "篇章结构",
  "content_zh": "篇章结构关注主题是否被逐步展开，观点是否有支撑，段落是否围绕中心推进。B1 常能表达观点但展开有限；B2 应能较系统地发展论点；C1 以上应能突出关键议题并形成成熟结构。"
}
```

## 学习差距 RAG 条目

### A2 到 B1

```json
{
  "id": "cefr-gap-a2-b1-writing",
  "collection": "cefr_learning_gap_guidance",
  "skill": "writing",
  "from_level": "A2",
  "to_level": "B1",
  "content_zh": "A2 到 B1 的关键不是写更多简单句，而是能围绕熟悉主题形成连贯短文。训练重点是时态稳定、基本段落、经历描述、简单观点和理由。"
}
```

### B1 到 B2

```json
{
  "id": "cefr-gap-b1-b2-writing",
  "collection": "cefr_learning_gap_guidance",
  "skill": "writing",
  "from_level": "B1",
  "to_level": "B2",
  "content_zh": "B1 到 B2 的关键是从连贯表达升级为清楚、详细、有支撑的论述。训练重点包括段落展开、观点论证、正式表达、复杂句准确性和更自然的连接。"
}
```

### B2 到 C1

```json
{
  "id": "cefr-gap-b2-c1-writing",
  "collection": "cefr_learning_gap_guidance",
  "skill": "writing",
  "from_level": "B2",
  "to_level": "C1",
  "content_zh": "B2 到 C1 的关键是从清楚详细升级为结构成熟、风格得体、表达灵活。训练重点包括复杂主题处理、观点层次、语体控制、抽象表达和更精准的衔接。"
}
```

## 检索策略

### Scoring Agent

检索顺序：

1. `cefr_writing_guidance`：当前估计等级。
2. `cefr_language_competence_guidance`：对应弱项解释。
3. `cefr_learning_gap_guidance`：下一等级差距。

### Vocab Agent

检索顺序：

1. 用户当前 `vocab_cefr_level`。
2. 单词书数据库的 `cefr_level`。
3. `cefr_language_competence_guidance` 中的词汇范围/词汇控制说明。

### Grammar Agent

检索顺序：

1. 用户当前 `grammar_cefr_level`。
2. 错误类型 taxonomy。
3. `cefr_language_competence_guidance` 中的语法准确说明。

### Learning Planner Agent

检索顺序：

1. `user_cefr_profiles` 当前等级和目标等级。
2. `cefr_learning_gap_guidance`。
3. 用户历史错误和写作评分弱项。

## 入库优先级

### P0

- `cefr_level_guidance`
- `cefr_skill_guidance`
- `cefr_writing_guidance`
- `cefr_learning_gap_guidance`

### P1

- `cefr_language_competence_guidance`
- `cefr_vocab_guidance`
- `cefr_grammar_guidance`

### P2

- online interaction
- mediation
- spoken production advanced descriptors

## 不建议入库的内容

- 手语能力章节。
- 大量政策背景。
- 完整参考文献。
- 研究方法细节。
- 大段官方英文原文。
- 无法映射到 PEAI 产品功能的 descriptor。

## 第一版可直接使用的中文检索问题

这些 query 可用于测试 RAG 召回：

```text
B1 写作到 B2 写作差什么？
为什么这篇作文只能算 B1？
B2 写作应该有什么表现？
语法错误很多会影响 CEFR 哪个维度？
词汇丰富但搭配不准怎么解释？
如何给 B1 学生安排 4 周写作提升计划？
```
