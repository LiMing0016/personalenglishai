<template>
  <div class="translation-hub-page">
    <header class="hub-header">
      <div>
        <p class="hub-kicker">PEAI TRANSLATION HUB</p>
        <h1>翻译中心</h1>
        <p class="hub-subtitle">找到素材，继续阅读，整理你的双语学习笔记</p>
      </div>

      <button type="button" class="create-button" @click="openCreatePanel('immersive')">
        <span class="button-icon" aria-hidden="true">+</span>
        新建翻译
        <span class="button-caret" aria-hidden="true">⌄</span>
      </button>
    </header>

    <main class="hub-layout">
      <div class="hub-main">
        <section class="hub-section" aria-labelledby="quick-actions-title">
          <div class="section-heading">
            <div>
              <p class="section-label">START</p>
              <h2 id="quick-actions-title">学习入口</h2>
            </div>
            <button type="button" class="text-button" @click="scrollToTranslations">查看我的翻译</button>
          </div>

          <div class="quick-action-grid">
            <button
              v-for="action in hubQuickActions"
              :key="action.id"
              type="button"
              class="quick-action-card"
              :class="`quick-action-card--${action.tone}`"
              @click="handleQuickAction(action.target)">
              <span class="quick-action-meta">{{ action.meta }}</span>
              <strong>{{ action.title }}</strong>
              <span>{{ action.description }}</span>
              <em>{{ action.actionLabel }}</em>
            </button>
          </div>
        </section>

        <section class="hub-section" aria-labelledby="library-title">
          <div class="section-heading">
            <div>
              <p class="section-label">MATERIALS</p>
              <h2 id="library-title">素材库</h2>
            </div>
            <button type="button" class="text-button" @click="showPlaceholderAction('全部素材')">全部素材</button>
          </div>

          <div class="material-grid">
            <button
              v-for="category in materialCategories"
              :key="category.id"
              type="button"
              class="material-card"
              :class="`material-card--${category.tone}`"
              @click="selectCategory(category.id, category.title)">
              <span class="material-icon" aria-hidden="true">{{ category.icon }}</span>
              <span class="material-title">{{ category.title }}</span>
              <span class="material-desc">{{ category.description }}</span>
              <span class="material-ability">{{ category.ability }}</span>
              <span class="material-meta">
                难度：{{ category.difficulty }} · {{ category.readingTime }}
              </span>
              <strong>{{ category.countLabel }}</strong>
            </button>
          </div>
        </section>

        <section ref="myTranslationsSection" class="hub-section" aria-labelledby="translations-title">
          <div class="section-heading section-heading--table">
            <div>
              <p class="section-label">MY LIBRARY</p>
              <h2 id="translations-title">我的翻译</h2>
            </div>
            <div class="table-tools">
              <label class="search-field">
                <span class="sr-only">搜索翻译记录</span>
                <input
                  v-model="translationQuery"
                  type="search"
                  placeholder="搜索标题、来源或内容"
                />
              </label>
              <button type="button" class="filter-button" @click="showPlaceholderAction('高级筛选')">
                筛选
              </button>
            </div>
          </div>

          <div class="filter-tabs" aria-label="我的翻译筛选">
            <button
              v-for="item in filterTabs"
              :key="item.key"
              type="button"
              :class="{ active: activeFilter === item.key }"
              @click="activeFilter = item.key">
              {{ item.label }}
            </button>
          </div>

          <div v-if="filteredTranslations.length > 0" class="translation-table">
            <div class="translation-row translation-row--head">
              <span>标题</span>
              <span>来源类型</span>
              <span>翻译模式</span>
              <span>更新时间</span>
              <span>笔记</span>
              <span>进度</span>
              <span>状态</span>
              <span>操作</span>
            </div>

            <article v-for="item in filteredTranslations" :key="item.id" class="translation-row">
              <div class="record-title">
                <span class="record-file" :class="`record-file--${item.sourceType}`">
                  {{ sourceTypeLabels[item.sourceType] }}
                </span>
                <div>
                  <h3>{{ item.title }}</h3>
                  <p>{{ item.subtitle }}</p>
                </div>
              </div>
              <span>{{ item.sourceLabel }}</span>
              <span>{{ modeLabels[item.mode] }}</span>
              <span>{{ item.updatedAt }}</span>
              <span>{{ item.noteCount }} 条笔记</span>
              <div class="mini-progress">
                <strong>{{ item.progress }}%</strong>
                <span aria-hidden="true"><i :style="{ width: `${item.progress}%` }"></i></span>
              </div>
              <span class="status-pill" :class="`status-pill--${item.status}`">
                {{ statusLabels[item.status] }}
              </span>
              <div class="row-actions">
                <button type="button" @click="continueReading(item)">
                  {{ item.status === 'reading' ? '继续阅读' : '查看笔记' }}
                </button>
                <button
                  type="button"
                  class="icon-button"
                  :aria-label="`打开 ${item.title} 操作菜单`"
                  @click="showPlaceholderAction(`${item.title} 操作菜单`)">
                  ⋮
                </button>
              </div>
            </article>
          </div>

          <div v-else class="empty-block empty-block--compact">
            <h3>没有找到匹配的翻译记录</h3>
            <p>换一个关键词，或切换筛选条件再试。</p>
          </div>
        </section>
      </div>

      <aside class="hub-side" aria-label="翻译学习摘要">
        <section class="side-panel">
          <div class="side-heading">
            <h2>今日推荐</h2>
            <button type="button" @click="showPlaceholderAction('换一换推荐')">换一换</button>
          </div>

          <div class="recommend-list">
            <article v-for="item in todayRecommendations" :key="item.id" class="recommend-item">
              <div class="recommend-cover" :class="`recommend-cover--${item.tone}`">
                {{ item.coverLabel }}
              </div>
              <div>
                <p>{{ item.source }}</p>
                <h3>{{ item.title }}</h3>
                <span>{{ item.meta }}</span>
              </div>
              <button
                type="button"
                class="bookmark-button"
                :aria-label="`收藏 ${item.title}`"
                @click="showPlaceholderAction(`收藏 ${item.title}`)">
                □
              </button>
            </article>
          </div>
        </section>

        <section class="side-panel">
          <div class="side-heading">
            <h2>我的笔记摘要</h2>
            <button type="button" @click="showPlaceholderAction('本周统计')">本周</button>
          </div>
          <div class="stats-grid">
            <div v-for="stat in noteStats" :key="stat.id" class="stat-card">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </div>
          </div>
          <p class="streak-line">学习连续天数：7 天</p>
        </section>

        <section class="side-panel">
          <div class="side-heading">
            <h2>最近笔记</h2>
            <button type="button" @click="showPlaceholderAction('全部笔记')">查看全部</button>
          </div>
          <div class="note-list">
            <article v-for="note in recentNotes" :key="note.id" class="note-item">
              <h3>{{ note.title }}</h3>
              <p>{{ note.source }}</p>
              <span>{{ note.updatedAt }}</span>
            </article>
          </div>
        </section>

        <section class="side-panel starter-panel">
          <div>
            <h2>不知道怎么开始？</h2>
            <p>试试新建翻译，上传一篇文章，或从素材库挑选感兴趣的内容。</p>
          </div>
          <button type="button" class="starter-action" @click="openCreatePanel('immersive')">
            新建翻译
          </button>
        </section>
      </aside>
    </main>

    <div v-if="createPanelOpen" class="modal-backdrop" @click.self="closeCreatePanel">
      <section
        class="create-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-translation-title">
        <div class="create-panel__header">
          <div>
            <p class="section-label">NEW TRANSLATION</p>
            <h2 id="create-translation-title">新建翻译</h2>
          </div>
          <button type="button" class="icon-button" aria-label="关闭新建翻译" @click="closeCreatePanel">×</button>
        </div>

        <div class="mode-choice" aria-label="选择翻译模式">
          <button
            type="button"
            :class="{ active: createMode === 'immersive' }"
            @click="createMode = 'immersive'">
            <strong>沉浸阅读</strong>
            <span>适合文章、PDF、外刊和学习材料</span>
          </button>
          <button
            type="button"
            :class="{ active: createMode === 'exam' }"
            @click="createMode = 'exam'">
            <strong>考试翻译</strong>
            <span>按当前学段拆解题干和阅读材料</span>
          </button>
        </div>

        <button type="button" class="upload-zone" @click="triggerFilePicker">
          <span class="upload-icon" aria-hidden="true">↑</span>
          <strong>上传 PDF / DOCX / TXT / MD</strong>
          <span>{{ selectedFileName || '拖拽文件到这里，或点击上传' }}</span>
        </button>
        <input
          ref="fileInput"
          class="hidden-input"
          type="file"
          accept=".pdf,.docx,.txt,.md"
          @change="handleFileChange"
        />

        <div v-if="selectedFileIsPdf" class="parse-mode-choice" aria-label="PDF 解析模型">
          <button
            type="button"
            class="active">
            <strong>本地 PaddleOCR 首批 10 页解析</strong>
            <span>先生成可阅读材料，剩余页面进入工作台后后台继续解析。</span>
          </button>
          <button
            type="button"
            disabled>
            <strong>PPStructureV3 高质量解析</strong>
            <span>结构化解析耗时较长，稳定后再开放。</span>
          </button>
        </div>

        <label class="paste-box">
          <span>粘贴文本</span>
          <textarea
            v-model="pastedText"
            rows="5"
            placeholder="把要翻译的文章、句子或考试材料粘贴到这里"
          />
        </label>

        <div class="create-actions">
          <button type="button" class="secondary-action" @click="closeCreatePanel">取消</button>
          <button type="button" class="create-submit" :disabled="!canStartCreate" @click="startTranslation">
            {{ isCreating ? '正在解析前 10 页...' : createMode === 'exam' ? '开始考试翻译' : '开始沉浸式翻译' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { importTranslationDocument } from '@/api/translation'
import { showToast } from '@/utils/toast'
import {
  filterTranslations,
  hubQuickActions,
  materialCategories,
  myTranslations,
  noteStats,
  recentNotes,
  todayRecommendations,
  type TranslationFilter,
  type TranslationMode,
  type TranslationRecord,
  type TranslationSourceType,
  type TranslationStatus,
} from './translationHubData'
import {
  createTranslationWorkspaceDraft,
  createTranslationWorkspaceDraftFromParsedDocument,
  listTranslationWorkspaceDrafts,
  saveTranslationWorkspaceDraft,
  validateNewTranslationInput,
} from './translationWorkspaceData'

const router = useRouter()

const filterTabs: Array<{ key: TranslationFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'reading', label: '阅读中' },
  { key: 'completed', label: '已完成' },
  { key: 'noted', label: '有笔记' },
  { key: 'exam', label: '考试模式' },
]

const modeLabels: Record<TranslationMode, string> = {
  immersive: '沉浸阅读',
  exam: '考试翻译',
}

const statusLabels: Record<TranslationStatus, string> = {
  reading: '阅读中',
  completed: '已完成',
}

const sourceTypeLabels: Record<TranslationSourceType, string> = {
  pdf: 'PDF',
  web: 'WEB',
  text: 'TXT',
  library: 'LIB',
}

const createPanelOpen = ref(false)
const createMode = ref<TranslationMode>('immersive')
const pastedText = ref('')
const selectedFileName = ref('')
const selectedFile = ref<File | null>(null)
const isCreating = ref(false)
const createdTranslations = ref<TranslationRecord[]>([])
const activeFilter = ref<TranslationFilter>('all')
const translationQuery = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const myTranslationsSection = ref<HTMLElement | null>(null)

const createInput = computed(() => ({
  mode: createMode.value,
  pastedText: pastedText.value,
  selectedFileName: selectedFileName.value,
}))

const canStartCreate = computed(() => {
  return !isCreating.value && validateNewTranslationInput(createInput.value).valid
})

const selectedFileIsPdf = computed(() => selectedFileName.value.toLowerCase().endsWith('.pdf'))
const selectedParseMode = computed((): 'standard' => 'standard')

const translationRows = computed(() => {
  return [...createdTranslations.value, ...myTranslations]
})

const filteredTranslations = computed(() => {
  return filterTranslations(translationRows.value, {
    filter: activeFilter.value,
    query: translationQuery.value,
  })
})

onMounted(() => {
  refreshCreatedTranslations()
})

function openCreatePanel(mode: TranslationMode) {
  createMode.value = mode
  createPanelOpen.value = true
}

function closeCreatePanel() {
  createPanelOpen.value = false
}

function triggerFilePicker() {
  fileInput.value?.click()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  selectedFile.value = file
  selectedFileName.value = file.name
  showToast(`已选择文件：${file.name}`, 'success')
}

async function startTranslation() {
  const validation = validateNewTranslationInput(createInput.value)
  if (!validation.valid) {
    showToast(validation.message ?? '请上传文件或粘贴至少 10 个字符', 'info')
    return
  }

  isCreating.value = true
  try {
    const draft = await buildDraftFromCreateForm()
    if (typeof window !== 'undefined') {
      saveTranslationWorkspaceDraft(window.localStorage, draft)
      refreshCreatedTranslations()
    }

    const warning = draft.warnings?.[0]
    showToast(warning ? `已创建，但需要处理：${warning}` : `已创建：${draft.title}`, warning ? 'info' : 'success')
    resetCreateForm()
    closeCreatePanel()
    void router.push({ name: 'TranslationWorkspace', params: { id: draft.id } })
  } catch (error) {
    const message = error instanceof Error ? error.message : '材料解析失败，请稍后重试'
    showToast(message, 'error')
  } finally {
    isCreating.value = false
  }
}

async function buildDraftFromCreateForm() {
  const file = selectedFile.value
  if (!file) {
    return createTranslationWorkspaceDraft(createInput.value)
  }

  const parsedDocument = await importTranslationDocument(
    file,
    createMode.value,
    selectedParseMode.value,
    'paddle-ocr',
  )
  const fallbackPdfPreviewUrl = file.name.toLowerCase().endsWith('.pdf') && typeof URL !== 'undefined'
    ? URL.createObjectURL(file)
    : undefined
  const pdfPreviewUrl = parsedDocument.fileUrl || fallbackPdfPreviewUrl
  return createTranslationWorkspaceDraftFromParsedDocument({ mode: createMode.value, pdfPreviewUrl }, parsedDocument)
}

function continueReading(item: TranslationRecord) {
  if (item.id.startsWith('translation-')) {
    void router.push({ name: 'TranslationWorkspace', params: { id: item.id } })
    return
  }
  const title = item.title
  showToast(`继续阅读：${title}`, 'info')
}

function showPlaceholderAction(label: string) {
  showToast(`${label} 即将开放`, 'info')
}

function handleQuickAction(target: 'create' | 'materials' | 'notes') {
  if (target === 'create') {
    openCreatePanel('immersive')
    return
  }
  if (target === 'materials') {
    showToast('可以从素材库选择一个分类开始', 'info')
    return
  }
  showToast('最近笔记整理即将开放', 'info')
}

function selectCategory(categoryId: string, title: string) {
  if (categoryId === 'user-imports') {
    openCreatePanel('immersive')
    return
  }
  showToast(`正在准备 ${title} 素材列表`, 'info')
}

function scrollToTranslations() {
  myTranslationsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function refreshCreatedTranslations() {
  if (typeof window === 'undefined') return
  createdTranslations.value = listTranslationWorkspaceDrafts(window.localStorage)
}

function resetCreateForm() {
  pastedText.value = ''
  selectedFileName.value = ''
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

</script>

<style scoped>
.translation-hub-page {
  min-height: 100vh;
  padding: 34px 32px 56px;
  background: #f6f8fb;
  color: #102033;
}

.hub-header,
.hub-layout {
  width: min(1500px, 100%);
  margin: 0 auto;
}

.hub-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.hub-kicker,
.section-label {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.hub-header h1 {
  margin: 8px 0 8px;
  color: #111827;
  font-size: 36px;
  line-height: 1.1;
  font-weight: 900;
}

.hub-subtitle {
  margin: 0;
  color: #667085;
  font-size: 16px;
  line-height: 1.6;
  font-weight: 700;
}

button {
  font: inherit;
}

.create-button,
.small-primary,
.create-submit {
  border: 0;
  background: #0f8f89;
  color: #ffffff;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(15, 143, 137, 0.18);
}

.create-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 18px;
  border-radius: 8px;
  white-space: nowrap;
}

.button-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 2px solid rgba(255, 255, 255, 0.82);
  border-radius: 50%;
  line-height: 1;
}

.button-caret {
  font-size: 18px;
  opacity: 0.86;
}

.hub-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 22px;
}

.hub-main,
.hub-side {
  min-width: 0;
}

.hub-main {
  display: grid;
  gap: 24px;
}

.hub-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hub-section,
.side-panel {
  min-width: 0;
}

.section-heading,
.side-heading,
.session-title-row,
.session-footer,
.progress-row,
.create-panel__header,
.create-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-heading {
  margin-bottom: 14px;
}

.section-heading h2,
.side-heading h2,
.create-panel h2 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 22px;
  line-height: 1.25;
  font-weight: 900;
}

