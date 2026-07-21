<template>
  <main class="vocabulary-shell">
    <header class="vocabulary-topbar">
      <nav class="vocabulary-nav" aria-label="单词学习页面">
        <button
          v-for="view in views"
          :key="view.key"
          type="button"
          :class="{ active: activeView === view.key }"
          @click="switchVocabularyView(view.key)"
        >
          <span aria-hidden="true">{{ view.icon }}</span>
          {{ view.label }}
        </button>
      </nav>
    </header>

    <section v-if="activeView === 'search'" class="vocabulary-page search-page" aria-label="搜索单词">
      <div class="search-main">
        <section class="page-heading">
          <p>Search</p>
          <h1>搜索单词</h1>
          <span>查询、学习、一步到位</span>
        </section>

        <form class="dictionary-search" @submit.prevent="submitLookup">
          <span aria-hidden="true">⌕</span>
          <input
            v-model="query"
            type="search"
            autocomplete="off"
            spellcheck="false"
            placeholder="输入单词、词组或中文释义"
            aria-label="输入单词、词组或中文释义"
          >
          <select v-model="language" aria-label="选择词典语言">
            <option value="en-gb">en-gb</option>
            <option value="en-us">en-us</option>
          </select>
          <button type="submit" :disabled="loading">
            {{ loading ? '查询中' : '搜索' }}
          </button>
        </form>

        <section v-if="errorMessage" class="lookup-message lookup-message--error">
          <strong>{{ errorMessage }}</strong>
          <span v-if="debugMessage">{{ debugMessage }}</span>
        </section>

        <section v-if="selectedWord && !errorMessage" class="search-detail-section" aria-label="当前单词详情">
          <DictionaryDetail
            :key="lookupResultWord"
            :result="result"
            :word="selectedWord"
            :source-title="lookupSourceTitle"
            :last-lookup-at="lastLookupAt"
            @review="addTodayReview(selectedWord.id)"
            @master="markSelectedMastered"
            @play-audio="playAudio"
            @toggle-favorite="toggleDictionaryFavorite"
          />
        </section>

        <section v-else-if="!errorMessage" class="lookup-message lookup-message--empty">
          <header>
            <div>
              <strong>先搜索一个单词</strong>
              <span>查询后这里会展示本次会话最新一次词典结果；刷新页面也会优先恢复最近查询。</span>
            </div>
          </header>
        </section>

        <section class="search-meta-grid" aria-label="搜索快捷入口">
          <article class="compact-panel compact-panel--hot">
            <header>
              <h2>热门搜索</h2>
            </header>
            <div class="chip-list">
              <button v-for="item in hotSearches" :key="item" type="button" @click="query = item">
                {{ item }}
              </button>
            </div>
          </article>

          <article class="compact-panel">
            <header>
              <h2>最近搜索</h2>
              <button type="button" :disabled="!recentSearches.length" @click="clearRecentSearches">清空</button>
            </header>
            <ul v-if="recentSearches.length" class="recent-list">
              <li v-for="item in recentSearches" :key="item">
                <span aria-hidden="true">○</span>
                <button type="button" @click="query = item">{{ item }}</button>
              </li>
            </ul>
            <p v-else class="recent-empty">暂无最近搜索</p>
          </article>
        </section>

        <section v-if="hasSearchContext" class="results-panel">
          <header>
            <h2>搜索结果 <small>({{ words.length * 32 }})</small></h2>
            <button type="button">相关度⌄</button>
          </header>
          <div class="result-list" role="table" aria-label="单词搜索结果">
            <button
              v-for="word in words.slice(0, 5)"
              :key="word.id"
              type="button"
              class="result-row"
              :class="{ selected: selectedWordId === word.id }"
              role="row"
              @click="selectStaticWord(word.id)"
            >
              <strong>{{ word.word }}</strong>
              <span>{{ word.phonetic }}</span>
              <em>{{ word.partOfSpeech }}.</em>
              <p>{{ word.meaning }}</p>
              <i aria-label="收藏状态">{{ word.favorite ? '★' : '☆' }}</i>
            </button>
          </div>
        </section>
      </div>
    </section>

    <section v-else-if="activeView === 'modes'" class="vocabulary-page mode-page" aria-label="背词模式">
      <div class="mode-content">
        <section class="page-heading">
          <p>Practice</p>
          <h1>选择适合你的学习模式</h1>
          <span>科学的学习方法，帮助你更高效地记忆单词</span>
        </section>

        <div class="mode-grid">
          <article v-for="mode in studyModes" :key="mode.title" class="mode-card" :class="mode.tone">
            <div class="mode-icon" aria-hidden="true">{{ mode.icon }}</div>
            <h2>{{ mode.title }}</h2>
            <p>{{ mode.description }}</p>
            <button type="button">{{ mode.action }} →</button>
          </article>
        </div>

        <section class="insight-strip">
          <article v-for="item in insights" :key="item.title">
            <div aria-hidden="true">{{ item.icon }}</div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </article>
        </section>
      </div>

      <aside class="today-plan-card">
        <header>
          <h2>今日学习计划</h2>
          <button type="button">调整计划设置</button>
        </header>
        <div class="progress-ring" aria-label="今日计划完成 60%">
          <span>60%</span>
        </div>
        <dl>
          <div>
            <dt>今日目标</dt>
            <dd>30 词</dd>
          </div>
          <div>
            <dt>已完成</dt>
            <dd>18 词</dd>
          </div>
          <div>
            <dt>剩余复习</dt>
            <dd>{{ reviewQueue.length }} 词</dd>
          </div>
        </dl>
        <button type="button" class="primary-action">继续学习</button>
      </aside>
    </section>

    <section
      v-else-if="activeView === 'collection' && !isPersistentVocabularyCardRoute"
      class="vocabulary-page collection-page"
      aria-label="单词沉淀"
    >
      <VocabularyCapturePanel
        :theme-catalog="themesQuery.data.value"
        :themes-loading="themesQuery.isLoading.value"
        :themes-error="themesBlockingError"
        :capture-mutation="captureMutation"
        :import-analysis-enabled="importAnalysisEnabled"
        :import-analysis-mutation="importAnalysisMutation"
        @captured="handleVocabularyCaptured"
      />
      <VocabularyCardList
        :filters="vocabularyFilters"
        :items="listQuery.data.value?.items ?? []"
        :total="listQuery.data.value?.total ?? 0"
        :page="listQuery.data.value?.page ?? vocabularyFilters.page ?? 1"
        :size="listQuery.data.value?.size ?? vocabularyFilters.size ?? 20"
        :loading="listQuery.isLoading.value"
        :error="listQuery.error.value instanceof Error ? listQuery.error.value.message : ''"
        :selected-card-uid="selectedCardUid"
        @select="selectVocabularyCard"
        @update:filters="updateVocabularyFilters"
      />
    </section>

    <section
      v-else-if="activeView === 'collection'"
      class="vocabulary-page vocabulary-card-page"
      aria-label="单词卡详情"
    >
      <div v-if="detailQuery.isLoading.value" class="vocabulary-card-page__skeleton" role="status" aria-label="正在加载单词卡">
        <span>正在加载单词卡...</span>
        <div class="vocabulary-card-page__skeleton-title"></div>
        <div class="vocabulary-card-page__skeleton-toolbar"></div>
        <div class="vocabulary-card-page__skeleton-body"></div>
      </div>
      <section v-else-if="detailQuery.error.value" class="vocabulary-card-page__state" role="alert">
        <div>
          <h1 v-if="detailErrorKind === 'not-found'">单词卡不存在或已被删除</h1>
          <h1 v-else-if="detailErrorKind === 'forbidden'">无权查看这张单词卡</h1>
          <h1 v-else>单词卡详情加载失败</h1>
          <p>返回单词库后可以继续浏览和沉淀词条。</p>
          <div class="vocabulary-card-page__state-actions">
            <button type="button" @click="returnToVocabularyCollection">返回单词库</button>
            <button v-if="detailErrorKind === 'generic'" type="button" @click="detailQuery.refetch()">重试</button>
          </div>
        </div>
      </section>
      <VocabularyCardInspector
        v-else-if="detailQuery.data.value"
        :card="detailQuery.data.value"
        :navigation="vocabularyCardSequence"
        :navigation-pending="vocabularyNavigationPending"
        :list-vocabulary-revisions="revisionsQuery.data.value"
        :update-mutation="updateMutation"
        :delete-mutation="deleteMutation"
        :regenerate-mutation="regenerateMutation"
        :retry-vocabulary-card="retryMutation"
        :resolve-conflict-mutation="resolveConflictMutation"
        @back="returnToVocabularyCollection"
        @navigate="navigateVocabularyCard"
      />
    </section>

    <section v-else class="vocabulary-page stats-page" aria-label="学习统计">
      <section class="stats-kpis" aria-label="学习概览">
        <article v-for="metric in metrics" :key="metric.title" class="metric-card">
          <div :class="metric.tone" aria-hidden="true">{{ metric.icon }}</div>
          <span>{{ metric.title }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </section>

      <section class="stats-grid">
        <article class="chart-panel trend-panel">
          <header>
            <h2>学习趋势（近 7 天）</h2>
            <button type="button">学习单词数⌄</button>
          </header>
          <div class="line-chart" aria-label="近 7 天学习趋势">
            <span
              v-for="point in trendPoints"
              :key="point.date"
              :style="{ '--value': `${point.value}%` }"
            >
              <i></i>
              <small>{{ point.date }}</small>
            </span>
          </div>
        </article>

        <article class="chart-panel">
          <header>
            <h2>掌握情况分布</h2>
          </header>
          <div class="donut-layout">
            <div class="donut-chart" aria-label="掌握情况分布"></div>
            <ul>
              <li><span class="legend-dot mastered"></span>已掌握 328 (46%)</li>
              <li><span class="legend-dot learning"></span>学习中 284 (40%)</li>
              <li><span class="legend-dot new"></span>新学 100 (14%)</li>
            </ul>
          </div>
        </article>

        <article class="chart-panel calendar-panel">
          <header>
            <h2>连续学习日历</h2>
          </header>
          <div class="calendar-grid" aria-label="连续学习日历">
            <span v-for="day in calendarDays" :key="day.label" :class="{ done: day.done }">
              {{ day.label }}
            </span>
          </div>
          <footer>
            <span><i class="legend-dot mastered"></i> 已学习</span>
            <span><i class="legend-dot idle"></i> 未学习</span>
          </footer>
        </article>
      </section>

      <section class="milestone-panel">
        <h2>学习里程碑</h2>
        <div class="milestone-track">
          <article v-for="milestone in milestones" :key="milestone.label" :class="{ locked: milestone.locked }">
            <span>{{ milestone.locked ? '锁' : '✓' }}</span>
            <strong>{{ milestone.label }}</strong>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AxiosError } from 'axios'
import { lookupDictionary, setDictionaryFavorite } from '@/api/dictionary'
import type { DictionaryEntry, DictionaryLanguage, DictionaryLookupResponse } from '@/api/dictionary'
import {
  listVocabularyCards,
  type VocabularyCardFilters,
  type VocabularyCardPage,
  type VocabularyCaptureResponse,
} from '@/api/vocabulary'
import VocabularyCapturePanel from '@/components/vocabulary/VocabularyCapturePanel.vue'
import VocabularyCardInspector from '@/components/vocabulary/VocabularyCardInspector.vue'
import VocabularyCardList from '@/components/vocabulary/VocabularyCardList.vue'
import { useVocabularyCards } from '@/composables/useVocabularyCards'
import { useVocabularyThemes } from '@/composables/useVocabularyThemes'
import { isVocabularyImportAnalysisEnabled } from '@/features/vocabulary/importAnalysis'
import { vocabularyProductEvents } from '@/features/vocabulary/productEvents'
import {
  buildVocabularyNavigationQuery,
  parseVocabularyNavigationQuery,
  resolveVocabularyCardSequence,
} from '@/features/vocabulary/vocabularyCardNavigation'
import { showToast } from '@/utils/toast'

