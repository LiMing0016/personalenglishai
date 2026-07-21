import type {
  AssistantBlock,
  AssistantBlockAction,
  AssistantBlockType,
  FallbackAssistantBlock,
  GrammarTreeData,
  LearningBlockDefinition,
  RenderableAssistantBlock,
  SentenceAnalysisData,
  SentenceReorderData,
  StudyPlanData,
  VocabCardData,
} from './contracts.ts'
import type { AssistantInteractionContext } from '@/types/assistantRequest.ts'
import { grammarTreeFallback, normalizeGrammarTreeData } from './grammar-tree/schema.ts'
import { normalizeSentenceAnalysisData, sentenceAnalysisFallback } from './sentence-analysis/schema.ts'
import { normalizeSentenceReorderData, sentenceReorderFallback } from './sentence-reorder/schema.ts'
import { normalizeStudyPlanData, studyPlanFallback } from './study-plan/schema.ts'
import { normalizeVocabCardData, vocabCardFallback } from './vocab-card/schema.ts'

const definitions: readonly LearningBlockDefinition[] = [
  {
    type: 'vocab_card',
    version: 1,
    kind: 'read_only',
    normalizeData: normalizeVocabCardData,
    buildFallbackMarkdown: (data) => vocabCardFallback(data as VocabCardData),
    loadComponent: () => import('../blocks/VocabCardBlock.vue'),
  },
  {
    type: 'grammar_tree',
    version: 1,
    kind: 'read_only',
    normalizeData: normalizeGrammarTreeData,
    buildFallbackMarkdown: (data) => grammarTreeFallback(data as GrammarTreeData),
    loadComponent: () => import('../blocks/GrammarTreeBlock.vue'),
  },
  {
    type: 'study_plan',
    version: 1,
    kind: 'read_only',
    normalizeData: normalizeStudyPlanData,
    buildFallbackMarkdown: (data) => studyPlanFallback(data as StudyPlanData),
    loadComponent: () => import('../blocks/StudyPlanBlock.vue'),
  },
  {
    type: 'sentence_analysis',
    version: 1,
    kind: 'read_only',
    normalizeData: normalizeSentenceAnalysisData,
    buildFallbackMarkdown: (data) => sentenceAnalysisFallback(data as SentenceAnalysisData),
    loadComponent: () => import('../blocks/SentenceAnalysisBlock.vue'),
  },
  {
    type: 'sentence_reorder',
    version: 1,
    kind: 'interactive',
    normalizeData: normalizeSentenceReorderData,
    buildFallbackMarkdown: (data) => sentenceReorderFallback(data as SentenceReorderData),
    loadComponent: () => import('./sentence-reorder/SentenceReorderBlock.vue'),
  },
]

const definitionByType = new Map(definitions.map((definition) => [definition.type, definition]))

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function hasText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function normalizeActions(value: unknown): AssistantBlockAction[] | undefined {
  if (!Array.isArray(value)) return undefined
  const actions = value.flatMap((action): AssistantBlockAction[] => {
    if (!isRecord(action)) return []
    if (!hasText(action.id) || !hasText(action.label)) return []
    if (hasText(action.prompt)) return [{ id: action.id, label: action.label, prompt: action.prompt }]
    if (!hasText(action.displayText) || !isRecord(action.interaction)) return []
    if (!hasText(action.interaction.source)) return []
    return [{
      id: action.id,
      label: action.label,
      displayText: action.displayText,
      interaction: action.interaction as unknown as AssistantInteractionContext,
    } as AssistantBlockAction]
  })
  return actions.length ? actions : undefined
}

function fallbackBlock(block: Record<string, unknown>): FallbackAssistantBlock | null {
  if (!hasText(block.id) || !hasText(block.type) || !hasText(block.fallbackMarkdown)) return null
  if (typeof block.version !== 'number' || !Number.isFinite(block.version)) return null
  return {
    id: block.id,
    type: '__fallback__',
    version: 1,
    originalType: block.type,
    originalVersion: block.version,
    fallbackMarkdown: block.fallbackMarkdown,
  }
}

function normalizeBlock(value: unknown): RenderableAssistantBlock | null {
  if (!isRecord(value) || !hasText(value.id) || !hasText(value.type)) return null
  const definition = definitionByType.get(value.type as AssistantBlockType)
  if (!definition || value.version !== definition.version) return fallbackBlock(value)

  const data = definition.normalizeData(value.data)
  if (!data) return fallbackBlock(value)

  return {
    id: value.id,
    type: definition.type,
    version: 1,
    title: typeof value.title === 'string' ? value.title : undefined,
    fallbackMarkdown: hasText(value.fallbackMarkdown)
      ? value.fallbackMarkdown
      : definition.buildFallbackMarkdown(data),
    data,
    actions: normalizeActions(value.actions),
  } as AssistantBlock
}

export function normalizeAssistantBlocks(value: unknown): RenderableAssistantBlock[] {
  if (!Array.isArray(value)) return []
  return value
    .map(normalizeBlock)
    .filter((block): block is RenderableAssistantBlock => block !== null)
}

export function isFallbackAssistantBlock(
  block: RenderableAssistantBlock,
): block is FallbackAssistantBlock {
  return block.type === '__fallback__'
}

export function definitionFor(block: RenderableAssistantBlock) {
  if (isFallbackAssistantBlock(block)) return undefined
  return definitionByType.get(block.type)
}
