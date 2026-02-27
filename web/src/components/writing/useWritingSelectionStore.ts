import { ref, type InjectionKey, type Ref } from 'vue'

export interface WritingSelectionStore {
  selectedText: Ref<string>
  setSelectedText: (text: string) => void
  clear: () => void
}

export const writingSelectionStoreKey: InjectionKey<WritingSelectionStore> =
  Symbol('writing-selection-store')

export function createWritingSelectionStore(): WritingSelectionStore {
  const selectedText = ref('')
  return {
    selectedText,
    setSelectedText(text: string) {
      selectedText.value = text.trim()
    },
    clear() {
      selectedText.value = ''
    },
  }
}

