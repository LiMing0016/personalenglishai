import type { GrammarTreeData, GrammarTreeNode } from '../contracts.ts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function isGrammarTreeNode(value: unknown): value is GrammarTreeNode {
  if (!isRecord(value)) return false
  if (typeof value.id !== 'string' || !value.id.trim()) return false
  if (typeof value.label !== 'string' || !value.label.trim()) return false
  return value.children === undefined
    || (Array.isArray(value.children) && value.children.every(isGrammarTreeNode))
}

export function normalizeGrammarTreeData(value: unknown): GrammarTreeData | null {
  if (!isRecord(value) || typeof value.topic !== 'string' || !value.topic.trim()) return null
  if (!isGrammarTreeNode(value.root)) return null
  return value as unknown as GrammarTreeData
}

export function grammarTreeFallback(data: GrammarTreeData) {
  return `### ${data.topic}\n\n${data.root.label}`
}

