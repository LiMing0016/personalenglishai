import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(
  new URL('../src/views/VocabularyView.vue', import.meta.url),
  'utf8',
)

for (const requiredText of [
  '词启',
  'Vocabulary',
  '搜索单词',
  '背词模式',
  '单词沉淀',
  '学习统计',
  '查询、学习、一步到位',
  '选择适合你的学习模式',
  '单词卡中心',
  '从 PDF、AI 对话、笔记和错题中整理的单词会自动回到这里',
  '全部来源',
  '今天',
  '本周',
  'A-Z',
  '最近沉淀',
  '每日沉淀',
  '我的笔记',
  '来源',
  '加入背词',
  '整理单词卡',
  '退出整理',
  '单词导航',
  '整理工作区',
  '编辑单词名称',
  '播放发音',
  '开始复习',
  '详情',
  '搭配',
  '例句',
  '易混',
  '选择模板',
  '模板库',
  '模板驱动',
  '官方模板',
  '我的模板',
  '模板广场',
  '适用阶段',
  '适用场景',
  '复制后自定义',
  '当前模板',
  '考试全景模板',
  '词义拓展模板',
  '阅读语境模板',
  '搭配表达模板',
  '基础单词卡',
  '考试阅读卡',
  '学术/专业词卡',
  'AI 整理',
  'AI 优化',
  '复习',
  '已自动保存',
  '学习趋势（近 7 天）',
  '单词详情',
  '词根联想',
  '今日学习计划',
  '连续学习日历',
  'innovative',
  '加入今日复习',
]) {
  assert.ok(pageSource.includes(requiredText), `vocabulary learning page should render ${requiredText}`)
}

for (const requiredClass of [
  'vocabulary-shell',
  'vocabulary-topbar',
  'vocabulary-nav',
  'search-page',
  'mode-page',
  'collection-page',
  'stats-page',
  'word-preview-card',
  'word-card-list',
  'word-card-row',
  'word-source-stack',
  'word-card-inspector',
  'word-card-workspace',
  'word-deposit-page--organizing',
  'word-navigation-panel',
  'word-title-editor',
  'word-study-hero',
  'word-study-hero__content',
  'word-study-hero__visual',
  'word-study-tabs',
  'word-template-picker',
  'word-template-modal-backdrop',
  'word-template-modal-panel',
  'word-template-trigger',
  'word-template-library-tabs',
  'word-template-card',
  'word-template-meta',
  'word-template-fields',
  'word-template-square-entry',
  'word-template-layout-badge',
  'word-card-canvas',
  'word-note-stack',
  'word-note-line',
  'word-card-bottom-actions',
  'template-field-grid',
  'template-field-editor',
  'ai-template-action',
  'word-detail-tags',
  'review-chip-card',
  'daily-deposit-card',
  'user-note-editor',
  'review-plan-switch',
  'search-detail-section',
  'dictionary-detail-card',
]) {
  assert.ok(pageSource.includes(requiredClass), `vocabulary learning page should include ${requiredClass}`)
}

assert.ok(
  !pageSource.includes('单词端数据源验收标记'),
  'vocabulary learning page should not expose backend integration status as student-facing content',
)

assert.ok(
  !pageSource.includes('<h3>掌握程度</h3>'),
  'word-card organizing page should not show mastery controls in the first version',
)

assert.ok(
  !pageSource.includes('<h3>复习设置</h3>'),
  'word-card organizing page should collapse review settings into a lightweight tag',
)

assert.ok(
  pageSource.includes('lookupDictionary'),
  'learning page should keep dictionary lookup integration for the top search',
)

assert.ok(
  pageSource.includes('buildWordCard'),
  'vocabulary learning page should adapt collected words into word cards',
)

assert.ok(
  pageSource.includes('dedupeWordCards'),
  'vocabulary learning page should merge duplicate word cards by word',
)

assert.ok(
  pageSource.includes('key: normalizeWordKey'),
  'vocabulary learning page should name each word card by normalized word key',
)

assert.ok(
  pageSource.includes('toggleReviewPlan'),
  'vocabulary learning page should let users add word cards to the review plan',
)

assert.ok(
  pageSource.includes('startWordCardOrganizing'),
  'vocabulary learning page should switch from preview to a word-card organizing workspace',
)

assert.ok(
  pageSource.includes('VocabularyWordCard'),
  'vocabulary word-card organizing workspace should be route addressable',
)

assert.ok(
  pageSource.includes('wordCardTemplates'),
  'word-card organizing workspace should provide system templates',
)

assert.ok(
  pageSource.includes('const showWordTemplatePicker = ref(false)'),
  'word-card organizing page should not show the template library inline by default',
)

assert.ok(
  pageSource.includes('selectWordTemplate'),
  'word-card organizing page should apply a template from the template picker',
)

assert.ok(
  pageSource.includes('audience:'),
  'word-card templates should encode target learner age/stage',
)

assert.ok(
  pageSource.includes('scenes:'),
  'word-card templates should encode learning demand scenarios',
)

assert.ok(
  pageSource.includes('layout:'),
  'word-card templates should drive the card learning layout',
)

assert.ok(
  pageSource.includes('source:'),
  'word-card templates should distinguish official, personal, and shared templates',
)

assert.ok(
  pageSource.includes('applyAiTemplateToWordCard'),
  'word-card organizing workspace should let AI fill the selected template',
)

console.log('vocabulary-learning-page-ok')
