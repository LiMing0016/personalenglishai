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
      { text: '学习', link: '/learning/' },
      { text: '产品', link: '/product/' },
      { text: '进度', link: '/progress/' },
      { text: '架构', link: '/architecture/' },
      { text: '接口', link: '/api/' },
      { text: 'iPadOS 联调', link: '/ios-integration/README' },
      { text: '数据', link: '/data/' },
      { text: 'AI', link: '/ai/' },
      { text: 'Admin', link: '/admin/' },
      { text: 'Agent', link: '/agent/' },
      { text: '运行手册', link: '/runbooks/' },
      { text: '测试', link: '/testing/' },
      { text: 'ADR', link: '/adr/' }
    ],
    sidebar: {
      '/learning/': [
        {
          text: '学习',
          items: [
            { text: '学习资料总览', link: '/learning/' },
            { text: 'Markdown 常用语法', link: '/learning/Markdown常用语法' },
            { text: 'Jupyter 验证不同 Schema', link: '/learning/Jupyter验证不同Schema' }
          ]
        }
      ],
      '/product/': [
        {
          text: '产品',
          items: [
            { text: '产品概览', link: '/product/' },
            { text: '路线图', link: '/product/roadmap' },
            { text: '邀请系统', link: '/product/referral-system' },
            { text: '订阅', link: '/product/subscription/' },
            { text: 'CEFR', link: '/product/cefr/' },
            {
              text: '翻译页面',
              collapsed: false,
              items: [
                { text: '翻译页面总览', link: '/product/翻译页面/' },
                { text: '作文资产学习价值提取预览', link: '/product/翻译页面/作文资产DeepSeek学习价值提取预览方案' }
              ]
            }
          ]
        }
      ],
      '/progress/': [
        {
          text: '进度',
          items: [
            { text: '进度总览', link: '/progress/' },
            { text: '学习材料高质量解析', link: '/progress/学习材料高质量解析进度' }
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
            { text: '单词沉淀架构', link: '/architecture/vocabulary-deposition' },
            { text: '文档知识提取管线', link: '/architecture/文档知识提取管线设计' },
            { text: 'PaddleOCR 高质量文档解析', link: '/architecture/PaddleOCR高质量文档解析方案' },
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
            { text: '用户 AI 用量活动', link: '/api/user-ai-usage' },
            { text: 'AI Command', link: '/api/ai-command' },
            { text: 'Document', link: '/api/document' },
            { text: 'Learning Notes', link: '/api/learning-notes' },
            { text: '单词沉淀', link: '/api/vocabulary' },
            { text: '考试写作题目', link: '/api/writing-ai-exam-prompt' }
          ]
        }
      ],
      '/ios-integration/': [
        {
          text: 'iPadOS 联调',
          items: [
            { text: '联调总览', link: '/ios-integration/README' },
            { text: 'AI 对话助手 iOS 协同', link: '/ios-integration/assistant-ios-collaboration-v1' },
            { text: 'AI 助手 API 契约', link: '/ios-integration/ai-assistant-api-contract' },
            { text: '认证 API 契约', link: '/ios-integration/auth-api-contract' },
            { text: '本地开发与 Docker', link: '/ios-integration/local-dev-and-docker' },
            { text: '联调验收清单', link: '/ios-integration/integration-checklist' },
            { text: '排障指南', link: '/ios-integration/troubleshooting' },
            { text: '变更记录', link: '/ios-integration/changelog' }
          ]
        }
      ],
      '/data/': [
        {
          text: '数据',
          items: [
            { text: '数据总览', link: '/data/' },
            { text: '评分持久化', link: '/data/scoring-persistence-schema' },
            { text: 'Learning Note', link: '/data/learning-note-schema' },
            { text: '订阅与 AI 用量', link: '/data/subscription-and-ai-usage' }
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
            { text: '主题化单词卡 Prompt', link: '/ai/vocabulary-theme-prompts' },
            { text: '单词图片识别', link: '/ai/vocabulary-image-recognition' },
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
            { text: '数据地图设计方案', link: '/admin/data-catalog-design' },
            { text: '数据清洗中心词典探查', link: '/admin/data-cleaning-center' }
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
            { text: '写作教练 Schema 设计', link: '/agent/写作教练Schema设计' },
            { text: '路由 Agent 设计', link: '/agent/路由Agent设计' },
            { text: 'Agent 可观测性与调试中心', link: '/agent/Agent可观测性与调试中心' },
            { text: 'AI 调试端设计', link: '/agent/AI调试端设计' },
            { text: '对话词句采集清洗方案', link: '/agent/数据清洗/对话词句采集清洗方案' },
            { text: 'OpenAI Agents SDK 学习笔记', link: '/agent/OpenAI Agents SDK中文学习笔记' },
            { text: 'Function Call 学习笔记', link: '/agent/FunctionCall学习笔记' },
            { text: 'Structured Output 学习笔记', link: '/agent/StructuredOutput学习笔记' },
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
