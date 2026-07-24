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

test('core summary renders canonical phonetics and bilingual meaning from typed core only', () => {
  assert.match(coreSummarySource, /core\.term/)
  assert.match(coreSummarySource, /displayPhonetics/)
  assert.match(coreSummarySource, /v-for="phonetic in displayPhonetics"/)
  assert.doesNotMatch(coreSummarySource, /v-for="phonetic in core\.phonetics"/)
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

test('core summary exposes accessible pronunciation controls for every phonetic', () => {
  assert.match(coreSummarySource, /import\s*\{\s*Volume2\s*\}\s*from\s*'lucide-vue-next'/)
  assert.match(coreSummarySource, /defineEmits<\{\s*pronounce:/)
  assert.match(coreSummarySource, /class="core-summary__phonetic-button"/)
  assert.match(coreSummarySource, /:aria-label="`播放\$\{regionLabel\(phonetic\.region\)\}式发音：\$\{phonetic\.text\}`"/)
  assert.match(coreSummarySource, /@click="emit\('pronounce', phonetic\)"/)
  assert.match(coreSummarySource, /<Volume2\s+aria-hidden="true"\s*\/>/)
})

test('markdown editor provides a Typora-style visual editor with source repair mode and outline', () => {
  assert.match(markdownEditorSource, /@tiptap\/markdown/)
  assert.match(markdownEditorSource, /EditorContent/)
  assert.match(markdownEditorSource, /BubbleMenu/)
  assert.match(markdownEditorSource, /contentType:\s*['"]markdown['"]/)
  assert.match(markdownEditorSource, /getMarkdown\(\)/)
  assert.match(markdownEditorSource, /<textarea/)
  assert.match(markdownEditorSource, /高级源码/)
  assert.match(markdownEditorSource, /学习内容目录/)
  assert.match(markdownEditorSource, /buildVocabularyMarkdownOutline/)
  assert.match(markdownEditorSource, /outline\.value\.findIndex/)
  assert.match(markdownEditorSource, /querySelectorAll<HTMLElement>\(['"]h2['"]\)/)
  assert.doesNotMatch(markdownEditorSource, /querySelector<HTMLElement>\(`#\$\{id\}`\)/)
  assert.match(markdownEditorSource, /grid-template-columns:\s*minmax\(0,\s*820px\)\s+200px/)
  assert.match(markdownEditorSource, /position:\s*sticky/)
  assert.match(markdownEditorSource, /@media \(max-width:\s*1023px\)/)
  assert.match(markdownEditorSource, /maxlength="20000"/)
  assert.match(markdownEditorSource, /20,000/)
  assert.match(markdownEditorSource, /modelValue\.length/)
  assert.match(markdownEditorSource, /emit\('update:modelValue', input\.value\)/)
  assert.match(markdownEditorSource, /if \(sourceMode\.value\) return/)
  assert.match(markdownEditorSource, /超过 20,000 字限制/)
  assert.doesNotMatch(markdownEditorSource, /TableKit/)
  assert.doesNotMatch(markdownEditorSource, /VocabularyMarkdownRenderer|实时预览|grid-template-columns:\s*minmax\(0,\s*1fr\)\s+minmax\(0,\s*1fr\)/)
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
    definitions: ['a written account；记录', 'to store information'],
  }), {
    schemaVersion: 1,
    term: 'record',
    phonetics: [{ region: 'other', text: '/rekord/', audioUrl: null }],
    senses: [{
      partOfSpeech: 'verb',
      meanings: [
        { definitionEn: 'a written account', definitionZh: '记录' },
        { definitionEn: 'to store information', definitionZh: '' },
      ],
    }],
  })

  assert.equal(projectLegacyVocabularyCore('record', null), null)
})

test('legacy projection matches backend bounds and ignores unknown definition shapes', () => {
  const definitions = [
    'english；中文',
    ...Array.from({ length: 34 }, (_, index) => `definition ${index}`),
    { definition: 'must not be guessed' },
    '   ',
  ]
  const projected = projectLegacyVocabularyCore('record', {
    phonetic: 'p'.repeat(2_100),
    partOfSpeech: `  ${'N'.repeat(2_100)}  `,
    definitions,
  })!

  assert.equal(projected.phonetics[0]?.text.length, 2_000)
  assert.equal(projected.senses[0]?.partOfSpeech.length, 2_000)
  assert.equal(projected.senses[0]?.partOfSpeech, 'n'.repeat(2_000))
  assert.equal(projected.senses[0]?.meanings.length, 30)
  assert.deepEqual(projected.senses[0]?.meanings[0], {
    definitionEn: 'english',
    definitionZh: '中文',
  })
  assert.ok(projected.senses[0]?.meanings.every((meaning) => (
    meaning.definitionEn.length <= 2_000 && meaning.definitionZh.length <= 2_000
  )))
})

test('legacy projection uses unknown part of speech and does not accept scalar definitions', () => {
  assert.deepEqual(projectLegacyVocabularyCore('record', { definitions: [] })?.senses, [{
    partOfSpeech: 'unknown',
    meanings: [],
  }])
  assert.deepEqual(projectLegacyVocabularyCore('record', {
    definitions: ['definition'],
    partOfSpeech: '   ',
  })?.senses[0], {
    partOfSpeech: 'unknown',
    meanings: [{ definitionEn: 'definition', definitionZh: '' }],
  })
  assert.deepEqual(projectLegacyVocabularyCore('record', { definitions: 'not-an-array' })?.senses, [])
})
