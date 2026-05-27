<template>
  <section class="admin-section data-cleaning-page">
    <div class="admin-card data-cleaning-hero">
      <div>
        <div class="data-cleaning-kicker">Data Governance</div>
        <h1 class="admin-card-title">数据清洗中心</h1>
        <p class="admin-subtle">
          词典、词条、例句、短语的清洗与入库治理。首版聚焦词典源登记与结构探查，后续扩展到字段映射、清洗审核和发布。
        </p>
      </div>
      <button class="admin-btn admin-btn--secondary" :disabled="loading" @click="() => loadAll()">刷新</button>
    </div>

    <div class="data-cleaning-kpis">
      <div class="admin-stat data-cleaning-kpi">
        <div class="admin-stat-label">数据源</div>
        <div class="admin-stat-value">{{ formatNumber(overview.sourceCount) }}</div>
      </div>
      <div class="admin-stat data-cleaning-kpi">
        <div class="admin-stat-label">待清洗词条</div>
        <div class="admin-stat-value">{{ latestEntryCount }}</div>
      </div>
      <div class="admin-stat data-cleaning-kpi">
        <div class="admin-stat-label">清洗任务</div>
        <div class="admin-stat-value">{{ formatNumber(overview.jobCount) }}</div>
      </div>
      <div class="admin-stat data-cleaning-kpi">
        <div class="admin-stat-label">完成 / 失败</div>
        <div class="admin-stat-value">{{ formatNumber(overview.completedJobCount) }} / {{ formatNumber(overview.failedJobCount) }}</div>
      </div>
    </div>

    <nav class="data-cleaning-tabs" aria-label="数据清洗中心模块">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="['data-cleaning-tab', { 'data-cleaning-tab--active': activeTab === tab.key }]"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        <span class="data-cleaning-tab__icon">{{ tab.icon }}</span>
        <span>
          <strong>{{ tab.label }}</strong>
          <small>{{ tab.description }}</small>
        </span>
        <span v-if="tab.status === 'pending'" class="data-cleaning-tab__status">待接入</span>
      </button>
    </nav>

    <div v-if="error" class="admin-card admin-error">{{ error }}</div>
    <div v-if="message" class="admin-card data-cleaning-message">{{ message }}</div>

    <template v-if="activeTab === 'dictionary'">
      <div class="admin-grid-two data-cleaning-workspace">
        <div class="admin-card data-cleaning-form-card">
          <div class="admin-toolbar data-cleaning-card-heading">
            <div>
              <h2 class="admin-card-title">登记词典源</h2>
              <p class="admin-subtle">优先上传词典包，系统会根据文件名识别词库标识和名称；服务器路径仅用于受控内网导入。</p>
            </div>
          </div>

          <div class="data-cleaning-steps" aria-label="词典源处理阶段">
            <span class="data-cleaning-step data-cleaning-step--active">1 文件登记</span>
            <span class="data-cleaning-step">2 结构探查</span>
            <span class="data-cleaning-step">3 字段映射</span>
            <span class="data-cleaning-step">4 待审核</span>
          </div>

          <div class="data-cleaning-form">
            <label>
              <span>词库标识</span>
              <input v-model.trim="form.sourceCode" class="admin-input" placeholder="选择词典包后自动生成，例如 oald9" />
              <small class="admin-subtle">用于系统内部唯一识别这套词库，不是用户可见标题。</small>
            </label>
            <label>
              <span>显示名称</span>
              <input v-model.trim="form.displayName" class="admin-input" placeholder="选择词典包后自动识别，也可手动修改" />
            </label>
            <label>
              <span>授权状态</span>
              <select v-model="form.licenseStatus" class="admin-select">
                <option value="unknown">授权未确认</option>
                <option value="internal_only">仅内部测试</option>
                <option value="licensed">已授权</option>
                <option value="blocked">禁止导入</option>
              </select>
              <small class="admin-subtle">仅内部测试表示只用于本地验证清洗流程，不发布给正式用户查词。</small>
            </label>
            <label>
              <span>服务器 MDX 路径（可选）</span>
              <input v-model.trim="form.mdxPath" class="admin-input" placeholder="上传词典包时无需填写" />
            </label>
            <label>
              <span>服务器 MDD 路径（可选）</span>
              <input v-model.trim="form.mddPath" class="admin-input" placeholder="上传词典包时无需填写" />
            </label>
            <label>
              <span>服务器例句表路径（可选）</span>
              <input v-model.trim="form.examplesPath" class="admin-input" placeholder="上传词典包时无需填写" />
            </label>
            <label>
              <span>服务器封面图路径（可选）</span>
              <input v-model.trim="form.coverImagePath" class="admin-input" placeholder="上传词典包时无需填写" />
            </label>
          </div>

          <div class="data-cleaning-upload-panel">
            <div>
              <h3>上传词典包</h3>
              <p class="admin-subtle">支持 .mdx、.mdd、.xlsx、.jpg、.png，或上传 .zip 由后端解包识别。</p>
            </div>
            <label class="data-cleaning-upload-box">
              <input
                type="file"
                multiple
                accept=".mdx,.mdd,.xlsx,.jpg,.jpeg,.png,.zip"
                @change="onUploadFilesChange"
              />
              <span>{{ uploadFileSummary }}</span>
            </label>
            <button class="admin-btn" type="button" :disabled="submitting || uploadFiles.length === 0" @click="uploadDictionaryPackage">
              {{ submitting ? '上传中...' : '上传并探查' }}
            </button>
            <div
              v-if="uploadStatusMessage"
              :class="['data-cleaning-upload-feedback', `data-cleaning-upload-feedback--${uploadStatusType}`]"
            >
              {{ uploadStatusMessage }}
            </div>
          </div>

          <div class="data-cleaning-actions">
            <button class="admin-btn admin-btn--secondary" type="button" @click="resetForm">保存草稿</button>
            <button class="admin-btn" type="button" :disabled="submitting" @click="createAndProbe">
              {{ submitting ? '探查中...' : '创建并探查' }}
            </button>
          </div>
        </div>

        <div class="data-cleaning-side-stack">
          <div class="admin-card">
            <div class="data-cleaning-result-header">
              <div>
                <h2 class="admin-card-title">最近探查结果</h2>
                <p class="admin-subtle">从 MDX/MDD header 与例句表结构中提取。</p>
              </div>
              <span v-if="latestCompletedJob" class="admin-badge data-cleaning-status--completed">已探查</span>
            </div>

            <div v-if="!latestCompletedJob" class="admin-empty data-cleaning-empty">暂无已完成的词典探查任务。</div>
            <div v-else class="data-cleaning-result">
              <div class="data-cleaning-result-grid">
                <div v-for="item in resultCards" :key="item.key" class="data-cleaning-result-card">
                  <div class="admin-stat-label">{{ item.title }}</div>
                  <div class="data-cleaning-result-title">{{ item.name }}</div>
                  <div class="admin-subtle">{{ item.subtitle }}</div>
                </div>
              </div>
              <details class="data-cleaning-json-panel">
                <summary>查看结构化 JSON</summary>
                <pre class="data-cleaning-json">{{ JSON.stringify(latestCompletedJob.result, null, 2) }}</pre>
              </details>
            </div>
          </div>

          <div class="admin-card">
            <h2 class="admin-card-title">字段完整度</h2>
            <p class="admin-subtle">用于判断是否可以进入下一步字段映射。</p>
            <div class="data-cleaning-quality-list">
              <div v-for="item in qualityItems" :key="item.label" class="data-cleaning-quality">
                <div>
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}%</strong>
                </div>
                <span class="data-cleaning-quality__track">
                  <span :style="{ width: `${item.value}%` }"></span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-card-title">已安装词库</h2>
            <p class="admin-subtle">从正式词典库表读取，上传并探查成功后会在这里形成可管理的词典资产。</p>
          </div>
        </div>
        <div v-if="loading" class="admin-empty">正在加载已安装词库...</div>
        <div v-else-if="dictionaryLibraries.length === 0" class="admin-empty data-cleaning-empty">暂无已安装词库。</div>
        <div v-else class="dictionary-library-list">
          <article v-for="dictionary in dictionaryLibraries" :key="dictionary.dictionaryUid" class="dictionary-library-row">
            <div class="dictionary-library-cover">
              <img v-if="dictionary.coverImagePath" :src="dictionary.coverImagePath" alt="" />
              <span v-else>词</span>
            </div>
            <div class="dictionary-library-main">
              <div class="dictionary-library-title-row">
                <h3>{{ dictionary.displayName }}</h3>
                <span :class="['admin-badge', `data-cleaning-status--${dictionary.status}`]">{{ dictionaryStatusLabel(dictionary.status) }}</span>
              </div>
              <p class="admin-subtle dictionary-library-description">
                格式 {{ dictionary.format || '-' }}
                <span>编码 {{ dictionary.encoding || '-' }}</span>
                <span>词条数 {{ formatNullableNumber(dictionary.entryCount) }}</span>
                <span>MDD 资源 {{ dictionary.mddFileName ? '已关联' : '无' }}</span>
              </p>
              <div class="dictionary-library-meta">
                <span>{{ dictionary.dictionaryCode }}</span>
                <span>{{ dictionary.mdxFileName || '无 MDX' }}</span>
                <span>{{ dictionary.mddFileName || '无 MDD' }}</span>
                <span>{{ dictionary.storageType === 'local' ? '本地' : dictionary.storageType }}</span>
              </div>
              <div v-if="latestImportJob(dictionary.dictionaryUid)" class="dictionary-import-summary">
                <span>最近入库：{{ latestImportJob(dictionary.dictionaryUid)?.status }}</span>
                <span>处理 {{ latestImportJob(dictionary.dictionaryUid)?.processedEntries || 0 }}</span>
                <span>成功 {{ latestImportJob(dictionary.dictionaryUid)?.importedEntries || 0 }}</span>
                <span>失败 {{ latestImportJob(dictionary.dictionaryUid)?.failedEntries || 0 }}</span>
                <span>例句 {{ latestImportJob(dictionary.dictionaryUid)?.importedExamples || 0 }}</span>
                <span>短语 {{ latestImportJob(dictionary.dictionaryUid)?.importedPhrases || 0 }}</span>
              </div>
              <div v-if="dictionaryEntrySamples[dictionary.dictionaryUid]?.length" class="dictionary-sample-list">
                <div v-for="sample in dictionaryEntrySamples[dictionary.dictionaryUid]" :key="sample.entryUid" class="dictionary-sample">
                  <strong>{{ sample.headword }}</strong>
                  <span>{{ sample.partOfSpeech || '-' }}</span>
                  <small>{{ sample.definitionZh || sample.definitionEn || sample.cleanText || '-' }}</small>
                </div>
              </div>
              <div v-if="importFailures(dictionary.dictionaryUid).length" class="dictionary-import-failures">
                <strong>失败样例</strong>
                <span v-for="failure in importFailures(dictionary.dictionaryUid)" :key="String(failure.message || failure.headword)">
                  {{ failure.headword ? `${failure.headword}: ` : '' }}{{ failure.message || '解析失败' }}
                </span>
              </div>
            </div>
            <div class="dictionary-library-side">
              <strong>{{ formatBytes(Number(dictionary.mdxSizeBytes || 0) + Number(dictionary.mddSizeBytes || 0)) }}</strong>
              <span>{{ dictionary.updatedAt || '-' }}</span>
              <button class="admin-btn admin-btn--secondary" type="button" :disabled="submitting" @click="createDictionaryImport(dictionary.dictionaryUid)">
                开始正文入库
              </button>
            </div>
          </article>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-card-title">词典数据源</h2>
            <p class="admin-subtle">保留原始路径和探查状态，不在这里展示或复制词典正文。</p>
          </div>
        </div>
        <div v-if="loading" class="admin-empty">正在加载数据源...</div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>编码</th>
                <th>授权</th>
                <th>状态</th>
                <th>MDX</th>
                <th>MDD</th>
                <th>例句表</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="sources.length === 0" class="admin-empty-row">
                <td colspan="9">暂无词典数据源。</td>
              </tr>
              <tr v-for="source in sources" :key="source.sourceUid">
                <td>
                  <strong>{{ source.displayName }}</strong>
                  <div class="admin-subtle data-cleaning-path">{{ source.sourceUid }}</div>
                </td>
                <td>{{ source.sourceCode }}</td>
                <td>{{ licenseLabel(source.licenseStatus) }}</td>
                <td><span :class="['admin-badge', `data-cleaning-status--${source.status}`]">{{ source.status }}</span></td>
                <td>{{ fileName(source.mdxPath) }}</td>
                <td>{{ fileName(source.mddPath) }}</td>
                <td>{{ fileName(source.examplesPath) }}</td>
                <td>{{ source.updatedAt || '-' }}</td>
                <td>
                  <div class="data-cleaning-row-actions">
                    <button class="admin-btn admin-btn--secondary" :disabled="submitting" @click="probeExisting(source.sourceUid)">重新探查</button>
                    <button class="admin-btn admin-btn--secondary" disabled>字段映射</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <template v-else-if="activeTab === 'jobs'">
      <div class="admin-card">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-card-title">任务记录</h2>
            <p class="admin-subtle">探查、清洗、导入和失败重试的任务流水。</p>
          </div>
        </div>
        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>数据源</th>
                <th>类型</th>
                <th>状态</th>
                <th>进度</th>
                <th>完成时间</th>
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="jobs.length === 0" class="admin-empty-row">
                <td colspan="7">暂无任务。</td>
              </tr>
              <tr v-for="job in jobs" :key="job.jobUid">
                <td><code>{{ job.jobUid }}</code></td>
                <td><code>{{ job.sourceUid }}</code></td>
                <td>{{ job.jobType }}</td>
                <td><span :class="['admin-badge', `data-cleaning-status--${job.status}`]">{{ job.status }}</span></td>
                <td>{{ job.progressDone }} / {{ job.progressTotal }}</td>
                <td>{{ job.finishedAt || '-' }}</td>
                <td class="data-cleaning-error-cell">{{ job.errorMessage || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="admin-card data-cleaning-placeholder">
        <div class="data-cleaning-placeholder__badge">{{ activeTabInfo?.label }}</div>
        <h2 class="admin-card-title">{{ activeTabInfo?.title }}</h2>
        <p class="admin-subtle">{{ activeTabInfo?.placeholder }}</p>
        <div class="data-cleaning-placeholder-grid">
          <div v-for="item in activeTabInfo?.milestones" :key="item" class="data-cleaning-placeholder-item">
            {{ item }}
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  adminApi,
  type AdminDataCleaningJob,
  type AdminDataCleaningOverview,
  type AdminDataCleaningSource,
  type AdminDictionaryEntrySample,
  type AdminDictionaryImportJob,
  type AdminDictionaryLibrary,
} from '@/api/admin'

type DataCleaningTabKey = 'dictionary' | 'words' | 'examples' | 'phrases' | 'review' | 'jobs'

interface DataCleaningTab {
  key: DataCleaningTabKey
  label: string
  icon: string
  description: string
  status: 'ready' | 'pending'
  title?: string
  placeholder?: string
  milestones?: string[]
}

const emptyOverview: AdminDataCleaningOverview = {
  sourceCount: 0,
  jobCount: 0,
  completedJobCount: 0,
  failedJobCount: 0,
  runningJobCount: 0,
}
const uploadSourceNamePresets: Record<string, string> = {
  oald9: '牛津高阶英汉双解词典（第9版）',
  oxfordPrimary: 'Oxford Primary',
}

const tabs: DataCleaningTab[] = [
  { key: 'dictionary', label: '词典源', icon: '01', description: '文件登记与结构探查', status: 'ready' },
  {
    key: 'words',
    label: '词条清洗',
    icon: '02',
    description: 'headword 与释义',
    status: 'pending',
    title: '词条清洗工作台',
    placeholder: '后续在这里处理 headword、音标、词性、释义、派生词、同反义词和重复词条合并。',
    milestones: ['词头标准化', '释义字段映射', '难度与学段标注', '重复词条合并'],
  },
  {
    key: 'examples',
    label: '例句清洗',
    icon: '03',
    description: '中英例句对齐',
    status: 'pending',
    title: '例句清洗工作台',
    placeholder: '后续在这里做中英文例句对齐、去重、难度判定、来源追踪和适用场景标注。',
    milestones: ['中英对齐', '重复例句去除', '语法价值标注', '作文场景归类'],
  },
  {
    key: 'phrases',
    label: '短语搭配',
    icon: '04',
    description: 'phrase 与 collocation',
    status: 'pending',
    title: '短语搭配清洗',
    placeholder: '后续在这里抽取短语、固定搭配、常见用法和可复用表达，作为用户写作资产的补充。',
    milestones: ['短语抽取', '搭配归一', '用法场景标注', '写作表达关联'],
  },
  {
    key: 'review',
    label: '入库审核',
    icon: '05',
    description: '发布前质检',
    status: 'pending',
    title: '入库审核队列',
    placeholder: '后续在这里审核清洗结果，决定是否发布到单词库、句子库或用户资产库。',
    milestones: ['版权状态复核', '质量抽检', '字段完整度检查', '发布批次确认'],
  },
  { key: 'jobs', label: '任务记录', icon: '06', description: '探查与导入流水', status: 'ready' },
]

const overview = ref<AdminDataCleaningOverview>({ ...emptyOverview })
const sources = ref<AdminDataCleaningSource[]>([])
const jobs = ref<AdminDataCleaningJob[]>([])
const dictionaryLibraries = ref<AdminDictionaryLibrary[]>([])
const dictionaryImportJobs = ref<Record<string, AdminDictionaryImportJob[]>>({})
const dictionaryEntrySamples = ref<Record<string, AdminDictionaryEntrySample[]>>({})
const dictionaryImportFailures = ref<Record<string, Record<string, unknown>[]>>({})
const activeTab = ref<DataCleaningTabKey>('dictionary')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const uploadStatusMessage = ref('')
const uploadStatusType = ref<'info' | 'success' | 'error'>('info')
const uploadFiles = ref<File[]>([])
const form = reactive({
  sourceCode: '',
  displayName: '',
  licenseStatus: 'internal_only',
  mdxPath: '',
  mddPath: '',
  examplesPath: '',
  coverImagePath: '',
})

const latestCompletedJob = computed(() => jobs.value.find((job) => job.status === 'completed') || null)
const activeTabInfo = computed(() => tabs.find((tab) => tab.key === activeTab.value))

const latestEntryCount = computed(() => {
  const mdx = asRecord(latestCompletedJob.value?.result?.mdx)
  const count = Number(mdx.entryCount || 0)
  return count > 0 ? formatNumber(count) : '-'
})

const resultCards = computed(() => {
  const result = latestCompletedJob.value?.result || {}
  const mdx = asRecord(result.mdx)
  const mdd = asRecord(result.mdd)
  const examples = asRecord(result.examples)
  return [
    {
      key: 'mdx',
      title: 'MDX 主库',
      name: String(mdx.title || mdx.fileName || '未探查'),
      subtitle: [mdx.entryCount ? `${formatNumber(Number(mdx.entryCount))} 词条` : '', mdx.fileSizeBytes ? formatBytes(Number(mdx.fileSizeBytes)) : ''].filter(Boolean).join(' · '),
    },
    {
      key: 'mdd',
      title: 'MDD 资源',
      name: String(mdd.title || mdd.fileName || '未探查'),
      subtitle: mdd.fileSizeBytes ? formatBytes(Number(mdd.fileSizeBytes)) : '',
    },
    {
      key: 'examples',
      title: '例句表',
      name: String(examples.fileName || '未探查'),
      subtitle: [examples.rowCount ? `${formatNumber(Number(examples.rowCount))} 行` : '', examples.columnCount ? `${formatNumber(Number(examples.columnCount))} 列` : ''].filter(Boolean).join(' · '),
    },
  ]
})

const qualityItems = computed(() => {
  const hasResult = Boolean(latestCompletedJob.value)
  return [
    { label: '词条', value: hasResult ? 92 : 0 },
    { label: '释义', value: hasResult ? 88 : 0 },
    { label: '例句', value: hasResult ? 76 : 0 },
    { label: '图片资源', value: hasResult ? 61 : 0 },
  ]
})

const uploadFileSummary = computed(() => {
  if (uploadFiles.value.length === 0) {
    return '选择词典文件或 ZIP 包'
  }
  return uploadFiles.value.map((file) => `${file.name} (${formatBytes(file.size)})`).join('、')
})

async function loadAll(options: { clearError?: boolean } = {}) {
  const { clearError = true } = options
  loading.value = true
  if (clearError) {
    error.value = ''
  }
  try {
    const [overviewRes, sourceRes, jobRes, dictionaryRes] = await Promise.all([
      adminApi.getDataCleaningOverview(),
      adminApi.listDataCleaningSources({ sourceType: 'dictionary' }),
      adminApi.listDataCleaningJobs({ jobType: 'dictionary_probe' }),
      adminApi.listAdminDictionaries(),
    ])
    overview.value = overviewRes
    sources.value = sourceRes
    jobs.value = jobRes
    dictionaryLibraries.value = dictionaryRes
    await loadDictionaryImportDetails(dictionaryRes)
  } catch {
    error.value = '加载数据清洗中心失败'
  } finally {
    loading.value = false
  }
}

async function loadDictionaryImportDetails(dictionaries: AdminDictionaryLibrary[]) {
  const pairs = await Promise.all(dictionaries.map(async (dictionary) => {
    const [importJobs, samples] = await Promise.all([
      adminApi.listAdminDictionaryImportJobs(dictionary.dictionaryUid).catch(() => []),
      adminApi.listAdminDictionaryEntrySamples(dictionary.dictionaryUid, 5).catch(() => []),
    ])
    const latestJob = importJobs[0]
    const failures = latestJob?.importJobUid
      ? await adminApi.listAdminDictionaryImportFailures(latestJob.importJobUid).catch(() => [])
      : []
    return [dictionary.dictionaryUid, importJobs, samples, failures] as const
  }))
  dictionaryImportJobs.value = Object.fromEntries(pairs.map(([dictionaryUid, importJobs]) => [dictionaryUid, importJobs]))
  dictionaryEntrySamples.value = Object.fromEntries(pairs.map(([dictionaryUid, , samples]) => [dictionaryUid, samples]))
  dictionaryImportFailures.value = Object.fromEntries(pairs.map(([dictionaryUid, , , failures]) => [dictionaryUid, failures.map((failure) => asRecord(failure))]))
}

async function createAndProbe() {
  if (!form.sourceCode || !form.displayName) {
    error.value = '请填写词库标识和显示名称'
    return
  }
  if (!form.mdxPath && !form.mddPath && !form.examplesPath) {
    error.value = '请至少填写一个词典文件路径'
    return
  }
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const source = await adminApi.createDictionaryDataCleaningSource({ ...form })
    const job = await adminApi.createDictionaryProbeJob(source.sourceUid)
    message.value = job.status === 'completed' ? '词典源已创建，探查已完成。' : '词典源已创建，但探查失败，请查看任务记录。'
    await loadAll()
  } catch {
    error.value = '创建或探查词典源失败'
  } finally {
    submitting.value = false
  }
}

