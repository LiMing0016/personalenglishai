import type { AnalyticsDataset } from '../types/index.ts'

export const mockAnalyticsDatasets: Record<string, AnalyticsDataset> = {
  overview: {
    pageKey: 'overview',
    source: 'mixed',
    generatedAt: '2026-05-16 12:00:00',
    notice: '订阅核心指标可接真实接口，写作、AI 和漏斗指标暂用 Mock 或待实现状态。',
    kpis: [
      { label: '新增用户', value: '1,240', delta: '+12.4%', tone: 'good', source: 'mock' },
      { label: '活跃用户', value: '8,920', delta: '+5.1%', tone: 'good', source: 'mock' },
      { label: '订阅新增', value: '126', delta: '+8.6%', tone: 'good', source: 'mixed' },
      { label: '作文提交', value: '3,482', delta: '待接入', tone: 'warning', source: 'todo' },
      { label: 'AI Tokens', value: '12.8M', delta: '待接入', tone: 'warning', source: 'todo' },
      { label: '失败率', value: '1.8%', delta: 'Mock', tone: 'neutral', source: 'mock' },
    ],
    charts: [
      {
        title: '用户增长趋势',
        subtitle: '新增与活跃用户，首版 Mock',
        source: 'mock',
        points: [
          { label: '05-10', value: 188 },
          { label: '05-11', value: 206 },
          { label: '05-12', value: 241 },
          { label: '05-13', value: 228 },
          { label: '05-14', value: 276 },
          { label: '05-15', value: 301 },
          { label: '05-16', value: 326 },
        ],
      },
      {
        title: '订阅转化趋势',
        subtitle: '优先复用订阅 daily-stats',
        source: 'mixed',
        points: [
          { label: '05-10', value: 24 },
          { label: '05-11', value: 28 },
          { label: '05-12', value: 31 },
          { label: '05-13', value: 27 },
          { label: '05-14', value: 36 },
          { label: '05-15', value: 42 },
          { label: '05-16', value: 39 },
        ],
      },
      {
        title: '写作提交与评分完成率',
        subtitle: '等待写作聚合接口',
        source: 'todo',
        points: [
          { label: 'Free', value: 1420, secondaryValue: 91 },
          { label: 'Basic', value: 920, secondaryValue: 95 },
          { label: 'Pro', value: 780, secondaryValue: 96 },
          { label: 'Premium', value: 362, secondaryValue: 97 },
        ],
      },
      {
        title: 'AI token 与失败率',
        subtitle: '等待模型用量聚合接口',
        source: 'todo',
        points: [
          { label: '评分', value: 5200, secondaryValue: 1.2 },
          { label: '语法', value: 3400, secondaryValue: 1.6 },
          { label: '润色', value: 2100, secondaryValue: 2.1 },
          { label: 'Agent', value: 1800, secondaryValue: 2.8 },
        ],
      },
    ],
    tables: [
      {
        title: '异常提醒',
        source: 'mock',
        columns: ['事项', '状态', '建议'],
        rows: [
          ['评分失败率升高', 'Mock', '接入任务聚合后自动判断'],
          ['AI token 异常波动', '待实现', '接入模型用量后启用'],
          ['订阅超额用户', '部分接入', '复用订阅列表筛选'],
        ],
      },
    ],
  },
  users: {
    pageKey: 'users',
    source: 'todo',
    generatedAt: '2026-05-16 12:00:00',
    notice: '用户增长、活跃和留存需要新增聚合接口，当前只展示目标结构。',
    kpis: [
      { label: '新增用户', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '活跃用户', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '次日留存', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '7 日留存', value: '待实现', source: 'todo', tone: 'warning' },
    ],
    charts: [
      { title: '用户增长趋势', subtitle: '等待用户聚合接口', source: 'todo', points: [] },
      { title: '留存矩阵', subtitle: '等待事件或快照数据', source: 'todo', points: [] },
    ],
    tables: [
      { title: '用户分群排行', source: 'todo', columns: ['分群', '用户数', '状态'], rows: [['新用户', '-', '待实现']] },
    ],
  },
  subscriptions: {
    pageKey: 'subscriptions',
    source: 'mixed',
    generatedAt: '2026-05-16 12:00:00',
    notice: '订阅概览、每日趋势和额度规则已有接口，部分漏斗和排行仍待聚合。',
    kpis: [
      { label: '订阅用户', value: '0', source: 'mixed' },
      { label: '新增订阅', value: '0', source: 'mixed' },
      { label: '订阅转化率', value: '0%', source: 'mixed' },
      { label: '超额用户', value: '0', source: 'mixed' },
    ],
    charts: [
      { title: '每日新增订阅', subtitle: '来自 daily-stats 或 Mock fallback', source: 'mixed', points: [] },
      { title: '套餐分布', subtitle: '来自 subscriptions/overview', source: 'mixed', points: [] },
    ],
    tables: [
      { title: '订阅分层', source: 'mixed', columns: ['分层', '用户数', '占比'], rows: [] },
    ],
  },
  writing: {
    pageKey: 'writing',
    source: 'todo',
    generatedAt: '2026-05-16 12:00:00',
    notice: '写作提交、评分完成率和失败任务聚合接口待实现。',
    kpis: [
      { label: '作文提交', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '评分完成', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '评分失败', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '平均分', value: '待实现', source: 'todo', tone: 'warning' },
    ],
    charts: [
      { title: '提交趋势', subtitle: '等待写作聚合接口', source: 'todo', points: [] },
      { title: '分数分布', subtitle: '等待评分聚合接口', source: 'todo', points: [] },
    ],
    tables: [
      { title: '失败任务排行', source: 'todo', columns: ['任务', '失败数', '状态'], rows: [['评分任务', '-', '待实现']] },
    ],
  },
  'ai-usage': {
    pageKey: 'ai-usage',
    source: 'todo',
    generatedAt: '2026-05-16 12:00:00',
    notice: '模型、workflow、token、失败率和成本估算等待模型用量聚合接口。',
    kpis: [
      { label: 'Total Tokens', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '请求数', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '失败率', value: '待实现', source: 'todo', tone: 'warning' },
      { label: '成本估算', value: '待实现', source: 'todo', tone: 'warning' },
    ],
    charts: [
      { title: 'Token 趋势', subtitle: '等待模型用量聚合接口', source: 'todo', points: [] },
      { title: '模型分布', subtitle: '等待 provider/model 聚合接口', source: 'todo', points: [] },
    ],
    tables: [
      { title: '失败原因排行', source: 'todo', columns: ['原因', '次数', '状态'], rows: [['Rate limit', '-', '待实现']] },
    ],
  },
  funnel: {
    pageKey: 'funnel',
    source: 'mock',
    generatedAt: '2026-05-16 12:00:00',
    notice: '转化漏斗先用 Mock 展示目标结构，后续接事件或快照聚合。',
    kpis: [
      { label: '注册用户', value: '10,000', source: 'mock' },
      { label: '完善学段', value: '7,800', source: 'mock' },
      { label: '首篇作文', value: '4,260', source: 'mock' },
      { label: '订阅', value: '520', source: 'mock' },
    ],
    charts: [
      {
        title: '注册到订阅漏斗',
        subtitle: 'Mock 转化路径',
        source: 'mock',
        points: [
          { label: '注册', value: 10000 },
          { label: '完善学段', value: 7800 },
          { label: '首篇作文', value: 4260 },
          { label: '完成评分', value: 3920 },
          { label: '订阅', value: 520 },
        ],
      },
    ],
    tables: [
      {
        title: '分渠道漏斗对比',
        source: 'mock',
        columns: ['渠道', '注册', '订阅'],
        rows: [
          ['Web', '6,800', '390'],
          ['活动兑换', '2,100', '92'],
          ['管理员导入', '1,100', '38'],
        ],
      },
    ],
  },
}
