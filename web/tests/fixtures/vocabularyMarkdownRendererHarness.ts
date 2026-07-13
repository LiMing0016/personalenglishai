import { createApp, h, ref } from 'vue'

import VocabularyMarkdownRenderer from '../../src/components/vocabulary/VocabularyMarkdownRenderer.vue'
import type { MarkdownSection } from '../../src/components/assistant/markdown'

declare global {
  interface Window {
    vocabularyMarkdownSections: MarkdownSection[][]
    setVocabularyMarkdown: (markdown: string) => void
  }
}

const markdown = ref('## 初始章节\n\nInitial content.')

window.vocabularyMarkdownSections = []
window.setVocabularyMarkdown = (value) => {
  markdown.value = value
}

createApp({
  setup() {
    return () => h(VocabularyMarkdownRenderer, {
      markdown: markdown.value,
      onSectionsChange: (sections: MarkdownSection[]) => {
        window.vocabularyMarkdownSections.push(sections.map((section) => ({ ...section })))
      },
    })
  },
}).mount('#app')
