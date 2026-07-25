import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

const apiSource = readFileSync(
  new URL('../src/api/vocabulary.ts', import.meta.url),
  'utf8',
)
const composableUrl = new URL('../src/composables/useVocabularyThemes.ts', import.meta.url)
const composableSource = existsSync(composableUrl) ? readFileSync(composableUrl, 'utf8') : ''

test('defines the theme catalog and mutation payload contracts from the backend DTOs', () => {
  for (const requiredPattern of [
    /interface VocabularyTheme\s*{/,
    /ownerType:\s*'system'\s*\|\s*'user'/,
    /status:\s*'active'\s*\|\s*'disabled'/,
    /interface VocabularyThemeCatalog\s*{/,
    /systemThemes:\s*VocabularyTheme\[\]/,
    /userThemes:\s*VocabularyTheme\[\]/,
    /defaultThemeUid:\s*string/,
    /recentThemeUids:\s*string\[\]/,
    /interface CreateVocabularyThemeRequest\s*{/,
    /type UpdateVocabularyThemeRequest\s*=\s*CreateVocabularyThemeRequest/,
    /name:\s*string/,
    /purpose:\s*string/,
  ]) {
    assert.match(apiSource, requiredPattern)
  }
})

test('maps every theme operation to the controller endpoint', () => {
  for (const requiredText of [
    "listVocabularyThemes = () =>",
    "http.get('/vocabulary/themes')",
    "createVocabularyTheme = (payload:",
    "http.post('/vocabulary/themes', payload)",
    'updateVocabularyTheme = (themeUid: string, payload:',
    "http.put(`/vocabulary/themes/${encodeURIComponent(themeUid)}`, payload)",
    'copyVocabularyTheme = (themeUid: string)',
    "http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/copy`)",
    'setDefaultVocabularyTheme = (themeUid: string)',
    "http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/default`)",
    'disableVocabularyTheme = (themeUid: string)',
    "http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/disable`)",
    'deleteVocabularyTheme = (themeUid: string)',
    "http.delete(`/vocabulary/themes/${encodeURIComponent(themeUid)}`)",
  ]) {
    assert.ok(apiSource.includes(requiredText), `theme API should include ${requiredText}`)
  }
})

test('keeps themes in one query cache and invalidates dependent card data', () => {
  assert.match(composableSource, /const VOCABULARY_THEMES_QUERY_KEY = \['vocabulary', 'themes'\] as const/)
  assert.match(composableSource, /queryKey:\s*VOCABULARY_THEMES_QUERY_KEY/)
  assert.match(composableSource, /createMutation/)
  assert.match(composableSource, /updateMutation/)
  assert.match(composableSource, /copyMutation/)
  assert.match(composableSource, /defaultMutation/)
  assert.match(composableSource, /disableMutation/)
  assert.match(composableSource, /deleteMutation/)
  assert.match(composableSource, /invalidateThemeAndCardQueries/)
  assert.match(composableSource, /queryKey:\s*\['vocabulary', 'cards'\]/)
  assert.doesNotMatch(composableSource, /pinia|localStorage|sessionStorage/i)
})
