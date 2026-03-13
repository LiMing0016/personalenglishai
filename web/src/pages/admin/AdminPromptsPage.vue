<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="搜索题目/内容" @keyup.enter="load" />
          <input v-model="filters.stageId" class="admin-input admin-input--sm" placeholder="stageId" />
        </div>
        <div class="admin-toolbar-right">
          <router-link class="admin-btn" to="/admin/prompts/new">新建题目</router-link>
          <button class="admin-btn admin-btn--secondary" @click="load">查询</button>
        </div>
      </div>
      <table class="admin-table">
        <thead><tr><th>ID</th><th>标题</th><th>卷号</th><th>年份</th><th>任务</th><th>启用</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="item.id" class="admin-row-link" @click="goDetail(item.id!)">
            <td>{{ item.id }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.paper }}</td>
            <td>{{ item.examYear ?? '-' }}</td>
            <td>{{ item.task || '-' }}</td>
            <td>{{ item.isActive ? '是' : '否' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, type AdminPromptDto } from '@/api/admin'
import { showToast } from '@/utils/toast'

const router = useRouter()
const rows = ref<AdminPromptDto[]>([])
const filters = ref({ keyword: '', stageId: '' })

async function load() {
  try {
    const res = await adminApi.listPrompts({
      keyword: filters.value.keyword || undefined,
      stageId: filters.value.stageId ? Number(filters.value.stageId) : undefined,
      page: 1,
      size: 100,
    })
    rows.value = res.items
  } catch {
    showToast('加载题库失败', 'error')
  }
}

function goDetail(id: number) {
  router.push(`/admin/prompts/${id}`)
}

onMounted(load)
</script>