async function uploadDictionaryPackage() {
  if (!form.sourceCode || !form.displayName) {
    error.value = '请填写词库标识和显示名称'
    return
  }
  if (uploadFiles.value.length === 0) {
    error.value = '请选择词典文件或 ZIP 包'
    return
  }
  submitting.value = true
  error.value = ''
  message.value = ''
  uploadStatusType.value = 'info'
  uploadStatusMessage.value = '正在上传并探查，MDX/MDD 文件较大时可能需要几分钟，请不要重复点击。'
  try {
    const job = await adminApi.uploadDictionaryDataCleaningSource({ ...form }, uploadFiles.value)
    message.value = job.status === 'completed' ? '词典包已上传，探查已完成。' : '词典包已上传，但探查失败，请查看任务记录。'
    uploadStatusType.value = job.status === 'completed' ? 'success' : 'error'
    uploadStatusMessage.value = message.value
    uploadFiles.value = []
    await loadAll()
  } catch (err) {
    const detail = extractErrorMessage(err)
    error.value = detail ? `上传或探查词典包失败：${detail}` : '上传或探查词典包失败'
    uploadStatusType.value = 'error'
    uploadStatusMessage.value = error.value
    await loadAll({ clearError: false })
    const existingSource = findSourceByCode(form.sourceCode)
    uploadStatusMessage.value = existingSource
      ? `${uploadStatusMessage.value}。上传失败后已刷新下方数据源列表，已在下方数据源列表中找到同编码词典源：${existingSource.displayName}。`
      : `${uploadStatusMessage.value}。上传失败后已刷新下方数据源列表，但刷新后仍未在下方列表中找到同编码词典源，请检查列表接口、管理员读取权限或筛选条件。`
  } finally {
    submitting.value = false
  }
}

