# ai_orchestrator 协作规则

本文件定义 `python/ai_orchestrator/` 目录下的细化开发规则。
除本文件外，必须同时遵守上层 `python/AGENTS.md`。

---

## 目录定位

`ai_orchestrator/` 是 Python 侧新的 agent 应用主承载目录。

该目录负责：

- orchestrator agent
- capability agents
- workflows
- tools
- prompts
- schemas
- services
- 业务链路编排
- 与对外入口层解耦的核心应用逻辑

该目录不负责：

- 演化通用 agent 平台
- 自研 agent runtime
- 承载遗留上下文系统扩展
- 混放一次性脚本与正式模块

---

## 架构分层

`ai_orchestrator/` 下的代码应尽量映射到以下分层。

### 1. agents/

用于承载 capability agent 与 orchestrator agent 定义。

要求：
- 每个 agent 职责单一
- 每个 agent 有清晰输入输出
- 每个 agent 对应明确 prompt 资产
- 每个 agent 可独立做最小回归

### 2. workflows/

用于承载明确业务链路。

例如：
- 评分 workflow
- 润色 workflow
- 评分后润色再复评 workflow
- 词汇讲解并生成练习 workflow

要求：
- 不把 workflow 写成长函数堆砌
- 中间结果显式传递
- 关键分支可测试
- 编排优先调用 tool 与 capability agent，不直接堆叠 prompt 文本

### 3. tools/

用于承载结构化、可复用能力。

例如：
- rubric 获取
- 用户画像加载
- 审题
- 字数检测
- 错误分类
- 结果保存
- 练习推荐

要求：
- tool 优先返回结构化数据
- tool 契约清晰
- tool 可单测
- tool 不偷偷依赖隐式共享状态

### 4. prompts/

用于存放正式 prompt 资产。

要求：
- 不散落到脚本、入口或测试代码中
- 命名清晰表达 agent / workflow / version / scenario
- 改动必须可回归

### 5. schemas/

用于定义输入输出契约、结构化结果、共享上下文对象。

要求：
- schema 优先作为 agent / workflow / tool 之间的显式契约
- 避免跨模块传递自由 dict 且字段含义不明
- schema 变更必须同步检查调用链路

### 6. services/

用于承载业务服务组合逻辑或外部依赖适配逻辑。

要求：
- 不把 service 变成另一层隐式 orchestrator
- service 应清晰区分业务服务与基础设施适配

### 7. adapters/ 或 entrypoints/

用于 HTTP、CLI、job runner 等对外接入层。

要求：
- 只做协议适配、参数解析、返回格式化
- 不承载主业务编排逻辑

---

## 推荐目录结构

目录可按实际情况微调，但整体应接近如下结构：

```text
ai_orchestrator/
- agents/
  - orchestrator/
  - scoring/
  - polish/
  - vocab/
  - translation_feedback/
  - sentence_analysis/
- workflows/
  - evaluate_essay.py
  - polish_essay.py
  - evaluate_then_polish.py
  - explain_vocab.py
- tools/
  - rubric/
  - user_profile/
  - writing_checks/
  - vocab_lookup/
  - persistence/
  - recommendation/
- prompts/
  - agents/
  - workflows/
  - shared/
- schemas/
  - context.py
  - requests.py
  - responses.py
  - evaluation.py
  - polish.py
- services/
- adapters/
- tests/
```

不要为了形式一致而机械建目录；但必须保持边界清晰。

---

## orchestrator agent 规则

`orchestrator agent` 是调度者，不是业务大杂烩。

职责：

1. 接收标准化输入
2. 判断进入哪个 workflow
3. 装配共享上下文
4. 调用必要的 tool
5. 调用 capability agent
6. 汇总结果并返回标准化输出

禁止：

1. 在 orchestrator 中直接堆大量领域规则
2. 在 orchestrator 中内联大型 prompt
3. 让 orchestrator 兼做数据持久化细节实现
4. 把多个业务链路直接写成 if/else 大泥球
5. 让 orchestrator 与 HTTP/CLI 入口层强耦合

---

## capability agent 规则

`capability agent` 是受控的垂直能力模块，不是自治智能体。

当前优先支持的能力型 agent 包括但不限于：

- scoring agent
- polish agent
- vocab agent
- translation feedback agent
- sentence analysis agent

### 设计要求

1. 一个 capability agent 只负责一类核心任务。
2. 必须有独立 prompt 资产。
3. 必须有明确输入 schema 与输出 schema。
4. 必须可以脱离完整系统进行最小回归。
5. 输出应尽量结构化，而不是只返回大段自然语言。

### 禁止事项

1. capability agent 自行决定全局流程
2. capability agent 自由调用其他 capability agent
3. capability agent 直接读写隐式共享状态
4. 在 capability agent 内塞入大量与本能力无关的业务逻辑
5. 把多个能力混成一个“万能 agent”

跨能力协作默认由 orchestrator 或 workflow 负责，不允许自由互调，除非文档明确批准。

---

## workflow 设计规则

workflow 是业务链路，不是 prompt 堆叠容器。

### workflow 应满足：

1. 输入输出明确
2. 节点顺序清晰
3. 中间结果可观察
4. 错误路径可处理
5. 可单独 smoke test

### workflow 应优先负责：

- 评分链路
- 润色链路
- 评分 + 润色 + 复评链路
- 词汇讲解与练习生成链路
- 句子分析与反馈链路

### workflow 不应负责：

- 实现具体 tool 细节
- 直接承载外部协议适配
- 保存大量散乱 prompt 文本
- 变成“另一个 orchestrator”

---

## 共享上下文规则

跨 agent、workflow、tool 传递的数据，必须优先走显式 schema，不鼓励自由拼 dict。

推荐至少统一一类共享上下文对象，例如：

