<template>
  <div class="assets-section">
    <div v-if="!selectedDocId" class="assets-list-view">
      <div class="learning-assets-heading">
        <div>
          <p class="section-eyebrow">学习资产</p>
          <h2 class="section-title">把每次练习沉淀为可复用能力</h2>
          <p>个人中心统一承接写作档案、词汇卡片和翻译精读，不再只围绕作文组织。</p>
        </div>
      </div>

      <div class="asset-categories">
        <div class="asset-category active">
          <span class="category-icon category-icon--writing"><PenLine :size="20" :stroke-width="1.8" /></span>
          <div>
            <strong>作文档案</strong>
            <small>{{ total }} 篇已归档作文</small>
          </div>
        </div>
        <RouterLink class="asset-category" to="/app/vocabulary">
          <span class="category-icon category-icon--vocabulary"><BookMarked :size="20" :stroke-width="1.8" /></span>
          <div>
            <strong>词汇卡片</strong>
            <small>查看与复习已沉淀词汇</small>
          </div>
        </RouterLink>
        <RouterLink class="asset-category" to="/app/translation">
          <span class="category-icon category-icon--translation"><Languages :size="20" :stroke-width="1.8" /></span>
          <div>
            <strong>翻译精读</strong>
            <small>继续处理文档与双语内容</small>
          </div>
        </RouterLink>
      </div>

      <div class="assets-heading">
        <div>
          <h3>作文档案</h3>
          <p>集中查看已归档作文，继续修改、复盘和沉淀高分表达。</p>
        </div>
        <span>{{ total }} 篇</span>
      </div>

      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="items.length === 0" class="empty-state">
        <h3>还没有归档作文</h3>
        <p>完成写作后将有价值的作品归档，就可以在这里继续复盘和编辑。</p>
        <button type="button" class="asset-primary" @click="router.push('/app/writing')">开始写作</button>
      </div>

      <div v-else class="asset-list">
        <article v-for="item in items" :key="item.docId" class="asset-card">
          <div class="asset-card-main">
            <div class="asset-tags">
              <span>{{ item.taskPrompt ? '考试' : '自由' }}</span>
              <span class="asset-archived">已归档</span>
            </div>
            <h3>{{ item.title || '未命名作文' }}</h3>
            <p>{{ promptSummary(item) }}</p>
            <div class="asset-metrics">
              <span><em>最新分数</em><strong>{{ item.latestScore != null ? `${item.latestScore} 分` : '未评分' }}</strong></span>
              <span><em>评分次数</em><strong>{{ item.submitCount || 0 }}</strong></span>
              <span><em>更新时间</em><strong>{{ formatDate(item.updatedAt) }}</strong></span>
            </div>
          </div>
          <div class="asset-actions">
            <button type="button" class="asset-primary" @click="openAsset(item)">查看档案</button>
            <button type="button" class="asset-secondary" @click="openEditor(item)">编辑</button>
            <button type="button" class="asset-secondary" @click="restoreAsset(item)">取消归档</button>
          </div>
        </article>
      </div>
    </div>

    <div v-else class="asset-detail-view">
      <div v-if="detailLoading" class="loading-state">加载中...</div>
      <div v-else-if="detailError" class="empty-state">{{ detailError }}</div>
      <div v-else-if="detail" class="asset-detail">
        <header class="detail-hero">
          <div class="detail-hero-top">
            <button type="button" class="back-link" @click="closeAsset">返回资产库</button>
            <div class="detail-status">
              <span class="status-chip status-archived">已归档</span>
              <span v-if="detail.stale" class="status-chip status-stale">需刷新</span>
            </div>
          </div>
          <div class="detail-hero-main">
            <div>
              <h2 class="detail-title">{{ detail.title || '作文档案' }}</h2>
              <p v-if="detail.generatedAt">档案生成于 {{ formatDateTime(detail.generatedAt) }}</p>
            </div>
            <div class="detail-actions">
              <button type="button" class="asset-secondary" :disabled="detailLoading" @click="refreshAssetDetail">刷新档案</button>
              <button type="button" class="asset-secondary" @click="openEditorByDocId(detail.docId)">编辑作文</button>
              <button type="button" class="asset-secondary" @click="downloadMarkdown">下载 Markdown</button>
              <button type="button" class="asset-primary" @click="copyMarkdown">复制 Markdown</button>
            </div>
          </div>
        </header>

        <div class="detail-metrics-grid">
          <span><em>最新分数</em><strong>{{ scoreText }}</strong></span>
          <span><em>评分次数</em><strong>{{ detail.submitCount || 0 }} 次</strong></span>
          <span><em>当前版本</em><strong>v{{ detail.latestRevision }}</strong></span>
          <span><em>教练会话</em><strong>{{ detail.coachConversations.length }} 个</strong></span>
        </div>

        <div v-if="detail.stale" class="stale-banner">
          作文或教练对话已有更新，可刷新档案。
        </div>

        <div class="detail-layout">
          <main class="detail-main">
            <section class="detail-section">
              <div class="section-heading">
                <span>01</span>
                <h3>题目信息</h3>
              </div>
              <p class="prompt-text">{{ detail.taskPrompt || '自由写作，无固定题目' }}</p>
            </section>

            <section class="detail-section">
              <div class="section-heading">
                <span>02</span>
                <h3>作文正文</h3>
              </div>
              <div class="essay-paper">
                <pre class="essay-content">{{ detail.content || '暂无正文' }}</pre>
              </div>
            </section>

            <section class="detail-section">
              <div class="section-heading">
                <span>03</span>
                <h3>评分记录</h3>
              </div>
              <div v-if="detail.evaluations.length === 0" class="section-empty">暂无评分记录</div>
              <div v-else class="evaluation-list">
                <div v-for="evaluation in detail.evaluations" :key="evaluation.id" class="evaluation-row">
                  <strong>{{ evaluation.overallScore != null ? `${evaluation.overallScore} 分` : '未评分' }}</strong>
                  <span>{{ evaluation.band || '未记录等级' }}</span>
                  <span>结构 {{ valueOrDash(evaluation.structureScore) }}</span>
                  <span>词汇 {{ valueOrDash(evaluation.vocabularyScore) }}</span>
                  <span>语法 {{ valueOrDash(evaluation.grammarScore) }}</span>
                  <time>{{ evaluation.createdAt ? formatDateTime(evaluation.createdAt) : '-' }}</time>
                </div>
              </div>
            </section>

            <section class="detail-section">
              <div class="section-heading">
                <span>04</span>
                <h3>写作教练对话</h3>
              </div>
              <div v-if="detail.coachConversations.length === 0" class="section-empty compact-empty">暂无写作教练对话</div>
              <div v-else class="coach-list">
                <div v-for="conversation in detail.coachConversations" :key="conversation.id" class="coach-row">
                  <div class="coach-row-main">
                    <strong>{{ conversation.title || conversation.id }}</strong>
                    <span>{{ conversation.messageCount }} 条消息</span>
                    <time>{{ conversation.updatedAt ? formatDateTime(conversation.updatedAt) : '-' }}</time>
                  </div>
                  <button type="button" class="coach-download" @click="downloadCoachConversation(conversation)">
                    下载对话 Markdown
                  </button>
                </div>
              </div>
            </section>

            <section class="detail-section learning-preview-section">
              <div class="section-heading section-heading-actions">
                <span>05</span>
                <div>
                  <h3>DeepSeek 学习资产预览</h3>
                  <p>从作文、评分和教练对话中提取可复盘的单词、表达、句子和语法点。</p>
                </div>
                <button
                  type="button"
                  class="asset-secondary"
                  :disabled="learningPreviewRefreshing"
                  @click="refreshLearningAssetPreview"
                >
                  {{ learningPreviewRefreshing ? '提取中...' : '提取学习资产' }}
                </button>
              </div>

              <div v-if="learningPreviewStatus === 'none'" class="section-empty compact-empty">
                暂未提取学习资产，点击上方按钮先看看 DeepSeek 会沉淀哪些内容。
              </div>
              <div v-else-if="learningPreviewStatus === 'failed'" class="learning-preview-failed">
                <strong>提取失败</strong>
                <span>{{ detail.learningAssetPreview.errorMessage || 'DeepSeek 暂时不可用，请稍后重试。' }}</span>
              </div>
              <div v-else>
                <div class="learning-preview-summary">
                  <strong>{{ detail.learningAssetPreview.summary || '已生成本篇作文的学习资产预览' }}</strong>
                  <span v-if="detail.learningAssetPreview.generatedAt">
                    {{ formatDateTime(detail.learningAssetPreview.generatedAt) }}
                  </span>
                </div>
                <div v-if="detail.learningAssetPreview.items.length === 0" class="section-empty compact-empty">
                  暂无高价值学习资产
                </div>
                <div v-else class="learning-asset-grid">
                  <article v-for="asset in detail.learningAssetPreview.items" :key="asset.id" class="learning-asset-card">
                    <div class="learning-asset-card-head">
                      <span>{{ assetTypeLabel(asset.assetType) }}</span>
                      <em>{{ sourceTypeLabel(asset.sourceType) }}</em>
                    </div>
                    <h4>{{ asset.recommendedText || asset.displayText }}</h4>
                    <p v-if="asset.originalText" class="learning-original">原文：{{ asset.originalText }}</p>
                    <p v-if="asset.meaningZh" class="learning-meaning">{{ asset.meaningZh }}</p>
                    <p v-if="asset.valueReasonForUser" class="learning-reason">{{ asset.valueReasonForUser }}</p>
                    <dl>
                      <div v-if="asset.howToReuse">
                        <dt>复用方式</dt>
                        <dd>{{ asset.howToReuse }}</dd>
                      </div>
                      <div v-if="asset.reviewPrompt">
                        <dt>复习提示</dt>
                        <dd>{{ asset.reviewPrompt }}</dd>
                      </div>
                      <div v-if="asset.sourceQuestion">
                        <dt>来自提问</dt>
                        <dd>{{ asset.sourceQuestion }}</dd>
                      </div>
                    </dl>
                    <div class="learning-score-row">
                      <span v-if="asset.learningValueScore != null">价值 {{ Math.round(asset.learningValueScore) }}</span>
                      <span v-if="asset.confidence != null">可信度 {{ formatPercent(asset.confidence) }}</span>
                    </div>
                  </article>
                </div>
              </div>
            </section>

            <section class="detail-section markdown-section">
              <button type="button" class="markdown-toggle" @click="markdownExpanded = !markdownExpanded">
                <span>
                  <strong>Markdown 档案</strong>
                  <em>可复制、下载或作为长期复盘材料</em>
                </span>
                <b>{{ markdownExpanded ? '收起' : '展开' }}</b>
              </button>
              <div
                v-if="markdownExpanded"
                class="markdown-preview"
                v-html="renderMarkdown(detail.markdown)"
                @click="onMarkdownClick"
              ></div>
            </section>
          </main>

          <aside class="detail-sidebar">
            <section class="sidebar-panel">
              <h3>档案状态</h3>
              <dl>
                <div>
                  <dt>归档状态</dt>
                  <dd>已归档</dd>
                </div>
                <div>
                  <dt>生成时间</dt>
                  <dd>{{ detail.generatedAt ? formatDateTime(detail.generatedAt) : '-' }}</dd>
                </div>
                <div>
                  <dt>更新提示</dt>
                  <dd>{{ detail.stale ? '可刷新' : '已同步' }}</dd>
                </div>
              </dl>
            </section>

            <section class="sidebar-panel">
              <h3>资产包含</h3>
              <ul class="asset-checklist">
                <li>作文正文</li>
                <li>题目信息</li>
                <li>评分记录</li>
                <li>教练对话</li>
                <li>学习资产预览</li>
                <li>Markdown 档案</li>
              </ul>
            </section>

            <section class="sidebar-panel sidebar-actions">
              <button type="button" class="asset-primary" @click="openEditorByDocId(detail.docId)">编辑作文</button>
              <button type="button" class="asset-secondary" @click="restoreSelectedAsset">取消归档</button>
            </section>
          </aside>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BookMarked, Languages, PenLine } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import {
  getWritingDocuments,
  getWritingDocumentAsset,
  refreshWritingDocumentAsset,
  refreshWritingDocumentLearningAssetPreview,
  getWritingDocumentAssetMarkdown,
  getWritingCoachConversationMarkdown,
  type WritingDocumentAssetCoachConversation,
  type WritingDocumentAssetResponse,
  type WritingDocumentItem,
} from '@/api/writing'
import { unarchiveDocument } from '@/api/document'
import { showToast } from '@/utils/toast'
import { copyMarkdownCodeFromClick, renderAssistantMarkdown } from '@/components/assistant/markdown'

