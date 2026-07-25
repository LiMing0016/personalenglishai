<template>
  <section class="card-inspector" aria-label="单词卡详情">
    <div
      class="card-inspector__content"
      :inert="Boolean(activeDialog)"
      :aria-hidden="activeDialog ? 'true' : undefined"
    >
    <header class="card-inspector__header">
      <button
        ref="backButton"
        type="button"
        class="card-inspector__back"
        aria-label="返回单词库"
        title="返回单词库"
        :disabled="cardOperationPending"
        @click="emit('back')"
      >
        <ArrowLeft aria-hidden="true" />
      </button>
      <div class="card-inspector__heading">
        <div class="card-inspector__title-row">
          <h2>
            <button
              type="button"
              class="card-inspector__term-button"
              :aria-label="`播放 ${card.displayTerm} 的默认发音`"
              :disabled="pronunciationState === 'loading'"
              @click="playDefaultPronunciation"
            >
              {{ card.displayTerm }}
            </button>
          </h2>
          <button
            type="button"
            class="card-inspector__pronunciation-button"
            :class="{ 'is-playing': pronunciationState === 'playing' }"
            :aria-label="`播放 ${card.displayTerm} 的默认发音`"
            :aria-pressed="pronunciationState === 'playing'"
            :title="pronunciationState === 'playing' ? '正在播放发音' : '播放发音'"
            :disabled="pronunciationState === 'loading'"
            @click="playDefaultPronunciation"
          >
            <LoaderCircle v-if="pronunciationState === 'loading'" class="card-inspector__spinner" aria-hidden="true" />
            <Volume2 v-else aria-hidden="true" />
          </button>
        </div>
        <p v-if="headerPhonetic" class="card-inspector__phonetic">{{ headerPhonetic }}</p>
        <ul v-if="headerSenseSummaries.length" class="card-inspector__sense-list" aria-label="常见释义">
          <li v-for="summary in headerSenseSummaries" :key="`${summary.partOfSpeech}-${summary.meaning}`">
            <span class="card-inspector__part-of-speech">{{ summary.partOfSpeech }}</span>
            <span class="card-inspector__meaning" :title="summary.meaning">{{ summary.meaning }}</span>
          </li>
        </ul>
        <div class="card-inspector__metadata">
          <span :class="`card-inspector__status card-inspector__status--${card.status}`">{{ statusLabel(card.status) }}</span>
          <span>{{ card.theme?.name || '兼容卡片' }}<template v-if="card.themeVersion"> · v{{ card.themeVersion }}</template></span>
        </div>
      </div>
    </header>

    <div class="card-inspector__toolbar" aria-label="单词卡操作">
      <div class="card-inspector__mode" aria-label="阅读模式">
        <button type="button" :aria-pressed="!editing" :disabled="cardOperationPending" @click="cancelEditing">阅读</button>
        <button type="button" :aria-pressed="editing" :disabled="!hasReadableRevision || cardOperationPending" @click="startEditing">编辑</button>
      </div>
      <template v-if="editing">
        <button type="button" :disabled="cardOperationPending" @click="cancelEditing">取消</button>
        <button
          type="button"
          class="card-inspector__primary"
          :disabled="!card.activeRevisionUid || markdownTooLong || cardOperationPending"
          @click="save"
        >
          {{ updateMutation.isPending.value ? '保存中...' : '保存修改' }}
        </button>
      </template>
      <template v-else>
        <button
          v-if="!isPartialMarkdown"
          type="button"
          :disabled="!selectedTheme || cardOperationPending || themesBlockingError"
          @click="requestRegenerate"
        >
          {{ regenerateMutation.isPending.value ? '生成中...' : '重新生成' }}
        </button>
        <button v-if="showRetry" type="button" :disabled="cardOperationPending" @click="retry">
          {{ retryVocabularyCard.isPending.value ? '重试中...' : '重试生成' }}
        </button>
      </template>

      <template v-if="!isNarrow">
        <ThemeSelector @selected="closeMoreMenu" />
        <button type="button" class="card-inspector__danger" :disabled="cardOperationPending" @click="openDeleteDialog">删除</button>
      </template>
      <div v-else class="card-inspector__more">
        <button
          ref="moreButton"
          type="button"
          aria-label="更多单词卡操作"
          :aria-expanded="moreMenuOpen"
          aria-controls="vocabulary-card-more-menu"
          :disabled="cardOperationPending"
          @click="toggleMoreMenu"
        >
          更多
        </button>
        <div v-if="moreMenuOpen" id="vocabulary-card-more-menu" class="card-inspector__more-menu">
          <ThemeSelector @selected="closeMoreMenu" />
          <button type="button" class="card-inspector__danger" :disabled="cardOperationPending" @click="openDeleteDialog">删除</button>
        </div>
      </div>

      <nav v-if="navigation" class="card-inspector__sequence" aria-label="连续浏览单词卡">
        <span class="card-inspector__sequence-term card-inspector__sequence-term--previous">
          <template v-if="navigation.previous">上一张 · {{ navigation.previous.displayTerm }}</template>
        </span>
        <button
          type="button"
          class="card-inspector__sequence-button"
          :aria-label="navigation.previous ? `上一张：${navigation.previous.displayTerm}` : '上一张单词卡'"
          :title="navigation.previous ? `上一张：${navigation.previous.displayTerm}` : '上一张单词卡'"
          :disabled="navigationDisabled || !navigation.hasPrevious"
          @click="requestNavigation('previous')"
        >
          <ChevronLeft aria-hidden="true" />
        </button>
        <span class="card-inspector__sequence-position" aria-live="polite">{{ navigation.position }} / {{ navigation.total }}</span>
        <button
          type="button"
          class="card-inspector__sequence-button"
          :aria-label="navigation.next ? `下一张：${navigation.next.displayTerm}` : '下一张单词卡'"
          :title="navigation.next ? `下一张：${navigation.next.displayTerm}` : '下一张单词卡'"
          :disabled="navigationDisabled || !navigation.hasNext"
          @click="requestNavigation('next')"
        >
          <ChevronRight aria-hidden="true" />
        </button>
        <span class="card-inspector__sequence-term card-inspector__sequence-term--next">
          <template v-if="navigation.next">下一张 · {{ navigation.next.displayTerm }}</template>
        </span>
      </nav>
    </div>

    <div v-if="themesQuery.isLoading.value && !themesQuery.data.value" class="card-inspector__theme-state">主题加载中...</div>
    <div v-else-if="themesBlockingError" class="card-inspector__theme-state card-inspector__theme-state--error" role="alert">
      <span>主题加载失败</span>
      <button type="button" :disabled="themesQuery.isFetching.value" @click="themesQuery.refetch()">重新加载</button>
    </div>
    <p v-else-if="!activeThemes.length" class="card-inspector__theme-state">暂无可用主题</p>

    <div v-if="generationState" class="card-inspector__generation" role="status" aria-live="polite">
      <span>{{ generationState.text }}</span>
    </div>
    <div v-show="conflict && !conflictDialogOpen" class="card-inspector__conflict-action">
      <button type="button" :disabled="cardOperationPending" @click="openConflictDialog">处理冲突</button>
    </div>

    <div v-if="editing" class="card-inspector__editor-document">
      <VocabularyMarkdownEditor v-model="editMarkdown" />
    </div>
    <div v-else-if="showReadableDocument" class="card-inspector__notebook">
      <nav class="card-inspector__chapters" aria-label="单词卡章节">
        <button
          v-for="section in sections"
          :key="section.id"
          type="button"
          :aria-current="activeSectionId === section.id ? 'location' : undefined"
          @click="scrollToSection(section.id)"
        >
          {{ section.title }}
        </button>
      </nav>

      <main class="card-inspector__document">
        <section id="core-information" class="card-inspector__document-section">
          <VocabularyCoreSummary :core="displayCore" @pronounce="playPhonetic" />
        </section>

        <section v-if="isPartialMarkdown" class="card-inspector__partial-markdown" aria-label="主题内容">
          <h3>主题内容待完善</h3>
          <p>核心信息已保留，可以重新生成主题内容。</p>
          <button
            type="button"
            :disabled="!selectedTheme || cardOperationPending || themesBlockingError"
            @click="requestRegenerate"
          >
            重新生成
          </button>
        </section>
        <VocabularyCardBlocks
          v-else-if="card.cardBlocks"
          :card-blocks="card.cardBlocks"
          @sections-change="markdownSections = $event"
        />
        <VocabularyMarkdownRenderer
          v-else
          :markdown="cardMarkdown(card)"
          @sections-change="markdownSections = $event"
        />

        <section v-if="card.sources.length" id="card-sources" class="card-inspector__document-section card-inspector__sources" aria-label="单词卡来源">
          <h3>来源</h3>
          <article v-for="source in card.sources" :key="source.sourceUid">
            <strong>{{ source.sourceTitle || source.sourceType }}</strong>
            <span>{{ source.contextText || '未记录语境' }}</span>
            <a v-if="safeExternalUrl(source.sourceUrl)" :href="safeExternalUrl(source.sourceUrl)!" target="_blank" rel="noreferrer">查看原始来源</a>
          </article>
        </section>

        <section v-if="listVocabularyRevisions?.items.length" id="card-history" class="card-inspector__document-section card-inspector__history" aria-label="单词卡修订历史">
          <h3>历史</h3>
          <article v-for="revision in listVocabularyRevisions.items" :key="revision.revisionUid">
            <div><strong>{{ revision.authorType }}</strong><span>{{ revision.changeSummary || '无修改说明' }}</span></div>
            <small>{{ revision.createdAt || '时间未知' }}</small>
          </article>
        </section>
      </main>
    </div>
    <div v-else class="card-inspector__placeholder" aria-hidden="true"></div>
    </div>

    <p class="card-inspector__save-announcement sr-only" aria-live="polite">{{ saveAnnouncement }}</p>
    <p class="sr-only" aria-live="polite">{{ pronunciationMessage }}</p>

    <div v-if="regenerateConfirmationOpen" class="card-inspector__dialog-backdrop" role="presentation" @click.self="closeRegenerateDialog">
      <section ref="regenerateDialog" class="card-inspector__dialog" role="dialog" aria-modal="true" aria-labelledby="regenerate-card-title" aria-describedby="regenerate-card-guidance">
        <h3 id="regenerate-card-title">使用最新主题版本？</h3>
        <p id="regenerate-card-guidance">将使用主题最新版本重新生成，当前版本会保留在历史中。</p>
        <div class="card-inspector__dialog-actions">
          <button ref="regenerateInitialControl" type="button" :disabled="cardOperationPending" @click="closeRegenerateDialog">取消</button>
          <button type="button" :disabled="cardOperationPending" @click="regenerate">确认重新生成</button>
        </div>
      </section>
    </div>

    <div v-if="deleteDialogOpen" class="card-inspector__dialog-backdrop" role="presentation" @click.self="closeDeleteDialog">
      <section ref="deleteDialog" class="card-inspector__dialog" role="dialog" aria-modal="true" aria-labelledby="delete-card-title" aria-describedby="delete-card-guidance">
        <h3 id="delete-card-title">删除单词卡？</h3>
        <p id="delete-card-guidance">删除后会从单词卡列表移除；再次收藏或录入时可恢复，修订历史会保留。</p>
        <div class="card-inspector__dialog-actions">
          <button ref="deleteInitialControl" type="button" :disabled="cardOperationPending" @click="closeDeleteDialog">取消</button>
          <button type="button" class="card-inspector__danger" :disabled="cardOperationPending" @click="removeCard">
            {{ deleteMutation.isPending.value ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="conflict && conflictDialogOpen" class="card-inspector__dialog-backdrop" role="presentation">
      <section ref="conflictDialog" class="card-inspector__dialog card-inspector__dialog--conflict" role="dialog" aria-modal="true" aria-labelledby="conflict-card-title" aria-describedby="conflict-card-guidance">
        <h3 id="conflict-card-title">发现版本冲突</h3>
        <p id="conflict-card-guidance">{{ conflictGuidance }}</p>

        <div v-if="v1Conflict" class="card-inspector__conflict-columns">
          <section><h4>{{ cardBlocksConflict ? '当前主题内容' : '当前 Markdown' }}</h4><pre>{{ currentConflictPreview }}</pre></section>
          <section><h4>{{ cardBlocksConflict ? '候选主题内容' : '候选 Markdown' }}</h4><pre>{{ candidateConflictPreview }}</pre></section>
        </div>
        <div v-else class="card-inspector__conflict-columns">
          <section><h4>当前内容</h4><dl><template v-for="field in legacyConflictFields" :key="`current-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.currentContent, field) }}</dd></template></dl></section>
          <section><h4>AI 新版本</h4><dl><template v-for="field in legacyConflictFields" :key="`candidate-${field}`"><dt>{{ fieldLabel(field) }}</dt><dd>{{ displayValue(conflict.candidateContent, field) }}</dd></template></dl></section>
        </div>

        <fieldset class="card-inspector__conflict-options" :disabled="cardOperationPending">
          <legend>解决方式</legend>
          <label><input ref="conflictInitialControl" v-model="conflictChoice" type="radio" value="keep_current">保留当前内容</label>
          <label><input v-model="conflictChoice" type="radio" value="use_ai">使用 AI 新版本</label>
          <label><input v-model="conflictChoice" type="radio" value="merge_fields">{{ v1Conflict ? cardBlocksConflict ? '组合核心数据与主题内容' : '组合核心数据与 Markdown' : '逐字段合并' }}</label>
        </fieldset>
        <div v-if="conflictChoice === 'merge_fields'" class="card-inspector__merge-fields">
          <label v-for="field in mergeableConflictFields" :key="field">
            <span>{{ fieldLabel(field) }}</span>
            <select v-model="mergeChoice[field]" :aria-label="`合并${fieldLabel(field)}`" :disabled="cardOperationPending">
              <option value="current">当前内容</option>
              <option value="candidate">AI 新版本</option>
            </select>
          </label>
        </div>
        <div class="card-inspector__dialog-actions">
          <button type="button" :disabled="cardOperationPending" @click="closeConflictDialog">取消</button>
          <button type="button" :disabled="cardOperationPending" @click="resolveConflict">确认处理</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { ArrowLeft, ChevronLeft, ChevronRight, LoaderCircle, Volume2 } from 'lucide-vue-next'
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

import type { MarkdownSection } from '../assistant/markdown'
import VocabularyCardBlocks from './VocabularyCardBlocks.vue'
import VocabularyCoreSummary from './VocabularyCoreSummary.vue'
import VocabularyMarkdownEditor from './VocabularyMarkdownEditor.vue'
import VocabularyMarkdownRenderer from './VocabularyMarkdownRenderer.vue'
import { buildVocabularyCardSections } from './vocabularyCardSections'
import {
  VocabularyConflictError,
  type RegenerateVocabularyCardRequest,
  type ResolveVocabularyConflictRequest,
  type UpdateVocabularyCardRequest,
  type VocabularyCardDetail,
  type VocabularyCardBlocks as VocabularyCardBlocksContent,
  type VocabularyCardStatus,
  type VocabularyConflictResponse,
  type VocabularyCoreContent,
  type VocabularyRevision,
  type VocabularyRevisionListResponse,
} from '@/api/vocabulary'
import { useVocabularyThemes } from '@/composables/useVocabularyThemes'
import { useVocabularyPronunciation } from '@/composables/useVocabularyPronunciation'
import {
  projectLegacyVocabularyCore,
  isVocabularyV1Revision,
  selectVocabularyThemeUid,
  shouldResetVocabularyCardDraft,
  type VocabularyCardDraftIdentity,
} from '@/composables/useVocabularyCards'
import { safeExternalUrl } from '@/features/vocabulary/safeExternalUrl'
import { buildVocabularyHeaderSenseSummaries } from '@/features/vocabulary/vocabularyCardHeader'
import type { VocabularyCardSequence } from '@/features/vocabulary/vocabularyCardNavigation'
import { vocabularyCardBlocksToMarkdown } from '@/features/vocabulary/vocabularyLearningMarkdown'
import { showToast } from '@/utils/toast'

type MutationBridge<T, TResult = unknown> = { isPending: Ref<boolean>, mutateAsync: (payload: T) => Promise<TResult> }
type MergeChoice = Record<string, 'current' | 'candidate'>
type InspectorDialog = 'regenerate' | 'delete' | 'conflict'

const props = defineProps<{
  card: VocabularyCardDetail
  navigation?: VocabularyCardSequence | null
  navigationPending?: boolean
  listVocabularyRevisions?: VocabularyRevisionListResponse
  updateMutation: MutationBridge<{ cardUid: string, payload: UpdateVocabularyCardRequest }, VocabularyCardDetail>
  deleteMutation: MutationBridge<string>
  regenerateMutation: MutationBridge<{ cardUid: string } & RegenerateVocabularyCardRequest>
  retryVocabularyCard: MutationBridge<string>
  resolveConflictMutation: MutationBridge<{ cardUid: string, revisionUid: string, payload: ResolveVocabularyConflictRequest }>
}>()

const emit = defineEmits<{
  back: []
  navigate: [direction: 'previous' | 'next']
}>()
const { themesQuery } = useVocabularyThemes()
const {
  state: pronunciationState,
  message: pronunciationMessage,
  play: playPronunciation,
  stop: stopPronunciation,
} = useVocabularyPronunciation()
const isNarrow = useMediaQuery('(max-width: 767px)')
const editing = ref(false)
const editMarkdown = ref('')
const selectedThemeUid = ref('')
const regenerateConfirmationOpen = ref(false)
const deleteDialogOpen = ref(false)
const moreMenuOpen = ref(false)
const moreButton = ref<HTMLButtonElement | null>(null)
const backButton = ref<HTMLButtonElement | null>(null)
const regenerateDialog = ref<HTMLElement | null>(null)
const regenerateInitialControl = ref<HTMLButtonElement | null>(null)
const deleteDialog = ref<HTMLElement | null>(null)
const deleteInitialControl = ref<HTMLButtonElement | null>(null)
const conflictDialog = ref<HTMLElement | null>(null)
const conflictInitialControl = ref<HTMLInputElement | null>(null)
const saveAnnouncement = ref('')
const conflict = ref<VocabularyConflictResponse | null>(null)
const conflictDialogOpen = ref(false)
const conflictChoice = ref<ResolveVocabularyConflictRequest['choice']>('keep_current')
const mergeChoice = ref<MergeChoice>({})
const draftIdentity = ref<VocabularyCardDraftIdentity>()
const markdownSections = ref<MarkdownSection[]>([])
const activeSectionId = ref('core-information')
const operationInFlight = ref(false)
let observer: IntersectionObserver | undefined
let dialogReturnTarget: HTMLElement | null = null
let focusLifecycleToken = 0
let componentMounted = false
const intersectingSectionIds = new Set<string>()

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
const headerPhonetic = computed(() => displayCore.value.phonetics[0]?.text.trim() || props.card.phonetic?.trim() || '')
const headerSenseSummaries = computed(() => buildVocabularyHeaderSenseSummaries(displayCore.value))
const hasReadableRevision = computed(() => Boolean(props.card.activeRevisionUid))
const markdownTooLong = computed(() => editMarkdown.value.length > 20_000)
const cardOperationPending = computed(() => (
  operationInFlight.value
  || props.updateMutation.isPending.value
  || props.regenerateMutation.isPending.value
  || props.retryVocabularyCard.isPending.value
  || props.resolveConflictMutation.isPending.value
  || props.deleteMutation.isPending.value
))
const activeDialog = computed<InspectorDialog | null>(() => {
  if (conflict.value && conflictDialogOpen.value) return 'conflict'
  if (regenerateConfirmationOpen.value) return 'regenerate'
  if (deleteDialogOpen.value) return 'delete'
  return null
})
const navigationDisabled = computed(() => (
  editing.value
  || cardOperationPending.value
  || Boolean(activeDialog.value)
  || Boolean(props.navigationPending)
))
const isPartialMarkdown = computed(() => (
  props.card.generationOutcome === 'partial'
  && (props.card.warning === 'card_blocks_unavailable' || props.card.warning === 'markdown_unavailable')
))
const showRetry = computed(() => (
  props.card.status === 'failed'
  || props.card.generationStatus === 'failed'
  || props.card.generationOutcome === 'failed'
  || Boolean(props.card.generationError)
))
const generationState = computed(() => {
  const generating = props.card.status === 'captured'
    || props.card.status === 'generating'
    || props.card.generationStatus === 'pending'
    || props.card.generationStatus === 'running'
  if (generating) {
    return hasReadableRevision.value
      ? { text: '正在生成新版本，当前内容可继续阅读' }
      : { text: '正在生成单词卡' }
  }
  if (props.card.status === 'needs_review' && props.card.candidateRevisionUid) {
    return { text: '发现待确认的新版本' }
  }
  if (props.card.generationOutcome === 'partial'
      && (props.card.warning === 'card_blocks_unavailable' || props.card.warning === 'markdown_unavailable')) {
    return { text: '主题内容待完善' }
  }
  if (props.card.generationOutcome === 'failed'
      || props.card.status === 'failed'
      || props.card.generationStatus === 'failed'
      || props.card.generationError) {
    return hasReadableRevision.value
      ? { text: '本次生成失败，当前内容未受影响' }
      : { text: '暂时没有可阅读的卡片内容' }
  }
  return null
})
const showReadableDocument = computed(() => hasReadableRevision.value || isPartialMarkdown.value)
const sections = computed(() => buildVocabularyCardSections(
  markdownSections.value,
  props.card.sources.length > 0,
  Boolean(props.listVocabularyRevisions?.items.length),
))
const renderedSectionIds = computed(() => (
  !editing.value && showReadableDocument.value ? sections.value.map((section) => section.id) : []
))
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

const ThemeSelector = defineComponent({
  emits: ['selected'],
  setup(_, { emit: emitSelection }) {
    return () => h('label', { class: 'card-inspector__regenerate-theme' }, [
      h('span', '主题'),
      h('select', {
        value: selectedThemeUid.value,
        'aria-label': '重新生成主题',
        disabled: !activeThemes.value.length || themesQuery.isLoading.value || cardOperationPending.value,
        onChange: (event: Event) => {
          if (cardOperationPending.value) return
          selectedThemeUid.value = (event.target as HTMLSelectElement).value
          emitSelection('selected')
        },
      }, activeThemes.value.map((theme) => h('option', { key: theme.themeUid, value: theme.themeUid }, theme.name))),
    ])
  },
})

function cardMarkdown(card: VocabularyCardDetail): string {
  if (card.markdown != null) return card.markdown
  const compatibleMarkdown = asRecord(card.content).markdown
  return typeof compatibleMarkdown === 'string' ? compatibleMarkdown : ''
}

function cardLearningMarkdown(card: VocabularyCardDetail): string {
  const blocksMarkdown = vocabularyCardBlocksToMarkdown(card.cardBlocks)
  return blocksMarkdown || cardMarkdown(card).trim()
}

function pronunciationLanguage(region: VocabularyCoreContent['phonetics'][number]['region']) {
  return region === 'us' ? 'en-US' : 'en-GB'
}

function playPhonetic(phonetic: VocabularyCoreContent['phonetics'][number]) {
  void playPronunciation({
    term: props.card.displayTerm,
    language: pronunciationLanguage(phonetic.region),
    audioUrl: phonetic.audioUrl,
  })
}

function playDefaultPronunciation() {
  const phonetics = displayCore.value.phonetics
  const preferred = phonetics.find((phonetic) => Boolean(phonetic.audioUrl)) ?? phonetics[0]
  if (preferred) {
    playPhonetic(preferred)
    return
  }
  void playPronunciation({ term: props.card.displayTerm, language: 'en-GB', audioUrl: null })
}

function requestNavigation(direction: 'previous' | 'next') {
  if (navigationDisabled.value) return
  emit('navigate', direction)
}

function scrollToSection(id: string) {
  activeSectionId.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function disconnectObserver() {
  observer?.disconnect()
  observer = undefined
  intersectingSectionIds.clear()
}

function rebuildObserver() {
  disconnectObserver()
  if (typeof window === 'undefined' || typeof document === 'undefined' || typeof IntersectionObserver === 'undefined') return
  const elements = renderedSectionIds.value
    .map((id) => document.getElementById(id))
    .filter((element): element is HTMLElement => Boolean(element))
  if (!elements.length) return
  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) intersectingSectionIds.add(entry.target.id)
      else intersectingSectionIds.delete(entry.target.id)
    })
    const closestSection = [...intersectingSectionIds]
      .map((id) => document.getElementById(id))
      .filter((element): element is HTMLElement => Boolean(element))
      .sort((left, right) => Math.abs(left.getBoundingClientRect().top) - Math.abs(right.getBoundingClientRect().top))[0]
    if (closestSection) activeSectionId.value = closestSection.id
  }, { rootMargin: '-96px 0px -55% 0px', threshold: [0, 0.1, 0.5] })
  elements.forEach((element) => observer?.observe(element))
}

