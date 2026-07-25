import type { Element, ElementContent, Parent, Root as HastRoot, RootContent, Text } from 'hast'
import type { Break, Html, Parent as MdastParent, Root as MdastRoot, Text as MdastText } from 'mdast'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import rehypeStringify from 'rehype-stringify'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import remarkRehype from 'remark-rehype'
import { unified } from 'unified'

const TABLE_SCROLL_LABEL = '可横向滚动的数据表格'
const RESPONSIVE_TABLE_MAX_COLUMNS = 3

export interface MarkdownSection {
  id: string
  title: string
  level: 2
}

export interface MarkdownDocument {
  html: string
  sections: MarkdownSection[]
}

export interface MarkdownRenderOptions {
  allowImages?: boolean
  allowHtmlBreaks?: boolean
  headingAnchors?: boolean
}

interface ResolvedMarkdownRenderOptions {
  allowImages: boolean
  allowHtmlBreaks: boolean
  headingAnchors: boolean
}

const markdownSanitizeSchema = {
  ...defaultSchema,
  clobberPrefix: '',
  tagNames: [
    ...(defaultSchema.tagNames ?? []),
    'button',
  ],
  attributes: {
    ...defaultSchema.attributes,
    '*': [
      ...(defaultSchema.attributes?.['*'] ?? []),
      'className',
    ],
    a: [
      ...(defaultSchema.attributes?.a ?? []),
      'target',
      'rel',
    ],
    button: [
      ...(defaultSchema.attributes?.button ?? []),
      'type',
      'className',
      'dataMarkdownCodeCopy',
      'ariaLabel',
      'ariaLive',
    ],
    div: [
      ...(defaultSchema.attributes?.div ?? []),
      'className',
      'tabIndex',
      'role',
      'ariaLabel',
    ],
    img: [
      ...(defaultSchema.attributes?.img ?? []),
      'className',
      'loading',
    ],
    h2: [
      ...(defaultSchema.attributes?.h2 ?? []),
      'id',
    ],
    input: [
      ...(defaultSchema.attributes?.input ?? []),
      'type',
      'checked',
      'disabled',
    ],
    table: [
      ...(defaultSchema.attributes?.table ?? []),
      'className',
    ],
    td: [
      ...(defaultSchema.attributes?.td ?? []),
      'dataLabel',
    ],
    th: [
      ...(defaultSchema.attributes?.th ?? []),
      'scope',
    ],
  },
  protocols: {
    ...defaultSchema.protocols,
    src: [
      ...(defaultSchema.protocols?.src ?? []),
      'blob',
      'data',
    ],
  },
}

function isElement(node: RootContent | ElementContent): node is Element {
  return node.type === 'element'
}

function isText(node: RootContent | ElementContent): node is Text {
  return node.type === 'text'
}

function appendClassName(element: Element, className: string) {
  const current = element.properties.className
  const classNames = Array.isArray(current)
    ? current.map(String)
    : []
  if (!classNames.includes(className)) classNames.push(className)
  element.properties.className = classNames
}

function readElementText(element: Element): string {
  return element.children
    .map((child) => {
      if (isText(child)) return child.value
      if (isElement(child)) return readElementText(child)
      return ''
    })
    .join('')
    .trim()
}

function replaceSafeBreakHtml(options: Pick<ResolvedMarkdownRenderOptions, 'allowHtmlBreaks'>) {
  return (tree: MdastRoot) => {
    const visit = (parent: MdastParent) => {
      parent.children = parent.children.map((child) => {
        if (child.type === 'html') {
          const rawHtml = (child as Html).value
          if (options.allowHtmlBreaks && /^<br\s*\/?\s*>$/i.test(rawHtml.trim())) {
            return { type: 'break' } satisfies Break
          }
          return { type: 'text', value: rawHtml } satisfies MdastText
        }
        if ('children' in child && Array.isArray(child.children)) {
          visit(child as MdastParent)
        }
        return child
      })
    }
    visit(tree)
  }
}

