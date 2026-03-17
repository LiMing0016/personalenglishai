# 润色功能规格（当前阶段）

## 摘要

当前阶段的润色功能只做一件事：

**让润色和评分严格使用同一套 rubric、同一套题目上下文、同一套评分逻辑。**

固定链路：

1. 用户写作文
2. 选择润色档位
3. 系统按当前 rubric 生成 1 个候选稿
4. 系统用同一评分器做 1 次安全复评
5. 若候选稿不差于原文，则允许展示与替换；若更差，则保留原文

当前阶段已移除：

- plan / 教练流
- 定制聊天式润色
- 自动多轮重试
- 自动闭环提分

## 核心原则

### 1. 润色与评分共用同一套上下文

必须共享：

- `studyStage`
- `writingMode`
- `taskType`
- `topicContent`
- `taskPrompt`
- `minWords`
- `recommendedMaxWords`
- `rubricKey`

题目来源固定：

- 图画作文：`topicContent = image_description`
- 材料作文：`topicContent = material_text`
- 任务型/书信类：`topicContent = prompt_text`
- `taskPrompt` 只表示写作动作要求

### 2. 润色档位只绑定目标 Band

润色层不再维护第二套分数区间，只复用 `scoring rubric` 中已有的总分档位区间。

当前默认目标：

- `basic` -> `Band 3`
- `steady` -> `Band 4`
- `advanced` -> `Band 4` 高位 / 接近 `Band 5`
- `perfect` -> `Band 5`

实现上只保存目标 Band，不再使用“60 分 / 90 分”这类额外规则。

### 3. 候选稿必须通过同 rubric 复评

润色候选稿生成后，必须再走一次同一评分器。

若出现以下任一回退，则不允许替换正文：

- 总分下降
- Band 下降
- 核心维度下降
- `exam` 下 `relevance / taskCompletion / coverage` 下降

前端必须明确提示：

`候选稿未通过安全复评，已保留原文。`

## 当前产品形态

### 自动润色

当前仅保留自动润色主流程：

- 选择档位
- 生成候选稿
- 安全复评
- 展示结果或回退原文

面板仅展示：

- 当前档位
- 处理路线
- baseline / final band
- 候选稿是否通过安全复评
- 若失败，明确说明已回退原文

### 范文

右侧原 `结构` 面板已替换为 `范文` 面板，作为独立学习能力存在，不参与润色兜底。

固定返回两张学习卡：

- `优秀作文`
- `满分作文`

每张学习卡包含：

- 范文正文
- `高分原因`
- `你该怎么做`

## 接口

### 自动润色

`POST /api/writing/polish-essay`

请求最少包含：

- `text`
- `tier`
- `studyStage`
- `writingMode`
- `taskType`
- `topicContent`
- `taskPrompt`
- `minWords`
- `recommendedMaxWords`

不再接收 plan / 教练字段。

### 范文

`POST /api/writing/model-essay`

固定返回：

- `excellentEssay`
- `perfectEssay`

以及每篇对应的：

- `highScoreReasons`
- `improvementGuidance`

## 默认假设

- 当前阶段重点是“评分与润色对齐”，不是“聊天体验升级”。
- `scoring rubric` 已包含 band 区间，润色层不再维护第二套分数体系。
- 后续新增学段时，只切换：
  - `scoring rubric`
  - `prompt 模板`
  - `model adapter`
- 在这套一致性稳定之前，不重新引入 plan、教练、多轮自动优化。
