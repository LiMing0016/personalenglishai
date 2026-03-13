<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="搜索邮箱/手机号/昵称" @keyup.enter="load" />
          <select v-model="filters.status" class="admin-select">
            <option value="">全部状态</option>
            <option value="active">active</option>
            <option value="disabled">disabled</option>
          </select>
        </div>
        <button class="admin-btn" @click="load">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>昵称</th>
            <th>邮箱</th>
            <th>学段</th>
            <th>状态</th>
            <th>管理员角色</th>
            <th>最近活跃</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in rows" :key="item.id" class="admin-row-link" @click="goDetail(item.id)">
            <td>{{ item.id }}</td>
            <td>{{ item.nickname }}</td>
            <td>{{ item.email || '-' }}</td>
            <td>{{ item.studyStage || '-' }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.adminRoles?.join(', ') || '-' }}</td>
            <td>{{ item.lastActiveAt || '-' }}</td>
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
import { useRouter } from 'vue-router'
import { adminApi, type AdminUserListItem } from '@/api/admin'
import { showToast } from '@/utils/toast'

const router = useRouter()
const rows = ref<AdminUserListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const filters = ref({ keyword: '', status: '' })

async function load() {
  try {
    const res = await adminApi.listUsers({ ...filters.value, page: page.value, size })
    rows.value = res.items
    total.value = res.total
  } catch {
    showToast('加载用户列表失败', 'error')
  }
}

function goDetail(id: number) {
  router.push(`/admin/users/${id}`)
}

onMounted(load)
</script>