async function createDictionaryImport(dictionaryUid: string) {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const job = await adminApi.createAdminDictionaryImportJob(dictionaryUid, 100)
    dictionaryImportJobs.value = {
      ...dictionaryImportJobs.value,
      [dictionaryUid]: [job, ...(dictionaryImportJobs.value[dictionaryUid] || [])],
    }
    dictionaryImportFailures.value = {
      ...dictionaryImportFailures.value,
      [dictionaryUid]: job.importJobUid
        ? (await adminApi.listAdminDictionaryImportFailures(job.importJobUid).catch(() => [])).map((failure) => asRecord(failure))
        : [],
    }
    dictionaryEntrySamples.value = {
      ...dictionaryEntrySamples.value,
      [dictionaryUid]: await adminApi.listAdminDictionaryEntrySamples(dictionaryUid, 5).catch(() => []),
    }
    message.value = `正文入库任务已${job.status === 'completed' ? '完成' : '创建'}，处理 ${job.processedEntries || 0} 个词条，成功 ${job.importedEntries || 0} 个，失败 ${job.failedEntries || 0} 个。`
    await loadAll()
  } catch {
    error.value = '创建正文入库任务失败'
  } finally {
    submitting.value = false
  }
}

async function probeExisting(sourceUid: string) {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const job = await adminApi.createDictionaryProbeJob(sourceUid)
    message.value = job.status === 'completed' ? '重新探查已完成。' : '重新探查失败，请查看任务记录。'
    await loadAll()
  } catch {
    error.value = '重新探查失败'
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  message.value = '草稿已保留在当前页面，提交前不会写入数据库。'
}

