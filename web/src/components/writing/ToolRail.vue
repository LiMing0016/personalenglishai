<template>
  <div class="tool-rail">
    <button
      v-for="item in items"
      :key="item.mode"
      type="button"
      class="rail-btn"
      :class="{ active: activePanel === item.mode }"
      :title="item.title"
      @click="$emit('select', item.mode)"
    >
      <span class="rail-icon" aria-hidden="true">{{ item.icon }}</span>
      <span class="rail-label">{{ item.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
export type PanelMode =
  | 'score'
  | 'rewrite'
  | 'grammarCheck'
  | 'structure'
  | 'improve'
  | 'explain'
  | 'translate'
  | 'archive'
  | 'aiNote'

defineProps<{
  activePanel: PanelMode | null
}>()

defineEmits<{
  select: [mode: PanelMode]
}>()

const items: { mode: PanelMode; label: string; title: string; icon: string }[] = [
  { mode: 'score', label: '评价', title: '作文评价', icon: '◇' },
  { mode: 'grammarCheck', label: '语法', title: '实时语法检查', icon: '✓' },
  { mode: 'rewrite', label: '润色', title: '分级润色', icon: '≡' },
  { mode: 'structure', label: '结构', title: '段落结构', icon: '¶' },
  { mode: 'improve', label: '提升', title: '润色/高级词', icon: '↑' },
  { mode: 'explain', label: '解释', title: '语法解释', icon: '?' },
  { mode: 'translate', label: '翻译', title: '翻译', icon: '⇄' },
  { mode: 'archive', label: '归档', title: '归档', icon: '□' },
  { mode: 'aiNote', label: 'AI助手', title: 'AI 助手', icon: '✦' },
]
</script>

<style scoped>
.tool-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 52px;
  padding: 10px 4px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  box-sizing: border-box;
}
.rail-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  min-height: 40px;
  padding: 4px 6px;
  font-size: 11px;
  color: #0f766e;
  background: transparent;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.rail-btn:hover {
  background: rgba(15, 118, 110, 0.1);
  color: #115e59;
}
.rail-btn.active {
  background: #0f766e;
  color: #fff;
}
.rail-icon {
  font-size: 16px;
  line-height: 1.2;
}
.rail-label {
  margin-top: 2px;
  line-height: 1;
}
</style>