type VocabularyViewKey = 'search' | 'modes' | 'collection' | 'stats'
type LearningStatus = '新学' | '学习中' | '复习中' | '已掌握'
interface LearningWord {
  id: string
  word: string
  phonetic: string
  partOfSpeech: string
  meaning: string
  example: string
  translation: string
  occurrences: number
  status: LearningStatus
  mastery: number
  usage: string
  morphemes: Array<{ name: string; meaning: string; hint: string }>
  synonyms: string[]
  derived: Array<{ word: string; partOfSpeech: string; meaning: string }>
  inReview: boolean
  favorite: boolean
  savedAt: string
}

const maxVisibleDefinitions = 3
const latestLookupStorageKey = 'vocabulary.latestLookup'
const recentSearchesStorageKey = 'vocabulary.recentSearches'
const route = useRoute()
const router = useRouter()
const vocabularyViewKeys: VocabularyViewKey[] = ['search', 'modes', 'collection', 'stats']

function isVocabularyCardRoute() {
  return route.name === 'vocabulary-card'
}

function vocabularyCardRouteParam() {
  const cardUid = Array.isArray(route.params.cardUid) ? route.params.cardUid[0] : route.params.cardUid
  return typeof cardUid === 'string' ? cardUid.trim() || null : null
}

function persistentVocabularyCardUid() {
  const cardUid = vocabularyCardRouteParam()
  return cardUid && cardUid.startsWith('card_') ? cardUid : null
}

function legacyVocabularyCardKeyword() {
  return persistentVocabularyCardUid() ? undefined : vocabularyCardRouteParam() ?? undefined
}

const isPersistentVocabularyCardRoute = computed(() => (
  isVocabularyCardRoute() && Boolean(persistentVocabularyCardUid())
))

const cachedLookup = readCachedLookup()
const activeView = ref<VocabularyViewKey>(
  isVocabularyCardRoute() ? 'collection' : parseVocabularyView(route.query.tab) ?? 'search',
)
const selectedWordId = ref('')
const query = ref(cachedLookup?.word ?? '')
const language = ref<DictionaryLanguage>(cachedLookup?.language ?? 'en-gb')
const loading = ref(false)
const result = ref<DictionaryLookupResponse | null>(cachedLookup?.result ?? null)
const errorMessage = ref('')
const debugMessage = ref('')
const lastLookupAt = ref(cachedLookup?.lastLookupAt ?? '')
const initialVocabularyNavigationFilters = parseVocabularyNavigationQuery(route.query)
const vocabularyFilters = ref<VocabularyCardFilters>(initialVocabularyNavigationFilters ?? {
  keyword: legacyVocabularyCardKeyword(),
  sort: 'recent',
  page: 1,
  size: 20,
})
const vocabularyNavigationContext = ref(Boolean(initialVocabularyNavigationFilters))
const selectedCardUid = ref<string | null>(persistentVocabularyCardUid())
const previousVocabularyPage = ref<VocabularyCardPage | null>(null)
const nextVocabularyPage = ref<VocabularyCardPage | null>(null)
const vocabularyNavigationPending = ref(false)
let vocabularyNavigationRequestId = 0
const {
  listQuery,
  detailQuery,
  revisionsQuery,
  captureMutation,
  importAnalysisMutation,
  updateMutation,
  deleteMutation,
  regenerateMutation,
  retryMutation,
  resolveConflictMutation,
} = useVocabularyCards(vocabularyFilters, selectedCardUid)
const { themesQuery } = useVocabularyThemes()
const themesBlockingError = computed(() => themesQuery.isError.value && !themesQuery.data.value)
const importAnalysisEnabled = isVocabularyImportAnalysisEnabled()
const vocabularyCardSequence = computed(() => {
  if (!vocabularyNavigationContext.value || !selectedCardUid.value || !listQuery.data.value) return null
  return resolveVocabularyCardSequence(
    listQuery.data.value,
    selectedCardUid.value,
    previousVocabularyPage.value,
    nextVocabularyPage.value,
  )
})

const views: Array<{ key: VocabularyViewKey; label: string; icon: string }> = [
  { key: 'search', label: '搜索单词', icon: '⌕' },
  { key: 'modes', label: '背词模式', icon: '▣' },
  { key: 'collection', label: '单词沉淀', icon: '☆' },
  { key: 'stats', label: '学习统计', icon: '◷' },
]

type VocabularyDetailErrorKind = 'not-found' | 'forbidden' | 'generic'

function vocabularyDetailErrorKind(error: unknown): VocabularyDetailErrorKind {
  const status = (error as AxiosError | null | undefined)?.response?.status
  if (status === 404) return 'not-found'
  if (status === 403) return 'forbidden'
  return 'generic'
}

const detailErrorKind = computed(() => vocabularyDetailErrorKind(detailQuery.error.value))

const words = ref<LearningWord[]>([
  {
    id: 'innovative',
    word: 'innovative',
    phonetic: '/ˈɪn.ə.veɪ.tɪv/',
    partOfSpeech: 'adj',
    meaning: '创新的；新颖的',
    example: 'The company is known for its innovative approach to problem-solving.',
    translation: '这家公司以其创新的问题解决方法而闻名。',
    occurrences: 3,
    status: '新学',
    mastery: 20,
    usage: '指在方法、技术、思维或产品等方面具有创新性和新颖性的，常用于赞美具有创造力的想法或解决方案。',
    morphemes: [
      { name: 'in-', meaning: '进入；在内', hint: '表示进入某种状态' },
      { name: 'nov', meaning: '新的', hint: '来自拉丁语 novus' },
      { name: '-ative', meaning: '具有...性质的', hint: '形容词后缀' },
    ],
    synonyms: ['creative 有创造力的', 'original 原创的', 'novel 新颖的', 'groundbreaking 突破性的'],
    derived: [
      { word: 'innovation', partOfSpeech: 'n', meaning: '创新；创新成果' },
      { word: 'innovate', partOfSpeech: 'v', meaning: '进行创新' },
      { word: 'innovator', partOfSpeech: 'n', meaning: '创新者；革新者' },
    ],
    inReview: false,
    favorite: true,
    savedAt: '2024-05-20',
  },
  {
    id: 'perception',
    word: 'perception',
    phonetic: '/pərˈsep.ʃən/',
    partOfSpeech: 'n',
    meaning: '认知；看法；印象',
    example: 'Public perception of the brand affects the product recall.',
    translation: '公众对品牌的认知会影响产品回忆度。',
    occurrences: 2,
    status: '学习中',
    mastery: 45,
    usage: '常用于观点表达、品牌评价和社会议题写作，强调人们对事物形成的主观认识。',
    morphemes: [
      { name: 'per-', meaning: '贯穿', hint: '强调完整过程' },
      { name: 'cept', meaning: '拿取', hint: 'capture 的同源词根' },
      { name: '-ion', meaning: '名词后缀', hint: '表示行为或结果' },
    ],
    synonyms: ['view 看法', 'impression 印象', 'awareness 意识', 'understanding 理解'],
    derived: [
      { word: 'perceive', partOfSpeech: 'v', meaning: '察觉；理解' },
      { word: 'perceptive', partOfSpeech: 'adj', meaning: '有洞察力的' },
    ],
    inReview: true,
    favorite: true,
    savedAt: '2024-05-18',
  },
  {
    id: 'sustainable',
    word: 'sustainable',
    phonetic: '/səˈsteɪ.nə.bəl/',
    partOfSpeech: 'adj',
    meaning: '可持续的；能维持的',
    example: 'We need to develop sustainable solutions for future generations.',
    translation: '我们需要为后代发展可持续的解决方案。',
    occurrences: 2,
    status: '学习中',
    mastery: 38,
    usage: '高频用于环境、经济、教育与商业写作，强调长期可维持。',
    morphemes: [
      { name: 'sustain', meaning: '维持', hint: '核心动词' },
      { name: '-able', meaning: '能够...的', hint: '形容词后缀' },
    ],
    synonyms: ['lasting 持久的', 'durable 耐久的', 'renewable 可再生的'],
    derived: [
      { word: 'sustain', partOfSpeech: 'v', meaning: '维持；支撑' },
      { word: 'sustainability', partOfSpeech: 'n', meaning: '可持续性' },
    ],
    inReview: true,
    favorite: true,
    savedAt: '2024-05-19',
  },
  {
    id: 'strategy',
    word: 'strategy',
    phonetic: '/ˈstræt.ə.dʒi/',
    partOfSpeech: 'n',
    meaning: '策略；战略',
    example: 'A clear strategy helps teams make better decisions.',
    translation: '清晰的策略有助于团队做出更好的决策。',
    occurrences: 2,
    status: '学习中',
    mastery: 52,
    usage: '常用于商业、学习计划和议论文写作，强调有目标的行动方案。',
    morphemes: [
      { name: 'strateg', meaning: '领导；部署', hint: '来自军事语境' },
      { name: '-y', meaning: '名词后缀', hint: '表示抽象概念' },
    ],
    synonyms: ['plan 计划', 'approach 方法', 'method 方法'],
    derived: [
      { word: 'strategic', partOfSpeech: 'adj', meaning: '战略性的' },
      { word: 'strategist', partOfSpeech: 'n', meaning: '战略家' },
    ],
    inReview: false,
    favorite: true,
    savedAt: '2024-05-17',
  },
  {
    id: 'launch',
    word: 'launch',
    phonetic: '/lɔːntʃ/',
    partOfSpeech: 'v',
    meaning: '启动；发布；推出',
    example: 'The startup plans to launch its new app next month.',
    translation: '这家初创公司计划下个月推出新应用。',
    occurrences: 1,
    status: '复习中',
    mastery: 62,
    usage: '适合描述项目、产品、活动或计划开始，是商业和科技话题常用词。',
    morphemes: [
      { name: 'launch', meaning: '发射；推出', hint: '可作动词或名词' },
    ],
    synonyms: ['release 发布', 'start 启动', 'introduce 推出'],
    derived: [
      { word: 'launcher', partOfSpeech: 'n', meaning: '启动器；发射器' },
    ],
    inReview: true,
    favorite: true,
    savedAt: '2024-05-16',
  },
])

const hotSearches = ['innovative', 'strategy', 'sustainable', 'perception', 'launch', 'challenge']
const recentSearches = ref<string[]>(readRecentSearches())

const studyModes = [
  {
    title: '智能复习',
    description: '基于艾宾浩斯记忆曲线，优先复习即将遗忘的单词',
    action: '开始复习',
    icon: '♧',
    tone: 'mode-card--green',
  },
  {
    title: '新词学习',
    description: '学习新单词，掌握词义、用法和真实语境例句',
    action: '开始学习',
    icon: '▤',
    tone: 'mode-card--blue',
  },
  {
    title: '拼写训练',
    description: '通过听音与拼写训练，强化单词书写记忆',
    action: '开始训练',
    icon: 'A-Z',
    tone: 'mode-card--violet',
  },
]

