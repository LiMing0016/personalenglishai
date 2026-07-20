import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const selectSource = readSource('../src/components/vocabulary/VocabularyThemeSelect.vue')
const textCaptureSource = readSource('../src/components/vocabulary/VocabularyTextCapture.vue')
const imageCaptureSource = readSource('../src/components/vocabulary/VocabularyImageCapture.vue')
const imageRecognitionSource = readSource('../src/features/vocabulary/imageRecognition.ts')
const reviewSource = readSource('../src/components/vocabulary/VocabularyTermReview.vue')
const captureSource = readSource('../src/components/vocabulary/VocabularyCapturePanel.vue')
const viewSource = readSource('../src/views/VocabularyView.vue')

test('theme selector lists active themes and links to theme management', () => {
  assert.match(selectSource, /<select/)
  assert.match(selectSource, /status === 'active'/)
  assert.match(selectSource, /to="\/app\/vocabulary\/themes"/)
  assert.match(selectSource, /管理主题/)
  assert.match(selectSource, /主题加载中/)
  assert.match(selectSource, /主题加载失败/)
  assert.match(selectSource, /暂无可用主题/)
})

test('text adapter parses terms without owning candidate state', () => {
  assert.match(textCaptureSource, /parseCaptureTerms/)
  assert.match(textCaptureSource, /emit\('terms'/)
  assert.doesNotMatch(textCaptureSource, /ImportCandidate\[\]|candidates\s*=\s*ref/)
})

test('image capture uses upload and camera inputs with stable preview lifecycle', () => {
  assert.equal((imageCaptureSource.match(/accept="image\/jpeg,image\/png,image\/webp"/g) ?? []).length, 2)
  assert.match(imageCaptureSource, /capture="environment"/)
  assert.match(imageRecognitionSource, /URL\.createObjectURL/)
  assert.match(imageRecognitionSource, /URL\.revokeObjectURL/)
  assert.match(imageCaptureSource, /aspect-ratio/)
  assert.match(imageCaptureSource, /object-fit:\s*contain/)
  assert.match(imageCaptureSource, /<details/)
  assert.match(imageCaptureSource, /recognizing \? '识别中\.\.\.' : response \? '重新识别' : '开始识别'/)
  assert.equal((imageCaptureSource.match(/\shidden(?:\s|>)/g) ?? []).length, 2)
  assert.match(imageCaptureSource, /:disabled="disabled"/)
  assert.match(imageCaptureSource, /:disabled="disabled \|\| recognizing"/)
  assert.match(imageCaptureSource, /未识别到可导入单词/)
  assert.match(imageCaptureSource, /emit\('clear-error'\)/)
  assert.match(imageCaptureSource, /createImageRequestLifecycle/)
  assert.match(imageCaptureSource, /defineExpose\(\{ deactivate \}\)/)
})

test('term review requires explicit typo decisions and emits reducer commands', () => {
  for (const label of ['采用', '保留原词', '删除', '全选', '清空']) assert.match(reviewSource, new RegExp(label))
  assert.match(reviewSource, /词典已验证/)
  assert.match(reviewSource, /emit\('command'/)
  assert.doesNotMatch(reviewSource, /props\.candidates\.(push|splice)/)
  assert.doesNotMatch(reviewSource, /candidate\.suggestions\.some/)
  assert.match(reviewSource, /v-for="suggestion in candidate\.suggestions"[\s\S]*v-if="suggestion\.dictionaryVerified"[\s\S]*词典已验证/)
})

test('keeps selection in the capture draft and falls back when it becomes unavailable', () => {
  assert.match(captureSource, /const selectedThemeUid = ref\(''\)/)
  assert.match(captureSource, /selectedThemeUid\.value = catalog\.defaultThemeUid/)
  assert.match(captureSource, /selectedThemeIsActive/)
  assert.match(captureSource, /watch\(/)
  assert.match(captureSource, /VocabularyThemeSelect/)
  assert.doesNotMatch(captureSource, /syncManualCandidates\(parseCaptureTerms\(rawTerms\.value\)\)/)
  assert.match(captureSource, /reconcileManualCandidates/)
})

test('submits the selected theme explicitly and explains every unavailable state', () => {
  assert.match(captureSource, /themeUid: selectedThemeUid\.value/)
  assert.doesNotMatch(captureSource, /templateKey: templateKey\.value/)
  assert.doesNotMatch(captureSource, /按「.*」生成/)
  assert.match(captureSource, /生成中\.\.\.|生成卡片/)
  assert.match(captureSource, /主题加载中/)
  assert.match(captureSource, /主题加载失败/)
  assert.match(captureSource, /暂无可用主题/)
  assert.match(captureSource, /captureMutation\.isPending\.value/)
  assert.match(captureSource, /orchestrateCaptureBatches/)
})

test('capture workspace has one error live region and locks image controls while capturing', () => {
  assert.equal(((captureSource + imageCaptureSource).match(/role="alert"/g) ?? []).length, 1)
  assert.doesNotMatch(imageCaptureSource, /const errorMessage = ref/)
  assert.match(captureSource, /@clear-error="requestError = ''"/)
  assert.match(captureSource, /:disabled="captureBusy"/)
  assert.match(captureSource, /:disabled="captureBusy"[\s\S]*文本录入/)
  assert.match(captureSource, /图片识别/)
  assert.match(captureSource, /imageCaptureRef\.value\?\.deactivate\(\)/)
})

test('vocabulary view owns the server theme query and passes its states to capture', () => {
  assert.match(viewSource, /useVocabularyThemes/)
  assert.match(viewSource, /:theme-catalog="themesQuery\.data\.value"/)
  assert.match(viewSource, /:themes-loading="themesQuery\.isLoading\.value"/)
  assert.match(viewSource, /:themes-error="themesBlockingError"/)
  assert.match(viewSource, /:image-recognition-enabled="imageRecognitionEnabled"/)
  assert.match(viewSource, /:image-recognition-mutation="imageRecognitionMutation"/)
})

test('blocks only a theme query error without cached catalog data', () => {
  assert.match(
    viewSource,
    /const themesBlockingError = computed\(\(\) => themesQuery\.isError\.value && !themesQuery\.data\.value\)/,
  )
  assert.doesNotMatch(viewSource, /:themes-error="themesQuery\.isError\.value"/)
})
