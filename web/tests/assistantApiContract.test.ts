import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantApiSource = readFileSync(new URL('../src/api/assistant.ts', import.meta.url), 'utf8')

assert.ok(assistantApiSource.includes('agentName?: string'))
assert.ok(assistantApiSource.includes('agentName?: string'))
assert.ok(assistantApiSource.includes('body.agentName'))
assert.ok(assistantApiSource.includes('studyStage?: string'))
assert.ok(assistantApiSource.includes("assistantMode?: 'default' | 'exam'"))
assert.ok(assistantApiSource.includes("formData.append('study_stage'"))
assert.ok(assistantApiSource.includes("formData.append('assistant_mode'"))
