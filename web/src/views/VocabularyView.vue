<template>
  <main class="learning-shell">
    <header class="learning-topbar">
      <div class="brand-block">
        <div class="brand-mark" aria-hidden="true">□</div>
        <div>
          <p class="brand-name">English Learning</p>
          <h1>单词学习</h1>
        </div>
      </div>

      <form class="learning-search" @submit.prevent="submitLookup">
        <span class="search-icon" aria-hidden="true">⌕</span>
        <input
          v-model="query"
          type="search"
          autocomplete="off"
          spellcheck="false"
          placeholder="搜索单词、短语或句子，例如 innovative 或 play a vital role"
          aria-label="搜索单词、短语或句子"
        >
        <select v-model="language" aria-label="选择词典语言">
          <option value="en-gb">en-gb</option>
          <option value="en-us">en-us</option>
        </select>
        <button type="submit" :disabled="loading">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </form>

      <div class="top-actions" aria-label="用户操作">
        <button type="button" class="icon-button" aria-label="通知">♢</button>
        <div class="user-avatar" aria-label="当前用户"></div>
      </div>
    </header>

    <nav class="learning-tabs" aria-label="单词学习视图">
      <button
        v-for="tab in tabs"
        :key="tab"
        type="button"
        :class="{ active: tab === activeTab }"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </nav>

    <section class="api-status-panel" aria-label="单词端接口接入状态">
      <header>
        <div>
          <p>接口接入状态</p>
          <h2>单词端数据源验收标记</h2>
        </div>
        <span>当前页面</span>
      </header>
      <div class="api-status-grid">
        <article v-for="item in apiStatusItems" :key="item.name" class="api-status-item">
          <mark :class="`api-badge api-badge--${item.status}`">{{ apiStatusLabel[item.status] }}</mark>
          <strong>{{ item.name }}</strong>
          <span>{{ item.endpoint }}</span>
          <small>{{ item.note }}</small>
        </article>
      </div>
    </section>

    <section class="metric-grid" aria-label="学习概览">
      <article v-for="metric in metrics" :key="metric.title" class="metric-card">
        <div class="metric-icon" :class="metric.tone">{{ metric.icon }}</div>
        <div>
          <p>{{ metric.title }}</p>
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.hint }} · 本地模拟</span>
        </div>
      </article>
    </section>

    <section class="learning-layout">
      <article class="word-table-card">
        <header class="card-header">
          <div>
            <h2>来自昨日对话的重点单词</h2>
            <p>根据对话采集、清洗与学习价值评分生成</p>
          </div>
          <mark class="api-badge api-badge--mock">列表未接用户词库 API</mark>
          <div class="table-actions">
            <button type="button">全部状态 ⌄</button>
            <button type="button">批量操作 ⌄</button>
          </div>
        </header>

        <div class="word-table" role="table" aria-label="昨日对话重点单词">
          <div class="word-row word-row--head" role="row">
            <span role="columnheader">单词</span>
            <span role="columnheader">词性</span>
            <span role="columnheader">中文释义</span>
            <span role="columnheader">例句</span>
            <span role="columnheader">来源</span>
            <span role="columnheader">出现</span>
            <span role="columnheader">学习状态</span>
            <span role="columnheader">操作</span>
          </div>

          <button
            v-for="word in words"
            :key="word.id"
            type="button"
            class="word-row word-row--item"
            :class="{ selected: word.id === selectedWordId }"
            role="row"
            @click="selectedWordId = word.id"
          >
            <span class="word-cell">
              <span class="favorite" aria-hidden="true">☆</span>
              <strong>{{ word.word }}</strong>
              <small>{{ word.phonetic }} ♪</small>
            </span>
            <span><mark class="pos-tag" :class="`pos-tag--${word.partOfSpeech}`">{{ word.partOfSpeech }}.</mark></span>
            <span>{{ word.meaning }}</span>
            <span class="example-cell" v-html="highlightExample(word.example, word.word)"></span>
            <span><mark class="source-tag">对话</mark></span>
            <span class="count-cell">{{ word.occurrences }}</span>
            <span class="status-cell">
              <i :class="statusClass(word.status)" aria-hidden="true"></i>
              {{ word.status }}
            </span>
            <span class="row-actions">
              <button type="button" @click.stop="addTodayReview(word.id)">加入复习 <small>本地</small></button>
              <button type="button" @click.stop="selectedWordId = word.id">查看详情 <small>静态</small> 〉</button>
            </span>
          </button>
        </div>

        <footer class="table-footer">
          <span>共 {{ words.length }} 条</span>
          <div class="pager" aria-label="分页">
            <button type="button">‹</button>
            <strong>1</strong>
            <button type="button">›</button>
          </div>
          <span>20 条/页</span>
        </footer>
      </article>

      <article v-if="selectedWord" class="word-detail-panel">
        <header class="detail-header">
          <h2>单词详情</h2>
          <button type="button" aria-label="关闭详情">×</button>
        </header>

        <section class="detail-hero">
          <div>
            <div class="word-title-line">
              <h3>{{ selectedWord.word }}</h3>
              <button type="button" aria-label="收藏单词">☆</button>
            </div>
            <mark class="api-badge api-badge--partial">详情静态 + 词典查询已接</mark>
            <p class="phonetic">{{ selectedWord.phonetic }} ♪</p>
            <p>
              <mark class="pos-tag pos-tag--adj">{{ selectedWord.partOfSpeech }}.</mark>
              <span>{{ selectedWord.meaning }}</span>
            </p>
          </div>
          <aside class="mastery-card">
            <strong>{{ selectedWord.status }}</strong>
            <span>熟悉度 {{ selectedWord.mastery }}%</span>
            <i><em :style="{ width: `${selectedWord.mastery}%` }"></em></i>
          </aside>
        </section>

        <section v-if="errorMessage" class="lookup-feedback lookup-feedback--error">
          <strong>{{ errorMessage }}</strong>
          <span v-if="debugMessage">{{ debugMessage }}</span>
        </section>

        <section v-if="result" class="lookup-feedback">
          <header>
            <strong>Oxford Dictionaries</strong>
            <span>{{ result.language }} · {{ lastLookupAt }}</span>
          </header>
          <p v-if="primaryPhonetic?.text">/{{ primaryPhonetic.text }}/</p>
          <button
            v-if="primaryPhonetic?.audioUrl"
            type="button"
            @click="playAudio(primaryPhonetic.audioUrl)"
          >
            播放发音
          </button>
          <div v-for="entry in result.entries" :key="entry.partOfSpeech || 'unknown'" class="dictionary-entry">
            <h4>{{ entry.partOfSpeech || 'unknown' }}</h4>
            <ol>
              <li
                v-for="(definition, index) in visibleDefinitions(entry)"
                :key="`${entry.partOfSpeech}-${index}-${definition}`"
              >
                <p>{{ definition }}</p>
                <small v-if="entry.examples[index]">{{ entry.examples[index] }}</small>
              </li>
            </ol>
            <button
              v-if="entry.definitions.length > maxVisibleDefinitions"
              type="button"
              class="expand-button"
              @click="toggleEntry(entry.partOfSpeech || 'unknown')"
            >
              {{ isExpanded(entry.partOfSpeech || 'unknown') ? '收起' : '展开更多' }}
            </button>
          </div>
        </section>

        <section class="meaning-card">
          <h4>释义与用法</h4>
          <p>{{ selectedWord.usage }}</p>
        </section>

        <section class="detail-section">
          <h4>例句 ♪</h4>
          <p class="detail-example" v-html="highlightExample(selectedWord.example, selectedWord.word)"></p>
          <small>{{ selectedWord.translation }}</small>
        </section>

        <section class="detail-section">
          <h4>词根词缀联想记忆</h4>
          <div class="morpheme-grid">
            <article v-for="part in selectedWord.morphemes" :key="part.name">
              <strong>{{ part.name }}</strong>
              <span>{{ part.meaning }}</span>
              <small>{{ part.hint }}</small>
            </article>
          </div>
        </section>

        <section class="detail-section">
          <h4>近义词</h4>
          <div class="chip-grid">
            <span v-for="item in selectedWord.synonyms" :key="item">{{ item }}</span>
          </div>
        </section>

        <section class="detail-section">
          <h4>词族 / 派生词</h4>
          <div class="derived-grid">
            <article v-for="item in selectedWord.derived" :key="item.word">
              <strong>{{ item.word }}</strong>
              <small>{{ item.partOfSpeech }}. {{ item.meaning }}</small>
            </article>
          </div>
        </section>

        <footer class="detail-actions">
          <button type="button" class="primary-action" @click="addTodayReview(selectedWord.id)">加入今日复习 <small>未接 API</small></button>
          <button type="button" @click="markSelectedMastered">标记已掌握 <small>本地</small></button>
          <button type="button">收藏 <small>未接 API</small></button>
        </footer>
      </article>

      <aside class="study-sidebar">
        <section class="side-card">
          <header>
            <h2>今日学习计划</h2>
            <mark class="api-badge api-badge--mock">静态</mark>
            <button type="button">设置</button>
          </header>
          <ul class="plan-list">
            <li v-for="item in studyPlan" :key="item.label">
              <span>{{ item.icon }}</span>
              <strong>{{ item.label }}</strong>
              <em>{{ item.done }} / {{ item.total }}</em>
            </li>
          </ul>
        </section>

        <section class="side-card">
          <header>
            <h2>复习队列 <small>({{ reviewQueue.length }})</small></h2>
            <mark class="api-badge api-badge--missing">未接 learning_review_queue</mark>
            <button type="button">全部</button>
          </header>
          <ul class="review-list">
            <li v-for="item in reviewQueue" :key="item.word">
              <span class="review-dot" :class="item.tone"></span>
              <strong>{{ item.word }}</strong>
              <mark>★</mark>
              <i>
                <em
                  v-for="step in 9"
                  :key="step"
                  :class="{ filled: step <= item.level }"
                ></em>
              </i>
            </li>
          </ul>
          <button type="button" class="primary-action full-width">开始复习 <small>未接 API</small></button>
        </section>

        <section class="side-card">
          <header>
            <h2>学习建议</h2>
            <mark class="api-badge api-badge--mock">静态</mark>
            <button type="button">换一批</button>
          </header>
          <ul class="advice-list">
            <li v-for="item in advice" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="side-card">
          <header>
            <h2>学习成就</h2>
            <mark class="api-badge api-badge--mock">静态</mark>
            <button type="button">查看全部</button>
          </header>
          <div class="badge-grid">
            <article v-for="badge in badges" :key="badge.title">
              <div :class="badge.tone">{{ badge.icon }}</div>
              <strong>{{ badge.title }}</strong>
              <span>{{ badge.subtitle }}</span>
            </article>
          </div>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { lookupDictionary } from '@/api/dictionary'
