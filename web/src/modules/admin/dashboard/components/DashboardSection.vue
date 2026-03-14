<template>
  <section class="admin-section">
    <div class="admin-card admin-dashboard-section">
      <div class="admin-dashboard-section__head">
        <div>
          <div class="admin-dashboard-section__title-row">
            <h2 class="admin-card-title">{{ title }}</h2>
            <slot name="badge" />
          </div>
          <p v-if="description" class="admin-subtle">{{ description }}</p>
        </div>
        <slot name="actions" />
      </div>

      <div v-if="status === 'loading'" class="admin-loading">正在加载模块数据...</div>
      <div v-else-if="status === 'error'" class="admin-error">{{ errorMessage || '模块数据加载失败' }}</div>
      <div v-else-if="status === 'empty'" class="admin-empty">{{ emptyMessage || '当前筛选条件下暂无数据' }}</div>
      <slot v-else />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AdminDashboardStatus } from '../types'

withDefaults(defineProps<{
  title: string
  description?: string
  status?: AdminDashboardStatus
  emptyMessage?: string
  errorMessage?: string
}>(), {
  description: '',
  status: 'ready',
  emptyMessage: '当前筛选条件下暂无数据',
  errorMessage: '模块数据加载失败',
})
</script>
