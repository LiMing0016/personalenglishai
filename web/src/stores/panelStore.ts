import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { PanelMode } from '@/components/writing/ToolRail.vue'
import {
  clampRatio,
  computePanelWidthByRatio,
  loadLayout,
  loadSplitRatio,
  saveLayout,
  saveSplitRatio,
  DEFAULT_SPLIT_RATIO,
} from '@/components/writing/editorShellStorage'

export const MIN_PANEL_WIDTH = 420
export const MAX_PANEL_WIDTH = 1280
export const MIN_LEFT_WIDTH = 360

const VALID_PANELS: PanelMode[] = [
  'score', 'rewrite', 'grammarCheck', 'structure', 'improve', 'explain', 'translate', 'aiNote',
]

export const usePanelStore = defineStore('panel', () => {
  const activePanel = ref<PanelMode | null>(null)
  const splitRatio = ref(DEFAULT_SPLIT_RATIO)
  const dockWidth = ref(480)
  const resizing = ref(false)

  const panelTitle = computed(() => {
    const t: Record<PanelMode, string> = {
      score: '作文评价',
      grammarCheck: '语法检查',
      rewrite: 'AI 改写',
      structure: '段落结构',
      improve: '提升',
      explain: '解释',
      translate: '翻译',
      aiNote: 'AI 助手',
    }
    return activePanel.value != null ? t[activePanel.value] : ''
  })

  const assistantOpen = computed(() => activePanel.value === 'aiNote')

  const layoutStyle = computed(() => ({
    '--rightWidth': activePanel.value !== null ? `${dockWidth.value}px` : '0px',
    '--splitter-width': activePanel.value !== null ? '8px' : '0px',
  }))

  function initLayout() {
    const layout = loadLayout(VALID_PANELS)
    activePanel.value = layout.activePanel
    splitRatio.value = loadSplitRatio()
    recalcDockWidth()
    return layout
  }

  function recalcDockWidth() {
    dockWidth.value = computePanelWidthByRatio(
      splitRatio.value, window.innerWidth,
      MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, MIN_LEFT_WIDTH,
    )
  }

  function selectPanel(mode: PanelMode) {
    activePanel.value = activePanel.value === mode ? null : mode
  }

  function updateDockWidth(width: number) {
    const viewportWidth = window.innerWidth
    const maxByEditor = Math.max(0, viewportWidth - MIN_LEFT_WIDTH)
    const maxPanelByViewport = Math.min(MAX_PANEL_WIDTH, maxByEditor)
    const minPanelByViewport = Math.min(MIN_PANEL_WIDTH, maxPanelByViewport)
    dockWidth.value = Math.max(minPanelByViewport, Math.min(width, maxPanelByViewport))
    splitRatio.value = clampRatio(dockWidth.value / window.innerWidth)
  }

  function finishDrag() {
    resizing.value = false
    saveSplitRatio(splitRatio.value)
  }

  function saveState() {
    saveLayout({
      rightPanelOpen: activePanel.value !== null,
      activePanel: activePanel.value,
    })
  }

  return {
    activePanel,
    splitRatio,
    dockWidth,
    resizing,
    panelTitle,
    assistantOpen,
    layoutStyle,
    initLayout,
    recalcDockWidth,
    selectPanel,
    updateDockWidth,
    finishDrag,
    saveState,
  }
})
