<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.action" class="admin-input" placeholder="action" @keyup.enter="load" />
          <input v-model="filters.resourceType" class="admin-input" placeholder="resourceType" @keyup.enter="load" />
        </div>
        <button class="admin-btn" @click="load">查询</button>
      </div>
      <table class="admin-table">
        <thead><tr><th>ID</th><th>管理员</th><th>动作</th><th>资源</th><th>目标用户</th><th>时间</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.adminNickname || item.adminUserId }}</td>
            <td>{{ item.action }}</td>
            <td>{{ item.resourceType }} / {{ item.resourceId || '-' }}</td>
            <td>{{ item.targetUserId || '-' }}</td>
            <td>{{ item.createdAt }}</td>
          </tr>
        </tbody>
      </table>
      <div class="admin-pagination">
        <button class="admin-btn admin-btn--secondary" :disabled="page<=1" @click="page--; load()">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
        <button class="admin-btn admin-btn--secondary" :disabled="page * size >= total" @click="page++; load()">下一页</button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi, type AdminAuditLogItem } from '@/api/admin'
import { showToast } from '@/utils/toast'

const rows = ref<AdminAuditLogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const filters = ref({ action: '', resourceType: '' })

async function load() {
  try {
    const res = await adminApi.listAuditLogs({ ...filters.value, page: page.value, size })
    rows.value = res.items
    total.value = res.total
  } catch {
    showToast('加载审计日志失败', 'error')
  }
}

onMounted(load)
</script>
