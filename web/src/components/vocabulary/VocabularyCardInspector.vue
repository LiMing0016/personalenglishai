<template>
  <section class="card-inspector" aria-label="单词卡详情">
    <header class="card-inspector__header">
      <div>
        <p>当前模板：{{ card.templateKey }}</p>
        <h2>{{ card.displayTerm }}</h2>
        <span :class="`card-inspector__status card-inspector__status--${card.status}`">{{ statusLabel(card.status) }}</span>
      </div>
      <button type="button" class="card-inspector__back" @click="emit('back')">返回单词库</button>
    </header>

    <div class="card-inspector__actions" aria-label="单词卡操作">
      <button v-if="editing" type="button" @click="cancelEditing">取消编辑</button>
      <button v-else type="button" @click="editing = true">编辑卡片</button>
      <label class="card-inspector__regenerate-template">
        <span>模板</span>
        <select v-model="regenerateTemplateKey" aria-label="重新生成模板">
          <option v-for="option in templates" :key="option.key" :value="option.key">{{ option.name }}</option>
        </select>
      </label>
      <button type="button" :disabled="regenerateMutation.isPending.value" @click="regenerate">
        {{ regenerateMutation.isPending.value ? '生成中...' : '重新生成' }}
      </button>
      <button v-if="card.status === 'failed'" type="button" :disabled="retryVocabularyCard.isPending.value" @click="retry">
        {{ retryVocabularyCard.isPending.value ? '重试中...' : '重试生成' }}
      </button>
      <button type="button" class="card-inspector__danger" @click="deleteDialogOpen = true">删除</button>
    </div>

    <p v-if="card.generationError" class="card-inspector__error" role="alert">{{ card.generationError }}</p>

    <div class="card-inspector__tabs" role="tablist" aria-label="单词卡内容">
      <button type="button" role="tab" :aria-selected="activeTab === 'details'" @click="activeTab = 'details'">卡片内容</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'sources'" @click="activeTab = 'sources'">来源</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'history'" @click="activeTab = 'history'">历史</button>
    </div>

    <form v-if="activeTab === 'details'" class="card-inspector__content" @submit.prevent="save">
      <div v-for="field in fieldNames" :key="field" class="card-inspector__field">
        <label :for="fieldId(field)">{{ fieldLabel(field) }}</label>
        <input
          v-if="field === 'term'"
          :id="fieldId(field)"
          :value="String(editContent[field] ?? card.displayTerm)"
          type="text"
          readonly
          aria-readonly="true"
        >
        <template v-else-if="isArrayField(field)">
          <div v-for="(_, index) in arrayValue(field)" :key="`${field}-${index}`" class="card-inspector__array-row">
            <input :id="index === 0 ? fieldId(field) : undefined" v-model="arrayValue(field)[index]" type="text" :readonly="!editing">
            <button v-if="editing" type="button" :aria-label="`删除${fieldLabel(field)}第${index + 1}项`" @click="removeArrayValue(field, index)">删除</button>
          </div>
          <button v-if="editing" type="button" class="card-inspector__add" @click="addArrayValue(field)">添加{{ fieldLabel(field) }}</button>
        </template>
        <textarea
          v-else
          :id="fieldId(field)"
          :value="textValue(field)"
          @input="updateTextValue(field, ($event.target as HTMLTextAreaElement).value)"
          :readonly="!editing"
          :aria-label="field === 'notes' ? '个人笔记' : fieldLabel(field)"
          :rows="field === 'notes' ? 4 : 3"
        ></textarea>
      </div>
      <div v-if="editing" class="card-inspector__save-row">
        <button type="submit" :disabled="!card.activeRevisionUid || updateMutation.isPending.value">
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

    <div v-if="deleteDialogOpen" class="card-inspector__dialog-backdrop" role="presentation" @click.self="deleteDialogOpen = false">
      <section class="card-inspector__dialog" role="dialog" aria-modal="true" aria-labelledby="delete-card-title">
        <h3 id="delete-card-title">删除单词卡？</h3>
        <p>删除后会从单词卡列表移除；再次收藏或录入时可恢复，修订历史会保留。</p>
        <div>
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
        <p>请决定保留当前内容、采用 AI 新版本，或逐字段合并。</p>
        <div class="card-inspector__conflict-columns">
          <section><h4>当前内容</h4><dl><template v-for="field in conflictFields" :key="`current-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.currentContent, field) }}</dd></template></dl></section>
          <section><h4>AI 新版本</h4><dl><template v-for="field in conflictFields" :key="`candidate-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.candidateContent, field) }}</dd></template></dl></section>
        </div>
        <fieldset class="card-inspector__conflict-options">
          <legend>解决方式</legend>
          <label><input v-model="conflictChoice" type="radio" value="keep_current">保留当前内容</label>
          <label><input v-model="conflictChoice" type="radio" value="use_ai">使用 AI 新版本</label>
          <label><input v-model="conflictChoice" type="radio" value="merge_fields">逐字段合并</label>
        </fieldset>
        <div v-if="conflictChoice === 'merge_fields'" class="card-inspector__merge-fields">
          <label v-for="field in mergeableFields" :key="field">
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

