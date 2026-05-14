<template>
  <!-- Loading -->
  <div v-if="phase === 'loading'" class="gate-center">
    <div class="gate-spinner" />
    <p class="gate-hint">加载中…</p>
  </div>

  <!-- Writing hub / dashboard -->
  <div v-else-if="phase === 'doc-list' || phase === 'dashboard'" class="hub-page" :class="{ 'hub-page--dashboard': phase === 'dashboard' }">
    <nav class="writing-section-tabs" aria-label="写作页面导航">
      <RouterLink class="writing-section-tab" :class="{ active: phase === 'doc-list' }" to="/app/writing">写作练习</RouterLink>
      <RouterLink class="writing-section-tab" :class="{ active: phase === 'dashboard' }" to="/app/writing/dashboard">Dashboard</RouterLink>
    </nav>

    <section class="dashboard-hero" aria-labelledby="writing-dashboard-title">
      <div class="hero-copy">
        <p class="hero-kicker">PEAI Writing / Practice</p>
        <h2 id="writing-dashboard-title" class="hub-title">{{ phase === 'dashboard' ? 'Dashboard' : '写作练习' }}</h2>
        <p class="hero-subtitle">
          {{ phase === 'dashboard' ? '查看写作成长、能力曲线和主题覆盖' : '坚持每天写一点，英语写作自然进步。' }}
        </p>
      </div>

      <div class="hero-art" aria-hidden="true">
        <svg viewBox="0 0 260 150">
          <path d="M32 116c22-20 48-21 76-8s58 13 91-16" />
          <path d="M92 41h84c8 0 14 6 14 14v58H78V55c0-8 6-14 14-14Z" />
          <path d="M103 64h49M103 80h62M103 96h38" />
          <path d="M180 103l33-49 18 12-33 49-25 8 7-20Z" />
          <path d="M35 95h42M46 82h31M57 69h20" />
          <path d="M43 48c10-15 26-13 31 4-15 5-25 5-31-4Z" />
        </svg>
      </div>

      <button class="new-doc-btn new-doc-btn--academy" type="button" @click="navigateToPhase('mode-select')">
        <span>+ 新建作文</span>
        <span class="new-doc-divider" aria-hidden="true"></span>
        <svg viewBox="0 0 20 20" aria-hidden="true">
          <path d="m5 7 5 5 5-5" />
        </svg>
      </button>
    </section>

    <WritingOverviewCard
      v-if="phase === 'dashboard'"
      v-model:range="dashboardRange"
      v-model:mode="dashboardMode"
      v-model:custom-range="dashboardCustomRange"
      :range-options="dashboardRangeOptions"
      :mode-options="dashboardModeOptions"
      :overview="dashboardOverview"
    />
    <div v-if="phase === 'dashboard' && dashboardLoading" class="dashboard-state">
      Dashboard 数据加载中…
    </div>
    <div v-else-if="phase === 'dashboard' && dashboardError" class="dashboard-state dashboard-state--error">
      {{ dashboardError }}
    </div>

    <section v-if="phase === 'dashboard'" class="dashboard-section" aria-labelledby="growth-title">
      <div class="section-heading">
        <span class="section-kicker">Growth</span>
        <h3 id="growth-title">成长 / 激励</h3>
      </div>
      <div class="growth-layout">
        <article class="report-card score-trend-card">
          <div class="card-header">
            <div>
              <span class="card-eyebrow">Essay Score Trend</span>
              <h4>单篇得分趋势</h4>
              <p>每个点代表一篇作文的最新评分</p>
            </div>
          <div class="mini-tabs" aria-label="趋势范围">
              <span class="active">最近{{ dashboardGrowth.essayScoreTrend.length }}篇</span>
              <span>全部</span>
            </div>
          </div>
          <div class="score-trend-summary">
            <span>最近3篇 <strong>+{{ recentScoreGrowth }}分</strong></span>
            <span>最高 <strong>{{ highestEssayScore }}分</strong></span>
          </div>
          <div
            v-if="hasEssayScoreTrend"
            ref="scoreTrendChartRef"
            class="dashboard-chart dashboard-chart--score"
            aria-label="得分趋势图"
          ></div>
          <div v-else class="score-trend-empty">
            完成评分后展示单篇得分趋势，2 篇以上会形成趋势线
          </div>
          <div class="score-band-legend" aria-label="分数区间说明">
            <span v-for="band in dashboardGrowth.scoreBands" :key="band.key">
              <i :style="{ background: band.color }"></i>{{ band.label }}
            </span>
          </div>
          <div class="overview-insight growth-insight">
            <strong>AI建议</strong>
            <span>{{ dashboardGrowth.insight }}</span>
          </div>
        </article>

        <div class="growth-side">
          <article class="report-card score-distribution-card">
            <div class="card-header card-header--compact">
              <div>
                <h4>分布分析</h4>
                <span class="hint-text">全部作文 · 最新评分</span>
              </div>
            </div>
            <h5 class="distribution-subtitle">得分分布</h5>
            <div class="distribution-overview">
              <div class="distribution-chart-shell">
                <div ref="scoreDistributionChartRef" class="dashboard-chart dashboard-chart--distribution" aria-label="得分分布图"></div>
                <div class="distribution-center" aria-hidden="true">
                  <strong>80+</strong>
                  <span>{{ highScorePercent }}%</span>
                </div>
              </div>
              <div class="distribution-summary">
                <span>高分占比</span>
                <strong>{{ highScorePercent }}%</strong>
                <em>80 分以上作文占全部最新评分的比例</em>
              </div>
            </div>
            <div class="score-distribution-list" aria-label="得分区间占比">
              <div v-for="bucket in dashboardGrowth.scoreDistribution" :key="bucket.key">
                <span class="distribution-dot" :style="{ background: bucket.color }"></span>
                <strong>{{ bucket.label }}</strong>
                <em>{{ bucket.stage }}</em>
                <span class="distribution-value">{{ bucket.count }}篇 · {{ bucket.percent }}%</span>
                <span class="distribution-track" aria-hidden="true">
                  <i :style="{ width: `${bucket.percent}%`, background: bucket.color }"></i>
                </span>
              </div>
            </div>
            <article class="score-scatter-card">
              <div class="card-header card-header--compact">
                <div>
                  <h5>作文落点</h5>
                  <span class="hint-text">按月份查看每篇作文所在分数区间</span>
                </div>
              </div>
              <div ref="scoreScatterChartRef" class="dashboard-chart dashboard-chart--scatter" aria-label="作文落点散点图"></div>
            </article>
          </article>
        </div>
      </div>
    </section>

    <section v-if="phase === 'dashboard'" class="dashboard-section practice-progress-section" aria-labelledby="practice-progress-title">
      <div class="section-heading">
        <span class="section-kicker">Practice</span>
        <h3 id="practice-progress-title">练习进度</h3>
      </div>
      <article class="report-card goal-card">
        <div>
          <h4>本月目标</h4>
          <p>完成 {{ dashboardGrowth.monthlyGoal.done }} / {{ dashboardGrowth.monthlyGoal.target }} 篇</p>
          <span class="hint-text">还差 {{ monthlyGoalRemaining }} 篇完成本月目标</span>
        </div>
        <div class="goal-progress">
          <span :style="{ width: `${monthlyGoalPercent}%` }"></span>
        </div>
        <div class="goal-badges">
          <span>连续写作 {{ dashboardGrowth.streak.currentDays }} 天</span>
          <span>最长记录 {{ dashboardGrowth.streak.bestDays }} 天</span>
        </div>
      </article>
    </section>

    <section v-if="phase === 'dashboard'" class="dashboard-section" aria-labelledby="ability-title">
      <div class="section-heading">
        <span class="section-kicker">Ability</span>
        <h3 id="ability-title">写作能力</h3>
      </div>
      <div class="ability-summary">
        <article class="report-card level-card">
          <span class="card-eyebrow">Current Level</span>
          <div class="level-row">
            <strong>{{ mockAbilityDashboard.level.currentLevel }}</strong>
            <span>目标 {{ mockAbilityDashboard.level.targetLevel }}</span>
          </div>
          <div class="level-progress">
            <span :style="{ width: `${mockAbilityDashboard.level.progressToNext}%` }"></span>
          </div>
          <p>{{ mockAbilityDashboard.level.basisText }}</p>
          <p class="gap-text">{{ mockAbilityDashboard.level.gapText }}</p>
          <div class="focus-tags">
            <span v-for="item in mockAbilityDashboard.level.focus" :key="item">{{ item }}</span>
          </div>
        </article>
        <article class="report-card growth-card">
          <span class="card-eyebrow">Recent Growth</span>
          <h4>最近成长点</h4>
          <div class="growth-items">
            <span v-for="item in mockAbilityDashboard.growthItems" :key="item.label">
              <em>{{ item.label }}</em>
              <strong :class="item.delta >= 0 ? 'delta-up' : 'delta-down'">{{ item.delta >= 0 ? '+' : '' }}{{ item.delta }}</strong>
            </span>
          </div>
          <p>语法和结构提升明显，句式复杂度仍需加强。</p>
        </article>
      </div>
      <article class="report-card ability-trend-card">
        <div class="card-header">
          <div>
            <span class="card-eyebrow">CEFR Reference</span>
            <h4>能力成长曲线</h4>
          </div>
          <div class="curve-legend">
            <span class="overall">综合</span>
            <span class="grammar">语法</span>
            <span class="vocabulary">词汇</span>
            <span class="coherence">结构</span>
          </div>
        </div>
        <div ref="abilityChartRef" class="dashboard-chart dashboard-chart--ability" aria-label="能力成长曲线"></div>
      </article>
      <div class="diagnostics-grid">
        <article class="report-card">
          <h4>高频错误</h4>
          <div class="error-bars">
            <div v-for="item in mockAbilityDashboard.diagnostics" :key="item.label" class="error-row">
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.count }} 次</strong>
              </div>
              <em><i :class="item.tone" :style="{ width: `${item.count}%` }"></i></em>
            </div>
          </div>
        </article>
        <article class="report-card">
          <h4>词汇与句式</h4>
          <div class="metric-list">
            <div v-for="item in mockAbilityDashboard.metrics" :key="item.label" class="metric-row">
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <em><i :style="{ width: `${item.percent}%` }"></i></em>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section v-if="phase === 'dashboard'" class="dashboard-section" aria-labelledby="topic-style-title">
      <div class="section-heading">
        <span class="section-kicker">Topic & Style</span>
        <h3 id="topic-style-title">写作主题和风格</h3>
      </div>
      <div class="topic-layout">
        <article class="report-card">
          <h4>常练主题</h4>
          <div class="topic-cloud">
            <span v-for="topic in mockTopicStyleDashboard.topics" :key="topic.label" :class="`weight-${topic.weight}`">{{ topic.label }}</span>
          </div>
        </article>
        <article class="report-card">
          <h4>体裁分布</h4>
          <div class="genre-list">
            <div v-for="genre in mockTopicStyleDashboard.genres" :key="genre.label" class="genre-row">
              <span>{{ genre.label }}</span>
              <em><i :style="{ width: `${genre.percent}%` }"></i></em>
              <strong>{{ genre.percent }}%</strong>
            </div>
          </div>
        </article>
        <article class="report-card next-prompt-card">
          <span class="card-eyebrow">Recommended</span>
          <h4>推荐下一篇</h4>
          <strong>{{ mockTopicStyleDashboard.nextPrompt.title }}</strong>
          <p>{{ mockTopicStyleDashboard.nextPrompt.reason }}</p>
          <span class="difficulty">难度：{{ mockTopicStyleDashboard.nextPrompt.level }}</span>
          <button type="button" @click="navigateToPhase('mode-select')">开始练习</button>
        </article>
      </div>
    </section>

    <div v-if="phase === 'doc-list'" class="writing-home-layout">
      <!-- Search -->
      <section class="doc-section" aria-labelledby="history-title">
      <div class="doc-section-header">
        <div>
          <span class="section-kicker">Archive</span>
          <h3 id="history-title" class="doc-section-title">历史作文</h3>
        </div>
        <div class="doc-filters">
          <span v-if="showHistoryPagination" class="history-page-summary">
            第 {{ currentPage }} / {{ maxPage }} 页 · 共 {{ filteredDocs.length }} 篇
          </span>
          <div class="search-bar">
            <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input
              v-model="searchQuery"
              class="search-input"
              type="text"
              placeholder="搜索作文标题或关键词..."
            />
            <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''">&times;</button>
          </div>
          <div class="filter-pills">
            <button
              v-for="f in filterOptions"
              :key="f.value"
              class="filter-pill"
              :class="{ active: filterMode === f.value }"
              @click="filterMode = f.value"
            >{{ f.label }}</button>
          </div>
          <select v-model="sortBy" class="sort-select">
            <option value="updatedAt">最近修改</option>
            <option value="createdAt">最近创建</option>
            <option value="score">最高分</option>
          </select>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="docListLoading" class="doc-empty">
        <div class="gate-spinner" />
      </div>

      <!-- Empty -->
      <div v-else-if="docList.length === 0" class="doc-empty">
        <p class="empty-icon">&#128221;</p>
        <p class="empty-text">还没有写过作文</p>
        <p class="empty-hint">点击「新建作文」开始你的第一篇写作练习</p>
        <button class="gate-btn" style="margin-top: 16px;" @click="navigateToPhase('mode-select')">开始写作</button>
      </div>

      <!-- No results after filter -->
      <div v-else-if="filteredDocs.length === 0" class="doc-empty">
        <p class="empty-text">没有找到符合条件的作文</p>
      </div>

      <!-- Cards grid -->
      <div v-else class="doc-grid">
        <div
          v-for="doc in displayDocs"
          :key="doc.docId"
          class="doc-card"
          @click="openDocument(doc)"
        >
          <div class="doc-card-top">
            <div class="doc-card-tags">
              <span class="doc-mode-tag" :class="doc.taskPrompt ? 'exam' : 'free'">
                {{ doc.taskPrompt ? '考试' : '自由' }}
              </span>
              <span class="doc-status-pill" :class="docStatusClass(doc)">
                {{ docStatusLabel(doc) }}
              </span>
            </div>
            <button class="doc-menu-btn" @click.stop="toggleMenu(doc.docId)" title="更多操作">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><circle cx="8" cy="3" r="1.5"/><circle cx="8" cy="8" r="1.5"/><circle cx="8" cy="13" r="1.5"/></svg>
            </button>
            <!-- Dropdown menu -->
            <div v-if="openMenuId === doc.docId" class="doc-menu" @click.stop>
              <button class="doc-menu-item" @click="startRename(doc)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3a2.85 2.85 0 114 4L7.5 20.5 2 22l1.5-5.5z"/></svg>
                重命名
              </button>
              <button class="doc-menu-item doc-menu-danger" @click="confirmDelete(doc)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                删除
              </button>
            </div>
          </div>
          <h3 class="doc-card-title">{{ doc.title || '未命名作文' }}</h3>
          <p class="doc-card-prompt">{{ docPromptSummary(doc) }}</p>
          <div class="doc-card-score-area">
            <template v-if="doc.latestScore != null">
              <span class="doc-score-num" :class="scoreColor(doc.latestScore)">{{ doc.latestScore }}</span>
              <span class="doc-score-max">/ 100</span>
              <span
                v-if="doc.initialScore != null && doc.latestScore !== doc.initialScore"
                class="doc-score-delta"
                :class="doc.latestScore - doc.initialScore > 0 ? 'up' : 'down'"
              >{{ doc.latestScore - doc.initialScore > 0 ? '+' : '' }}{{ doc.latestScore - doc.initialScore }}</span>
            </template>
            <span v-else class="doc-score-none">未评分</span>
          </div>
          <div class="doc-card-metrics">
            <span>
              <em>评分次数</em>
              <strong>{{ doc.submitCount || 0 }}</strong>
            </span>
            <span>
              <em>较初评</em>
              <strong v-if="docScoreDelta(doc) !== null" :class="docScoreDelta(doc)! > 0 ? 'metric-up' : 'metric-down'">
                {{ docScoreDelta(doc)! > 0 ? '+' : '' }}{{ docScoreDelta(doc) }}
              </strong>
              <strong v-else>--</strong>
            </span>
            <span>
              <em>最近修改</em>
              <strong>{{ formatTime(doc.updatedAt) }}</strong>
            </span>
          </div>
          <p class="doc-next-step">{{ docNextStep(doc) }}</p>
          <div class="doc-card-bottom">
            <span class="doc-card-time">{{ doc.taskPrompt ? '考试写作记录' : '自由写作记录' }}</span>
            <span class="doc-card-action">继续写作 &rarr;</span>
          </div>
        </div>
        <div
          v-for="index in historyPlaceholderCount"
          :key="`history-placeholder-${index}`"
          class="doc-card doc-card-placeholder"
          aria-hidden="true"
        ></div>
      </div>

      <!-- Rename dialog -->
      <div v-if="renameDialog.visible" class="confirm-overlay" @click.self="renameDialog.visible = false">
        <div class="rename-dialog">
          <button class="confirm-close" @click="renameDialog.visible = false">&times;</button>
          <h3 class="rename-title">重命名</h3>
          <input
            v-model="renameDialog.title"
            class="rename-input"
            placeholder="请输入新标题"
            maxlength="100"
            @keyup.enter="doRename"
          />
          <div class="rename-actions">
            <button class="btn-cancel" @click="renameDialog.visible = false">取消</button>
            <button class="gate-btn" :disabled="!renameDialog.title.trim()" @click="doRename">确定</button>
          </div>
        </div>
      </div>

      <!-- Delete confirm dialog -->
      <div v-if="deleteDialog.visible" class="confirm-overlay" @click.self="deleteDialog.visible = false">
        <div class="rename-dialog">
          <button class="confirm-close" @click="deleteDialog.visible = false">&times;</button>
          <h3 class="rename-title">确认删除</h3>
          <p class="delete-hint">删除「{{ deleteDialog.title }}」后将无法恢复，确定要删除吗？</p>
          <div class="rename-actions">
            <button class="btn-cancel" @click="deleteDialog.visible = false">取消</button>
            <button class="gate-btn gate-btn--danger" @click="doDelete">删除</button>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="showHistoryPagination" class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage <= 1"
          @click="currentPage--"
        >&lsaquo;</button>
        <button
          v-for="p in paginationPages"
          :key="p"
          class="page-btn"
          :class="{ active: currentPage === p, ellipsis: p === -1 }"
          :disabled="p === -1"
          @click="p !== -1 && (currentPage = p)"
        >{{ p === -1 ? '...' : p }}</button>
        <button
          class="page-btn"
          :disabled="currentPage >= maxPage"
          @click="currentPage++"
        >&rsaquo;</button>
      </div>
    </section>

      <aside class="daily-recommendations" aria-labelledby="daily-prompts-title">
        <div class="daily-header">
          <div>
            <span class="section-kicker">Daily</span>
            <h3 id="daily-prompts-title">每日推荐作文</h3>
          </div>
          <button class="daily-refresh" type="button" title="换一换">
            <svg viewBox="0 0 20 20" aria-hidden="true">
              <path d="M15 6a6 6 0 1 0 1 6M15 6V2m0 4h-4" />
            </svg>
            换一换
          </button>
        </div>

        <article v-for="prompt in mockDailyWritingPrompts" :key="prompt.id" class="daily-card">
          <div class="daily-tags">
            <span>{{ prompt.level }}</span>
            <span>{{ prompt.genre }}</span>
          </div>
          <h4>{{ prompt.title }}</h4>
          <p>{{ prompt.description }}</p>
          <div class="daily-meta">
            <span>推荐字数：{{ prompt.wordRange }}</span>
            <span>预计用时：{{ prompt.estimatedMinutes }} 分钟</span>
          </div>
          <button type="button" @click="navigateToPhase('mode-select')">开始练习</button>
        </article>
      </aside>
    </div>
  </div>

  <!-- New writing task modal -->
  <div v-else-if="phase === 'mode-select'" class="task-modal-page">
    <div class="task-modal-backdrop" />
    <section class="task-modal" aria-label="新建写作任务">
      <button class="task-modal-close" type="button" title="退出" @click="navigateToPhase('doc-list')">&times;</button>
      <p class="task-modal-kicker">写作设置</p>
      <h2 class="task-modal-title">新建写作任务</h2>

      <div class="task-modal-section">
        <p class="task-modal-label">写作模式</p>
        <div class="task-option-grid task-option-grid--two">
          <button
            class="task-option"
            :class="{ active: newTaskMode === 'free' }"
            type="button"
            @click="newTaskMode = 'free'"
          >
            <span class="task-option-title">自由写作</span>
            <span class="task-option-desc">直接进入空白写作页</span>
          </button>
          <button
            class="task-option"
            :class="{ active: newTaskMode === 'exam' }"
            type="button"
            @click="newTaskMode = 'exam'"
          >
            <span class="task-option-title">考试写作</span>
            <span class="task-option-desc">先确定题目来源</span>
          </button>
        </div>
      </div>

      <div v-if="newTaskMode === 'exam'" class="task-modal-section">
        <p class="task-modal-label">题目来源</p>
        <div class="task-option-grid task-option-grid--two">
          <button
            class="task-option"
            :class="{ active: newTaskSource === 'past_prompt' }"
            type="button"
            @click="newTaskSource = 'past_prompt'"
          >
            <span class="task-option-title">历年真题</span>
            <span class="task-option-desc">进入真题选择页，忠实复现原题</span>
          </button>
          <button
            class="task-option"
            :class="{ active: newTaskSource === 'ai_design' }"
            type="button"
            @click="newTaskSource = 'ai_design'"
          >
            <span class="task-option-title">AI 题目设计</span>
            <span class="task-option-desc">按要求生成原创练习题</span>
          </button>
        </div>
      </div>

      <div v-if="newTaskMode === 'exam' && newTaskSource === 'ai_design'" class="task-modal-section">
        <p class="task-modal-label">AI 题目设计要求</p>
        <div class="task-field-grid">
          <label class="task-field">
            <span>体裁</span>
            <select v-model="newTaskGenre" class="task-select">
              <option :value="null">请选择体裁</option>
              <option v-for="option in newTaskGenreOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="task-field">
            <span>字数</span>
            <div class="task-word-row">
              <select
                v-model="newTaskWordRange"
                class="task-select"
                @change="newTaskCustomWordRange = newTaskWordRange === '__custom__' ? newTaskCustomWordRange : ''"
              >
                <option :value="null">请选择字数</option>
                <option v-for="option in newTaskWordRangeOptions" :key="option" :value="option">{{ option }} 词</option>
                <option value="__custom__">自定义</option>
              </select>
              <input
                v-if="newTaskWordRange === '__custom__'"
                v-model="newTaskCustomWordRange"
                class="task-input"
                type="text"
                inputmode="numeric"
                placeholder="160-200"
              />
            </div>
          </label>
        </div>
      </div>

      <div class="task-modal-actions">
        <button class="task-btn task-btn--secondary" type="button" @click="navigateToPhase('doc-list')">退出</button>
        <button class="task-btn task-btn--primary" type="button" @click="continueNewWritingTask">继续</button>
      </div>
    </section>
  </div>

  <!-- Past prompt select -->
  <div v-else-if="phase === 'past-prompt-select'" class="past-prompt-page">
    <button class="setup-back-link" type="button" @click="navigateToPhase('mode-select')">
      &larr; 返回新建任务
    </button>
    <div class="past-prompt-header">
      <div>
        <p class="past-prompt-kicker">历年真题</p>
        <h2 class="past-prompt-title">选择一套真题开始写作</h2>
      </div>
      <button class="task-btn task-btn--primary" type="button" :disabled="!selectedPastPrompt" @click="startWritingFromPastPrompt">
        使用该真题
      </button>
    </div>

    <div class="past-prompt-toolbar">
      <input
        v-model="pastPromptKeyword"
        class="past-prompt-search"
        type="text"
        placeholder="搜索题目、试卷或关键词"
        @keyup.enter="loadPastPrompts(1)"
      />
      <select v-model="pastPromptYearSelect" class="past-prompt-select" @change="onPastPromptYearChange">
        <option :value="0">全部年份</option>
        <option v-for="year in pastPromptYears" :key="year" :value="year">{{ year }}</option>
      </select>
      <button class="task-btn task-btn--secondary" type="button" @click="loadPastPrompts(1)">搜索</button>
    </div>

    <div v-if="pastPromptLoading" class="past-prompt-empty">
      <div class="gate-spinner" />
      <span>加载真题中...</span>
    </div>
    <div v-else-if="pastPromptItems.length === 0" class="past-prompt-empty">
      <span>暂未找到匹配的真题</span>
    </div>
    <div v-else class="past-prompt-layout">
      <div class="past-prompt-list">
        <button
          v-for="prompt in pastPromptItems"
          :key="prompt.id"
          class="past-prompt-item"
          :class="{ active: selectedPastPrompt?.id === prompt.id }"
          type="button"
          @click="selectedPastPrompt = prompt"
        >
          <span class="past-prompt-item-title">{{ prompt.title || prompt.paper || '未命名真题' }}</span>
          <span class="past-prompt-item-meta">
            {{ prompt.examYear || '年份未知' }} · {{ prompt.paper || '试卷未知' }} · {{ prompt.task || 'Task 未标注' }}
          </span>
          <span class="past-prompt-item-text">{{ prompt.promptText }}</span>
        </button>
      </div>

      <aside class="past-prompt-preview">
        <template v-if="selectedPastPrompt">
          <p class="past-prompt-preview-kicker">{{ selectedPastPrompt.paper || '历年真题' }}</p>
          <h3>{{ selectedPastPrompt.title || '真题预览' }}</h3>
          <div class="past-prompt-preview-meta">
            <span>{{ selectedPastPrompt.examYear || '年份未知' }}</span>
            <span>{{ selectedPastPrompt.task || 'Task 未标注' }}</span>
            <span>{{ formatPastPromptWordRange(selectedPastPrompt) || '字数未标注' }}</span>
          </div>
          <p class="past-prompt-preview-text">{{ selectedPastPrompt.promptText }}</p>
          <div v-if="selectedPastPrompt.imageDescription" class="past-prompt-preview-block">
            <strong>图片/图表信息</strong>
            <p>{{ selectedPastPrompt.imageDescription }}</p>
          </div>
          <div v-if="selectedPastPrompt.materialText" class="past-prompt-preview-block">
            <strong>材料内容</strong>
            <p>{{ selectedPastPrompt.materialText }}</p>
          </div>
          <img
            v-if="selectedPastPrompt.imageUrl"
            class="past-prompt-preview-image"
            :src="selectedPastPrompt.imageUrl"
            alt="真题图片"
          />
        </template>
        <p v-else class="past-prompt-preview-placeholder">从左侧选择一套真题查看题面。</p>
      </aside>
    </div>
  </div>

  <!-- Exam setup -->
  <ExamSetupPage
    v-else-if="phase === 'exam-setup'"
    :initial-topic="resumeTopicForSetup"
    :resume-metadata="resumeMetadataForSetup"
    :study-stage="currentStage ?? ''"
    :initial-genre="examSetupInitialGenre"
    :initial-word-range="examSetupInitialWordRange"
    :initial-tab="examSetupInitialTab"
    @confirm="onExamConfirm"
    @back="onExamSetupBack"
    @save-draft="onExamSaveDraft"
    @switch-mode="onExamSetupSwitchMode"
  />

  <!-- Editor -->
  <EditorShell
    v-else-if="phase === 'editor'"
    :initial-writing-mode="chosenMode"
    :initial-task-prompt="initialTaskPrompt"
    :initial-doc-id="initialDocId"
    :initial-existing-content="initialExistingContent"
    :exam-max-score="examMaxScore"
    :initial-submit-count="initialSubmitCount"
    :study-stage="currentStage ?? ''"
    @back="onEditorBack"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStorage, useEventListener } from '@vueuse/core'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, RadarChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  MarkPointComponent,
  RadarComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart, RadarChart, PieChart, ScatterChart,
  GridComponent, TooltipComponent, RadarComponent, LegendComponent, MarkAreaComponent, MarkLineComponent, MarkPointComponent,
  CanvasRenderer,
])
import EditorShell from '@/components/writing/EditorShell.vue'
import WritingOverviewCard from '@/components/writing/dashboard/WritingOverviewCard.vue'
import ExamSetupPage from '@/pages/app/ExamSetupPage.vue'
import type { ExamPromptType, ExamTopicInfo } from '@/pages/app/examPromptHelpers'
import { buildExamTaskPrompt } from '@/pages/app/examPromptHelpers'
import { stageCache } from '@/stores/stageCache'
import { getStageId } from '@/constants/stage'
import { getWritingSessionMetadata, startWritingSession, getWritingDocuments, getWritingStats, getEssayPrompts, getWritingDashboard } from '@/api/writing'
import type {
  EssayPromptItem,
  WritingDashboardGrowth,
  WritingDashboardMode,
  WritingDashboardOverview,
  WritingDashboardRange,
  WritingDashboardResponse,
  WritingDocumentItem,
  WritingSessionMetadataResponse,
  WritingStatsResponse,
} from '@/api/writing'
import { renameDocument, deleteDocument } from '@/api/document'
import { showToast } from '@/utils/toast'
import {
  dashboardModeOptions,
  dashboardRangeOptions,
  mockAbilityDashboard,
  mockDailyWritingPrompts,
  mockTopicStyleDashboard,
  type WritingDashboardCustomRange,
} from './writingDashboardMock'

