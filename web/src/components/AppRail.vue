<template>
  <aside class="app-rail" aria-label="应用快捷导航">
    <button
      type="button"
      class="rail-brand"
      :title="assistantDrawerOpen ? '关闭边栏' : '打开边栏'"
      :aria-label="assistantDrawerOpen ? '关闭边栏' : '打开边栏'"
      :aria-pressed="assistantDrawerOpen"
      @click="emit('toggleAssistantDrawer')"
    >
      <img src="/brand/peai-logo.png" alt="PEAI" class="rail-logo" />
    </button>

    <RouterLink
      v-for="item in appNavItems"
      :key="item.to"
      :to="item.to"
      class="rail-button rail-nav-link"
      :class="{ 'rail-nav-link--active': isActive(item.activePrefix) }"
      :title="item.label"
      :aria-label="item.label"
    >
      {{ item.shortLabel }}
    </RouterLink>

    <button
      type="button"
      class="rail-button"
      title="新建对话"
      aria-label="新建对话"
      @click="emit('openAssistantDrawer')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 5v14M5 12h14" />
      </svg>
    </button>
    <button
      type="button"
      class="rail-button"
      title="搜索对话"
      aria-label="搜索对话"
      @click="emit('openAssistantDrawer')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="m16 16 4 4" />
      </svg>
    </button>
    <button
      type="button"
      class="rail-button"
      title="历史对话"
      aria-label="历史对话"
      @click="emit('openAssistantDrawer')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-6.2A8 8 0 1 1 21 12Z" />
      </svg>
    </button>

    <div class="rail-spacer" aria-hidden="true"></div>

    <RouterLink
      to="/app/me"
      class="rail-profile-link"
      :class="{ 'rail-profile-link--active': isActive('/app/me') }"
      title="个人中心"
      aria-label="个人中心"
    >
      我
    </RouterLink>
  </aside>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

defineProps<{
  assistantDrawerOpen: boolean
}>()

const emit = defineEmits<{
  openAssistantDrawer: []
  toggleAssistantDrawer: []
}>()

const route = useRoute()

const appNavItems = [
  { to: '/app/writing', activePrefix: '/app/writing', label: '写作', shortLabel: '写' },
  { to: '/app/assistant', activePrefix: '/app/assistant', label: '学习助手', shortLabel: '助' },
  { to: '/app/vocabulary', activePrefix: '/app/vocabulary', label: '单词', shortLabel: '词' },
  { to: '/app/listening', activePrefix: '/app/listening', label: '听力', shortLabel: '听' },
  { to: '/app/speaking', activePrefix: '/app/speaking', label: '口语', shortLabel: '说' },
] as const

function isActive(activePrefix: string) {
  return route.path === activePrefix || route.path.startsWith(`${activePrefix}/`)
}
</script>

<style scoped>
.app-rail {
  display: flex;
  align-items: center;
  flex: 0 0 64px;
  flex-direction: column;
  gap: 18px;
  width: 64px;
  height: 100vh;
  padding: 18px 10px;
  border-right: 1px solid var(--app-sidebar-border, #d9e2ec);
  background: #ffffff;
  box-sizing: border-box;
}

.rail-spacer {
  flex: 1 1 auto;
  min-height: 24px;
}

.rail-brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--app-sidebar-border, #d9e2ec);
  border-radius: 12px;
  background: #f8fafc;
  cursor: pointer;
}

.rail-brand:hover,
.rail-brand:focus-visible,
.rail-brand[aria-pressed='true'] {
  background: #d1fae5;
  border-color: #a7f3d0;
}

.rail-logo {
  display: block;
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.rail-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #334155;
  cursor: pointer;
}

.rail-button:hover,
.rail-button:focus-visible,
.rail-nav-link--active {
  background: #ecfdf5;
  color: #047857;
}

.rail-nav-link {
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.rail-button svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.rail-profile-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #047857;
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
  box-shadow: 0 8px 20px rgba(4, 120, 87, 0.18);
}

.rail-profile-link:hover,
.rail-profile-link:focus-visible,
.rail-profile-link--active {
  background: #065f46;
}
</style>
