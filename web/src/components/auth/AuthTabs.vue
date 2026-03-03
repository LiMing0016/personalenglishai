<template>
  <div class="auth-tabs">
    <button
      v-for="tab in tabs"
      :key="tab.value"
      class="auth-tab"
      :class="{ active: modelValue === tab.value }"
      @click="$emit('update:modelValue', tab.value)"
    >
      {{ tab.label }}
    </button>
    <div class="auth-tab-indicator" :style="indicatorStyle" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Tab {
  label: string
  value: string
}

const props = defineProps<{
  tabs: Tab[]
  modelValue: string
}>()

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const indicatorStyle = computed(() => {
  const idx = props.tabs.findIndex((t) => t.value === props.modelValue)
  const width = 100 / props.tabs.length
  return {
    width: `${width}%`,
    transform: `translateX(${idx * 100}%)`,
  }
})
</script>

<style scoped>
.auth-tabs {
  position: relative;
  display: flex;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 3px;
  margin-bottom: 8px;
}

.auth-tab {
  flex: 1;
  position: relative;
  z-index: 1;
  padding: 8px 0;
  border: none;
  background: none;
  color: rgba(225, 235, 255, 0.55);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.25s;
  border-radius: 10px;
}

.auth-tab.active {
  color: #fff;
}

.auth-tab:hover:not(.active) {
  color: rgba(225, 235, 255, 0.8);
}

.auth-tab-indicator {
  position: absolute;
  bottom: 3px;
  left: 3px;
  height: calc(100% - 6px);
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(53, 192, 255, 0.25) 0%, rgba(111, 107, 255, 0.25) 100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
}
</style>
