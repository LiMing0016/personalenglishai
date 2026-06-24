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
  title: 'nuanced',
  selectedText: 'nuanced',
  contextText: 'A nuanced answer considers different sides.',
})

store.saveDraft(draft)
assert.deepEqual(store.readDraft('conv-1')?.title, 'nuanced')
assert.equal(store.readDraft('conv-2'), null)

const restoredStore = createLearningAssetDraftStore({ storage })
assert.equal(restoredStore.readDraft('conv-1')?.sourceMessageId, 'msg-1')
assert.ok(storage.getItem(LEARNING_ASSET_DRAFT_STORAGE_KEY)?.includes('conv-1'))

restoredStore.clearDraft('conv-1')
assert.equal(restoredStore.readDraft('conv-1'), null)

console.log('learning-asset-draft-store-ok')
