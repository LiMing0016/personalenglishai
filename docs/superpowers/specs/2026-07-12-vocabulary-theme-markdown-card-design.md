---
title: 单词主题与 Markdown 单词卡设计
status: draft
owner: product
last_updated: 2026-07-12
review_cycle: on-change
related_code:
  - web/src/views/VocabularyView.vue
  - web/src/components/vocabulary/
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql
related_docs:
  - docs/superpowers/specs/2026-07-10-vocabulary-deposition-core-design.md
  - docs/architecture/vocabulary-deposition.md
---

# 单词主题与 Markdown 单词卡设计

## 当前结论

在现有单词沉淀内核上新增“单词主题（Theme）”。主题不是视觉皮肤，而是一套可版本化的 AI 生成上下文。用户沉淀单词前选择主题，后端使用主题对应的 Prompt 生成单词卡。

第一期采用“统一核心 JSON + 主题扩展 Markdown”的内容模型。单词、音标、词性和释义保持统一结构，供列表、搜索、学习统计和后续组件化渲染使用；主题特色内容使用 Markdown，先验证内容质量，暂不建设复杂的卡片渲染器。

主题入口采用组合式布局：单词卡中心展示默认主题和最近使用主题，完整的创建、编辑和管理操作进入独立主题库。系统提供基础学习、考试突破和阅读精析三个不可编辑的内置主题；用户可以创建自己的主题，第一期只定义名称和用途说明，自定义字段、章节编辑器和完整 Prompt 编辑器后续再做。

本设计扩展并修正《单词沉淀内核设计》中“用户创建模板”为非目标的旧范围。沉淀、去重、来源、异步任务、卡片版本和冲突规则继续沿用现有内核。

## 背景与问题

当前实现把 `basic`、`exam`、`reading` 写死为模板键，并要求 AI 返回字段集合完全一致的 JSON。实际运行中，AI 请求可以成功，但模型输出只要多一个字段、少一个字段或字段类型不同，整张卡就会以 `INVALID_AI_OUTPUT` 失败。用户最终看到空白卡片和技术错误，而不是可用的基础词典内容。

当前还存在以下产品缺口：

- 用户只能选择三个系统模板，不能定义自己的学习方向。
- 模板只是字段列表，没有独立、可演进的 Prompt 资产和版本身份。
- 卡片核心词典事实与主题扩展内容绑定在同一份严格 JSON 中，任何扩展失败都会拖垮整张卡。
- 详情页直接按固定字段渲染，不适合在内容结构尚未稳定时快速迭代主题。
- 修改模板后的旧卡片如何保持可恢复，没有主题级版本契约。

## 目标与非目标

### 目标

- 建立系统主题和用户主题的持久化资产与版本模型。
- 用户在单词卡中心快速选择默认或最近使用主题。
- 提供独立主题库，支持创建、编辑、复制、设为默认、停用和删除。
- 每张卡和每个卡片版本记录生成时使用的主题及主题版本。
- 将稳定的核心词典数据与自由的主题 Markdown 分离。
- AI 失败时优先保留可用核心卡片，不再因扩展内容失败产生完全空白卡。
- 保持现有沉淀、来源合并、生成任务、编辑冲突和历史版本能力。
- 兼容已有 `basic`、`exam`、`reading` 卡片和旧 `content_json`。

### 非目标

- 用户配置任意卡片字段或拖拽章节顺序。
- 用户直接编辑完整系统 Prompt。
- 主题市场、分享、团队协作和导入导出。
- Markdown 的复杂组件化渲染、交互式练习或卡片皮肤。
- 自动为用户选择主题；第一期由用户明确选择。
- 闪卡复习、间隔重复和学习统计闭环。
- AI 会话、PDF、网页划词等新沉淀来源。

## 领域定义

### 主题

主题描述“这张单词卡为哪种学习目标服务”。它包含稳定身份、显示名称、用途说明、所有者、状态和当前版本。

主题分为：

- `system`：系统内置主题，不允许用户直接编辑、停用或删除，可以复制为用户主题。
- `user`：用户创建的主题，只对本人可见，可以编辑、复制、设为默认、停用和软删除。

### 主题版本