const router = useRouter()
const loading = ref(true)
const items = ref<WritingDocumentItem[]>([])
const total = ref(0)
const selectedDocId = ref('')
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<WritingDocumentAssetResponse | null>(null)
const markdownExpanded = ref(false)
const learningPreviewRefreshing = ref(false)

const selectedItem = computed(() => items.value.find((item) => item.docId === selectedDocId.value) ?? null)
const scoreText = computed(() => {
  const score = detail.value?.latestScore
  return score == null ? '未评分' : `${score} 分`
})
const learningPreviewStatus = computed(() => detail.value?.learningAssetPreview?.status ?? 'none')

async function loadAssets() {
  loading.value = true
  try {
    const res = await getWritingDocuments(0, 50, { archived: true })
    items.value = res.items ?? []
    total.value = res.total ?? items.value.length
  } catch (e) {
    console.warn('[WritingAssetsSection] load assets failed', e)
    showToast('作文资产加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function openEditor(item: WritingDocumentItem) {
  openEditorByDocId(item.docId)
}

function openEditorByDocId(docId: string) {
  sessionStorage.setItem('peai:writing:docId', docId)
  void router.push('/app/writing/editor')
}

async function openAsset(item: WritingDocumentItem) {
  selectedDocId.value = item.docId
  await loadAssetDetail(item.docId)
}

function closeAsset() {
  selectedDocId.value = ''
  detail.value = null
  detailError.value = ''
  markdownExpanded.value = false
}

async function loadAssetDetail(docId: string) {
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getWritingDocumentAsset(docId)
    markdownExpanded.value = false
  } catch (e) {
    console.warn('[WritingAssetsSection] load asset detail failed', e)
    detailError.value = '作文档案加载失败'
  } finally {
    detailLoading.value = false
  }
}

async function refreshAssetDetail() {
  if (!selectedDocId.value) return
  detailLoading.value = true
  try {
    detail.value = await refreshWritingDocumentAsset(selectedDocId.value)
    showToast('档案已刷新', 'success')
  } catch (e) {
    console.warn('[WritingAssetsSection] refresh asset failed', e)
    showToast('刷新档案失败，请稍后重试', 'error')
  } finally {
    detailLoading.value = false
  }
}

async function refreshLearningAssetPreview() {
  if (!selectedDocId.value) return
  learningPreviewRefreshing.value = true
  try {
    detail.value = await refreshWritingDocumentLearningAssetPreview(selectedDocId.value)
    const status = detail.value.learningAssetPreview?.status
    showToast(status === 'failed' ? '学习资产提取失败，请稍后重试' : '学习资产已提取', status === 'failed' ? 'error' : 'success')
  } catch (e) {
    console.warn('[WritingAssetsSection] refresh learning asset preview failed', e)
    showToast('学习资产提取失败，请稍后重试', 'error')
  } finally {
    learningPreviewRefreshing.value = false
  }
}

async function restoreAsset(item: WritingDocumentItem) {
  try {
    await unarchiveDocument(item.docId)
    items.value = items.value.filter((doc) => doc.docId !== item.docId)
    total.value = Math.max(0, total.value - 1)
    showToast('已取消归档', 'success')
  } catch (e) {
    console.warn('[WritingAssetsSection] unarchive failed', e)
    showToast('取消归档失败，请稍后重试', 'error')
  }
}

async function restoreSelectedAsset() {
  const docId = detail.value?.docId || selectedDocId.value
  if (!docId) return
  const item = selectedItem.value ?? ({ docId } as WritingDocumentItem)
  await restoreAsset(item)
  closeAsset()
}

async function copyMarkdown() {
  const markdown = detail.value?.markdown ?? ''
  if (!markdown.trim()) return
  try {
    await navigator.clipboard.writeText(markdown)
    showToast('Markdown 已复制', 'success')
  } catch {
    showToast('复制失败，请手动选择文本', 'error')
  }
}

async function downloadMarkdown() {
  const docId = detail.value?.docId || selectedDocId.value
  if (!docId) return
  try {
    const markdown = await getWritingDocumentAssetMarkdown(docId)
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `writing-asset-${docId}.md`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('[WritingAssetsSection] download markdown failed', e)
    showToast('下载失败，请稍后重试', 'error')
  }
}

async function downloadCoachConversation(conversation: WritingDocumentAssetCoachConversation) {
  const docId = detail.value?.docId || selectedDocId.value
  if (!docId || !conversation.id) return
  try {
    const markdown = await getWritingCoachConversationMarkdown(docId, conversation.id)
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `writing-coach-${docId}-${conversation.id}.md`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('[WritingAssetsSection] download coach conversation failed', e)
    showToast('下载写作教练对话失败，请稍后重试', 'error')
  }
}

function promptSummary(item: WritingDocumentItem) {
  return item.taskPrompt?.replace(/\s+/g, ' ').trim() || '自由写作，无固定题目'
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatDateTime(dateStr: string) {
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return dateStr
  return `${formatDate(dateStr)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function valueOrDash(value: number | null | undefined) {
  return value == null ? '-' : value
}

function assetTypeLabel(type: string) {
  const labels: Record<string, string> = {
    word: '单词',
    phrase: '短语',
    sentence: '句子',
    grammar: '语法点',
    writing_strategy: '写作策略',
  }
  return labels[type] || '学习资产'
}

function sourceTypeLabel(type: string) {
  const labels: Record<string, string> = {
    user_focus: '用户提问',
    coach_feedback: '教练反馈',
    system_discovered: '系统发现',
  }
  return labels[type] || '资产来源'
}

function formatPercent(value: number) {
  const normalized = value > 1 ? value / 100 : value
  return `${Math.round(normalized * 100)}%`
}

function renderMarkdown(markdown: string) {
  return renderAssistantMarkdown(markdown)
}

function onMarkdownClick(event: MouseEvent) {
  void copyMarkdownCodeFromClick(event)
}

onMounted(loadAssets)
</script>

<style scoped>
.assets-section {
  max-width: 1120px;
}

.learning-assets-heading {
  margin-bottom: 22px;
}

.section-eyebrow {
  margin: 0 0 5px;
  color: #7a8da2;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.learning-assets-heading p:last-child {
  max-width: 720px;
  margin: 9px 0 0;
  color: #6f8297;
  font-size: 13px;
  line-height: 1.6;
}

.asset-categories {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 32px;
}

.asset-category {
  display: grid;
  min-height: 82px;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 13px;
  border: 1px solid #dfe7ef;
  border-radius: 16px;
  padding: 14px;
  background: #fff;
  color: #63768c;
  text-decoration: none;
  transition: border-color 160ms ease, transform 160ms ease;
}

.asset-category:not(.active):hover {
  border-color: #a8c8bc;
  transform: translateY(-1px);
}

.asset-category.active {
  border-color: #9fc8b9;
  background: #f1faf6;
}

.category-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 12px;
}

.category-icon--writing {
  background: #e2f4ed;
  color: #087a59;
}

.category-icon--vocabulary {
  background: #edf2fb;
  color: #41628f;
}

.category-icon--translation {
  background: #f5f0fb;
  color: #755293;
}

.asset-category strong,
.asset-category small {
  display: block;
}

.asset-category strong {
  color: #1c334c;
  font-size: 14px;
}

.asset-category small {
  overflow: hidden;
  margin-top: 4px;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assets-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.section-title {
  margin: 0;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
}

.assets-heading h3 {
  margin: 0 0 8px;
  color: #1e293b;
  font-size: 18px;
}

.assets-heading p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.assets-heading > span {
  flex: 0 0 auto;
  padding: 5px 12px;
  border-radius: 999px;
  color: #0f766e;
  background: #e8f6ef;
  font-size: 13px;
  font-weight: 700;
}

.loading-state,
.empty-state,
.section-empty {
  padding: 40px 0;
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: #1c334c;
  font-size: 17px;
}

.empty-state p {
  margin: 9px 0 18px;
  color: #73869b;
  line-height: 1.6;
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.asset-card {
  display: flex;
  gap: 18px;
  justify-content: space-between;
  padding: 18px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.asset-card-main {
  min-width: 0;
}

.asset-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.asset-tags span {
  padding: 3px 10px;
  border-radius: 999px;
  color: #64748b;
  background: #f1f5f9;
  font-size: 12px;
  font-weight: 700;
}

.asset-tags .asset-archived {
  color: #0f766e;
  background: #e8f6ef;
}

.asset-card h3 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 16px;
}

.asset-card p {
  display: -webkit-box;
  max-width: 680px;
  margin: 0 0 14px;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.asset-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.asset-metrics span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 96px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.asset-metrics em {
  color: #94a3b8;
  font-size: 11px;
  font-style: normal;
}

.asset-metrics strong {
  color: #1e293b;
  font-size: 12px;
}

.asset-actions,
.detail-actions,
.bottom-actions {
  display: flex;
  gap: 8px;
}

.asset-actions {
  flex: 0 0 110px;
  flex-direction: column;
  justify-content: center;
}

.detail-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.asset-primary,
.asset-secondary,
.back-link {
  min-height: 34px;
  border: none;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.asset-primary {
  color: #fff;
  background: #0f766e;
}

.asset-secondary,
.back-link {
  color: #0f766e;
  background: #e8f6ef;
}

.asset-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.asset-detail {
  padding-bottom: 32px;
}

.detail-hero {
  margin-bottom: 18px;
  padding: 22px;
  border: 1px solid #dbe7e4;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fffd 0%, #ffffff 100%);
}

.detail-hero-top,
.detail-hero-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-hero-top {
  margin-bottom: 16px;
}

.detail-title {
  margin: 0 0 8px;
  color: #111827;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.25;
}

.detail-hero-main p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.detail-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.status-archived {
  color: #0f766e;
  background: #dff7ee;
}

.status-stale {
  color: #92400e;
  background: #fef3c7;
}

.detail-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-metrics-grid span {
  display: flex;
  min-height: 86px;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.detail-metrics-grid em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.detail-metrics-grid strong {
  color: #111827;
  font-size: 22px;
  line-height: 1.1;
}

.stale-banner {
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #92400e;
  background: #fffbeb;
  font-size: 13px;
  font-weight: 700;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
  align-items: start;
}

.detail-main {
  min-width: 0;
}

.detail-section {
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.section-heading-actions {
  align-items: flex-start;
}

.section-heading-actions > div {
  min-width: 0;
  flex: 1 1 auto;
}

.section-heading span {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #0f766e;
  background: #e8f6ef;
  font-size: 12px;
  font-weight: 800;
}

.section-heading h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.section-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.detail-section p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.prompt-text {
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
}

.essay-paper {
  max-height: 520px;
  overflow: auto;
  padding: 24px;
  border: 1px solid #edf0f3;
  border-radius: 8px;
  background: #fcfcfb;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);
}

.essay-content {
  max-width: 72ch;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  color: #111827;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 16px;
  line-height: 1.9;
}

.evaluation-list,
.coach-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.evaluation-row,
.coach-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.coach-row {
  justify-content: space-between;
}

.coach-row-main {
  display: flex;
  min-width: 0;
  flex: 1 1 360px;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.evaluation-row strong,
.coach-row-main strong {
  color: #111827;
}

.evaluation-row time,
.coach-row-main time {
  margin-left: auto;
  color: #94a3b8;
}

.coach-download {
  flex: 0 0 auto;
  min-height: 30px;
  border: none;
  border-radius: 8px;
  padding: 0 10px;
  color: #0f766e;
  background: #e8f6ef;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.compact-empty {
  padding: 22px 0;
  border-radius: 8px;
  background: #f8fafc;
}

.learning-preview-section {
  background: linear-gradient(180deg, #ffffff 0%, #fbfefd 100%);
}

.learning-preview-summary,
.learning-preview-failed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f1f9f6;
}

.learning-preview-summary strong,
.learning-preview-failed strong {
  color: #111827;
  font-size: 14px;
}

.learning-preview-summary span,
.learning-preview-failed span {
  color: #64748b;
  font-size: 12px;
}

.learning-preview-failed {
  align-items: flex-start;
  flex-direction: column;
  background: #fff7ed;
}

.learning-preview-failed strong {
  color: #9a3412;
}

.learning-asset-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.learning-asset-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 9px;
  padding: 14px;
  border: 1px solid #dbe7e4;
  border-radius: 8px;
  background: #fff;
}

.learning-asset-card-head,
.learning-score-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.learning-asset-card-head span {
  padding: 3px 9px;
  border-radius: 999px;
  color: #0f766e;
  background: #e8f6ef;
  font-size: 12px;
  font-weight: 800;
}

.learning-asset-card-head em,
.learning-score-row span {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.learning-asset-card h4 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.learning-asset-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.learning-original {
  color: #64748b;
}

.learning-meaning {
  color: #0f766e;
  font-weight: 700;
}

.learning-reason {
  padding: 10px;
  border-radius: 8px;
  color: #334155;
  background: #f8fafc;
}

.learning-asset-card dl {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
}

.learning-asset-card dl div {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 8px;
}

.learning-asset-card dt,
.learning-asset-card dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.learning-asset-card dt {
  color: #94a3b8;
  font-weight: 800;
}

.learning-asset-card dd {
  color: #475569;
}

.markdown-section {
  padding: 0;
  overflow: hidden;
}

.markdown-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: none;
  padding: 18px;
  color: #111827;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.markdown-toggle span {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.markdown-toggle strong {
  font-size: 16px;
}

.markdown-toggle em {
  color: #64748b;
  font-size: 13px;
  font-style: normal;
  font-weight: 500;
}

.markdown-toggle b {
  flex: 0 0 auto;
  color: #0f766e;
  font-size: 13px;
}

.markdown-preview {
  max-height: 520px;
  overflow: auto;
  padding: 18px;
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
  color: #1f2937;
  line-height: 1.65;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 18px 0 10px;
  color: #111827;
}

.markdown-preview :deep(h1:first-child),
.markdown-preview :deep(h2:first-child),
.markdown-preview :deep(h3:first-child) {
  margin-top: 0;
}

.markdown-preview :deep(p) {
  margin: 0 0 12px;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 0 0 12px;
  padding-left: 20px;
}

.markdown-preview :deep(.markdown-code-block) {
  overflow: hidden;
  border-radius: 8px;
  background: #0f172a;
}

.markdown-preview :deep(.markdown-code-header) {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  color: #cbd5e1;
  background: #111827;
}

.markdown-preview :deep(.markdown-code-copy) {
  border: none;
  color: #ecfeff;
  background: transparent;
  cursor: pointer;
}

.markdown-preview :deep(pre) {
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: #e5e7eb;
}

.detail-sidebar {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-panel {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.sidebar-panel h3 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 15px;
}

.sidebar-panel dl {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.sidebar-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.sidebar-panel dl div:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.sidebar-panel dt {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.sidebar-panel dd {
  margin: 0;
  color: #111827;
  font-size: 12px;
  font-weight: 800;
  text-align: right;
}

.asset-checklist {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.asset-checklist li {
  position: relative;
  padding-left: 20px;
  color: #475569;
  font-size: 13px;
}

.asset-checklist li::before {
  position: absolute;
  left: 0;
  color: #0f766e;
  content: "✓";
  font-weight: 900;
}

.sidebar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 760px) {
  .asset-categories {
    grid-template-columns: 1fr;
  }

  .asset-card,
  .assets-heading {
    flex-direction: column;
  }

  .asset-actions,
  .detail-actions {
    flex: none;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .detail-hero-main,
  .detail-hero-top {
    flex-direction: column;
  }

  .detail-title {
    font-size: 24px;
  }

  .detail-metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-layout {
    grid-template-columns: 1fr;
  }

  .learning-asset-grid {
    grid-template-columns: 1fr;
  }

  .detail-sidebar {
    position: static;
  }

  .essay-paper {
    padding: 16px;
  }

  .evaluation-row time,
  .coach-row-main time {
    margin-left: 0;
  }
}

@media (max-width: 520px) {
  .detail-metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