const insights = [
  { title: '学习建议', description: '每天坚持学习 20-30 分钟，效果更佳', icon: '◎' },
  { title: '记忆曲线', description: '科学复习，事半功倍', icon: '⌁' },
  { title: '个性化推荐', description: '根据你的掌握程度智能推荐', icon: '◇' },
]

const metrics = [
  { title: '今日待复习', value: '18', hint: '较昨日 +6', icon: '□', tone: 'tone-green' },
  { title: '新增单词', value: '26', hint: '较昨日 +8', icon: '+', tone: 'tone-blue' },
  { title: '已掌握', value: '328', hint: '总计', icon: '★', tone: 'tone-amber' },
  { title: '连续学习', value: '12 天', hint: '超过 82% 的学习者', icon: '●', tone: 'tone-purple' },
]

const trendPoints = [
  { date: '05-14', value: 28 },
  { date: '05-15', value: 38 },
  { date: '05-16', value: 64 },
  { date: '05-17', value: 42 },
  { date: '05-18', value: 78 },
  { date: '05-19', value: 70 },
  { date: '05-20', value: 92 },
]

const calendarDays = Array.from({ length: 35 }, (_, index) => ({
  label: ['一', '二', '三', '四', '五', '六', '日'][index % 7],
  done: ![3, 6, 13, 18, 24, 27, 32].includes(index),
}))

const milestones = [
  { label: '累计学习 7 天', locked: false },
  { label: '累计学习 14 天', locked: false },
  { label: '累计学习 30 天', locked: true },
  { label: '累计学习 60 天', locked: true },
  { label: '累计学习 100 天', locked: true },
]

const selectedWord = computed<LearningWord | null>(() => {
  const staticWord = words.value.find((item) => item.id === selectedWordId.value)
  if (staticWord) {
    return staticWord
  }
  if (result.value) {
    return createLearningWordFromLookup(result.value)
  }
  return null
})
const reviewQueue = computed(() => words.value.filter((item) => item.inReview))
const lookupResultWord = computed(() => result.value?.word || selectedWord.value?.word || '')
const hasSearchContext = computed(() => Boolean(result.value || selectedWordId.value || query.value.trim()))
const lookupSourceTitle = computed(() => {
  if (result.value?.source === 'local') {
    return '已安装本地词典'
  }
  if (result.value?.source === 'oxford') {
    return 'Oxford Dictionaries'
  }
  return result.value?.source || '词典查询'
})

const DictionaryDetail = defineComponent({
  name: 'DictionaryDetail',
  props: {
    result: {
      type: Object as () => DictionaryLookupResponse | null,
      default: null,
    },
    word: {
      type: Object as () => LearningWord,
      required: true,
    },
    sourceTitle: {
      type: String,
      required: true,
    },
    lastLookupAt: {
      type: String,
      default: '',
    },
  },
  emits: ['review', 'master', 'play-audio', 'toggle-favorite'],
  setup(props, { emit }) {
    const dictionaryEntries = computed<DictionaryEntry[]>(() => {
      if (props.result?.entries?.length) {
        return props.result.entries
      }
      return [{
        partOfSpeech: props.word.partOfSpeech,
        definitions: [props.word.usage || props.word.meaning],
        examples: [props.word.example],
      }]
    })
    const dictionaryPhonetics = computed(() => props.result?.phonetics ?? [])
    const displayWord = computed(() => props.result?.word || props.word.word)
    const speechWord = computed(() => displayWord.value.replace(/[·•]/g, ''))
    const displaySource = computed(() => props.result ? props.sourceTitle : '学习词库')
    const displayLanguage = computed(() => props.result?.language || '本地学习数据')
    const displayFavorite = computed(() => props.result?.favorite ?? props.word.favorite)
    const displayLookupCount = computed(() => props.result?.lookupCount ?? 0)
    const partOfSpeechLabel = computed(() => dictionaryEntries.value[0]?.partOfSpeech || props.word.partOfSpeech)
    const primaryDefinition = computed(() => {
      const first = dictionaryEntries.value[0]?.definitions?.[0]
      return first || props.word.meaning
    })
    const contentEntries = computed<DictionaryEntry[]>(() => dictionaryEntries.value.map((entry) => {
      const definitions = entry.definitions.filter((definition) => !definition.trim().startsWith('短语：'))
      return {
        ...entry,
        definitions: definitions.length ? definitions : entry.definitions,
      }
    }))
    const supplementWord = computed(() => {
      if (!props.result || props.result.word === props.word.word) {
        return props.word
      }
      return null
    })
    const expandedDetailEntries = ref<Set<string>>(new Set())
    const commonPhrases = computed(() => {
      const phraseDefinitions = dictionaryEntries.value
        .flatMap((entry) => entry.definitions)
        .filter((definition) => definition.trim().startsWith('短语：'))
        .map((definition) => {
          const text = definition.trim().replace(/^短语：/, '')
          const [phrase, ...rest] = text.split(' - ')
          return {
            phrase: phrase.trim(),
            meaning: rest.join(' - ').trim() || text,
          }
        })

      if (phraseDefinitions.length) {
        return phraseDefinitions
      }

      const base = props.result?.word || props.word.word
      return [
        { phrase: `have the ${base} to do sth`, meaning: '有勇气或能力去做某事' },
        { phrase: `${base} in context`, meaning: '在真实语境中理解并使用这个词' },
        { phrase: `${base} approach`, meaning: '与该词相关的常见表达或搭配' },
      ]
    })
    function detailEntryKey(entry: DictionaryEntry, index: number) {
      return `${entry.partOfSpeech || 'entry'}-${index}`
    }
    function isDetailEntryExpanded(key: string) {
      return expandedDetailEntries.value.has(key)
    }
    function toggleDetailEntry(key: string) {
      const next = new Set(expandedDetailEntries.value)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      expandedDetailEntries.value = next
    }
    function visibleDetailDefinitions(entry: DictionaryEntry, key: string) {
      return isDetailEntryExpanded(key)
        ? entry.definitions
        : entry.definitions.slice(0, maxVisibleDefinitions)
    }
    function cleanPhonetic(text = '') {
      return text.replace(/^(BrE|NAmE)\s*/i, '').trim()
    }
    function phoneticText(label: string, index: number) {
      const preferred = dictionaryPhonetics.value.find((item) => item.text?.toLowerCase().startsWith(label.toLowerCase()))
      return cleanPhonetic(preferred?.text || dictionaryPhonetics.value[index]?.text || props.word.phonetic)
    }
    const pronunciationItems = computed(() => [
      {
        label: '英',
        language: 'en-GB',
        text: phoneticText('BrE', 0),
        audioUrl: dictionaryPhonetics.value[0]?.audioUrl,
      },
      {
        label: '美',
        language: 'en-US',
        text: phoneticText('NAmE', 1),
        audioUrl: dictionaryPhonetics.value[1]?.audioUrl,
      },
    ].filter((item) => item.text))
    function playPronunciation(item: { audioUrl?: string; text: string; language: string }) {
      emit('play-audio', {
        audioUrl: item.audioUrl,
        text: speechWord.value,
        language: item.language,
      })
    }

    return () => h('article', { class: 'dictionary-detail-card' }, [
      h('header', { class: 'dictionary-hero' }, [
        h('div', { class: 'dictionary-title-block' }, [
          h('span', { class: 'section-eyebrow' }, 'Oxford Dictionary'),
          h('h2', displayWord.value),
          h('div', { class: 'phonetic-line' }, pronunciationItems.value.map((item) => h('button', {
                type: 'button',
                class: 'pronunciation-button',
                'aria-label': `播放${item.label}式发音`,
                onClick: () => playPronunciation(item),
              }, [
                h('span', { class: 'pronunciation-icon', 'aria-hidden': 'true' }, '♪'),
                h('span', { class: 'pronunciation-label' }, item.label),
                h('span', { class: 'pronunciation-text' }, `/${item.text}/`),
              ]))),
          h('div', { class: 'dictionary-meta-row' }, [
            h('mark', { class: 'source-pill' }, displaySource.value),
            h('span', displayLanguage.value),
            props.lastLookupAt ? h('span', props.lastLookupAt) : null,
          ]),
          h('p', { class: 'hero-definition' }, primaryDefinition.value),
        ]),
        h('div', { class: 'dictionary-actions' }, [
          h('button', {
            type: 'button',
            class: ['favorite-action', displayFavorite.value ? 'active' : ''],
            'aria-label': displayFavorite.value ? '取消收藏单词' : '收藏单词',
            onClick: () => emit('toggle-favorite', {
              word: displayWord.value,
              favorite: !displayFavorite.value,
              language: props.result?.language,
            }),
          }, displayFavorite.value ? '★' : '☆'),
          h('button', { type: 'button', class: 'primary-action', onClick: () => emit('review') }, '加入今日复习'),
          h('button', { type: 'button', onClick: () => emit('master') }, '标记已掌握'),
        ]),
      ]),

      h('section', { class: 'dictionary-insight-row', 'aria-label': '单词学习状态' }, [
        h('article', [
          h('span', '查询次数'),
          h('strong', `${displayLookupCount.value} 次`),
        ]),
        h('article', [
          h('span', '收藏状态'),
          h('strong', displayFavorite.value ? '已收藏' : '未收藏'),
        ]),
      ]),

      h('section', { class: 'definition-list' }, [
        h('header', { class: 'section-title-row' }, [
          h('h3', '释义与例句'),
          h('span', `${contentEntries.value.reduce((total, entry) => total + entry.definitions.length, 0)} 条释义`),
        ]),
        ...contentEntries.value.map((entry, entryIndex) => {
          const key = detailEntryKey(entry, entryIndex)
          return h('article', { class: 'definition-entry' }, [
            h('div', { class: 'definition-index' }, String(entryIndex + 1)),
            h('div', [
              h('h3', [
                h('mark', { class: 'pos-label' }, entry.partOfSpeech || partOfSpeechLabel.value || 'entry'),
              ]),
              ...visibleDetailDefinitions(entry, key).map((definition, definitionIndex) => h('section', { class: 'definition-item' }, [
                h('p', { class: 'definition-text' }, definition),
                entry.examples[definitionIndex]
                  ? h('blockquote', [
                    h('p', entry.examples[definitionIndex]),
                    !props.result && definitionIndex === 0 ? h('small', props.word.translation) : null,
                  ])
                  : null,
              ])),
              entry.definitions.length > maxVisibleDefinitions
                ? h('button', {
                  type: 'button',
                  class: 'ghost-button detail-expand-button',
                  onClick: () => toggleDetailEntry(key),
                }, isDetailEntryExpanded(key) ? '收起' : '展开更多')
                : null,
            ]),
          ])
        }),
      ]),

      h('section', { class: 'phrase-panel' }, [
        h('header', { class: 'section-title-row' }, [
          h('h3', '常用搭配 / 习语'),
          h('span', `${commonPhrases.value.length} 条`),
        ]),
        h('div', { class: 'phrase-list' }, commonPhrases.value.map((item, index) => h('article', [
          h('strong', item.phrase),
          h('p', `${index + 1}. ${item.meaning}`),
        ]))),
      ]),

      h('section', { class: 'learning-supplement' }, [
        h('article', [
          h('h3', '近义词'),
          supplementWord.value
            ? h('div', { class: 'chip-list' }, supplementWord.value.synonyms.map((item) => h('span', item)))
            : h('p', { class: 'supplement-empty' }, '查询结果优先展示词典释义，近义词可在本地词库补充后显示。'),
        ]),
        h('article', [
          h('h3', '派生词'),
          supplementWord.value
            ? h('div', { class: 'derived-list' }, supplementWord.value.derived.map((item) => h('span', `${item.word} ${item.partOfSpeech}. ${item.meaning}`)))
            : h('p', { class: 'supplement-empty' }, '暂未匹配本地派生词。'),
        ]),
        h('article', [
          h('h3', '词根联想'),
          supplementWord.value
            ? h('div', { class: 'morpheme-list compact' }, supplementWord.value.morphemes.map((part) => h('article', [
              h('strong', part.name),
              h('span', part.meaning),
            ])))
            : h('p', { class: 'supplement-empty' }, '可在单词入库后补充词根记忆。'),
        ]),
      ]),
    ])
  },
})

