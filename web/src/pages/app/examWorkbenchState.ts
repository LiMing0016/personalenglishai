export type WorkbenchCanvasState = 'empty' | 'waiting' | 'ready'

export interface CommitWorkbenchSubmissionResult {
  submittedText: string | null
  nextDraftText: string
}

export interface ResolveWorkbenchCanvasStateInput {
  workbenchBusy: boolean
  pendingSubmission: boolean
  hasPreviewSheet: boolean
  previewDirty: boolean
  hasSubmittedContext: boolean
}

export interface ResolveRestoredDraftTopicInput {
  topic: string
  submittedTopic: string
  previewDirty: boolean
  pendingSubmission: boolean
}

export interface ShouldUseInitialTopicSeedInput {
  initialTopic: string | null | undefined
  hasLiveState: boolean
}

export interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export interface WorkbenchEnterKeyInput {
  key: string
  shiftKey: boolean
  ctrlKey: boolean
  metaKey: boolean
  nativeEvent?: {
    isComposing?: boolean
  } | null
}

export interface ShouldConfirmModeSwitchInput {
  currentMode: 'free' | 'exam'
  nextMode: 'free' | 'exam'
  hasPreviewSheet: boolean
}

export interface ResolveWorkbenchComposerActionInput {
  workbenchBusy: boolean
}

export interface CanStartWorkbenchFromPreviewInput {
  selectedMode: 'free' | 'exam'
  workbenchStep: 'compose' | 'preview'
  hasPreviewSheet: boolean
  hasPreviewInfo: boolean
}

export interface ShouldShowWorkbenchRefreshWarningInput {
  previewDirty: boolean
  workbenchBusy: boolean
  hasSubmittedContext: boolean
}

export interface ResolveExamSetupSaveActionInput {
  hasPromptInfo: boolean
  isDirty: boolean
}

export function commitWorkbenchSubmission(draftText: string): CommitWorkbenchSubmissionResult {
  const submittedText = draftText.trim() || null
  return {
    submittedText,
    nextDraftText: '',
  }
}

export function resolveWorkbenchCanvasState(input: ResolveWorkbenchCanvasStateInput): WorkbenchCanvasState {
  if ((input.workbenchBusy || input.pendingSubmission) && input.hasSubmittedContext) return 'waiting'
  if (input.hasPreviewSheet && !input.previewDirty) return 'ready'
  return 'empty'
}

export function resolveRestoredDraftTopic(input: ResolveRestoredDraftTopicInput): string {
  const topic = input.topic ?? ''
  if (!topic.trim()) return ''
  if (input.previewDirty) return topic
  if (input.pendingSubmission) return ''
  if (topic.trim() && topic.trim() === (input.submittedTopic ?? '').trim()) {
    return ''
  }
  return topic
}

export function shouldUseInitialTopicSeed(input: ShouldUseInitialTopicSeedInput): boolean {
  return !!input.initialTopic?.trim() && !input.hasLiveState
}

export function persistExamSetupStateSnapshot<T>(storage: StorageLike | null | undefined, key: string, state: T | null): void {
  if (!storage) return
  try {
    if (state == null) {
      storage.removeItem(key)
      return
    }
    storage.setItem(key, JSON.stringify(state))
  } catch {
    // Ignore storage failures and let the in-memory state continue.
  }
}

export function restoreExamSetupStateSnapshot<T>(storage: Pick<StorageLike, 'getItem'> | null | undefined, key: string): T | null {
  if (!storage) return null
  try {
    const raw = storage.getItem(key)
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function shouldSubmitWorkbenchOnEnter(input: WorkbenchEnterKeyInput): boolean {
  if (input.key !== 'Enter') return false
  if (input.shiftKey || input.ctrlKey || input.metaKey) return false
  if (input.nativeEvent?.isComposing) return false
  return true
}

export function shouldConfirmModeSwitch(input: ShouldConfirmModeSwitchInput): boolean {
  if (input.currentMode === input.nextMode) return false
  return input.hasPreviewSheet
}

export function resolveWorkbenchComposerAction(input: ResolveWorkbenchComposerActionInput): 'submit' | 'cancel' {
  return input.workbenchBusy ? 'cancel' : 'submit'
}

export function isWorkbenchAbortError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const maybeError = error as { name?: string; code?: string }
  return maybeError.name === 'AbortError'
    || maybeError.name === 'CanceledError'
    || maybeError.code === 'ERR_CANCELED'
}

export function canStartWorkbenchFromPreview(input: CanStartWorkbenchFromPreviewInput): boolean {
  return input.selectedMode === 'exam'
    && input.workbenchStep === 'preview'
    && input.hasPreviewSheet
    && input.hasPreviewInfo
}

export function shouldShowWorkbenchRefreshWarning(input: ShouldShowWorkbenchRefreshWarningInput): boolean {
  return input.previewDirty && !input.workbenchBusy && input.hasSubmittedContext
}

export function resolveExamSetupSaveAction(input: ResolveExamSetupSaveActionInput): 'createDraftDocument' | 'saveSetupState' | 'noop' {
  if (input.hasPromptInfo) return 'createDraftDocument'
  if (input.isDirty) return 'saveSetupState'
  return 'noop'
}
