<template>
  <aside class="app-rail" :class="{ 'app-rail--collapsed': collapsed }" aria-label="应用快捷导航">
    <AppNavigationMenu :collapsed="collapsed" @toggle="emit('toggleRail')" />

    <section v-if="!collapsed && isWritingRoute" class="rail-context-section" aria-label="写作空间">
      <div class="rail-section-label">写作空间</div>
      <RouterLink to="/app/writing/mode" class="rail-primary-action">＋ 新建作文</RouterLink>
      <RouterLink
        to="/app/writing"
        class="rail-context-link"
        :class="{ 'rail-context-link--active': route.path === '/app/writing' }"
      >
        写作练习
      </RouterLink>
      <RouterLink
        to="/app/writing/dashboard"
        class="rail-context-link"
        :class="{ 'rail-context-link--active': route.path.startsWith('/app/writing/dashboard') }"
      >
        Dashboard
      </RouterLink>
    </section>

    <div class="rail-spacer" aria-hidden="true"></div>

    <RouterLink
      to="/app/me"
      class="rail-profile-link"
      :class="{ 'rail-profile-link--active': route.path.startsWith('/app/me') }"
      title="个人中心"
      aria-label="个人中心"
    >
      <span class="rail-profile-avatar">我</span>
      <span v-if="!collapsed" class="rail-profile-copy">
        <strong>个人中心</strong>
        <small>账号设置与订阅</small>
      </span>
    </RouterLink>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AppNavigationMenu from './AppNavigationMenu.vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  openAssistantDrawer: []
  toggleRail: []
}>()

const route = useRoute()
const isWritingRoute = computed(() => route.path.startsWith('/app/writing'))
</script>

<style scoped>
.app-rail {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 20;
  display: flex;
  flex: 0 0 218px;
  flex-direction: column;
  width: 218px;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  padding: 16px 12px;
  border-right: 1px solid var(--app-sidebar-border, #d9e2ec);
  background: #eaf4fc;
  color: #0f172a;
  box-sizing: border-box;
  transition:
    width 180ms ease,
    flex-basis 180ms ease,
    padding 180ms ease;
}

.app-rail--collapsed {
  flex-basis: 72px;
  width: 72px;
  padding-right: 14px;
  padding-left: 14px;
}

.rail-context-section {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(100, 116, 139, 0.18);
}

.rail-section-label {
  padding: 0 12px 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.rail-primary-action,
.rail-context-link {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.rail-primary-action {
  justify-content: center;
  margin-bottom: 3px;
  background: #078966;
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(7, 137, 102, 0.16);
}

.rail-primary-action:hover,
.rail-primary-action:focus-visible {
  background: #047857;
}

.rail-context-link {
  color: #334155;
}

.rail-context-link:hover,
.rail-context-link:focus-visible,
.rail-context-link--active {
  background: rgba(255, 255, 255, 0.72);
  color: #0f172a;
}

.rail-spacer {
  flex: 1 1 auto;
  min-height: 18px;
}

.rail-profile-link {
  display: flex;
  align-items: center;
  min-height: 52px;
  padding: 5px 8px;
  border-radius: 14px;
  color: #334155;
  text-decoration: none;
}

.rail-profile-link:hover,
.rail-profile-link:focus-visible,
.rail-profile-link--active {
  background: rgba(255, 255, 255, 0.72);
}

.rail-profile-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #047857;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(4, 120, 87, 0.16);
}

.rail-profile-copy {
  display: flex;
  min-width: 0;
  margin-left: 10px;
  flex-direction: column;
  line-height: 1.35;
}

.rail-profile-copy strong {
  font-size: 13px;
}

.rail-profile-copy small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-rail--collapsed .rail-profile-link {
  justify-content: center;
  width: 44px;
  min-height: 44px;
  padding: 0;
}
</style>
