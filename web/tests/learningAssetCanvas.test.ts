import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const canvasSource = readFileSync(
  new URL('../src/components/assistant/LearningAssetCanvas.vue', import.meta.url),
  'utf8',
)
const pageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')

for (const requiredText of [
  '学习资产画布',
  '单词卡',
  '语法笔记',
  '句子笔记',
  '新建空白笔记',
  'canvas-workspace-header',
  'workspace-create-menu',
  'asset-tab-rail',
  'asset-tab-list',
  'asset-tab-actions',
  'asset-tab-scroll',
  'assetTabListRef',
  'scrollAssetTabs',
  'asset-tab',
  'asset-tab--active',
  'asset-tab-add',
  'asset-tab-rename-input',
  'renamingDraftId',
  'setRenameInputRef',
  'startTabRename',
  'commitTabRename',
  'cancelTabRename',
  'renameDraft',
  'readAssetTypeIcon',
  'toggleCreateMenu',
  'createMenuOpen',
  'drafts',
  'activeDraftId',
  'saveStatusByDraftId',
  'selectDraft',
  'createEmptyDraft',
  'Copilot ✦',
  'copilot-menu',
  'toggleCopilotMenu',
  'runCopilotAction',
  '补全空白',
  '扩展内容',
  '润色语句',
  '按模板整理',
  '优化格式',
  '生成例句',
  '只处理当前资产',
  '告诉 Copilot 你想怎么整理',
  'copilotInstruction',
  'LearningAssetCopilotRequest',
  '保存',
  'floating-format-toolbar',
  'insert-block-button',
  'insert-block-menu',
  'applyInlineFormat',
  'insertMarkdownSnippet',
  'candidateMarkdown',
  'Copilot 建议',
  '只填空白',
  '追加到正文',
  '覆盖正文',
  '取消候选',
  'update:contentMarkdown',
  'saveStatus',
  'auto-save-status',
  'autoSaveStatusLabel',
  'renderAssistantMarkdown',
  'resize-handle',
  'startResize',
  'resize:width',
  'contenteditable',
  'editableMarkdownRef',
  'serializeEditableMarkdown',
  'imageInputRef',
  'handleImageFileChange',
  'handleImagePaste',
  'handleImageDrop',
  'insertImageFiles',
  'readFileAsDataUrl',
  'accept="image/*"',
]) {
  assert.ok(canvasSource.includes(requiredText), `learning asset canvas should include ${requiredText}`)
}

const toolbarSource = canvasSource.slice(
  canvasSource.indexOf('<section class="canvas-toolbar"'),
  canvasSource.indexOf('</section>', canvasSource.indexOf('<section class="canvas-toolbar"')),
)

assert.ok(!toolbarSource.includes('调整格式'), 'format controls should live in the editor chrome instead of the top toolbar')
assert.ok(!toolbarSource.includes('添加图片'), 'image insertion should live in the editor insert menu instead of the top toolbar')
assert.ok(!toolbarSource.includes('AI 整理'), 'top toolbar should expose the learning asset Copilot instead of the old organize button')
assert.ok(!toolbarSource.includes('class="save-button"'), 'top toolbar should rely on autosave instead of a manual save button')
assert.ok(!toolbarSource.includes('class="view-toggle"'), 'top toolbar should not duplicate preview/edit controls')
assert.ok(!toolbarSource.includes('预览'), 'top toolbar should not show a preview toggle')
assert.ok(!toolbarSource.includes('编辑'), 'top toolbar should not show an edit toggle')
assert.ok(!canvasSource.includes('<textarea'), 'edit mode should not expose raw markdown source as the primary editor')
assert.ok(!canvasSource.includes('class="canvas-header"'), 'canvas should not render a duplicated top identity header')
assert.ok(!canvasSource.includes('class="canvas-kicker"'), 'canvas should not show a redundant learning asset label above the selector')
assert.ok(!canvasSource.includes('class="canvas-workspace-topbar"'), 'canvas should not render a redundant title row above tabs')
assert.ok(!canvasSource.includes('class="workspace-eyebrow"'), 'canvas tabs should not repeat a learning-canvas label above the file tabs')
assert.ok(!canvasSource.includes('window.prompt'), 'tab rename should use an inline input instead of a browser prompt')
assert.ok(canvasSource.includes('aria-label="向左滚动学习资产标签"'), 'tab rail should expose a left scroll control for many tabs')
assert.ok(canvasSource.includes('aria-label="向右滚动学习资产标签"'), 'tab rail should expose a right scroll control for many tabs')
assert.ok(!canvasSource.includes('class="title-input"'), 'canvas title should not be duplicated above the current-note selector')
assert.ok(!canvasSource.includes('class="workspace-title"'), 'workspace header should not duplicate the active tab title')
assert.ok(canvasSource.includes('width: 20px;'), 'resize handle should expose a wider pointer target')
assert.ok(canvasSource.includes('left: -10px;'), 'resize handle should extend farther into the split gutter')
assert.ok(!canvasSource.includes('class="type-chip"'), 'asset type should not be duplicated in a separate top chip')
assert.ok(canvasSource.includes('asset-close-button'), 'canvas close action should move into the compact selector row')
assert.ok(!canvasSource.includes('asset-selector-trigger'), 'canvas should not keep the old current-note dropdown trigger')
assert.ok(!canvasSource.includes('asset-selector-popover'), 'canvas creation should move out of the old current-note popover')
assert.ok(!canvasSource.includes('当前笔记'), 'canvas should not duplicate the current-note card label above tabs')
assert.ok(!pageSource.includes('选区补充'), 'append-to-current-note should not add an automatic section title')
assert.ok(!pageSource.includes('**摘录：**'), 'append-to-current-note should paste the selected text without an excerpt label')
assert.ok(!pageSource.includes('**上下文：**'), 'append-to-current-note should not paste selection context')

for (const requiredPageText of [
  'learningAssetCanvasWidth',
  'setLearningAssetCanvasWidth',
  '@resize:width',
  '@rename-draft',
  '--learning-canvas-width',
  'learningAssetSaveStatus',
  'queueAutoSaveLearningAsset',
  'saveLearningAssetDraft({ showSuccessToast: false, draftId: draft.draftId })',
  'saveLearningAssetPayload',
  'EmptyApiDataError',
  'error instanceof EmptyApiDataError',
  'queueAutoSaveLearningAsset(activeDraft)',
  'renameLearningAssetDraft',
  'applyLearningAssetCandidate',
  'Copilot 补充',
  'getLearningNote',
  'openLearningAssetFromNoteUid',
  'learningAssetDrafts',
  'activeLearningAssetDraftId',
  'learningAssetSaveStatusByDraftId',
  'learningAssetCandidateMarkdownByDraftId',
  'setActiveLearningAssetDraft',
  'createEmptyLearningAssetDraft',
  "mode: 'replace' | 'append' | 'fill'",
  'handleOrganizeLearningAsset(request',
  'action: request.action',
  'instruction: request.instruction',
]) {
  assert.ok(pageSource.includes(requiredPageText), `assistant page should wire ${requiredPageText}`)
}

assert.ok(!canvasSource.includes('来源：'), 'canvas should not expose a separate source metadata row')

console.log('learning-asset-canvas-ok')
