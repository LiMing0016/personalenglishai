# Postgrad Exam Policy
> key: `postgrad-exam-policy-v1`
> stage: `postgrad`
> mode: `exam`

## 一、Policy 目标

本 Policy 用于 `postgrad-exam-v1` 的执行层控制，不替代 Rubric，而是负责：

- 方向门槛
- 跑题封顶
- 任务未完成封顶
- 字数处罚
- 最终总分计算顺序

Rubric 负责定义“什么叫 A/B/C/D/E”，
Policy 负责定义“哪些考试规则会限制最终得分”。

---

## 二、总分计算顺序

最终得分必须按以下顺序生成：

1. 按 Rubric 对 6 个维度评分
2. 根据维度权重计算 `raw_score`
3. 判定方向门槛（切题度、任务完成度、覆盖度）
4. 应用跑题 / 未完成任务的封顶规则
5. 应用字数规则
6. 得到 `final_score`
7. 将 `final_score` 映射到 Band 档位

### 公式示意

```text
dimension_scores
→ weighted raw_score
→ topic/task cap
→ word_count adjustment
→ final_score
```

---

## 三、方向门槛层

方向门槛层先判断作文是否“有资格进入高档”。

它不负责给出最终精确分数，但负责限制最高分。

### 3.1 方向判定字段

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

### 3.2 最高档位限制

| 条件 | 最高档位 | 最高分 |
|------|----------|--------|
| `fully_on_topic` + `fully_completed` + `all_key_points` | Band 5 | 100 |
| `mostly_on_topic` + `mostly_completed` + `most_key_points` | Band 4 | 84 |
| 基本切题，但覆盖不完整或完成度一般 | Band 3 | 69 |
| 明显偏题或任务完成较差 | Band 2 | 54 |
| 严重跑题或几乎未完成任务 | Band 1 | 39 |

说明：

- 方向门槛优先于语言质量
- 语言维度不能突破封顶上限
- 即使语言非常成熟，只要严重偏题，最终也不得高分

---

## 四、跑题与任务未完成规则

### 4.1 跑题封顶

| relevance | 处理 |
|-----------|------|
| `fully_on_topic` | 不封顶 |
| `mostly_on_topic` | 不额外封顶 |
| `partially_off_topic` | `final_score <= 54` |
| `seriously_off_topic` | `final_score <= 39` |

### 4.2 任务未完成封顶

| task_completion | 处理 |
|-----------------|------|
| `fully_completed` | 不封顶 |
| `mostly_completed` | 不额外封顶 |
| `partially_completed` | `final_score <= 69` |
| `seriously_incomplete` | `final_score <= 54` |

### 4.3 特别说明

#### `task1`

以下情况通常至少判为 `partially_completed`：

- 关键动作缺失
- 语气与对象明显不匹配
- 格式严重不完整
- 只写了一个要求，漏掉另一个主要要求

#### `task2`

以下情况通常至少判为 `partially_completed`：

- 未完成“描述 + 解读 + 评论”三步
- 只描述，不解释
- 只评论，不描述材料
- 材料使用极弱，评论脱离题目

---

## 五、字数规则

字数处罚不作为独立维度，而作为后处理规则参与最终分计算。

### 5.1 计算方式

```text
ratio = actual_words / min_required_words
effective_ratio = ratio + buffer_zone
```

### 5.2 推荐配置

```yaml
word_count_policy:
  enabled: true

  tiers:
    - name: "达标"
      ratio_min: 1.00
      deduction: 0
      cap_score: null

    - name: "轻微不足"
      ratio_min: 0.85
      deduction: 3
      cap_score: 84

    - name: "中度不足"
      ratio_min: 0.70
      deduction: 5
      cap_score: 69

    - name: "严重不足"
      ratio_min: 0.50
      deduction: 0
      cap_score: 54

    - name: "极严重"
      ratio_min: 0.00
      deduction: 0
      cap_score: 39

  buffer_zone: 0.02
```

### 5.3 解释

| 区间 | 处理逻辑 |
|------|----------|
| `>= 1.00` | 不处罚 |
| `0.85 - 0.99` | 轻度扣分，且最高不超过 Band 4 |
| `0.70 - 0.84` | Band 3 封顶并轻扣 |
| `0.50 - 0.69` | Band 2 封顶 |
| `< 0.50` | Band 1 封顶 |

说明：

- 字数不足会同时影响 `task_achievement` 的判档倾向
- Policy 再从最终分层面做封顶 / 扣分
- 这样既体现考试规则，也避免简单平均掩盖任务不足

---

## 六、封顶与扣分执行规则

为避免“双重惩罚过重”，统一采用以下顺序：

1. 计算 `raw_score`
2. 汇总所有封顶上限，取最严格的那个：
   - `topic_cap`
   - `task_completion_cap`
   - `word_count_cap`
3. 对 `raw_score` 先做封顶
4. 再应用当前 tier 的 `deduction`
5. 得到 `final_score`

### 公式

```text
cap_score = min(topic_cap, task_completion_cap, word_count_cap)
capped_score = min(raw_score, cap_score)   # 若 cap_score 存在
final_score = capped_score - deduction
```

### 保护规则

- `final_score` 不得低于 `0`
- 若已触发 `Band 1` 封顶，不再追加重罚
- 若多个规则同时命中，只叠加小额扣分，不叠加多个大额重罚

---

## 七、Band 映射

最终分数按以下区间映射：

| Band | 区间 |
|------|------|
| Band 5 | 85-100 |
| Band 4 | 70-84 |
| Band 3 | 55-69 |
| Band 2 | 40-54 |
| Band 1 | 0-39 |

---

## 八、结构化输出建议

推荐在后端内部或响应中保留以下字段，便于解释分数来源：

```json
{
  "requested_stage": "postgrad",
  "effective_stage": "postgrad",
  "rubric_key": "postgrad-exam-v1",
  "policy_key": "postgrad-exam-policy-v1",
  "task_type": "task2",
  "raw_score": 78,
  "final_score": 69,
  "exam_band": {
    "label": "Band 3",
    "min": 55,
    "max": 69
  },
  "direction_assessment": {
    "relevance": "mostly_on_topic",
    "task_completion": "partially_completed",
    "coverage": "most_key_points"
  },
  "score_adjustment": {
    "topic_cap": null,
    "task_completion_cap": 69,
    "word_count_cap": 84,
    "deduction": 3,
    "reasons": [
      "字数轻微不足，最高不超过 Band 4",
      "字数轻微不足，扣 3 分"
    ]
  }
}
```

---

## 九、Prompt 使用规则

模型与后端都必须遵守：

- 先看任务，再看语言
- 偏题不能高分
- 未完成任务不能高分
- 字数不足不能进入不合理高档
- 加权用于汇总，不用于覆盖考试规则
- Policy 优先级高于普通平均分
- `task1` 不按议论文标准打分
- `task2` 不按功能写作标准打分

---

## 十、首版范围

首版支持：

- `studyStage = postgrad`
- `mode = exam`
- `taskType = task1 / task2`
- 跑题封顶
- 任务未完成封顶
- 字数处罚
- 最终总分计算与 Band 映射

首版不做：

- 英语一 / 英语二分化
- 图画作文 / 图表作文进一步分拆
- 模板痕迹专项惩罚
- 抄袭检测
- 原始卷面分映射
