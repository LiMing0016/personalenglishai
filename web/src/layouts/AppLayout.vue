<template>
  <div
    class="app-layout"
    :class="{
      immersive,
      'app-layout--writing': isWritingRoute,
      'app-layout--assistant': isAssistantRoute,
      'app-layout--rail-collapsed': railCollapsed,
      'app-layout--rail-expanded': !railCollapsed,
    }"
    @mouseup="handleSelectionChange"
    @keyup="handleSelectionChange"
  >
    <button
      v-if="selectionToolbar.visible"
      type="button"
      class="selection-toolbar"
      :style="selectionToolbarStyle"
      @mousedown.prevent
      @click="askAssistantWithSelection"
    >
      询问 AI 助手
    </button>

    <AppRail
      v-if="!isAssistantRoute"
      :collapsed="railCollapsed"
      @open-assistant-drawer="openAssistantDrawer"
      @toggle-rail="toggleRail"
    />
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppRail from '@/components/AppRail.vue'
import {
  buildAskAssistantPrompt,
  buildPendingAssistantSelection,
  PENDING_ASSISTANT_PROMPT_KEY,
  PENDING_ASSISTANT_SELECTION_KEY,
} from '@/pages/app/assistantMessageActions.ts'
import { shouldOpenAssistantDrawerForSelection } from './appSelectionToolbar.ts'

const RAIL_COLLAPSED_STORAGE_KEY = 'peai:app-rail-collapsed'
const route = useRoute()
const router = useRouter()
const immersiveOverride = ref<boolean | null>(null)
const assistantDrawerOpen = ref(false)
const railCollapsed = ref(readRailCollapsedPreference())
const selectionToolbar = reactive({
  visible: false,
  text: '',
  left: 0,
  top: 0,
})
provide('setImmersive', (v: boolean | null) => { immersiveOverride.value = v })
provide('assistantDrawerOpen', assistantDrawerOpen)
const immersive = computed(() =>
  immersiveOverride.value !== null ? immersiveOverride.value : Boolean(route.meta.immersive)
)
const isWritingRoute = computed(() => route.path.startsWith('/app/writing'))
const isAssistantRoute = computed(() => route.path.startsWith('/app/assistant'))
const selectionToolbarStyle = computed(() => ({
  left: `${selectionToolbar.left}px`,
  top: `${selectionToolbar.top}px`,
}))

function openAssistantDrawer() {
  assistantDrawerOpen.value = true
  if (route.path !== '/app/assistant') {
    void router.push('/app/assistant')
  }
}

function readRailCollapsedPreference() {
  if (typeof window === 'undefined') return false
  return localStorage.getItem(RAIL_COLLAPSED_STORAGE_KEY) === 'true'
}

function persistRailCollapsedPreference(collapsed: boolean) {
  localStorage.setItem(RAIL_COLLAPSED_STORAGE_KEY, String(collapsed))
}

function toggleRail() {
  railCollapsed.value = !railCollapsed.value
  persistRailCollapsedPreference(railCollapsed.value)
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function hideSelectionToolbar() {
  selectionToolbar.visible = false
  selectionToolbar.text = ''
}

function handleSelectionChange(event: Event) {
  const target = event.target as HTMLElement | null
  if (target?.closest('textarea, input, button, .selection-toolbar')) {
    return
  }

  const selection = window.getSelection()
  const selectedText = selection?.toString().trim() ?? ''
  if (!selection || selectedText.length === 0 || selection.rangeCount === 0) {
    hideSelectionToolbar()
    return
  }

  const range = selection.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  if (rect.width === 0 && rect.height === 0) {
    hideSelectionToolbar()
    return
  }

  selectionToolbar.text = selectedText
  selectionToolbar.left = clamp(rect.left + rect.width / 2 - 58, 8, window.innerWidth - 132)
  selectionToolbar.top = clamp(rect.top - 48, 8, window.innerHeight - 48)
  selectionToolbar.visible = true
}

function askAssistantWithSelection() {
  const prompt = buildAskAssistantPrompt(selectionToolbar.text)
  const pendingSelection = buildPendingAssistantSelection(selectionToolbar.text)
  if (!prompt || !pendingSelection) return
  sessionStorage.setItem(PENDING_ASSISTANT_PROMPT_KEY, prompt)
  sessionStorage.setItem(PENDING_ASSISTANT_SELECTION_KEY, JSON.stringify(pendingSelection))
  window.dispatchEvent(
    new CustomEvent('peai:assistant:use-prompt', {
      detail: {
        prompt,
        selection: pendingSelection,
      },
    }),
  )
  hideSelectionToolbar()
  window.getSelection()?.removeAllRanges()
  if (shouldOpenAssistantDrawerForSelection(route.path)) {
    assistantDrawerOpen.value = true
    void router.push('/app/assistant')
  }
}
</script>

<style scoped>
.app-layout {
  --app-sidebar-border: #d9e2ec;
  min-height: 100vh;
  display: flex;
  flex-direction: row;
  background: #f5f6f7;
  transition: background-color 180ms ease;
}
.app-layout--writing {
  height: 100vh;
  --app-sidebar-border: #e4dfd3;
  overflow: hidden;
  background: #f7f5ef;
}
.app-layout.immersive {
  /* immersive mode: no nav, full height for child */
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  height: 100vh;
  transition: margin-left 180ms ease;
}
.app-layout--writing .app-main {
  overflow-y: auto;
  overscroll-behavior: contain;
  background: #f7f5ef;
  scrollbar-gutter: stable;
}
/* immersive: main takes full viewport */
.app-layout.immersive .app-main {
  height: 100vh;
}
.app-layout:not(.immersive) .app-main {
  height: 100vh;
}

.selection-toolbar {
  position: fixed;
  z-index: 90;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  background: #0f172a;
  color: #ffffff;
  padding: 9px 14px;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.24);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
</style>
