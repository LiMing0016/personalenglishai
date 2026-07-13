<template>
  <section class="card-inspector" aria-label="单词卡详情">
    <header class="card-inspector__header">
      <div>
        <p>{{ card.theme?.name || '兼容卡片' }}<template v-if="card.themeVersion"> · v{{ card.themeVersion }}</template></p>
        <h2>{{ card.displayTerm }}</h2>
        <span :class="`card-inspector__status card-inspector__status--${card.status}`">{{ statusLabel(card.status) }}</span>
      </div>
      <button type="button" class="card-inspector__back" @click="emit('back')">返回单词库</button>
    </header>

    <div class="card-inspector__actions" aria-label="单词卡操作">
      <button v-if="editing" type="button" @click="cancelEditing">取消编辑</button>
      <button v-else type="button" @click="editing = true">编辑 Markdown</button>
      <label class="card-inspector__regenerate-theme">
        <span>主题</span>
        <select v-model="selectedThemeUid" aria-label="重新生成主题" :disabled="!activeThemes.length || themesQuery.isLoading.value">
          <option v-for="theme in activeThemes" :key="theme.themeUid" :value="theme.themeUid">{{ theme.name }}</option>
        </select>
      </label>
      <button
        type="button"
        :disabled="!selectedTheme || regenerateMutation.isPending.value || themesBlockingError"
        @click="requestRegenerate"
      >
        {{ regenerateMutation.isPending.value ? '生成中...' : '重新生成' }}
      </button>
      <button v-if="card.status === 'failed' || card.generationStatus === 'failed'" type="button" :disabled="retryVocabularyCard.isPending.value" @click="retry">
        {{ retryVocabularyCard.isPending.value ? '重试中...' : '重试生成' }}
      </button>
      <button type="button" class="card-inspector__danger" @click="deleteDialogOpen = true">删除</button>
    </div>

    <div v-if="themesQuery.isLoading.value && !themesQuery.data.value" class="card-inspector__theme-state">主题加载中...</div>
    <div v-else-if="themesBlockingError" class="card-inspector__theme-state card-inspector__theme-state--error" role="alert">
      <span>主题加载失败</span>
      <button type="button" :disabled="themesQuery.isFetching.value" @click="themesQuery.refetch()">重新加载</button>
    </div>
    <p v-else-if="!activeThemes.length" class="card-inspector__theme-state">暂无可用主题</p>
    <p v-if="generationErrorMessage" class="card-inspector__error" role="alert">{{ generationErrorMessage }}</p>

    <div class="card-inspector__tabs" role="tablist" aria-label="单词卡内容">
      <button type="button" role="tab" :aria-selected="activeTab === 'details'" @click="activeTab = 'details'">卡片内容</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'sources'" @click="activeTab = 'sources'">来源</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'history'" @click="activeTab = 'history'">历史</button>
    </div>

    <form v-if="activeTab === 'details'" class="card-inspector__content" @submit.prevent="save">
      <VocabularyCoreSummary :core="displayCore" />
      <VocabularyMarkdownEditor v-model="editMarkdown" :readonly="!editing" />
      <div v-if="editing" class="card-inspector__save-row">
        <button type="submit" :disabled="!card.activeRevisionUid || markdownTooLong || updateMutation.isPending.value">
          {{ updateMutation.isPending.value ? '保存中...' : '保存修改' }}
        </button>
      </div>
    </form>

    <section v-else-if="activeTab === 'sources'" class="card-inspector__sources" aria-label="单词卡来源">
      <p v-if="!card.sources.length" class="card-inspector__empty">暂无来源记录</p>
      <article v-for="source in card.sources" :key="source.sourceUid">
        <strong>{{ source.sourceTitle || source.sourceType }}</strong>
        <span>{{ source.contextText || '未记录语境' }}</span>
        <a v-if="safeExternalUrl(source.sourceUrl)" :href="safeExternalUrl(source.sourceUrl)!" target="_blank" rel="noreferrer">查看原始来源</a>
      </article>
    </section>

    <section v-else class="card-inspector__history" aria-label="单词卡修订历史">
      <p v-if="!listVocabularyRevisions || !listVocabularyRevisions.items.length" class="card-inspector__empty">暂无修订记录</p>
      <article v-for="revision in listVocabularyRevisions?.items ?? []" :key="revision.revisionUid">
        <div><strong>{{ revision.authorType }}</strong><span>{{ revision.changeSummary || '无修改说明' }}</span></div>
        <small>{{ revision.createdAt || '时间未知' }}</small>
      </article>
    </section>

    <div v-if="regenerateConfirmationOpen" class="card-inspector__dialog-backdrop" role="presentation" @click.self="regenerateConfirmationOpen = false">
      <section class="card-inspector__dialog" role="dialog" aria-modal="true" aria-labelledby="regenerate-card-title">
        <h3 id="regenerate-card-title">使用最新主题版本？</h3>
        <p>将使用主题最新版本重新生成，当前版本会保留在历史中。</p>
        <div class="card-inspector__dialog-actions">
          <button type="button" @click="regenerateConfirmationOpen = false">取消</button>
          <button type="button" :disabled="regenerateMutation.isPending.value" @click="regenerate">确认重新生成</button>
        </div>
      </section>
    </div>

    <div v-if="deleteDialogOpen" class="card-inspector__dialog-backdrop" role="presentation" @click.self="deleteDialogOpen = false">
      <section class="card-inspector__dialog" role="dialog" aria-modal="true" aria-labelledby="delete-card-title">
        <h3 id="delete-card-title">删除单词卡？</h3>
        <p>删除后会从单词卡列表移除；再次收藏或录入时可恢复，修订历史会保留。</p>
        <div class="card-inspector__dialog-actions">
          <button type="button" @click="deleteDialogOpen = false">取消</button>
          <button type="button" class="card-inspector__danger" :disabled="deleteMutation.isPending.value" @click="removeCard">
            {{ deleteMutation.isPending.value ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="conflict" class="card-inspector__dialog-backdrop" role="presentation">
      <section class="card-inspector__dialog card-inspector__dialog--conflict" role="dialog" aria-modal="true" aria-labelledby="conflict-card-title">
        <h3 id="conflict-card-title">发现版本冲突</h3>
        <p>{{ v1Conflict ? '请整体比较 Markdown，并选择要保留的版本。' : '请决定保留当前内容、采用 AI 新版本，或逐字段合并。' }}</p>

        <div v-if="v1Conflict" class="card-inspector__conflict-columns">
          <section><h4>当前 Markdown</h4><pre>{{ currentConflictMarkdown || '暂无 Markdown 内容' }}</pre></section>
          <section><h4>候选 Markdown</h4><pre>{{ candidateConflictMarkdown || '暂无 Markdown 内容' }}</pre></section>
        </div>
        <div v-else class="card-inspector__conflict-columns">
          <section><h4>当前内容</h4><dl><template v-for="field in legacyConflictFields" :key="`current-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.currentContent, field) }}</dd></template></dl></section>
          <section><h4>AI 新版本</h4><dl><template v-for="field in legacyConflictFields" :key="`candidate-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.candidateContent, field) }}</dd></template></dl></section>
        </div>

        <fieldset class="card-inspector__conflict-options">
          <legend>解决方式</legend>
          <label><input v-model="conflictChoice" type="radio" value="keep_current">保留当前内容</label>
          <label><input v-model="conflictChoice" type="radio" value="use_ai">使用 AI 新版本</label>
          <label><input v-model="conflictChoice" type="radio" value="merge_fields">{{ v1Conflict ? '组合核心数据与 Markdown' : '逐字段合并' }}</label>
        </fieldset>
        <div v-if="conflictChoice === 'merge_fields'" class="card-inspector__merge-fields">
          <label v-for="field in mergeableConflictFields" :key="field">
            <span>{{ fieldLabel(field) }}</span>
            <select v-model="mergeChoice[field]" :aria-label="`合并${fieldLabel(field)}`">
              <option value="current">当前内容</option>
              <option value="candidate">AI 新版本</option>
            </select>
          </label>
        </div>
        <div class="card-inspector__dialog-actions">
          <button type="button" @click="conflict = null">取消</button>
          <button type="button" :disabled="resolveConflictMutation.isPending.value" @click="resolveConflict">确认处理</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Ref } from 'vue'

import VocabularyCoreSummary from './VocabularyCoreSummary.vue'
import VocabularyMarkdownEditor from './VocabularyMarkdownEditor.vue'
import {
  VocabularyConflictError,
  type RegenerateVocabularyCardRequest,
  type ResolveVocabularyConflictRequest,
  type UpdateVocabularyCardRequest,
  type VocabularyCardDetail,
  type VocabularyCardStatus,
  type VocabularyConflictResponse,
  type VocabularyCoreContent,
  type VocabularyRevision,
  type VocabularyRevisionListResponse,
  type VocabularyTemplate,
} from '@/api/vocabulary'
import { useVocabularyThemes } from '@/composables/useVocabularyThemes'
import {
  projectLegacyVocabularyCore,
  isVocabularyV1Revision,
  selectVocabularyThemeUid,
  shouldResetVocabularyCardDraft,
  type VocabularyCardDraftIdentity,
} from '@/composables/useVocabularyCards'
import { safeExternalUrl } from '@/features/vocabulary/safeExternalUrl'
import { showToast } from '@/utils/toast'

type MutationBridge<T, TResult = unknown> = { isPending: Ref<boolean>, mutateAsync: (payload: T) => Promise<TResult> }
type MergeChoice = Record<string, 'current' | 'candidate'>

const props = defineProps<{
  card: VocabularyCardDetail
  template: VocabularyTemplate
  templates: VocabularyTemplate[]
  listVocabularyRevisions?: VocabularyRevisionListResponse
  updateMutation: MutationBridge<{ cardUid: string, payload: UpdateVocabularyCardRequest }, VocabularyCardDetail>
  deleteMutation: MutationBridge<string>
  regenerateMutation: MutationBridge<{ cardUid: string } & RegenerateVocabularyCardRequest>
  retryVocabularyCard: MutationBridge<string>
  resolveConflictMutation: MutationBridge<{ cardUid: string, revisionUid: string, payload: ResolveVocabularyConflictRequest }>
}>()

const emit = defineEmits<{ back: [] }>()
const { themesQuery } = useVocabularyThemes()
const activeTab = ref<'details' | 'sources' | 'history'>('details')
const editing = ref(false)
const editMarkdown = ref('')
const selectedThemeUid = ref('')
const regenerateConfirmationOpen = ref(false)
const deleteDialogOpen = ref(false)
const conflict = ref<VocabularyConflictResponse | null>(null)
const conflictChoice = ref<ResolveVocabularyConflictRequest['choice']>('keep_current')
const mergeChoice = ref<MergeChoice>({})
const draftIdentity = ref<VocabularyCardDraftIdentity>()

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function minimalVocabularyCore(term: string): VocabularyCoreContent {
  return { schemaVersion: 1, term, phonetics: [], senses: [] }
}

const displayCore = computed<VocabularyCoreContent>(() => {
  const projected = props.card.core ?? projectLegacyVocabularyCore(props.card.normalizedTerm, props.card.content)
  return projected
    ? { ...projected, term: props.card.normalizedTerm }
    : minimalVocabularyCore(props.card.normalizedTerm)
})
const markdownTooLong = computed(() => editMarkdown.value.length > 20_000)
const generationErrorMessage = computed(() => {
  if (props.card.generationOutcome === 'partial'
      && props.card.warning === 'markdown_unavailable') {
    return '主题内容待完善，可重新生成。'
  }
  if (props.card.generationOutcome === 'failed'
      || props.card.generationStatus === 'failed'
      || props.card.generationError) {
    return '生成未完成，请重试。'
  }
  return ''
})
const themesBlockingError = computed(() => themesQuery.isError.value && !themesQuery.data.value)
const activeThemes = computed(() => {
  const catalog = themesQuery.data.value
  if (!catalog) return []
  return [...catalog.systemThemes, ...catalog.userThemes].filter((theme) => theme.status === 'active')
})
const selectedTheme = computed(() => activeThemes.value.find((theme) => theme.themeUid === selectedThemeUid.value))
const regenerateNeedsConfirmation = computed(() => (
  selectedTheme.value?.themeUid !== props.card.theme?.themeUid
  || selectedTheme.value?.version !== props.card.themeVersion
))

function cardMarkdown(card: VocabularyCardDetail): string {
  if (card.markdown != null) return card.markdown
  const compatibleMarkdown = asRecord(card.content).markdown
  return typeof compatibleMarkdown === 'string' ? compatibleMarkdown : ''
}

function revisionFor(revisionUid: string | null): VocabularyRevision | undefined {
  return props.listVocabularyRevisions?.items.find((revision) => revision.revisionUid === revisionUid)
}

function isCoreContent(value: unknown): value is VocabularyCoreContent {
  const record = asRecord(value)
  return record.schemaVersion === 1 && Array.isArray(record.phonetics) && Array.isArray(record.senses)
}

const v1Conflict = computed(() => {
  const currentRevision = revisionFor(conflict.value?.currentRevisionUid ?? null)
  const currentContentFormatVersion = conflict.value?.currentContentFormatVersion
  const currentFormatVersion = currentContentFormatVersion !== undefined
    ? currentContentFormatVersion
    : currentRevision?.contentFormatVersion
  return isVocabularyV1Revision(currentFormatVersion, conflict.value?.currentContent)
})

function conflictMarkdown(revisionUid: string | null, content: unknown): string {
  const revisionMarkdown = revisionFor(revisionUid)?.markdown
  if (revisionMarkdown != null) return revisionMarkdown
  const markdown = asRecord(content).markdown
  return typeof markdown === 'string' ? markdown : ''
}

const currentConflictMarkdown = computed(() => conflictMarkdown(conflict.value?.currentRevisionUid ?? null, conflict.value?.currentContent))
const candidateConflictMarkdown = computed(() => conflictMarkdown(conflict.value?.candidateRevisionUid ?? null, conflict.value?.candidateContent))
const legacyConflictFields = computed(() => {
  const keys = new Set([
    ...Object.keys(asRecord(conflict.value?.currentContent)),
    ...Object.keys(asRecord(conflict.value?.candidateContent)),
  ])
  return [...keys].filter((field) => field !== 'markdown')
})
const legacyMergeableFields = computed(() => legacyConflictFields.value.filter((field) => field !== 'term'))
const mergeableConflictFields = computed(() => v1Conflict.value ? ['core', 'markdown'] : legacyMergeableFields.value)

function conflictCore(revisionUid: string | null, content: unknown): VocabularyCoreContent {
  const revisionCore = revisionFor(revisionUid)?.core
  const source = revisionCore ?? (isCoreContent(content) ? content : null)
  return source
    ? {
        schemaVersion: source.schemaVersion,
        term: props.card.normalizedTerm,
        phonetics: source.phonetics,
        senses: source.senses,
      }
    : displayCore.value
}

function fieldLabel(field: string) {
  return ({
    term: '单词', definitions: '释义', examples: '例句', notes: '个人笔记',
    core: '核心词典数据', markdown: 'Markdown',
  } as Record<string, string>)[field] ?? field
}

function displayValue(content: unknown, field: string) {
  const value = asRecord(content)[field]
  return Array.isArray(value) ? value.join('；') : value == null ? '未填写' : String(value)
}

function resetMergeChoice() {
  mergeChoice.value = Object.fromEntries(mergeableConflictFields.value.map((field) => [field, 'current']))
}

function setConflict(nextConflict: VocabularyConflictResponse) {
  conflict.value = nextConflict
  conflictChoice.value = 'keep_current'
  resetMergeChoice()
}

watch(
  () => [props.card.cardUid, props.card.activeRevisionUid] as const,
  ([cardUid, activeRevisionUid]) => {
    const nextIdentity = { cardUid, activeRevisionUid }
    if (!shouldResetVocabularyCardDraft(draftIdentity.value, nextIdentity)) return
    const cardChanged = draftIdentity.value?.cardUid !== cardUid
    draftIdentity.value = nextIdentity
    editMarkdown.value = cardMarkdown(props.card)
    if (cardChanged) {
      selectedThemeUid.value = selectVocabularyThemeUid(
        activeThemes.value,
        themesQuery.data.value?.defaultThemeUid ?? '',
        props.card.theme?.themeUid,
      )
    }
    editing.value = false
    regenerateConfirmationOpen.value = false
  },
  { immediate: true },
)

watch(
  () => [
    props.card.status,
    props.card.activeRevisionUid,
    props.card.candidateRevisionUid,
    props.card.content,
    props.card.candidateContent,
  ] as const,
  ([status, activeRevisionUid, candidateRevisionUid, content, candidateContent]) => {
    if (status === 'needs_review' && candidateRevisionUid && candidateContent) {
      setConflict({
        currentRevisionUid: activeRevisionUid,
        candidateRevisionUid,
        currentContent: content,
        candidateContent,
        currentContentFormatVersion: props.card.contentFormatVersion,
        candidateContentFormatVersion: null,
        conflictStatus: 'needs_review',
      })
    } else {
      conflict.value = null
    }
  },
  { immediate: true, deep: true },
)

watch(() => themesQuery.data.value, (catalog) => {
  if (!catalog || activeThemes.value.some((theme) => theme.themeUid === selectedThemeUid.value)) return
  selectedThemeUid.value = selectVocabularyThemeUid(
    activeThemes.value,
    catalog.defaultThemeUid,
    props.card.theme?.themeUid,
  )
}, { immediate: true })

watch([v1Conflict, conflict], () => resetMergeChoice())

function cancelEditing() {
  editMarkdown.value = cardMarkdown(props.card)
  editing.value = false
}

async function save() {
  if (!props.card.activeRevisionUid || markdownTooLong.value) return
  try {
    const savedCard = await props.updateMutation.mutateAsync({
      cardUid: props.card.cardUid,
      payload: {
        baseRevisionUid: props.card.activeRevisionUid,
        core: { ...displayCore.value, term: props.card.normalizedTerm },
        markdown: editMarkdown.value,
        changeSummary: '用户编辑 Markdown 卡片',
      },
    })
    editMarkdown.value = cardMarkdown(savedCard)
    draftIdentity.value = {
      cardUid: savedCard.cardUid,
      activeRevisionUid: savedCard.activeRevisionUid,
    }
    editing.value = false
    showToast('单词卡已保存', 'success')
  } catch (error) {
    if (error instanceof VocabularyConflictError) {
      setConflict(error.conflict)
      return
    }
    showToast(error instanceof Error ? error.message : '保存失败，请重试', 'error')
  }
}

function requestRegenerate() {
  if (!selectedTheme.value) return
  if (regenerateNeedsConfirmation.value) {
    regenerateConfirmationOpen.value = true
    return
  }
  void regenerate()
}

async function regenerate() {
  if (!selectedTheme.value) return
  try {
    await props.regenerateMutation.mutateAsync({
      cardUid: props.card.cardUid,
      themeUid: selectedThemeUid.value,
      useLatestThemeVersion: true,
    })
    regenerateConfirmationOpen.value = false
    showToast('已提交重新生成任务', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '重新生成失败，请重试', 'error')
  }
}

async function retry() {
  try {
    await props.retryVocabularyCard.mutateAsync(props.card.cardUid)
    showToast('已提交重试任务', 'success')
  } catch (error) { showToast(error instanceof Error ? error.message : '重试失败，请重试', 'error') }
}

async function removeCard() {
  try {
    await props.deleteMutation.mutateAsync(props.card.cardUid)
    deleteDialogOpen.value = false
    emit('back')
    showToast('单词卡已删除', 'success')
  } catch (error) { showToast(error instanceof Error ? error.message : '删除失败，请重试', 'error') }
}

function conflictMergeFields(): Record<string, unknown> {
  if (!conflict.value) return {}
  if (v1Conflict.value) {
    const currentCore = conflictCore(conflict.value.currentRevisionUid, conflict.value.currentContent)
    const candidateCore = conflictCore(conflict.value.candidateRevisionUid, conflict.value.candidateContent)
    return {
      core: mergeChoice.value.core === 'candidate' ? candidateCore : currentCore,
      markdown: mergeChoice.value.markdown === 'candidate'
        ? candidateConflictMarkdown.value
        : currentConflictMarkdown.value,
    }
  }
  return Object.fromEntries(legacyMergeableFields.value.map((field) => [
    field,
    asRecord(mergeChoice.value[field] === 'candidate' ? conflict.value?.candidateContent : conflict.value?.currentContent)[field] ?? null,
  ]))
}

async function resolveConflict() {
  if (!conflict.value?.candidateRevisionUid) return
  const mergeFields = conflictMergeFields()
  try {
    await props.resolveConflictMutation.mutateAsync({
      cardUid: props.card.cardUid,
      revisionUid: conflict.value.candidateRevisionUid,
      payload: conflictChoice.value === 'merge_fields'
        ? { choice: 'merge_fields', mergeFields }
        : { choice: conflictChoice.value },
    })
    conflict.value = null
    editing.value = false
    showToast('冲突已处理', 'success')
  } catch (error) { showToast(error instanceof Error ? error.message : '冲突处理失败，请重试', 'error') }
}

function statusLabel(status: VocabularyCardStatus) {
  return ({ captured: '已收下', generating: '正在生成', ready: '已就绪', needs_review: '待确认', failed: '生成失败' } as Record<VocabularyCardStatus, string>)[status]
}
</script>

<style scoped>
.card-inspector { min-width: 0; padding: 18px; border: 1px solid #dce7e1; border-radius: 8px; background: #fff; color: #334155; }
.card-inspector__header, .card-inspector__actions, .card-inspector__dialog-actions { display: flex; align-items: start; justify-content: space-between; gap: 10px; }
.card-inspector__header p, .card-inspector__header h2 { margin: 0; }
.card-inspector__header p { color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.card-inspector__header h2 { margin-top: 5px; color: #0f172a; font-size: 22px; overflow-wrap: anywhere; }
.card-inspector__status { display: inline-block; margin-top: 7px; color: #047857; font-size: 12px; font-weight: 800; }
.card-inspector__status--failed { color: #b91c1c; }
.card-inspector__status--needs_review { color: #b45309; }
.card-inspector button { min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; font-weight: 700; padding: 0 10px; cursor: pointer; }
.card-inspector button:disabled { cursor: not-allowed; opacity: .55; }
.card-inspector__back { white-space: nowrap; }
.card-inspector__actions { flex-wrap: wrap; margin-top: 16px; align-items: center; justify-content: flex-start; }
.card-inspector__actions button:first-child, .card-inspector__save-row button, .card-inspector__dialog-actions button:last-child { border-color: #059669; background: #059669; color: #fff; }
.card-inspector__danger { border-color: #fecaca !important; background: #fff !important; color: #b91c1c !important; }
.card-inspector__regenerate-theme { min-width: 0; display: flex; align-items: center; gap: 6px; color: #64748b; font-size: 12px; }
.card-inspector__regenerate-theme select { box-sizing: border-box; max-width: 220px; min-width: 0; min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; padding: 0 8px; }
.card-inspector__theme-state { min-width: 0; display: flex; align-items: center; gap: 8px; margin: 9px 0 0; color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.card-inspector__theme-state--error, .card-inspector__error { color: #b91c1c; }
.card-inspector__error { margin: 12px 0 0; font-size: 13px; overflow-wrap: anywhere; }
.card-inspector__tabs { display: flex; gap: 4px; margin-top: 18px; overflow-x: auto; border-bottom: 1px solid #dce7e1; }
.card-inspector__tabs button { flex: none; border: 0; border-radius: 0; background: transparent; padding: 0 8px 9px; }
.card-inspector__tabs button[aria-selected="true"] { border-bottom: 2px solid #059669; color: #047857; }
.card-inspector__content { min-width: 0; display: grid; gap: 20px; margin-top: 16px; }
.card-inspector__save-row { display: flex; justify-content: flex-end; }
.card-inspector__sources, .card-inspector__history { min-width: 0; display: grid; gap: 10px; margin-top: 16px; }
.card-inspector__sources article, .card-inspector__history article { min-width: 0; display: grid; gap: 4px; padding: 11px; border: 1px solid #edf2f7; border-radius: 6px; }
.card-inspector__sources span, .card-inspector__history span, .card-inspector__history small { color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.card-inspector__sources a { color: #047857; font-size: 13px; overflow-wrap: anywhere; }
.card-inspector__history article div { min-width: 0; display: flex; justify-content: space-between; gap: 8px; }
.card-inspector__empty { color: #64748b; font-size: 13px; }
.card-inspector__dialog-backdrop { position: fixed; inset: 0; z-index: 10001; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .42); }
.card-inspector__dialog { box-sizing: border-box; width: min(100%, 460px); max-height: calc(100vh - 32px); overflow: auto; border-radius: 8px; background: #fff; padding: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .25); }
.card-inspector__dialog h3, .card-inspector__dialog p { margin: 0; }
.card-inspector__dialog > p { margin-top: 8px; color: #64748b; line-height: 1.5; }
.card-inspector__dialog-actions { margin-top: 18px; justify-content: flex-end; align-items: center; }
.card-inspector__dialog--conflict { width: min(100%, 840px); }
.card-inspector__conflict-columns { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
.card-inspector__conflict-columns section { min-width: 0; border: 1px solid #edf2f7; border-radius: 6px; padding: 10px; }
.card-inspector__conflict-columns h4 { margin: 0 0 9px; color: #0f172a; font-size: 14px; }
.card-inspector__conflict-columns pre { min-height: 120px; max-height: 280px; overflow: auto; margin: 0; color: #334155; font: 12px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.card-inspector__conflict-columns dl { display: grid; gap: 4px; margin: 0; }
.card-inspector__conflict-columns dt { color: #64748b; font-size: 12px; }
.card-inspector__conflict-columns dd { margin: 0; overflow-wrap: anywhere; font-size: 13px; }
.card-inspector__conflict-options { display: grid; gap: 8px; margin: 16px 0 0; border: 0; padding: 0; }
.card-inspector__conflict-options legend { margin-bottom: 8px; font-weight: 800; }
.card-inspector__conflict-options label { display: flex; gap: 8px; align-items: center; font-size: 13px; }
.card-inspector__merge-fields { display: grid; gap: 8px; margin-top: 14px; }
.card-inspector__merge-fields label { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) minmax(120px, 1fr); gap: 8px; align-items: center; font-size: 13px; }
.card-inspector__merge-fields select { box-sizing: border-box; width: 100%; min-width: 0; min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; }
@media (max-width: 620px) {
  .card-inspector { padding: 14px; }
  .card-inspector__header { align-items: start; flex-direction: column; }
  .card-inspector__back { width: 100%; }
  .card-inspector__actions { display: grid; grid-template-columns: 1fr; }
  .card-inspector__actions button, .card-inspector__regenerate-theme, .card-inspector__regenerate-theme select { width: 100%; max-width: none; }
  .card-inspector__conflict-columns, .card-inspector__merge-fields label { grid-template-columns: 1fr; }
  .card-inspector__history article div { flex-direction: column; }
}
</style>