type Phase = 'loading' | 'doc-list' | 'dashboard' | 'mode-select' | 'past-prompt-select' | 'exam-setup' | 'editor'
type RoutePhase = Exclude<Phase, 'loading'>
type NewTaskMode = 'free' | 'exam'
type NewTaskSource = 'past_prompt' | 'ai_design'
type ExamSetupInitialTab = 'manual' | 'ai' | 'past'

const router = useRouter()
const route = useRoute()
const setImmersive = inject<(v: boolean | null) => void>('setImmersive', () => {})

const booting = ref(true)
const currentStage = ref<string | null>(null)
const chosenMode = useSessionStorage<'free' | 'exam'>('peai:writing:chosenMode', 'free')
const initialTaskPrompt = ref<string | undefined>(undefined)
const initialDocId = useSessionStorage<string | null>('peai:writing:docId', null)
const initialExistingContent = ref<string | null>(null)
const examMaxScore = useSessionStorage<number | null>('peai:writing:examMaxScore', null)
const initialSubmitCount = ref(0)
const resumeTopicForSetup = ref<string | undefined>(undefined)
const resumeMetadataForSetup = ref<WritingSessionMetadataResponse | null>(null)
const newTaskMode = ref<NewTaskMode>('free')
const newTaskSource = ref<NewTaskSource>('ai_design')
const newTaskGenre = ref<string | null>(null)
const newTaskWordRange = ref<string | null>(null)
const newTaskCustomWordRange = ref('')
const examSetupInitialGenre = ref<string | null>(null)
const examSetupInitialWordRange = ref<string | null>(null)
const examSetupInitialTab = ref<ExamSetupInitialTab | null>(null)

