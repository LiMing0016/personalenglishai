import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantApiSource = readFileSync(new URL('../src/api/assistant.ts', import.meta.url), 'utf8')

assert.ok(assistantApiSource.includes('studyStage?: string'))
assert.ok(assistantApiSource.includes("assistantMode?: 'default' | 'exam' | 'learning'"))
assert.ok(assistantApiSource.includes("formData.append('studyStage'"))
assert.ok(assistantApiSource.includes("formData.append('assistantMode'"))
