export type {
  AssistantBlock,
  AssistantBlockAction,
  AssistantBlockBase,
  AssistantBlockType,
  FallbackAssistantBlock,
  GrammarTreeBlock,
  GrammarTreeData,
  GrammarTreeNode,
  LearningBlockDefinition,
  RenderableAssistantBlock,
  SentenceAnalysisBlock,
  SentenceAnalysisData,
  SentenceReorderBlock,
  SentenceReorderData,
  SentenceReorderItem,
  SentenceReorderToken,
  StudyPlanBlock,
  StudyPlanData,
  VocabCardBlock,
  VocabCardData,
} from '@/components/assistant/learning-blocks/contracts.ts'

export { normalizeAssistantBlocks } from '@/components/assistant/learning-blocks/registry.ts'
