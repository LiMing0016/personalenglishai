<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.stage" class="admin-input" placeholder="stage，如 highschool" />
          <select v-model="filters.active" class="admin-select">
            <option value="">全部状态</option>
            <option value="1">active</option>
            <option value="0">inactive</option>
          </select>
        </div>
        <button class="admin-btn" @click="load">查询</button>
      </div>
      <table class="admin-table">
        <thead><tr><th>ID</th><th>rubricKey</th><th>stage</th><th>modes</th><th>active</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="item.id" class="admin-row-link" @click="goDetail(item.id!)">
            <td>{{ item.id }}</td>
            <td>{{ item.rubricKey }}</td>
            <td>{{ item.stage }}</td>
            <td>{{ item.modes?.join(', ') || '-' }}</td>
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
import { adminApi, type AdminRubricVersionDto } from '@/api/admin'
import { showToast } from '@/utils/toast'

const router = useRouter()
const rows = ref<Partial<AdminRubricVersionDto>[]>([])
const filters = ref({ stage: '', active: '' })

async function load() {
  try {
    const res = await adminApi.listRubrics({
      stage: filters.value.stage || undefined,
      active: filters.value.active === '' ? undefined : filters.value.active === '1',
      page: 1,
      size: 100,
    })
    rows.value = res.items
  } catch {
    showToast('加载 Rubric 列表失败', 'error')
  }
}

function goDetail(id: number) {
  router.push(`/admin/rubrics/${id}`)
}

onMounted(load)
</script>
