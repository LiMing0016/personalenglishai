<template>
  <svg v-if="name === 'writing'" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z" />
    <path d="m13.5 6.5 4 4" />
  </svg>
  <svg v-else-if="name === 'translation'" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M5 5h8" />
    <path d="M9 3v2c0 3.2-1.8 6.2-4.5 7.7" />
    <path d="M4 10c1.4 1.6 3.2 2.6 5.4 3" />
    <path d="M14 20l4-9 4 9" />
    <path d="M15.4 17h5.2" />
  </svg>
  <svg v-else-if="name === 'assistant'" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-6.2A8 8 0 1 1 21 12Z" />
  </svg>
  <svg v-else-if="name === 'reading'" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 5.5A3.5 3.5 0 0 1 7.5 2H20v17H7.5A3.5 3.5 0 0 0 4 22V5.5Z" />
    <path d="M4 18.5A3.5 3.5 0 0 1 7.5 15H20" />
  </svg>
  <svg v-else-if="name === 'listening'" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M6 12a6 6 0 0 1 12 0" />
    <path d="M18 12v2.5a3.5 3.5 0 0 1-7 0V13a2 2 0 0 1 4 0v1.4a1.4 1.4 0 0 1-2.8 0V14" />
    <path d="M4 12a8 8 0 0 1 16 0" />
  </svg>
  <svg v-else viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
    <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
    <path d="M12 18v4" />
    <path d="M8 22h8" />
  </svg>
</template>

<script setup lang="ts">
import type { AppSkillIcon } from './appNavigation'

defineProps<{
  name: AppSkillIcon
}>()
</script>

<style scoped>
svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
