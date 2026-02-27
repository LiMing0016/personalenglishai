/**
 * 前端路由与鉴权守卫
 * - 公共路由（meta.public=true）：放行
 * - 业务路由：无 token 或 token 已过期时跳转 /login
 * - 登录成功后支持回跳到原始目标页面（redirect query）
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { clearToken, getToken, isTokenExpired } from '@/utils/token'

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
    path: '/app',
    name: 'AppHome',
    component: () => import('@/views/AppHome.vue'),
    meta: { public: false },
  },
  {
    path: '/app/writing',
    name: 'Writing',
    component: () => import('@/pages/app/WritingPage.vue'),
    meta: { public: false },
  },
  {
    path: '/app/ai-test',
    name: 'AiCommandTest',
    component: () => import('@/pages/app/AiCommandTestPage.vue'),
    meta: { public: false },
  },
  {
    path: '/app/writing/evaluate',
    redirect: () => ({ path: '/app/writing' }),
  },
  {
    path: '/me',
    name: 'Me',
    component: () => import('@/views/MeView.vue'),
    meta: { public: false },
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

router.beforeEach((to, from, next) => {
  const token = getToken()
  const expired = isTokenExpired(token)
  const hasToken = !!token && !expired
  const isPublic = Boolean((to.meta as { public?: boolean }).public)

  if (token && expired) {
    clearToken()
    if (DEBUG_ROUTER) {
      console.info('[router] guard: token expired, cleared local token', { to: to.fullPath, from: from.fullPath })
    }
  }

  if (!isPublic && !hasToken) {
    if (DEBUG_ROUTER) {
      console.info('[router] guard: no valid token, redirect to /login', { to: to.fullPath, from: from.fullPath })
    }
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

  if (DEBUG_ROUTER) {
    console.info('[router] guard: pass', { to: to.fullPath, hasToken })
  }
  next()
})

export default router
