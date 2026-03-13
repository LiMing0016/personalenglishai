<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="搜索作文/题目/昵称" @keyup.enter="load" />
          <select v-model="filters.mode" class="admin-select">
            <option value="">全部模式</option>
            <option value="free">free</option>
            <option value="exam">exam</option>
          </select>
        </div>
        <button class="admin-btn" @click="load">查询</button>
      </div>
      <table class="admin-table">
        <thead><tr><th>ID</th><th>用户</th><th>模式</th><th>总分</th><th>高考分</th><th>归档</th><th>时间</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="item.evaluationId" class="admin-row-link" @click="goDetail(item.evaluationId)">
            <td>{{ item.evaluationId }}</td>
            <td>{{ item.userNickname }}</td>
            <td>{{ item.mode }}</td>
            <td>{{ item.overallScore ?? '-' }}</td>
            <td>{{ item.gaokaoScore ?? '-' }}</td>
            <td>{{ item.archived ? '是' : '否' }}</td>
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
import { useRouter } from 'vue-router'
import { adminApi, type AdminEssayListItem } from '@/api/admin'
import { showToast } from '@/utils/toast'

const router = useRouter()
const rows = ref<AdminEssayListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const filters = ref({ keyword: '', mode: '' })

async function load() {
  try {
    const res = await adminApi.listEssays({ ...filters.value, page: page.value, size })
    rows.value = res.items
    total.value = res.total
  } catch {
    showToast('加载作文列表失败', 'error')
  }
}

function goDetail(id: number) {
  router.push(`/admin/essays/${id}`)
}

onMounted(load)
</script>
