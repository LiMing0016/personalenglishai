# CEFR 在 PEAI 项目中的使用指南

## 这份文档解决什么问题

CEFR 不是一套英语教材，也不是一个作文评分公式。它更像一把通用尺子，用来描述一个学习者当前大概能用英语完成什么任务。

对 PEAI 来说，CEFR 最有价值的地方不是照搬官方原文，而是把项目里原本分散的能力判断统一到同一套坐标上：

- 用户当前能力处在什么水平。
- 单词、语法、作文任务大概适合什么水平。
- 一篇作文为什么看起来像 B1，而不是 B2。
- 用户从当前水平到目标水平，中间差了哪些能力。
- 学习助手应该用多难的解释、例句、练习和反馈。

## CEFR 能帮项目做什么

### 1. 做用户能力画像

当前项目已有学习助手、语法事件、写作评分、学习规划等能力。CEFR 可以作为这些能力之间的共同标签。

建议画像字段：

| 字段 | 含义 |
| --- | --- |
| `estimated_cefr_level` | 用户整体英语能力估计，例如 `B1` |
| `target_cefr_level` | 用户目标水平，例如 `B2` |
| `writing_cefr_level` | 写作能力估计 |
| `vocab_cefr_level` | 词汇能力估计 |
| `grammar_cefr_level` | 语法能力估计 |
| `confidence` | 本次估计可信度，避免把粗略判断说成精确结果 |
| `evidence` | 触发判断的证据，例如作文、错题、测验或学习记录 |

第一版不要声称“正式 CEFR 认证”。产品文案应使用“估计”“接近”“约等于”。

### 2. 做写作评分解释层

CEFR 不替代中高考、四级、考研、雅思、托福等评分规则。考试评分仍按各自 rubric 来做。

CEFR 负责解释能力：

```text
考试评分：按考研写作 rubric 给 72/100。
CEFR 解释：这篇文章大致接近 B1+ 到 B2-。
主要限制：论证展开不足，复杂句不稳定，正式表达不足。
下一目标：稳定达到 B2 写作。
```

这样用户更容易理解“我现在在哪里”和“下一步补什么”。

### 3. 给单词书和表达库分级

你的自写单词书适合先做数据库，再加 CEFR 标签。

建议给每个词条或短语增加：

| 字段 | 含义 |
| --- | --- |
| `cefr_level` | A1 / A2 / B1 / B2 / C1 / C2 |
| `level_source` | `manual` / `estimated` / `imported` |
| `topic_tags` | 主题，例如 education、environment、technology |
| `exam_tags` | 四级、六级、考研、雅思、托福等 |
| `productive_use` | 是否适合写作和口语主动使用 |
| `common_mistakes` | 常见误用 |
| `collocations` | 常见搭配 |

这样 Vocab Agent 可以回答：

```text
这个词对你当前 B1 写作略偏难，但可以作为 B2 升级表达。
如果你只是想写得清楚，用 improve 就够了；如果想更正式，可以用 enhance。
```

### 4. 给语法知识库分层

CEFR 可以帮助语法讲解避免“一上来讲太难”。

可按如下思路整理项目内语法知识库：

| CEFR | 语法重点 |
| --- | --- |
| A1-A2 | 基本句型、be 动词、一般现在时、一般过去时、冠词、介词、基础疑问句 |
| B1 | 常见从句、现在完成时、被动语态、比较结构、基础连接词 |
| B2 | 更稳定的复杂句、条件句、非谓语、让步结构、因果和对比衔接 |
| C1-C2 | 名词化、强调结构、语体控制、复杂衔接、抽象表达和细微语义差异 |

这不是官方完整清单，而是项目第一版可执行的产品分层。

### 5. 做学习规划

Learning Planner Agent 可以围绕“当前级别 -> 目标级别”生成计划。

示例：

```text
当前：B1 writing
目标：B2 writing
差距：
1. 段落展开不足
2. 复杂句能用但错误多
3. 正式表达储备不足

4 周计划：
第 1 周：句子准确性
第 2 周：段落展开
第 3 周：连接和论证
第 4 周：限时写作与复盘
```

## 从 CEFR 官方资料中提取什么

