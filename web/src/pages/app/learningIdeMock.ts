import type {
  KnowledgeBacklink,
  KnowledgeCard,
  KnowledgeGraph,
  LearningIdeContext,
  LearningModule,
  LearningModuleGroup,
  LearningModuleGroupId,
  LearningTag,
} from '../../types/learningIde'

const moduleGroupMeta: Record<LearningModuleGroupId, Pick<LearningModuleGroup, 'id' | 'label' | 'description'>> = {
  base: {
    id: 'base',
    label: '基础工具',
    description: 'PDF 讲解、笔记、学习产出等所有学段都能使用的核心模块。',
  },
  language: {
    id: 'language',
    label: '语言学习',
    description: '单词卡、表达卡和语言材料沉淀，适合英语与外语场景。',
  },
  practice: {
    id: 'practice',
    label: '应试学习',
    description: '错题本、题库练习、复习计划，支撑小学到考研的刷题闭环。',
  },
  research: {
    id: 'research',
    label: '高阶学习',
    description: '论文卡片、引用管理和专题研究，覆盖本科到硕士阶段。',
  },
}

export const demoModuleCatalog: LearningModule[] = [
  {
    id: 'pdf-explainer',
    groupId: 'base',
    label: 'PDF 讲解',
    description: '对教材、论文、讲义做选区解释、块级引用和上下文提问。',
    icon: 'PDF',
    status: 'enabled',
    enabledByDefault: true,
  },
  {
    id: 'notes',
    groupId: 'base',
    label: '笔记',
    description: '记录课堂笔记、章节总结和 PDF 锚点笔记。',
    icon: 'N',
    status: 'enabled',
    enabledByDefault: true,
  },
  {
    id: 'learning-output',
    groupId: 'base',
    label: '学习产出',
    description: '把选区、笔记和 AI 回答整理成可复习的结构化内容。',
    icon: 'O',
    status: 'enabled',
    enabledByDefault: true,
  },
  {
    id: 'knowledge-cards',
    groupId: 'base',
    label: '知识卡',
    description: '用双向链接、反向链接、标签和图谱组织知识点。',
    icon: 'K',
    status: 'enabled',
    enabledByDefault: true,
  },
  {
    id: 'word-cards',
    groupId: 'language',
    label: '单词卡',
    description: '沉淀生词、短语、例句和复习记录。',
    icon: 'W',
    status: 'available',
    enabledByDefault: false,
  },
  {
    id: 'mistake-book',
    groupId: 'practice',
    label: '错题本',
    description: '收集错题、薄弱点、原因和重做记录。',
    icon: 'M',
    status: 'available',
    enabledByDefault: false,
  },
  {
    id: 'question-bank',
    groupId: 'practice',
    label: '题库练习',
    description: '从资料、知识卡和错题生成练习。',
    icon: 'Q',
    status: 'available',
    enabledByDefault: false,
  },
  {
    id: 'flash-review',
    groupId: 'practice',
    label: '闪卡复习',
    description: '把知识卡、单词卡和错题加入间隔复习。',
    icon: 'F',
    status: 'available',
    enabledByDefault: false,
  },
  {
    id: 'paper-cards',
    groupId: 'research',
    label: '论文卡片',
    description: '整理论文观点、方法、引用和研究问题。',
    icon: 'P',
    status: 'available',
    enabledByDefault: false,
  },
]

export function buildLearningModuleGroups(modules: LearningModule[]): LearningModuleGroup[] {
  return (Object.keys(moduleGroupMeta) as LearningModuleGroupId[])
    .map((groupId) => ({
      ...moduleGroupMeta[groupId],
      modules: modules.filter((module) => module.groupId === groupId),
    }))
    .filter((group) => group.modules.length > 0)
}

const functionTag: LearningTag = {
  id: 'tag-math-function',
  path: '#数学/函数',
  label: '函数',
  color: '#0f8f89',
}

const mistakeTag: LearningTag = {
  id: 'tag-mistake-symbol',
  path: '#易错/符号判断',
  label: '符号判断',
  color: '#ef4444',
}

const reviewTag: LearningTag = {
  id: 'tag-review-pending',
  path: '#待复习',
  label: '待复习',
  color: '#2563eb',
}

export const demoKnowledgeCard: KnowledgeCard = {
  id: 'knowledge-quadratic-function',
  title: '二次函数',
  summary: '形如 y = ax^2 + bx + c 且 a 不等于 0 的函数，图像是抛物线，开口方向由 a 的符号决定。',
  aliases: ['Quadratic Function', '抛物线函数'],
  tags: [functionTag, reviewTag],
  blockRefs: [
    {
      id: 'blockref-pdf-8-2-2',
      sourceId: 'pdf-selection-8-2-2-property-1',
      sourceType: 'pdf-selection',
      pageNumber: 128,
      bbox: '0.216,0.312,0.754,0.352',
      excerpt: '图像是一条抛物线，开口方向由 a 的符号决定。',
    },
  ],
}