import {
  VocabularyConflictError,
  type ResolveVocabularyConflictRequest,
  type UpdateVocabularyCardRequest,
  type VocabularyCardDetail,
  type VocabularyCardStatus,
  type VocabularyConflictResponse,
  type VocabularyRevisionListResponse,
  type VocabularyTemplate,
  type VocabularyTemplateKey,
} from '@/api/vocabulary'
import { showToast } from '@/utils/toast'
import { safeExternalUrl } from '@/features/vocabulary/safeExternalUrl'

type MutationBridge<T> = { isPending: Ref<boolean>, mutateAsync: (payload: T) => Promise<unknown> }
type EditableContent = Record<string, string | string[]>

const props = defineProps<{
  card: VocabularyCardDetail
  template: VocabularyTemplate
  templates: VocabularyTemplate[]
  listVocabularyRevisions?: VocabularyRevisionListResponse
  updateMutation: MutationBridge<{ cardUid: string, payload: UpdateVocabularyCardRequest }>
  deleteMutation: MutationBridge<string>
  regenerateMutation: MutationBridge<{ cardUid: string, templateKey: VocabularyTemplateKey }>
  retryVocabularyCard: MutationBridge<string>
  resolveConflictMutation: MutationBridge<{ cardUid: string, revisionUid: string, payload: ResolveVocabularyConflictRequest }>
}>()

const emit = defineEmits<{ back: [] }>()
const activeTab = ref<'details' | 'sources' | 'history'>('details')
const editing = ref(false)
const deleteDialogOpen = ref(false)
const conflict = ref<VocabularyConflictResponse | null>(null)
const conflictChoice = ref<ResolveVocabularyConflictRequest['choice']>('keep_current')
const mergeChoice = ref<Record<string, 'current' | 'candidate'>>({})
const editContent = ref<EditableContent>({})
const regenerateTemplateKey = ref<VocabularyTemplateKey>(props.card.templateKey)

function cloneEditableContent(value: unknown): EditableContent {
  const source = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  const content: EditableContent = {}
  for (const [field, fieldValue] of Object.entries(source)) {
    content[field] = Array.isArray(fieldValue)
      ? fieldValue.map((item) => String(item))
      : fieldValue == null ? '' : String(fieldValue)
  }
  for (const field of props.template.fields) {
    if (field in content) continue
    const candidateValue = asRecord(props.card.candidateContent)[field]
    content[field] = Array.isArray(candidateValue) ? [] : ''
  }
  if (!('term' in content)) content.term = props.card.displayTerm
  if (!('notes' in content)) content.notes = ''
  return content
}