interface CachedDictionaryLookup {
  word: string
  language: DictionaryLanguage
  lastLookupAt: string
  result: DictionaryLookupResponse
}

function readCachedLookup(): CachedDictionaryLookup | null {
  try {
    const raw = window.sessionStorage.getItem(latestLookupStorageKey)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<CachedDictionaryLookup>
    if (
      !parsed.word ||
      !parsed.result?.word ||
      (parsed.language !== 'en-gb' && parsed.language !== 'en-us')
    ) {
      return null
    }
    return {
      word: parsed.word,
      language: parsed.language,
      lastLookupAt: parsed.lastLookupAt || '',
      result: parsed.result,
    }
  } catch {
    return null
  }
}

function readRecentSearches() {
  try {
    const raw = window.sessionStorage.getItem(recentSearchesStorageKey)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      .map((item) => item.trim())
      .slice(0, 8)
  } catch {
    return []
  }
}

function persistLatestLookup(lookup: DictionaryLookupResponse, searchedWord: string, lookedUpAt: string) {
  try {
    window.sessionStorage.setItem(latestLookupStorageKey, JSON.stringify({
      word: lookup.word || searchedWord,
      language: language.value,
      lastLookupAt: lookedUpAt,
      result: lookup,
    }))
  } catch {
    // sessionStorage may be unavailable in private mode; the page can still use in-memory state.
  }
}

function rememberRecentSearch(word: string) {
  const normalizedWord = word.trim()
  if (!normalizedWord) {
    return
  }
  const next = [
    normalizedWord,
    ...recentSearches.value.filter((item) => item.toLowerCase() !== normalizedWord.toLowerCase()),
  ].slice(0, 8)
  recentSearches.value = next
  try {
    window.sessionStorage.setItem(recentSearchesStorageKey, JSON.stringify(next))
  } catch {
    // Ignore storage failures; recent searches still work for the current session.
  }
}

function clearRecentSearches() {
  recentSearches.value = []
  try {
    window.sessionStorage.removeItem(recentSearchesStorageKey)
  } catch {
    // Ignore storage failures.
  }
}

function createLearningWordFromLookup(lookup: DictionaryLookupResponse): LearningWord {
  const firstEntry = lookup.entries[0]
  const firstDefinition = firstEntry?.definitions[0] || '暂无释义'
  return {
    id: lookup.word.toLowerCase(),
    word: lookup.word,
    phonetic: lookup.phonetics[0]?.text?.replace(/^(BrE|NAmE)\s*/i, '').trim() || '',
    partOfSpeech: firstEntry?.partOfSpeech || 'entry',
    meaning: firstDefinition,
    example: firstEntry?.examples[0] || '',
    translation: '',
    occurrences: lookup.lookupCount ?? 0,
    status: '新学',
    mastery: 0,
    usage: firstDefinition,
    morphemes: [],
    synonyms: [],
    derived: [],
    inReview: false,
    favorite: Boolean(lookup.favorite),
    savedAt: '',
  }
}

function selectStaticWord(wordId: string) {
  selectedWordId.value = wordId
  result.value = null
  errorMessage.value = ''
  debugMessage.value = ''
  lastLookupAt.value = ''
}

async function submitLookup() {
  const word = query.value.trim()
  if (!word) {
    errorMessage.value = '请输入要查询的单词'
    debugMessage.value = import.meta.env.DEV ? '400 / INVALID_WORD' : ''
    result.value = null
    return
  }

  loading.value = true
  errorMessage.value = ''
  debugMessage.value = ''

  try {
    result.value = await lookupDictionary(word, language.value)
    selectedWordId.value = ''
    const lookedUpAt = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(Date.now())
    lastLookupAt.value = lookedUpAt
    rememberRecentSearch(result.value.word || word)
    persistLatestLookup(result.value, word, lookedUpAt)
  } catch (err) {
    result.value = null
    const normalized = normalizeError(err)
    errorMessage.value = normalized.message
    debugMessage.value = import.meta.env.DEV ? normalized.debug : ''
  } finally {
    loading.value = false
  }
}

async function toggleDictionaryFavorite(payload: { word: string; favorite: boolean; language?: string }) {
  const targetWord = payload.word?.trim()
  if (!targetWord) {
    return
  }
  try {
    const state = await setDictionaryFavorite(targetWord, payload.favorite, payload.language || language.value)
    if (result.value && result.value.word.toLowerCase() === state.word.toLowerCase()) {
      result.value = {
        ...result.value,
        favorite: state.favorite,
        lookupCount: state.lookupCount,
      }
    }
    const localWord = words.value.find((item) => item.word.toLowerCase() === targetWord.toLowerCase())
    if (localWord) {
      localWord.favorite = state.favorite
    }
    showToast(state.favorite ? '已加入收藏' : '已取消收藏', 'success')
  } catch {
    showToast('收藏状态更新失败，请稍后重试', 'error')
  }
}

function parseVocabularyView(value: unknown): VocabularyViewKey | null {
  const tab = Array.isArray(value) ? value[0] : value
  return typeof tab === 'string' && vocabularyViewKeys.includes(tab as VocabularyViewKey)
    ? tab as VocabularyViewKey
    : null
}

function switchVocabularyView(view: VocabularyViewKey) {
  activeView.value = view
  const nextQuery = { ...route.query }
  if (view === 'search') {
    delete nextQuery.tab
  } else {
    nextQuery.tab = view
  }
  void router.replace({ name: 'Vocabulary', query: nextQuery })
}

function updateVocabularyFilters(filters: VocabularyCardFilters) {
  vocabularyFilters.value = filters
  selectedCardUid.value = null
  vocabularyNavigationContext.value = true
  void router.replace({
    name: 'Vocabulary',
    query: { tab: 'collection', ...buildVocabularyNavigationQuery(filters) },
  })
}

function handleVocabularyCaptured(response: VocabularyCaptureResponse) {
  const selected = response.items.find((item) => item.cardUid)
  if (selected?.cardUid) selectVocabularyCard(selected.cardUid)
}

function selectVocabularyCard(cardUid: string) {
  vocabularyNavigationContext.value = true
  void router.push({
    name: 'vocabulary-card',
    params: { cardUid },
    query: buildVocabularyNavigationQuery(vocabularyFilters.value),
  })
}

function returnToVocabularyCollection() {
  const query = vocabularyNavigationContext.value
    ? { tab: 'collection', ...buildVocabularyNavigationQuery(vocabularyFilters.value) }
    : { tab: 'collection' }
  void router.replace({ name: 'Vocabulary', query })
}

function syncVocabularyRoute() {
  const navigationFilters = parseVocabularyNavigationQuery(route.query)
  vocabularyNavigationContext.value = Boolean(navigationFilters)
  if (navigationFilters) vocabularyFilters.value = navigationFilters

  if (isVocabularyCardRoute()) {
    activeView.value = 'collection'
    const cardUid = persistentVocabularyCardUid()
    if (cardUid) {
      selectedCardUid.value = cardUid
      return
    }
    vocabularyFilters.value = {
      ...vocabularyFilters.value,
      keyword: legacyVocabularyCardKeyword(),
      page: 1,
    }
    selectedCardUid.value = null
    return
  }

  activeView.value = parseVocabularyView(route.query.tab) ?? 'search'
  selectedCardUid.value = null
}

function navigationPageFilters(page: number): VocabularyCardFilters {
  return { ...vocabularyFilters.value, page }
}

async function loadAdjacentVocabularyPages() {
  const requestId = ++vocabularyNavigationRequestId
  previousVocabularyPage.value = null
  nextVocabularyPage.value = null
  vocabularyNavigationPending.value = false

  const currentPage = listQuery.data.value
  const cardUid = selectedCardUid.value
  if (!vocabularyNavigationContext.value || !currentPage || !cardUid) return

  const index = currentPage.items.findIndex((card) => card.cardUid === cardUid)
  if (index < 0) return

  const shouldLoadPrevious = index === 0 && currentPage.page > 1
  const shouldLoadNext = index === currentPage.items.length - 1
    && currentPage.page * currentPage.size < currentPage.total
  if (!shouldLoadPrevious && !shouldLoadNext) return

  vocabularyNavigationPending.value = true
  try {
    const [previousPage, nextPage] = await Promise.all([
      shouldLoadPrevious
        ? listVocabularyCards(navigationPageFilters(currentPage.page - 1))
        : Promise.resolve(null),
      shouldLoadNext
        ? listVocabularyCards(navigationPageFilters(currentPage.page + 1))
        : Promise.resolve(null),
    ])
    if (requestId !== vocabularyNavigationRequestId) return
    previousVocabularyPage.value = previousPage
    nextVocabularyPage.value = nextPage
  } catch {
    if (requestId !== vocabularyNavigationRequestId) return
    previousVocabularyPage.value = null
    nextVocabularyPage.value = null
  } finally {
    if (requestId === vocabularyNavigationRequestId) vocabularyNavigationPending.value = false
  }
}

function navigateVocabularyCard(direction: 'previous' | 'next') {
  const sequence = vocabularyCardSequence.value
  const target = direction === 'previous' ? sequence?.previous : sequence?.next
  const currentPage = listQuery.data.value
  if (!sequence || !target || !currentPage) return

  const targetOnCurrentPage = currentPage.items.some((card) => card.cardUid === target.cardUid)
  const targetPage = targetOnCurrentPage
    ? currentPage.page
    : direction === 'previous'
      ? Math.max(1, currentPage.page - 1)
      : currentPage.page + 1
  const filters = navigationPageFilters(targetPage)

  vocabularyFilters.value = filters
  selectedCardUid.value = target.cardUid
  void router.push({
    name: 'vocabulary-card',
    params: { cardUid: target.cardUid },
    query: buildVocabularyNavigationQuery(filters),
  })
}

watch(() => route.fullPath, syncVocabularyRoute)

watch(
  () => [listQuery.data.value, selectedCardUid.value, vocabularyNavigationContext.value],
  () => void loadAdjacentVocabularyPages(),
  { immediate: true },
)

watch(
  () => detailQuery.data.value,
  (card) => {
    if (!isPersistentVocabularyCardRoute.value || !card?.activeRevisionUid) return
    void vocabularyProductEvents.learningStarted(card.cardUid, card.sourceTypes[0])
  },
  { immediate: true },
)

function addTodayReview(wordId: string) {
  const word = words.value.find((item) => item.id === wordId)
  if (!word) return
  word.inReview = true
  if (word.status === '新学') {
    word.status = '学习中'
  }
}