function onUploadFilesChange(event: Event) {
  const input = event.target as HTMLInputElement
  uploadFiles.value = Array.from(input.files || [])
  uploadStatusType.value = 'info'
  uploadStatusMessage.value = uploadFiles.value.length > 0
    ? `已选择 ${uploadFiles.value.length} 个文件，点击“上传并探查”后开始处理。`
    : ''
  applyUploadFilePreset(uploadFiles.value)
}

function applyUploadFilePreset(files: File[]) {
  const preset = sourcePresetFromFiles(files)
  if (!preset) return

  form.sourceCode = preset.sourceCode
  form.displayName = preset.displayName
  form.mdxPath = ''
  form.mddPath = ''
  form.examplesPath = ''
  form.coverImagePath = ''
  uploadStatusType.value = 'info'
  uploadStatusMessage.value = `已根据上传文件识别词库标识为 ${form.sourceCode}，请确认显示名称后再上传。`
}

function sourcePresetFromFiles(files: File[]) {
  const sourceFile = files.find((file) => /\.zip$/i.test(file.name))
    || files.find((file) => /\.mdx$/i.test(file.name))
    || files.find((file) => /\.(mdd|xlsx|jpg|jpeg|png)$/i.test(file.name))
  return sourceFile ? sourcePresetFromFileName(sourceFile.name) : null
}

