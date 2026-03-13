<template>
  <div class="admin-toolbar admin-dashboard-filterbar">
    <div class="admin-toolbar-left">
      <button
        v-for="item in presetOptions"
        :key="item.value"
        type="button"
        class="admin-btn admin-btn--secondary"
        :class="{ 'admin-dashboard-filterbar__preset--active': modelValue.preset === item.value }"
        @click="setPreset(item.value)"
      >
        {{ item.label }}
      </button>
    </div>
    <div class="admin-toolbar-right">
      <template v-if="modelValue.preset === 'custom'">
        <input :value="modelValue.startDate" type="date" class="admin-input admin-input--sm" @input="onDateChange('startDate', $event)" />
        <input :value="modelValue.endDate" type="date" class="admin-input admin-input--sm" @input="onDateChange('endDate', $event)" />
      </template>
      <span class="admin-subtle">时区：{{ timezoneLabel }}</span>
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

const presetOptions: Array<{ value: AdminDashboardPreset; label: string }> = [
  { value: 'today', label: '今日' },
  { value: 'yesterday', label: '昨日' },
  { value: 'thisWeek', label: '本周' },
  { value: 'thisMonth', label: '本月' },
  { value: 'custom', label: '自定义' },
]

const timezoneLabel = props.modelValue.timezone || 'Asia/Shanghai'

function setPreset(preset: AdminDashboardPreset) {
  emit('update:modelValue', {
    ...props.modelValue,
    preset,
    startDate: preset === 'custom' ? props.modelValue.startDate : undefined,
    endDate: preset === 'custom' ? props.modelValue.endDate : undefined,
  })
}

function onDateChange(field: 'startDate' | 'endDate', event: Event) {
  const value = (event.target as HTMLInputElement).value || undefined
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value,
  })
}
</script>
