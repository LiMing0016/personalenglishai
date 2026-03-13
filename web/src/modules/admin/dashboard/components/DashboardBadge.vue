<template>
  <span class="admin-dashboard-badge" :class="toneClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  tone?: 'default' | 'warn'
}>(), {
  tone: 'default',
})

const toneClass = computed(() => `admin-dashboard-badge--${props.tone}`)
</script>