function markSelectedMastered() {
  const word = selectedWord.value
  if (!word) return
  word.status = '已掌握'
  word.mastery = 100
}

function playAudio(payload: string | { audioUrl?: string; text?: string; language?: string }) {
  if (typeof payload === 'string') {
    void new Audio(payload).play()
    return
  }

  if (payload.audioUrl) {
    void new Audio(payload.audioUrl).play()
    return
  }

  if (!payload.text || !('speechSynthesis' in window)) {
    return
  }

  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(payload.text)
  utterance.lang = payload.language || 'en-GB'
  utterance.rate = 0.9
  window.speechSynthesis.speak(utterance)
}

function normalizeError(err: unknown) {
  const response = (err as { response?: { status?: number; data?: { code?: string; message?: string } } }).response
  const status = response?.status
  const code = response?.data?.code
  const message = response?.data?.message

  if (status === 404) {
    return { message: '未找到该单词', debug: `${status} / ${code ?? 'DICTIONARY_NOT_FOUND'}` }
  }
  if (status === 429) {
    return { message: '词典服务额度已用完，请稍后再试', debug: `${status} / ${code ?? 'OXFORD_QUOTA_EXCEEDED'}` }
  }
  if (status === 504) {
    return { message: '词典服务响应超时', debug: `${status} / ${code ?? 'OXFORD_TIMEOUT'}` }
  }
  return {
    message: message || '词典服务暂时不可用，请稍后再试',
    debug: `${status ?? 'NETWORK'} / ${code ?? 'DICTIONARY_LOOKUP_FAILED'}`,
  }
}
</script>

<style scoped>
.vocabulary-shell {
  min-height: 100vh;
  padding: 22px;
  background: #f7faf9;
  color: #0f172a;
}

.vocabulary-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  min-height: 68px;
  padding: 0 24px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.vocabulary-nav,
.result-row,
.collection-tools,
.filter-row,
.collection-actions,
.stats-kpis,
.chart-panel header,
.table-footer,
.table-footer div,
.milestone-track {
  display: flex;
  align-items: center;
}

.vocabulary-nav {
  min-width: 0;
  justify-content: center;
  gap: 16px;
  overflow-x: auto;
}

.vocabulary-nav button,
.dictionary-search button,
.compact-panel button,
.results-panel button,
.ghost-button,
.mode-card button,
.today-plan-card button,
.collection-tools button,
.filter-row button,
.collection-actions button,
.table-footer button,
.chart-panel button,
.word-preview-card button {
  border: 0;
  border-radius: 8px;
  font-weight: 800;
  cursor: pointer;
}

.vocabulary-nav button {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  background: transparent;
  color: #475569;
  white-space: nowrap;
}

.vocabulary-nav button.active {
  color: #047857;
  box-shadow: inset 0 -3px 0 #059669;
}

.vocabulary-page {
  margin-top: 18px;
}

.search-page,
.mode-page,
.collection-page {
  display: grid;
  gap: 18px;
  align-items: start;
}

.search-page {
  grid-template-columns: minmax(0, 1fr);
}

.mode-page {
  grid-template-columns: minmax(0, 1fr) 380px;
}

.search-main,
.mode-content,
.collection-main,
.word-preview-card,
.today-plan-card,
.metric-card,
.chart-panel,
.milestone-panel,
.compact-panel,
.results-panel,
.collection-table {
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.search-main,
.mode-content,
.collection-main {
  padding: 26px;
}

.search-main .page-heading {
  text-align: center;
}

.page-heading p,
.page-heading h1,
.page-heading span {
  margin: 0;
}

.page-heading p {
  color: #059669;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.page-heading h1 {
  margin-top: 6px;
  font-size: 26px;
  line-height: 1.2;
}

.page-heading span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 14px;
}

.dictionary-search {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 110px 92px;
  align-items: center;
  width: 100%;
  max-width: 820px;
  min-height: 54px;
  margin: 24px auto 0;
  overflow: hidden;
  border: 1px solid #10b981;
  border-radius: 8px;
  background: #ffffff;
}

.dictionary-search > span {
  color: #64748b;
  text-align: center;
}

.dictionary-search input,
.dictionary-search select,
.collection-tools input {
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  outline: none;
}

.dictionary-search input,
.dictionary-search select {
  height: 52px;
}

.dictionary-search input::placeholder,
.collection-tools input::placeholder {
  color: #94a3b8;
}

.dictionary-search select {
  border-left: 1px solid #dce7e1;
  padding: 0 10px;
  font-weight: 800;
}

.dictionary-search button,
.primary-action {
  min-height: 42px;
  background: #059669;
  color: #ffffff;
  box-shadow: 0 10px 22px rgba(5, 150, 105, 0.18);
}

.dictionary-search button {
  margin-right: 6px;
}

.dictionary-search button:disabled {
  background: #94a3b8;
  box-shadow: none;
}

.lookup-message {
  width: 100%;
  max-width: 820px;
  margin-top: 16px;
  margin-inline: auto;
  padding: 16px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
}

.lookup-message--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.lookup-message--empty {
  color: #334155;
}

.lookup-message header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.lookup-message header span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  line-height: 1.6;
}

.dictionary-entry {
  margin-top: 12px;
}

.dictionary-entry h3 {
  margin: 0;
  font-size: 14px;
}

.dictionary-entry p,
.dictionary-entry small {
  margin: 4px 0 0;
  color: #334155;
  line-height: 1.5;
}

.recent-empty {
  margin: 14px 0 0;
  color: #94a3b8;
  font-size: 13px;
}

.ghost-button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid #bbd7ca;
  background: #ffffff;
  color: #047857;
}

.search-meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 12px;
  width: 100%;
  max-width: 1080px;
  margin-top: 12px;
  margin-inline: auto;
}

.search-detail-section {
  width: 100%;
  max-width: 1080px;
  margin-top: 18px;
  margin-inline: auto;
}

.dictionary-detail-card {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.search-detail-section :deep(.dictionary-hero) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: start;
  padding: 24px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ffffff 72%);
}

.search-detail-section :deep(.section-eyebrow) {
  display: block;
  color: #059669;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
}

.search-detail-section :deep(.dictionary-title-block h2) {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 42px;
  line-height: 1.1;
}

.search-detail-section :deep(.phonetic-line),
.search-detail-section :deep(.dictionary-meta-row),
.search-detail-section :deep(.dictionary-actions) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.search-detail-section :deep(.phonetic-line) {
  gap: 14px;
  margin-top: 12px;
  color: #475569;
  font-size: 15px;
}

