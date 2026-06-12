import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const apiSource = readFileSync(
  new URL('../src/api/translation.ts', import.meta.url),
  'utf8',
)

const pageSource = readFileSync(
  new URL('../src/pages/app/TranslationPage.vue', import.meta.url),
  'utf8',
)

assert.ok(
  apiSource.includes("parseMode: 'standard' | 'high_quality'"),
  'translation import API should expose parseMode',
)
assert.ok(
  apiSource.includes("formData.append('parseMode', parseMode)"),
  'translation import API should send parseMode to backend',
)
assert.ok(
  pageSource.includes('标准解析') && pageSource.includes('高质量解析'),
  'new translation panel should let PDF users choose parse quality',
)
assert.ok(
  pageSource.includes("importTranslationDocument(file, createMode.value, parseMode.value)"),
  'new translation panel should pass parseMode into import API',
)

console.log('translation-api-ok')
