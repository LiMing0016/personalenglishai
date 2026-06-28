<template>
  <aside class="app-rail" :class="{ 'app-rail--collapsed': collapsed }" aria-label="应用快捷导航">
    <button
      type="button"
      class="rail-brand"
      :title="collapsed ? '展开导航' : '收起导航'"
      :aria-label="collapsed ? '展开导航' : '收起导航'"
      :aria-pressed="collapsed"
      @click="emit('toggleRail')"
    >
      <img src="/brand/peai-logo.png" alt="PEAI" class="rail-logo" draggable="false" />
      <svg class="rail-brand-toggle-icon" viewBox="0 0 24 24" aria-hidden="true">
        <rect x="4" y="5" width="16" height="14" rx="3" />
        <path d="M10 5v14" />
      </svg>
    </button>

    <div v-if="!collapsed" class="rail-content">
      <RouterLink
        v-for="item in appNavItems"
        :key="item.to"
        :to="item.to"
        class="rail-button rail-nav-link"
        :class="{ 'rail-nav-link--active': isActive(item.activePrefix) }"
        :title="item.label"
        :aria-label="item.label"
      >
        <span class="rail-line-icon" aria-hidden="true">
          <svg v-if="item.skillIcon === 'writing'" viewBox="0 0 24 24">
            <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z" />
            <path d="m13.5 6.5 4 4" />
          </svg>
          <svg v-else-if="item.skillIcon === 'translation'" viewBox="0 0 24 24">
            <path d="M5 5h8" />
            <path d="M9 3v2c0 3.2-1.8 6.2-4.5 7.7" />
            <path d="M4 10c1.4 1.6 3.2 2.6 5.4 3" />
            <path d="M14 20l4-9 4 9" />
            <path d="M15.4 17h5.2" />
          </svg>
          <svg v-else-if="item.skillIcon === 'assistant'" viewBox="0 0 24 24">
            <path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-6.2A8 8 0 1 1 21 12Z" />
          </svg>
          <svg v-else-if="item.skillIcon === 'reading'" viewBox="0 0 24 24">
            <path d="M4 5.5A3.5 3.5 0 0 1 7.5 2H20v17H7.5A3.5 3.5 0 0 0 4 22V5.5Z" />
            <path d="M4 18.5A3.5 3.5 0 0 1 7.5 15H20" />
          </svg>
          <svg v-else-if="item.skillIcon === 'listening'" viewBox="0 0 24 24">
            <path d="M6 12a6 6 0 0 1 12 0" />
            <path d="M18 12v2.5a3.5 3.5 0 0 1-7 0V13a2 2 0 0 1 4 0v1.4a1.4 1.4 0 0 1-2.8 0V14" />
            <path d="M4 12a8 8 0 0 1 16 0" />
          </svg>
          <svg v-else viewBox="0 0 24 24">
            <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
            <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
            <path d="M12 18v4" />
            <path d="M8 22h8" />
          </svg>
        </span>
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
    </div>
  </aside>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  openAssistantDrawer: []
  toggleRail: []
}>()

const route = useRoute()

type SkillIcon = 'assistant' | 'writing' | 'translation' | 'reading' | 'listening' | 'speaking'
type AppNavItem = {
  to: string
  activePrefix: string
  label: string
  skillIcon: SkillIcon
}

const appNavItems = [
  { to: '/app/writing', activePrefix: '/app/writing', label: '写作', skillIcon: 'writing' },
  { to: '/app/translation', activePrefix: '/app/translation', label: '翻译', skillIcon: 'translation' },
  { to: '/app/assistant', activePrefix: '/app/assistant', label: '学习助手', skillIcon: 'assistant' },
  { to: '/app/vocabulary', activePrefix: '/app/vocabulary', label: '阅读', skillIcon: 'reading' },
  { to: '/app/listening', activePrefix: '/app/listening', label: '听力', skillIcon: 'listening' },
  { to: '/app/speaking', activePrefix: '/app/speaking', label: '口语', skillIcon: 'speaking' },
] satisfies readonly AppNavItem[]

function isActive(activePrefix: string) {
  return route.path === activePrefix || route.path.startsWith(`${activePrefix}/`)
}
</script>

<style scoped>
.app-rail {
  display: flex;
  align-items: center;
  flex: 0 0 72px;
  flex-direction: column;
  gap: 18px;
  width: 72px;
  height: 100vh;
  padding: 18px 10px;
  border-right: 1px solid var(--app-sidebar-border, #d9e2ec);
  background: inherit;
  color: #0f172a;
  box-sizing: border-box;
  transition:
    width 180ms ease,
    flex-basis 180ms ease,
    padding 180ms ease,
    border-color 180ms ease,
    background-color 180ms ease;
}

.app-rail--collapsed {
  position: fixed;
  top: max(12px, env(safe-area-inset-top));
  left: max(12px, env(safe-area-inset-left));
  z-index: 80;
  flex-basis: 0;
  width: auto;
  height: auto;
  padding: 0;
  border-right: 0;
  background: transparent;
}

.rail-content {
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  min-height: 0;
  animation: rail-content-in 180ms ease;
}

.rail-spacer {
  flex: 1 1 auto;
  min-height: 24px;
}

.rail-brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.rail-brand:hover,
.rail-brand:focus-visible {
  background: #ffffff;
  color: #334155;
}

.rail-logo,
.rail-brand-toggle-icon {
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}

.rail-logo {
  display: block;
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.rail-brand-toggle-icon {
  position: absolute;
  width: 23px;
  height: 23px;
  opacity: 0;
  transform: scale(0.88);
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.rail-brand:hover .rail-logo,
.rail-brand:focus-visible .rail-logo {
  opacity: 0;
  transform: scale(0.88);
}

.rail-brand:hover .rail-brand-toggle-icon,
.rail-brand:focus-visible .rail-brand-toggle-icon {
  opacity: 1;
  transform: scale(1);
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
  background: #ffffff;
  color: #0f172a;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.rail-nav-link {
  text-decoration: none;
}

.rail-line-icon,
.rail-line-icon svg,
.rail-button > svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
}

.rail-line-icon svg,
.rail-button > svg {
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

@keyframes rail-content-in {
  from {
    opacity: 0;
    transform: translateX(-6px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