import type { DictionaryEntry, DictionaryLanguage, DictionaryLookupResponse } from '@/api/dictionary'

type LearningStatus = '新学' | '学习中' | '复习中' | '已掌握'
type ApiStatus = 'connected' | 'partial' | 'mock' | 'missing'

interface LearningWord {
  id: string
  word: string
  phonetic: string
  partOfSpeech: 'adj' | 'n' | 'v'
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
}

const maxVisibleDefinitions = 3
const tabs = ['今日复习', '我的单词', '短语句子', '学习统计']
const apiStatusLabel: Record<ApiStatus, string> = {
  connected: '已接入',
  partial: '部分接入',
  mock: '本地模拟',
  missing: '未接入',
}
const apiStatusItems: Array<{
  name: string
  endpoint: string
  status: ApiStatus
  note: string
}> = [
  {
    name: 'Oxford 单词查询',
    endpoint: 'GET /api/dictionary/lookup',
    status: 'connected',
    note: '顶部搜索框已调用真实后端词典接口。',
  },
  {
    name: '对话词句候选池',
    endpoint: 'learning_raw_candidate / learning_evidence',
    status: 'partial',
    note: '后端已入库，单词页还没有用户侧读取 API。',
  },
  {
    name: '重点单词列表',
    endpoint: 'GET /api/learning/vocabulary/items',
    status: 'missing',
    note: '当前表格仍使用前端静态数据。',
  },
  {
    name: '每日复习队列',
    endpoint: 'learning_review_queue',
    status: 'missing',
    note: '队列表和前端读取接口尚未落地。',
  },
  {
    name: '学习操作',
    endpoint: 'POST review / mastery / favorite',
    status: 'mock',
    note: '加入复习、标记掌握、收藏目前只改本地状态。',
  },
]
const activeTab = ref('今日复习')
const selectedWordId = ref('innovative')
const query = ref('')
const language = ref<DictionaryLanguage>('en-gb')
const loading = ref(false)
const result = ref<DictionaryLookupResponse | null>(null)
const errorMessage = ref('')
const debugMessage = ref('')
const expandedEntries = ref<Set<string>>(new Set())
const lastLookupAt = ref('')

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
  },
  {
    id: 'stakeholder',
    word: 'stakeholder',
    phonetic: '/ˈsteɪk.hoʊl.dər/',
    partOfSpeech: 'n',
    meaning: '利益相关者；干系人',
    example: 'It is important to consider the needs of all stakeholders.',
    translation: '考虑所有利益相关者的需求很重要。',
    occurrences: 2,
    status: '学习中',
    mastery: 42,
    usage: '用于商业、政策、项目管理写作，指会被决策影响或能影响决策的人或组织。',
    morphemes: [
      { name: 'stake', meaning: '利害关系', hint: '表示投入或风险' },
      { name: 'holder', meaning: '持有者', hint: '表示角色身份' },
    ],
    synonyms: ['participant 参与者', 'partner 伙伴', 'shareholder 股东'],
    derived: [
      { word: 'stake', partOfSpeech: 'n', meaning: '利害关系' },
    ],
    inReview: true,
  },
  {
    id: 'optimize',
    word: 'optimize',
    phonetic: '/ˈɑːp.tɪ.maɪz/',
    partOfSpeech: 'v',
    meaning: '优化；使最优化',
    example: 'We need to optimize our workflow to improve efficiency.',
    translation: '我们需要优化工作流程以提高效率。',
    occurrences: 1,
    status: '复习中',
    mastery: 58,
    usage: '适合描述流程、系统、策略或学习方法的改进。',
    morphemes: [
      { name: 'optim', meaning: '最好', hint: '来自 optimum' },
      { name: '-ize', meaning: '使成为', hint: '动词后缀' },
    ],
    synonyms: ['improve 改进', 'refine 完善', 'enhance 增强'],
    derived: [
      { word: 'optimization', partOfSpeech: 'n', meaning: '优化' },
      { word: 'optimal', partOfSpeech: 'adj', meaning: '最佳的' },
    ],
    inReview: true,
  },
])

