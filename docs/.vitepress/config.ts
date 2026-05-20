import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Personal English AI 文档',
  description: 'Personal English AI 项目文档中心',
  cleanUrls: true,
  ignoreDeadLinks: false,
  markdown: {
    config(md) {
      const defaultFence = md.renderer.rules.fence!

      md.renderer.rules.fence = (tokens, idx, options, env, self) => {
        const token = tokens[idx]
        const language = token.info.trim().split(/\s+/)[0]

        if (language === 'mermaid') {
          return `<pre class="mermaid">${md.utils.escapeHtml(token.content)}</pre>`
        }

        return defaultFence(tokens, idx, options, env, self)
      }
    }
  },
  themeConfig: {
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            noResultsText: '没有找到相关结果',
            resetButtonTitle: '清除搜索',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    },
    nav: [
      { text: '首页', link: '/' },
      { text: '产品', link: '/product/' },
      { text: '架构', link: '/architecture/' },
      { text: '接口', link: '/api/' },
      { text: '数据', link: '/data/' },
      { text: 'AI', link: '/ai/' },
      { text: 'Admin', link: '/admin/' },
      { text: 'Agent', link: '/agent/' },
      { text: '运行手册', link: '/runbooks/' },
      { text: '测试', link: '/testing/' },
      { text: 'ADR', link: '/adr/' }
    ],
    sidebar: {
      '/product/': [
        {
          text: '产品',
          items: [
            { text: '产品概览', link: '/product/' },
            { text: '路线图', link: '/product/roadmap' },
            { text: '邀请系统', link: '/product/referral-system' },
            { text: '订阅', link: '/product/subscription/' },
            { text: 'CEFR', link: '/product/cefr/' }
          ]
        }
      ],
      '/architecture/': [
        {
          text: '架构',
          items: [
            { text: '架构总览', link: '/architecture/' },
            { text: '鉴权', link: '/architecture/auth' },
            { text: '助手会话管理', link: '/architecture/assistant-conversation-management' },
            { text: '仓库结构规范', link: '/architecture/repository-structure' },
            { text: '词典集成', link: '/architecture/dictionary-oxford' },
            { text: '写作任务元数据', link: '/architecture/writing-task-metadata' }
          ]
        }
      ],
      '/api/': [
        {
          text: '接口',
          items: [
            { text: '接口总览', link: '/api/' },
            { text: 'Agent Debug', link: '/api/agent-debug' },
            { text: 'Admin 用户', link: '/api/admin-users' },
            { text: 'Admin 订阅与额度', link: '/api/admin-subscription' },
            { text: 'AI Command', link: '/api/ai-command' },
            { text: 'Document', link: '/api/document' },
            { text: '考试写作题目', link: '/api/writing-ai-exam-prompt' }
          ]
        }
      ],
      '/data/': [
        {
          text: '数据',
          items: [
            { text: '数据总览', link: '/data/' },
            { text: '评分持久化', link: '/data/scoring-persistence-schema' }
          ]
        }
      ],
      '/ai/': [
        {
          text: 'AI',
          items: [
            { text: 'AI 总览', link: '/ai/' },
            { text: '评分方法', link: '/ai/scoring-methodology' },
            { text: '评分规则', link: '/ai/scoring-rules/' },
            { text: '语法检查', link: '/ai/grammar-check' },
            { text: '润色', link: '/ai/polish-feature' },
            { text: '学习助手 Agent 编排', link: '/agent/学习助手Agent编排架构' },
            { text: '助手输出格式', link: '/ai/assistant-output-format' },
            { text: 'Prompt 管理', link: '/ai/prompt-management' },
            { text: 'OpenAI Agents 请求架构', link: '/ai/openai-agents-request-architecture' }
          ]
        }
      ],
      '/admin/': [
        {
          text: 'Admin',
          items: [
            { text: 'Admin 后台体验改造', link: '/admin/' },
            { text: '当前管理员端产品说明', link: '/admin/current-admin-product-design' },
            { text: '用户中心设计方案', link: '/admin/user-center-design' },
            { text: 'BI 分析后台方案', link: '/admin/bi-analytics-design' },
            { text: '数据地图设计方案', link: '/admin/data-catalog-design' }
          ]
        }
      ],
      '/agent/': [
        {
          text: 'Agent',
          items: [
            { text: 'Agent 设计总览', link: '/agent/' },
            { text: 'Agent 产品现状与路线图', link: '/agent/Agent产品现状与路线图' },
            { text: '学习助手 Agent 编排架构', link: '/agent/学习助手Agent编排架构' },
            { text: 'Agent 能力清单', link: '/agent/Agent能力清单' },
            { text: '路由 Agent 设计', link: '/agent/路由Agent设计' },
            { text: 'Agent 可观测性与调试中心', link: '/agent/Agent可观测性与调试中心' },
            { text: 'AI 调试端设计', link: '/agent/AI调试端设计' },
            { text: '对话词句采集清洗方案', link: '/agent/数据清洗/对话词句采集清洗方案' },
            { text: 'OpenAI Agents SDK 学习笔记', link: '/agent/OpenAI Agents SDK中文学习笔记' },
            {
              text: 'Agent Builder 学习资料',
              collapsed: false,
              items: [
                { text: 'Agent Builder 总览', link: '/agent/agent-builder/' },
                { text: 'Node reference 学习笔记', link: '/agent/agent-builder/node-reference' }
              ]
            }
          ]
        }
      ],
      '/runbooks/': [
        {
          text: '运行手册',
          items: [
            { text: '运行手册总览', link: '/runbooks/' },
            { text: '本地开发', link: '/runbooks/local-dev' },
            { text: '启动环境检查', link: '/runbooks/startup-env-checklist' },
            { text: '本地脚本', link: '/runbooks/local-scripts' },
            { text: '仓库目录卫生治理', link: '/runbooks/repo-hygiene' },
            { text: '环境变量', link: '/runbooks/environment-variables' },
            { text: '部署', link: '/runbooks/deploy' }
          ]
        }
      ],
      '/testing/': [
        {
          text: '测试',
          items: [
            { text: '测试总览', link: '/testing/' },
            { text: '冒烟与回归矩阵', link: '/testing/testing-matrix-and-smoke' }
          ]
        }
      ],
      '/adr/': [
        {
          text: 'ADR',
          items: [
            { text: '决策记录说明', link: '/adr/' },
            { text: 'ADR 模板', link: '/adr/template' },
            { text: '0001：使用 VitePress', link: '/adr/0001-use-vitepress-for-docs' }
          ]
        }
      ]
    },
    outline: {
      label: '本页目录'
    },
    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },
    lastUpdated: {
      text: '最后更新'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/LiMing0016/personalenglishai' }
    ]
  }
})
