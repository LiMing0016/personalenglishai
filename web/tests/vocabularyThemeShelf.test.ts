import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const selectSource = readSource('../src/components/vocabulary/VocabularyThemeSelect.vue')
const composerSource = readSource('../src/components/vocabulary/VocabularyImportComposer.vue')
const dialogSource = readSource('../src/components/vocabulary/VocabularyImportDialog.vue')
const reviewSource = readSource('../src/components/vocabulary/VocabularyTermReview.vue')
const captureSource = readSource('../src/components/vocabulary/VocabularyCapturePanel.vue')
const viewSource = readSource('../src/views/VocabularyView.vue')

test('theme selector lists active themes and links to theme management', () => {
  assert.match(selectSource, /<select/)
  assert.match(selectSource, /status === 'active'/)
  assert.match(selectSource, /to="\/app\/vocabulary\/themes"/)
  assert.match(selectSource, /管理主题/)
})

test('capture panel is a compact entry instead of a page-sized form', () => {
  assert.match(captureSource, /VocabularyImportDialog/)
  assert.match(captureSource, /打开导入单词对话框|导入单词/)
  assert.doesNotMatch(captureSource, /VocabularyTextCapture|VocabularyImageCapture|capture-mode/)
  assert.doesNotMatch(captureSource, /来源语境/)
})

test('composer accepts text, selected images, and pasted clipboard images in one input', () => {
  assert.match(composerSource, /<textarea/)
  assert.match(composerSource, /@paste="handlePaste"/)
  assert.match(composerSource, /clipboardData\?\.items/)
  assert.match(composerSource, /accept="image\/jpeg,image\/png,image\/webp"/)
  assert.match(composerSource, /type="file"/)
  assert.match(composerSource, /aria-label="添加图片"/)
  assert.match(composerSource, /URL\.createObjectURL|previewUrl/)
  assert.match(composerSource, /URL\.revokeObjectURL/)
  assert.doesNotMatch(composerSource, /文本录入|图片识别/)
})

test('dialog owns latest-wins analysis and stale result protection', () => {
  assert.match(dialogSource, /createImportAnalysisLifecycle/)
  assert.match(dialogSource, /calculateVocabularyImportFingerprint/)
  assert.match(dialogSource, /lifecycle\.invalidate\(\)/)
  assert.match(dialogSource, /lifecycle\.isCurrent/)
  assert.match(dialogSource, /lastSuccessfulFingerprint/)
  assert.match(dialogSource, /currentFingerprint/)
  assert.match(dialogSource, /输入已变化，请重新分析/)
  assert.match(dialogSource, /分析中/)
  assert.match(dialogSource, /AI 分析/)
  assert.match(dialogSource, /canGenerateFromCurrentAnalysis/)
  assert.doesNotMatch(dialogSource, /来源语境/)
})

test('term review supports explicit typo decisions and stable view sorting', () => {
  for (const label of ['采用', '保留原词', '删除', '全选', '清空', '录入顺序', 'A-Z']) {
    assert.match(reviewSource, new RegExp(label))
  }
  assert.match(reviewSource, /sortImportCandidates/)
  assert.match(reviewSource, /emit\('command'/)
  assert.doesNotMatch(reviewSource, /props\.candidates\.(push|splice|sort)/)
})

test('dialog submits the selected theme only for a current successful analysis', () => {
  assert.match(dialogSource, /VocabularyThemeSelect/)
  assert.match(dialogSource, /themeUid: selectedThemeUid\.value/)
  assert.match(dialogSource, /canGenerateFromCurrentAnalysis/)
  assert.match(dialogSource, /orchestrateCaptureBatches/)
  assert.match(dialogSource, /captureMutation\.isPending\.value/)
})

test('vocabulary view owns theme and unified analysis mutations', () => {
  assert.match(viewSource, /useVocabularyThemes/)
  assert.match(viewSource, /:theme-catalog="themesQuery\.data\.value"/)
  assert.match(viewSource, /:themes-loading="themesQuery\.isLoading\.value"/)
  assert.match(viewSource, /:themes-error="themesBlockingError"/)
  assert.match(viewSource, /:import-analysis-enabled="importAnalysisEnabled"/)
  assert.match(viewSource, /:import-analysis-mutation="importAnalysisMutation"/)
  assert.doesNotMatch(viewSource, /:image-recognition-mutation=/)
})