主题修改采用追加版本，不原地覆盖历史配置。主题版本至少固化：

- 主题名称快照。
- 用途说明。
- Prompt 策略键。
- 内容格式版本。
- 创建时间。

第一期用户主题统一继承基础 Markdown 生成骨架，并把用途说明作为受控的主题目标注入 Prompt。系统主题分别使用独立 Prompt 策略。以后增加字段或章节配置时，只扩展新的主题版本，不改变旧卡片语义。

### 卡片内容

每个卡片版本包含两部分：

- `core`：统一 JSON，保存稳定、可查询的词典事实。
- `markdown`：由主题 Prompt 生成的扩展学习内容。

## 用户体验设计

### 单词卡中心

批量录入区在输入框之前展示“主题架”：

- 当前默认主题。
- 最近使用的 2 至 3 个主题。
- “新建主题”入口。
- “管理全部主题”入口。

用户选择主题后，主要按钮显示明确动作，例如 `按「雅思写作」生成 5 张卡片`。临时选择不会自动修改默认主题，但成功沉淀后会更新最近使用顺序。

### 主题库

新增 `/app/vocabulary/themes`：

- 系统主题和我的主题分区展示。
- 支持按名称搜索。
- 系统主题提供“使用”和“复制”操作。
- 用户主题提供“使用、编辑、复制、设为默认、停用、删除”。
- 已被卡片引用的主题删除时只做软删除；历史卡片继续显示主题名称快照和内容。

### 新建与编辑主题

第一期表单保持最小：

- 主题名称：必填、用户内唯一。
- 用途说明：必填，描述学习目标和内容侧重点。

保存后即可用于沉淀。字段选择、章节排序和 Prompt 高级设置不在第一期表单出现。

### 卡片详情

详情页分为：

1. 核心信息：单词、英美音标、按词性分组的中英释义。
2. 主题内容：Markdown 源码。
3. 来源。
4. 历史。

第一期允许编辑并保存 Markdown。可以提供轻量的“源码 / 预览”切换，但不建设主题专属组件。重新生成时明确显示将使用的主题版本；旧卡引用旧主题版本时，用户确认后才使用最新版本生成。

## 核心 JSON 契约

核心数据使用版本化结构：

```json
{
  "schemaVersion": 1,
  "term": "record",
  "phonetics": [
    {
      "region": "uk",
      "text": "/ˈrekɔːd/",
      "audioUrl": null
    },
    {
      "region": "us",
      "text": "/ˈrekərd/",
      "audioUrl": null
    }
  ],
  "senses": [
    {
      "partOfSpeech": "noun",
      "meanings": [
        {
          "definitionEn": "a written account of something",
          "definitionZh": "记录；记载"
        }
      ]
    },
    {
      "partOfSpeech": "verb",
      "meanings": [
        {
          "definitionEn": "to store information for future use",
          "definitionZh": "记录；录制"
        }
      ]
    }
  ]
}
```

约束：

- `term` 必须与卡片身份一致，不能由 AI 或用户修改为其他词。
- `phonetics` 和 `senses` 允许为空数组，但节点和字段类型必须稳定。
- 音标按地区区分，不假设每个词都同时有英式和美式音标。
- 释义必须归属于词性；不使用互不关联的单一词性和扁平释义数组。
- 可靠本地词典数据优先于 AI 内容。
- 外部音频地址只接受允许的绝对 HTTP/HTTPS 地址，并继续经过前端安全 URL 过滤。

## Markdown 契约

Markdown 保存主题扩展内容，不重复承担卡片身份和查询字段。第一期只要求：

- UTF-8 文本。
- 非空时不得超过后端配置的长度上限。
- 禁止原始 HTML 或在展示前进行严格清洗。
- 不把主题 UID、版本、来源等业务元数据写入 frontmatter；这些信息保存在数据库字段中。
- 系统主题使用各自推荐章节，但解析器不依赖章节名称完成业务查询。

基础主题示例：

```markdown
## 常用例句

- Hello, John. How are you?
- Say hello to Liz for me.

## 学习提示

这是最常用的英语问候语之一。
```

## AI 生成流程

### Prompt 资产

系统主题对应独立 Prompt 策略：

