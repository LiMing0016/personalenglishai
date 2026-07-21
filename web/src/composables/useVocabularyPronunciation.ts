import { getCurrentScope, onScopeDispose, ref } from 'vue'

export type VocabularyPronunciationState = 'idle' | 'loading' | 'playing' | 'failed'
export type VocabularyPronunciationResult = 'audio' | 'speech' | 'failed'

export interface VocabularyPronunciationRequest {
  term: string
  language: string
  audioUrl?: string | null
}

export interface VocabularyAudioLike {
  currentTime: number
  onplaying: (() => void) | null
  onended: (() => void) | null
  onerror: (() => void) | null
  play: () => Promise<void>
  pause: () => void
}

export interface VocabularySpeechAdapter {
  isSupported: () => boolean
  cancel: () => void
  speak: (request: VocabularyPronunciationRequest, handlers: {
    onEnd: () => void
    onError: () => void
  }) => void
}

interface VocabularyPronunciationOptions {
  createAudio?: (url: string) => VocabularyAudioLike
  speech?: VocabularySpeechAdapter
}

function browserSpeechAdapter(): VocabularySpeechAdapter {
  return {
    isSupported: () => (
      typeof window !== 'undefined'
      && 'speechSynthesis' in window
      && 'SpeechSynthesisUtterance' in window
    ),
    cancel: () => {
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel()
      }
    },
    speak: (request, handlers) => {
      const utterance = new SpeechSynthesisUtterance(request.term)
      utterance.lang = request.language || 'en-GB'
      utterance.rate = 0.9
      utterance.onend = handlers.onEnd
      utterance.onerror = handlers.onError
      window.speechSynthesis.speak(utterance)
    },
  }
}

export function useVocabularyPronunciation(options: VocabularyPronunciationOptions = {}) {
  const createAudio = options.createAudio ?? ((url: string) => new Audio(url))
  const speech = options.speech ?? browserSpeechAdapter()
  const state = ref<VocabularyPronunciationState>('idle')
  const activeLanguage = ref<string | null>(null)
  const message = ref('')
  let activeAudio: VocabularyAudioLike | null = null
  let activeRequestId = 0

  function releaseAudio() {
    if (!activeAudio) return
    activeAudio.pause()
    activeAudio.onplaying = null
    activeAudio.onended = null
    activeAudio.onerror = null
    try {
      activeAudio.currentTime = 0
    } catch {
      // Some remote streams do not allow seeking before metadata is loaded.
    }
    activeAudio = null
  }

  function releaseSources() {
    releaseAudio()
    speech.cancel()
  }

  function finish(requestId: number) {
    if (requestId !== activeRequestId) return
    state.value = 'idle'
    message.value = ''
  }

  function fail(requestId: number) {
    if (requestId !== activeRequestId) return
    state.value = 'failed'
    message.value = '暂时无法播放发音'
  }

  async function playSpeech(
    request: VocabularyPronunciationRequest,
    requestId: number,
  ): Promise<VocabularyPronunciationResult> {
    if (requestId !== activeRequestId || !speech.isSupported()) {
      fail(requestId)
      return 'failed'
    }

    releaseAudio()
    speech.cancel()
    state.value = 'playing'
    message.value = '正在使用设备语音'
    try {
      speech.speak(request, {
        onEnd: () => finish(requestId),
        onError: () => fail(requestId),
      })
      return 'speech'
    } catch {
      fail(requestId)
      return 'failed'
    }
  }

  async function play(request: VocabularyPronunciationRequest): Promise<VocabularyPronunciationResult> {
    const term = request.term.trim()
    const language = request.language || 'en-GB'
    const normalizedRequest = { ...request, term, language }
    const requestId = ++activeRequestId
    releaseSources()
    activeLanguage.value = language
    state.value = 'loading'
    message.value = '正在加载发音'

    if (!term) {
      fail(requestId)
      return 'failed'
    }

    if (!request.audioUrl) {
      return playSpeech(normalizedRequest, requestId)
    }

    let fallbackStarted = false
    const fallback = () => {
      if (fallbackStarted || requestId !== activeRequestId) return Promise.resolve<VocabularyPronunciationResult>('failed')
      fallbackStarted = true
      return playSpeech(normalizedRequest, requestId)
    }

    try {
      const audio = createAudio(request.audioUrl)
      activeAudio = audio
      audio.onplaying = () => {
        if (requestId !== activeRequestId) return
        state.value = 'playing'
        message.value = '正在播放发音'
      }
      audio.onended = () => finish(requestId)
      audio.onerror = () => { void fallback() }
      await audio.play()
      if (requestId !== activeRequestId) return 'failed'
      state.value = 'playing'
      message.value = '正在播放发音'
      return 'audio'
    } catch {
      return fallback()
    }
  }

  function stop() {
    activeRequestId += 1
    releaseSources()
    state.value = 'idle'
    activeLanguage.value = null
    message.value = ''
  }

  if (getCurrentScope()) onScopeDispose(stop)

  return {
    state,
    activeLanguage,
    message,
    play,
    stop,
  }
}