export const demoKnowledgeGraph: KnowledgeGraph = {
  nodes: [
    { id: 'knowledge-quadratic-function', type: 'knowledge-card', label: '二次函数', weight: 9 },
    { id: 'pdf-section-8-2-2', type: 'pdf-section', label: '8.2.2 二次函数的性质', weight: 7 },
    { id: 'pdf-selection-8-2-2-property-1', type: 'pdf-selection', label: '性质 1 选区', weight: 6 },
    { id: 'note-quadratic-opening', type: 'note', label: '开口方向笔记', weight: 5 },
    { id: 'mistake-symbol-a', type: 'mistake', label: '忽略二次项系数符号', weight: 5 },
    { id: 'word-parabola', type: 'word-card', label: 'parabola', weight: 3 },
  ],
  edges: [
    {
      id: 'edge-section-card',
      source: 'pdf-section-8-2-2',
      target: 'knowledge-quadratic-function',
      relation: 'references',
    },
    {
      id: 'edge-selection-card',
      source: 'pdf-selection-8-2-2-property-1',
      target: 'knowledge-quadratic-function',
      relation: 'references',
    },
    {
      id: 'edge-note-card',
      source: 'note-quadratic-opening',
      target: 'knowledge-quadratic-function',
      relation: 'backlinks',
    },
    {
      id: 'edge-mistake-card',
      source: 'mistake-symbol-a',
      target: 'knowledge-quadratic-function',
      relation: 'reviews',
    },
  ],
}

export const demoLearningIdeContext: LearningIdeContext = {
  moduleCatalog: demoModuleCatalog,
  activeKnowledgeCard: demoKnowledgeCard,
  wikiLinks: [
    {
      id: 'wikilink-note-quadratic',
      raw: '[[二次函数]]',
      label: '二次函数',
      targetId: 'knowledge-quadratic-function',
      sourceId: 'note-quadratic-opening',
    },
    {
      id: 'wikilink-mistake-quadratic',
      raw: '[[二次函数]]',
      label: '二次函数',
      targetId: 'knowledge-quadratic-function',
      sourceId: 'mistake-symbol-a',
    },
  ],
  tags: [functionTag, mistakeTag, reviewTag],
  backlinks: [
    {
      id: 'backlink-pdf-selection',
      targetId: 'knowledge-quadratic-function',
      sourceId: 'pdf-selection-8-2-2-property-1',
      sourceType: 'pdf-selection',
      title: 'PDF 选区 · 性质 1',
      excerpt: '图像是一条抛物线，开口方向由 a 的符号决定。',
      blockRef: demoKnowledgeCard.blockRefs[0],
      tags: [functionTag],
    },
    {
      id: 'backlink-note-opening',
      targetId: 'knowledge-quadratic-function',
      sourceId: 'note-quadratic-opening',
      sourceType: 'note',
      title: '开口方向笔记',
      excerpt: '当 a > 0 开口向上，当 a < 0 开口向下。这个判断要先看二次项系数。',
      blockRef: {
        id: 'blockref-note-opening',
        sourceId: 'note-quadratic-opening',
        sourceType: 'note',
        excerpt: '[[二次函数]] 的开口方向只由 a 决定。',
      },
      tags: [functionTag, reviewTag],
    },
    {
      id: 'backlink-mistake-symbol',
      targetId: 'knowledge-quadratic-function',
      sourceId: 'mistake-symbol-a',
      sourceType: 'mistake',
      title: '错题 · 忽略二次项系数符号',
      excerpt: '把 a < 0 的抛物线误判成开口向上。',
      blockRef: {
        id: 'blockref-mistake-symbol',
        sourceId: 'mistake-symbol-a',
        sourceType: 'mistake',
        pageNumber: 128,
        excerpt: '错因：没有先确定 a 的符号。',
      },
      tags: [mistakeTag, reviewTag],
    },
  ],
  graph: demoKnowledgeGraph,
  outputs: [
    { id: 'output-structured-note', label: '结构化笔记', count: 1, status: 'ready' },
    { id: 'output-knowledge-points', label: '知识点', count: 4, status: 'ready' },
    { id: 'output-quiz', label: '小测', count: 3, status: 'draft' },
    { id: 'output-flashcards', label: '闪卡', count: 6, status: 'reviewing' },
  ],
}

export function resolveBacklinksForKnowledgeNode(
  context: LearningIdeContext,
  nodeId: string,
): KnowledgeBacklink[] {
  return context.backlinks.filter((backlink) => backlink.targetId === nodeId)
}