第一阶段只提取这些，不要贪多：

### P0：必须提取

| 内容 | 用途 |
| --- | --- |
| A1-C2 六级名称和大意 | 作为项目统一能力等级 |
| Basic / Independent / Proficient 三大类 | 给用户看懂等级层次 |
| Writing descriptors | 写作评分解释、写作目标设定 |
| Reading descriptors | 阅读任务和材料难度匹配 |
| Listening descriptors | 后续听力模块预留 |
| Spoken interaction / production descriptors | 后续口语模块预留 |
| Self-assessment grid | 产品化文案和用户自评入口 |

### P1：后续提取

| 内容 | 用途 |
| --- | --- |
| Plus levels：A2+ / B1+ / B2+ | 更细的成长阶段 |
| Online interaction | 在线聊天、邮件、论坛表达任务 |
| Mediation | 解释、转述、总结、跨语言表达 |
| Phonological control | 口语发音模块 |

### 暂时不要提取

| 内容 | 原因 |
| --- | --- |
| 完整 Companion Volume 全文 | 太长，不适合直接塞进 prompt 或 RAG |
| 所有 descriptor 原文 | 容易变成低价值全文库 |
| 官方 logo / 认证表述 | 容易让用户误解为官方认证 |
| 非英语语言相关内容 | 当前项目只服务英语学习 |

## Prompt、RAG、数据库怎么分

### 放 Prompt

放必须稳定遵守的流程规则：

```text
回答时根据用户当前 CEFR 估计调整难度。
如果给 CEFR 判断，必须说明这是估计，不是官方认证。
评分时先使用当前考试 rubric，再补充 CEFR 能力解释。
不要向用户暴露内部检索、路由和评分机制。
```

### 放 RAG

放需要按场景检索的知识：

- A1-C2 写作能力描述。
- A1-C2 语法能力描述。
- A1-C2 词汇使用特征。
- 不同级别的例句难度说明。
- 项目自己整理的“从 B1 到 B2 的常见差距”。

### 放数据库

放需要精确查询、更新和统计的数据：

- 用户当前 CEFR 估计。
- 用户目标 CEFR。
- 用户历史作文 CEFR 变化。
- 单词书词条的 CEFR 标签。
- 语法点的 CEFR 标签。
- 错题、学习事件、复习记录。

## 推荐的第一版落地范围

第一版只做这 5 件事：

1. 建一个项目内部文档：`cefr-ability-framework.md`。
2. 定义项目自己的 A1-C2 中文解释。
3. 给写作评分结果加 `estimated_cefr_level`。
4. 给单词书词条加 `cefr_level` 字段。
5. 给学习规划加“当前级别 -> 目标级别”的差距解释。

先不要做：

- 官方 CEFR 全量 RAG。
- 自动认证用户 CEFR。
- 复杂跨考试换算。
- 口语、听力全模块扩展。

## 项目内建议命名

### Schema

```text
EstimatedCefrLevel
CefrSkillProfile
CefrEvidence
CefrLearningGap
```

### Tool

```text
get_cefr_level_policy
estimate_writing_cefr_level
search_cefr_learning_guidance
get_vocab_entries_by_cefr
```

### RAG collection

```text
cefr_level_guidance
cefr_writing_descriptors
cefr_grammar_guidance
cefr_vocab_guidance
```

## 官方资料入口

- Council of Europe CEFR 官方首页：  
  https://www.coe.int/en/web/common-european-framework-reference-languages

- CEFR Companion Volume 2020：  
  https://www.coe.int/en/web/common-european-framework-reference-languages/cefr-companion-volume-and-its-language-versions

- CEFR Levels：  
  https://www.coe.int/en/web/common-european-framework-reference-languages/level-%20%20%20%20%20%20%20%20%20%20descriptions

- Europass CEFR self-assessment grid：  
  https://europass.europa.eu/en/common-european-framework-reference-language-skills

## 一句话结论

CEFR 在 PEAI 里应该作为“能力坐标系”，不是教材、不是考试评分公式、也不是官方认证。考试规则继续负责打分；CEFR 负责解释能力、组织学习路径、标注词汇和语法难度。