const metrics = [
  { title: '今日待复习', value: '18', hint: '较昨日 -6', icon: '□', tone: 'tone-green' },
  { title: '新增单词', value: '26', hint: '较昨日 +8', icon: '+', tone: 'tone-blue' },
  { title: '已掌握', value: '328', hint: '总计', icon: '★', tone: 'tone-amber' },
  { title: '连续学习', value: '12 天', hint: '超越超过 12 天', icon: '●', tone: 'tone-purple' },
]

const studyPlan = [
  { icon: '●', label: '复习 20 个单词', done: 18, total: 20 },
  { icon: '↻', label: '学习 5 个句子', done: 3, total: 5 },
  { icon: '○', label: '完成 1 次测试', done: 0, total: 1 },
]

const reviewQueue = [
  { word: 'perception', tone: 'tone-amber', level: 3 },
  { word: 'launch', tone: 'tone-amber', level: 3 },
  { word: 'stakeholder', tone: 'tone-blue', level: 4 },
  { word: 'sustainable', tone: 'tone-purple', level: 2 },
  { word: 'optimize', tone: 'tone-gray', level: 3 },
]

const advice = [
  '建议在早上或者注意力较高的时候进行学习',
  '尝试使用间隔复习法，强化记忆',
  '每天完成测试，巩固学习效果',
]

