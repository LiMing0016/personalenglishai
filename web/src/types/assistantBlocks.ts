export type AssistantBlockType =
  | 'vocab_card'
  | 'grammar_tree'
  | 'study_plan'
  | 'sentence_analysis'

export interface AssistantBlockAction {
  id: string
  label: string
  prompt: string
}

export interface AssistantBlockBase<TType extends AssistantBlockType, TData> {
  id: string
  type: TType
  version: 1
  title?: string
  fallbackMarkdown?: string
  data: TData
  actions?: AssistantBlockAction[]
}

export interface VocabCardData {
  word: string
  phonetic?: string
  partOfSpeech?: string
  meanings?: Array<{
    text: string
    usage?: string
  }>
  examples?: Array<{
    en: string
    zh?: string
  }>
  collocations?: Array<{
    phrase: string
    meaning?: string
  }>
  memoryTip?: string
}

export interface GrammarTreeNode {
  id: string
  label: string
  description?: string
  examples?: string[]
  children?: GrammarTreeNode[]
}

export interface GrammarTreeData {
  topic: string
  root: GrammarTreeNode
}

export interface StudyPlanData {
  title: string
  durationDays?: number
  goal?: string
  days?: Array<{
    day: number
    title: string
    focus?: string
    tasks?: Array<{
      title: string
      minutes?: number
      output?: string
    }>
    check?: string
  }>
}

export interface SentenceAnalysisData {
  sentence: string
  translation?: string
  structure?: {
    subject?: string
    predicate?: string
    object?: string
    complement?: string
  }
  chunks?: Array<{
    text: string
    role: string
    explanation?: string
  }>
  grammarPoints?: Array<{
    name: string
    explanation?: string
  }>
  improvedVersions?: string[]
}

export type VocabCardBlock = AssistantBlockBase<'vocab_card', VocabCardData>
export type GrammarTreeBlock = AssistantBlockBase<'grammar_tree', GrammarTreeData>
export type StudyPlanBlock = AssistantBlockBase<'study_plan', StudyPlanData>
export type SentenceAnalysisBlock = AssistantBlockBase<'sentence_analysis', SentenceAnalysisData>

export type AssistantBlock =
  | VocabCardBlock
  | GrammarTreeBlock
  | StudyPlanBlock
  | SentenceAnalysisBlock

const assistantBlockTypes = new Set<AssistantBlockType>([
  'vocab_card',
  'grammar_tree',
  'study_plan',
  'sentence_analysis',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function isAssistantBlockType(value: unknown): value is AssistantBlockType {
  return typeof value === 'string' && assistantBlockTypes.has(value as AssistantBlockType)
}

function hasText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isValidGrammarNode(value: unknown): value is GrammarTreeNode {
  if (!isRecord(value)) return false
  if (!hasText(value.id) || !hasText(value.label)) return false
  if (value.children !== undefined && !Array.isArray(value.children)) return false
  if (Array.isArray(value.children) && !value.children.every(isValidGrammarNode)) return false
  return true
}

function isValidBlockData(type: AssistantBlockType, data: Record<string, unknown>) {
  if (type === 'vocab_card') {
    return hasText(data.word)
  }
  if (type === 'grammar_tree') {
    return hasText(data.topic) && isValidGrammarNode(data.root)
  }
  if (type === 'study_plan') {
    return hasText(data.title)
  }
  if (type === 'sentence_analysis') {
    return hasText(data.sentence)
  }
  return false
}

function normalizeActions(value: unknown): AssistantBlockAction[] | undefined {
  if (!Array.isArray(value)) return undefined

  const actions = value
    .map((action): AssistantBlockAction | null => {
      if (!isRecord(action)) return null
      if (
        typeof action.id !== 'string' ||
        typeof action.label !== 'string' ||
        typeof action.prompt !== 'string'
      ) {
        return null
      }
      return {
        id: action.id,
        label: action.label,
        prompt: action.prompt,
      }
    })
    .filter((action): action is AssistantBlockAction => Boolean(action))

  return actions.length > 0 ? actions : undefined
}

export function normalizeAssistantBlocks(value: unknown): AssistantBlock[] {
  if (!Array.isArray(value)) return []

  return value
    .map((block): AssistantBlock | null => {
      if (!isRecord(block)) return null
      if (typeof block.id !== 'string' || !block.id.trim()) return null
      if (!isAssistantBlockType(block.type)) return null
      if (block.version !== 1) return null
      if (!isRecord(block.data)) return null
      if (!isValidBlockData(block.type, block.data)) return null

      const base = {
        id: block.id,
        version: 1 as const,
        title: typeof block.title === 'string' ? block.title : undefined,
        fallbackMarkdown: typeof block.fallbackMarkdown === 'string' ? block.fallbackMarkdown : undefined,
        actions: normalizeActions(block.actions),
      }

      if (block.type === 'vocab_card') {
        return {
          ...base,
          type: block.type,
          data: block.data as unknown as VocabCardData,
        }
      }
      if (block.type === 'grammar_tree') {
        return {
          ...base,
          type: block.type,
          data: block.data as unknown as GrammarTreeData,
        }
      }
      if (block.type === 'study_plan') {
        return {
          ...base,
          type: block.type,
          data: block.data as unknown as StudyPlanData,
        }
      }
      return {
        ...base,
        type: block.type,
        data: block.data as unknown as SentenceAnalysisData,
      }
    })
    .filter((block): block is AssistantBlock => Boolean(block))
}
