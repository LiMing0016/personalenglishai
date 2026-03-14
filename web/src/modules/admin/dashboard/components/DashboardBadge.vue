<template>
  <span class="admin-dashboard-badge" :class="badgeClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  tone?: 'default' | 'warn'
}>(), {
  tone: 'default',
})

const badgeClass = computed(() => props.tone === 'warn' ? 'admin-dashboard-badge--warn' : 'admin-dashboard-badge--default')
</script>
