<template>
  <div class="app-layout" :class="{ immersive }">
    <AppRail
      :assistant-drawer-open="assistantDrawerOpen"
      @open-assistant-drawer="openAssistantDrawer"
      @toggle-assistant-drawer="toggleAssistantDrawer"
    />
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppRail from '@/components/AppRail.vue'

const route = useRoute()
const router = useRouter()
const immersiveOverride = ref<boolean | null>(null)
const assistantDrawerOpen = ref(false)
provide('setImmersive', (v: boolean | null) => { immersiveOverride.value = v })
provide('assistantDrawerOpen', assistantDrawerOpen)
const immersive = computed(() =>
  immersiveOverride.value !== null ? immersiveOverride.value : Boolean(route.meta.immersive)
)

function openAssistantDrawer() {
  assistantDrawerOpen.value = true
  if (route.path !== '/app/assistant') {
    void router.push('/app/assistant')
  }
}

function toggleAssistantDrawer() {
  if (route.path !== '/app/assistant') {
    assistantDrawerOpen.value = true
    void router.push('/app/assistant')
    return
  }
  assistantDrawerOpen.value = !assistantDrawerOpen.value
}
</script>

<style scoped>
.app-layout {
  --app-sidebar-border: #d9e2ec;
  min-height: 100vh;
  display: flex;
  flex-direction: row;
  background: #f5f6f7;
}
.app-layout.immersive {
  /* immersive mode: no nav, full height for child */
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  height: 100vh;
}

/* immersive: main takes full viewport */
.app-layout.immersive .app-main {
  height: 100vh;
}
.app-layout:not(.immersive) .app-main {
  height: 100vh;
}
</style>
