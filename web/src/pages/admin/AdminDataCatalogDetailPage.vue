<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <router-link class="admin-back-link" to="/admin/data-catalog">返回数据地图</router-link>
          <h1 class="admin-card-title data-catalog-detail-title">{{ detail?.title || route.params.tableName }}</h1>
          <p class="admin-subtle">{{ detail?.description || '查看表结构、敏感字段、关系图和业务入口。' }}</p>
        </div>
        <div class="data-catalog-detail-actions">
          <span
            v-if="detail"
            class="admin-badge"
            :class="detail.configured ? 'data-catalog-status--configured' : 'data-catalog-status--discovered'"
          >
            {{ detail.configured ? '配置增强' : '自动发现' }}
          </span>
          <router-link v-if="detail?.adminRoute" class="admin-btn" :to="detail.adminRoute">进入业务页面</router-link>
        </div>
      </div>

      <div v-if="loading" class="admin-empty">正在加载表详情...</div>
      <div v-else-if="error" class="admin-error">
        {{ error }}
        <button class="admin-btn admin-btn--secondary" @click="load">重试</button>
      </div>
      <template v-else-if="detail">
        <div class="admin-grid-three">
          <div class="admin-kv"><span>表名</span><strong>{{ detail.tableName }}</strong></div>
          <div class="admin-kv"><span>所属模块</span><strong>{{ detail.module || '未分组' }}</strong></div>
          <div class="admin-kv"><span>敏感级别</span><strong>{{ detail.sensitivity || 'low' }}</strong></div>
          <div class="admin-kv"><span>行数</span><strong>{{ formatNumber(detail.rowCount) }}</strong></div>
          <div class="admin-kv"><span>最近更新时间</span><strong>{{ detail.latestAt || '-' }}</strong></div>
          <div class="admin-kv"><span>业务入口</span><strong>{{ detail.adminRoute || '-' }}</strong></div>
        </div>
      </template>
    </div>

    <template v-if="detail">
      <div class="admin-grid-two">
        <div class="admin-card">
          <h2 class="admin-card-title">字段字典</h2>
          <div class="admin-table-wrap">
            <table class="admin-table">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>类型</th>
                  <th>Nullable</th>
                  <th>默认值</th>
                  <th>标记</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detail.columns.length === 0" class="admin-empty-row"><td colspan="6">暂无字段信息。</td></tr>
                <tr v-for="column in detail.columns" :key="column.name">
                  <td><code>{{ column.name }}</code></td>
                  <td>{{ column.type || '-' }}</td>
                  <td>{{ column.nullable ? 'YES' : 'NO' }}</td>
                  <td>{{ column.defaultValue ?? '-' }}</td>
                  <td>
                    <span v-if="column.primaryKey" class="admin-badge">PK</span>
                    <span v-if="column.sensitive" class="admin-badge data-catalog-sensitive">敏感</span>
                  </td>
                  <td>{{ column.comment || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="admin-card">
          <h2 class="admin-card-title">局部关系图</h2>
          <p class="admin-subtle">以当前表为中心，展示数据库真实外键和补充的逻辑关系。</p>
          <div v-if="graphLoading" class="admin-empty">正在加载关系图...</div>
          <div v-else-if="graphError" class="admin-error">
            {{ graphError }}
            <button class="admin-btn admin-btn--secondary" @click="loadGraph">重试</button>
          </div>
          <AdminMermaidGraph v-else :definition="mermaidDefinition" empty-text="当前表没有可展示的关系。" />
        </div>
      </div>

      <div class="admin-grid-two">
        <div class="admin-card">
          <h2 class="admin-card-title">索引</h2>
          <div v-if="detail.indexes.length === 0" class="admin-empty">暂无索引信息。</div>
          <div v-else>
            <div v-for="item in detail.indexes" :key="item.name" class="admin-kv">
              <span>{{ item.name }}</span>
              <strong>{{ item.columns || '-' }}{{ item.uniqueIndex ? ' / unique' : '' }}</strong>
            </div>
          </div>
        </div>

        <div class="admin-card">
          <h2 class="admin-card-title">外键关系</h2>
          <div v-if="detail.foreignKeys.length === 0" class="admin-empty">暂无数据库真实外键。</div>
          <div v-else>
            <div v-for="item in detail.foreignKeys" :key="`${item.name}-${item.columnName}`" class="admin-kv">
              <span>{{ item.columnName }}</span>
              <strong>{{ item.referencedTableName }}.{{ item.referencedColumnName }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <h2 class="admin-card-title">业务关系说明</h2>
        <div v-if="detail.relationships.length === 0" class="admin-empty">暂无业务关系说明。</div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>方向</th>
                <th>类型</th>
                <th>源表</th>
                <th>源字段</th>
                <th>目标表</th>
                <th>目标字段</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in detail.relationships" :key="`${item.direction}-${item.relationType}-${item.sourceTable}-${item.sourceColumn}-${item.targetTable}-${item.targetColumn}`">
                <td>{{ item.direction === 'incoming' ? '被引用' : '引用外部' }}</td>
                <td>
                  <span class="admin-badge" :class="item.relationType === 'logical' ? 'data-catalog-status--discovered' : 'data-catalog-status--configured'">
                    {{ item.relationType === 'logical' ? '逻辑' : '物理' }}
                  </span>
                </td>
                <td><code>{{ item.sourceTable }}</code></td>
                <td>{{ item.sourceColumn || '-' }}</td>
                <td><code>{{ item.targetTable }}</code></td>
                <td>{{ item.targetColumn || '-' }}</td>
                <td>{{ item.description || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="admin-card">
        <h2 class="admin-card-title">安全说明</h2>
        <div v-if="detail.securityNotes.length === 0" class="admin-empty">暂无额外安全说明。</div>
        <ul v-else class="data-catalog-notes">
          <li v-for="note in detail.securityNotes" :key="note">{{ note }}</li>
        </ul>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi, type AdminDataCatalogGraph, type AdminDataCatalogTableDetail } from '@/api/admin'
import AdminMermaidGraph from '@/components/admin/AdminMermaidGraph.vue'
import { buildAdminDataCatalogMermaid } from './dataCatalogGraph'

const route = useRoute()
const detail = ref<AdminDataCatalogTableDetail | null>(null)
const graph = ref<AdminDataCatalogGraph | null>(null)
const loading = ref(false)
const graphLoading = ref(false)
const error = ref('')
const graphError = ref('')

const mermaidDefinition = computed(() => buildAdminDataCatalogMermaid(graph.value || { nodes: [], edges: [] }))

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await adminApi.getDataCatalogTable(String(route.params.tableName))
  } catch {
    error.value = '加载表详情失败'
  } finally {
    loading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    graph.value = await adminApi.getDataCatalogGraph({ tableName: String(route.params.tableName) })
  } catch {
    graphError.value = '加载关系图失败'
  } finally {
    graphLoading.value = false
  }
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

watch(() => route.params.tableName, async () => {
  await load()
  await loadGraph()
})

onMounted(async () => {
  await load()
  await loadGraph()
})
</script>

<style scoped>
.data-catalog-detail-title {
  margin-top: 16px;
}

.data-catalog-detail-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.data-catalog-status--configured {
  background: rgba(19, 111, 75, 0.1);
  color: var(--admin-accent-dark);
}

.data-catalog-status--discovered {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.data-catalog-sensitive {
  margin-left: 6px;
  background: rgba(203, 65, 84, 0.12);
  color: #a32136;
}

.data-catalog-notes {
  margin: 12px 0 0;
  padding-left: 20px;
  color: var(--admin-text);
}
</style>
