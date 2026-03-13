# Postgrad Exam 评分细则草案

## 目标

为 `postgrad` 学段的 `exam` 模式定义一套可执行评分协议，用于后端评分逻辑、Prompt 约束和前端结果解释。

第一阶段先共用一套 `postgrad` 规则，不拆英语一/英语二。

## 一、评分总原则

评分顺序固定如下：

1. 先判方向
2. 再看质量
3. 最后执行硬约束

对应系统语义：

- `方向门槛层`
  - 判断切题度、任务完成度、要点覆盖度
  - 产出允许进入的最高档位 `max_band`
- `质量细化层`
  - 在 `max_band` 允许范围内，根据语言和结构质量确定具体分数
- `硬约束层`
  - 对严重跑题、严重字数不足、重大语法错误累计等情况做封顶或扣分

禁止出现的评分结果：

- 切题度低，但因为语言漂亮进入高档
- 任务未完成，但因为局部表达亮眼拿到高总分

## 二、满分与档位

第一阶段统一按 `100` 分制输出总分，并映射五档：

| 档位 | 中文 | 分数区间 |
|------|------|----------|
| Band 5 | 优秀 | 85-100 |
| Band 4 | 良好 | 70-84 |
| Band 3 | 中等 | 55-69 |
| Band 2 | 较差 | 40-54 |
| Band 1 | 差 | 0-39 |

说明：

- 该区间用于系统内部评分和前端展示
- 后续如果需要对接考研英语一/英语二原始分值，可在结果层再做换算

## 三、方向门槛层

### 3.1 判定目标

方向门槛层先判断作文是否“值得进入高档”。

它不决定最终精确分数，但决定 `max_band`。

### 3.2 关键输入

- `relevance`
  - `fully_on_topic`
  - `mostly_on_topic`
  - `partially_off_topic`
  - `seriously_off_topic`
- `task_completion`
  - `fully_completed`
  - `mostly_completed`
  - `partially_completed`
  - `seriously_incomplete`
- `coverage`
  - `all_key_points`
  - `most_key_points`
  - `partial_key_points`
  - `few_key_points`

### 3.3 最高档位规则

建议按以下规则收敛为 `max_band`：

| 条件 | 最高档位 |
|------|----------|
| `fully_on_topic` + `fully_completed` + `all_key_points` | Band 5 |
| `mostly_on_topic` + `mostly_completed` + `most_key_points` | Band 4 |
| 基本切题，但覆盖不完整或完成度一般 | Band 3 |
| 明显偏题或任务完成较差 | Band 2 |
| 严重跑题或几乎未完成任务 | Band 1 |

### 3.4 实现约束

- 方向门槛层是考试评分的首要约束
- 质量层不得突破 `max_band`
- 若方向层判定为 `Band 2` 上限，则后续即使语言质量很好，最终也只能落在 `40-54`

## 四、质量细化层

### 4.1 作用

质量细化层只负责决定“在允许档位里跑到哪一段”，不负责越档。

### 4.2 使用维度

沿用当前 6 维：

- `content_quality`
- `task_achievement`
- `structure`
- `vocabulary`
- `grammar`
- `expression`

### 4.3 质量判断重点

- `content_quality`
  - 论点是否明确，论述是否充实
- `task_achievement`
  - 是否完成题目要求的写作任务
- `structure`
  - 段落是否完整，逻辑是否顺畅
- `vocabulary`
  - 词汇是否准确、丰富
- `grammar`
  - 语法是否稳定，重大错误是否频繁
- `expression`
  - 表达是否自然，句式是否有变化

### 4.4 档内分数分配建议

可按三段落位处理：

- 档内高位
  - 维度整体稳定，仅有轻微缺点
- 档内中位
  - 有明显不足，但整体仍符合该档标准
- 档内低位
  - 勉强符合该档，存在多个拖后腿维度

示例：

- `Band 4`
  - 高位：`81-84`
  - 中位：`76-80`
  - 低位：`70-75`

该分配由后端基于维度分计算，不要求前端展示细节。

## 五、硬约束层

第一阶段只实现可稳定落地的硬约束。

### 5.1 跑题封顶

- `seriously_off_topic`
  - 总分封顶 `39`
  - 即不能超过 `Band 1`
- `partially_off_topic`
  - 总分封顶 `54`
  - 即不能超过 `Band 2`

### 5.2 字数不足处罚

先使用字数达成率判断，再决定扣分或降档。

建议字段：

- `word_count_ratio`
  - `>= 1.0`
  - `0.85 - 0.99`
  - `0.70 - 0.84`
  - `< 0.70`

建议处罚：

| 字数达成率 | 处理建议 |
|------------|----------|
| `>= 1.0` | 不处罚 |
| `0.85 - 0.99` | 扣 3 分 |
| `0.70 - 0.84` | 扣 8 分，并且最高不超过 Band 3 |
| `< 0.70` | 最高不超过 Band 2，必要时进一步扣分 |

### 5.3 任务未完成封顶

- `partially_completed`
  - 总分最高不超过 `69`
- `seriously_incomplete`
  - 总分最高不超过 `54`

### 5.4 重大语法错误累计处罚

重大语法错误指影响理解或严重破坏句子结构的错误，例如：

- 句子缺少谓语
- 主谓严重不一致
- 时态混乱导致语义不清
- 长句结构断裂，难以理解

建议处罚：

| 严重度 | 处理建议 |
|--------|----------|
| 轻度 | 不额外处罚，仅体现在 `grammar` 维度 |
| 中度 | 额外扣 3-5 分 |
| 重度 | 额外扣 8-12 分，必要时降一档 |

## 六、后端结构化输出建议

为让前端能解释评分过程，建议响应中新增：

```json
{
  "requested_stage": "postgrad",
  "effective_stage": "postgrad",
  "rubric_key": "postgrad-exam-v1",
  "fallback_used": false,
  "exam_band": {
    "label": "Band 3",
    "min": 55,
    "max": 69
  },
  "direction_assessment": {
    "relevance": "mostly_on_topic",
    "task_completion": "partially_completed",
    "max_band": "Band 3",
    "reasons": [
      "内容基本切题",
      "任务覆盖不完整，限制最高档位"
    ]
  },
  "score_adjustment": {
    "cap": 69,
    "deductions": 8,
    "reasons": [
      "字数不足触发扣分",
      "重大语法错误较多"
    ]
  }
}
```

## 七、前端展示建议

前端先不大改 UI，只增加解释区即可：

- `当前第三档：内容基本切题，但任务覆盖不完整`
- `因任务完成度不足，最高档位被限制`
- `因字数不足触发扣分`

继续保留：

- `overall / 100`
- 当前 6 维评分区

## 八、一期范围与非目标

一期目标：

- 支持 `studyStage=postgrad`
- 支持 `exam` 模式的方向门槛评分
- 支持跑题、字数不足、任务未完成、重大语法错误四类硬约束

一期不做：

- 模板痕迹强判定
- 抄袭/雷同判定
- 英语一 / 英语二拆分
- 前端按学段改造成不同维度列表

## 九、后续拆分建议

当 `postgrad` 规则跑稳后，再继续补：

- `highschool-exam-policy.md`
- `middle-school-exam-policy.md`
- `free-mode-weight-policy.md`
- `cet4-exam-policy.md`
