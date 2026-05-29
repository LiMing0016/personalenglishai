import type { AdminDataCatalogGraph, AdminDataCatalogGraphEdge } from '@/api/admin'

const MODULE_COLORS = [
  { fill: '#effaf4', stroke: '#0f9f6e' },
  { fill: '#eff6ff', stroke: '#2563eb' },
  { fill: '#fff7ed', stroke: '#ea580c' },
  { fill: '#faf5ff', stroke: '#9333ea' },
  { fill: '#fef2f2', stroke: '#dc2626' },
  { fill: '#ecfeff', stroke: '#0891b2' },
]

export function buildAdminDataCatalogMermaid(graph: AdminDataCatalogGraph, options?: { detailBasePath?: string }) {
  const detailBasePath = options?.detailBasePath ?? '/admin/data-catalog'
  if (!graph.nodes.length) {
    return ''
  }

  const lines: string[] = ['flowchart LR']
  const moduleClassMap = new Map<string, string>()

  graph.nodes.forEach((node) => {
    const nodeId = graphNodeId(node.tableName)
    const label = `${escapeLabel(node.title || node.tableName)}<br/><span style='font-size:11px'>${escapeLabel(node.tableName)}</span>`
    lines.push(`  ${nodeId}["${label}"]`)
    lines.push(`  click ${nodeId} "${detailBasePath}/${encodeURIComponent(node.tableName)}" "查看表详情"`)
    if (node.module && !moduleClassMap.has(node.module)) {
      moduleClassMap.set(node.module, `moduleClass${moduleClassMap.size + 1}`)
    }
    const classes = [
      node.module ? moduleClassMap.get(node.module) : 'moduleClassFallback',
      `sensitivity${normalizeSensitivity(node.sensitivity)}`,
      node.configured ? 'configuredTable' : 'discoveredTable',
    ].filter(Boolean)
    lines.push(`  class ${nodeId} ${classes.join(',')}`)
  })

  graph.edges.forEach((edge) => {
    const connector = edge.relationType === 'logical' ? '-.->' : '-->'
    lines.push(
      `  ${graphNodeId(edge.sourceTable)} ${connector}|"${escapeLabel(formatRelationshipLabel(edge))}"| ${graphNodeId(edge.targetTable)}`,
    )
  })

  Array.from(moduleClassMap.entries()).forEach(([, className], index) => {
    const palette = MODULE_COLORS[index % MODULE_COLORS.length]
    lines.push(`  classDef ${className} fill:${palette.fill},stroke:${palette.stroke},stroke-width:1.5px,color:#0f172a`)
  })
  lines.push('  classDef moduleClassFallback fill:#f8fafc,stroke:#94a3b8,stroke-width:1.2px,color:#0f172a')
  lines.push('  classDef sensitivityLow stroke-width:1.2px')
  lines.push('  classDef sensitivityMedium stroke-width:1.8px')
  lines.push('  classDef sensitivityHigh stroke-width:2.2px,stroke:#be123c')
  lines.push('  classDef sensitivityCritical stroke-width:2.4px,stroke:#7f1d1d')
  lines.push('  classDef configuredTable stroke-dasharray:0')
  lines.push('  classDef discoveredTable stroke-dasharray:6 4')

  return lines.join('\n')
}

export function formatRelationshipLabel(edge: AdminDataCatalogGraphEdge) {
  const source = edge.sourceColumn || '?'
  const target = edge.targetColumn || '?'
  const prefix = edge.relationType === 'logical' ? '逻辑' : '物理'
  return `${prefix}: ${source} -> ${target}`
}

function graphNodeId(tableName: string) {
  return `tbl_${tableName.replace(/[^A-Za-z0-9_]/g, '_')}`
}

function normalizeSensitivity(value: string | null | undefined) {
  const normalized = String(value || 'low').toLowerCase()
  if (normalized === 'critical') return 'Critical'
  if (normalized === 'high') return 'High'
  if (normalized === 'medium') return 'Medium'
  return 'Low'
}

function escapeLabel(value: string) {
  return value.replace(/"/g, '\\"')
}
