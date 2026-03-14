<template>
  <div class="admin-card admin-dashboard-filterbar">
    <div class="admin-toolbar">
      <div class="admin-toolbar-left">
        <button
          v-for="item in presets"
          :key="item.value"
          type="button"
          class="admin-btn admin-btn--secondary"
          :class="{ 'admin-dashboard-filterbar__preset--active': props.modelValue.preset === item.value }"
          @click="applyPreset(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="admin-toolbar-right">
        <input
          class="admin-input admin-input--sm"
          type="date"
          :disabled="props.modelValue.preset !== 'custom'"
          :value="props.modelValue.startDate || ''"
          @input="updateField('startDate', ($event.target as HTMLInputElement).value)"
        >
        <input
          class="admin-input admin-input--sm"
          type="date"
          :disabled="props.modelValue.preset !== 'custom'"
          :value="props.modelValue.endDate || ''"
          @input="updateField('endDate', ($event.target as HTMLInputElement).value)"
        >
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AdminDashboardFilter, AdminDashboardPreset } from '../types'

const props = defineProps<{
  modelValue: AdminDashboardFilter
}>()

const emit = defineEmits<{
  'update:modelValue': [value: AdminDashboardFilter]
}>()

const presets: Array<{ value: AdminDashboardPreset; label: string }> = [
  { value: 'today', label: '今日' },
  { value: 'yesterday', label: '昨日' },
  { value: 'thisWeek', label: '本周' },
  { value: 'thisMonth', label: '本月' },
  { value: 'custom', label: '自定义' },
]

function applyPreset(preset: AdminDashboardPreset) {
  emit('update:modelValue', {
    ...props.modelValue,
    preset,
    startDate: preset === 'custom' ? props.modelValue.startDate : undefined,
    endDate: preset === 'custom' ? props.modelValue.endDate : undefined,
  })
}

function updateField(field: 'startDate' | 'endDate', value: string) {
  emit('update:modelValue', {
    ...props.modelValue,
    preset: 'custom',
    [field]: value || undefined,
  })
}
</script>
