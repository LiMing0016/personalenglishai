<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">数据地图</h1>
          <p class="admin-subtle">查看核心数据表、业务归属、敏感级别和排查入口。</p>
        </div>
        <button class="admin-btn admin-btn--secondary" @click="resetFilters">重置</button>
      </div>

      <div class="admin-toolbar data-catalog-filter">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="表名 / 中文名 / 说明" @keyup.enter="load" />
          <select v-model="filters.module" class="admin-select">
            <option value="">全部模块</option>
            <option v-for="item in modules" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="filters.sensitivity" class="admin-select">
            <option value="">全部敏感级别</option>
            <option value="low">low</option>
            <option value="medium">medium</option>
            <option value="high">high</option>
            <option value="critical">critical</option>
          </select>
          <select v-model="filters.hasAdminRoute" class="admin-select">
            <option value="">全部入口</option>
            <option value="true">有业务入口</option>
            <option value="false">无业务入口</option>
          </select>
        </div>
        <button class="admin-btn" @click="load">查询</button>
      </div>

      <div v-if="loading" class="admin-empty">正在加载数据地图...</div>
      <div v-else-if="error" class="admin-error">
        {{ error }}
        <button class="admin-btn admin-btn--secondary" @click="load">重试</button>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>表名</th>
              <th>中文名</th>
              <th>模块</th>
              <th>行数</th>
              <th>敏感级别</th>
              <th>最近更新时间</th>
              <th>入口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="rows.length === 0" class="admin-empty-row">
              <td colspan="7">当前筛选下没有匹配的数据表。</td>
            </tr>
            <tr v-for="item in rows" :key="item.tableName" class="admin-row-link" @click="openDetail(item.tableName)">
              <td><code>{{ item.tableName }}</code></td>
              <td>
                <strong>{{ item.title || item.tableName }}</strong>
                <div class="admin-subtle data-catalog-description">{{ item.description || '-' }}</div>
              </td>
              <td>{{ item.module || '-' }}</td>
              <td>{{ formatNumber(item.rowCount) }}</td>
              <td><span :class="['admin-badge', `data-catalog-sensitivity--${item.sensitivity}`]">{{ item.sensitivity || 'low' }}</span></td>
              <td>{{ item.latestAt || '-' }}</td>
              <td>
                <router-link v-if="item.adminRoute" class="admin-link" :to="item.adminRoute" @click.stop>进入业务页</router-link>
                <span v-else>-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, type AdminDataCatalogTable } from '@/api/admin'

const router = useRouter()
const rows = ref<AdminDataCatalogTable[]>([])
const loading = ref(false)
const error = ref('')
const filters = ref({
  keyword: '',
  module: '',
  sensitivity: '',
  hasAdminRoute: '',
})

const modules = computed(() => {
  const set = new Set<string>()
  rows.value.forEach((item) => {
    if (item.module) set.add(item.module)
  })
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await adminApi.listDataCatalogTables(cleanParams(filters.value))
  } catch {
    error.value = '加载数据地图失败'
  } finally {
    loading.value = false
  }
}

function cleanParams(input: Record<string, string>) {
  const params: Record<string, unknown> = {}
  Object.entries(input).forEach(([key, value]) => {
    if (value === '') return
    if (key === 'hasAdminRoute') {
      params[key] = value === 'true'
    } else {
      params[key] = value
    }
  })
  return params
}

function resetFilters() {
  filters.value = { keyword: '', module: '', sensitivity: '', hasAdminRoute: '' }
  load()
}

function openDetail(tableName: string) {
  router.push(`/admin/data-catalog/${encodeURIComponent(tableName)}`)
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

onMounted(load)
</script>

<style scoped>
.data-catalog-filter {
  align-items: flex-start;
}

.data-catalog-filter .admin-input,
.data-catalog-filter .admin-select {
  min-width: 180px;
}

.data-catalog-description {
  max-width: 420px;
}

.data-catalog-sensitivity--high,
.data-catalog-sensitivity--critical {
  background: rgba(203, 65, 84, 0.12);
  color: #a32136;
}

.data-catalog-sensitivity--medium {
  background: rgba(174, 112, 29, 0.12);
  color: #925f18;
}

.data-catalog-sensitivity--low {
  background: rgba(19, 111, 75, 0.1);
  color: var(--admin-accent-dark);
}
</style>
