import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(
  new URL('../src/views/VocabularyView.vue', import.meta.url),
  'utf8',
)

for (const requiredText of [
  '单词学习',
  '今日复习',
  '接口接入状态',
  '已接入',
  '未接入',
  '本地模拟',
  'GET /api/dictionary/lookup',
  'learning_raw_candidate / learning_evidence',
  '来自昨日对话的重点单词',
  '单词详情',
  '词根词缀联想记忆',
  '复习队列',
  '学习成就',
  'innovative',
  '加入今日复习',
]) {
  assert.ok(pageSource.includes(requiredText), `vocabulary learning page should render ${requiredText}`)
}

for (const requiredClass of [
  'learning-shell',
  'metric-card',
  'api-status-panel',
  'api-badge--connected',
  'api-badge--missing',
  'word-table-card',
  'word-detail-panel',
  'study-sidebar',
]) {
  assert.ok(pageSource.includes(requiredClass), `vocabulary learning page should include ${requiredClass}`)
}

assert.ok(
  pageSource.includes('lookupDictionary'),
  'learning page should keep dictionary lookup integration for the top search',
)

console.log('vocabulary-learning-page-ok')
