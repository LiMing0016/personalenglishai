export type LearningModuleGroupId = 'base' | 'language' | 'practice' | 'research'

export type LearningModuleStatus = 'enabled' | 'available' | 'coming-soon'

export type LearningObjectType =
  | 'knowledge-card'
  | 'pdf-section'
  | 'pdf-selection'
  | 'note'
  | 'mistake'
  | 'word-card'
  | 'quiz'
  | 'tag'

export type LearningGraphRelation =
  | 'references'
  | 'backlinks'
  | 'explains'
  | 'reviews'
  | 'contains'

export interface LearningModule {
  id: string
  groupId: LearningModuleGroupId
  label: string
  description: string
  icon: string
  status: LearningModuleStatus
  enabledByDefault: boolean
}

export interface LearningModuleGroup {
  id: LearningModuleGroupId
  label: string
  description: string
  modules: LearningModule[]
}

export interface WikiLink {
  id: string
  raw: string
  label: string
  targetId: string
  sourceId: string
}

export interface LearningTag {
  id: string
  path: string
  label: string
  color: string
}

export interface BlockReference {
  id: string
  sourceId: string
  sourceType: LearningObjectType
  pageNumber?: number
  bbox?: string | null
  excerpt: string
}

export interface KnowledgeCard {
  id: string
  title: string
  summary: string
  aliases: string[]
  tags: LearningTag[]
  blockRefs: BlockReference[]
}

export interface KnowledgeBacklink {
  id: string
  targetId: string
  sourceId: string
  sourceType: LearningObjectType
  title: string
  excerpt: string
  blockRef: BlockReference
  tags: LearningTag[]
}

export interface KnowledgeGraphNode {
  id: string
  type: LearningObjectType
  label: string
  weight: number
}

export interface KnowledgeGraphEdge {
  id: string
  source: string
  target: string
  relation: LearningGraphRelation
}

export interface KnowledgeGraph {
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
}

export interface LearningOutputItem {
  id: string
  label: string
  count: number
  status: 'draft' | 'ready' | 'reviewing'
}

export interface LearningSidePanelOption {
  id: string
  label: string
  count: number
}

export type LearningResourceExplorerView = 'project' | 'file'

export interface LearningWorkspaceTab {
  id: string
  kind: string
  title: string
  subtitle?: string
  dirty?: boolean
}

export interface LearningResourceTreeItem {
  id: string
  kind: string
  title: string
  subtitle?: string
  count?: number
}

export interface LearningResourceTreeFolder {
  id: string
  label: string
  badge: string
  resources: LearningResourceTreeItem[]
  emptyText: string
  children?: LearningResourceTreeFolder[]
}

export interface LearningAssistantCitation {
  documentId: string
  chunkId: string
  pageNumber: number | null
  elementId: string | null
  bbox: string | null
  quote: string
  sectionPath: string[]
  score: number
}

export interface LearningAssistantMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations?: LearningAssistantCitation[]
}

export interface LearningIdeContext {
  moduleCatalog: LearningModule[]
  activeKnowledgeCard: KnowledgeCard
  wikiLinks: WikiLink[]
  tags: LearningTag[]
  backlinks: KnowledgeBacklink[]
  graph: KnowledgeGraph
  outputs: LearningOutputItem[]
}
