import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const shelfSource = readSource('../src/components/vocabulary/VocabularyThemeShelf.vue')
const captureSource = readSource('../src/components/vocabulary/VocabularyCapturePanel.vue')
const viewSource = readSource('../src/views/VocabularyView.vue')

test('shows at most three active themes before the fixed create action', () => {
  assert.match(shelfSource, /defaultThemeUid/)
  assert.match(shelfSource, /recentThemeUids/)
  assert.match(shelfSource, /status === 'active'/)
  assert.match(shelfSource, /slice\(0, 3\)/)
  assert.match(shelfSource, /新建主题/)
  assert.match(shelfSource, /管理全部主题/)
})

test('deduplicates the default and recent themes while keeping the default first', () => {
  assert.match(shelfSource, /new Set<string>/)
  assert.match(shelfSource, /catalog\.defaultThemeUid/)
  assert.match(shelfSource, /catalog\.recentThemeUids/)
  assert.match(shelfSource, /selectedThemeUid === theme\.themeUid/)
  assert.match(shelfSource, /emit\('select', theme\.themeUid\)/)
})

test('routes both theme management actions without pretending to select through query state', () => {
  assert.ok(
    (shelfSource.match(/to="\/app\/vocabulary\/themes"/g) ?? []).length >= 2,
    'create and manage actions should open the theme library',
  )
  assert.doesNotMatch(shelfSource, /query\s*:/)
})

test('keeps the fixed create action reachable in loading, error, and empty states', () => {
  assert.doesNotMatch(shelfSource, /v-else class="theme-shelf__items"/)
  assert.match(shelfSource, /class="theme-shelf__items"/)
})

test('keeps selection in the capture draft and falls back when it becomes unavailable', () => {
  assert.match(captureSource, /const selectedThemeUid = ref\(''\)/)
  assert.match(captureSource, /selectedThemeUid\.value = catalog\.defaultThemeUid/)
  assert.match(captureSource, /selectedThemeIsActive/)
  assert.match(captureSource, /watch\(/)
  assert.match(captureSource, /VocabularyThemeShelf/)
})

test('submits the selected theme explicitly and explains every unavailable state', () => {
  assert.match(captureSource, /themeUid: selectedThemeUid\.value/)
  assert.doesNotMatch(captureSource, /templateKey: templateKey\.value/)
  assert.match(captureSource, /按「.*」生成/)
  assert.match(captureSource, /主题加载中/)
  assert.match(captureSource, /主题加载失败/)
  assert.match(captureSource, /暂无可用主题/)
  assert.match(captureSource, /captureMutation\.isPending\.value/)
})

test('vocabulary view owns the server theme query and passes its states to capture', () => {
  assert.match(viewSource, /useVocabularyThemes/)
  assert.match(viewSource, /:theme-catalog="themesQuery\.data\.value"/)
  assert.match(viewSource, /:themes-loading="themesQuery\.isLoading\.value"/)
  assert.match(viewSource, /:themes-error="themesBlockingError"/)
})

test('blocks only a theme query error without cached catalog data', () => {
  assert.match(
    viewSource,
    /const themesBlockingError = computed\(\(\) => themesQuery\.isError\.value && !themesQuery\.data\.value\)/,
  )
  assert.doesNotMatch(viewSource, /:themes-error="themesQuery\.isError\.value"/)
})
