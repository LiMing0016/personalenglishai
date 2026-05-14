<template>
  <span class="skill-logo" aria-hidden="true">
    <img :src="iconSrc" alt="" draggable="false" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  icon: 'assistant' | 'writing' | 'reading' | 'listening' | 'speaking'
}>()

const iconSrc = computed(() => `/nav-icons/${props.icon}.png`)
</script>

<style scoped>
.skill-logo {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: 12px;
}

.skill-logo img {
  display: block;
  width: 60px;
  height: 60px;
  max-width: none;
  object-fit: cover;
  user-select: none;
  pointer-events: none;
}
</style>
