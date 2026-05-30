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
  '我的收藏',
  '学习统计',
  '查询、学习、一步到位',
  '选择适合你的学习模式',
  '我的收藏 / 生词本',
  '学习趋势（近 7 天）',
  '单词详情',
  '词根词缀联想记忆',
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
  pageSource.includes('lookupDictionary'),
  'learning page should keep dictionary lookup integration for the top search',
)

console.log('vocabulary-learning-page-ok')
