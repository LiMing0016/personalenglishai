# 评分持久化数据库说明

## 总体结构

本次评分持久化分三层：

- `essay_evaluation`
  - 每次评分一条主记录
- `essay_evaluation_dimension`
  - 每次评分下各维度明细
- `document_score_summary`
  - 每篇作文一条评分摘要

其中：

- `writing_metadata / writing_exam_metadata`
  - 继续只保存题目上下文和考试约束
- `result_json`
  - 继续保存原始评分结果快照

## essay_evaluation

### 基础关联

- `id`
  - 评分记录主键
- `user_id`
  - 用户 ID
- `document_id`
  - 作文 ID
- `mode`
  - 评分模式，`free` 或 `exam`
- `task_prompt`
  - 题目快照
- `essay_text`
  - 作文正文快照

### 评分结果

- `gaokao_score`
  - 兼容旧链路的换算分
- `max_score`
  - 兼容旧链路的分值上限
- `band`
  - 兼容旧链路的档位
- `overall_score`
  - 本次评分总分
- `exam_band_label`
  - 100 分制档位标签
- `exam_band_min`
  - 100 分制档位下限
- `exam_band_max`
  - 100 分制档位上限

### 评分规则版本

- `study_stage`
  - 本次评分实际使用的学段
- `rubric_key`
  - 本次评分使用的 rubric 版本
- `exam_policy_key`
  - 本次考试评分策略版本
- `model_version`
  - 本次评分使用的模型版本
- `evaluated_revision`
  - 本次评分对应的作文正文版本号

### 方向门槛

- `direction_relevance`
  - 切题度判断
- `direction_task_completion`
  - 任务完成度判断
- `direction_coverage`
  - 要点覆盖度判断
- `direction_max_band`
  - 方向门槛允许进入的最高档位

### 硬约束

- `cap_score`
  - 封顶分
- `deduction_total`
  - 总扣分
- `penalty_flags_json`
  - 处罚标记 JSON
- `direction_reasons_json`
  - 方向门槛原因 JSON
- `adjustment_reasons_json`
  - 调整原因 JSON

### 文本统计

- `word_count`
  - 实际词数
- `sentence_count`
  - 句子数
- `paragraph_count`
  - 段落数

### 错误统计

- `total_error_count`
  - 总错误数
- `major_error_count`
  - 严重错误数
- `minor_error_count`
  - 轻微错误数
- `grammar_error_count`
  - 语法类错误数
- `vocabulary_error_count`
  - 兼容旧链路的词汇错误数
- `lexical_error_count`
  - 词汇类错误数
- `spelling_error_count`
  - 拼写类错误数
- `punctuation_error_count`
  - 标点类错误数
- `syntax_error_count`
  - 句法类错误数

### 结果快照

- `result_json`
  - 原始评分结果 JSON
- `created_at`
  - 评分记录创建时间

## essay_evaluation_dimension

- `id`
  - 维度明细主键
- `evaluation_id`
  - 关联的评分记录 ID
- `dimension_key`
  - 维度标识
- `dimension_label_snapshot`
  - 维度名称快照
- `sort_order`
  - 维度显示顺序
- `score`
  - 该维度分数
- `grade`
  - 该维度等级
- `strength`
  - 该维度优势点评
- `weakness`
  - 该维度不足点评
- `suggestion`
  - 该维度改进建议
- `created_at`
  - 明细创建时间

说明：

- 一条评分可对应多条维度明细
- 不再依赖固定 5 维或 6 维列
- 本期不存 `strength_quote / weakness_quote`

## document_score_summary

- `document_id`
  - 作文 ID，一篇作文一条摘要
- `user_id`
  - 用户 ID
- `first_evaluation_id`
  - 首次评分记录 ID
- `latest_evaluation_id`
  - 最近一次评分记录 ID
- `best_evaluation_id`
  - 历史最佳评分记录 ID
- `first_overall_score`
  - 首次评分总分
- `latest_overall_score`
  - 最近一次评分总分
- `best_overall_score`
  - 历史最高分
- `latest_band_label`
  - 最近一次评分档位
- `latest_word_count`
  - 最近一次词数
- `latest_total_error_count`
  - 最近一次总错误数
- `latest_major_error_count`
  - 最近一次严重错误数
- `latest_minor_error_count`
  - 最近一次轻微错误数
- `created_at`
  - 摘要创建时间
- `updated_at`
  - 摘要更新时间

说明：

- 这张表只做单篇作文的快速摘要
- 不复制整份评分详情
- 页面快速读取和个性化入口优先用这张表