const newTaskGenreOptions = [
  { value: 'argumentative', label: '议论文' },
  { value: 'material', label: '材料作文' },
  { value: 'chart', label: '图表作文' },
  { value: 'picture', label: '图画作文' },
  { value: 'practical', label: '应用文' },
  { value: 'letter', label: '书信' },
]
const newTaskWordRangeOptions = ['80-100', '100-120', '120-150', '160-200', '160-220', '250']

const emptyDashboardOverview: WritingDashboardOverview = {
  summary: {
    totalEssays: 0,
    totalSubmissions: 0,
    averageScore: 0,
    bestScore: 0,
  },
  trend: [],
  insight: '先完成一篇作文评分后，这里会展示写作趋势和建议。',
}

const emptyDashboardGrowth: WritingDashboardGrowth = {
  essayScoreTrend: [],
  scoreDistribution: [
    { key: 'under-60', label: '<60', stage: '需要补基础', min: null, max: 60, count: 0, percent: 0, color: '#D97A72', backgroundColor: '#F7D8D4' },
    { key: '60-70', label: '60-70', stage: '基础建立', min: 60, max: 70, count: 0, percent: 0, color: '#D49A45', backgroundColor: '#F3E0BD' },
    { key: '70-80', label: '70-80', stage: '稳定提升', min: 70, max: 80, count: 0, percent: 0, color: '#A7B45F', backgroundColor: '#E7E8C8' },
    { key: '80-90', label: '80-90', stage: '良好', min: 80, max: 90, count: 0, percent: 0, color: '#63AE86', backgroundColor: '#D7EADD' },
    { key: '90-100', label: '90-100', stage: '优秀', min: 90, max: 101, count: 0, percent: 0, color: '#6999C2', backgroundColor: '#D8E6F2' },
  ],
  scoreBands: [
    { key: 'under-60', label: '需要补基础', min: 0, max: 60, color: '#F7D8D4' },
    { key: '60-70', label: '基础建立', min: 60, max: 70, color: '#F3E0BD' },
    { key: '70-80', label: '稳定提升', min: 70, max: 80, color: '#E7E8C8' },
    { key: '80-90', label: '良好', min: 80, max: 90, color: '#D7EADD' },
    { key: '90-100', label: '优秀', min: 90, max: 101, color: '#D8E6F2' },
  ],
  highScorePercent: 0,
  scoreScatter: [],
  monthlyGoal: {
    done: 0,
    target: 3,
    remaining: 3,
  },
  streak: {
    currentDays: 0,
    bestDays: 0,
    activeDays: 0,
  },
  insight: '先完成一篇作文评分后，这里会展示写作趋势和建议。',
}

function resolveRoutePhase(): RoutePhase {
  switch (route.name) {
    case 'WritingDashboard':
      return 'dashboard'
    case 'WritingModeSelect':
      return 'mode-select'
    case 'WritingPastPromptSelect':
      return 'past-prompt-select'
    case 'WritingExamSetup':
      return 'exam-setup'
    case 'WritingEditor':
      return 'editor'
    default:
      return 'doc-list'
  }
}

const phase = computed<Phase>(() => {
  if (booting.value) return 'loading'
  return resolveRoutePhase()
})

function routeNameForPhase(nextPhase: RoutePhase) {
  switch (nextPhase) {
    case 'dashboard':
      return 'WritingDashboard'
    case 'mode-select':
      return 'WritingModeSelect'
    case 'past-prompt-select':
      return 'WritingPastPromptSelect'
    case 'exam-setup':
      return 'WritingExamSetup'
    case 'editor':
      return 'WritingEditor'
    default:
      return 'WritingDocList'
  }
}

async function navigateToPhase(nextPhase: RoutePhase, replace = false) {
  // Mark navigation to editor so EditorShell can distinguish from refresh
  if (nextPhase === 'editor') {
    try { sessionStorage.setItem('peai:writing:freshNav', '1') } catch {}
  }
  const target = { name: routeNameForPhase(nextPhase) }
  if (replace) {
    await router.replace(target)
    return
  }
  await router.push(target)
}

// Document list & pagination
const PAGE_SIZE = 9
const docList = ref<WritingDocumentItem[]>([])
const currentPage = ref(1)
const docListLoading = ref(false)
const dashboardRange = ref<WritingDashboardRange>('30d')
const dashboardMode = ref<WritingDashboardMode>('all')
const dashboardCustomRange = ref<WritingDashboardCustomRange>({
  start: formatDateInput(addDays(new Date(), -30)),
  end: formatDateInput(new Date()),
})
const dashboardData = ref<WritingDashboardResponse | null>(null)
const dashboardLoading = ref(false)
const dashboardError = ref('')
const filterMode = ref<'all' | 'free' | 'exam'>('all')
const sortBy = ref<'updatedAt' | 'createdAt' | 'score'>('updatedAt')
const searchQuery = ref('')

const filterOptions = [
  { value: 'all' as const, label: '全部' },
  { value: 'free' as const, label: '自由' },
  { value: 'exam' as const, label: '考试' },
]

// Computed stats
const scoredDocs = computed(() => docList.value.filter(d => d.latestScore != null))
const dashboardOverview = computed(() => dashboardData.value?.overview ?? emptyDashboardOverview)
const dashboardGrowth = computed(() => dashboardData.value?.growth ?? emptyDashboardGrowth)
const monthlyGoalPercent = computed(() => {
  const target = dashboardGrowth.value.monthlyGoal.target || 1
  return Math.round((dashboardGrowth.value.monthlyGoal.done / target) * 100)
})

const monthlyGoalRemaining = computed(() => {
  return dashboardGrowth.value.monthlyGoal.remaining
})

const highestEssayScore = computed(() => {
  return Math.max(0, ...dashboardGrowth.value.essayScoreTrend.map(item => item.score))
})

const hasEssayScoreTrend = computed(() => {
  return dashboardGrowth.value.essayScoreTrend.length >= 2
})

const recentScoreGrowth = computed(() => {
  const recent = dashboardGrowth.value.essayScoreTrend.slice(-3)
  if (recent.length < 2) return 0
  return Math.max(0, recent[recent.length - 1].score - recent[0].score)
})

const highScorePercent = computed(() => {
  return dashboardGrowth.value.highScorePercent
})
const filteredDocs = computed(() => {
  let list = [...docList.value]
  // Search
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(d => (d.title || '').toLowerCase().includes(q) || (d.taskPrompt || '').toLowerCase().includes(q))
  // Filter
  if (filterMode.value === 'exam') list = list.filter(d => !!d.taskPrompt)
  else if (filterMode.value === 'free') list = list.filter(d => !d.taskPrompt)
  if (sortBy.value === 'score') {
    list.sort((a, b) => (b.latestScore ?? -1) - (a.latestScore ?? -1))
  } else if (sortBy.value === 'createdAt') {
    list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } else {
    list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  }
  return list
})

