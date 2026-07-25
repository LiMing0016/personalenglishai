import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  return readFileSync(new URL(path, import.meta.url), 'utf8')
}

const api = readSource('../src/api/vocabulary.ts')
const renderer = readSource('../src/components/vocabulary/VocabularyCardBlocks.vue')
const editor = readSource('../src/components/vocabulary/VocabularyCardBlocksEditor.vue')
const inspector = readSource('../src/components/vocabulary/VocabularyCardInspector.vue')

test('API mirrors Core 2 and every Card Blocks 1 variant while retaining legacy markdown', () => {
  for (const token of [
    "schemaVersion: 1 | 2",
    'id?: string',
    "type: 'exampleList'",
    "type: 'collocationList'",
    "type: 'usageBoundary'",
    "type: 'contrastTable'",
    "type: 'memoryTip'",
    "type: 'note'",
    "type: 'legacyMarkdown'",
    'cardBlocks: VocabularyCardBlocks | null',
    'cardBlocksSchemaVersion: number | null',
    'cardBlocks?: VocabularyCardBlocks | null',
  ]) {
    assert.ok(api.includes(token), `vocabulary API should include ${token}`)
  }
})

test('renderer covers all block variants without repeating lexical core data', () => {
  for (const type of [
    'exampleList',
    'collocationList',
    'usageBoundary',
    'contrastTable',
    'memoryTip',
    'note',
    'legacyMarkdown',
  ]) {
    assert.match(renderer, new RegExp(type))
  }
  assert.match(renderer, /VocabularyMarkdownRenderer/)
  assert.match(renderer, /Copy/)
  assert.match(renderer, /sections-change/)
  assert.match(renderer, /level:\s*2/)
  assert.doesNotMatch(renderer, /VocabularyCoreSummary|definitionEn|definitionZh|phonetics/)
})

test('editor updates typed content, ordering, deletion, and Markdown note insertion', () => {
  assert.match(editor, /defineModel<VocabularyCardBlocks>/)
  assert.match(editor, /moveBlock/)
  assert.match(editor, /removeBlock/)
  assert.match(editor, /addNote/)
  assert.match(editor, /userEdited:\s*true/)
  assert.match(editor, /locked:\s*true/)
  assert.match(editor, /source:\s*'user'/)
  for (const field of [
    'sentence',
    'translation',
    'expression',
    'useWhen',
    'avoidWhen',
    'typicalContext',
    'points',
    'markdown',
  ]) {
    assert.match(editor, new RegExp(field))
  }
  assert.doesNotMatch(editor, /localStorage|sessionStorage|useStorage/)
})

test('inspector keeps blocks for reading and converts every edit into one Markdown document', () => {
  assert.match(inspector, /VocabularyCardBlocks/)
  assert.match(inspector, /vocabularyCardBlocksToMarkdown/)
  assert.match(inspector, /VocabularyMarkdownEditor/)
  assert.match(inspector, /markdown:\s*editMarkdown\.value/)
  assert.doesNotMatch(inspector, /VocabularyCardBlocksEditor/)
  assert.doesNotMatch(inspector, /editCardBlocks/)
})