.text-button,
.side-heading button,
.filter-button,
.secondary-action {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #526071;
  font-weight: 800;
  cursor: pointer;
}

.text-button,
.side-heading button {
  padding: 8px 12px;
}

.quick-action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.quick-action-card,
.material-card,
.side-panel,
.translation-table,
.empty-block {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.quick-action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  min-height: 178px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
}

.quick-action-card--primary {
  background: #ecfdf5;
}

.quick-action-card--mint {
  background: #f0fdfa;
}

.quick-action-card--amber {
  background: #fffbeb;
}

.quick-action-meta {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(15, 143, 137, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.quick-action-card strong {
  color: #111827;
  font-size: 20px;
  line-height: 1.25;
}

.quick-action-card span:not(.quick-action-meta) {
  color: #526071;
  font-size: 14px;
  line-height: 1.55;
}

.quick-action-card em {
  margin-top: auto;
  color: #0f8f89;
  font-style: normal;
  font-weight: 900;
}

.recommend-cover,
.record-file {
  display: grid;
  place-items: center;
  border-radius: 8px;
  font-weight: 900;
}

.record-title h3,
.recommend-item h3,
.note-item h3 {
  margin: 0;
  color: #1f2937;
  font-size: 15px;
  line-height: 1.35;
  font-weight: 900;
}

.record-title p,
.material-desc,
.recommend-item p,
.note-item p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.45;
}

.icon-button,
.bookmark-button {
  flex: 0 0 auto;
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #667085;
  cursor: pointer;
}

.mini-progress span {
  overflow: hidden;
  display: block;
  height: 6px;
  border-radius: 999px;
  background: #e9eef5;
}

.mini-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #0f8f89;
}