// Clamp currentPage when filteredDocs shrinks
const maxPage = computed(() => Math.max(1, Math.ceil(filteredDocs.value.length / PAGE_SIZE)))
const showHistoryPagination = computed(() => maxPage.value > 1)
watch(maxPage, (mp) => {
  if (currentPage.value > mp) currentPage.value = mp
})

const displayDocs = computed(() => {
  const page = Math.min(currentPage.value, maxPage.value)
  const start = (page - 1) * PAGE_SIZE
  return filteredDocs.value.slice(start, start + PAGE_SIZE)
})
const historyPlaceholderCount = computed(() => {
  if (!showHistoryPagination.value) return 0
  return Math.max(0, PAGE_SIZE - displayDocs.value.length)
})

const paginationPages = computed(() => {
  const total = maxPage.value
  const cur = Math.min(currentPage.value, total)
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: number[] = [1]
  if (cur > 3) pages.push(-1)
  for (let i = Math.max(2, cur - 1); i <= Math.min(total - 1, cur + 1); i++) pages.push(i)
  if (cur < total - 2) pages.push(-1)
  pages.push(total)
  return pages
})

// Reset page when filter/sort changes
watch([filterMode, sortBy, searchQuery], () => { currentPage.value = 1 })

let dashboardRequestSeq = 0

watch([phase, dashboardRange, dashboardMode, dashboardCustomRange], () => {
  if (phase.value === 'dashboard') {
    void loadWritingDashboard()
  }
}, { immediate: true, deep: true })

async function loadWritingDashboard() {
  const requestSeq = ++dashboardRequestSeq
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    const params: Parameters<typeof getWritingDashboard>[0] = {
      range: dashboardRange.value,
      mode: dashboardMode.value,
    }
    if (dashboardRange.value === 'custom') {
      params.start = dashboardCustomRange.value.start
      params.end = dashboardCustomRange.value.end
    }
    const data = await getWritingDashboard(params)
    if (requestSeq !== dashboardRequestSeq) return
    dashboardData.value = data
  } catch {
    if (requestSeq !== dashboardRequestSeq) return
    dashboardData.value = null
    dashboardError.value = 'Dashboard 数据加载失败，完成评分后可重试；当前不会展示 mock 成绩。'
  } finally {
    if (requestSeq === dashboardRequestSeq) {
      dashboardLoading.value = false
    }
  }
}

function addDays(date: Date, days: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function formatDateInput(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getScatterMonthTime(value: string) {
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return Number.MAX_SAFE_INTEGER
  return new Date(date.getFullYear(), date.getMonth(), 1).getTime()
}

function foldScoreForChart(score: number) {
  if (score >= 60) return score
  return 50 + Math.max(0, Math.min(score, 60)) / 60 * 10
}

function unfoldScoreAxisLabel(value: number) {
  return value === 50 ? '0-60' : `${value}`
}

function resolveScoreBandColor(score: number) {
  const bucket = dashboardGrowth.value.scoreDistribution.find((item) => {
    const min = item.min ?? 0
    return score >= min && score < item.max
  })
  return bucket?.color ?? '#0f8b6d'
}

const scoreTrendChartRef = ref<HTMLElement | null>(null)
const scoreDistributionChartRef = ref<HTMLElement | null>(null)
const scoreScatterChartRef = ref<HTMLElement | null>(null)
const abilityChartRef = ref<HTMLElement | null>(null)
let scoreTrendChartInstance: echarts.ECharts | null = null
let scoreDistributionChartInstance: echarts.ECharts | null = null
let scoreScatterChartInstance: echarts.ECharts | null = null
let abilityChartInstance: echarts.ECharts | null = null

const dashboardTooltipStyle = {
  backgroundColor: '#fffefa',
  borderColor: '#e4dfd3',
  borderWidth: 1,
  textStyle: {
    color: '#191919',
    fontSize: 12,
  },
}

watch([phase, dashboardRange, dashboardMode, dashboardCustomRange, dashboardGrowth], async () => {
  await nextTick()
  if (phase.value !== 'dashboard') return
  renderDashboardCharts()
}, { immediate: true })

useEventListener(window, 'resize', () => {
  scoreTrendChartInstance?.resize()
  scoreDistributionChartInstance?.resize()
  scoreScatterChartInstance?.resize()
  abilityChartInstance?.resize()
})

function renderDashboardCharts() {
  renderScoreTrendChart()
  renderScoreDistributionChart()
  renderScoreScatterChart()
  renderAbilityChart()
}

function renderScoreTrendChart() {
  const trend = dashboardGrowth.value.essayScoreTrend
  if (!scoreTrendChartRef.value || trend.length < 2) {
    if (scoreTrendChartInstance) {
      scoreTrendChartInstance.dispose()
      scoreTrendChartInstance = null
    }
    return
  }
  if (!scoreTrendChartInstance) {
    scoreTrendChartInstance = echarts.init(scoreTrendChartRef.value)
  }
  const highest = trend.reduce((best, item) => (item.score > best.score ? item : best), trend[0])
  const latest = trend[trend.length - 1]
  scoreTrendChartInstance.setOption({
    animationDuration: 650,
    color: ['#0f8b6d'],
    grid: { top: 34, right: 24, bottom: 34, left: 38 },
    tooltip: {
      trigger: 'axis',
      ...dashboardTooltipStyle,
      formatter: (params: any) => {
        const dataIndex = params[0]?.dataIndex ?? 0
        const point = trend[dataIndex]
        if (!point) return ''
        const modeLabel = point.mode === 'exam' ? '考试模式' : '自由模式'
        const deltaText = point.delta > 0 ? `+${point.delta}` : point.delta < 0 ? `${point.delta}` : '持平'
        return [
          `<strong>${point.title}</strong>`,
          modeLabel,
          `最新得分：<strong>${point.score}</strong> 分`,
          `较上一篇：<strong>${deltaText}</strong>`,
          `评分时间：${point.scoredAt}`,
          `AI建议：${point.aiSuggestion || '继续保持当前练习节奏。'}`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map(item => `第${item.essayNo}篇`),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#ded9ce' } },
      axisLabel: { color: '#8b8579', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 50,
      max: 100,
      interval: 10,
      axisLabel: { color: '#8b8579', fontSize: 11, formatter: unfoldScoreAxisLabel },
      splitLine: { lineStyle: { color: 'rgba(222, 217, 206, 0.42)', type: 'dashed' } },
    },
    series: [{
      name: '得分',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      data: trend.map(item => ({
        value: foldScoreForChart(item.score),
        itemStyle: item.essayNo === latest?.essayNo
          ? { color: '#0f8b6d', borderColor: '#0f8b6d', borderWidth: 3 }
          : { color: '#fffefa', borderColor: '#0f8b6d', borderWidth: 2.5 },
      })),
      lineStyle: {
        color: '#0f8b6d',
        width: 3.5,
        shadowBlur: 8,
        shadowColor: 'rgba(15, 139, 109, 0.18)',
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(15, 139, 109, 0.16)' },
          { offset: 1, color: 'rgba(15, 139, 109, 0.01)' },
        ]),
      },
      markArea: {
        silent: true,
        label: { show: false },
        data: dashboardGrowth.value.scoreBands.map(band => [
          {
            name: band.label,
            yAxis: band.min === 0 ? 50 : band.min,
            itemStyle: { color: `${band.color}90` },
          },
          { yAxis: band.max },
        ]),
      },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { show: false },
        lineStyle: {
          color: 'rgba(31, 28, 21, 0.18)',
          width: 1,
          type: 'solid',
        },
        data: [60, 70, 80, 90].map(value => ({ yAxis: value })),
      },
      markPoint: highest
        ? {
            symbol: 'roundRect',
            symbolSize: [72, 28],
            symbolOffset: [0, -22],
            itemStyle: { color: '#fff7ed', borderColor: '#d97706', borderWidth: 1 },
            label: {
              color: '#c2410c',
              fontSize: 11,
              fontWeight: 800,
              formatter: `最高${highest.score}`,
            },
            data: [{ coord: [`第${highest.essayNo}篇`, foldScoreForChart(highest.score)] }],
          }
        : undefined,
    }],
  })
}

function renderScoreDistributionChart() {
  if (!scoreDistributionChartRef.value) return
  if (!scoreDistributionChartInstance) {
    scoreDistributionChartInstance = echarts.init(scoreDistributionChartRef.value)
  }

  scoreDistributionChartInstance.setOption({
    animationDuration: 650,
    color: dashboardGrowth.value.scoreDistribution.map(item => item.color),
    tooltip: {
      trigger: 'item',
      ...dashboardTooltipStyle,
      formatter: (params: any) => {
        const item = dashboardGrowth.value.scoreDistribution[params.dataIndex]
        if (!item) return ''
        return `${item.label} · ${item.stage}<br/>作文：<strong>${item.count}</strong> 篇<br/>占比：<strong>${item.percent}%</strong>`
      },
    },
    series: [{
      name: '得分分布',
      type: 'pie',
      radius: ['68%', '84%'],
      center: ['50%', '50%'],
      startAngle: 92,
      minAngle: 8,
      avoidLabelOverlap: true,
      label: { show: false },
      labelLine: { show: false },
      itemStyle: {
        borderColor: '#fbfaf7',
        borderWidth: 2,
        borderRadius: 3,
      },
      emphasis: {
        scaleSize: 3,
        itemStyle: {
          shadowBlur: 14,
          shadowColor: 'rgba(31, 28, 21, 0.12)',
        },
      },
      data: dashboardGrowth.value.scoreDistribution.map(item => ({
        name: `${item.label} ${item.stage}`,
        value: item.count,
      })),
    }],
  })
}

function renderScoreScatterChart() {
  const points = dashboardGrowth.value.scoreScatter
  if (!scoreScatterChartRef.value || points.length === 0) {
    if (scoreScatterChartInstance) {
      scoreScatterChartInstance.dispose()
      scoreScatterChartInstance = null
    }
    return
  }
  if (!scoreScatterChartInstance) {
    scoreScatterChartInstance = echarts.init(scoreScatterChartRef.value)
  }

  const months = Array.from(new Set(
    [...points]
      .sort((a, b) => getScatterMonthTime(a.scoredAt) - getScatterMonthTime(b.scoredAt))
      .map(item => item.month),
  ))

  scoreScatterChartInstance.setOption({
    animationDuration: 650,
    grid: { top: 18, right: 18, bottom: 30, left: 44 },
    tooltip: {
      trigger: 'item',
      ...dashboardTooltipStyle,
      formatter: (params: any) => {
        const point = params.data?.essay
        if (!point) return ''
        const modeLabel = point.mode === 'exam' ? '考试模式' : '自由模式'
        return [
          `<strong>${point.title}</strong>`,
          modeLabel,
          `最新得分：<strong>${point.score}</strong> 分`,
          `评分时间：${point.scoredAt}`,
          `区间：${params.data?.bandLabel ?? '-'}`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      data: months,
      boundaryGap: true,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#ded9ce' } },
      axisLabel: { color: '#8b8579', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 50,
      max: 100,
      interval: 10,
      axisLabel: { color: '#8b8579', fontSize: 11, formatter: unfoldScoreAxisLabel },
      splitLine: { lineStyle: { color: 'rgba(222, 217, 206, 0.42)', type: 'dashed' } },
    },
    series: [{
      name: '作文落点',
      type: 'scatter',
      symbolSize: 10,
      data: points.map(item => ({
        value: [item.month, foldScoreForChart(item.score)],
        essay: item,
        bandLabel: item.bandLabel,
        itemStyle: {
          color: resolveScoreBandColor(item.score),
          borderColor: '#fffefa',
          borderWidth: 2,
          shadowBlur: 10,
          shadowColor: 'rgba(31, 28, 21, 0.12)',
        },
      })),
      markArea: {
        silent: true,
        label: { show: false },
        data: dashboardGrowth.value.scoreBands.map(band => [
          {
            yAxis: band.min === 0 ? 50 : band.min,
            itemStyle: { color: `${band.color}58` },
          },
          { yAxis: band.max },
        ]),
      },
    }],
  })
}

function renderAbilityChart() {
  if (!abilityChartRef.value) return
  if (!abilityChartInstance) {
    abilityChartInstance = echarts.init(abilityChartRef.value)
  }
  const trend = mockAbilityDashboard.trend
  const dates = trend.map(item => item.date)
  const makeSeries = (
    name: string,
    key: 'overall' | 'grammar' | 'vocabulary' | 'coherence',
    color: string,
    extra: Record<string, unknown> = {},
  ) => ({
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: key === 'overall' ? 7 : 5,
    data: trend.map(item => item[key]),
    lineStyle: { width: key === 'overall' ? 4 : 2.5, color },
    itemStyle: { color, borderColor: '#fffefa', borderWidth: 2 },
    ...extra,
  })

  abilityChartInstance.setOption({
    animationDuration: 650,
    color: ['#059669', '#d97706', '#2563eb', '#7c3aed'],
    legend: {
      top: 4,
      right: 8,
      icon: 'roundRect',
      itemWidth: 18,
      itemHeight: 3,
      textStyle: { color: '#6f6a60', fontSize: 11 },
    },
    grid: { top: 44, right: 24, bottom: 34, left: 42 },
    tooltip: {
      trigger: 'axis',
      ...dashboardTooltipStyle,
      formatter: (params: any) => {
        const rows = params
          .map((item: any) => `${item.marker}${item.seriesName}：<strong>${item.value}</strong>`)
          .join('<br/>')
        return `${params[0].axisValue}<br/>${rows}`
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#ded9ce' } },
      axisLabel: { color: '#8b8579', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 40,
      max: 100,
      interval: 10,
      axisLabel: { color: '#8b8579', fontSize: 11 },
      splitLine: { lineStyle: { color: '#ebe5da', type: 'dashed' } },
    },
    series: [
      makeSeries('综合', 'overall', '#059669', {
        markArea: {
          silent: true,
          label: { color: 'rgba(31, 31, 31, 0.35)', fontSize: 11, fontWeight: 700 },
          data: [
            [{ name: 'C2', yAxis: 90, itemStyle: { color: 'rgba(124, 58, 237, 0.06)' } }, { yAxis: 100 }],
            [{ name: 'C1', yAxis: 80, itemStyle: { color: 'rgba(217, 119, 6, 0.055)' } }, { yAxis: 90 }],
            [{ name: 'B2', yAxis: 70, itemStyle: { color: 'rgba(5, 150, 105, 0.08)' } }, { yAxis: 80 }],
            [{ name: 'B1', yAxis: 55, itemStyle: { color: 'rgba(37, 99, 235, 0.055)' } }, { yAxis: 70 }],
            [{ name: 'A2', yAxis: 40, itemStyle: { color: 'rgba(107, 114, 128, 0.05)' } }, { yAxis: 55 }],
          ],
        },
      }),
      makeSeries('语法', 'grammar', '#d97706'),
      makeSeries('词汇', 'vocabulary', '#2563eb'),
      makeSeries('结构', 'coherence', '#7c3aed'),
    ],
  })
}

function disposeDashboardCharts() {
  if (scoreTrendChartInstance) { scoreTrendChartInstance.dispose(); scoreTrendChartInstance = null }
  if (scoreDistributionChartInstance) { scoreDistributionChartInstance.dispose(); scoreDistributionChartInstance = null }
  if (scoreScatterChartInstance) { scoreScatterChartInstance.dispose(); scoreScatterChartInstance = null }
  if (abilityChartInstance) { abilityChartInstance.dispose(); abilityChartInstance = null }
}

// Immersive toggle: only editor is immersive
watch(phase, (p, prev) => {
  setImmersive(p === 'editor' ? true : false)
  if (prev === 'dashboard' && p !== 'dashboard') {
    disposeDashboardCharts()
  }
  if (!booting.value && p === 'doc-list' && prev && prev !== 'doc-list') {
    void loadDocList()
  }
  if (!booting.value && p === 'past-prompt-select' && pastPromptItems.value.length === 0 && !pastPromptLoading.value) {
    void loadPastPrompts(1)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  setImmersive(null)
  disposeDashboardCharts()
})

// Carousel
const carouselIndex = ref(0)

// Charts
const chartRef = ref<HTMLElement | null>(null)
const radarRef = ref<HTMLElement | null>(null)
const errorRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null
let radarInstance: echarts.ECharts | null = null
let errorInstance: echarts.ECharts | null = null

// Stats data
const writingStats = ref<WritingStatsResponse | null>(null)

const hasRadarData = computed(() => {
  const s = writingStats.value
  return s && (s.avgContentQuality != null || s.avgTaskAchievement != null || s.avgStructureScore != null || s.avgVocabularyScore != null || s.avgGrammarScore != null || s.avgExpressionScore != null)
})

const hasErrorData = computed(() => {
  const s = writingStats.value
  return s && (s.totalGrammarErrors > 0 || s.totalSpellingErrors > 0 || s.totalVocabularyErrors > 0)
})

watch([() => scoredDocs.value, chartRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 0 && scoredDocs.value.length >= 3 && chartRef.value) {
    renderChart()
  }
}, { immediate: true })

watch([hasRadarData, radarRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 1 && hasRadarData.value && radarRef.value) {
    renderRadarChart()
  }
}, { immediate: true })

watch([hasErrorData, errorRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 2 && hasErrorData.value && errorRef.value) {
    renderErrorChart()
  }
}, { immediate: true })

function renderChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  const sorted = [...scoredDocs.value].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  chartInstance.setOption({
    grid: { top: 10, right: 16, bottom: 24, left: 36 },
    xAxis: {
      type: 'category',
      data: sorted.map((_, i) => `#${i + 1}`),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [{
      type: 'line',
      data: sorted.map(d => d.latestScore),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#047857', width: 2 },
      itemStyle: { color: '#047857' },
      areaStyle: { color: 'rgba(4, 120, 87, 0.08)' },
    }],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => `第${params[0].dataIndex + 1}篇：${params[0].value}分`,
    },
  })
}

function renderRadarChart() {
  if (!radarRef.value) return
  if (radarInstance) radarInstance.dispose()
  radarInstance = echarts.init(radarRef.value)
  const s = writingStats.value!
  radarInstance.setOption({
    radar: {
      indicator: [
        { name: '内容质量', max: 100 },
        { name: '任务完成', max: 100 },
        { name: '篇章结构', max: 100 },
        { name: '词汇运用', max: 100 },
        { name: '语法准确', max: 100 },
        { name: '语言表达', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: '#6b7280', fontSize: 11 },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
      splitArea: { areaStyle: { color: ['#fff', '#f9fafb', '#f3f4f6', '#e5e7eb'] } },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          s.avgContentQuality ?? 0,
          s.avgTaskAchievement ?? 0,
          s.avgStructureScore ?? 0,
          s.avgVocabularyScore ?? 0,
          s.avgGrammarScore ?? 0,
          s.avgExpressionScore ?? 0,
        ],
        areaStyle: { color: 'rgba(4, 120, 87, 0.15)' },
        lineStyle: { color: '#047857', width: 2 },
        itemStyle: { color: '#047857' },
        symbol: 'circle',
        symbolSize: 5,
      }],
    }],
    tooltip: {
      trigger: 'item',
    },
  })
}

function renderErrorChart() {
  if (!errorRef.value) return
  if (errorInstance) errorInstance.dispose()
  errorInstance = echarts.init(errorRef.value)
  const s = writingStats.value!
  const data = [
    { value: s.totalGrammarErrors, name: '语法错误', itemStyle: { color: '#ef4444' } },
    { value: s.totalSpellingErrors, name: '拼写错误', itemStyle: { color: '#f59e0b' } },
    { value: s.totalVocabularyErrors, name: '词汇错误', itemStyle: { color: '#6366f1' } },
  ].filter(d => d.value > 0)
  errorInstance.setOption({
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '55%'],
      avoidLabelOverlap: true,
      label: {
        formatter: '{b}\n{c}次 ({d}%)',
        fontSize: 11,
        color: '#374151',
        lineHeight: 16,
      },
      emphasis: {
        label: { fontSize: 13, fontWeight: 'bold' },
      },
      data,
    }],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}次 ({d}%)',
    },
  })
}

