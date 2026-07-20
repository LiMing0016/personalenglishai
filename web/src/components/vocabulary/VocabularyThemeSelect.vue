<template>
  <div class="theme-select">
    <label for="vocabulary-theme">生成主题</label>
    <div class="theme-select__control">
      <select
        id="vocabulary-theme"
        :value="selectedThemeUid"
        :disabled="loading || error || !activeThemes.length"
        :aria-describedby="stateMessage ? 'vocabulary-theme-state' : undefined"
        @change="selectTheme"
      >
        <option value="" disabled>选择主题</option>
        <option v-for="theme in activeThemes" :key="theme.themeUid" :value="theme.themeUid">
          {{ theme.name }}
        </option>
      </select>
      <RouterLink to="/app/vocabulary/themes">管理主题</RouterLink>
    </div>
    <p
      v-if="stateMessage"
      id="vocabulary-theme-state"
      :role="error ? 'alert' : 'status'"
      :class="{ 'theme-select__state--error': error }"
    >
      {{ stateMessage }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { VocabularyThemeCatalog } from '@/api/vocabulary'

const props = withDefaults(defineProps<{
  catalog?: VocabularyThemeCatalog
  selectedThemeUid: string
  loading?: boolean
  error?: boolean
}>(), {
  catalog: undefined,
  loading: false,
  error: false,
})

const emit = defineEmits<{
  select: [themeUid: string]
}>()

const activeThemes = computed(() => {
  const catalog = props.catalog
  if (!catalog) return []
  return [...catalog.systemThemes, ...catalog.userThemes]
    .filter((theme) => theme.status === 'active')
})

const stateMessage = computed(() => {
  if (props.loading) return '主题加载中...'
  if (props.error) return '主题加载失败，请稍后重试'
  if (!activeThemes.value.length) return '暂无可用主题，请先创建或启用主题'
  return ''
})

function selectTheme(event: Event) {
  const themeUid = (event.target as HTMLSelectElement).value
  if (activeThemes.value.some((theme) => theme.themeUid === themeUid)) emit('select', themeUid)
}
</script>

<style scoped>
.theme-select { display: grid; min-width: 0; gap: 6px; }
.theme-select > label { color: #334155; font-size: 13px; font-weight: 800; }
.theme-select__control { display: grid; grid-template-columns: minmax(0, 260px) auto; align-items: center; gap: 10px; }
.theme-select select { box-sizing: border-box; width: 100%; min-height: 38px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; padding: 0 34px 0 10px; }
.theme-select select:focus { border-color: #14b8a6; outline: none; box-shadow: 0 0 0 3px rgba(20, 184, 166, .12); }
.theme-select select:disabled { cursor: not-allowed; opacity: .65; }
.theme-select a { color: #047857; font-size: 12px; font-weight: 800; text-decoration: none; white-space: nowrap; }
.theme-select a:hover, .theme-select a:focus-visible { text-decoration: underline; outline: none; }
.theme-select p { margin: 0; color: #64748b; font-size: 12px; }
.theme-select .theme-select__state--error { color: #b91c1c; }
@media (max-width: 520px) { .theme-select__control { grid-template-columns: 1fr; }.theme-select a { justify-self: start; } }
</style>
