import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync('F:/personalenglishai/web/src/components/writing/panels/TaskPromptPanel.vue', 'utf8')

assert.match(source, /visualPreview\.chartSpec/)
assert.match(source, /chart|table/)

console.log('task-prompt-panel-template-ok')