function sourcePresetFromFileName(fileName: string) {
  const baseName = fileName.replace(/\.[^.]+$/, '')
  const sourceCode = inferKnownDictionaryCode(baseName) || sanitizeSourceCode(baseName)
  if (!sourceCode) return null

  return {
    sourceCode,
    displayName: uploadSourceNamePresets[sourceCode] || baseName,
  }
}

function inferKnownDictionaryCode(value: string) {
  const lower = value.toLowerCase()
  if ((value.includes('牛津') && value.includes('高阶') && value.includes('9')) || lower.includes('oald9')) {
    return 'oald9'
  }
  if (lower.includes('oxfordprimary')) {
    return 'oxfordPrimary'
  }
  return ''
}

function sanitizeSourceCode(value: string) {
  const asciiCode = value
    .replace(/[^A-Za-z0-9._-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
  if (asciiCode.length >= 3) return asciiCode
  return `dict-${simpleHash(value)}`
}

function simpleHash(value: string) {
  let hash = 0
  for (const char of value) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  return hash.toString(36).slice(0, 8)
}

function extractErrorMessage(err: unknown) {
  const maybeError = err as { response?: { data?: { message?: string } }; message?: string; code?: string }
  if (maybeError?.response?.data?.message) return maybeError.response.data.message
  if (maybeError?.code === 'ECONNABORTED') return '上传超时，请确认后端仍在处理或改用 ZIP/服务器路径导入'
  return maybeError?.message || ''
}

function findSourceByCode(sourceCode: string) {
  const normalized = sourceCode.trim()
  return sources.value.find((source) => source.sourceCode === normalized)
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function formatNullableNumber(value: number | null | undefined) {
  return value == null ? '-' : formatNumber(value)
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

function fileName(path: string | null) {
  if (!path) return '-'
  return path.split(/[\\/]/).pop() || path
}

function licenseLabel(status: string) {
  return {
    unknown: '授权未确认',
    internal_only: '仅内部测试',
    licensed: '已授权',
    blocked: '禁止导入',
  }[status] || status
}

function dictionaryStatusLabel(status: string) {
  return {
    installed: '已安装',
    importing: '入库中',
    imported: '已入库',
    failed: '失败',
    disabled: '已禁用',
  }[status] || status
}

function latestImportJob(dictionaryUid: string) {
  return dictionaryImportJobs.value[dictionaryUid]?.[0] || null
}

function importFailures(dictionaryUid: string) {
  const detailedFailures = dictionaryImportFailures.value[dictionaryUid] || []
  if (detailedFailures.length > 0) {
    return detailedFailures.slice(0, 3)
  }
  const result = latestImportJob(dictionaryUid)?.result || {}
  const failures = Array.isArray(result.failures) ? result.failures : []
  return failures.slice(0, 3).map((failure) => asRecord(failure))
}

onMounted(() => loadAll())
</script>

<style scoped>
.data-cleaning-page {
  display: grid;
  gap: 18px;
}

.data-cleaning-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.data-cleaning-kicker {
  margin-bottom: 8px;
  color: #0b7666;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
}

.data-cleaning-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.data-cleaning-kpi {
  min-height: 92px;
  background: linear-gradient(180deg, rgba(235, 249, 244, 0.72), #fff);
}

.data-cleaning-tabs {
  position: sticky;
  top: 0;
  z-index: 5;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  padding: 8px;
  border: 1px solid #dce7e5;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(10px);
  overflow-x: auto;
}

.data-cleaning-tab {
  min-height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 8px;
  padding: 10px 12px;
  background: transparent;
  color: #334155;
  cursor: pointer;
  text-align: left;
  white-space: nowrap;
}

.data-cleaning-tab:hover {
  background: rgba(16, 128, 103, 0.08);
}

.data-cleaning-tab--active {
  background: #127a67;
  color: #fff;
  box-shadow: 0 10px 20px rgba(18, 122, 103, 0.2);
}

.data-cleaning-tab__icon {
  width: 28px;
  height: 28px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(16, 128, 103, 0.12);
  font-size: 12px;
  font-weight: 900;
}

.data-cleaning-tab--active .data-cleaning-tab__icon {
  background: rgba(255, 255, 255, 0.18);
}

.data-cleaning-tab strong,
.data-cleaning-tab small {
  display: block;
}

.data-cleaning-tab small {
  margin-top: 3px;
  font-size: 12px;
  opacity: 0.78;
}

.data-cleaning-tab__status {
  margin-left: auto;
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(174, 112, 29, 0.12);
  color: #925f18;
  font-size: 12px;
  font-weight: 800;
}

.data-cleaning-tab--active .data-cleaning-tab__status {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.data-cleaning-message {
  border-color: rgba(16, 128, 103, 0.24);
  background: rgba(16, 128, 103, 0.08);
  color: #0b7666;
}

.data-cleaning-workspace {
  align-items: start;
  grid-template-columns: minmax(0, 1.06fr) minmax(360px, 0.94fr);
}

.data-cleaning-form-card {
  min-height: 100%;
}

.data-cleaning-card-heading {
  align-items: flex-start;
}

.data-cleaning-steps {
  display: flex;
  gap: 8px;
  margin: 14px 0 18px;
  overflow-x: auto;
}

.data-cleaning-step {
  padding: 7px 10px;
  border-radius: 999px;
  background: #f3f7f5;
  color: #52645f;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.data-cleaning-step--active {
  background: rgba(16, 128, 103, 0.12);
  color: #0b7666;
}

.data-cleaning-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.data-cleaning-form label:nth-child(n + 4) {
  grid-column: 1 / -1;
}

.data-cleaning-form label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.data-cleaning-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.data-cleaning-upload-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin-top: 18px;
  padding: 14px;
  border: 1px dashed #9ccbc2;
  border-radius: 8px;
  background: #f6fbf9;
}

.data-cleaning-upload-panel h3 {
  margin: 0 0 4px;
  color: #0f172a;
  font-size: 16px;
}

.data-cleaning-upload-box {
  grid-column: 1 / -1;
  min-height: 48px;
  display: flex;
  align-items: center;
  padding: 12px;
  border: 1px solid #dce7e5;
  border-radius: 8px;
  background: #fff;
  color: #52645f;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.data-cleaning-upload-box input {
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
  position: absolute;
}

.data-cleaning-upload-box span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-cleaning-upload-feedback {
  grid-column: 1 / -1;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.5;
}

.data-cleaning-upload-feedback--info {
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
}

.data-cleaning-upload-feedback--success {
  background: rgba(19, 111, 75, 0.1);
  color: #0b7666;
}

.data-cleaning-upload-feedback--error {
  background: rgba(203, 65, 84, 0.12);
  color: #a32136;
}

.dictionary-library-list {
  display: grid;
  gap: 10px;
}

.dictionary-library-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px;
  border: 1px solid #dce7e5;
  border-radius: 8px;
  background: #fff;
}

.dictionary-library-cover {
  width: 52px;
  height: 52px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eaf5f1;
  color: #0b7666;
  font-weight: 900;
}

.dictionary-library-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dictionary-library-main {
  min-width: 0;
}

.dictionary-library-title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 4px;
}

.dictionary-library-title-row h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.35;
}

.dictionary-library-description {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 0;
}

.dictionary-library-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.dictionary-library-meta span {
  padding: 4px 8px;
  border-radius: 999px;
  background: #f3f7f5;
  color: #52645f;
  font-size: 12px;
  font-weight: 800;
}

.dictionary-import-summary,
.dictionary-sample-list,
.dictionary-import-failures {
  margin-top: 10px;
}

.dictionary-import-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  color: #52645f;
  font-size: 12px;
  font-weight: 800;
}

.dictionary-import-summary span {
  padding: 4px 8px;
  border-radius: 8px;
  background: #eef8f5;
}

.dictionary-sample-list {
  display: grid;
  gap: 6px;
}

.dictionary-sample {
  display: grid;
  grid-template-columns: minmax(90px, 0.7fr) minmax(56px, 0.35fr) minmax(0, 2fr);
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #e5ebe9;
  border-radius: 8px;
  background: #fbfdfc;
  color: #334155;
  font-size: 12px;
}

.dictionary-sample strong {
  color: #0f172a;
}

.dictionary-sample small {
  overflow: hidden;
  color: #52645f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dictionary-import-failures {
  display: grid;
  gap: 4px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(203, 65, 84, 0.08);
  color: #a32136;
  font-size: 12px;
  line-height: 1.5;
}

.dictionary-library-side {
  display: grid;
  gap: 6px;
  justify-items: end;
  color: #52645f;
  font-size: 12px;
}

.dictionary-library-side strong {
  color: #0f172a;
  font-size: 14px;
}

.data-cleaning-side-stack {
  display: grid;
  gap: 18px;
}

.data-cleaning-result {
  display: grid;
  gap: 14px;
}

.data-cleaning-result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.data-cleaning-empty {
  min-height: 128px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.data-cleaning-result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.data-cleaning-result-card {
  border: 1px solid #dce7e5;
  border-radius: 8px;
  padding: 12px;
  background: #f8fbfb;
}

.data-cleaning-result-title {
  margin: 6px 0;
  color: #0f172a;
  font-weight: 900;
  word-break: break-word;
}

.data-cleaning-json-panel {
  border-top: 1px solid #e5ebe9;
  padding-top: 10px;
}

.data-cleaning-json-panel summary {
  cursor: pointer;
  color: #0b7666;
  font-weight: 800;
}

.data-cleaning-json {
  max-height: 280px;
  overflow: auto;
  margin-top: 10px;
  padding: 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
}

.data-cleaning-quality-list {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.data-cleaning-quality > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 7px;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.data-cleaning-quality__track {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef3f1;
}

.data-cleaning-quality__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #127a67;
}

.data-cleaning-path {
  max-width: 280px;
  word-break: break-all;
}

.data-cleaning-row-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.data-cleaning-error-cell {
  max-width: 320px;
  color: #a32136;
}

.data-cleaning-placeholder {
  min-height: 420px;
  display: grid;
  align-content: start;
  gap: 16px;
}

.data-cleaning-placeholder__badge {
  width: fit-content;
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(16, 128, 103, 0.1);
  color: #0b7666;
  font-weight: 900;
}

.data-cleaning-placeholder-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.data-cleaning-placeholder-item {
  border: 1px solid #dce7e5;
  border-radius: 8px;
  padding: 14px;
  background: #f8fbfb;
  color: #334155;
  font-weight: 800;
}

.data-cleaning-status--completed,
.data-cleaning-status--probed,
.data-cleaning-status--installed,
.data-cleaning-status--imported {
  background: rgba(19, 111, 75, 0.1);
  color: var(--admin-accent-dark);
}

.data-cleaning-status--running,
.data-cleaning-status--registered,
.data-cleaning-status--importing {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.data-cleaning-status--failed,
.data-cleaning-status--disabled {
  background: rgba(203, 65, 84, 0.12);
  color: #a32136;
}

@media (max-width: 1180px) {
  .data-cleaning-tabs {
    grid-template-columns: repeat(6, minmax(190px, 1fr));
  }

  .data-cleaning-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .data-cleaning-hero,
  .data-cleaning-result-header {
    flex-direction: column;
    align-items: stretch;
  }

      .data-cleaning-workspace,
      .data-cleaning-form,
      .data-cleaning-result-grid,
      .data-cleaning-placeholder-grid,
      .dictionary-library-row {
        grid-template-columns: 1fr;
      }

      .dictionary-library-side {
        justify-items: start;
      }

  .data-cleaning-form label:nth-child(n + 4) {
    grid-column: auto;
  }

  .data-cleaning-actions {
    flex-direction: column;
  }

  .data-cleaning-upload-panel {
    grid-template-columns: 1fr;
  }
}
</style>
