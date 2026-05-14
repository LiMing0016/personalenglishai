---
status: active
owner: writing
last_updated: 2026-05-13
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java
  - backend/src/main/java/com/personalenglishai/backend/service/writing/WritingDashboardService.java
  - backend/src/main/resources/mapper/DocumentScoreSummaryMapper.xml
  - backend/src/main/resources/mapper/EssayEvaluationMapper.xml
  - web/src/pages/app/WritingPage.vue
  - web/src/api/writing.ts
---

# 写作 Dashboard 数据口径

写作总览和成长激励使用 `GET /api/writing/dashboard`，默认参数为 `range=30d`、`mode=all`，分数口径固定为每篇作文的最新评分。

## 数据来源

- `documents`：限定当前用户作文，过滤已删除作文，并通过 `task_prompt` 判断写作模式。
- `document_score_summary`：读取每篇作文最新评分、最佳分、最新字数和最新错误数。
- `essay_evaluation`：读取评分提交历史，用于评分次数、趋势和时间桶统计。

`task_prompt` 为空或空白时记为自由写作，非空时记为考试写作。当前实现不新增数据表，不调用 LLM，也不写入新的 Dashboard 缓存。

## 查询参数

- `range`：`7d`、`14d`、`30d`、`year`、`all`、`custom`，默认 `30d`。
- `mode`：`all`、`free`、`exam`，默认 `all`。
- `start` / `end`：仅 `range=custom` 时生效，格式为 `yyyy-MM-dd`。非法自定义范围会回退到 `30d`。

趋势粒度由后端返回：`7d` 和 `14d` 为日，`30d` 和自定义为周，`year` 为月，`all` 按数据跨度返回月或年。

## 返回结构

响应包含三段：

- `scope`：规范化后的范围、模式、开始结束日期、粒度和 `scorePolicy=latest`。
- `overview`：累计作文、评分次数、平均分、最高分、趋势序列和规则建议。
- `growth`：单篇最新得分趋势、得分分布、分数区间、80 分以上占比、散点图数据、本月目标和连续写作天数。

本月目标当前固定为 3 篇。连续写作按最新评分日期计算，只有完成评分的作文才计入 active day。

## 规则建议

Dashboard 的 `AI建议` 是后端规则生成，不调用外部模型：

- 无评分数据：提示先完成一篇作文评分。
- 评分次数少于 3 次：提示样本偏少。
- 至少 6 篇时比较最近 3 篇和前 3 篇平均分，给出上升或回落建议。
- 80 分以上占比低于 30% 时，提示优先稳定基础表达和段落结构。
- 其他情况提示保持固定练习频率并观察高分占比。

