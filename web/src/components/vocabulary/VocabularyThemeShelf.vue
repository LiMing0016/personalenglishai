<template>
  <section class="theme-shelf" aria-labelledby="theme-shelf-heading">
    <header class="theme-shelf__header">
      <div>
        <h3 id="theme-shelf-heading">生成主题</h3>
        <p>选择这批单词卡的学习侧重点</p>
      </div>
      <RouterLink to="/app/vocabulary/themes">管理全部主题</RouterLink>
    </header>

    <p v-if="loading" class="theme-shelf__state" role="status">主题加载中...</p>
    <p v-else-if="error" class="theme-shelf__state theme-shelf__state--error" role="alert">
      主题加载失败，请稍后重试或前往主题库检查。
    </p>
    <p v-else-if="!visibleThemes.length" class="theme-shelf__state">
      暂无可用主题，请先新建或启用一个主题。
    </p>

    <div class="theme-shelf__items" aria-label="快捷主题">
      <template v-if="!loading && !error">
        <button
          v-for="theme in visibleThemes"
          :key="theme.themeUid"
          type="button"
          class="theme-shelf__theme"
          :class="{ 'theme-shelf__theme--selected': selectedThemeUid === theme.themeUid }"
          :aria-pressed="selectedThemeUid === theme.themeUid"
          @click="emit('select', theme.themeUid)"
        >
          <strong>{{ theme.name }}</strong>
          <span>{{ theme.themeUid === catalog?.defaultThemeUid ? '默认主题' : '最近使用' }}</span>
        </button>
      </template>
      <RouterLink class="theme-shelf__create" to="/app/vocabulary/themes">
        <span aria-hidden="true">+</span>
        <strong>新建主题</strong>
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { VocabularyTheme, VocabularyThemeCatalog } from '@/api/vocabulary'

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

const visibleThemes = computed(() => {
  const catalog = props.catalog
  if (!catalog) return []

  const activeThemes = [...catalog.systemThemes, ...catalog.userThemes]
    .filter((theme) => theme.status === 'active')
  const themesByUid = new Map(activeThemes.map((theme) => [theme.themeUid, theme]))
  const seenThemeUids = new Set<string>()
  const orderedThemes: VocabularyTheme[] = []

  for (const themeUid of [catalog.defaultThemeUid, ...catalog.recentThemeUids]) {
    const theme = themesByUid.get(themeUid)
    if (!theme || seenThemeUids.has(themeUid)) continue
    seenThemeUids.add(themeUid)
    orderedThemes.push(theme)
  }

  return orderedThemes.slice(0, 3)
})
</script>

<style scoped>
.theme-shelf { min-width: 0; padding-bottom: 14px; border-bottom: 1px solid #edf2f7; }
.theme-shelf__header { display: flex; align-items: end; justify-content: space-between; gap: 12px; }
.theme-shelf__header h3, .theme-shelf__header p { margin: 0; }
.theme-shelf__header h3 { color: #0f172a; font-size: 14px; }
.theme-shelf__header p { margin-top: 3px; color: #64748b; font-size: 12px; }
.theme-shelf__header a { flex: 0 0 auto; color: #047857; font-size: 12px; font-weight: 800; text-decoration: none; }
.theme-shelf__header a:hover, .theme-shelf__header a:focus-visible { text-decoration: underline; outline: none; }
.theme-shelf__state { display: grid; box-sizing: border-box; min-height: 56px; margin: 10px 0 0; place-items: center; border: 1px dashed #cbd5e1; border-radius: 6px; color: #64748b; font-size: 13px; padding: 8px 12px; text-align: center; }
.theme-shelf__state--error { border-color: #fecaca; background: #fff7f7; color: #b91c1c; }
.theme-shelf__items { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; min-width: 0; margin-top: 10px; }
.theme-shelf__theme, .theme-shelf__create { display: grid; box-sizing: border-box; min-width: 0; height: 58px; align-content: center; gap: 3px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #475569; font: inherit; padding: 7px 9px; text-align: left; text-decoration: none; }
.theme-shelf__theme { cursor: pointer; }
.theme-shelf__theme strong, .theme-shelf__create strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.theme-shelf__theme span { color: #64748b; font-size: 11px; }
.theme-shelf__theme:hover, .theme-shelf__theme:focus-visible, .theme-shelf__create:hover, .theme-shelf__create:focus-visible { border-color: #5eead4; outline: none; }
.theme-shelf__theme--selected { border-color: #14b8a6; background: #ecfdf5; color: #047857; box-shadow: inset 0 0 0 1px #14b8a6; }
.theme-shelf__create { grid-template-columns: 22px minmax(0, 1fr); align-items: center; background: #fff; color: #047857; }
.theme-shelf__create span { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: #dcfce7; font-size: 18px; line-height: 1; }

@media (max-width: 680px) {
  .theme-shelf__items { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 420px) {
  .theme-shelf__header { align-items: flex-start; flex-direction: column; gap: 7px; }
}
</style>