function closeMoreMenu() {
  moreMenuOpen.value = false
}

function toggleMoreMenu() {
  if (cardOperationPending.value || activeDialog.value) return
  moreMenuOpen.value = !moreMenuOpen.value
}

function eventCurrentTarget(event?: Event): HTMLElement | null {
  return event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
}

function openDeleteDialog(event?: Event) {
  if (cardOperationPending.value || activeDialog.value) return
  closeMoreMenu()
  dialogReturnTarget = eventCurrentTarget(event) ?? backButton.value
  deleteDialogOpen.value = true
}

function closeRegenerateDialog() {
  if (cardOperationPending.value) return
  regenerateConfirmationOpen.value = false
}

function closeDeleteDialog() {
  if (cardOperationPending.value) return
  deleteDialogOpen.value = false
}

function closeConflictDialog() {
  if (cardOperationPending.value) return
  conflictDialogOpen.value = false
}

function openConflictDialog(event?: Event) {
  if (!conflict.value || cardOperationPending.value || activeDialog.value) return
  dialogReturnTarget = eventCurrentTarget(event) ?? backButton.value
  conflictDialogOpen.value = true
}

function closeActiveDialog() {
  if (cardOperationPending.value) return
  if (activeDialog.value === 'regenerate') closeRegenerateDialog()
  else if (activeDialog.value === 'delete') closeDeleteDialog()
  else if (activeDialog.value === 'conflict') closeConflictDialog()
}

