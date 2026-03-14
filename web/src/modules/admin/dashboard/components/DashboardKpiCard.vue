<template>
  <div class="admin-stat admin-dashboard-kpi">
    <div class="admin-dashboard-kpi__label">{{ label }}</div>
    <div class="admin-dashboard-kpi__value">{{ value }}</div>
    <div v-if="hint" class="admin-dashboard-kpi__hint">{{ hint }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  hint?: string
}>()
</script>
