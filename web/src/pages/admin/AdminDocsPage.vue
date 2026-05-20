<template>
  <section class="admin-section admin-docs-page">
    <div class="admin-card admin-docs-hero">
      <div>
        <p class="admin-eyebrow">Documentation</p>
        <h1>项目文档中心</h1>
        <p class="admin-docs-hero__copy">
          从后台快速打开产品、架构、接口、AI、Agent 和运行手册文档。文档站独立运行，默认地址为
          <code>{{ docsBaseUrl }}</code>。
        </p>
      </div>
      <a class="admin-btn" :href="docsHomeUrl" target="_blank" rel="noreferrer">打开文档首页</a>
    </div>

    <div class="admin-docs-grid" aria-label="文档快捷入口">
      <a
        v-for="link in docsLinks"
        :key="link.path"
        class="admin-card admin-docs-link"
        :href="toDocsUrl(link.path)"
        target="_blank"
        rel="noreferrer"
      >
        <span class="admin-docs-link__label">{{ link.label }}</span>
        <span class="admin-docs-link__description">{{ link.description }}</span>
      </a>
    </div>
  </section>
</template>

<script setup lang="ts">
const DEFAULT_DOCS_BASE_URL = 'http://127.0.0.1:5174'

const configuredDocsBaseUrl = (import.meta.env.VITE_DOCS_BASE_URL as string | undefined)?.trim()
const docsBaseUrl = normalizeBaseUrl(configuredDocsBaseUrl || DEFAULT_DOCS_BASE_URL)
const docsHomeUrl = toDocsUrl('')

const docsLinks = [
  { label: '文档首页', path: '', description: '项目文档总入口和主导航。' },
  { label: 'Agent 设计', path: 'agent/', description: 'Agent 产品现状、能力清单、路由和观测方案。' },
  { label: '产品路线图', path: 'product/roadmap', description: '产品阶段规划、范围和优先级。' },
  { label: '系统架构', path: 'architecture/', description: '系统模块、边界和关键架构说明。' },
  { label: '接口文档', path: 'api/', description: '后端 API 契约、字段和权限要求。' },
  { label: '运行手册', path: 'runbooks/', description: '本地开发、环境变量、部署和排障。' },
]

function normalizeBaseUrl(value: string) {
  return value.endsWith('/') ? value : `${value}/`
}

function toDocsUrl(path: string) {
  return new URL(path, docsBaseUrl).toString()
}
</script>

<style scoped>
.admin-docs-page {
  display: grid;
  gap: 20px;
}

.admin-docs-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.admin-docs-hero h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
}

.admin-docs-hero__copy {
  max-width: 760px;
  margin: 10px 0 0;
  color: #526070;
  line-height: 1.7;
}

.admin-docs-hero__copy code {
  font-size: 13px;
}

.admin-docs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.admin-docs-link {
  display: grid;
  gap: 8px;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.admin-docs-link:hover,
.admin-docs-link:focus-visible {
  border-color: #9db7d6;
  box-shadow: 0 10px 24px rgba(23, 40, 58, 0.08);
  outline: none;
  transform: translateY(-1px);
}

.admin-docs-link__label {
  font-size: 16px;
  font-weight: 700;
  color: #1f2a37;
}

.admin-docs-link__description {
  color: #667085;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .admin-docs-hero {
    display: grid;
  }
}
</style>