function activeDialogElement(): HTMLElement | null {
  if (activeDialog.value === 'regenerate') return regenerateDialog.value
  if (activeDialog.value === 'delete') return deleteDialog.value
  if (activeDialog.value === 'conflict') return conflictDialog.value
  return null
}

function activeDialogInitialControl(): HTMLElement | null {
  if (activeDialog.value === 'regenerate') return regenerateInitialControl.value
  if (activeDialog.value === 'delete') return deleteInitialControl.value
  if (activeDialog.value === 'conflict') return conflictInitialControl.value
  return null
}

function canReceiveFocus(target: HTMLElement | null): target is HTMLElement {
  return Boolean(target?.isConnected
    && !target.hasAttribute('disabled')
    && target.getClientRects().length > 0)
}

async function focusActiveDialog() {
  const lifecycleToken = ++focusLifecycleToken
  if (!componentMounted || cardOperationPending.value || !activeDialog.value) return
  await nextTick()
  if (!componentMounted
      || lifecycleToken !== focusLifecycleToken
      || cardOperationPending.value
      || !activeDialog.value) return
  const initialControl = activeDialogInitialControl()
  if (canReceiveFocus(initialControl)) initialControl.focus()
}

async function restoreDialogFocus() {
  const lifecycleToken = ++focusLifecycleToken
  if (!componentMounted || activeDialog.value || cardOperationPending.value || !dialogReturnTarget) return
  await nextTick()
  if (!componentMounted
      || lifecycleToken !== focusLifecycleToken
      || activeDialog.value
      || cardOperationPending.value
      || !dialogReturnTarget) return
  const preferredTarget = dialogReturnTarget
  const target = canReceiveFocus(preferredTarget)
    ? preferredTarget
    : backButton.value
  if (!canReceiveFocus(target)) return
  target.focus()
  if (document.activeElement === target) dialogReturnTarget = null
}

