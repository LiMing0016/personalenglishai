<template>
  <div class="app-layout" :class="{ immersive }">
    <nav v-if="!immersive" class="app-nav">
      <router-link to="/app" class="nav-brand">PEAI</router-link>
      <div class="nav-links">
        <router-link
          v-for="link in navLinks"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          active-class="nav-link--active"
        >{{ link.label }}</router-link>
      </div>
      <div class="nav-right">
        <router-link to="/app" class="nav-icon" title="总览">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
        </router-link>
        <router-link to="/app/me" class="nav-icon" title="个人中心">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        </router-link>
      </div>
    </nav>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const immersive = computed(() => Boolean(route.meta.immersive))

const navLinks = [
  { to: '/app/writing', label: '写作' },
  { to: '/app/vocabulary', label: '单词' },
  { to: '/app/listening', label: '听力' },
  { to: '/app/speaking', label: '口语' },
]
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f6f7;
}
.app-layout.immersive {
  /* immersive mode: no nav, full height for child */
}

.app-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.nav-brand {
  font-size: 17px;
  font-weight: 800;
  color: #047857;
  text-decoration: none;
  letter-spacing: -0.5px;
  margin-right: 32px;
}
.nav-brand:hover {
  color: #065f46;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  padding: 6px 14px;
  font-size: 14px;
  font-weight: 500;
  color: #4b5563;
  text-decoration: none;
  border-radius: 8px;
  transition: background 0.15s, color 0.15s;
}
.nav-link:hover {
  background: #f3f4f6;
  color: #111827;
}
.nav-link--active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}

.nav-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: #6b7280;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.nav-icon:hover {
  background: #f3f4f6;
  color: #111827;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* immersive: main takes full viewport */
.app-layout.immersive .app-main {
  height: 100vh;
}
.app-layout:not(.immersive) .app-main {
  height: calc(100vh - 48px);
}

@media (max-width: 640px) {
  .app-nav {
    padding: 0 12px;
  }
  .nav-brand {
    margin-right: 16px;
  }
  .nav-link {
    padding: 6px 10px;
    font-size: 13px;
  }
}
</style>
