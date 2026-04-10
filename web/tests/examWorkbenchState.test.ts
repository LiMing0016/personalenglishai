import test from 'node:test'
import assert from 'node:assert/strict'

import {
  canStartWorkbenchFromPreview,
  commitWorkbenchSubmission,
  isWorkbenchAbortError,
  resolveWorkbenchComposerAction,
  shouldConfirmModeSwitch,
  shouldShowWorkbenchRefreshWarning,
  shouldSubmitWorkbenchOnEnter,
  persistExamSetupStateSnapshot,
  restoreExamSetupStateSnapshot,
  resolveRestoredDraftTopic,
  shouldUseInitialTopicSeed,
  resolveWorkbenchCanvasState,
} from '../src/pages/app/examWorkbenchState.ts'

test('commitWorkbenchSubmission clears draft while preserving submitted text', () => {
  const result = commitWorkbenchSubmission('  年轻人  ')

  assert.equal(result.submittedText, '年轻人')
  assert.equal(result.nextDraftText, '')
})

test('resolveWorkbenchCanvasState prefers waiting over stale preview while request is running', () => {
  const state = resolveWorkbenchCanvasState({
    workbenchBusy: true,
    pendingSubmission: false,
    hasPreviewSheet: true,
    previewDirty: false,
    hasSubmittedContext: true,
  })

  assert.equal(state, 'waiting')
})

test('resolveWorkbenchCanvasState returns ready only when preview exists and is clean', () => {
  const state = resolveWorkbenchCanvasState({
    workbenchBusy: false,
    pendingSubmission: false,
    hasPreviewSheet: true,
    previewDirty: false,
    hasSubmittedContext: true,
  })

  assert.equal(state, 'ready')
})

test('resolveWorkbenchCanvasState returns waiting when submission is persisted as pending', () => {
  const state = resolveWorkbenchCanvasState({
    workbenchBusy: false,
    pendingSubmission: true,
    hasPreviewSheet: true,
    previewDirty: false,
    hasSubmittedContext: true,
  })

  assert.equal(state, 'waiting')
})

test('resolveRestoredDraftTopic clears composer draft after a submitted prompt is already shown', () => {
  const restored = resolveRestoredDraftTopic({
    topic: '年轻人',
    submittedTopic: '年轻人',
    previewDirty: false,
    pendingSubmission: false,
  })

  assert.equal(restored, '')
})

test('resolveRestoredDraftTopic preserves unsent draft when preview is dirty', () => {
  const restored = resolveRestoredDraftTopic({
    topic: '年轻人与责任',
    submittedTopic: '年轻人',
    previewDirty: true,
    pendingSubmission: false,
  })

  assert.equal(restored, '年轻人与责任')
})

test('shouldUseInitialTopicSeed yields to live state when both exist', () => {
  const shouldUseInitial = shouldUseInitialTopicSeed({
    initialTopic: 'Directions: Write an essay...',
    hasLiveState: true,
  })

  assert.equal(shouldUseInitial, false)
})

test('shouldUseInitialTopicSeed uses initial topic when no live state exists', () => {
  const shouldUseInitial = shouldUseInitialTopicSeed({
    initialTopic: 'Directions: Write an essay...',
    hasLiveState: false,
  })

  assert.equal(shouldUseInitial, true)
})

test('persistExamSetupStateSnapshot writes snapshot synchronously to storage', () => {
  const storage = createMemoryStorage()

  persistExamSetupStateSnapshot(storage, 'peai:examSetup:live', {
    submittedTopic: '年轻人与责任',
    previewSheet: { promptText: 'Write an essay.' },
  })

  assert.equal(
    storage.getItem('peai:examSetup:live'),
    JSON.stringify({
      submittedTopic: '年轻人与责任',
      previewSheet: { promptText: 'Write an essay.' },
    }),
  )
})

test('restoreExamSetupStateSnapshot returns null for malformed json', () => {
  const storage = createMemoryStorage()
  storage.setItem('peai:examSetup:live', '{invalid-json')

  const restored = restoreExamSetupStateSnapshot(storage, 'peai:examSetup:live')

  assert.equal(restored, null)
})