function isSafeImageSource(source: string): boolean {
  const trimmed = source.trim()
  if (/^https?:\/\//i.test(trimmed)) return true
  if (/^blob:/i.test(trimmed)) return true
  if (/^\/(?!\/)/.test(trimmed)) return true
  if (/^\.{0,2}\//.test(trimmed)) return true
  return /^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=\s]+$/i.test(trimmed)
}

function createTextNode(value: string): Text {
  return { type: 'text', value }
}

function createCodeBlock(element: Element): Element | null {
  if (element.tagName !== 'pre' || element.children.length !== 1) return null
  const codeElement = element.children[0]
  if (!isElement(codeElement) || codeElement.tagName !== 'code') return null

  const languageClass = Array.isArray(codeElement.properties.className)
    ? codeElement.properties.className.map(String).find((value) => value.startsWith('language-'))
    : undefined
  const language = languageClass?.slice('language-'.length).trim() || 'text'
  if (!languageClass) appendClassName(codeElement, 'language-text')
  const shouldWrap = ['text', 'markdown', 'md'].includes(language.toLowerCase())

  return {
    type: 'element',
    tagName: 'div',
    properties: {
      className: [
        'markdown-code-block',
        ...(shouldWrap ? ['markdown-code-block--wrap'] : []),
      ],
    },
    children: [
      {
        type: 'element',
        tagName: 'div',
        properties: { className: ['markdown-code-header'] },
        children: [
          {
            type: 'element',
            tagName: 'span',
            properties: {},
            children: [createTextNode(language)],
          },
          {
            type: 'element',
            tagName: 'button',
            properties: {
              type: 'button',
              className: ['markdown-code-copy'],
              dataMarkdownCodeCopy: '',
              ariaLabel: '复制文本',
              ariaLive: 'polite',
            },
            children: [createTextNode('复制')],
          },
        ],
      },
      element,
    ],
  }
}

function getTableHeaders(table: Element): string[] {
  const thead = table.children.find((child) => isElement(child) && child.tagName === 'thead')
  if (!thead || !isElement(thead)) return []
  const row = thead.children.find((child) => isElement(child) && child.tagName === 'tr')
  if (!row || !isElement(row)) return []
  return row.children
    .filter((child): child is Element => isElement(child) && child.tagName === 'th')
    .map(readElementText)
}

function makeTableResponsive(table: Element): boolean {
  const headers = getTableHeaders(table)
  const isSimpleTable = headers.length > 0 && headers.length <= RESPONSIVE_TABLE_MAX_COLUMNS

  const thead = table.children.find((child) => isElement(child) && child.tagName === 'thead')
  if (thead && isElement(thead)) {
    for (const row of thead.children) {
      if (!isElement(row) || row.tagName !== 'tr') continue
      for (const cell of row.children) {
        if (isElement(cell) && cell.tagName === 'th') cell.properties.scope = 'col'
      }
    }
  }

  if (!isSimpleTable) return false
  appendClassName(table, 'markdown-table--responsive-cards')

  const tbody = table.children.find((child) => isElement(child) && child.tagName === 'tbody')
  if (!tbody || !isElement(tbody)) return true
  for (const row of tbody.children) {
    if (!isElement(row) || row.tagName !== 'tr') continue
    let columnIndex = 0
    for (const cell of row.children) {
      if (!isElement(cell) || cell.tagName !== 'td') continue
      cell.properties.dataLabel = headers[columnIndex] ?? ''
      columnIndex += 1
    }
  }
  return true
}

function createTableRegion(table: Element): Element {
  const usesCards = makeTableResponsive(table)
  return {
    type: 'element',
    tagName: 'div',
    properties: {
      className: [
        'markdown-table-scroll',
        ...(usesCards ? ['markdown-table-scroll--cards'] : []),
      ],
      tabIndex: 0,
      role: 'region',
      ariaLabel: TABLE_SCROLL_LABEL,
    },
    children: [table],
  }
}

function enhanceMarkdownHtml(configuration: {
  options: ResolvedMarkdownRenderOptions
  sections: MarkdownSection[]
}) {
  return (tree: HastRoot) => {
    const visit = (parent: Parent) => {
      parent.children = parent.children.map((child) => {
        if (!isElement(child as RootContent)) return child
        const element = child as Element

        if (element.tagName === 'h2' && configuration.options.headingAnchors) {
          const id = `markdown-section-${configuration.sections.length + 1}`
          element.properties.id = id
          configuration.sections.push({
            id,
            title: readElementText(element) || '未命名章节',
            level: 2,
          })
        }

        if (element.tagName === 'img') {
          if (!configuration.options.allowImages) {
            return createTextNode(String(element.properties.alt ?? '').trim() || '图片')
          }
          const source = String(element.properties.src ?? '')
          if (!isSafeImageSource(source)) {
            return createTextNode(String(element.properties.alt ?? '图片'))
          }
          appendClassName(element, 'markdown-image')
          element.properties.loading = 'lazy'
        }

        if (element.tagName === 'a') {
          const href = String(element.properties.href ?? '')
          if (/^https?:\/\//i.test(href)) {
            element.properties.target = '_blank'
            element.properties.rel = ['noopener', 'noreferrer']
          }
        }

        if (element.tagName === 'table') return createTableRegion(element)

        const codeBlock = createCodeBlock(element)
        if (codeBlock) return codeBlock

        visit(element)
        return element
      })
    }
    visit(tree)
  }
}

function resolveMarkdownRenderOptions(options: MarkdownRenderOptions): ResolvedMarkdownRenderOptions {
  return {
    allowImages: options.allowImages ?? true,
    allowHtmlBreaks: options.allowHtmlBreaks ?? true,
    headingAnchors: options.headingAnchors ?? false,
  }
}

export function renderMarkdownDocument(
  markdown: string,
  options: MarkdownRenderOptions = {},
): MarkdownDocument {
  const resolvedOptions = resolveMarkdownRenderOptions(options)
  const sections: MarkdownSection[] = []
  const html = unified()
    .use(remarkParse)
    .use(remarkGfm)
    .use(replaceSafeBreakHtml, resolvedOptions)
    .use(remarkRehype)
    .use(enhanceMarkdownHtml, { options: resolvedOptions, sections })
    .use(rehypeSanitize, markdownSanitizeSchema)
    .use(rehypeStringify)
    .processSync(markdown)
    .toString()
    .replace(/&#x3C;/g, '&lt;')
    .replace(/&lt;([^<>]*?)>/g, '&lt;$1&gt;')

  return { html, sections }
}

export function renderAssistantMarkdown(markdown: string): string {
  return renderMarkdownDocument(markdown).html
}

async function writeTextToClipboard(text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  if (typeof document === 'undefined') {
    throw new Error('Clipboard API is not available.')
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function showCopiedState(button: HTMLButtonElement) {
  const previousText = button.textContent || '复制'
  button.textContent = '已复制'
  button.classList.add('markdown-code-copy--copied')
  window.setTimeout(() => {
    if (!button.isConnected) return
    button.textContent = previousText
    button.classList.remove('markdown-code-copy--copied')
  }, 1200)
}

export async function copyMarkdownCodeFromClick(event: MouseEvent): Promise<boolean> {
  if (typeof Element === 'undefined') return false
  const target = event.target instanceof Element ? event.target : null
  const button = target?.closest<HTMLButtonElement>('[data-markdown-code-copy]')
  if (!button) return false

  event.preventDefault()
  event.stopPropagation()

  const code = button.closest('.markdown-code-block')?.querySelector('code')?.textContent ?? ''
  if (!code) return true

  try {
    await writeTextToClipboard(code)
    showCopiedState(button)
  } catch {
    button.textContent = '复制失败'
    window.setTimeout(() => {
      if (!button.isConnected) return
      button.textContent = '复制'
    }, 1200)
  }
  return true
}