- user_id
- stage
- exam_type
- task_type
- prompt_text
- essay_text
- selected_text
- rubric_key
- target_action
- ability_profile
- trace metadata

规则：

1. 共享上下文字段命名应统一。
2. 同一字段在不同模块中含义必须一致。
3. 不允许同义字段并存，如 `essay`, `essay_text`, `content` 混用。
4. 上下文对象应最小化，不要把所有信息都塞进去。

---

## Tool 细化规则

`ai_orchestrator/` 下的 tool 默认围绕业务对象设计。

优先沉淀的 tool 类型包括：

### 1. 用户与画像类

- get_user_profile
- get_stage_policy
- get_ability_profile
- update_ability_profile

### 2. 写作资产类

- get_rubric
- get_exam_constraints
- get_prompt_template
- save_evaluation_result
- save_polish_result

### 3. 学习资源类

- search_word_bank
- search_phrase_bank
- get_model_essay
- search_question_bank

### 4. 校验与分析类

- check_word_count
- check_topic_alignment
- classify_writing_errors
- extract_key_issues

### 5. 追踪与推荐类

- summarize_recent_progress
- recommend_next_exercise
- build_revision_plan

要求：

1. 这些能力优先做成 tool，而不是先做成 agent。
2. 同一类数据查询不要重复造多个近义 tool。
3. tool 名称应直接表达动作和对象。
4. tool 失败行为必须可预期。

---

## Prompt 管理规则

### 存放规则

1. prompt 必须放在 `prompts/` 正式目录下。
2. agent prompt 与 workflow prompt 应分开管理。
3. 共享片段应放在可追踪位置，避免复制粘贴分叉失控。

### 内容规则

1. prompt 应包含清晰的角色、目标、输入、输出和边界。
2. 约束前置，避免在尾部追加大量补丁式限制。
3. 输出格式要求必须明确，必要时与 schema 对齐。
4. 避免写不可验证的风格性行为指令。

### 版本与回归

1. 高风险 prompt 变更应有版本或变更说明。
2. prompt 改动必须至少配一组最小回归样例。
3. 主链路 prompt 需要保留 smoke case。

### OpenAI 远程 Prompt

1. 仓库内 `prompts/` 仍是 prompt 权威源，OpenAI 远程 Prompt 只作为运行时发布版本。
2. 使用正式 prompt 资产的 agent 创建必须通过 `prompts.resolver.resolve_agent_prompt_kwargs` 接入 prompt，不要在 agent 构造点自行判断环境变量。
3. 默认使用本地 `instructions`；远程 Prompt 只能通过显式环境变量启用，并必须保留本地回退路径。
4. 生产启用远程 Prompt 时应固定 OpenAI Prompt version，避免远程最新版本静默改变线上行为。
5. Kimi、Qwen 或其他非 `api.openai.com` base URL 不应使用 OpenAI 远程 Prompt。

---

## Schema 与契约规则

1. agent 输入输出必须优先使用 schema 定义。
2. workflow 节点之间传递的关键中间结果应结构化。
3. tool 返回结构必须稳定，字段含义明确。
4. 关键响应对象不得直接返回松散自由文本替代结构化结果。
5. schema 变更时必须同步更新：
   - 调用方
   - 测试
   - 文档
   - 必要的 prompt 输出约束

---

## 入口层规则

HTTP、CLI、任务消费器等入口层只做适配，不承载核心编排逻辑。

入口层职责仅限于：

1. 参数解析
2. 请求校验
3. 调用 orchestrator / workflow
4. 返回格式转换
5. 基础错误映射
6. trace / request_id 注入

禁止：

1. 在入口层写业务链路编排
2. 在入口层拼接大型 prompt
3. 在入口层访问多个底层服务后自己拼业务结果
4. 在入口层偷偷绕过 orchestrator 或 workflow 直接调用多个 agent

---

## 测试规则

`ai_orchestrator/` 下的改动必须可验证。

### 最低要求

1. 新增或修改 tool：至少有单测或最小调用验证
2. 新增或修改 workflow：至少有一条端到端 smoke case
3. 新增或修改 capability agent：至少有 prompt 回归样例和结构化输出验证
4. 修改 orchestrator：至少覆盖主路径和一个失败路径
5. 修改 schema：至少覆盖校验与兼容性测试

### 推荐测试层次

- unit tests
- prompt regression tests
- workflow smoke tests
- end-to-end verification for key chains

---

## 文档与评审要求

以下变化应同步补文档或说明：

- 新增 capability agent
- 新增 workflow
- 新增关键 tool
- schema 调整
- prompt 组织方式变化
- 主链路编排变化
- 新配置项
- 新运行入口

评审时应优先看：

1. 这段代码属于哪一层
2. 边界是否清晰
3. 是否本应做成 tool 而不是 agent
4. 是否违反了 orchestrator / capability agent 的职责划分
5. 是否附带了足够验证

---

## 禁止模式

以下模式在 `ai_orchestrator/` 下默认禁止：

1. 万能 agent
2. 自由互调的多 agent 网络
3. 把 workflow 写成上千行长函数
4. prompt 与 Python 逻辑高度缠绕
5. 自由 dict 到处传，字段含义不清
6. 把持久化、业务规则、模型调用、返回格式化揉成一个文件
7. 为了“以后可能复用”提前抽象出平台层
8. 在没有明确需要时新增目录层级和抽象层

---

## 目标

`ai_orchestrator/` 的目标是：

1. 成为本项目新的业务 agent 应用主承载目录
2. 用清晰分层承载 orchestrator、capability agents、workflows、tools、prompts、schemas
3. 优先服务业务主链路与学习闭环
4. 依托 OpenAI 官方工作流，而不是演化自定义 agent 平台