- `basic-markdown-v1`：日常释义、常用例句和学习提示。
- `exam-markdown-v1`：考试考义、固定搭配、易错点和真题风格例句。
- `reading-markdown-v1`：语境义、句中作用、同义改写和上下文解释。

用户主题第一期使用 `custom-markdown-v1`。后端把用途说明作为数据注入固定 Prompt，不允许用途说明覆盖安全规则、输出格式或卡片身份。

### 生成步骤

1. 根据卡片规范词形查询共享词典。
2. 将可靠词典结果规范化为核心 JSON。
3. 词典不足以形成最低核心数据时，调用受严格 JSON Schema 约束的 AI 补全核心数据。
4. 将核心 JSON、来源语境、主题用途和主题 Prompt 策略交给 Markdown 生成调用。
5. 校验 Markdown 的存在性、长度和安全边界。
6. 在同一生成收尾事务中保存卡片版本、主题引用和任务结果。

核心 JSON 与 Markdown 分开校验。Markdown 输出不再因章节差异触发“字段集合不一致”。

### 失败与降级

- 词典核心有效、Markdown 成功：卡片为 `ready`。
- 词典核心有效、Markdown 失败：保存核心卡片，状态为 `needs_review`，展示“主题内容待完善”并允许重试。
- 词典核心不足、AI 核心补全成功、Markdown 失败：保存核心卡片并进入 `needs_review`。
- 核心数据无法形成：卡片为 `failed`，保留原始词、来源、任务错误和重试入口。
- 重新生成失败但已有活跃版本：保留旧版本可用，最新任务单独显示失败，不覆盖卡片内容。

错误信息面向用户显示可执行说明；错误码、模型和 trace ID 留在诊断信息和日志中。

## 数据模型

### 新增主题表

`vocabulary_theme` 保存主题身份和生命周期：

- `theme_uid`
- `owner_type`
- `user_id`，系统主题为空
- `name`
- `status`
- `current_version`
- `deleted_at`
- `created_at`
- `updated_at`

`vocabulary_theme_revision` 保存不可变主题版本：

- `revision_uid`
- `theme_uid`
- `version`
- `name_snapshot`
- `purpose`
- `prompt_strategy_key`
- `content_format_version`
- `created_at`

用户主题名称只要求在当前用户的未删除主题内唯一。系统主题使用固定 UID，并通过幂等迁移或启动数据保证存在。

### 用户偏好

扩展现有 `vocabulary_user_preference`：

- `default_theme_uid`
- 最近使用主题列表或等价的最近使用关系

读取偏好时，如果默认主题已停用或删除，回退到系统基础主题。

### 卡片与版本

为卡片和卡片版本增加主题关联。卡片版本至少保存：

- `theme_uid`
- `theme_version`
- `core_json`
- `content_markdown`
- `content_format_version`

卡片当前主题表示下一次默认重新生成使用的主题；历史版本内的主题 UID 和版本永不变化。主题名称快照从主题版本读取，主题软删除不影响历史展示。

## API 设计

### 主题管理

- `GET /api/vocabulary/themes`：列出系统主题、用户主题、默认和最近使用状态。
- `POST /api/vocabulary/themes`：创建用户主题。
- `PUT /api/vocabulary/themes/{themeUid}`：修改主题并创建新版本。
- `POST /api/vocabulary/themes/{themeUid}/copy`：复制为用户主题。
- `POST /api/vocabulary/themes/{themeUid}/default`：设为默认主题。
- `POST /api/vocabulary/themes/{themeUid}/disable`：停用主题。
- `DELETE /api/vocabulary/themes/{themeUid}`：软删除用户主题。

系统主题的修改、停用和删除返回稳定的 403 或业务错误。所有接口只允许访问当前用户拥有的用户主题。

### 捕获与重新生成

`POST /api/vocabulary/captures` 从 `templateKey` 迁移为接受 `themeUid`。兼容期继续接受旧 `templateKey`，映射到固定系统主题；新客户端优先发送 `themeUid`。

`POST /api/vocabulary/cards/{cardUid}/regenerate` 接受：

```json
{
  "themeUid": "theme_user_123",
  "useLatestThemeVersion": true
}
```