const fieldNames = computed(() => {
  const templateFields = props.template.fields.filter((field) => field !== 'term' && field !== 'notes')
  return ['term', ...new Set(templateFields), 'notes']
})
const conflictFields = computed(() => fieldNames.value)
const mergeableFields = computed(() => fieldNames.value.filter((field) => field !== 'term'))

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function fieldId(field: string) { return `vocabulary-card-${field}` }
function fieldLabel(field: string) {
  return ({ term: '单词', definitions: '释义', examples: '例句', notes: '个人笔记' } as Record<string, string>)[field] ?? field
}
function isArrayField(field: string) {
  return Array.isArray(editContent.value[field])
    || Array.isArray(asRecord(props.card.content)[field])
    || Array.isArray(asRecord(props.card.candidateContent)[field])
}
function arrayValue(field: string): string[] {
  const value = editContent.value[field]
  if (!Array.isArray(value)) editContent.value[field] = []
  return editContent.value[field] as string[]
}
function textValue(field: string): string {
  const value = editContent.value[field]
  if (Array.isArray(value)) return value.join('\n')
  return value ?? ''
}
function updateTextValue(field: string, value: string) { editContent.value[field] = value }
function addArrayValue(field: string) { arrayValue(field).push('') }
function removeArrayValue(field: string, index: number) { arrayValue(field).splice(index, 1) }
function displayValue(content: unknown, field: string) {
  const value = asRecord(content)[field]
  return Array.isArray(value) ? value.join('；') : value == null ? '未填写' : String(value)
}

function setConflict(nextConflict: VocabularyConflictResponse) {
  conflict.value = nextConflict
  conflictChoice.value = 'keep_current'
  mergeChoice.value = Object.fromEntries(mergeableFields.value.map((field) => [field, 'current']))
}

watch(() => [props.card, props.template] as const, ([card]) => {
  editContent.value = cloneEditableContent(card.content)
  regenerateTemplateKey.value = card.templateKey
  editing.value = false
  if (card.status === 'needs_review' && card.candidateRevisionUid && card.candidateContent) {
    setConflict({
      currentRevisionUid: card.activeRevisionUid,
      candidateRevisionUid: card.candidateRevisionUid,
      currentContent: card.content,
      candidateContent: card.candidateContent,
      conflictStatus: 'needs_review',
    })
  } else {
    conflict.value = null
  }
}, { immediate: true, deep: true })

function cancelEditing() {
  editContent.value = cloneEditableContent(props.card.content)
  editing.value = false
}

function snapshotEditableContent(): EditableContent {
  return Object.fromEntries(Object.entries(editContent.value).map(([field, value]) => [
    field,
    Array.isArray(value) ? [...value] : value,
  ]))
}

