import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

import { projectLegacyVocabularyCore } from '../src/composables/useVocabularyCards.ts'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const coreSummarySource = readSource('../src/components/vocabulary/VocabularyCoreSummary.vue')
const markdownEditorSource = readSource('../src/components/vocabulary/VocabularyMarkdownEditor.vue')
const cardsComposableSource = readSource('../src/composables/useVocabularyCards.ts')

test('core summary renders every phonetic and bilingual meaning from typed core only', () => {
  assert.match(coreSummarySource, /core\.term/)
  assert.match(coreSummarySource, /v-for="phonetic in core\.phonetics"/)
  assert.match(coreSummarySource, /phonetic\.region/)
  assert.match(coreSummarySource, /phonetic\.text/)
  assert.match(coreSummarySource, /v-for="(?:\(sense, senseIndex\)|sense) in core\.senses"/)
  assert.match(coreSummarySource, /sense\.partOfSpeech/)
  assert.match(coreSummarySource, /meaning\.definitionEn/)
  assert.match(coreSummarySource, /meaning\.definitionZh/)
  assert.doesNotMatch(coreSummarySource, /core\.content|core\.definitions|core\.phonetic\b/)
})

test('core summary has neutral states for empty phonetics senses and meanings', () => {
  assert.match(coreSummarySource, /暂无音标/)
  assert.match(coreSummarySource, /暂无释义/)
  assert.match(coreSummarySource, /暂无双语释义/)
})

test('markdown editor preserves source in a bounded textarea without rendering HTML', () => {
  assert.match(markdownEditorSource, /<textarea/)
  assert.match(markdownEditorSource, /Markdown 内容/)
  assert.match(markdownEditorSource, /maxlength="20000"/)
  assert.match(markdownEditorSource, /20,000/)
  assert.match(markdownEditorSource, /modelValue\.length/)
  assert.match(markdownEditorSource, /emit\('update:modelValue', input\.value\)/)
  assert.match(markdownEditorSource, /超过 20,000 字限制/)
  assert.doesNotMatch(markdownEditorSource, /v-html|contenteditable|\.trim\(|markdown-it|marked/)
})

test('legacy content is projected through one pure typed core adapter', () => {
  assert.match(cardsComposableSource, /export function projectLegacyVocabularyCore/)
  assert.match(cardsComposableSource, /schemaVersion:\s*1/)
  assert.match(cardsComposableSource, /phonetics:/)
  assert.match(cardsComposableSource, /senses:/)
  assert.match(cardsComposableSource, /definitionEn:/)
  assert.match(cardsComposableSource, /definitionZh:/)

  assert.deepEqual(projectLegacyVocabularyCore('record', {
    term: 'changed-by-ai',
    phonetic: '/rekord/',
    partOfSpeech: 'verb',
    definitions: ['记录', 'to store information'],
  }), {
    schemaVersion: 1,
    term: 'record',
    phonetics: [{ region: 'other', text: '/rekord/', audioUrl: null }],
    senses: [{
      partOfSpeech: 'verb',
      meanings: [
        { definitionEn: '', definitionZh: '记录' },
        { definitionEn: 'to store information', definitionZh: '' },
      ],
    }],
  })

  assert.equal(projectLegacyVocabularyCore('record', null), null)
})