无 body 时沿用卡片当前主题。旧 `templateKey` 请求在兼容期继续映射。

### 卡片响应

卡片 summary 从核心 JSON 派生音标和首条核心释义。详情响应增加：

- `theme`
- `themeVersion`
- `core`
- `markdown`
- `contentFormatVersion`

兼容期保留旧 `content`，前端适配器优先读取新结构，缺失时读取旧结构。

## 兼容与迁移

### 系统主题映射

- `basic` -> 基础学习系统主题。
- `exam` -> 考试突破系统主题。
- `reading` -> 阅读精析系统主题。

迁移为现有卡片补充对应的主题 UID 和版本，不删除 `template_key`、`template_version` 或旧 `content_json`。确认所有新旧客户端都迁移后，再单独设计字段下线。

### 旧卡片读取

兼容适配器把旧 JSON 中的 `term`、`phonetic`、`partOfSpeech` 和 `definitions` 投影成核心 JSON；其余字段可以按旧模板转换成只读 Markdown，或继续由旧详情渲染。第一期不批量调用 AI 重写历史卡片。

用户对旧卡片重新生成时，创建新的主题格式版本，旧版本仍保留在历史中。

## 安全与内容边界

- 主题用途说明按不可信用户输入处理，不能成为 system 指令。
- Prompt 明确禁止用途说明改变输出目标、泄露系统提示或请求外部操作。
- Markdown 展示禁止执行脚本、事件属性、任意 iframe 和危险 URL。
- AI 输出中的链接和媒体不直接自动加载；第一期可完全禁用 Markdown 内嵌 HTML。
- 核心 JSON 的 `term` 永远由卡片身份覆盖。
- 用户只能操作自己拥有的主题、卡片和版本。

## 测试与验收

### 后端

- 系统主题幂等创建和固定映射。
- 用户主题 CRUD、所有权、名称唯一、软删除和默认回退。
- 修改主题追加版本，不改变旧版本。
- 捕获与重新生成固化主题 UID 和版本。
- 核心 JSON Schema 校验覆盖多音标、多词性和双语释义。
- 词典核心有效而 Markdown 失败时仍保存可用卡片。
- 旧 `templateKey` 和旧 `content_json` 兼容读取。
- 过期生成任务、冲突和失败重试继续满足现有租约规则。

### 前端

- 主题架显示默认和最近主题，选择后更新沉淀按钮文案。
- 主题库系统/用户分区及全部管理操作。
- 新建和编辑表单的名称、用途校验。
- 详情正确显示核心 JSON 和 Markdown 源码。
- 旧卡片兼容显示，重新生成后切换到新格式。
- 桌面和移动端主题选择、列表、详情无重叠和横向溢出。

### 端到端验收

1. 创建“雅思写作”主题并填写用途说明。
2. 设为默认后在单词卡中心可直接看到并选中。
3. 批量沉淀两个单词，按钮明确显示主题名称。
4. 卡片先可靠保存，随后出现核心 JSON 和主题 Markdown。
5. 修改主题用途产生新版本，旧卡片内容不变化。
6. 使用最新主题版本重新生成，产生新卡片版本并保留旧历史。
7. 模拟 Markdown AI 输出失败，卡片仍展示核心词典内容和重试入口。
8. 打开一个旧 `basic` 卡片，确认兼容展示和升级生成正常。

## 发布与回滚

发布顺序：

1. 先执行只新增表和字段的数据库迁移。
2. 发布支持新旧内容双读和旧 `templateKey` 映射的后端。
3. 发布主题库、主题选择和 Markdown 详情前端。
4. 验证新卡片生成稳定后，再考虑停止前端发送旧 `templateKey`。

回滚时保留新增主题表、关联字段和新版本数据。旧后端继续读取原字段；不得通过删除新表或覆盖版本完成回滚。新格式卡片在旧前端不可完整编辑时，应保持只读或由兼容响应投影，而不是丢弃 Markdown。

## 后续演进

内容质量稳定后，再独立设计：

- 主题字段选择和章节排序。
- Markdown 章节到前端组件的映射。
- 高级 Prompt 编辑和 Prompt 测试台。
- 主题导入导出、分享和市场。
- 按主题组织复习计划和学习统计。