async function save() {
  if (!props.card.activeRevisionUid) return
  try {
    await props.updateMutation.mutateAsync({
      cardUid: props.card.cardUid,
      payload: {
        baseRevisionUid: props.card.activeRevisionUid,
        content: snapshotEditableContent(),
        changeSummary: '用户编辑卡片',
      },
    })
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

async function regenerate() {
  try {
    await props.regenerateMutation.mutateAsync({
      cardUid: props.card.cardUid,
      templateKey: regenerateTemplateKey.value,
    })
    showToast('已提交重新生成任务', 'success')
  } catch (error) { showToast(error instanceof Error ? error.message : '重新生成失败，请重试', 'error') }
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

async function resolveConflict() {
  if (!conflict.value?.candidateRevisionUid) return
  const mergeFields = Object.fromEntries(mergeableFields.value.map((field) => [
    field,
    asRecord(mergeChoice.value[field] === 'candidate' ? conflict.value?.candidateContent : conflict.value?.currentContent)[field] ?? null,
  ]))
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
.card-inspector__header, .card-inspector__actions, .card-inspector__dialog-actions { display: flex; align-items: start; justify-content: space-between; gap: 10px; }.card-inspector__header p, .card-inspector__header h2 { margin: 0; }.card-inspector__header p { color: #64748b; font-size: 12px; }.card-inspector__header h2 { margin-top: 5px; color: #0f172a; font-size: 22px; overflow-wrap: anywhere; }.card-inspector__status { display: inline-block; margin-top: 7px; color: #047857; font-size: 12px; font-weight: 800; }.card-inspector__status--failed { color: #b91c1c; }.card-inspector__status--needs_review { color: #b45309; }
.card-inspector button { min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; font-weight: 700; padding: 0 10px; cursor: pointer; }.card-inspector button:disabled { cursor: not-allowed; opacity: .55; }.card-inspector__back { white-space: nowrap; }.card-inspector__actions { flex-wrap: wrap; margin-top: 16px; align-items: center; justify-content: flex-start; }.card-inspector__actions button:first-child, .card-inspector__save-row button, .card-inspector__dialog-actions button:last-child { border-color: #059669; background: #059669; color: #fff; }.card-inspector__danger { border-color: #fecaca !important; color: #b91c1c !important; }.card-inspector__error { margin: 12px 0 0; color: #b91c1c; font-size: 13px; }
.card-inspector__regenerate-template { display: flex; align-items: center; gap: 6px; color: #64748b; font-size: 12px; }.card-inspector__regenerate-template select { width: auto; min-height: 34px; padding-block: 0; }
.card-inspector__tabs { display: flex; gap: 4px; margin-top: 18px; border-bottom: 1px solid #dce7e1; }.card-inspector__tabs button { border: 0; border-radius: 0; background: transparent; padding: 0 8px 9px; }.card-inspector__tabs button[aria-selected="true"] { border-bottom: 2px solid #059669; color: #047857; }
.card-inspector__content { display: grid; gap: 13px; margin-top: 16px; }.card-inspector__field { display: grid; gap: 6px; min-width: 0; }.card-inspector__field > label { color: #475569; font-size: 13px; font-weight: 800; }.card-inspector input, .card-inspector textarea, .card-inspector select { box-sizing: border-box; width: 100%; min-width: 0; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; padding: 8px 10px; }.card-inspector textarea { resize: vertical; }.card-inspector input[readonly], .card-inspector textarea[readonly] { background: #f8fafc; color: #64748b; }.card-inspector__array-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }.card-inspector__add { justify-self: start; }.card-inspector__save-row { display: flex; justify-content: flex-end; }
.card-inspector__sources, .card-inspector__history { display: grid; gap: 10px; margin-top: 16px; }.card-inspector__sources article, .card-inspector__history article { display: grid; gap: 4px; padding: 11px; border: 1px solid #edf2f7; border-radius: 6px; }.card-inspector__sources span, .card-inspector__history span, .card-inspector__history small { color: #64748b; font-size: 13px; overflow-wrap: anywhere; }.card-inspector__sources a { color: #047857; font-size: 13px; }.card-inspector__history article div { display: flex; justify-content: space-between; gap: 8px; }.card-inspector__empty { color: #64748b; font-size: 13px; }
.card-inspector__dialog-backdrop { position: fixed; inset: 0; z-index: 10001; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .42); }.card-inspector__dialog { width: min(100%, 460px); max-height: calc(100vh - 32px); overflow: auto; border-radius: 8px; background: #fff; padding: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .25); }.card-inspector__dialog h3, .card-inspector__dialog p { margin: 0; }.card-inspector__dialog p { margin-top: 8px; color: #64748b; line-height: 1.5; }.card-inspector__dialog > div:last-child { margin-top: 18px; }.card-inspector__dialog-actions { justify-content: flex-end; align-items: center; }
.card-inspector__dialog--conflict { width: min(100%, 840px); }.card-inspector__conflict-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }.card-inspector__conflict-columns section { min-width: 0; border: 1px solid #edf2f7; border-radius: 6px; padding: 10px; }.card-inspector__conflict-columns h4 { margin: 0 0 9px; color: #0f172a; font-size: 14px; }.card-inspector__conflict-columns dl { display: grid; gap: 4px; margin: 0; }.card-inspector__conflict-columns dt { color: #64748b; font-size: 12px; }.card-inspector__conflict-columns dd { margin: 0; overflow-wrap: anywhere; font-size: 13px; }.card-inspector__conflict-options { display: grid; gap: 8px; margin: 16px 0 0; border: 0; padding: 0; }.card-inspector__conflict-options legend { margin-bottom: 8px; font-weight: 800; }.card-inspector__conflict-options label { display: flex; gap: 8px; align-items: center; font-size: 13px; }.card-inspector__merge-fields { display: grid; gap: 8px; margin-top: 14px; }.card-inspector__merge-fields label { display: grid; grid-template-columns: minmax(0, 1fr) minmax(120px, 1fr); gap: 8px; align-items: center; font-size: 13px; }
@media (max-width: 620px) { .card-inspector { padding: 14px; }.card-inspector__header { align-items: start; flex-direction: column; }.card-inspector__back { width: 100%; }.card-inspector__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.card-inspector__actions button { width: 100%; padding-inline: 6px; }.card-inspector__conflict-columns { grid-template-columns: 1fr; }.card-inspector__merge-fields label { grid-template-columns: 1fr; } }
</style>
