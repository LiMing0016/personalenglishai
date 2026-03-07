<template>
  <div
    ref="elRef"
    class="splitter"
    role="separator"
    aria-orientation="vertical"
    tabindex="0"
    @pointerdown="onPointerDown"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useEventListener } from '@vueuse/core'

const props = defineProps<{
  minRight: number
  maxRight: number
  minEditor: number
}>()

const emit = defineEmits<{
  'update:width': [px: number]
  'drag-start': []
  'drag-end': []
}>()

const elRef = ref<HTMLElement | null>(null)

const dragging = ref(false)

function onPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  emit('drag-start')
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  dragging.value = true
}

function onMove(e: PointerEvent) {
  const viewport = window.innerWidth
  const maxByEditor = Math.max(props.minRight, viewport - props.minEditor)
  const maxRight = Math.min(props.maxRight, maxByEditor)
  let w = viewport - e.clientX
  w = Math.max(props.minRight, Math.min(w, maxRight))
  emit('update:width', w)
}

function onUp(e: PointerEvent) {
  emit('drag-end')
  const el = elRef.value
  if (el) try { el.releasePointerCapture(e.pointerId) } catch (_) {}
  dragging.value = false
}

useEventListener(window, 'pointermove', (e: PointerEvent) => { if (dragging.value) onMove(e) })
useEventListener(window, 'pointerup', (e: PointerEvent) => { if (dragging.value) onUp(e) })
useEventListener(window, 'pointercancel', (e: PointerEvent) => { if (dragging.value) onUp(e) })

</script>

<style scoped>
.splitter {
  flex-shrink: 0;
  width: 8px;
  cursor: col-resize;
  background: #e5e7eb;
  transition: background 0.15s ease;
}
.splitter:hover {
  background: #d1d5db;
}
</style>
