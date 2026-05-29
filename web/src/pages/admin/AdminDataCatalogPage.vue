<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">数据地图</h1>
          <p class="admin-subtle">自动发现数据库表，并通过业务配置补充中文名、模块、敏感级别、入口和关系图。</p>
        </div>
        <div class="data-catalog-view-switch">
          <button
            type="button"
            class="admin-btn"
            :class="{ 'admin-btn--secondary': activeView !== 'overview' }"
            @click="switchView('overview')"
          >
            表总览
          </button>
          <button
            type="button"
            class="admin-btn"
            :class="{ 'admin-btn--secondary': activeView !== 'graph' }"
            @click="switchView('graph')"
          >
            ER 图
          </button>
        </div>
      </div>

      <div class="data-catalog-summary">
        <div class="data-catalog-summary-card">
          <span>已发现表</span>
          <strong>{{ rows.length }}</strong>
        </div>
        <div class="data-catalog-summary-card">
          <span>已配置增强</span>
          <strong>{{ configuredCount }}</strong>
        </div>
        <div class="data-catalog-summary-card">
          <span>自动发现</span>
          <strong>{{ autoDiscoveredCount }}</strong>
        </div>
        <div class="data-catalog-summary-card">
          <span>模块数量</span>
          <strong>{{ modules.length }}</strong>
        </div>
      </div>

      <div v-if="activeView === 'overview'">
        <div class="admin-toolbar data-catalog-filter">
          <div class="admin-toolbar-left">
            <input v-model="filters.keyword" class="admin-input" placeholder="表名 / 中文名 / 说明" @keyup.enter="loadTables" />
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
          <div class="data-catalog-filter-actions">
            <button class="admin-btn" @click="loadTables">查询</button>
            <button class="admin-btn admin-btn--secondary" @click="resetFilters">重置</button>
          </div>
        </div>

        <div v-if="tableLoading" class="admin-empty">正在加载数据地图...</div>
        <div v-else-if="tableError" class="admin-error">
          {{ tableError }}
          <button class="admin-btn admin-btn--secondary" @click="loadTables">重试</button>
        </div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>表名</th>
                <th>中文名 / 说明</th>
                <th>模块</th>
                <th>状态</th>
                <th>行数</th>
                <th>敏感级别</th>
                <th>最近更新时间</th>
                <th>入口</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="rows.length === 0" class="admin-empty-row">
                <td colspan="8">当前筛选下没有匹配的数据表。</td>
              </tr>
              <tr v-for="item in rows" :key="item.tableName" class="admin-row-link" @click="openDetail(item.tableName)">
                <td><code>{{ item.tableName }}</code></td>
                <td>
                  <strong>{{ item.title || item.tableName }}</strong>
                  <div class="admin-subtle data-catalog-description">{{ item.description || '该表已自动发现，尚未补充业务说明。' }}</div>
                </td>
                <td>{{ item.module || '未分组' }}</td>
                <td>
                  <span class="admin-badge" :class="item.configured ? 'data-catalog-status--configured' : 'data-catalog-status--discovered'">
                    {{ item.configured ? '配置增强' : '自动发现' }}
                  </span>
                </td>
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

      <div v-else class="data-catalog-graph-view">
        <div class="admin-toolbar data-catalog-filter">
          <div class="admin-toolbar-left">
            <select v-model="graphFilters.module" class="admin-select">
              <option value="">全部模块</option>
              <option v-for="item in modules" :key="item" :value="item">{{ item }}</option>
            </select>
            <select v-model="graphFilters.tableName" class="admin-select">
              <option value="">按模块查看</option>
              <option v-for="item in rows" :key="item.tableName" :value="item.tableName">
                {{ item.title || item.tableName }} / {{ item.tableName }}
              </option>
            </select>
          </div>
          <div class="data-catalog-filter-actions">
            <button class="admin-btn" @click="loadGraph">生成关系图</button>
            <button class="admin-btn admin-btn--secondary" @click="copyMermaid">复制 Mermaid</button>
            <button class="admin-btn admin-btn--secondary" @click="downloadMermaid">下载 Mermaid</button>
            <button class="admin-btn admin-btn--secondary" @click="downloadDbml">下载 DBML</button>
          </div>
        </div>

        <div v-if="graphLoading" class="admin-empty">正在生成关系图...</div>
        <div v-else-if="graphError" class="admin-error">
          {{ graphError }}
          <button class="admin-btn admin-btn--secondary" @click="loadGraph">重试</button>
        </div>
        <template v-else>
          <div class="admin-grid-two">
            <div class="admin-card data-catalog-graph-panel">
              <div class="admin-toolbar data-catalog-graph-toolbar">
                <div>
                  <h2 class="admin-card-title">关系图</h2>
                  <p class="admin-subtle">实线表示数据库真实外键，虚线表示配置补充的逻辑关系。</p>
                </div>
              </div>
              <AdminMermaidGraph :definition="mermaidDefinition" empty-text="当前筛选下暂无可展示的关系图。" />
            </div>

            <div class="admin-card">
              <h2 class="admin-card-title">图例与范围</h2>
              <ul class="data-catalog-legend">
                <li><span class="data-catalog-legend-chip data-catalog-legend-chip--configured"></span> 已配置增强</li>
                <li><span class="data-catalog-legend-chip data-catalog-legend-chip--discovered"></span> 自动发现</li>
                <li><span class="data-catalog-legend-line data-catalog-legend-line--physical"></span> 真实外键</li>
                <li><span class="data-catalog-legend-line data-catalog-legend-line--logical"></span> 逻辑关系</li>
              </ul>
              <div class="data-catalog-side-list">
                <div class="admin-subtle">图中表（{{ graph?.nodes.length || 0 }}）</div>
                <button
                  v-for="node in graph?.nodes || []"
                  :key="node.tableName"
                  type="button"
                  class="data-catalog-side-item"
                  @click="openDetail(node.tableName)"
                >
                  <strong>{{ node.title || node.tableName }}</strong>
                  <span>{{ node.tableName }}</span>
                </button>
              </div>
            </div>
          </div>

          <div class="admin-card">
            <h2 class="admin-card-title">关系明细</h2>
            <div v-if="!graph || graph.edges.length === 0" class="admin-empty">当前范围内没有关系边。</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>类型</th>
                    <th>源表</th>
                    <th>源字段</th>
                    <th>目标表</th>
                    <th>目标字段</th>
                    <th>说明</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="edge in graph.edges" :key="`${edge.relationType}-${edge.sourceTable}-${edge.sourceColumn}-${edge.targetTable}-${edge.targetColumn}`">
                    <td>
                      <span class="admin-badge" :class="edge.relationType === 'logical' ? 'data-catalog-status--discovered' : 'data-catalog-status--configured'">
                        {{ edge.relationType === 'logical' ? '逻辑' : '物理' }}
                      </span>
                    </td>
                    <td><code>{{ edge.sourceTable }}</code></td>
                    <td>{{ edge.sourceColumn || '-' }}</td>
                    <td><code>{{ edge.targetTable }}</code></td>
                    <td>{{ edge.targetColumn || '-' }}</td>
                    <td>{{ edge.description || formatRelationshipLabel(edge) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, type AdminDataCatalogGraph, type AdminDataCatalogTable } from '@/api/admin'
import AdminMermaidGraph from '@/components/admin/AdminMermaidGraph.vue'
import { buildAdminDataCatalogMermaid, formatRelationshipLabel } from './dataCatalogGraph'
import { showToast } from '@/utils/toast'

type CatalogView = 'overview' | 'graph'

const route = useRoute()
const router = useRouter()
const rows = ref<AdminDataCatalogTable[]>([])
const graph = ref<AdminDataCatalogGraph | null>(null)
const tableLoading = ref(false)
const graphLoading = ref(false)
const tableError = ref('')
const graphError = ref('')

const filters = ref({
  keyword: '',
  module: '',
  sensitivity: '',
  hasAdminRoute: '',
})

const graphFilters = ref({
  module: '',
  tableName: '',
})

const activeView = computed<CatalogView>(() => (route.query.view === 'graph' ? 'graph' : 'overview'))

const configuredCount = computed(() => rows.value.filter((item) => item.configured).length)
const autoDiscoveredCount = computed(() => rows.value.filter((item) => !item.configured).length)

const modules = computed(() => {
  const set = new Set<string>()
  rows.value.forEach((item) => {
    if (item.module) set.add(item.module)
  })
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const mermaidDefinition = computed(() => buildAdminDataCatalogMermaid(graph.value || { nodes: [], edges: [] }))

watch(activeView, async (view) => {
  if (view === 'graph' && !graph.value && !graphLoading.value) {
    await loadGraph()
  }
})

async function loadTables() {
  tableLoading.value = true
  tableError.value = ''
  try {
    rows.value = await adminApi.listDataCatalogTables(cleanParams(filters.value))
  } catch {
    tableError.value = '加载数据地图失败'
  } finally {
    tableLoading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    graph.value = await adminApi.getDataCatalogGraph(cleanParams(graphFilters.value))
  } catch {
    graphError.value = '加载关系图失败'
  } finally {
    graphLoading.value = false
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

function switchView(view: CatalogView) {
  router.replace({
    query: {
      ...route.query,
      view: view === 'overview' ? undefined : 'graph',
    },
  })
}

function resetFilters() {
  filters.value = { keyword: '', module: '', sensitivity: '', hasAdminRoute: '' }
  loadTables()
}

function openDetail(tableName: string) {
  router.push(`/admin/data-catalog/${encodeURIComponent(tableName)}`)
}

async function copyMermaid() {
  try {
    const text = await adminApi.exportDataCatalogMermaid(cleanParams(graphFilters.value))
    await navigator.clipboard.writeText(text)
    showToast('Mermaid 已复制', 'success')
  } catch {
    showToast('复制 Mermaid 失败', 'error')
  }
}

async function downloadMermaid() {
  await downloadText('mermaid')
}

async function downloadDbml() {
  await downloadText('dbml')
}

async function downloadText(type: 'mermaid' | 'dbml') {
  try {
    const text = type === 'mermaid'
      ? await adminApi.exportDataCatalogMermaid(cleanParams(graphFilters.value))
      : await adminApi.exportDataCatalogDbml(cleanParams(graphFilters.value))
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `data-catalog-${graphFilters.value.tableName || graphFilters.value.module || 'all'}.${type === 'mermaid' ? 'mmd' : 'dbml'}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch {
    showToast(`下载 ${type.toUpperCase()} 失败`, 'error')
  }
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

onMounted(async () => {
  await loadTables()
  if (activeView.value === 'graph') {
    await loadGraph()
  }
})
</script>

<style scoped>
.data-catalog-view-switch,
.data-catalog-filter-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.data-catalog-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 20px 0;
}

.data-catalog-summary-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.data-catalog-summary-card span {
  color: var(--admin-subtle);
  font-size: 13px;
}

.data-catalog-summary-card strong {
  font-size: 28px;
  color: var(--admin-text);
}

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

.data-catalog-status--configured {
  background: rgba(19, 111, 75, 0.1);
  color: var(--admin-accent-dark);
}

.data-catalog-status--discovered {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
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

.data-catalog-graph-view {
  display: grid;
  gap: 20px;
}

.data-catalog-graph-panel {
  min-height: 520px;
}

.data-catalog-graph-toolbar {
  margin-bottom: 16px;
}

.data-catalog-legend {
  list-style: none;
  padding: 0;
  margin: 0 0 20px;
  display: grid;
  gap: 12px;
}

.data-catalog-legend li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.data-catalog-legend-chip {
  width: 20px;
  height: 12px;
  border-radius: 999px;
  border: 1.5px solid rgba(15, 23, 42, 0.18);
}

.data-catalog-legend-chip--configured {
  background: rgba(19, 111, 75, 0.14);
}

.data-catalog-legend-chip--discovered {
  background: rgba(37, 99, 235, 0.14);
  border-style: dashed;
}

.data-catalog-legend-line {
  width: 28px;
  height: 0;
  border-top: 2px solid #0f172a;
}

.data-catalog-legend-line--logical {
  border-top-style: dashed;
}

.data-catalog-side-list {
  display: grid;
  gap: 10px;
}

.data-catalog-side-item {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  padding: 12px;
  background: #fff;
  text-align: left;
  display: grid;
  gap: 4px;
}

.data-catalog-side-item strong {
  color: var(--admin-text);
}

.data-catalog-side-item span {
  color: var(--admin-subtle);
  font-size: 12px;
}

@media (max-width: 1200px) {
  .data-catalog-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .data-catalog-summary {
    grid-template-columns: 1fr;
  }
}
</style>