function trapDialogFocus(event: KeyboardEvent) {
  const dialog = activeDialogElement()
  if (!dialog) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeActiveDialog()
    return
  }
  if (event.key !== 'Tab') return
  const focusable = [...dialog.querySelectorAll<HTMLElement>(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
  )].filter((element) => element.getClientRects().length > 0)
  if (!focusable.length) {
    event.preventDefault()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const current = document.activeElement
  if (event.shiftKey && (current === first || !dialog.contains(current))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (current === last || !dialog.contains(current))) {
    event.preventDefault()
    first.focus()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (activeDialog.value) {
    trapDialogFocus(event)
    return
  }
  if (event.key !== 'Escape' || !moreMenuOpen.value) return
  closeMoreMenu()
  void nextTick(() => moreButton.value?.focus())
}

function startEditing() {
  if (!hasReadableRevision.value || cardOperationPending.value) return
  editMarkdown.value = cardLearningMarkdown(props.card)
  editing.value = true
  closeMoreMenu()
}

function revisionFor(revisionUid: string | null): VocabularyRevision | undefined {
  return props.listVocabularyRevisions?.items.find((revision) => revision.revisionUid === revisionUid)
}

function isCoreContent(value: unknown): value is VocabularyCoreContent {
  const record = asRecord(value)
  return (record.schemaVersion === 1 || record.schemaVersion === 2)
    && Array.isArray(record.phonetics)
    && Array.isArray(record.senses)
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
function conflictCardBlocks(revisionUid: string | null, content: unknown): VocabularyCardBlocksContent | null {
  const revisionBlocks = revisionFor(revisionUid)?.cardBlocks
  if (revisionBlocks) return revisionBlocks
  const compatibleBlocks = asRecord(content).cardBlocks
  const record = asRecord(compatibleBlocks)
  return record.schemaVersion === 1 && Array.isArray(record.blocks)
    ? compatibleBlocks as VocabularyCardBlocksContent
    : null
}
const currentConflictCardBlocks = computed(() => conflictCardBlocks(
  conflict.value?.currentRevisionUid ?? null,
  conflict.value?.currentContent,
))
const candidateConflictCardBlocks = computed(() => conflictCardBlocks(
  conflict.value?.candidateRevisionUid ?? null,
  conflict.value?.candidateContent,
))
const cardBlocksConflict = computed(() => Boolean(currentConflictCardBlocks.value || candidateConflictCardBlocks.value))
const currentConflictPreview = computed(() => cardBlocksConflict.value
  ? JSON.stringify(currentConflictCardBlocks.value, null, 2) || '暂无主题内容'
  : currentConflictMarkdown.value || '暂无 Markdown 内容')
const candidateConflictPreview = computed(() => cardBlocksConflict.value
  ? JSON.stringify(candidateConflictCardBlocks.value, null, 2) || '暂无主题内容'
  : candidateConflictMarkdown.value || '暂无 Markdown 内容')
const conflictGuidance = computed(() => {
  if (!v1Conflict.value) return '请先比较当前内容与 AI 新版本，再选择保留当前内容、使用 AI 新版本或逐字段合并，然后确认处理。'
  return cardBlocksConflict.value
    ? '请整体比较当前与候选主题内容，再选择保留当前内容、使用 AI 新版本或组合内容，然后确认处理。'
    : '请整体比较当前与候选 Markdown，再选择保留当前内容、使用 AI 新版本或组合内容，然后确认处理。'
})
const legacyConflictFields = computed(() => {
  const keys = new Set([
    ...Object.keys(asRecord(conflict.value?.currentContent)),
    ...Object.keys(asRecord(conflict.value?.candidateContent)),
  ])
  return [...keys].filter((field) => field !== 'markdown')
})
const legacyMergeableFields = computed(() => legacyConflictFields.value.filter((field) => field !== 'term'))
const mergeableConflictFields = computed(() => v1Conflict.value
  ? ['core', cardBlocksConflict.value ? 'cardBlocks' : 'markdown']
  : legacyMergeableFields.value)

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
    core: '核心词典数据', cardBlocks: '主题内容', markdown: 'Markdown',
  } as Record<string, string>)[field] ?? field
}

