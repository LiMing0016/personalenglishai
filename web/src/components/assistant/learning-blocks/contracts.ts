import type { Component } from 'vue'
import type { AssistantInteractionContext } from '@/types/assistantRequest.ts'

export type AssistantBlockType =
  | 'vocab_card'
  | 'grammar_tree'
  | 'study_plan'
  | 'sentence_analysis'
  | 'sentence_reorder'

export interface AssistantBlockPromptAction {
  id: string
  label: string
  prompt: string
}

export interface AssistantBlockInteractionAction {
  id: string
  label: string
  displayText: string
  interaction: AssistantInteractionContext
}

export type AssistantBlockAction = AssistantBlockPromptAction | AssistantBlockInteractionAction

export interface AssistantBlockBase<TType extends AssistantBlockType, TData> {
  id: string
  type: TType
  version: 1
  title?: string
  fallbackMarkdown: string
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

export interface SentenceReorderToken {
  id: string
  text: string
}

export interface SentenceReorderItem {
  id: string
  instruction: string
  translation?: string
  tokens: SentenceReorderToken[]
  initialOrder: string[]
  acceptedOrders: string[][]
  explanation?: string
  hint?: string
}

export interface SentenceReorderData {
  activityId: string
  items: SentenceReorderItem[]
}

export type VocabCardBlock = AssistantBlockBase<'vocab_card', VocabCardData>
export type GrammarTreeBlock = AssistantBlockBase<'grammar_tree', GrammarTreeData>
export type StudyPlanBlock = AssistantBlockBase<'study_plan', StudyPlanData>
export type SentenceAnalysisBlock = AssistantBlockBase<'sentence_analysis', SentenceAnalysisData>
export type SentenceReorderBlock = AssistantBlockBase<'sentence_reorder', SentenceReorderData>

export type AssistantBlock =
  | VocabCardBlock
  | GrammarTreeBlock
  | StudyPlanBlock
  | SentenceAnalysisBlock
  | SentenceReorderBlock

export interface FallbackAssistantBlock {
  id: string
  type: '__fallback__'
  version: 1
  originalType: string
  originalVersion: number
  fallbackMarkdown: string
}

export type RenderableAssistantBlock = AssistantBlock | FallbackAssistantBlock

export interface LearningBlockDefinition {
  type: AssistantBlockType
  version: 1
  kind: 'read_only' | 'interactive'
  normalizeData(value: unknown): AssistantBlock['data'] | null
  buildFallbackMarkdown(data: AssistantBlock['data']): string
  loadComponent: () => Promise<{ default: Component }>
}
