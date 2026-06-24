/// <reference types="vite/client" />

declare module 'pdfjs-dist/build/pdf.mjs' {
  export * from 'pdfjs-dist/types/src/pdf'
}

declare module 'pdfjs-dist/web/pdf_viewer.mjs' {
  export class TextLayerBuilder {
    div: HTMLElement
    constructor(options: {
      pdfPage: unknown
      highlighter?: unknown
      accessibilityManager?: unknown
      enablePermissions?: boolean
      onAppend?: (textLayerDiv: HTMLElement) => void
      abortSignal?: AbortSignal
    })
    render(params: {
      viewport: unknown
      images?: unknown
      textContentParams?: Record<string, unknown>
    }): Promise<void>
    cancel(): void
    hide(): void
    show(): void
  }
}
