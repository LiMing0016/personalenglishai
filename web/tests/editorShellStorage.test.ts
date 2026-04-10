import test from 'node:test'
import assert from 'node:assert/strict'

import {
  clearAiProviderNow,
  loadAiProvider,
  saveAiProviderNow,
} from '../src/components/writing/editorShellStorage.ts'

type StorageMap = Map<string, string>

function createStorageMock() {
  const state: StorageMap = new Map()
  return {
    getItem(key: string) {
      return state.has(key) ? state.get(key)! : null
    },
    setItem(key: string, value: string) {
      state.set(key, value)
    },
    removeItem(key: string) {
      state.delete(key)
    },
    clear() {
      state.clear()
    },
  }
}

test('saveAiProviderNow stores provider by doc scope and loadAiProvider restores it', () => {
  const localStorageMock = createStorageMock()
  const sessionStorageMock = createStorageMock()

  Object.assign(globalThis, {
    localStorage: localStorageMock,
    sessionStorage: sessionStorageMock,
  })

  saveAiProviderNow('kimi', 'doc-1')
  saveAiProviderNow('qwen', 'doc-2')

  assert.equal(loadAiProvider('doc-1'), 'kimi')
  assert.equal(loadAiProvider('doc-2'), 'qwen')
})

test('clearAiProviderNow only removes current doc scoped provider', () => {
  const localStorageMock = createStorageMock()
  const sessionStorageMock = createStorageMock()

  Object.assign(globalThis, {
    localStorage: localStorageMock,
    sessionStorage: sessionStorageMock,
  })

  saveAiProviderNow('openai', 'doc-1')
  saveAiProviderNow('kimi', 'doc-2')

  clearAiProviderNow('doc-1')

  assert.equal(loadAiProvider('doc-1'), null)
  assert.equal(loadAiProvider('doc-2'), 'kimi')
})
