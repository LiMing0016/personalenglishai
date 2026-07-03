import assert from 'node:assert/strict'
import {
  buildLearningModuleGroups,
  demoKnowledgeGraph,
  demoLearningIdeContext,
  demoModuleCatalog,
  resolveBacklinksForKnowledgeNode,
} from '../src/pages/app/learningIdeMock.ts'

assert.ok(
  demoModuleCatalog.some((module) => module.id === 'pdf-explainer'),
  'module catalog should include PDF explanation as a base tool',
)
assert.ok(
  demoModuleCatalog.some((module) => module.id === 'mistake-book'),
  'module catalog should include mistake book for practice loops',
)
assert.ok(
  demoModuleCatalog.some((module) => module.id === 'word-cards'),
  'module catalog should include word cards for language learning',
)
assert.ok(
  demoModuleCatalog.some((module) => module.id === 'knowledge-cards'),
  'module catalog should include knowledge cards as the graph backbone',
)

const groups = buildLearningModuleGroups(demoModuleCatalog)
assert.ok(
  groups.some((group) => group.id === 'base' && group.modules.some((module) => module.id === 'pdf-explainer')),
  'base group should contain PDF explanation',
)
assert.ok(
  groups.some((group) => group.id === 'practice' && group.modules.some((module) => module.id === 'mistake-book')),
  'practice group should contain mistake book',
)

const quadraticBacklinks = resolveBacklinksForKnowledgeNode(
  demoLearningIdeContext,
  'knowledge-quadratic-function',
)
assert.ok(
  quadraticBacklinks.some((item) => item.sourceType === 'pdf-selection'),
  'knowledge card should surface PDF selection backlinks',
)
assert.ok(
  quadraticBacklinks.some((item) => item.sourceType === 'note'),
  'knowledge card should surface note backlinks',
)
assert.ok(
  quadraticBacklinks.some((item) => item.sourceType === 'mistake'),
  'knowledge card should surface mistake backlinks',
)
assert.ok(
  quadraticBacklinks.every((item) => item.blockRef && item.tags.length > 0),
  'backlinks should keep block-level references and tags',
)

assert.ok(
  demoLearningIdeContext.wikiLinks.some((link) => link.raw === '[[二次函数]]'),
  'context should preserve wiki-style links',
)
assert.ok(
  demoLearningIdeContext.tags.some((tag) => tag.path === '#数学/函数'),
  'context should expose hierarchical tags',
)
assert.ok(
  demoKnowledgeGraph.nodes.some((node) => node.type === 'pdf-selection'),
  'knowledge graph should include PDF selection nodes',
)
assert.ok(
  demoKnowledgeGraph.nodes.some((node) => node.type === 'mistake'),
  'knowledge graph should include mistake nodes',
)
assert.ok(
  demoKnowledgeGraph.edges.some((edge) => edge.relation === 'references'),
  'knowledge graph should include reference edges',
)
