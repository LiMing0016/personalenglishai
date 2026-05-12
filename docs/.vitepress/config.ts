import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Personal English AI 文档',
  description: 'Personal English AI 项目文档中心',
  cleanUrls: true,
  ignoreDeadLinks: false,
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
            { text: '学习助手', link: '/ai/learning-assistant-architecture' },
            { text: '助手输出格式', link: '/ai/assistant-output-format' },
            { text: 'OpenAI Agents 请求架构', link: '/ai/openai-agents-request-architecture' }
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
