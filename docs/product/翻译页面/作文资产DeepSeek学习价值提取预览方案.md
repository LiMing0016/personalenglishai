---
title: 作文资产 DeepSeek 学习价值提取预览方案
status: draft
owner: product
last_updated: 2026-05-25
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/writing/WritingDocumentAssetService.java
  - backend/src/main/java/com/personalenglishai/backend/service/learning/LearningDeepseekCleaningService.java
  - web/src/components/personal-center/WritingAssetsSection.vue
related_docs:
  - ./index.md
  - ../../data/writing-document-assets.md
  - ../../agent/数据清洗/对话词句采集清洗方案.md
---

# 作文资产 DeepSeek 学习价值提取预览方案

## 当前结论

第一版先不把抽取结果放入单词页，也不直接进入复习队列。目标是在“作文资产详情”里增加一个“DeepSeek 学习资产预览”区块，让用户先看到 DeepSeek 从自己这篇作文和写作教练对话里提取出了哪些有学习价值的内容。

核心判断标准不是“高级词越多越好”，而是这条内容是否能让用户明确感受到：

- 这是我作文里真实出现过的问题或表达。
- 这是我在写作教练对话里问过、纠结过或被纠正过的点。
- 这个内容下次写作文时可以复用，或者可以避免我再次犯同类错误。

## 当前落地状态

MVP 已按预览链路实现：

- 后端新增 `POST /api/writing/documents/{docId}/asset/learning-preview/refresh`，只允许作文 owner 触发。
- 后端新增 `writing_learning_asset_preview_run` 和 `writing_learning_asset_preview_item`，保存 DeepSeek 提取运行和预览条目。
- 资产详情接口 `GET /api/writing/documents/{docId}/asset` 返回最新 `learningAssetPreview`。
- 个人中心“作文资产”详情增加“DeepSeek 学习资产预览”区块，支持手动提取并查看结果。

当前仍不把预览项写入正式单词库、句子库或复习队列。后续要进入正式学习库时，应增加用户确认、去重、质量阈值和 `promotion_status` 晋级流程。

## 产品目标

把作文资产从“存档材料”升级为“个人写作成长资产”。用户归档一篇作文后，不仅能看到正文、评分和教练对话，还能看到系统整理出的学习价值点。

第一版要解决的问题：

- 用户不知道一篇作文里到底有哪些值得复习的内容。
- 写作教练对话很长，用户事后很难复盘重点。
- 普通单词表太泛，不能体现“这是我自己的问题”。
- 高级表达如果脱离用户原句，用户很难感受到价值。

## 范围与非范围

### 本次范围

- 从已归档作文资产中生成学习资产预览。
- 结合作文正文、评分摘要和写作教练完整对话。
- 按用户提问内容分类提取：单词、短语、句子、语法、写作策略。
- 在作文资产详情中展示预览结果。
- 支持重新提取，用于观察 DeepSeek 输出质量。

### 暂不做

- 暂不接入 `/app/vocabulary` 单词页。
- 暂不进入正式复习队列。
- 暂不新增独立句子页。
- 暂不让用户编辑、收藏或标记掌握这些预览项。
- 暂不把 DeepSeek 输出直接当成稳定学习数据源。

## 抽取原则

### 1. 用户提问驱动为主

DeepSeek 第一件事不是抽词，而是判断用户在写作教练对话里问了什么。

用户问单词时，生成单词卡；用户问句子时，生成句子卡；用户问语法时，生成语法卡；用户问短语时，生成短语卡；用户问如何展开或是否跑题时，生成写作策略卡。

### 2. 少量主动补充为辅

如果用户没有明确问某个点，但作文或教练回复里有明显值得沉淀的内容，可以少量主动提取。

主动提取只保留高价值项，例如：

- 明显影响得分的基础错误。
- 可迁移复用的高级表达。
- 写作教练重点讲过但用户没有明确追问的内容。
- 和本篇作文主题强相关的句型或策略。

主动提取项必须标记为 `system_discovered`，用户提问项标记为 `user_focus`。

### 3. 每条资产必须解释用户价值

每条卡片必须回答：

- 用户当时问了什么？
- 原表达或原问题是什么？
- 推荐表达或知识点是什么？
- 为什么这个内容对这个用户值得保存？
- 下次写作时怎么复用？

如果 DeepSeek 不能给出明确的 `valueReasonForUser`，该项不进入预览。