const badges = [
  { title: '连续 12 天', subtitle: '学习达人', icon: '★', tone: 'badge-gold' },
  { title: '掌握单词 300+', subtitle: '词汇大师', icon: '⚖', tone: 'badge-silver' },
  { title: '完成测试 20 次', subtitle: '坚持不懈', icon: '✿', tone: 'badge-bronze' },
]

const selectedWord = computed(() => words.value.find((word) => word.id === selectedWordId.value) ?? words.value[0])
const primaryPhonetic = computed(() => result.value?.phonetics.find((item) => item.text || item.audioUrl))

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
  expandedEntries.value = new Set()

  try {
    result.value = await lookupDictionary(word, language.value)
    lastLookupAt.value = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(Date.now())
  } catch (err) {
    result.value = null
    const normalized = normalizeError(err)
    errorMessage.value = normalized.message
    debugMessage.value = import.meta.env.DEV ? normalized.debug : ''
  } finally {
    loading.value = false
  }
}

function addTodayReview(wordId: string) {
  const word = words.value.find((item) => item.id === wordId)
  if (!word) return
  word.inReview = true
  if (word.status === '新学') {
    word.status = '学习中'
  }
}

function markSelectedMastered() {
  if (!selectedWord.value) return
  selectedWord.value.status = '已掌握'
  selectedWord.value.mastery = 100
}

