/**
 * 前端路由与鉴权守卫
 * - 公共路由（meta.public=true）：放行
 * - 业务路由：无 token 或 token 已过期时跳转 /login
 * - 已登录但无学段 → 跳转 /app/stage-setup
 * - 登录成功后支持回跳到原始目标页面（redirect query）
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { clearToken, getToken, setToken, isTokenExpired } from '@/utils/token'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { stageCache, clearStageCache } from '@/stores/stageCache'

const BUSINESS_HOME = '/app'

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

  // ── 业务路由（嵌套在 AppLayout 下） ──
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
      },      {
        path: 'writing',
        name: 'WritingDocList',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/mode',
        name: 'WritingModeSelect',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/exam-setup',
        name: 'WritingExamSetup',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'writing/editor',
        name: 'WritingEditor',
        component: () => import('@/pages/app/WritingPage.vue'),
      },
      {
        path: 'vocabulary',
        name: 'Vocabulary',
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

  // ── 兼容旧路径 ──
  {
    path: '/app/writing/evaluate',
    redirect: () => ({ path: '/app/writing' }),
  },
  {
    path: '/me',
    redirect: () => ({ path: '/app/me' }),
  },
]

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

router.beforeEach(async (to, from, next) => {
  const token = getToken()
  const expired = isTokenExpired(token)
  let hasToken = !!token && !expired
  const isPublic = Boolean((to.meta as { public?: boolean }).public)

  // Access token 过期时尝试用 refresh cookie 静默续签
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
        if (DEBUG_ROUTER) {
          console.info('[router] guard: refresh succeeded')
        }
      } else {
        clearToken()
        clearStageCache()
      }
    } catch {
      clearToken()
      clearStageCache()
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
    next(buildLoginRedirectTarget(to.fullPath))
    return
  }

  if (isPublic && (to.path === '/login' || to.path === '/login-form' || to.path === '/register') && hasToken) {
    if (DEBUG_ROUTER) {
      console.info('[router] guard: has token on auth page, redirect to', BUSINESS_HOME, { to: to.fullPath })
    }
    next(BUSINESS_HOME)
    return
  }

  // ── Stage check for authenticated non-public routes ──
  if (!isPublic && hasToken && to.path !== '/app/stage-setup') {
    // Fetch profile once and cache it
    if (stageCache.value === null) {
      try {
        const res = await userApi.getMyProfile()
        stageCache.value = res.data?.studyStage || ''
      } catch {
        // On failure, skip the check to avoid blocking navigation
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

