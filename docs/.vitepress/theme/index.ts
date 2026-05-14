import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import { h, nextTick, onMounted, watch } from 'vue'
import { useRoute } from 'vitepress'
import mermaid from 'mermaid'

import './styles.css'

let initialized = false
let mermaidRenderId = 0

function initializeMermaid() {
  if (initialized || typeof window === 'undefined') return

  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    flowchart: {
      htmlLabels: true
    },
    theme: document.documentElement.classList.contains('dark') ? 'dark' : 'default'
  })
  initialized = true
}

async function renderMermaid() {
  if (typeof window === 'undefined') return

  initializeMermaid()
  const nodes = Array.from(
    document.querySelectorAll<HTMLElement>('pre.mermaid:not([data-mermaid-rendered="true"])')
  )
  if (nodes.length === 0) return

  for (const node of nodes) {
    node.dataset.mermaidRendered = 'true'
    const source = node.textContent ?? ''
    const id = `vitepress-mermaid-${Date.now()}-${mermaidRenderId++}`

    try {
      const { svg, bindFunctions } = await mermaid.render(id, source)
      const wrapper = document.createElement('div')
      wrapper.className = 'mermaid-rendered'
      wrapper.innerHTML = svg
      node.replaceWith(wrapper)
      bindFunctions?.(wrapper)
    } catch (error) {
      console.error('[vitepress-mermaid] render failed', error)
      delete node.dataset.mermaidRendered
    }
  }
}

const MermaidLayout = {
  setup() {
    const route = useRoute()

    async function renderAfterContentUpdate() {
      await nextTick()
      window.requestAnimationFrame(() => {
        void renderMermaid()
      })
    }

    onMounted(() => {
      void renderAfterContentUpdate()
    })

    watch(
      () => route.path,
      () => {
        void renderAfterContentUpdate()
      }
    )

    return () => h(DefaultTheme.Layout)
  }
}

const theme: Theme = {
  extends: DefaultTheme,
  Layout: MermaidLayout
}

export default theme