function displayValue(content: unknown, field: string) {
  const value = asRecord(content)[field]
  return Array.isArray(value) ? value.join('；') : value == null ? '未填写' : String(value)
}

function resetMergeChoice() {
  mergeChoice.value = Object.fromEntries(mergeableConflictFields.value.map((field) => [field, 'current']))
}

function setConflict(nextConflict: VocabularyConflictResponse, returnTarget?: HTMLElement | null) {
  regenerateConfirmationOpen.value = false
  deleteDialogOpen.value = false
  closeMoreMenu()
  dialogReturnTarget = returnTarget ?? backButton.value
  conflict.value = nextConflict
  conflictDialogOpen.value = true
  conflictChoice.value = 'keep_current'
  resetMergeChoice()
  void focusActiveDialog()
}

watch(
  () => [props.card.cardUid, props.card.activeRevisionUid] as const,
  ([cardUid, activeRevisionUid]) => {
    closeMoreMenu()
    const nextIdentity = { cardUid, activeRevisionUid }
    if (!shouldResetVocabularyCardDraft(draftIdentity.value, nextIdentity)) return
    const cardChanged = draftIdentity.value?.cardUid !== cardUid
    if (cardChanged) {
      stopPronunciation()
      focusLifecycleToken += 1
      dialogReturnTarget = null
      conflict.value = null
      conflictDialogOpen.value = false
    }
    draftIdentity.value = nextIdentity
    editMarkdown.value = cardLearningMarkdown(props.card)
    markdownSections.value = []
    activeSectionId.value = 'core-information'
    if (cardChanged) {
      selectedThemeUid.value = selectVocabularyThemeUid(
        activeThemes.value,
        themesQuery.data.value?.defaultThemeUid ?? '',
        props.card.theme?.themeUid,
      )
    }
    editing.value = false
    regenerateConfirmationOpen.value = false
    deleteDialogOpen.value = false
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
      conflictDialogOpen.value = false
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
watch(isNarrow, (narrow) => { if (!narrow) closeMoreMenu() })
watch([activeDialog, cardOperationPending], ([nextDialog, operationPending], [previousDialog]) => {
  if (nextDialog) {
    dialogReturnTarget ??= backButton.value
    if (!operationPending) void focusActiveDialog()
  } else if (previousDialog || dialogReturnTarget) {
    if (operationPending) return
    void restoreDialogFocus()
  }
}, { flush: 'post' })
watch(
  () => renderedSectionIds.value.join('|'),
  async () => {
    await nextTick()
    rebuildObserver()
  },
  { flush: 'post' },
)

onMounted(() => {
  componentMounted = true
  window.addEventListener('keydown', handleKeydown)
  rebuildObserver()
  if (activeDialog.value) {
    dialogReturnTarget ??= backButton.value
    if (!cardOperationPending.value) void focusActiveDialog()
  }
})

onBeforeUnmount(() => {
  stopPronunciation()
  componentMounted = false
  focusLifecycleToken += 1
  disconnectObserver()
  window.removeEventListener('keydown', handleKeydown)
  dialogReturnTarget = null
})

function cancelEditing() {
  if (cardOperationPending.value) return
  editMarkdown.value = cardLearningMarkdown(props.card)
  editing.value = false
}

async function runCardOperation(operation: () => Promise<void>): Promise<boolean> {
  if (cardOperationPending.value) return false
  operationInFlight.value = true
  try {
    await operation()
    return true
  } finally {
    operationInFlight.value = false
  }
}

async function save(event?: Event) {
  if (!props.card.activeRevisionUid || markdownTooLong.value || cardOperationPending.value) return
  const conflictReturnTarget = eventCurrentTarget(event)
  saveAnnouncement.value = ''
  await runCardOperation(async () => {
    try {
      const payload: UpdateVocabularyCardRequest = {
        baseRevisionUid: props.card.activeRevisionUid!,
        core: { ...displayCore.value, term: props.card.normalizedTerm },
        markdown: editMarkdown.value,
        changeSummary: '用户编辑学习内容',
      }
      const savedCard = await props.updateMutation.mutateAsync({
        cardUid: props.card.cardUid,
        payload,
      })
      editMarkdown.value = cardLearningMarkdown(savedCard)
      draftIdentity.value = {
        cardUid: savedCard.cardUid,
        activeRevisionUid: savedCard.activeRevisionUid,
      }
      editing.value = false
      saveAnnouncement.value = '单词卡已保存'
      showToast('单词卡已保存', 'success')
    } catch (error) {
      saveAnnouncement.value = '保存失败，请重试'
      if (error instanceof VocabularyConflictError) {
        setConflict(error.conflict, conflictReturnTarget)
        return
      }
      showToast(error instanceof Error ? error.message : '保存失败，请重试', 'error')
    }
  })
}

function requestRegenerate(event?: Event) {
  if (cardOperationPending.value || activeDialog.value) return
  closeMoreMenu()
  if (!selectedTheme.value) return
  if (regenerateNeedsConfirmation.value) {
    dialogReturnTarget = eventCurrentTarget(event) ?? backButton.value
    regenerateConfirmationOpen.value = true
    return
  }
  void regenerate()
}

async function regenerate() {
  if (!selectedTheme.value || cardOperationPending.value) return
  await runCardOperation(async () => {
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
  })
}

async function retry() {
  if (cardOperationPending.value) return
  await runCardOperation(async () => {
    try {
      await props.retryVocabularyCard.mutateAsync(props.card.cardUid)
      showToast('已提交重试任务', 'success')
    } catch (error) { showToast(error instanceof Error ? error.message : '重试失败，请重试', 'error') }
  })
}

async function removeCard() {
  if (cardOperationPending.value) return
  await runCardOperation(async () => {
    try {
      await props.deleteMutation.mutateAsync(props.card.cardUid)
      deleteDialogOpen.value = false
      emit('back')
      showToast('单词卡已删除', 'success')
    } catch (error) { showToast(error instanceof Error ? error.message : '删除失败，请重试', 'error') }
  })
}

function conflictMergeFields(): Record<string, unknown> {
  if (!conflict.value) return {}
  if (v1Conflict.value) {
    const currentCore = conflictCore(conflict.value.currentRevisionUid, conflict.value.currentContent)
    const candidateCore = conflictCore(conflict.value.candidateRevisionUid, conflict.value.candidateContent)
    if (cardBlocksConflict.value) {
      return {
        core: mergeChoice.value.core === 'candidate' ? candidateCore : currentCore,
        cardBlocks: mergeChoice.value.cardBlocks === 'candidate'
          ? candidateConflictCardBlocks.value
          : currentConflictCardBlocks.value,
      }
    }
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
  if (!conflict.value?.candidateRevisionUid || cardOperationPending.value) return
  const mergeFields = conflictMergeFields()
  const revisionUid = conflict.value.candidateRevisionUid
  await runCardOperation(async () => {
    try {
      await props.resolveConflictMutation.mutateAsync({
        cardUid: props.card.cardUid,
        revisionUid,
        payload: conflictChoice.value === 'merge_fields'
          ? { choice: 'merge_fields', mergeFields }
          : { choice: conflictChoice.value },
      })
      conflict.value = null
      conflictDialogOpen.value = false
      editing.value = false
      showToast('冲突已处理', 'success')
    } catch (error) { showToast(error instanceof Error ? error.message : '冲突处理失败，请重试', 'error') }
  })
}

function statusLabel(status: VocabularyCardStatus) {
  return ({ captured: '已收下', generating: '正在生成', ready: '已就绪', needs_review: '待确认', failed: '生成失败' } as Record<VocabularyCardStatus, string>)[status]
}
</script>

<style scoped>
.card-inspector { min-width: 0; padding: 22px clamp(16px, 3vw, 40px) 56px; background: #fff; color: #334155; }
.card-inspector__header { min-width: 0; display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: 12px; max-width: 1060px; margin: 0 auto; }
.card-inspector__heading { min-width: 0; }
.card-inspector__title-row { min-width: 0; display: flex; align-items: center; gap: 10px; }
.card-inspector__heading h2 { min-width: 0; margin: 0; color: #0f172a; font-size: 28px; line-height: 1.2; overflow-wrap: anywhere; }
.card-inspector .card-inspector__term-button { min-width: 0; min-height: 0; border: 0; background: transparent; color: inherit; font-size: inherit; line-height: inherit; padding: 0; text-align: left; overflow-wrap: anywhere; }
.card-inspector .card-inspector__term-button:hover { color: #047857; }
.card-inspector .card-inspector__term-button:focus-visible { outline: 2px solid #10b981; outline-offset: 4px; }
.card-inspector .card-inspector__pronunciation-button { width: 38px; height: 38px; min-height: 38px; display: inline-grid; flex: none; place-items: center; border-color: #a7f3d0; border-radius: 50%; background: #ecfdf5; color: #047857; padding: 0; }
.card-inspector .card-inspector__pronunciation-button:hover, .card-inspector .card-inspector__pronunciation-button.is-playing { border-color: #10b981; background: #d1fae5; }
.card-inspector__pronunciation-button svg { width: 18px; height: 18px; }
.card-inspector__spinner { animation: card-inspector-spin .8s linear infinite; }
.card-inspector__phonetic { margin: 6px 0 0; color: #64748b; font-size: 14px; overflow-wrap: anywhere; }
.card-inspector__sense-list { min-width: 0; display: flex; flex-wrap: wrap; gap: 6px 18px; margin: 10px 0 0; padding: 0; list-style: none; }
.card-inspector__sense-list li { min-width: 0; max-width: min(100%, 560px); display: inline-flex; align-items: baseline; gap: 8px; }
.card-inspector__part-of-speech { flex: none; border: 1px solid #dce7e1; border-radius: 4px; background: #f8fafc; color: #475569; padding: 2px 6px; font-size: 11px; font-weight: 800; line-height: 1.35; }
.card-inspector__meaning { min-width: 0; display: -webkit-box; overflow: hidden; color: #334155; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.card-inspector__metadata { min-width: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-top: 9px; color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.card-inspector__status { color: #047857; font-weight: 800; }
.card-inspector__status--failed { color: #b91c1c; }
.card-inspector__status--needs_review { color: #b45309; }
.card-inspector button { box-sizing: border-box; min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; font-weight: 700; padding: 0 10px; cursor: pointer; }
.card-inspector button:disabled { cursor: not-allowed; opacity: .55; }
.card-inspector__back { display: inline-grid; place-items: center; width: 34px; height: 34px; padding: 0; }
.card-inspector__back svg { width: 18px; height: 18px; }
.card-inspector__toolbar { position: sticky; top: 0; z-index: 8; min-width: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; max-width: 1060px; margin: 18px auto 0; padding: 10px 0; border-bottom: 1px solid #dce7e1; background: rgba(255, 255, 255, .96); }
.card-inspector__mode { display: inline-grid; grid-template-columns: repeat(2, minmax(56px, 1fr)); border: 1px solid #dce7e1; border-radius: 6px; overflow: hidden; }
.card-inspector__mode button { min-width: 56px; border: 0; border-radius: 0; }
.card-inspector__mode button + button { border-left: 1px solid #dce7e1; }
.card-inspector__mode button[aria-pressed="true"] { background: #ecfdf5; color: #047857; }
.card-inspector__primary, .card-inspector__dialog-actions button:last-child { border-color: #059669 !important; background: #059669 !important; color: #fff !important; }
.card-inspector__danger { border-color: #fecaca !important; background: #fff !important; color: #b91c1c !important; }
.card-inspector__regenerate-theme { min-width: 0; display: flex; align-items: center; gap: 6px; color: #64748b; font-size: 12px; }
.card-inspector__regenerate-theme select { box-sizing: border-box; max-width: 220px; min-width: 0; min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; padding: 0 8px; }
.card-inspector__more { position: relative; margin-left: auto; }
.card-inspector__more-menu { position: absolute; top: calc(100% + 6px); right: 0; z-index: 10; min-width: min(280px, calc(100vw - 32px)); display: grid; gap: 10px; padding: 12px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; box-shadow: 0 12px 26px rgba(15, 23, 42, .14); }
.card-inspector__more-menu .card-inspector__regenerate-theme { display: grid; }
.card-inspector__more-menu select, .card-inspector__more-menu button { width: 100%; max-width: none; }
.card-inspector__sequence { min-width: 0; display: grid; grid-template-columns: minmax(0, 120px) 34px auto 34px minmax(0, 120px); align-items: center; gap: 6px; margin-left: auto; padding-left: 14px; border-left: 1px solid #dce7e1; }
.card-inspector .card-inspector__sequence-button { width: 34px; height: 34px; min-height: 34px; display: inline-grid; place-items: center; padding: 0; }
.card-inspector__sequence-button svg { width: 17px; height: 17px; }
.card-inspector__sequence-position { min-width: 52px; color: #475569; font-size: 12px; font-weight: 800; text-align: center; }
.card-inspector__sequence-term { min-width: 0; overflow: hidden; color: #64748b; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.card-inspector__sequence-term--previous { text-align: right; }
.card-inspector__sequence-term--next { text-align: left; }
.card-inspector__theme-state, .card-inspector__generation { min-width: 0; max-width: 1060px; display: flex; align-items: center; gap: 8px; margin: 10px auto 0; color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.card-inspector__conflict-action { min-width: 0; max-width: 1060px; margin: 10px auto 0; }
.card-inspector__theme-state--error { color: #b91c1c; }
.card-inspector__generation { padding: 10px 12px; border-left: 3px solid #34d399; background: #f0fdf4; color: #065f46; }
.card-inspector__notebook { min-width: 0; max-width: 1060px; margin: 28px auto 0; }
.card-inspector__chapters { min-width: 0; }
.card-inspector__chapters button { display: block; width: 100%; min-width: 0; border: 0; background: transparent; color: #64748b; text-align: left; overflow-wrap: anywhere; }
.card-inspector__chapters button[aria-current="location"] { color: #047857; background: #ecfdf5; }
.card-inspector__document, .card-inspector__editor-document { min-width: 0; width: 100%; }
.card-inspector__document { max-width: 840px; margin: 0 auto; }
.card-inspector__editor-document { max-width: none; margin: 22px auto 0; }
.card-inspector__document-section { min-width: 0; scroll-margin-top: 96px; }
.card-inspector__document :deep([id^="markdown-section-"]) { scroll-margin-top: 96px; }
.card-inspector__document-section + .card-inspector__document-section, .card-inspector__sources, .card-inspector__history { margin-top: 40px; padding-top: 26px; border-top: 1px solid #dce7e1; }
.card-inspector__partial-markdown { min-width: 0; margin-top: 32px; padding: 16px 0; border-top: 1px solid #dce7e1; }
.card-inspector__partial-markdown h3, .card-inspector__partial-markdown p { margin: 0; overflow-wrap: anywhere; }
.card-inspector__partial-markdown p { margin-top: 6px; color: #64748b; }
.card-inspector__partial-markdown button { margin-top: 12px; }
.card-inspector__sources h3, .card-inspector__history h3 { margin: 0 0 14px; color: #0f172a; font-size: 20px; }
.card-inspector__sources article, .card-inspector__history article { min-width: 0; display: grid; gap: 5px; padding: 12px 0; border-bottom: 1px solid #edf2f7; }
.card-inspector__sources span, .card-inspector__history span, .card-inspector__history small { color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.card-inspector__sources a { color: #047857; font-size: 13px; overflow-wrap: anywhere; word-break: break-all; }
.card-inspector__history article div { min-width: 0; display: flex; justify-content: space-between; gap: 8px; }
.card-inspector__placeholder { min-width: 0; max-width: 840px; min-height: 220px; display: grid; place-items: center; margin: 28px auto 0; border-top: 1px solid #dce7e1; color: #64748b; text-align: center; overflow-wrap: anywhere; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.card-inspector__dialog-backdrop { position: fixed; inset: 0; z-index: 10001; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .42); }
.card-inspector__dialog { box-sizing: border-box; width: min(100%, 460px); max-height: calc(100vh - 32px); overflow: auto; border-radius: 6px; background: #fff; padding: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .25); }
.card-inspector__dialog h3, .card-inspector__dialog p { margin: 0; overflow-wrap: anywhere; }
.card-inspector__dialog > p { margin-top: 8px; color: #64748b; line-height: 1.5; }
.card-inspector__dialog-actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 18px; }
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

@keyframes card-inspector-spin { to { transform: rotate(360deg); } }

@media (min-width: 1024px) {
  .card-inspector__notebook { display: grid; grid-template-columns: 180px minmax(0, 840px); gap: 40px; align-items: start; }
  .card-inspector__chapters { position: sticky; top: 86px; display: grid; gap: 4px; }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .card-inspector__sequence { grid-template-columns: 34px auto 34px; }
  .card-inspector__sequence-term { display: none; }
  .card-inspector__notebook { display: grid; grid-template-columns: 1fr; gap: 22px; }
  .card-inspector__chapters { display: flex; gap: 4px; overflow-x: auto; padding-bottom: 6px; }
  .card-inspector__chapters button { width: auto; flex: none; white-space: nowrap; }
}

@media (max-width: 767px) {
  .card-inspector { padding: 16px 14px 42px; }
  .card-inspector__header { grid-template-columns: 1fr; gap: 12px; }
  .card-inspector__heading h2 { font-size: 24px; }
  .card-inspector__toolbar { align-items: stretch; }
  .card-inspector__mode { flex: 1 1 130px; }
  .card-inspector__more { position: static; margin-left: 0; }
  .card-inspector__sequence { width: 100%; grid-template-columns: 34px minmax(52px, auto) 34px; justify-content: end; margin-left: 0; padding: 8px 0 0; border-top: 1px solid #edf2f7; border-left: 0; }
  .card-inspector__sequence-term { display: none; }
  .card-inspector__more-menu { box-sizing: border-box; left: 0; right: 0; width: auto; min-width: 0; }
  .card-inspector__generation { align-items: flex-start; flex-direction: column; }
  .card-inspector__notebook { display: grid; grid-template-columns: 1fr; gap: 20px; margin-top: 20px; }
  .card-inspector__chapters { display: flex; gap: 4px; overflow-x: auto; padding-bottom: 5px; }
  .card-inspector__chapters button { width: auto; flex: none; white-space: nowrap; }
  .card-inspector__history article div { flex-direction: column; }
  .card-inspector__conflict-columns, .card-inspector__merge-fields label { grid-template-columns: 1fr; }
}
</style>