.search-detail-section :deep(.pronunciation-button) {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-height: 36px;
  padding: 0 12px 0 8px;
  border: 1px solid #dce7e1;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  color: #334155;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
  font-weight: 800;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.search-detail-section :deep(.pronunciation-button:hover) {
  border-color: #10b981;
  color: #047857;
  box-shadow: 0 10px 24px rgba(5, 150, 105, 0.12);
  transform: translateY(-1px);
}

.search-detail-section :deep(.pronunciation-icon) {
  display: inline-grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 50%;
  background: #d1fae5;
  color: #047857;
  font-size: 14px;
  line-height: 1;
}

.search-detail-section :deep(.pronunciation-label) {
  color: #047857;
  font-size: 16px;
  font-weight: 900;
}

.search-detail-section :deep(.pronunciation-text) {
  color: #334155;
  font-size: 15px;
  font-weight: 700;
}

.search-detail-section :deep(.dictionary-meta-row) {
  gap: 10px;
  margin-top: 14px;
  color: #64748b;
  font-size: 13px;
}

.search-detail-section :deep(.source-pill),
.search-detail-section :deep(.pos-label) {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.search-detail-section :deep(.source-pill) {
  background: #dcfce7;
  color: #047857;
}

.search-detail-section :deep(.pos-label) {
  background: #eef2ff;
  color: #2563eb;
}

.search-detail-section :deep(.hero-definition) {
  max-width: 820px;
  margin: 14px 0 0;
  color: #334155;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.65;
}

.search-detail-section :deep(.dictionary-actions) {
  justify-content: flex-end;
  gap: 10px;
  max-width: 360px;
}

.search-detail-section :deep(.dictionary-actions button),
.search-detail-section :deep(.detail-expand-button) {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #bbd7ca;
  border-radius: 8px;
  background: #ffffff;
  color: #047857;
  font-weight: 900;
}

.search-detail-section :deep(.dictionary-actions .primary-action) {
  border: 0;
  background: #059669;
  color: #ffffff;
  box-shadow: 0 10px 22px rgba(5, 150, 105, 0.18);
}

.search-detail-section :deep(.dictionary-actions .favorite-action) {
  width: 46px;
  padding: 0;
  font-size: 18px;
}

.search-detail-section :deep(.dictionary-actions .favorite-action.active) {
  border-color: #10b981;
  background: #ecfdf5;
  color: #047857;
}

.search-detail-section :deep(.dictionary-insight-row) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.search-detail-section :deep(.dictionary-insight-row article) {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.search-detail-section :deep(.dictionary-insight-row span) {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.search-detail-section :deep(.dictionary-insight-row strong) {
  color: #0f172a;
  font-size: 18px;
}

.search-detail-section :deep(.definition-list),
.search-detail-section :deep(.phrase-panel),
.search-detail-section :deep(.learning-supplement) {
  margin-top: 22px;
}

.search-detail-section :deep(.section-title-row) {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf2f7;
}

.search-detail-section :deep(.section-title-row h3) {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
}

.search-detail-section :deep(.section-title-row span) {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.search-detail-section :deep(.definition-entry) {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 14px;
  padding: 18px 4px;
}

.search-detail-section :deep(.definition-entry + .definition-entry) {
  border-top: 1px solid #edf2f7;
}

.search-detail-section :deep(.definition-index) {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 50%;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 900;
}

.search-detail-section :deep(.definition-entry h3) {
  margin: 0;
}

.search-detail-section :deep(.definition-item) {
  margin-top: 12px;
  padding-left: 14px;
  border-left: 3px solid #d1fae5;
}

.search-detail-section :deep(.definition-text) {
  margin: 0;
  color: #0f172a;
  font-weight: 700;
  line-height: 1.65;
}

.search-detail-section :deep(.definition-item blockquote) {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fbff;
}

.search-detail-section :deep(.definition-item blockquote p) {
  margin: 0;
  color: #2563eb;
  font-style: italic;
  line-height: 1.55;
}

.search-detail-section :deep(.definition-item blockquote small) {
  display: block;
  margin-top: 4px;
  color: #64748b;
  line-height: 1.45;
}

.search-detail-section :deep(.phrase-list) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.search-detail-section :deep(.phrase-list article) {
  padding: 12px 14px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #fbfdfc;
}

.search-detail-section :deep(.phrase-list strong) {
  color: #0f172a;
}

.search-detail-section :deep(.phrase-list p) {
  margin: 5px 0 0;
  color: #475569;
  line-height: 1.45;
}

.search-detail-section :deep(.learning-supplement) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding-top: 18px;
  border-top: 1px solid #edf2f7;
}

.search-detail-section :deep(.learning-supplement h3) {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.search-detail-section :deep(.supplement-empty) {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.search-detail-section :deep(.derived-list) {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.search-detail-section :deep(.derived-list span) {
  min-height: 30px;
  padding: 8px 10px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.search-detail-section :deep(.morpheme-list.compact) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dictionary-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: start;
  padding-bottom: 18px;
  border-bottom: 1px solid #edf2f7;
}

.section-eyebrow {
  display: block;
  color: #059669;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.dictionary-title-block h2 {
  margin: 6px 0 0;
  font-size: 42px;
  line-height: 1.1;
}

.phonetic-line,
.dictionary-meta-row,
.dictionary-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.phonetic-line {
  gap: 14px;
  margin-top: 14px;
  color: #475569;
  font-size: 15px;
}

.audio-button {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid #dce7e1;
  border-radius: 50%;
  background: #ffffff;
  color: #047857;
  font-weight: 900;
}

.audio-button.muted {
  color: #94a3b8;
}

.dictionary-meta-row {
  gap: 10px;
  margin-top: 14px;
  color: #64748b;
  font-size: 13px;
}

.source-pill,
.pos-label {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.source-pill {
  background: #dcfce7;
  color: #047857;
}

.pos-label {
  background: #fee2e2;
  color: #b91c1c;
}

.dictionary-actions {
  justify-content: flex-end;
  gap: 10px;
  max-width: 360px;
}

.dictionary-actions button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #bbd7ca;
  border-radius: 8px;
  background: #ffffff;
  color: #047857;
  font-weight: 900;
}

.dictionary-actions .primary-action {
  border: 0;
  background: #059669;
  color: #ffffff;
}

.definition-list {
  margin-top: 20px;
}

.entry-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.entry-heading span {
  color: #334155;
  font-weight: 800;
}

.definition-entry {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
}

.definition-entry + .definition-entry {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #edf2f7;
}

.definition-index {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 50%;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 900;
}

.definition-entry h3,
.phrase-panel h3,
.learning-supplement h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.definition-item {
  margin-top: 12px;
  padding-left: 14px;
  border-left: 3px solid #d1fae5;
}

.definition-text,
.definition-translation {
  margin: 0;
  line-height: 1.55;
}

.definition-text {
  color: #0f172a;
  font-weight: 800;
}

.definition-translation {
  margin-top: 4px;
  color: #475569;
}

.definition-item blockquote {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fbff;
}

.definition-item blockquote p {
  margin: 0;
  color: #2563eb;
  font-style: italic;
  line-height: 1.5;
}

.definition-item blockquote small {
  display: block;
  margin-top: 4px;
  color: #64748b;
  line-height: 1.45;
}

.detail-expand-button {
  margin-top: 10px;
}

.phrase-panel,
.learning-supplement {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid #edf2f7;
}

.phrase-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.phrase-list article {
  padding: 12px 14px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #fbfdfc;
}

.phrase-list strong {
  color: #0f172a;
}

.phrase-list p {
  margin: 5px 0 0;
  color: #475569;
  line-height: 1.45;
}

.learning-supplement {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.derived-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.derived-list span {
  min-height: 30px;
  padding: 8px 10px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.supplement-empty {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.morpheme-list.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.compact-panel,
.results-panel {
  padding: 16px;
}

.search-meta-grid .compact-panel {
  padding: 12px 14px;
}

.search-meta-grid .compact-panel h2 {
  font-size: 14px;
}

.compact-panel--hot {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.compact-panel--hot header {
  align-items: center;
}

.compact-panel--hot .chip-list {
  margin-top: 0;
}

.compact-panel header,
.results-panel header,
.today-plan-card header,
.word-preview-card header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.compact-panel h2,
.results-panel h2,
.today-plan-card h2,
.chart-panel h2,
.milestone-panel h2,
.preview-title {
  margin: 0;
  font-size: 17px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.chip-list button,
.chip-list span {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.recent-list {
  display: grid;
  gap: 6px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.recent-list li {
  display: grid;
  grid-template-columns: 18px 1fr;
  align-items: center;
  min-height: 28px;
}

.recent-list button {
  min-width: 0;
  background: transparent;
  color: #475569;
  text-align: left;
}

.results-panel {
  width: 100%;
  max-width: 1080px;
  margin-top: 14px;
  margin-inline: auto;
}

.results-panel header button,
.compact-panel header button,
.chart-panel button,
.collection-tools button,
.filter-row button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #dce7e1;
  background: #ffffff;
  color: #047857;
}

.result-list {
  display: grid;
  margin-top: 12px;
}

.result-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 120px 56px minmax(180px, 1.4fr) 28px;
  gap: 12px;
  width: 100%;
  min-height: 54px;
  padding: 0 12px;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  text-align: left;
}

.result-row + .result-row {
  border-top: 1px solid #edf2f7;
}

.result-row.selected {
  background: #e9fbf2;
}

.result-row strong {
  color: #0f172a;
  font-size: 15px;
}

.result-row span,
.result-row p {
  overflow: hidden;
  margin: 0;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-row em {
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #2563eb;
  font-style: normal;
  font-weight: 900;
}

.result-row i {
  color: #059669;
  font-style: normal;
  text-align: center;
}

.word-preview-card,
.today-plan-card {
  padding: 22px;
}

.word-preview-card {
  position: sticky;
  top: 20px;
}

.word-preview-card h3 {
  margin: 0;
  font-size: 28px;
}

.word-preview-card header p,
.word-meaning,
.preview-block p,
.preview-block small {
  margin: 6px 0 0;
  color: #475569;
  line-height: 1.55;
}

.word-preview-card header button {
  width: 34px;
  height: 34px;
  background: transparent;
  color: #64748b;
  font-size: 20px;
}

.word-state,
.status-badge {
  display: inline-flex;
  width: fit-content;
  min-height: 24px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #dcfce7;
  color: #047857;
  font-size: 12px;
  font-weight: 900;
}

.word-state {
  margin-top: 8px;
}

.word-meaning {
  color: #0f172a;
  font-weight: 800;
}

.preview-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #edf2f7;
}

.preview-block h3 {
  margin: 0;
  font-size: 15px;
}

.morpheme-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.morpheme-list article {
  min-width: 0;
  padding: 10px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
}

.morpheme-list strong,
.morpheme-list span,
.morpheme-list small {
  display: block;
}

.morpheme-list strong {
  color: #047857;
}

.morpheme-list span,
.morpheme-list small {
  margin-top: 3px;
  color: #475569;
  font-size: 11px;
}

.word-preview-card footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
}

.word-preview-card footer button {
  min-height: 38px;
  border: 1px solid #bbd7ca;
  background: #ffffff;
  color: #047857;
}

.word-preview-card footer .primary-action {
  grid-column: 1 / -1;
  border: 0;
  color: #ffffff;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 28px;
}

.mode-card {
  display: grid;
  justify-items: center;
  min-height: 250px;
  padding: 28px 22px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  text-align: center;
}

.mode-card--green { background: #f0fdf4; }
.mode-card--blue { background: #eff6ff; }
.mode-card--violet { background: #f5f3ff; }

.mode-icon {
  display: grid;
  width: 68px;
  height: 68px;
  place-items: center;
  border-radius: 18px;
  background: #ffffff;
  color: #059669;
  font-size: 28px;
  font-weight: 900;
}

.mode-card h2 {
  margin: 22px 0 0;
  font-size: 21px;
}

.mode-card p {
  max-width: 210px;
  margin: 12px 0 0;
  color: #64748b;
  line-height: 1.55;
}

.mode-card button {
  align-self: end;
  min-width: 150px;
  min-height: 42px;
  margin-top: 24px;
  background: #d1fae5;
  color: #047857;
}

.mode-card--blue button {
  background: #dbeafe;
  color: #2563eb;
}

.mode-card--violet button {
  background: #ede9fe;
  color: #6d28d9;
}

.insight-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 26px;
  overflow: hidden;
  border: 1px solid #dce7e1;
  border-radius: 8px;
}

.insight-strip article {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 4px 12px;
  align-items: center;
  padding: 18px;
}

.insight-strip article + article {
  border-left: 1px solid #e2e8f0;
}

.insight-strip div {
  display: grid;
  grid-row: 1 / span 2;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: #ecfdf5;
  color: #047857;
}

.insight-strip span {
  color: #64748b;
  font-size: 13px;
}

.today-plan-card header {
  align-items: center;
}

.today-plan-card header button {
  background: transparent;
  color: #047857;
}

.progress-ring {
  display: grid;
  width: 132px;
  height: 132px;
  place-items: center;
  margin: 28px auto 20px;
  border-radius: 50%;
  background:
    radial-gradient(circle, #ffffff 56%, transparent 57%),
    conic-gradient(#059669 0 60%, #d1fae5 60% 100%);
}

.progress-ring span {
  font-size: 26px;
  font-weight: 900;
}

.today-plan-card dl {
  display: grid;
  gap: 12px;
  margin: 0 0 22px;
}

.today-plan-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.today-plan-card dt,
.today-plan-card dd {
  margin: 0;
}

.today-plan-card dt {
  color: #64748b;
}

.today-plan-card dd {
  color: #0f172a;
  font-weight: 900;
}

.today-plan-card .primary-action {
  width: 100%;
}

.word-deposit-page {
  grid-template-columns: minmax(0, 1fr) 350px;
}

.word-deposit-main {
  min-width: 0;
}

.word-deposit-header {
  align-items: flex-start;
}

.word-deposit-header .page-heading span {
  max-width: 520px;
}

.deposit-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 22px;
  padding: 10px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.deposit-source-select {
  display: inline-grid;
  grid-template-columns: auto minmax(118px, 1fr);
  gap: 8px;
  align-items: center;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 850;
}

.deposit-source-select select {
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  font: inherit;
}

.deposit-segmented {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  min-height: 36px;
  padding: 4px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
}

.deposit-segmented button {
  min-height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-weight: 850;
  cursor: pointer;
}

.deposit-segmented button.active {
  background: #dcfce7;
  color: #047857;
}

.word-card-list {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.word-card-group {
  display: grid;
  gap: 8px;
}

.word-card-group h2 {
  margin: 0;
  color: #0f766e;
  font-size: 13px;
  font-weight: 950;
  letter-spacing: 0;
}

.word-card-row {
  display: grid;
  grid-template-columns: minmax(130px, 0.9fr) minmax(180px, 1.3fr) minmax(150px, 0.9fr) auto;
  gap: 16px;
  align-items: center;
  min-height: 74px;
  padding: 12px 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.word-card-row:hover,
.word-card-row:focus-visible {
  border-color: #99f6e4;
  outline: none;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.word-card-row.selected {
  border-color: #5eead4;
  background: #f0fdfa;
}

.word-card-primary,
.word-card-actions {
  display: flex;
  align-items: center;
}

.word-card-primary {
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.word-card-primary strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.2;
}

.word-card-primary small,
.word-card-meaning {
  color: #64748b;
  line-height: 1.55;
}

.word-card-meaning {
  display: -webkit-box;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.word-source-stack {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
}

.word-source-stack span,
.word-source-stack small {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 0 8px;
  border: 1px solid #ccfbf1;
  border-radius: 999px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
}

.word-card-actions {
  justify-content: flex-end;
  gap: 10px;
}

.favorite-action {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
  color: #d97706;
  font-size: 17px;
  cursor: pointer;
}

.review-plan-switch {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: #475569;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.review-plan-switch input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.review-plan-switch span {
  position: relative;
  width: 38px;
  height: 22px;
  border-radius: 999px;
  background: #e2e8f0;
  transition: background 0.16s ease;
}

.review-plan-switch span::after {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.22);
  content: "";
  transition: transform 0.16s ease;
}

.review-plan-switch input:checked + span {
  background: #0f9f8f;
}

.review-plan-switch input:checked + span::after {
  transform: translateX(16px);
}

.review-plan-switch em {
  min-width: 64px;
  color: #0f172a;
  font-style: normal;
}

.word-deposit-side {
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
}

.word-deposit-page--organizing {
  grid-template-columns: minmax(250px, 320px) minmax(0, 1fr);
  gap: 20px;
}

.word-deposit-page--organizing .word-deposit-main {
  padding: 18px;
}

.word-navigation-panel {
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 140px);
  overflow: auto;
}

.word-navigation-panel .word-deposit-header {
  align-items: stretch;
  flex-direction: column;
}

.word-navigation-panel .page-heading h1 {
  font-size: 22px;
}

.word-navigation-panel .collection-tools label {
  grid-template-columns: 32px minmax(0, 1fr);
}

.word-navigation-panel .collection-tools button {
  min-height: 38px;
}

.word-deposit-page--organizing .deposit-toolbar {
  align-items: stretch;
  flex-direction: column;
}

.word-deposit-page--organizing .deposit-source-select,
.word-deposit-page--organizing .deposit-segmented {
  width: 100%;
}

.word-deposit-page--organizing .deposit-segmented {
  flex-wrap: wrap;
}

.word-deposit-page--organizing .word-card-row {
  grid-template-columns: 1fr;
  gap: 7px;
  min-height: 92px;
}

.word-deposit-page--organizing .word-card-primary strong {
  font-size: 17px;
}

.word-deposit-page--organizing .word-card-meaning {
  -webkit-line-clamp: 2;
  font-size: 13px;
}

.word-deposit-page--organizing .word-card-actions {
  display: none;
}

.word-card-workspace {
  min-width: 0;
}

.daily-deposit-card {
  padding: 18px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.daily-deposit-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.daily-deposit-card h2 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
}

.daily-deposit-card header span,
.daily-empty {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.daily-deposit-timeline {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.daily-deposit-timeline article {
  display: grid;
  gap: 8px;
  padding-left: 14px;
  border-left: 2px solid #ccfbf1;
}

.daily-deposit-timeline time {
  color: #0f766e;
  font-size: 13px;
  font-weight: 950;
}

.daily-deposit-timeline button {
  display: grid;
  gap: 2px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  text-align: left;
  cursor: pointer;
}

.daily-deposit-timeline button span {
  color: #64748b;
  font-size: 12px;
}

.word-card-inspector .word-meaning {
  margin-top: 12px;
}

.word-card-inspector header > div:first-child {
  min-width: 0;
}

.word-card-inspector header > div:first-child span {
  display: block;
  margin-top: 7px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 850;
}

.word-title-editor {
  width: min(520px, 100%);
  border: 0;
  background: transparent;
  color: #0f172a;
  font: inherit;
  font-size: 36px;
  font-weight: 950;
  line-height: 1.16;
}

.word-title-editor:focus {
  outline: none;
  box-shadow: inset 0 -2px 0 #14b8a6;
}

.word-card-breadcrumb {
  margin: 0 0 10px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.word-card-header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.word-study-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 28px;
  min-height: 258px;
  margin-top: 14px;
  padding: 30px 34px;
  overflow: hidden;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background:
    linear-gradient(112deg, #ffffff 0%, #ffffff 52%, #effaf4 100%);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.word-study-hero__content {
  position: relative;
  z-index: 2;
  min-width: 0;
}

.word-study-hero__title-row {
  display: flex;
  gap: 14px;
  align-items: center;
}

.word-study-hero .word-title-editor {
  width: min(560px, 100%);
  font-size: clamp(48px, 5vw, 72px);
  letter-spacing: 0;
}

.hero-play-button {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #ffffff;
  color: #047857;
  font-size: 16px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1);
  cursor: pointer;
}

.hero-phonetic {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 6px 0 0;
  color: #64748b;
  font-size: 20px;
  font-weight: 750;
}

.hero-phonetic button {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #dce7e1;
  border-radius: 50%;
  background: #ffffff;
  color: #64748b;
  cursor: pointer;
}

.hero-meaning {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  max-width: 720px;
  margin: 16px 0 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.55;
}

.hero-meaning strong {
  color: #047857;
  font-size: 18px;
}

.hero-chip-row,
.hero-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 18px;
}

.hero-chip-row span {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  font-weight: 850;
}

.hero-action-row button {
  min-width: 138px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-size: 14px;
  font-weight: 950;
  cursor: pointer;
}

.hero-action-row .hero-ai-action {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
  box-shadow: 0 14px 28px rgba(4, 120, 87, 0.22);
}

.word-study-hero__visual {
  position: absolute;
  inset: 0 0 0 auto;
  width: 38%;
  opacity: 0.92;
  background:
    radial-gradient(circle at 28% 42%, rgba(187, 247, 208, 0.58), transparent 36%),
    linear-gradient(135deg, rgba(240, 253, 244, 0.12), rgba(187, 247, 208, 0.72));
}

.word-study-hero__visual span {
  position: absolute;
  right: 44px;
  bottom: 28px;
  width: 170px;
  height: 210px;
  border: 18px solid rgba(6, 95, 70, 0.08);
  border-bottom: 0;
  border-radius: 120px 120px 0 0;
  box-shadow:
    -38px 20px 0 -18px rgba(6, 95, 70, 0.08),
    -72px 42px 0 -26px rgba(20, 184, 166, 0.1);
}

.word-study-hero .word-card-header-actions {
  position: absolute;
  top: 22px;
  right: 24px;
  z-index: 3;
}

.word-preview-card header .word-card-header-actions button {
  width: auto;
  min-width: 38px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.word-preview-card header .word-card-header-actions .primary-action {
  border-color: #0f9f8f;
  background: #0f9f8f;
  color: #ffffff;
}

.word-preview-card header .word-card-header-actions .favorite-action {
  width: 36px;
  padding: 0;
  border-color: #fde68a;
  background: #fffbeb;
  color: #d97706;
  font-size: 16px;
}

.word-card-quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 16px;
}

.word-card-quick-actions > button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #ccfbf1;
  border-radius: 8px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.word-template-picker {
  padding: 16px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #f8fafc;
}

.word-template-picker header,
.word-card-canvas header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.word-template-picker header h3,
.word-card-canvas header h3 {
  margin: 0;
}

.word-template-picker header small,
.word-card-canvas header p,
.word-card-canvas header span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.ai-template-action {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #99f6e4;
  border-radius: 8px;
  background: #ccfbf1;
  color: #0f766e;
  font-size: 13px;
  font-weight: 950;
  cursor: pointer;
  white-space: nowrap;
}

.word-template-library-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.word-template-library-tabs button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.word-template-library-tabs button.active {
  border-color: #99f6e4;
  background: #ccfbf1;
  color: #0f766e;
}

.word-template-card-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.word-template-card {
  position: relative;
  display: grid;
  gap: 7px;
  min-height: 154px;
  padding: 12px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
  cursor: pointer;
}

.word-template-card strong {
  font-size: 14px;
}

.word-template-card span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.word-template-meta {
  display: grid;
  gap: 4px;
  color: #475569;
  font-size: 11px;
  line-height: 1.45;
}

.word-template-meta b {
  font-weight: 850;
}

.word-template-fields {
  color: #0f766e;
}

.word-template-layout-badge {
  width: fit-content;
  min-height: 24px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-weight: 950;
}

.word-template-card.active {
  border-color: #14b8a6;
  background: #f0fdfa;
  box-shadow: inset 3px 0 0 #14b8a6;
}

.word-template-square-entry {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  margin-top: 14px;
  padding: 14px;
  border: 1px dashed #99f6e4;
  border-radius: 8px;
  background: #f0fdfa;
}

.word-template-square-entry strong,
.word-template-square-entry span {
  display: block;
}

.word-template-square-entry span {
  margin-top: 4px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.word-template-square-entry button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #0f9f8f;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-weight: 950;
  white-space: nowrap;
  cursor: pointer;
}

.word-card-canvas {
  padding: 18px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.04);
}

.word-card-inspector--organizing .word-card-canvas {
  padding: 0;
  overflow: hidden;
  border-color: #dce7e1;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
}

.word-study-tabs {
  display: flex;
  gap: 28px;
  min-height: 54px;
  align-items: stretch;
  padding: 0 22px;
  border-bottom: 1px solid #e2e8f0;
}

.word-study-tabs button {
  position: relative;
  border: 0;
  background: transparent;
  color: #475569;
  font-size: 15px;
  font-weight: 900;
  cursor: pointer;
}

.word-study-tabs button.active {
  color: #047857;
}

.word-study-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 999px;
  background: #14b8a6;
  content: "";
}

.word-card-inspector--organizing .word-card-canvas header {
  padding: 18px 22px 0;
  border: 0;
}

.word-card-canvas header span {
  margin-top: 0;
  color: #0f766e;
  font-weight: 900;
}

.word-note-stack.template-field-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  margin: 16px 22px 0;
  overflow: hidden;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
}

.word-note-line.template-field-editor {
  display: grid;
  grid-template-columns: 36px 96px minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
  padding: 13px 14px;
}

.word-note-line + .word-note-line {
  border-top: 1px solid #edf2f7;
}

.word-note-line > span {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 950;
}

.word-note-line strong {
  padding-top: 3px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
}

.word-note-line.template-field-editor textarea {
  min-height: 34px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: #334155;
}

.word-note-line.template-field-editor textarea:focus {
  box-shadow: none;
}

.word-card-bottom-actions {
  display: flex;
  justify-content: flex-end;
  gap: 22px;
  align-items: center;
  margin-top: 16px;
  padding: 0 22px 18px;
}

.word-card-bottom-actions button {
  min-height: 32px;
  border: 0;
  background: transparent;
  color: #475569;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.word-card-bottom-actions span {
  margin-left: auto;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.word-template-modal-backdrop {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 32px;
  background: rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(4px);
}

.word-template-modal-panel {
  width: min(980px, 100%);
  max-height: min(760px, calc(100vh - 64px));
  overflow: auto;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.22);
}

.word-template-modal-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.word-template-modal-actions button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-size: 13px;
  font-weight: 950;
  cursor: pointer;
}

.template-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.template-field-editor {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.template-field-editor span {
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
}

.template-field-editor textarea {
  width: 100%;
  min-height: 118px;
  resize: vertical;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font: inherit;
  line-height: 1.6;
  padding: 11px 12px;
}

.template-field-editor textarea:focus {
  border-color: #14b8a6;
  outline: none;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.mastery-segmented,
.review-chip-card > div,
.note-structure-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.mastery-segmented button,
.review-chip-card button,
.note-structure-hints span {
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 850;
}

.mastery-segmented button,
.review-chip-card button {
  cursor: pointer;
}

.mastery-segmented button.active,
.review-chip-card button.active {
  border-color: #99f6e4;
  background: #ccfbf1;
  color: #0f766e;
}

.note-structure-hints span {
  background: #f8fafc;
}

.word-card-inspector--organizing {
  position: static;
  min-height: calc(100vh - 150px);
  padding: 28px;
}

.word-card-inspector--organizing .preview-title {
  font-size: 20px;
}

.word-card-inspector--organizing > header {
  padding-bottom: 18px;
  border-bottom: 1px solid #edf2f7;
}

.word-card-inspector--organizing > header h3 {
  font-size: 36px;
}

.word-card-inspector--organizing .word-meaning {
  font-size: 18px;
}

.compact-tag-block {
  padding-top: 12px;
}

.compact-tag-block h3 {
  font-size: 13px;
}

.word-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.word-detail-tags button,
.word-detail-tags span {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #ccfbf1;
  border-radius: 999px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.word-detail-tags button {
  cursor: pointer;
}

.user-note-editor {
  width: 100%;
  resize: vertical;
  margin-top: 10px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font: inherit;
  line-height: 1.6;
  padding: 10px 12px;
}

.user-note-editor:focus {
  border-color: #14b8a6;
  outline: none;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.source-record-list {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.source-record-list article {
  padding: 10px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.source-record-list strong {
  color: #0f172a;
  font-size: 13px;
}

.source-record-list p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.source-record-list article button {
  min-height: 30px;
  margin-top: 9px;
  padding: 0 10px;
  border: 1px solid #ccfbf1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.word-card-inspector--organizing footer {
  grid-template-columns: minmax(160px, 220px) 1fr;
  align-items: center;
}

.word-card-inspector--organizing footer .primary-action {
  grid-column: auto;
}

.word-card-inspector--organizing footer span {
  justify-self: end;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.collection-tools {
  gap: 10px;
}

.collection-tools label {
  display: grid;
  grid-template-columns: 32px 190px;
  align-items: center;
  min-height: 40px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
}

.filter-row {
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.filter-row button {
  background: #f8fafc;
  color: #475569;
}

.filter-row button.active {
  border-color: #bbf7d0;
  background: #dcfce7;
  color: #047857;
}

.add-group-button {
  margin-left: auto;
}

.collection-table {
  margin-top: 14px;
  overflow: hidden;
}

.collection-row {
  display: grid;
  grid-template-columns: 40px minmax(145px, 1fr) minmax(220px, 1.6fr) 92px 104px 78px;
  gap: 12px;
  align-items: center;
  min-height: 60px;
  padding: 0 16px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
}

.collection-row--head {
  min-height: 46px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.collection-row.selected {
  background: #f0fdf4;
}

.collection-empty {
  display: grid;
  min-height: 180px;
  place-items: center;
  padding: 24px;
  border-top: 1px solid #edf2f7;
  color: #64748b;
  text-align: center;
}

.collection-empty--error {
  color: #991b1b;
}

.collection-word {
  display: grid;
  border: 0;
  background: transparent;
  color: #0f172a;
  text-align: left;
}

.collection-word small {
  margin-top: 3px;
  color: #64748b;
}

.status-badge--new { background: #ffedd5; color: #ea580c; }
.status-badge--learning { background: #dbeafe; color: #2563eb; }
.status-badge--review { background: #fef3c7; color: #b45309; }
.status-badge--mastered { background: #dcfce7; color: #047857; }

.collection-actions {
  gap: 6px;
}

.collection-actions button {
  width: 30px;
  height: 30px;
  border: 1px solid #dce7e1;
  background: #ffffff;
  color: #64748b;
}

.table-footer {
  min-height: 64px;
  justify-content: space-between;
  padding: 0 18px;
  color: #64748b;
  font-size: 13px;
}

.table-footer div {
  gap: 8px;
}

.table-footer button,
.table-footer strong {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
}

.table-footer button {
  border: 1px solid #dce7e1;
  background: #ffffff;
}

.table-footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.table-footer strong {
  background: #059669;
  color: #ffffff;
}

.favorite-preview-stats {
  display: grid;
  gap: 10px;
  margin: 0;
}

.favorite-preview-stats div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.favorite-preview-stats dt,
.favorite-preview-stats dd {
  margin: 0;
}

.favorite-preview-stats dt {
  color: #64748b;
}

.favorite-preview-stats dd {
  color: #0f172a;
  font-weight: 900;
}

.saved-note-list {
  margin-top: 18px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

.saved-note-list > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid #edf2f7;
}

.saved-note-list h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.saved-note-list header span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.saved-note-list header button {
  min-height: 34px;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  background: #ffffff;
  color: #047857;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 800;
}

.saved-note-list header button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.saved-note-empty {
  display: grid;
  min-height: 120px;
  place-items: center;
  padding: 24px;
  color: #64748b;
  text-align: center;
}

.saved-note-empty--error {
  color: #991b1b;
}

.saved-note-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  padding: 14px;
}

.saved-note-item {
  min-width: 0;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.saved-note-item strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.saved-note-item p {
  display: -webkit-box;
  min-height: 42px;
  margin: 8px 0;
  overflow: hidden;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.saved-note-item small {
  color: #64748b;
  font-size: 12px;
}

.saved-note-item footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.saved-note-item footer button {
  min-height: 30px;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  background: #ecfdf5;
  color: #047857;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.saved-note-item footer button:hover,
.saved-note-item footer button:focus-visible {
  border-color: #047857;
  outline: none;
}

.stats-kpis {
  gap: 16px;
}

.metric-card {
  flex: 1;
  min-width: 0;
  padding: 18px;
}

.metric-card div {
  display: grid;
  width: 50px;
  height: 50px;
  place-items: center;
  border-radius: 50%;
  font-size: 20px;
  font-weight: 900;
}

.metric-card span,
.metric-card small {
  display: block;
  margin-top: 8px;
  color: #64748b;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  font-size: 30px;
  line-height: 1.1;
}

.tone-green { background: #dcfce7; color: #059669; }
.tone-blue { background: #dbeafe; color: #2563eb; }
.tone-amber { background: #fef3c7; color: #f59e0b; }
.tone-purple { background: #ede9fe; color: #7c3aed; }

.stats-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) minmax(260px, 0.85fr);
  gap: 16px;
  margin-top: 16px;
}

.chart-panel,
.milestone-panel {
  padding: 20px;
}

.chart-panel header {
  justify-content: space-between;
}

.line-chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
  height: 240px;
  margin-top: 22px;
  border-bottom: 1px solid #dce7e1;
  background:
    linear-gradient(to top, #edf2f7 1px, transparent 1px) 0 0 / 100% 48px;
}

.line-chart span {
  display: grid;
  gap: 8px;
  justify-items: center;
  height: 100%;
  align-items: end;
}

.line-chart i {
  width: 100%;
  height: var(--value);
  max-width: 42px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(180deg, #10b981, #a7f3d0);
}

.line-chart small {
  color: #64748b;
}

.donut-layout {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 22px;
  align-items: center;
  margin-top: 28px;
}

.donut-chart {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background:
    radial-gradient(circle, #ffffff 56%, transparent 57%),
    conic-gradient(#059669 0 46%, #3b82f6 46% 86%, #f59e0b 86% 100%);
}

.donut-layout ul {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  color: #334155;
  list-style: none;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 8px;
  border-radius: 50%;
}

.legend-dot.mastered { background: #059669; }
.legend-dot.learning { background: #3b82f6; }
.legend-dot.new { background: #f59e0b; }
.legend-dot.idle { background: #cbd5e1; }

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 10px;
  margin-top: 24px;
}

.calendar-grid span {
  display: grid;
  aspect-ratio: 1;
  place-items: center;
  border-radius: 50%;
  background: #e2e8f0;
  color: transparent;
  font-size: 0;
}

.calendar-grid span.done {
  background: #059669;
}

.calendar-panel footer {
  display: flex;
  gap: 18px;
  margin-top: 18px;
  color: #64748b;
  font-size: 13px;
}

.milestone-panel {
  margin-top: 16px;
}

.milestone-track {
  gap: 12px;
  margin-top: 18px;
}

.milestone-track article {
  display: grid;
  flex: 1;
  grid-template-columns: 34px 1fr;
  gap: 10px;
  align-items: center;
  min-height: 54px;
  color: #047857;
}

.milestone-track span {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #dcfce7;
  font-size: 12px;
  font-weight: 900;
}

.milestone-track article.locked {
  color: #94a3b8;
}

.milestone-track article.locked span {
  background: #e2e8f0;
}

@media (max-width: 1180px) {
  .search-page,
  .mode-page,
  .collection-page,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .vocabulary-nav {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .word-preview-card {
    position: static;
  }
}

@media (max-width: 820px) {
  .vocabulary-shell {
    padding: 12px;
  }

  .dictionary-search,
  .search-meta-grid,
  .mode-grid,
  .word-template-card-list,
  .template-field-grid,
  .insight-strip,
  .stats-kpis,
  .donut-layout,
  .morpheme-list {
    grid-template-columns: 1fr;
  }

  .dictionary-search {
    padding: 8px;
  }

  .dictionary-search > span {
    display: none;
  }

  .dictionary-search input,
  .dictionary-search select,
  .dictionary-search button {
    width: 100%;
    margin: 0;
  }

  .word-study-hero {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .word-study-hero__visual {
    display: none;
  }

  .word-study-hero .word-card-header-actions {
    position: static;
    justify-content: flex-start;
    margin-top: 16px;
  }

  .word-study-hero__title-row,
  .hero-action-row,
  .word-card-bottom-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .word-template-modal-backdrop {
    padding: 14px;
    place-items: end stretch;
  }

  .word-template-modal-panel {
    width: 100%;
    max-height: calc(100vh - 28px);
  }

  .word-template-modal-actions,
  .word-template-square-entry {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-play-button {
    width: 42px;
    height: 42px;
  }

  .word-note-line.template-field-editor {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .word-note-line strong {
    grid-column: 2;
  }

  .word-note-line textarea {
    grid-column: 1 / -1;
  }

  .result-row,
  .collection-row {
    grid-template-columns: 1fr;
    padding: 14px;
  }

  .collection-row--head {
    display: none;
  }

  .collection-tools,
  .milestone-track {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .vocabulary-nav {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
    width: 100%;
    overflow: visible;
  }

  .vocabulary-nav button {
    justify-content: center;
    width: 100%;
    min-width: 0;
    padding: 0 6px;
  }
}

.vocabulary-card-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
}

.vocabulary-card-page > * {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.vocabulary-card-page__skeleton,
.vocabulary-card-page__state {
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.vocabulary-card-page__skeleton {
  display: grid;
  min-height: 620px;
  align-content: start;
  gap: 22px;
  padding: 28px;
}

.vocabulary-card-page__skeleton > span {
  color: #64748b;
  font-size: 13px;
}

.vocabulary-card-page__skeleton-title,
.vocabulary-card-page__skeleton-toolbar,
.vocabulary-card-page__skeleton-body {
  border-radius: 6px;
  background: #edf5f1;
}

.vocabulary-card-page__skeleton-title {
  width: min(46%, 360px);
  height: 42px;
}

.vocabulary-card-page__skeleton-toolbar {
  width: min(70%, 620px);
  height: 36px;
}

.vocabulary-card-page__skeleton-body {
  width: 100%;
  height: 410px;
}

.vocabulary-card-page__state {
  min-height: 420px;
  display: grid;
  place-items: center;
  padding: 28px;
  text-align: center;
}

.vocabulary-card-page__state h1,
.vocabulary-card-page__state p {
  margin: 0;
}

.vocabulary-card-page__state h1 {
  color: #0f172a;
  font-size: 22px;
}

.vocabulary-card-page__state p {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.vocabulary-card-page__state-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}

.vocabulary-card-page__state-actions button {
  min-height: 36px;
  border: 1px solid #059669;
  border-radius: 6px;
  background: #ffffff;
  color: #047857;
  padding: 0 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.vocabulary-card-page__state-actions button:last-child {
  background: #059669;
  color: #ffffff;
}
</style>
