<template>
  <div class="app-navigation-menu" :class="{ 'app-navigation-menu--collapsed': collapsed }">
    <button
      type="button"
      class="app-navigation-brand"
      :title="collapsed ? '展开导航' : '收起导航'"
      :aria-label="collapsed ? '展开导航' : '收起导航'"
      :aria-expanded="!collapsed"
      @click="emit('toggle')"
    >
      <img src="/brand/peai-logo.png" alt="PEAI" class="app-navigation-logo" draggable="false" />
      <span v-if="!collapsed" class="app-navigation-brand-name">PEAI</span>
      <svg v-if="!collapsed" class="app-navigation-collapse-hint" viewBox="0 0 24 24" aria-hidden="true">
        <rect x="4" y="5" width="16" height="14" rx="3" />
        <path d="M10 5v14" />
      </svg>
    </button>

    <nav class="app-navigation-links" aria-label="应用导航">
      <RouterLink
        v-for="item in APP_NAV_ITEMS"
        :key="item.to"
        :to="item.to"
        class="app-navigation-link"
        :class="{ 'app-navigation-link--active': isAppRouteActive(route.path, item.activePrefix) }"
        :title="collapsed ? item.label : undefined"
        :aria-label="item.label"
      >
        <span class="app-navigation-icon" aria-hidden="true">
          <AppNavigationIcon :name="item.skillIcon" />
        </span>
        <span v-if="!collapsed" class="app-navigation-label">{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppNavigationIcon from './AppNavigationIcon.vue'
import { APP_NAV_ITEMS, isAppRouteActive } from './appNavigation'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const route = useRoute()
</script>

<style scoped>
.app-navigation-menu {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.app-navigation-brand,
.app-navigation-link {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 44px;
  border-radius: 12px;
  color: #334155;
  text-decoration: none;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    box-shadow 160ms ease;
}

.app-navigation-brand {
  width: 100%;
  padding: 0 10px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.app-navigation-brand:hover,
.app-navigation-brand:focus-visible,
.app-navigation-link:hover,
.app-navigation-link:focus-visible {
  background: rgba(255, 255, 255, 0.68);
  color: #0f172a;
}

.app-navigation-logo {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.app-navigation-brand-name {
  margin-left: 10px;
  color: #047857;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.app-navigation-collapse-hint {
  margin-left: auto;
  color: #64748b;
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.app-navigation-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-navigation-link {
  padding: 0 12px;
  font-size: 14px;
  font-weight: 600;
}

.app-navigation-link--active {
  background: rgba(255, 255, 255, 0.82);
  color: #0f172a;
  font-weight: 750;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.app-navigation-link--active::before {
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: #059669;
  content: '';
}

.app-navigation-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
}

.app-navigation-label {
  margin-left: 12px;
  white-space: nowrap;
}

.app-navigation-menu--collapsed {
  align-items: center;
}

.app-navigation-menu--collapsed .app-navigation-brand,
.app-navigation-menu--collapsed .app-navigation-link {
  justify-content: center;
  width: 44px;
  min-height: 44px;
  padding: 0;
}

.app-navigation-menu--collapsed .app-navigation-link--active::before {
  top: 8px;
  bottom: 8px;
  left: -4px;
}
</style>