## 学习资产类型

### 单词卡 `word`

适用场景：

- 用户问某个单词是什么意思、怎么用、是否自然。
- 作文中某个词用错、词性不对或重复低阶。
- 教练明确解释过某个关键词。

示例：

```json
{
  "assetType": "word",
  "sourceType": "user_focus",
  "sourceQuestion": "这个单词在这里是什么意思？",
  "originalText": "flexibility",
  "recommendedText": "flexibility",
  "explanation": "灵活性，常用于说明 online learning 的优势。",
  "valueReasonForUser": "用户正在写 online learning 的优点段，这个词可以准确概括核心优势。",
  "howToReuse": "可用于 One major advantage of online learning is flexibility.",
  "reviewPrompt": "用 flexibility 写一句关于 online learning 的优点句。",
  "sourceExcerpt": "One major advantage of online learning is flexibility.",
  "confidence": 0.9
}
```

### 短语卡 `phrase`

适用场景：

- 用户问短语、搭配或表达是否地道。
- 原文表达偏口语或低阶，教练给出更正式表达。
- 可作为考试作文常用表达复用。

示例：

```json
{
  "assetType": "phrase",
  "sourceType": "system_discovered",
  "sourceQuestion": "",
  "originalText": "more and more popular",
  "recommendedText": "increasingly popular",
  "explanation": "increasingly popular 更正式，适合考试作文描述趋势。",
  "valueReasonForUser": "用户原文开头使用 more and more popular，可替换为更自然正式的表达。",
  "howToReuse": "用于描述社会现象、学习方式、技术趋势等话题。",
  "reviewPrompt": "用 increasingly popular 改写一句描述社会趋势的句子。",
  "sourceExcerpt": "online learning become more and more popular",
  "confidence": 0.86
}
```

### 句子卡 `sentence`

适用场景：

- 用户问某个句子能不能放进正文。
- 教练改写过用户原句。
- 某个句子适合下次作文复用。

示例：

```json
{
  "assetType": "sentence",
  "sourceType": "user_focus",
  "sourceQuestion": "这句话能不能放进正文？",
  "originalText": "Students can choose the time what they want to study.",
  "recommendedText": "Students can choose when to study and watch the lessons repeatedly.",
  "explanation": "改写后语法更自然，也更适合作为 online learning 优点段的细节句。",
  "valueReasonForUser": "用户询问该句是否可用，教练确认可作为正文支撑句。",
  "howToReuse": "用于说明在线学习的灵活性。",
  "reviewPrompt": "仿写一句说明 online learning 灵活性的句子。",
  "sourceExcerpt": "Students can choose when to study and watch the lessons repeatedly.",
  "confidence": 0.92
}
```

### 语法卡 `grammar`

适用场景：

- 用户问为什么要这样改。
- 作文里出现明确语法错误。
- 教练指出了可复用的语法规则。

示例：

```json
{
  "assetType": "grammar",
  "sourceType": "user_focus",
  "sourceQuestion": "帮我看看这句话怎么改。",
  "originalText": "online learning become",
  "recommendedText": "online learning has become",
  "explanation": "online learning 是单数概念，谓语需要使用 has become 或 becomes。",
  "valueReasonForUser": "用户在开头句中出现主谓一致问题，这是考试作文高频扣分点。",
  "howToReuse": "描述趋势时可使用 X has become increasingly popular。",
  "reviewPrompt": "用 has become increasingly popular 写一句关于 AI learning 的句子。",
  "sourceExcerpt": "Nowadays, online learning become more and more popular.",
  "confidence": 0.95
}
```

### 写作策略卡 `writing_strategy`

适用场景：

- 用户问如何展开、是否跑题、能不能放正文。
- 教练解释了段落功能、支撑逻辑或审题策略。
- 内容不适合进入单词或句子库，但适合留在作文资产中复盘。

示例：

```json
{
  "assetType": "writing_strategy",
  "sourceType": "user_focus",
  "sourceQuestion": "这句话可以放进正文吗？",
  "originalText": "Students can choose when to study...",
  "recommendedText": "可以放在 online learning 优点段，用来支撑 flexibility。",
  "explanation": "该句适合作为主体段细节，不适合作为独立观点句。",
  "valueReasonForUser": "用户需要判断句子在段落中的位置，这能帮助用户形成段落组织意识。",
  "howToReuse": "先写观点句，再放具体例子或解释句。",
  "reviewPrompt": "给一个观点句补充一条具体支撑句。",
  "sourceExcerpt": "One major advantage of online learning is flexibility.",
  "confidence": 0.88
}
```