.small-primary {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  white-space: nowrap;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.material-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-height: 210px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
}

.material-card--mint { background: #f0fdfa; }
.material-card--blue { background: #eff6ff; }
.material-card--amber { background: #fffbeb; }
.material-card--violet { background: #f5f3ff; }
.material-card--teal { background: #ecfeff; }
.material-card--neutral { background: #f8fafc; }

.material-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 2px solid currentColor;
  border-radius: 8px;
  color: #0f8f89;
  font-size: 12px;
  font-weight: 900;
}

.material-title {
  color: #111827;
  font-size: 18px;
  font-weight: 900;
}

.material-desc {
  min-height: 38px;
}

.material-ability,
.material-meta {
  color: #526071;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 800;
}

.material-card strong {
  margin-top: auto;
  color: #667085;
  font-size: 13px;
}

.section-heading--table {
  align-items: flex-end;
}

.table-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: min(520px, 100%);
}

.search-field {
  flex: 1;
}

.search-field input {
  width: 100%;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #1f2937;
  font: inherit;
  box-sizing: border-box;
}

.filter-button {
  min-height: 42px;
  padding: 0 14px;
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-tabs button {
  min-height: 34px;
  padding: 0 13px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #526071;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.filter-tabs button.active {
  border-color: #0f8f89;
  background: #0f8f89;
  color: #ffffff;
}

.translation-table {
  overflow: hidden;
  overflow-x: auto;
}

.translation-row {
  display: grid;
  grid-template-columns: minmax(320px, 2fr) 110px 112px 112px 96px 120px 92px 120px;
  align-items: center;
  gap: 14px;
  min-width: 1080px;
  min-height: 68px;
  padding: 12px 16px;
  border-top: 1px solid #edf1f6;
  color: #526071;
  font-size: 13px;
  font-weight: 700;
}

.translation-row--head {
  min-height: 44px;
  border-top: 0;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.translation-row > span,
.status-pill,
.row-actions button:first-child {
  white-space: nowrap;
}

.record-title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.record-title > div {
  min-width: 0;
}

.record-title h3,
.record-title p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-file {
  width: 40px;
  height: 40px;
  color: #2563eb;
  background: #dbeafe;
  font-size: 11px;
}

.record-file--web { color: #0f766e; background: #ccfbf1; }
.record-file--text { color: #c2410c; background: #ffedd5; }
.record-file--library { color: #7c3aed; background: #ede9fe; }

.mini-progress {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.mini-progress strong {
  color: #344054;
}

.mini-progress span {
  height: 5px;
}

.status-pill {
  display: inline-flex;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  line-height: 26px;
}

.status-pill--reading {
  background: #e7f7f3;
  color: #0f766e;
}

.status-pill--completed {
  background: #eff6ff;
  color: #2563eb;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-actions button:first-child {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #344054;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.side-panel {
  padding: 18px;
}

.side-heading {
  margin-bottom: 14px;
}

.side-heading h2 {
  font-size: 19px;
}

.recommend-list,
.note-list {
  display: grid;
  gap: 14px;
}

.recommend-item {
  position: relative;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) 28px;
  gap: 12px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid #edf1f6;
}

.recommend-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.recommend-cover {
  width: 54px;
  height: 54px;
  color: #ffffff;
}

.recommend-cover--red { background: #dc2626; }
.recommend-cover--sand { background: #fed7aa; color: #7c2d12; }
.recommend-cover--black { background: #111827; }

.recommend-item h3 {
  margin-top: 3px;
}

.recommend-item span,
.note-item span {
  display: inline-block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.bookmark-button {
  border: 1px solid #d9e2ec;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid #edf1f6;
  border-radius: 8px;
  overflow: hidden;
}

.stat-card {
  display: grid;
  gap: 6px;
  min-height: 72px;
  place-items: center;
  padding: 10px;
  border-right: 1px solid #edf1f6;
  border-bottom: 1px solid #edf1f6;
  text-align: center;
}

.stat-card:nth-child(2n) {
  border-right: 0;
}

.stat-card:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.stat-card span {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.stat-card strong {
  color: #111827;
  font-size: 20px;
  line-height: 1.1;
  font-weight: 900;
}

.streak-line {
  margin: 14px 0 0;
  color: #c2410c;
  font-size: 13px;
  font-weight: 900;
}

.note-item {
  padding-bottom: 12px;
  border-bottom: 1px solid #edf1f6;
}

.note-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.starter-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
}

.starter-panel p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.6;
}

.starter-action {
  width: 76px;
  height: 76px;
  border: 0;
  border-radius: 50%;
  background: #d9f7f3;
  color: #0f8f89;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.empty-block {
  display: grid;
  gap: 10px;
  place-items: center;
  min-height: 180px;
  padding: 26px;
  text-align: center;
}

.empty-block--compact {
  min-height: 150px;
}

.empty-block h3 {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
}

.empty-block p {
  margin: 0;
  color: #667085;
  line-height: 1.6;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.create-panel {
  width: min(560px, 100%);
  max-height: min(720px, calc(100vh - 48px));
  overflow: auto;
  padding: 22px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.24);
}

.mode-choice {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 20px 0 14px;
}

.mode-choice button {
  display: grid;
  gap: 6px;
  min-height: 92px;
  padding: 14px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #526071;
  text-align: left;
  cursor: pointer;
}

.mode-choice button.active {
  border-color: #0f8f89;
  background: #ecfdf5;
  color: #0f766e;
}

.mode-choice strong {
  color: #111827;
}

.mode-choice span {
  font-size: 13px;
  line-height: 1.45;
}

.upload-zone {
  display: grid;
  place-items: center;
  gap: 8px;
  width: 100%;
  min-height: 148px;
  margin: 0 0 14px;
  padding: 18px;
  border: 1px dashed #b9c4d0;
  border-radius: 8px;
  background: #f8fafc;
  color: #526071;
  cursor: pointer;
}

.upload-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 2px solid #8aa0b6;
  border-radius: 50%;
  color: #526071;
  font-size: 22px;
  font-weight: 900;
}

.upload-zone strong {
  color: #1f2937;
  font-size: 17px;
}

.hidden-input {
  display: none;
}

.parse-mode-choice {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: -4px 0 14px;
}

.parse-mode-choice button {
  display: grid;
  gap: 4px;
  min-height: 72px;
  padding: 12px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #526071;
  text-align: left;
  cursor: pointer;
}

.parse-mode-choice button.active {
  border-color: #0f8f89;
  background: #f0fdfa;
}

.parse-mode-choice button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.parse-mode-choice strong {
  color: #111827;
  font-size: 14px;
}

.parse-mode-choice span {
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.paste-box {
  display: grid;
  gap: 8px;
  color: #344054;
  font-weight: 900;
}

.paste-box textarea {
  width: 100%;
  resize: vertical;
  min-height: 128px;
  padding: 12px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  color: #1f2937;
  font: inherit;
  line-height: 1.6;
  box-sizing: border-box;
}

.create-actions {
  margin-top: 18px;
}

.secondary-action,
.create-submit {
  min-height: 42px;
  padding: 0 16px;
}

.create-submit {
  border-radius: 8px;
}

.create-submit:disabled {
  background: #9ca3af;
  box-shadow: none;
  cursor: not-allowed;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

button:hover:not(:disabled),
.material-card:hover {
  transform: translateY(-1px);
}

button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgba(20, 184, 166, 0.24);
  outline-offset: 2px;
}

@media (max-width: 1320px) {
  .material-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .hub-layout {
    grid-template-columns: 1fr;
  }

  .hub-side {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .translation-row {
    min-width: 0;
    grid-template-columns: 1fr;
    gap: 8px;
    align-items: start;
  }

  .translation-row--head {
    display: none;
  }

  .mini-progress {
    max-width: 220px;
  }
}

@media (max-width: 760px) {
  .translation-hub-page {
    padding: 22px 14px 40px;
  }

  .hub-header,
  .section-heading,
  .section-heading--table {
    align-items: stretch;
    flex-direction: column;
  }

  .create-button {
    justify-content: center;
    width: 100%;
  }

  .table-tools,
  .create-actions,
  .starter-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .material-grid,
  .quick-action-grid,
  .hub-side,
  .mode-choice {
    grid-template-columns: 1fr;
  }

  .parse-mode-choice {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .stat-card,
  .stat-card:nth-child(2n),
  .stat-card:nth-last-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid #edf1f6;
  }

  .stat-card:last-child {
    border-bottom: 0;
  }

  .starter-action {
    width: 100%;
    height: 46px;
    border-radius: 8px;
  }
}
</style>
