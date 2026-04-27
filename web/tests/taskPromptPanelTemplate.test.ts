import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync('F:/personalenglishai/web/src/components/writing/panels/TaskPromptPanel.vue', 'utf8')

assert.match(source, /visualPreview\.chartSpec/)
assert.match(source, /chart|table/)
assert.match(source, /paper-attachment--image/)
assert.match(source, /\.paper-attachment--image\s+\.paper-attachment-image\s*{[^}]*width:\s*calc\(100%\s*\+\s*60px\)/s)
assert.match(source, /\.paper-attachment-image\s*{[^}]*object-fit:\s*contain/s)
assert.match(source, /\.task-prompt-panel\s*{[^}]*min-height:\s*100%/s)
assert.match(source, /\.task-prompt-panel\s*{[^}]*#f8fbfd\s+52%/s)

console.log('task-prompt-panel-template-ok')
