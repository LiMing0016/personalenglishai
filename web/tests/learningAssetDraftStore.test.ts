import assert from 'node:assert/strict'

import {
  createLearningAssetDraftStore,
  LEARNING_ASSET_DRAFT_STORAGE_KEY,
} from '../src/pages/app/learningAssetDraftStore.ts'
import { createLearningAssetDraft } from '../src/types/learningAssets.ts'

function createMemoryStorage(): Storage {
  const entries = new Map<string, string>()
  return {
    get length() {
      return entries.size
    },
    clear() {
      entries.clear()
    },
    getItem(key: string) {
      return entries.get(key) ?? null
    },
    key(index: number) {
      return Array.from(entries.keys())[index] ?? null
    },
    removeItem(key: string) {
      entries.delete(key)
    },
    setItem(key: string, value: string) {
      entries.set(key, value)
    },
  }
}

const storage = createMemoryStorage()
const store = createLearningAssetDraftStore({ storage })
const draft = createLearningAssetDraft({
  conversationId: 'conv-1',
  messageId: 'msg-1',
  draftId: 'draft-word',
  title: 'nuanced',
  selectedText: 'nuanced',
  contextText: 'A nuanced answer considers different sides.',
})
const grammarDraft = createLearningAssetDraft({
  conversationId: 'conv-1',
  messageId: 'msg-2',
  draftId: 'draft-grammar',
  type: 'grammar',
  title: 'relative clause',
  selectedText: 'which improves clarity',
  contextText: 'Use a relative clause, which improves clarity.',
})

store.saveDraft(draft)
assert.deepEqual(store.readDraft('conv-1')?.title, 'nuanced')
assert.equal(store.readDraft('conv-2'), null)
store.saveWorkspace({
  conversationId: 'conv-1',
  activeDraftId: grammarDraft.draftId,
  drafts: [draft, grammarDraft],
})

const workspace = store.readWorkspace('conv-1')
assert.equal(workspace?.activeDraftId, 'draft-grammar')
assert.equal(workspace?.drafts.length, 2)
assert.equal(workspace?.drafts[1]?.type, 'grammar')

const restoredStore = createLearningAssetDraftStore({ storage })
assert.equal(restoredStore.readDraft('conv-1')?.sourceMessageId, 'msg-1')
assert.equal(restoredStore.readWorkspace('conv-1')?.drafts.length, 2)
assert.ok(storage.getItem(LEARNING_ASSET_DRAFT_STORAGE_KEY)?.includes('conv-1'))

restoredStore.clearDraft('conv-1')
assert.equal(restoredStore.readDraft('conv-1'), null)
assert.equal(restoredStore.readWorkspace('conv-1'), null)

console.log('learning-asset-draft-store-ok')
