import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const chatViewSource = readFileSync(new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url), 'utf8')

for (const requiredSource of [
  'ref="scrollContainerRef"',
  '@scroll.passive="handleScroll"',
  'shouldAutoFollowMessages',
  'scrollToConversationBottom',
  'requestAnimationFrame',
  'flush: \'post\'',
]) {
  assert.ok(
    chatViewSource.includes(requiredSource),
    `assistant chat view should auto-follow streaming messages with ${requiredSource}`,
  )
}

console.log('assistant-chat-auto-scroll-ok')
