import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  filterTranslations,
  hubQuickActions,
  materialCategories,
  myTranslations,
  noteStats,
  todayRecommendations,
} from '../src/pages/app/translationHubData.ts'

const pageSource = readFileSync(
  new URL('../src/pages/app/TranslationPage.vue', import.meta.url),
  'utf8',
)

assert.ok(hubQuickActions.length >= 3, 'translation hub should show at least three non-duplicated quick actions')
assert.ok(todayRecommendations.length >= 3, 'translation hub should show at least three recommendations')
assert.ok(noteStats.length >= 6, 'translation hub should show a compact six-metric note summary')

for (const category of ['经济学人', '外刊新闻', '学术期刊', '考试材料', '技术文档', '用户导入']) {
  assert.ok(
    materialCategories.some((item) => item.title === category),
    `translation material library should include ${category}`,
  )
}

assert.ok(
  filterTranslations(myTranslations, { filter: 'reading', query: '' }).every((item) => item.status === 'reading'),
  'reading filter should only return reading translations',
)

assert.ok(
  filterTranslations(myTranslations, { filter: 'completed', query: '' }).every((item) => item.status === 'completed'),
  'completed filter should only return completed translations',
)

assert.ok(
  filterTranslations(myTranslations, { filter: 'noted', query: '' }).every((item) => item.noteCount > 0),
  'noted filter should only return translations with notes',
)

assert.ok(
  filterTranslations(myTranslations, { filter: 'exam', query: '' }).every((item) => item.mode === 'exam'),
  'exam filter should only return exam translation records',
)

const economistMatches = filterTranslations(myTranslations, { filter: 'all', query: 'Economist' })
assert.ok(economistMatches.length > 0, 'keyword search should match English titles and descriptions')
assert.ok(
  economistMatches.every((item) => `${item.title} ${item.subtitle} ${item.sourceLabel}`.toLowerCase().includes('economist')),
  'keyword search should filter by title, subtitle, or source label',
)

for (const requiredCopy of [
  '翻译中心',
  '找到素材，继续阅读，整理你的双语学习笔记',
  '学习入口',
  '素材库',
  '我的翻译',
  '今日推荐',
  '我的笔记摘要',
  '新建翻译',
]) {
  assert.ok(pageSource.includes(requiredCopy), `translation hub page should render ${requiredCopy}`)
}

assert.ok(!pageSource.includes('id="continue-title"'), 'translation hub should not duplicate history as a continue-learning section')
assert.ok(!pageSource.includes('continue-grid'), 'translation hub should not render recent translation cards above my translations')
assert.ok(!pageSource.includes('translateEssay'), 'translation hub should not call the writing translation API')
assert.ok(!pageSource.includes('翻译练习'), 'translation hub should replace the old translation practice hero')

console.log('translation-hub-data-ok')
