import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const componentNames = [
  'LearningIdeTopBar',
  'LearningResourcePanel',
  'LearningModuleLibrary',
  'WorkspaceTabs',
  'KnowledgeCardView',
  'BacklinksPanel',
  'LocalGraphPanel',
  'ContextAssistantPanel',
  'LearningOutputDock',
  'PdfSelectionActionToolbar',
]

for (const componentName of componentNames) {
  const componentUrl = new URL(`../src/components/learning-ide/${componentName}.vue`, import.meta.url)
  assert.ok(existsSync(componentUrl), `${componentName} should exist as a dedicated learning IDE component`)

  const source = readFileSync(componentUrl, 'utf8')
  assert.ok(source.includes('<script setup lang="ts">'), `${componentName} should use typed script setup`)
  assert.ok(source.includes('defineProps'), `${componentName} should expose an explicit prop contract`)
}

const topBarSource = readFileSync(
  new URL('../src/components/learning-ide/LearningIdeTopBar.vue', import.meta.url),
  'utf8',
)
assert.ok(topBarSource.includes('添加学习工具'), 'top bar should expose the module library entry')
assert.ok(topBarSource.includes("defineEmits"), 'top bar should emit page-level intent events')
assert.ok(
  !topBarSource.includes('{{ currentSpace }}') && !topBarSource.includes('currentSpace?:'),
  'top bar should not display the current file name beside the brand',
)

const resourcePanelSource = readFileSync(
  new URL('../src/components/learning-ide/LearningResourcePanel.vue', import.meta.url),
  'utf8',
)
assert.ok(
  !resourcePanelSource.includes('learning-resource-panel__header')
    && !resourcePanelSource.includes('learning-resource-panel__document')
    && !resourcePanelSource.includes('aria-label="当前资料"'),
  'resource panel should not duplicate the resource summary or current file detail above the resource tree',
)
assert.ok(
  resourcePanelSource.includes('learning-resource-panel__scope-tabs')
    && resourcePanelSource.includes('项目')
    && resourcePanelSource.includes('当前文件')
    && resourcePanelSource.includes('selectExplorerView'),
  'resource panel should switch between project resources and the active file outline',
)
assert.ok(
  resourcePanelSource.includes('projectFolders')
    && resourcePanelSource.includes('fileFolders')
    && resourcePanelSource.includes('currentFileTitle')
    && resourcePanelSource.includes('currentFileSubtitle'),
  'resource panel should accept separate project folders and current-file folders',
)
assert.ok(
  resourcePanelSource.includes('renderFolder')
    && resourcePanelSource.includes('folder.children')
    && resourcePanelSource.includes('learning-resource-folder--child'),
  'resource panel should render nested child folders so file outlines can fold by chapter',
)
assert.ok(
  !resourcePanelSource.includes('导入 PDF')
    && !resourcePanelSource.includes('新建笔记')
    && !resourcePanelSource.includes('learning-resource-panel__actions'),
  'resource panel should leave create/import commands to the top menu instead of leading with PDF actions',
)
assert.ok(
  resourcePanelSource.includes('.learning-resource-panel__scope-tabs button')
    && resourcePanelSource.includes('min-height: 32px;')
    && !resourcePanelSource.includes('min-height: 48px;'),
  'resource panel should use compact IDE controls instead of large category tiles',
)

const workspaceTabsSource = readFileSync(
  new URL('../src/components/learning-ide/WorkspaceTabs.vue', import.meta.url),
  'utf8',
)
assert.ok(
  !workspaceTabsSource.includes('tab.subtitle || tab.kind') && !workspaceTabsSource.includes('{{ tab.subtitle'),
  'workspace tabs should show only file names, not source type labels like PDF',
)
assert.ok(
  workspaceTabsSource.includes('min-height: 36px;') && !workspaceTabsSource.includes('min-height: 50px;'),
  'workspace tabs should stay compact instead of using tall two-line tabs',
)

const learningIdeTypesSource = readFileSync(
  new URL('../src/types/learningIde.ts', import.meta.url),
  'utf8',
)
assert.ok(
  learningIdeTypesSource.includes('children?: LearningResourceTreeFolder[]'),
  'learning resource folders should support nested children for chapter-level outline folding',
)

const moduleLibrarySource = readFileSync(
  new URL('../src/components/learning-ide/LearningModuleLibrary.vue', import.meta.url),
  'utf8',
)
assert.ok(moduleLibrarySource.includes('LearningModuleGroup'), 'module library should consume grouped learning modules')

const cardSource = readFileSync(
  new URL('../src/components/learning-ide/KnowledgeCardView.vue', import.meta.url),
  'utf8',
)
assert.ok(cardSource.includes('[[{{ card.title }}]]'), 'knowledge card should render wiki-link style identity')
assert.ok(cardSource.includes('#'), 'knowledge card should render tags')

const backlinkSource = readFileSync(
  new URL('../src/components/learning-ide/BacklinksPanel.vue', import.meta.url),
  'utf8',
)
assert.ok(backlinkSource.includes('反向链接'), 'backlinks panel should make backlinks visible')
assert.ok(backlinkSource.includes('blockRef'), 'backlinks panel should preserve block-level source references')