onBeforeUnmount(() => {
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
  if (radarInstance) { radarInstance.dispose(); radarInstance = null }
  if (errorInstance) { errorInstance.dispose(); errorInstance = null }
})

// Lifecycle
onMounted(async () => {
  const cached = stageCache.value
  if (!cached || cached === '' || cached === '__error__') {
    router.replace({ path: '/app/stage-setup', query: { redirect: '/app/writing' } })
    return
  }
  currentStage.value = cached

  const currentPhase = resolveRoutePhase()

  if (currentPhase === 'editor') {
    if (!initialDocId.value) {
      await loadDocList()
      booting.value = false
      await navigateToPhase('doc-list', true)
      return
    }
    booting.value = false
    return
  }

  if (currentPhase === 'doc-list') {
    await loadDocList()
  }

  booting.value = false
})

async function loadDocList() {
  docListLoading.value = true
  try {
    const [docRes, statsRes] = await Promise.all([
      getWritingDocuments(0, 200),
      getWritingStats().catch(() => null),
    ])
    docList.value = docRes.items ?? []
    writingStats.value = statsRes
  } catch (e) {
    console.warn('[WritingPage] loadDocList failed', e)
  } finally {
    docListLoading.value = false
  }
}

async function createFreeDoc(seed?: { title?: string; initialTaskPrompt?: string | null }) {
  chosenMode.value = 'free'
  initialTaskPrompt.value = seed?.initialTaskPrompt ?? undefined
  examMaxScore.value = null
  initialExistingContent.value = null
  initialSubmitCount.value = 0
  try {
    const now = new Date()
    const freeTitle = seed?.title?.trim() || `自由写作 ${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    const session = await startWritingSession({
      mode: 'free',
      title: freeTitle,
      studyStage: currentStage.value,
      sourceType: 'free_input',
      titleSnapshot: freeTitle,
      topicTitle: freeTitle,
    })
    initialDocId.value = session.docId
    initialExistingContent.value = session.existingContent ?? null
    initialSubmitCount.value = session.submitCount ?? 0
    const sessionMetadata = await getWritingSessionMetadata(session.docId).catch((err) => {
      console.warn('[WritingPage] load session metadata failed', err)
      return null
    })
    if (sessionMetadata) {
      console.log('[WritingPage] writing metadata', sessionMetadata)
    }
  } catch (e) {
    console.warn('[WritingPage] create free doc failed', e)
    initialDocId.value = null
    initialExistingContent.value = null
    showToast('创建写作会话失败，请重试', 'error')
    return
  }
  await navigateToPhase('editor')
}

async function createBlankFreeDoc() {
  await createFreeDoc()
}

function getNewTaskEffectiveWordRange() {
  if (newTaskWordRange.value === '__custom__') {
    return newTaskCustomWordRange.value.trim() || null
  }
  return newTaskWordRange.value?.trim() || null
}

async function continueNewWritingTask() {
  if (newTaskMode.value === 'free') {
    examSetupInitialGenre.value = null
    examSetupInitialWordRange.value = null
    examSetupInitialTab.value = null
    await createBlankFreeDoc()
    return
  }
  if (newTaskSource.value === 'past_prompt') {
    examSetupInitialGenre.value = null
    examSetupInitialWordRange.value = null
    examSetupInitialTab.value = null
    await navigateToPhase('past-prompt-select')
    return
  }
  const effectiveWordRange = getNewTaskEffectiveWordRange()
  if (!newTaskGenre.value) {
    showToast('请先选择体裁', 'error')
    return
  }
  if (!effectiveWordRange) {
    showToast('请先选择字数', 'error')
    return
  }
  examSetupInitialGenre.value = newTaskGenre.value
  examSetupInitialWordRange.value = effectiveWordRange
  examSetupInitialTab.value = 'ai'
  await openExamSetupFromModeSelect()
}

async function openExamSetupFromModeSelect() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  await navigateToPhase('exam-setup')
}

const pastPromptKeyword = ref('')
const pastPromptYear = ref<number | null>(null)
const pastPromptYearSelect = ref(0)
const pastPromptItems = ref<EssayPromptItem[]>([])
const pastPromptYears = ref<number[]>([])
const pastPromptLoading = ref(false)
const selectedPastPrompt = ref<EssayPromptItem | null>(null)

function onPastPromptYearChange() {
  pastPromptYear.value = pastPromptYearSelect.value === 0 ? null : pastPromptYearSelect.value
  selectedPastPrompt.value = null
  void loadPastPrompts(1)
}

async function loadPastPrompts(page = 1) {
  pastPromptLoading.value = true
  try {
    const res = await getEssayPrompts({
      stageId: getStageId(currentStage.value),
      keyword: pastPromptKeyword.value.trim() || undefined,
      year: pastPromptYear.value ?? undefined,
      page,
      size: 12,
    })
    pastPromptItems.value = res.items
    if (res.years.length > 0) {
      pastPromptYears.value = res.years
    }
    if (selectedPastPrompt.value && !res.items.some((item) => item.id === selectedPastPrompt.value?.id)) {
      selectedPastPrompt.value = null
    }
  } catch (e) {
    console.warn('[WritingPage] load past prompts failed', e)
    showToast('加载历年真题失败，请稍后重试', 'error')
  } finally {
    pastPromptLoading.value = false
  }
}

function formatPastPromptWordRange(prompt: EssayPromptItem) {
  if (prompt.wordCountMin != null && prompt.wordCountMax != null) {
    return `${prompt.wordCountMin}-${prompt.wordCountMax} 词`
  }
  if (prompt.wordCountMin != null) {
    return `${prompt.wordCountMin}+ 词`
  }
  return ''
}

function buildPastPromptTopicInfo(prompt: EssayPromptItem): ExamTopicInfo {
  const wordRange = formatPastPromptWordRange(prompt).replace(/\s*词$/, '') || null
  const promptType: ExamPromptType = prompt.materialText?.trim()
    ? 'material'
    : prompt.imageUrl?.trim() || prompt.imageDescription?.trim()
      ? 'comic'
      : 'general'
  const promptTitle = prompt.title?.trim() || prompt.paper?.trim() || prompt.promptText.trim()
  return {
    paper: prompt.paper?.trim() || null,
    promptSheetId: null,
    topic: promptTitle,
    genre: null,
    wordRange,
    requirements: prompt.promptText.trim(),
    imageDescription: prompt.imageDescription?.trim() || null,
    materialText: prompt.materialText?.trim() || null,
    attachmentImageUrl: prompt.imageUrl?.trim() || null,
    maxScore: prompt.maxScore ?? 100,
    sourceType: 'past_prompt',
    examType: currentStage.value,
    taskType: prompt.task ?? null,
    minWords: prompt.wordCountMin ?? null,
    recommendedMaxWords: prompt.wordCountMax ?? null,
    promptType,
    chartSpec: null,
    comicScenes: [],
  }
}

async function startWritingFromPastPrompt() {
  if (!selectedPastPrompt.value) {
    showToast('请先选择一套历年真题', 'error')
    return
  }
  await onExamConfirm(buildPastPromptTopicInfo(selectedPastPrompt.value))
}

async function onExamConfirm(info: ExamTopicInfo) {
  resumeTopicForSetup.value = undefined
  chosenMode.value = 'exam'
  const prompt = buildExamTaskPrompt(info)
  examMaxScore.value = info.maxScore ?? 100
  initialTaskPrompt.value = prompt
  initialExistingContent.value = null
  initialSubmitCount.value = 0
  try {
    const session = await startWritingSession({
      mode: 'exam',
      taskPrompt: prompt,
      title: info.topic.slice(0, 100),
      studyStage: currentStage.value,
      sourceType: info.sourceType,
      titleSnapshot: info.topic.slice(0, 100),
      topicTitle: info.topic,
      promptText: prompt,
      promptSheetId: info.promptSheetId ?? null,
      attachmentImageUrl: info.attachmentImageUrl ?? null,
      genre: info.genre ?? undefined,
      examType: info.examType,
      taskType: info.taskType,
      minWords: info.minWords,
      recommendedMaxWords: info.recommendedMaxWords,
      maxScore: info.maxScore,
    })
    initialDocId.value = session.docId
    initialExistingContent.value = session.existingContent ?? null
    initialSubmitCount.value = session.submitCount ?? 0
    const sessionMetadata = await getWritingSessionMetadata(session.docId).catch((err) => {
      console.warn('[WritingPage] load session metadata failed', err)
      return null
    })
    if (sessionMetadata) {
      console.log('[WritingPage] writing metadata', sessionMetadata)
    }
  } catch (e) {
    console.warn('[WritingPage] create exam doc failed', e)
    initialDocId.value = null
    initialExistingContent.value = null
    showToast('创建考试写作会话失败，请重试', 'error')
    return
  }
  await navigateToPhase('editor')
}

async function openDocument(doc: WritingDocumentItem) {
  chosenMode.value = doc.taskPrompt ? 'exam' : 'free'
  initialTaskPrompt.value = doc.taskPrompt ?? undefined
  initialDocId.value = doc.docId
  initialExistingContent.value = null
  examMaxScore.value = null
  initialSubmitCount.value = doc.submitCount ?? 0

  // 考试模式草稿（status=0，从题目设置页保存退出，未点击"开始写作"）→ 回到题目设置页
  if (doc.taskPrompt && doc.status === 0) {
    const metadata = await getWritingSessionMetadata(doc.docId).catch((err) => {
      console.warn('[WritingPage] load setup draft metadata failed', err)
      return null
    })
    resumeMetadataForSetup.value = metadata
    resumeTopicForSetup.value = metadata?.topicTitle?.trim() || doc.title?.trim() || undefined
    void navigateToPhase('exam-setup')
    return
  }

  await navigateToPhase('editor')
}

async function onExamSetupBack() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  examSetupInitialGenre.value = null
  examSetupInitialWordRange.value = null
  examSetupInitialTab.value = null
  await navigateToPhase('doc-list')
}

async function onExamSetupSwitchMode(payload: { mode: 'free' | 'exam'; info?: ExamTopicInfo | null }) {
  if (payload.mode === 'free') {
    resumeTopicForSetup.value = undefined
    resumeMetadataForSetup.value = null
    const seedInfo = payload.info ?? null
    await createFreeDoc(seedInfo ? {
      title: seedInfo.topic.slice(0, 100),
      initialTaskPrompt: buildExamTaskPrompt(seedInfo),
    } : undefined)
  }
}

async function onEditorBack() {
  initialDocId.value = null
  initialExistingContent.value = null
  initialTaskPrompt.value = undefined
  examMaxScore.value = null
  await navigateToPhase('doc-list')
}

async function onExamSaveDraft() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  examSetupInitialGenre.value = null
  examSetupInitialWordRange.value = null
  examSetupInitialTab.value = null
  await navigateToPhase('doc-list')
}

// ── Card menu ──
const openMenuId = ref<string | null>(null)

function toggleMenu(docId: string) {
  openMenuId.value = openMenuId.value === docId ? null : docId
}

// Close menu on outside click (auto-cleanup by useEventListener)
useEventListener(document, 'click', () => { openMenuId.value = null })

// ── Rename ──
const renameDialog = ref({ visible: false, docId: '', title: '' })

function startRename(doc: WritingDocumentItem) {
  openMenuId.value = null
  renameDialog.value = { visible: true, docId: doc.docId, title: doc.title || '' }
}

async function doRename() {
  const { docId, title } = renameDialog.value
  if (!title.trim()) return
  try {
    await renameDocument(docId, title.trim())
    const item = docList.value.find(d => d.docId === docId)
    if (item) item.title = title.trim()
  } catch (e) {
    console.warn('[WritingPage] rename failed', e)
  }
  renameDialog.value.visible = false
}

// ── Delete ──
const deleteDialog = ref({ visible: false, docId: '', title: '' })

function confirmDelete(doc: WritingDocumentItem) {
  openMenuId.value = null
  deleteDialog.value = { visible: true, docId: doc.docId, title: doc.title || '未命名作文' }
}

async function doDelete() {
  const { docId } = deleteDialog.value
  try {
    await deleteDocument(docId)
    docList.value = docList.value.filter(d => d.docId !== docId)
  } catch (e) {
    console.warn('[WritingPage] delete failed', e)
  }
  deleteDialog.value.visible = false
}

function scoreColor(score: number) {
  if (score >= 80) return 'high'
  if (score >= 60) return 'mid'
  return 'low'
}

function docStatusLabel(doc: WritingDocumentItem) {
  if (doc.status === 0 && doc.taskPrompt) return '题目草稿'
  if (doc.latestScore != null) return '已评分'
  if ((doc.submitCount ?? 0) > 0) return '待查看'
  return '待评分'
}

function docStatusClass(doc: WritingDocumentItem) {
  if (doc.status === 0 && doc.taskPrompt) return 'draft'
  if (doc.latestScore != null) return 'scored'
  if ((doc.submitCount ?? 0) > 0) return 'review'
  return 'pending'
}

function docPromptSummary(doc: WritingDocumentItem) {
  const prompt = doc.taskPrompt?.replace(/\s+/g, ' ').trim()
  return prompt || '自由写作，无固定题目'
}

function docScoreDelta(doc: WritingDocumentItem) {
  if (doc.initialScore == null || doc.latestScore == null || doc.latestScore === doc.initialScore) {
    return null
  }
  return doc.latestScore - doc.initialScore
}

function docNextStep(doc: WritingDocumentItem) {
  if (doc.status === 0 && doc.taskPrompt) return '继续完善题目设置，再进入写作。'
  if (doc.latestScore == null) return '提交评分，生成讲评和修改建议。'
  const delta = docScoreDelta(doc)
  if (delta != null && delta > 0) return '分数已有提升，可以继续做同类题巩固。'
  if (doc.latestScore >= 80) return '提炼高分表达，沉淀为个人模板。'
  if (doc.latestScore >= 60) return '根据讲评补强结构、词汇和表达。'
  return '先订正基础错误，再重写一版。'
}

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  return d.toLocaleDateString('zh-CN')
}
</script>

<style src="@/styles/gate.css" />
<style scoped>
/* ── Hub page ── */
.hub-page {
  min-height: 100%;
  background: #f3f4f6;
  padding: 28px 24px 48px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}

.hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.hub-title {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.new-doc-btn {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.new-doc-btn:hover { background: #065f46; }

/* ── Analytics carousel ── */
.analytics-carousel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 18px 20px 14px;
  margin-bottom: 24px;
}

.carousel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.carousel-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.carousel-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.carousel-indicator {
  font-size: 12px;
  color: #9ca3af;
  min-width: 28px;
  text-align: center;
}

.carousel-arrow {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
}
.carousel-arrow:hover:not(:disabled) {
  border-color: #047857;
  color: #047857;
  background: #ecfdf5;
}
.carousel-arrow:disabled {
  opacity: 0.25;
  cursor: default;
}

.carousel-viewport {
  overflow: hidden;
  width: 100%;
}

.carousel-track {
  display: flex;
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.carousel-slide {
  min-width: 100%;
  flex-shrink: 0;
}

.carousel-chart {
  width: 100%;
  height: 170px;
}

.carousel-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 140px;
  color: #9ca3af;
  font-size: 13px;
}

.carousel-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding-top: 8px;
}

.carousel-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: none;
  background: #d1d5db;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s;
}
.carousel-dot.active {
  background: #047857;
  width: 16px;
  border-radius: 3px;
}

/* ── Search bar ── */
.search-bar {
  position: relative;
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.search-icon {
  position: absolute;
  left: 14px;
  color: #9ca3af;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 10px 36px 10px 40px;
  font-size: 14px;
  color: #111827;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-input:focus {
  border-color: #047857;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.08);
}
.search-input::placeholder { color: #9ca3af; }

.search-clear {
  position: absolute;
  right: 8px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #9ca3af;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  line-height: 1;
}
.search-clear:hover { color: #374151; background: #f3f4f6; }

/* ── Document section ── */
.doc-section {
  margin-top: 4px;
}

.doc-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.doc-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.doc-filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-pills {
  display: flex;
  gap: 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 2px;
}

.filter-pill {
  padding: 4px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-pill.active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}
.filter-pill:hover:not(.active) {
  background: #f9fafb;
}

.sort-select {
  padding: 5px 10px;
  font-size: 13px;
  color: #6b7280;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  outline: none;
}
.sort-select:focus { border-color: #047857; }

/* ── Document grid ── */
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.doc-card {
  display: flex;
  flex-direction: column;
  min-height: 180px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}
.doc-card:hover {
  border-color: #047857;
  box-shadow: 0 4px 16px rgba(4, 120, 87, 0.08);
  transform: translateY(-2px);
}

.doc-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  position: relative;
}

.doc-menu-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  border-radius: 6px;
  color: #9ca3af;
  cursor: pointer;
  transition: all 0.15s;
  opacity: 0;
}
.doc-card:hover .doc-menu-btn { opacity: 1; }
.doc-menu-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.doc-menu {
  position: absolute;
  top: 32px;
  right: 0;
  z-index: 20;
  min-width: 130px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.10);
  padding: 4px;
  animation: menuIn 0.12s ease;
}
@keyframes menuIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.doc-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: #374151;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s;
}
.doc-menu-item:hover { background: #f3f4f6; }
.doc-menu-danger { color: #dc2626; }
.doc-menu-danger:hover { background: #fef2f2; }

.doc-mode-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}
.doc-mode-tag.exam { background: #fef3c7; color: #92400e; }
.doc-mode-tag.free { background: #dbeafe; color: #1e40af; }

.doc-submit-count {
  font-size: 11px;
  color: #9ca3af;
}

.doc-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-card-prompt {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-card-score-area {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: auto;
  margin-bottom: 12px;
}

.doc-score-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.doc-score-num.high { color: #047857; }
.doc-score-num.mid { color: #d97706; }
.doc-score-num.low { color: #dc2626; }

.doc-score-max {
  font-size: 13px;
  color: #9ca3af;
}

.doc-score-delta {
  font-size: 12px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 4px;
}
.doc-score-delta.up { background: #ecfdf5; color: #047857; }
.doc-score-delta.down { background: #fef2f2; color: #dc2626; }

.doc-score-none {
  font-size: 14px;
  color: #9ca3af;
}

.doc-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.doc-card-time {
  font-size: 12px;
  color: #9ca3af;
}

.doc-card-action {
  font-size: 12px;
  color: #047857;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.15s;
}
.doc-card:hover .doc-card-action { opacity: 1; }

/* ── Dialogs ── */
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
}

.confirm-close {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #9ca3af;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  line-height: 1;
}
.confirm-close:hover { color: #374151; background: #f3f4f6; }

.rename-dialog {
  position: relative;
  width: 90%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 28px 24px 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.rename-title {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 16px;
}

.rename-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.rename-input:focus { border-color: #047857; box-shadow: 0 0 0 3px rgba(4,120,87,0.1); }

.rename-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.btn-cancel {
  padding: 8px 18px;
  font-size: 14px;
  color: #6b7280;
  background: none;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: #9ca3af; color: #374151; }

.gate-btn--danger { background: #dc2626; }
.gate-btn--danger:hover { background: #b91c1c; }

.delete-hint {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 4px;
  line-height: 1.5;
}

/* ── Pagination ── */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  margin-top: 24px;
}

.page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover:not(:disabled):not(.active) {
  border-color: #047857;
  color: #047857;
}
.page-btn.active {
  background: #047857;
  border-color: #047857;
  color: #fff;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
.page-btn.ellipsis {
  border: none;
  background: none;
  cursor: default;
}

/* ── Empty state ── */
.doc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 20px;
}

.empty-icon { font-size: 48px; margin: 0 0 12px; }
.empty-text { font-size: 16px; font-weight: 600; color: #374151; margin: 0 0 4px; }
.empty-hint { font-size: 13px; color: #9ca3af; margin: 0; }

/* ── Mode select ── */
.mode-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-width: 520px;
  width: 100%;
}

.mode-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 20px;
  border: 2px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}
.mode-card:hover {
  border-color: #047857;
  box-shadow: 0 4px 16px rgba(4, 120, 87, 0.10);
  transform: translateY(-2px);
}

.mode-icon { font-size: 32px; line-height: 1; }
.mode-name { font-size: 17px; font-weight: 700; color: #111827; }
.mode-desc { font-size: 13px; color: #6b7280; text-align: center; line-height: 1.4; }

.back-link {
  margin-top: 24px;
  font-size: 14px;
  color: #6b7280;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.15s;
}
.back-link:hover { color: #047857; }

/* ── New task setup ── */
.task-modal-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: #f3f4f6;
}

.task-modal-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
}

.task-modal {
  position: relative;
  z-index: 1;
  width: min(880px, calc(100vw - 32px));
  padding: 32px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.20);
}

.task-modal-close {
  position: absolute;
  top: 20px;
  right: 22px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 32px;
  line-height: 1;
  cursor: pointer;
}
.task-modal-close:hover { color: #334155; }

.task-modal-kicker {
  margin: 0 0 8px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.task-modal-title {
  margin: 0 0 28px;
  color: #0f172a;
  font-size: 28px;
  font-weight: 900;
}

.task-modal-section {
  padding: 22px 0;
  border-top: 1px solid #e5e7eb;
}

.task-modal-label {
  margin: 0 0 12px;
  color: #334155;
  font-size: 15px;
  font-weight: 800;
}

.task-option-grid {
  display: grid;
  gap: 14px;
}

.task-option-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-option {
  min-height: 86px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}
.task-option:hover {
  border-color: #10b981;
}
.task-option.active {
  border-color: #10b981;
  background: #ecfdf5;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.20);
}

.task-option-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.task-option-desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
}

.task-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.task-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.task-select,
.task-input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.task-word-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 10px;
}

.task-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 26px;
  border-top: 1px solid #e5e7eb;
}

.task-btn {
  min-width: 116px;
  height: 44px;
  padding: 0 22px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  transition: opacity 0.15s, background 0.15s, border-color 0.15s;
}
.task-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.task-btn--secondary {
  background: #fff;
  color: #475569;
  border-color: #cbd5e1;
}
.task-btn--secondary:hover:not(:disabled) {
  border-color: #94a3b8;
}
.task-btn--primary {
  background: #047857;
  color: #fff;
}
.task-btn--primary:hover:not(:disabled) {
  background: #065f46;
}

/* ── Past prompts ── */
.past-prompt-page {
  min-height: 100vh;
  padding: 32px min(5vw, 72px);
  background: #f8fafc;
}

.setup-back-link {
  border: none;
  background: transparent;
  color: #047857;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.past-prompt-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-top: 28px;
  margin-bottom: 20px;
}

.past-prompt-kicker {
  margin: 0 0 6px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.past-prompt-title {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  font-weight: 900;
}

.past-prompt-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 180px auto;
  gap: 12px;
  margin-bottom: 18px;
}

.past-prompt-search,
.past-prompt-select {
  height: 44px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
}

.past-prompt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.7fr);
  gap: 18px;
  align-items: start;
}

.past-prompt-list,
.past-prompt-preview,
.past-prompt-empty {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.past-prompt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.past-prompt-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.past-prompt-item:hover {
  border-color: #10b981;
}
.past-prompt-item.active {
  border-color: #10b981;
  background: #ecfdf5;
}

.past-prompt-item-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.past-prompt-item-meta {
  color: #64748b;
  font-size: 13px;
}

.past-prompt-item-text {
  display: -webkit-box;
  overflow: hidden;
  color: #334155;
  font-size: 14px;
  line-height: 1.55;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.past-prompt-preview {
  position: sticky;
  top: 24px;
  padding: 22px;
}

.past-prompt-preview h3 {
  margin: 0 0 12px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.25;
}

.past-prompt-preview-kicker {
  margin: 0 0 8px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.past-prompt-preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.past-prompt-preview-meta span {
  padding: 5px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.past-prompt-preview-text,
.past-prompt-preview-block p {
  color: #334155;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.past-prompt-preview-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.past-prompt-preview-block strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

.past-prompt-preview-image {
  width: 100%;
  margin-top: 16px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.past-prompt-preview-placeholder {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.past-prompt-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 240px;
  color: #64748b;
}

.hub-page {
  max-width: 1880px;
  padding: 34px 28px 56px;
  background: #f7f5ef;
  color: #191919;
}

.writing-section-tabs {
  display: inline-flex;
  gap: 28px;
  margin: 0 0 28px;
  border-bottom: 1px solid #ded9ce;
}

.writing-section-tab {
  position: relative;
  padding: 0 0 12px;
  color: #7a746a;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.writing-section-tab.active {
  color: #191919;
}

.writing-section-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #191919;
  border-radius: 999px;
}

.dashboard-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px auto;
  align-items: start;
  gap: 28px;
  margin-bottom: 24px;
}

.hub-page--dashboard .dashboard-hero {
  margin-bottom: 18px;
}

.hero-copy {
  min-width: 0;
}

.hero-kicker,
.section-kicker,
.card-eyebrow {
  display: block;
  margin: 0 0 8px;
  color: #6f6a60;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.hub-title {
  margin: 0;
  color: #1e1e1e;
  font-size: 42px;
  font-weight: 800;
  line-height: 1.08;
}

.hero-subtitle {
  margin: 10px 0 0;
  color: #6f6a60;
  font-size: 16px;
  line-height: 1.6;
}

.filter-pills--warm {
  border-color: #ded9ce;
  background: rgba(255, 255, 255, 0.72);
}

.hero-art {
  display: flex;
  justify-content: center;
  min-width: 0;
  color: #1f1f1f;
}

.hero-art svg {
  width: 250px;
  height: 144px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
  opacity: 0.78;
}

.new-doc-btn--academy {
  display: inline-flex;
  align-items: center;
  align-self: start;
  gap: 14px;
  min-height: 48px;
  padding: 0 16px 0 22px;
  border-radius: 12px;
  background: #111111;
  box-shadow: none;
  white-space: nowrap;
}

.new-doc-btn--academy:hover {
  background: #000000;
}

.new-doc-btn--academy svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.new-doc-divider {
  width: 1px;
  align-self: stretch;
  background: rgba(255, 255, 255, 0.18);
}

.writing-home-layout {
  --writing-home-panel-min-height: 860px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 36px;
  align-items: stretch;
}

.writing-home-layout .doc-section {
  display: flex;
  flex-direction: column;
  margin-top: 0;
  min-height: var(--writing-home-panel-min-height);
  padding: 18px;
  background: rgba(255, 255, 255, 0.74);
  border: 1px solid #e4dfd3;
  border-radius: 16px;
  box-shadow: 0 16px 42px rgba(31, 28, 21, 0.05);
}

.writing-home-layout .doc-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.writing-home-layout .doc-card {
  min-height: 168px;
  padding: 18px;
}

.daily-recommendations {
  display: flex;
  flex-direction: column;
  min-height: var(--writing-home-panel-min-height);
  padding: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #e4dfd3;
  border-radius: 16px;
  box-shadow: 0 16px 42px rgba(31, 28, 21, 0.05);
}

.daily-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.daily-header h3 {
  margin: 0;
  color: #191919;
  font-size: 20px;
  font-weight: 800;
}

.daily-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  color: #7a746a;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.daily-refresh:hover {
  color: #191919;
  background: #f2eee5;
}

.daily-refresh svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.daily-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 16px;
  background: #fffefa;
  border: 1px solid #e9e2d4;
  border-radius: 12px;
}

.daily-card + .daily-card {
  margin-top: 12px;
}

.daily-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.daily-tags span {
  padding: 4px 8px;
  color: #087858;
  background: #e8f7ef;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.daily-tags span:first-child {
  color: #9a6200;
  background: #fff2d4;
}

.daily-card h4 {
  margin: 0;
  color: #191919;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.35;
}

.daily-card p {
  margin: 8px 0 0;
  color: #6f6a60;
  font-size: 13px;
  line-height: 1.6;
}

.daily-meta {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  color: #8a8275;
  font-size: 12px;
}

.daily-card button {
  width: 100%;
  margin-top: auto;
  padding: 9px 12px;
  color: #5e4b2c;
  background: #f4efe5;
  border: 1px solid #e4dfd3;
  border-radius: 9px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.daily-card button:hover {
  color: #191919;
  background: #ebe3d5;
  border-color: #d8cfbf;
}

.report-card,
.doc-card {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid #e4dfd3;
  box-shadow: 0 14px 36px rgba(31, 28, 21, 0.04);
}

.dashboard-section {
  margin-top: 30px;
}

.dashboard-state {
  margin: -14px 0 24px;
  padding: 10px 14px;
  border: 1px solid #e4dfd3;
  border-radius: 12px;
  background: #fbfaf7;
  color: #6f6a60;
  font-size: 13px;
  font-weight: 750;
}

.dashboard-state--error {
  border-color: #f1c4bd;
  background: #fff4f2;
  color: #b33b2f;
}

.section-heading {
  margin-bottom: 14px;
}

.section-heading h3,
.doc-section-title {
  margin: 0;
  color: #191919;
  font-size: 22px;
  font-weight: 800;
}

.report-card {
  border-radius: 14px;
  padding: 18px;
}

.card-header,
.card-header--compact {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-header h4,
.report-card h4,
.report-card h5 {
  margin: 0;
  color: #191919;
  font-size: 16px;
  font-weight: 800;
}

.report-card h5 {
  font-size: 14px;
}

.hint-text {
  color: #8b8579;
  font-size: 12px;
}

.growth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.75fr);
  gap: 18px;
}

.score-trend-card {
  min-height: 470px;
}

.dashboard-chart {
  width: 100%;
  min-width: 0;
}

.dashboard-chart--score {
  height: 292px;
  margin-top: 16px;
}

.score-trend-empty {
  display: grid;
  place-items: center;
  height: 292px;
  margin-top: 16px;
  border: 1px dashed #ded9ce;
  border-radius: 14px;
  background: #fbfaf7;
  color: #8b8579;
  font-size: 13px;
  font-weight: 700;
}

.dashboard-chart--distribution {
  height: 128px;
}

.dashboard-chart--scatter {
  height: 178px;
  margin-top: 10px;
}

.dashboard-chart--ability {
  height: 330px;
  margin-top: 16px;
  border: 1px solid #eee9df;
  border-radius: 12px;
  background: #fbfaf7;
}

.mini-tabs {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #ded9ce;
  border-radius: 999px;
  background: #fbfaf7;
}

.mini-tabs span {
  padding: 5px 10px;
  border-radius: 999px;
  color: #7a746a;
  font-size: 12px;
}

.mini-tabs .active {
  background: #e8f6ef;
  color: #047857;
  font-weight: 800;
}

.card-header p {
  margin: 6px 0 0;
  color: #6f6a60;
  font-size: 12px;
}

.score-trend-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.score-trend-summary span,
.goal-badges span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f2eee6;
  color: #6f6a60;
  font-size: 12px;
  font-weight: 750;
}

.score-trend-summary strong {
  margin-left: 4px;
  color: #047857;
}

.score-band-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 12px;
  color: #6f6a60;
  font-size: 11px;
  font-weight: 700;
}

.score-band-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.score-band-legend i {
  width: 12px;
  height: 8px;
  border: 1px solid rgba(31, 28, 21, 0.08);
  border-radius: 999px;
}

.growth-insight {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 11px 12px;
  border: 1px solid #eee9df;
  border-radius: 12px;
  background: #fbfaf7;
}

.growth-insight strong {
  flex: 0 0 auto;
  color: #047857;
  font-size: 12px;
  font-weight: 850;
}

.growth-insight span {
  color: #514c43;
  font-size: 12px;
  line-height: 1.55;
}

.trend-plot {
  position: relative;
  height: 250px;
  margin-top: 20px;
  padding-left: 38px;
}

.trend-grid {
  position: absolute;
  inset: 0 0 20px 0;
  display: grid;
  grid-template-rows: repeat(4, 1fr);
}

.trend-grid span {
  border-top: 1px dashed #e9e3d7;
  color: #9a9387;
  font-size: 11px;
}

.trend-plot svg {
  position: relative;
  width: 100%;
  height: 230px;
  overflow: visible;
}

.trend-line {
  fill: none;
  stroke: #059669;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 4;
}

.trend-plot circle {
  fill: #f7f5ef;
  stroke: #059669;
  stroke-width: 3;
}

.trend-badge {
  position: absolute;
  top: 62px;
  left: 45%;
  padding: 5px 10px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
}

.trend-tooltip {
  position: absolute;
  right: 58px;
  top: 128px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #e4dfd3;
  border-radius: 10px;
  background: #ffffff;
  color: #191919;
  font-size: 12px;
  box-shadow: 0 10px 24px rgba(31, 28, 21, 0.08);
}

.growth-side {
  min-width: 0;
}

.score-distribution-card {
  min-height: 470px;
}

.distribution-subtitle {
  margin-top: 16px !important;
}

.distribution-overview {
  display: grid;
  grid-template-columns: 136px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #eee9df;
  border-radius: 14px;
  background: linear-gradient(135deg, #fffefa 0%, #f6f1e8 100%);
}

.distribution-chart-shell {
  position: relative;
  min-height: 128px;
}

.distribution-center {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  place-items: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.distribution-center strong {
  color: #191919;
  font-size: 17px;
  line-height: 1;
}

.distribution-center span {
  margin-top: 4px;
  color: #047857;
  font-size: 12px;
  font-weight: 850;
}

.distribution-summary {
  display: grid;
  gap: 4px;
}

.distribution-summary span {
  color: #7a746a;
  font-size: 11px;
  font-weight: 800;
  text-transform: uppercase;
}

.distribution-summary strong {
  color: #191919;
  font-size: 30px;
  line-height: 1;
}

.distribution-summary em {
  max-width: 180px;
  color: #6f6a60;
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.score-distribution-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.score-distribution-list div {
  display: grid;
  grid-template-columns: auto 52px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  color: #7a746a;
  font-size: 11px;
}

.distribution-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
}

.score-distribution-list strong {
  color: #191919;
  font-size: 12px;
}

.score-distribution-list em {
  overflow: hidden;
  color: #6f6a60;
  font-style: normal;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-value {
  color: #7a746a;
  font-weight: 750;
}

.distribution-track {
  grid-column: 2 / -1;
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #eee9df;
}

.distribution-track i {
  display: block;
  height: 100%;
  min-width: 6px;
  border-radius: inherit;
}

.score-scatter-card {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eee9df;
}

.practice-progress-section .goal-card {
  grid-template-columns: minmax(240px, 0.42fr) minmax(0, 1fr) auto;
  align-items: center;
}

.practice-progress-section .goal-progress {
  min-width: 260px;
}

.goal-card {
  display: grid;
  gap: 14px;
}

.goal-card p {
  margin: 8px 0 0;
  color: #514c43;
  font-size: 22px;
  font-weight: 800;
}

.goal-progress,
.level-progress {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #eee9df;
}

.goal-progress span,
.level-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #059669;
}

.goal-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #7a746a;
  font-size: 12px;
}

.goal-meta strong {
  color: #191919;
}

.goal-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ability-summary {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 18px;
  margin-bottom: 18px;
}

.level-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin: 6px 0 14px;
}

.level-row strong {
  color: #191919;
  font-size: 48px;
  line-height: 1;
}

.level-row span {
  color: #514c43;
  font-size: 14px;
  font-weight: 800;
}

.level-card p,
.growth-card p,
.next-prompt-card p {
  margin: 10px 0 0;
  color: #6f6a60;
  font-size: 13px;
  line-height: 1.6;
}

.gap-text {
  color: #047857 !important;
  font-weight: 800;
}

.focus-tags,
.topic-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.focus-tags span,
.topic-cloud span {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f2eee6;
  color: #514c43;
  font-size: 12px;
  font-weight: 700;
}

.growth-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.growth-items span {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbfaf7;
}

.growth-items em {
  color: #514c43;
  font-style: normal;
  font-size: 13px;
}

.delta-up,
.metric-up {
  color: #047857 !important;
}

.delta-down,
.metric-down {
  color: #dc2626 !important;
}

.ability-trend-card {
  margin-bottom: 18px;
}

.curve-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #6f6a60;
  font-size: 12px;
}

.curve-legend span::before {
  content: "";
  display: inline-block;
  width: 18px;
  height: 3px;
  margin-right: 6px;
  border-radius: 999px;
  vertical-align: middle;
  background: #059669;
}

.curve-legend .grammar::before { background: #d97706; }
.curve-legend .vocabulary::before { background: #2563eb; }
.curve-legend .coherence::before { background: #7c3aed; }

.ability-chart {
  position: relative;
  height: 300px;
  margin-top: 18px;
  overflow: hidden;
  border: 1px solid #eee9df;
  border-radius: 12px;
  background: #fbfaf7;
}

.cefr-band {
  position: absolute;
  left: 0;
  width: 100%;
  display: flex;
  align-items: center;
  padding-left: 12px;
  color: rgba(31, 31, 31, 0.35);
  font-size: 12px;
  font-weight: 800;
}

.band-c2 { top: 0; height: 8%; background: rgba(124, 58, 237, 0.07); }
.band-c1 { top: 8%; height: 17%; background: rgba(217, 119, 6, 0.06); }
.band-b2 { top: 25%; height: 25%; background: rgba(5, 150, 105, 0.08); }
.band-b1 { top: 50%; height: 25%; background: rgba(37, 99, 235, 0.06); }
.band-a2 { top: 75%; height: 25%; background: rgba(107, 114, 128, 0.05); }

.ability-chart svg {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
}

.ability-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.ability-line.overall { stroke: #059669; stroke-width: 4; }
.ability-line.grammar { stroke: #d97706; }
.ability-line.vocabulary { stroke: #2563eb; }
.ability-line.coherence { stroke: #7c3aed; }

.diagnostics-grid,
.topic-layout {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.diagnostics-grid {
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
}

.error-bars,
.metric-list,
.genre-list {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.error-row,
.metric-row,
.genre-row {
  display: grid;
  gap: 8px;
}

.error-row div,
.metric-row div,
.genre-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.genre-row {
  grid-template-columns: 76px minmax(0, 1fr) 44px;
}

.error-row span,
.metric-row span,
.genre-row span {
  color: #514c43;
  font-size: 13px;
}

.error-row strong,
.metric-row strong,
.genre-row strong {
  color: #191919;
  font-size: 12px;
}

.error-row em,
.metric-row em,
.genre-row em {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #eee9df;
}

.error-row i,
.metric-row i,
.genre-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #059669;
}

.error-row i.danger { background: #dc2626; }
.error-row i.warning { background: #d97706; }
.error-row i.success { background: #059669; }

.topic-cloud .weight-5 {
  color: #047857;
  font-size: 19px;
}

.topic-cloud .weight-4 {
  color: #2563eb;
  font-size: 16px;
}

.topic-cloud .weight-3 {
  color: #7c3aed;
}

.next-prompt-card strong {
  display: block;
  margin-top: 6px;
  color: #191919;
  font-size: 20px;
}

.difficulty {
  display: inline-flex;
  margin-top: 14px;
  color: #7a746a;
  font-size: 12px;
  font-weight: 800;
}

.next-prompt-card button {
  display: block;
  margin-top: 18px;
  padding: 0;
  border: none;
  background: transparent;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.doc-section {
  margin-top: 34px;
}

.doc-section-header {
  align-items: flex-end;
}

.doc-filters {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.history-page-summary {
  color: #7a746a;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.doc-filters .search-bar {
  width: min(340px, 100%);
  margin-bottom: 0;
}

.search-input,
.sort-select,
.filter-pills {
  border-color: #ded9ce;
  background: rgba(255, 255, 255, 0.86);
}

.doc-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.doc-card {
  min-height: 260px;
  border-radius: 14px;
}

.doc-card:hover {
  border-color: #b8d9c6;
  box-shadow: 0 18px 38px rgba(31, 28, 21, 0.07);
  transform: translateY(-2px);
}

.doc-card-placeholder,
.doc-card-placeholder:hover {
  visibility: hidden;
  pointer-events: none;
  transform: none;
}

.doc-card-tags {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.doc-mode-tag {
  border-radius: 999px;
}

.doc-status-pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.doc-status-pill.scored { background: #e8f6ef; color: #047857; }
.doc-status-pill.pending { background: #f2eee6; color: #6f6a60; }
.doc-status-pill.review { background: #eef2ff; color: #4f46e5; }
.doc-status-pill.draft { background: #fff7ed; color: #c2410c; }

.doc-card-prompt {
  min-height: 38px;
}

.doc-card-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.doc-card-metrics span {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 8px;
  border-radius: 9px;
  background: #fbfaf7;
}

.doc-card-metrics em {
  color: #8b8579;
  font-style: normal;
  font-size: 11px;
}

.doc-card-metrics strong {
  overflow: hidden;
  color: #191919;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-next-step {
  margin: 0 0 12px;
  color: #6f6a60;
  font-size: 12px;
  line-height: 1.5;
}

/* ── Responsive ── */
@media (max-width: 1500px) {
  .writing-home-layout {
    --writing-home-panel-min-height: 780px;
  }

  .writing-home-layout .doc-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .dashboard-hero {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .hero-art {
    display: none;
  }
  .doc-grid,
  .topic-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .writing-home-layout {
    grid-template-columns: 1fr;
  }
  .growth-layout,
  .ability-summary,
  .diagnostics-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hub-page { padding: 24px 16px 44px; }
  .dashboard-hero { grid-template-columns: 1fr; }
  .hub-title { font-size: 34px; }
  .doc-grid,
  .writing-home-layout .doc-grid,
  .topic-layout { grid-template-columns: 1fr; }
  .writing-section-tabs {
    width: 100%;
    gap: 20px;
  }
  .growth-items { grid-template-columns: 1fr; }
  .card-header { flex-direction: column; }
  .doc-filters { justify-content: flex-start; }
  .doc-filters .search-bar { width: 100%; }
  .task-modal { padding: 26px 20px; }
  .task-option-grid--two,
  .task-field-grid,
  .past-prompt-layout,
  .past-prompt-toolbar {
    grid-template-columns: 1fr;
  }
  .task-word-row {
    grid-template-columns: 1fr;
  }
  .past-prompt-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .past-prompt-preview {
    position: static;
  }
  .writing-home-layout {
    --writing-home-panel-min-height: 0;
  }
}

@media (max-width: 560px) {
  .new-doc-btn--academy { width: 100%; justify-content: center; }
  .filter-pills { width: 100%; }
  .filter-pill { flex: 1; }
  .mode-grid { grid-template-columns: 1fr; }
  .doc-section-header { flex-direction: column; align-items: flex-start; }
  .task-modal-page { align-items: flex-start; }
  .task-modal-actions {
    flex-direction: column-reverse;
  }
  .task-btn {
    width: 100%;
  }
}
</style>