test('shouldSubmitWorkbenchOnEnter returns true for plain enter', () => {
  assert.equal(
    shouldSubmitWorkbenchOnEnter({
      key: 'Enter',
      shiftKey: false,
      ctrlKey: false,
      metaKey: false,
      nativeEvent: { isComposing: false },
    }),
    true,
  )
})

test('shouldSubmitWorkbenchOnEnter ignores modified enter and composition', () => {
  assert.equal(
    shouldSubmitWorkbenchOnEnter({
      key: 'Enter',
      shiftKey: true,
      ctrlKey: false,
      metaKey: false,
      nativeEvent: { isComposing: false },
    }),
    false,
  )
  assert.equal(
    shouldSubmitWorkbenchOnEnter({
      key: 'Enter',
      shiftKey: false,
      ctrlKey: true,
      metaKey: false,
      nativeEvent: { isComposing: false },
    }),
    false,
  )
  assert.equal(
    shouldSubmitWorkbenchOnEnter({
      key: 'Enter',
      shiftKey: false,
      ctrlKey: false,
      metaKey: false,
      nativeEvent: { isComposing: true },
    }),
    false,
  )
})

test('shouldConfirmModeSwitch requires a generated preview and a real mode change', () => {
  assert.equal(
    shouldConfirmModeSwitch({
      currentMode: 'exam',
      nextMode: 'free',
      hasPreviewSheet: true,
    }),
    true,
  )

  assert.equal(
    shouldConfirmModeSwitch({
      currentMode: 'exam',
      nextMode: 'exam',
      hasPreviewSheet: true,
    }),
    false,
  )

  assert.equal(
    shouldConfirmModeSwitch({
      currentMode: 'exam',
      nextMode: 'free',
      hasPreviewSheet: false,
    }),
    false,
  )
})

test('canStartWorkbenchFromPreview depends on right preview only', () => {
  assert.equal(
    canStartWorkbenchFromPreview({
      selectedMode: 'exam',
      workbenchStep: 'preview',
      hasPreviewSheet: true,
      hasPreviewInfo: true,
    }),
    true,
  )

  assert.equal(
    canStartWorkbenchFromPreview({
      selectedMode: 'exam',
      workbenchStep: 'compose',
      hasPreviewSheet: true,
      hasPreviewInfo: true,
    }),
    false,
  )

  assert.equal(
    canStartWorkbenchFromPreview({
      selectedMode: 'free',
      workbenchStep: 'preview',
      hasPreviewSheet: true,
      hasPreviewInfo: true,
    }),
    false,
  )
})

test('shouldShowWorkbenchRefreshWarning requires actual submitted context', () => {
  assert.equal(
    shouldShowWorkbenchRefreshWarning({
      previewDirty: true,
      workbenchBusy: false,
      hasSubmittedContext: true,
    }),
    true,
  )

  assert.equal(
    shouldShowWorkbenchRefreshWarning({
      previewDirty: true,
      workbenchBusy: false,
      hasSubmittedContext: false,
    }),
    false,
  )
})

test('resolveWorkbenchComposerAction returns cancel while request is running', () => {
  assert.equal(resolveWorkbenchComposerAction({ workbenchBusy: true }), 'cancel')
  assert.equal(resolveWorkbenchComposerAction({ workbenchBusy: false }), 'submit')
})

test('isWorkbenchAbortError recognizes abort-like request failures', () => {
  assert.equal(isWorkbenchAbortError({ name: 'AbortError' }), true)
  assert.equal(isWorkbenchAbortError({ name: 'CanceledError' }), true)
  assert.equal(isWorkbenchAbortError({ code: 'ERR_CANCELED' }), true)
  assert.equal(isWorkbenchAbortError(new Error('other failure')), false)
  assert.equal(isWorkbenchAbortError(null), false)
})

function createMemoryStorage() {
  const store = new Map<string, string>()

  return {
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null
    },
    setItem(key: string, value: string) {
      store.set(key, value)
    },
    removeItem(key: string) {
      store.delete(key)
    },
  }
}