function visibleDefinitions(entry: DictionaryEntry) {
  const key = entry.partOfSpeech || 'unknown'
  return isExpanded(key)
    ? entry.definitions
    : entry.definitions.slice(0, maxVisibleDefinitions)
}

function isExpanded(key: string) {
  return expandedEntries.value.has(key)
}

function toggleEntry(key: string) {
  const next = new Set(expandedEntries.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedEntries.value = next
}

function playAudio(audioUrl: string) {
  void new Audio(audioUrl).play()
}

function statusClass(status: LearningStatus) {
  return {
    'status-dot': true,
    'status-dot--new': status === '新学',
    'status-dot--learning': status === '学习中',
    'status-dot--review': status === '复习中',
    'status-dot--mastered': status === '已掌握',
  }
}

function highlightExample(example: string, word: string) {
  return example.replace(new RegExp(`(${word})`, 'gi'), '<strong>$1</strong>')
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
.learning-shell {
  min-height: 100vh;
  padding: 16px 22px 28px;
  overflow: auto;
  background: #f8fafc;
  color: #0f172a;
}

.learning-topbar {
  display: grid;
  grid-template-columns: 420px minmax(520px, 1fr) auto;
  gap: 22px;
  align-items: center;
}

.brand-block,
.top-actions,
.word-title-line,
.card-header,
.table-actions,
.detail-header,
.detail-hero,
.detail-actions,
.side-card header,
.plan-list li,
.review-list li {
  display: flex;
  align-items: center;
}

.brand-block {
  gap: 12px;
}

.brand-mark {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 2px solid #059669;
  border-radius: 7px;
  color: #059669;
  font-size: 12px;
  font-weight: 800;
}

.brand-name {
  margin: 0;
  color: #047857;
  font-size: 15px;
  font-weight: 800;
}

.learning-topbar h1 {
  margin: 0;
  color: #020617;
  font-size: 30px;
  line-height: 1.1;
}

.learning-search {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 118px 78px;
  align-items: center;
  min-height: 48px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.search-icon {
  color: #64748b;
  text-align: center;
}

.learning-search input,
.learning-search select {
  height: 46px;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  outline: none;
}

.learning-search input {
  padding-right: 12px;
  font-size: 15px;
}

.learning-search input::placeholder {
  color: #94a3b8;
}

.learning-search select {
  border-left: 1px solid #e2e8f0;
  padding: 0 12px;
  font-weight: 700;
}

.learning-search button,
.primary-action {
  min-height: 38px;
  border: 0;
  border-radius: 8px;
  background: #059669;
  color: #ffffff;
  font-weight: 800;
  box-shadow: 0 10px 24px rgba(5, 150, 105, 0.18);
}

.learning-search button {
  margin-right: 5px;
}

.learning-search button:disabled {
  background: #94a3b8;
  box-shadow: none;
}

.top-actions {
  gap: 14px;
}

.icon-button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #334155;
  font-size: 22px;
}

.user-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 28%, #059669 0 14%, transparent 15%),
    radial-gradient(circle at 50% 78%, #059669 0 32%, transparent 33%),
    #dcfce7;
}

.learning-tabs {
  display: inline-grid;
  grid-template-columns: repeat(4, 108px);
  margin-top: 16px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
}

.learning-tabs button {
  height: 32px;
  border: 0;
  border-right: 1px solid #e2e8f0;
  background: transparent;
  color: #334155;
  font-weight: 700;
}

.learning-tabs button:last-child {
  border-right: 0;
}

.learning-tabs button.active {
  border-radius: 8px;
  background: #e6f6ef;
  color: #047857;
}

.api-status-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #d9e5de;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.05);
}

.api-status-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.api-status-panel p,
.api-status-panel h2 {
  margin: 0;
}

.api-status-panel p {
  color: #059669;
  font-size: 13px;
  font-weight: 800;
}

.api-status-panel h2 {
  margin-top: 4px;
  font-size: 16px;
}

.api-status-panel header > span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.api-status-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.api-status-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.api-status-item strong,
.api-status-item span,
.api-status-item small {
  display: block;
}

.api-status-item strong {
  margin-top: 8px;
  font-size: 14px;
}

.api-status-item span {
  margin-top: 5px;
  overflow: hidden;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.api-status-item small {
  margin-top: 7px;
  color: #64748b;
  line-height: 1.45;
}

.api-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 22px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.api-badge--connected {
  border-color: #bbf7d0;
  background: #dcfce7;
  color: #047857;
}

.api-badge--partial {
  border-color: #bfdbfe;
  background: #dbeafe;
  color: #1d4ed8;
}

.api-badge--mock {
  border-color: #fde68a;
  background: #fef3c7;
  color: #92400e;
}

.api-badge--missing {
  border-color: #fecaca;
  background: #fee2e2;
  color: #b91c1c;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  max-width: 840px;
  margin-top: 16px;
}

.metric-card,
.word-table-card,
.word-detail-panel,
.side-card {
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.metric-card {
  display: flex;
  gap: 16px;
  align-items: center;
  min-height: 92px;
  padding: 16px;
}

.metric-icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  font-size: 22px;
  font-weight: 900;
}

.metric-card p,
.metric-card span {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.metric-card p {
  color: #334155;
  font-weight: 800;
}

.metric-card strong {
  display: block;
  margin-top: 2px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.05;
}

.tone-green { background: #dcfce7; color: #059669; }
.tone-blue { background: #dbeafe; color: #2563eb; }
.tone-amber { background: #fef3c7; color: #f59e0b; }
.tone-purple { background: #ede9fe; color: #7c3aed; }
.tone-gray { background: #e2e8f0; color: #64748b; }

.learning-layout {
  display: grid;
  grid-template-columns: minmax(800px, 0.95fr) minmax(420px, 0.75fr) 304px;
  gap: 14px;
  align-items: start;
  margin-top: 16px;
}

.word-table-card {
  min-width: 0;
  overflow: hidden;
}

.card-header {
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.card-header h2,
.detail-header h2,
.side-card h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.card-header p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 12px;
}

.table-actions {
  gap: 8px;
}

.table-actions button,
.detail-header button,
.detail-actions button,
.side-card header button,
.row-actions button,
.pager button,
.expand-button,
.lookup-feedback button {
  border: 1px solid #bbd7ca;
  border-radius: 8px;
  background: #ffffff;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.table-actions button {
  height: 30px;
  padding: 0 10px;
}

.word-table {
  display: grid;
  padding: 0 8px;
}

.word-row {
  display: grid;
  grid-template-columns: minmax(130px, 1.05fr) 52px minmax(96px, 0.8fr) minmax(170px, 1.5fr) 48px 38px 72px 78px;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  border: 0;
  border-bottom: 1px solid #e5e7eb;
  background: transparent;
  text-align: left;
}

.word-row--head {
  min-height: 42px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.word-row--item {
  min-height: 82px;
  padding: 0;
  color: #334155;
  font-size: 12px;
  cursor: pointer;
}

.word-row--item.selected {
  border: 1px solid #b9ead2;
  border-radius: 8px;
  background: #e9fbf2;
}

.word-cell {
  display: grid;
  grid-template-columns: 24px 1fr;
  column-gap: 6px;
}

.favorite {
  grid-row: 1 / span 2;
  align-self: center;
  color: #94a3b8;
  font-size: 18px;
}

.word-cell strong {
  color: #0f172a;
  font-size: 16px;
}

.word-cell small {
  color: #64748b;
}

.pos-tag,
.source-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-style: normal;
  font-weight: 800;
}

.pos-tag--adj { background: #f3e8ff; color: #7c3aed; }
.pos-tag--n { background: #dbeafe; color: #2563eb; }
.pos-tag--v { background: #dcfce7; color: #059669; }
.source-tag { background: #e8f8f0; color: #047857; }

.example-cell {
  line-height: 1.45;
}

.example-cell :deep(strong),
.detail-example :deep(strong) {
  color: #047857;
}

.count-cell {
  color: #0f172a;
  font-weight: 800;
  text-align: center;
}

.status-cell {
  display: flex;
  gap: 7px;
  align-items: center;
  font-weight: 700;
}

.status-dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot--new,
.status-dot--mastered { background: #059669; }
.status-dot--learning { background: #2563eb; }
.status-dot--review { background: #f59e0b; }

.row-actions {
  display: grid;
  gap: 6px;
}

.row-actions button {
  height: 28px;
  padding: 0 6px;
}

.table-footer {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  color: #334155;
  font-size: 13px;
}

.pager {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pager button,
.pager strong {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
}

.pager strong {
  background: #059669;
  color: #ffffff;
}

.word-detail-panel {
  min-width: 0;
  overflow: hidden;
}

.detail-header {
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-header button {
  width: 28px;
  height: 28px;
  border: 0;
  color: #475569;
  font-size: 22px;
}

.detail-hero {
  justify-content: space-between;
  gap: 18px;
  padding: 24px 24px 12px;
}

.word-title-line {
  gap: 8px;
}

.word-title-line h3 {
  margin: 0;
  font-size: 34px;
  line-height: 1.1;
}

.word-title-line button {
  border: 0;
  background: transparent;
  color: #94a3b8;
  font-size: 24px;
}

.phonetic {
  margin: 8px 0 10px;
  color: #475569;
  font-size: 14px;
}

.mastery-card {
  min-width: 116px;
  padding: 14px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
}

.mastery-card strong,
.mastery-card span {
  display: block;
}

.mastery-card span {
  margin-top: 8px;
  font-size: 12px;
}

.mastery-card i,
.review-list i {
  display: flex;
  overflow: hidden;
  background: #d1fae5;
}

.mastery-card i {
  height: 6px;
  margin-top: 10px;
  border-radius: 999px;
}

.mastery-card em {
  display: block;
  border-radius: inherit;
  background: #059669;
}

.lookup-feedback,
.meaning-card,
.detail-section {
  margin: 12px 24px 0;
}

.lookup-feedback,
.meaning-card {
  padding: 14px;
  border: 1px solid #b9ead2;
  border-radius: 8px;
  background: #f0fdf4;
}

.lookup-feedback--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.lookup-feedback header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.lookup-feedback p,
.meaning-card p,
.detail-section p,
.detail-section small {
  margin: 6px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.dictionary-entry {
  margin-top: 10px;
}

.dictionary-entry h4,
.meaning-card h4,
.detail-section h4 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.dictionary-entry ol {
  margin: 8px 0 0;
  padding-left: 20px;
}

.dictionary-entry li + li {
  margin-top: 8px;
}

.expand-button {
  margin-top: 8px;
  padding: 7px 10px;
}

.morpheme-grid,
.chip-grid,
.derived-grid {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.morpheme-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.morpheme-grid article,
.chip-grid span,
.derived-grid article {
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
}

.morpheme-grid article {
  padding: 10px;
  background: #f0fdf4;
}

.morpheme-grid strong,
.derived-grid strong {
  display: block;
  color: #047857;
}

.morpheme-grid span,
.morpheme-grid small,
.derived-grid small {
  display: block;
  margin-top: 3px;
  color: #475569;
  font-size: 11px;
}

.chip-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.chip-grid span {
  padding: 10px;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.derived-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.derived-grid article {
  padding: 10px;
}

.detail-actions {
  gap: 10px;
  padding: 18px 24px 24px;
}

.detail-actions button {
  flex: 1;
  min-height: 38px;
}

.study-sidebar {
  display: grid;
  gap: 14px;
}

.side-card {
  padding: 18px;
}

.side-card header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.side-card h2 {
  font-size: 17px;
}

.side-card h2 small {
  color: #64748b;
  font-weight: 500;
}

.side-card header button {
  border: 0;
  color: #059669;
}

.plan-list,
.review-list,
.advice-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.plan-list li {
  gap: 12px;
}

.plan-list span {
  color: #059669;
}

.plan-list strong {
  flex: 1;
  color: #334155;
  font-size: 14px;
}

.plan-list em {
  color: #047857;
  font-style: normal;
  font-weight: 800;
}

.review-list li {
  gap: 10px;
}

.review-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.review-list strong {
  flex: 1;
  font-size: 13px;
}

.review-list mark {
  width: 28px;
  border-radius: 999px;
  background: #fef3c7;
  color: #f97316;
  text-align: center;
}

.review-list i {
  gap: 4px;
  background: transparent;
}

.review-list i em {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
}

.review-list i em.filled {
  background: #059669;
}

.full-width {
  width: 100%;
  margin-top: 16px;
}

.advice-list li {
  position: relative;
  padding-left: 16px;
  color: #475569;
  font-size: 12px;
}

.advice-list li::before {
  position: absolute;
  top: 8px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  content: '';
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.badge-grid article {
  display: grid;
  justify-items: center;
  gap: 6px;
  text-align: center;
}

.badge-grid div {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 50%;
  font-size: 24px;
  font-weight: 900;
}

.badge-grid strong,
.badge-grid span {
  color: #475569;
  font-size: 11px;
}

.badge-grid span {
  margin-top: -6px;
}

.badge-gold { background: #fef3c7; color: #f59e0b; }
.badge-silver { background: #e2e8f0; color: #64748b; }
.badge-bronze { background: #fed7aa; color: #ea580c; }

@media (max-width: 1480px) {
  .learning-topbar {
    grid-template-columns: 1fr;
  }

  .api-status-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .learning-layout {
    grid-template-columns: minmax(0, 1fr) 420px;
  }

  .study-sidebar {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .metric-grid,
  .api-status-grid,
  .learning-layout,
  .study-sidebar {
    grid-template-columns: 1fr;
  }

  .word-table-card {
    overflow-x: auto;
  }
}

@media (max-width: 720px) {
  .learning-shell {
    padding: 14px;
  }

  .learning-search {
    grid-template-columns: 34px minmax(0, 1fr);
    padding-bottom: 6px;
  }

  .learning-search select,
  .learning-search button {
    grid-column: span 1;
    margin: 0 6px;
    border-top: 1px solid #e2e8f0;
  }

  .learning-tabs {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-hero,
  .detail-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .morpheme-grid,
  .chip-grid,
  .derived-grid,
  .badge-grid {
    grid-template-columns: 1fr;
  }
}
</style>
