/**
 * 前端路由与鉴权守卫
 * - 公共路由（meta.public=true）：放行
 * - 业务路由：无 token 或 token 已过期时跳转 /login
 * - 管理员路由：先校验管理员身份，失败回退 /app
 * - 已登录但无学段 → 跳转 /app/stage-setup（管理员路由跳过）
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import type { AxiosError } from 'axios'
import { clearToken, getToken, setToken, isTokenExpired } from '@/utils/token'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { clearAdminMeCache, getAdminMe } from '@/api/admin'
import { showToast } from '@/utils/toast'
import { stageCache, clearStageCache } from '@/stores/stageCache'

const BUSINESS_HOME = '/app'
const ADMIN_HOME = '/admin/dashboard'
const OPS_AGENT_HOME = '/ops/agent/runs'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/pages/Home.vue'),
    meta: { public: true },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginFormView.vue'),
    meta: { public: true },
  },
  {
    path: '/login-form',
    name: 'LoginForm',
    redirect: (to) => ({ path: '/login', query: to.query }),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/check-email',
    name: 'CheckEmail',
    component: () => import('@/pages/CheckEmail.vue'),
    meta: { public: true },
  },
  {
    path: '/assistant/share/:shareToken',
    name: 'AssistantShare',
    component: () => import('@/pages/AssistantSharePage.vue'),
    meta: { public: true },
  },
  {
    path: '/verify-email',
    name: 'VerifyEmail',
    component: () => import('@/pages/VerifyEmail.vue'),
    meta: { public: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPasswordView.vue'),
    meta: { public: true },
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/ResetPasswordView.vue'),
    meta: { public: true },
  },
  {
    path: '/app',
    component: () => import('@/layouts/AppLayout.vue'),
    meta: { public: false },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
      },
      {
        path: 'stage-setup',
        name: 'StageSetup',
        component: () => import('@/pages/app/StageSetupPage.vue'),
        meta: { skipStageCheck: true },
      },
      {
        path: 'writing',
        name: 'WritingDocList',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/dashboard',
        name: 'WritingDashboard',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/mode',
        name: 'WritingModeSelect',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/setup',
        alias: 'writing/exam-setup',
        name: 'WritingExamSetup',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/past-prompts',
        name: 'WritingPastPromptSelect',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/editor',
        name: 'WritingEditor',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'translation',
        name: 'Translation',
        component: () => import('@/pages/app/TranslationPage.vue'),
      },
      {
        path: 'translation/workspace/:id',
        name: 'TranslationWorkspace',
        component: () => import('@/pages/app/TranslationWorkspacePage.vue'),
        meta: { immersive: true },
      },
      {
        path: 'tools/document-parse',
        name: 'DocumentParseToolbox',
        component: () => import('@/pages/app/DocumentParseToolboxPage.vue'),
        meta: { immersive: true },
      },
      {
        path: 'assistant',
        name: 'LearningAssistant',
        component: () => import('@/pages/app/AssistantPage.vue'),
        meta: { immersive: true },
      },
      {
        path: 'vocabulary',
        name: 'Vocabulary',
        component: () => import('@/views/VocabularyView.vue'),
      },
      {
        path: 'vocabulary/cards/:word',
        name: 'VocabularyWordCard',
        component: () => import('@/views/VocabularyView.vue'),
      },
      {
        path: 'vocabulary/card/:cardUid',
        name: 'vocabulary-card',
        component: () => import('@/views/VocabularyView.vue'),
      },
      {
        path: 'listening',
        name: 'Listening',
        component: () => import('@/views/ListeningView.vue'),
      },
      {
        path: 'speaking',
        name: 'Speaking',
        component: () => import('@/views/SpeakingView.vue'),
      },
      {
        path: 'me',
        name: 'PersonalCenter',
        component: () => import('@/pages/app/PersonalCenterPage.vue'),
        meta: { immersive: true },
      },
      {
        path: 'profile',
        name: 'Profile',
        redirect: () => ({ path: '/app/me', query: { tab: 'settings' } }),
      },
      {
        path: 'ai-test',
        name: 'AiCommandTest',
        component: () => import('@/pages/app/AiCommandTestPage.vue'),
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { public: false, requiresAdmin: true, skipStageCheck: true },
    children: [
      {
        path: '',
        redirect: ADMIN_HOME,
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/modules/admin/dashboard/pages/AdminDashboardPage.vue'),
      },
      {
        path: 'docs',
        name: 'AdminDocs',
        component: () => import('@/pages/admin/AdminDocsPage.vue'),
      },
      {
        path: 'analytics',
        name: 'AdminAnalyticsOverview',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsOverviewPage.vue'),
      },
      {
        path: 'analytics/users',
        name: 'AdminAnalyticsUsers',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsUsersPage.vue'),
      },
      {
        path: 'analytics/subscriptions',
        name: 'AdminAnalyticsSubscriptions',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsSubscriptionsPage.vue'),
      },
      {
        path: 'analytics/writing',
        name: 'AdminAnalyticsWriting',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsWritingPage.vue'),
      },
      {
        path: 'analytics/ai-usage',
        name: 'AdminAnalyticsAiUsage',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsAiUsagePage.vue'),
      },
      {
        path: 'analytics/funnel',
        name: 'AdminAnalyticsFunnel',
        component: () => import('@/modules/admin/analytics/pages/AdminAnalyticsFunnelPage.vue'),
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/pages/admin/AdminUsersPage.vue'),
      },
      {
        path: 'users/:id',
        name: 'AdminUserDetail',
        component: () => import('@/pages/admin/AdminUserDetailPage.vue'),
      },
      {
        path: 'subscriptions',
        name: 'AdminSubscriptions',
        component: () => import('@/pages/admin/AdminSubscriptionsPage.vue'),
      },
      {
        path: 'subscription/redeem-codes',
        name: 'AdminRedeemCodes',
        component: () => import('@/pages/admin/AdminRedeemCodesPage.vue'),
      },
      {
        path: 'subscription/quota-ledger',
        name: 'AdminQuotaLedger',
        component: () => import('@/pages/admin/AdminQuotaLedgerPage.vue'),
      },
      {
        path: 'essays',
        name: 'AdminEssays',
        component: () => import('@/pages/admin/AdminEssaysPage.vue'),
      },
      {
        path: 'essays/:id',
        name: 'AdminEssayDetail',
        component: () => import('@/pages/admin/AdminEssayDetailPage.vue'),
      },
      {
        path: 'prompts',
        name: 'AdminPrompts',
        component: () => import('@/pages/admin/AdminPromptsPage.vue'),
      },
      {
        path: 'prompts/new',
        name: 'AdminPromptCreate',
        component: () => import('@/pages/admin/AdminPromptDetailPage.vue'),
      },
      {
        path: 'prompts/:id',
        name: 'AdminPromptDetail',
        component: () => import('@/pages/admin/AdminPromptDetailPage.vue'),
      },
      {
        path: 'rubrics',
        name: 'AdminRubrics',
        component: () => import('@/pages/admin/AdminRubricsPage.vue'),
      },
      {
        path: 'rubrics/:id',
        name: 'AdminRubricDetail',
        component: () => import('@/pages/admin/AdminRubricDetailPage.vue'),
      },
      {
        path: 'prompt-assets',
        name: 'AdminPromptAssets',
        component: () => import('@/pages/admin/AdminPromptAssetsPage.vue'),
      },
      {
        path: 'materials',
        name: 'AdminMaterials',
        component: () => import('@/pages/admin/AdminMaterialsPage.vue'),
      },
      {
        path: 'scoring-config',
        name: 'AdminScoringConfig',
        component: () => import('@/pages/admin/AdminScoringConfigPage.vue'),
      },
      {
        path: 'agent-debug/runs',
        name: 'AdminAgentDebugRuns',
        component: () => import('@/pages/ops/agent/OpsAgentRunsPage.vue'),
      },
      {
        path: 'agent-debug/runs/:id',
        name: 'AdminAgentDebugRunDetail',
        component: () => import('@/pages/ops/agent/OpsAgentRunDetailPage.vue'),
      },
      {
        path: 'model-usage',
        name: 'AdminModelUsage',
        component: () => import('@/pages/admin/AdminModelUsagePage.vue'),
      },
      {
        path: 'audit-logs',
        name: 'AdminAuditLogs',
        component: () => import('@/pages/admin/AdminAuditLogsPage.vue'),
      },
      {
        path: 'data-catalog',
        name: 'AdminDataCatalog',
        component: () => import('@/pages/admin/AdminDataCatalogPage.vue'),
      },
      {
        path: 'data-catalog/:tableName',
        name: 'AdminDataCatalogDetail',
        component: () => import('@/pages/admin/AdminDataCatalogDetailPage.vue'),
      },
      {
        path: 'data-cleaning',
        name: 'AdminDataCleaning',
        component: () => import('@/pages/admin/AdminDataCleaningPage.vue'),
      },
      {
        path: 'admin-users',
        name: 'AdminAdminUsers',
        component: () => import('@/pages/admin/AdminAdminUsersPage.vue'),
      },
    ],
  },
  {
    path: '/agent/agent-observability-center',
    redirect: OPS_AGENT_HOME,
  },
  {
    path: '/ops/agent',
    component: () => import('@/layouts/OpsAgentLayout.vue'),
    meta: { public: false, requiresAdmin: true, skipStageCheck: true },
    children: [
      {
        path: '',
        redirect: OPS_AGENT_HOME,
      },
      {
        path: 'runs',
        name: 'OpsAgentRuns',
        component: () => import('@/pages/ops/agent/OpsAgentRunsPage.vue'),
      },
      {
        path: 'runs/:id',
        name: 'OpsAgentRunDetail',
        component: () => import('@/pages/ops/agent/OpsAgentRunDetailPage.vue'),
      },
      {
        path: 'prompts',
        name: 'OpsAgentPrompts',
        component: () => import('@/pages/ops/agent/OpsAgentPromptsPage.vue'),
      },
      {
        path: 'eval-cases',
        name: 'OpsAgentEvalCases',
        component: () => import('@/pages/ops/agent/OpsAgentEvalCasesPage.vue'),
      },
    ],
  },
  {
    path: '/app/writing/evaluate',
    redirect: () => ({ path: '/app/writing' }),
  },
  {
    path: '/me',
    redirect: () => ({ path: '/app/me' }),
  },
]

if (import.meta.env.DEV) {
  routes.push({
    path: '/dev/admin-login',
    name: 'AdminDevLoginBridge',
    component: () => import('@/pages/dev/AdminDevLoginBridge.vue'),
    meta: { public: true },
  })
}

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const DEBUG_ROUTER = import.meta.env.DEV

function buildLoginRedirectTarget(fullPath: string) {
  if (!fullPath || fullPath === '/login') {
    return { path: '/login' }
  }
  return { path: '/login', query: { redirect: fullPath } }
}

function getStatus(err: unknown) {
  return (err as AxiosError | undefined)?.response?.status
}

router.beforeEach(async (to, from, next) => {
  const token = getToken()
  const expired = isTokenExpired(token)
  let hasToken = !!token && !expired
  const meta = to.meta as { public?: boolean; skipStageCheck?: boolean; requiresAdmin?: boolean }
  const isPublic = Boolean(meta.public)

  if (token && expired) {
    if (DEBUG_ROUTER) {
      console.info('[router] guard: token expired, attempting refresh', { to: to.fullPath })
    }
    try {
      const res = await authApi.refresh()
      const fallbackToken = (res as unknown as { token?: string }).token
      const newToken = res.data?.token ?? fallbackToken
      if (newToken) {
        setToken(newToken)
        hasToken = true
        clearAdminMeCache()
        if (DEBUG_ROUTER) {
          console.info('[router] guard: refresh succeeded')
        }
      } else {
        clearToken()
        clearStageCache()
        clearAdminMeCache()
      }
    } catch {
      clearToken()
      clearStageCache()
      clearAdminMeCache()
      if (DEBUG_ROUTER) {
        console.info('[router] guard: refresh failed, cleared token')
      }
    }
  }

  if (!isPublic && !hasToken) {
    if (DEBUG_ROUTER) {
      console.info('[router] guard: no valid token, redirect to /login', { to: to.fullPath, from: from.fullPath })
    }
    clearStageCache()
    clearAdminMeCache()
    next(buildLoginRedirectTarget(to.fullPath))
    return
  }

  if (isPublic && (to.path === '/login' || to.path === '/login-form' || to.path === '/register') && hasToken) {
    let home = BUSINESS_HOME
    try {
      await getAdminMe()
      home = ADMIN_HOME
    } catch {
      home = BUSINESS_HOME
    }
    if (DEBUG_ROUTER) {
      console.info('[router] guard: has token on auth page, redirect to', home, { to: to.fullPath })
    }
    next(home)
    return
  }

  if (meta.requiresAdmin) {
    try {
      await getAdminMe()
    } catch (err) {
      const status = getStatus(err)
      if (status === 401 || status === 403) {
        if (status === 403) {
          showToast('当前账号不是管理员，请切换管理员账号登录', 'error')
        }
        clearToken()
        clearStageCache()
        clearAdminMeCache()
        next(buildLoginRedirectTarget(to.fullPath))
        return
      }
      if (status === 404) {
        showToast('管理员接口未部署，请确认后端已更新', 'error')
      }
      next(BUSINESS_HOME)
      return
    }
  }

  if (!isPublic && hasToken && !meta.skipStageCheck && to.path !== '/app/stage-setup') {
    if (stageCache.value === null) {
      try {
        const res = await userApi.getMyProfile()
        stageCache.value = res.data?.studyStage || ''
      } catch {
        stageCache.value = '__error__'
      }
    }

    if (stageCache.value === '') {
      if (DEBUG_ROUTER) {
        console.info('[router] guard: no studyStage, redirect to /app/stage-setup', { to: to.fullPath })
      }
      next({ path: '/app/stage-setup', query: { redirect: to.fullPath } })
      return
    }
  }

  if (DEBUG_ROUTER) {
    console.info('[router] guard: pass', { to: to.fullPath, hasToken })
  }
  next()
})

export default router