## DeepSeek 输入设计

不要只传整段 Markdown。后端应组装结构化输入，降低模型误判。

建议输入结构：

```json
{
  "document": {
    "title": "考研英语一小作文 2025",
    "taskPrompt": "题目要求...",
    "content": "作文正文..."
  },
  "evaluationSummary": {
    "latestScore": 34,
    "band": "偏低",
    "vocabularyScore": 60,
    "grammarScore": 42,
    "errorCount": 3
  },
  "coachDialogues": [
    {
      "conversationId": "conv-xxx",
      "turns": [
        {
          "userQuestion": "这句话能不能放进正文？",
          "assistantAnswer": "可以，建议放在优点段..."
        }
      ]
    }
  ],
  "requirements": {
    "maxUserAskedItems": 12,
    "maxSystemDiscoveredItems": 5,
    "mustExplainUserValue": true
  }
}
```

## DeepSeek 输出格式

输出必须是严格 JSON：

```json
{
  "assets": [
    {
      "assetType": "word",
      "sourceType": "user_focus",
      "sourceQuestion": "",
      "originalText": "",
      "recommendedText": "",
      "explanation": "",
      "valueReasonForUser": "",
      "howToReuse": "",
      "reviewPrompt": "",
      "sourceExcerpt": "",
      "confidence": 0.0
    }
  ],
  "summary": {
    "userAskedCount": 0,
    "systemDiscoveredCount": 0,
    "mainLearningValue": ""
  }
}
```

过滤规则：

- `assetType` 不在允许范围内则丢弃。
- `valueReasonForUser` 为空则丢弃。
- `confidence < 0.65` 的系统主动发现项丢弃。
- 完全重复的 `recommendedText` 只保留最高置信度版本。
- 不保存普通功能词、无上下文短词、整段废话。

## 前端展示设计

位置：个人中心 > 作文资产 > 资产详情。

新增区块：`DeepSeek 学习资产预览`。

展示方式：

- 顶部显示摘要：提取了多少条、用户提问驱动多少条、系统主动发现多少条。
- 分组展示：单词、短语、句子、语法、写作策略。
- 每张卡展示：
  - 类型标签。
  - 原表达。
  - 推荐表达或知识点。
  - 用户当时的问题。
  - 为什么值得保存。
  - 下次如何复用。
  - 复习提示。

操作：

- `重新提取学习资产`：重新调用 DeepSeek，覆盖当前预览。
- 第一版不做“加入单词本”“加入复习”“标记掌握”。

空状态：

- 没有写作教练对话时：提示“暂无可用于学习资产提取的写作教练对话，可先在写作页向教练提问。”
- DeepSeek 没提取出内容时：提示“本次未发现足够明确的学习资产。”
- 抽取失败时：展示失败提示，并允许重试。

## 数据保存策略

第一版建议把学习资产预览保存在作文资产快照 JSON 中，不进入正式学习词库。

建议在 `writing_document_asset_snapshot.snapshot_json` 中增加：

```json
{
  "learningAssetPreview": {
    "status": "completed",
    "generatedAt": "2026-05-25T10:00:00",
    "model": "deepseek-chat",
    "assets": [],
    "summary": {}
  }
}
```

这样可以做到：

- 和当前作文资产快照生命周期一致。
- 不污染单词页和复习队列。
- 方便先观察 DeepSeek 提取质量。
- 后续如果要正式入库，可以再设计“确认加入学习资产库”的流程。

## 验收标准

- 归档或刷新作文资产后，资产详情能看到学习资产预览区块。
- DeepSeek 能根据用户问单词、短语、句子、语法分别生成对应类型卡片。
- 每张卡都有 `valueReasonForUser`。
- 系统主动发现项数量受限，且标记为 `system_discovered`。
- 抽取结果不进入 `/app/vocabulary`。
- DeepSeek 失败不影响作文资产正常展示。
- 用户只能查看自己作文的学习资产预览。

## 后续方向

当预览质量稳定后，再考虑第二阶段：

- 用户手动选择“加入单词页”。
- 句子和句型进入独立句子库。
- 语法卡进入个人语法问题库。
- 写作策略卡保留在作文资产详情中。
- 将高频重复资产加入复习队列。
