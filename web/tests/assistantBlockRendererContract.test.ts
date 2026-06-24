import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantMockSource = readFileSync(
  new URL('../src/pages/app/assistantMock.ts', import.meta.url),
  'utf8',
)
const assistantStateSource = readFileSync(
  new URL('../src/pages/app/assistantState.ts', import.meta.url),
  'utf8',
)
const chatViewSource = readFileSync(
  new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url),
  'utf8',
)
const rendererSource = readFileSync(
  new URL('../src/components/assistant/AssistantBlockRenderer.vue', import.meta.url),
  'utf8',
)

assert.ok(
  assistantMockSource.includes('parts?: AssistantBlock[]'),
  'assistant messages should carry optional structured learning blocks',
)
assert.ok(
  assistantStateSource.includes('normalizeAssistantBlocks'),
  'assistant state should normalize remote and persisted blocks before rendering',
)
assert.ok(
  assistantStateSource.includes('parts: normalizeAssistantBlocks'),
  'assistant state should preserve normalized parts on assistant messages',
)
assert.ok(
  chatViewSource.includes('<AssistantBlockRenderer'),
  'assistant chat view should render structured blocks below assistant markdown',
)
assert.ok(
  rendererSource.includes('VocabCardBlock')
    && rendererSource.includes('GrammarTreeBlock')
    && rendererSource.includes('StudyPlanBlock')
    && rendererSource.includes('SentenceAnalysisBlock'),
  'assistant block renderer should register the four v1 learning blocks',
)

console.log('assistant-block-renderer-contract-ok')
