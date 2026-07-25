import assert from 'node:assert/strict'
import test from 'node:test'

import {
  useVocabularyPronunciation,
  type VocabularyAudioLike,
  type VocabularySpeechAdapter,
} from '../src/composables/useVocabularyPronunciation'

class FakeAudio implements VocabularyAudioLike {
  currentTime = 0
  onplaying: (() => void) | null = null
  onended: (() => void) | null = null
  onerror: (() => void) | null = null
  pauseCount = 0

  constructor(private readonly outcome: 'resolve' | 'reject' = 'resolve') {}

  async play() {
    if (this.outcome === 'reject') throw new Error('audio unavailable')
    this.onplaying?.()
  }

  pause() {
    this.pauseCount += 1
  }
}

function speechAdapter(supported = true) {
  const calls: Array<{ term: string, language: string }> = []
  let cancelCount = 0
  const adapter: VocabularySpeechAdapter = {
    isSupported: () => supported,
    cancel: () => { cancelCount += 1 },
    speak: ({ term, language }) => { calls.push({ term, language }) },
  }
  return { adapter, calls, cancelCount: () => cancelCount }
}

test('real audio is preferred over device speech', async () => {
  const audio = new FakeAudio()
  const speech = speechAdapter()
  const playback = useVocabularyPronunciation({
    createAudio: () => audio,
    speech: speech.adapter,
  })

  assert.equal(await playback.play({ term: 'receive', language: 'en-GB', audioUrl: '/receive.mp3' }), 'audio')
  assert.equal(playback.state.value, 'playing')
  assert.equal(playback.activeLanguage.value, 'en-GB')
  assert.deepEqual(speech.calls, [])

  audio.onended?.()
  assert.equal(playback.state.value, 'idle')
})

test('missing or rejected audio falls back to device speech', async () => {
  const speech = speechAdapter()
  const withoutAudio = useVocabularyPronunciation({ speech: speech.adapter })
  assert.equal(await withoutAudio.play({ term: 'receive', language: 'en-US', audioUrl: null }), 'speech')
  assert.deepEqual(speech.calls, [{ term: 'receive', language: 'en-US' }])
  assert.equal(withoutAudio.message.value, '正在使用设备语音')

  const rejectedSpeech = speechAdapter()
  const rejectedAudio = useVocabularyPronunciation({
    createAudio: () => new FakeAudio('reject'),
    speech: rejectedSpeech.adapter,
  })
  assert.equal(await rejectedAudio.play({ term: 'package', language: 'en-GB', audioUrl: '/missing.mp3' }), 'speech')
  assert.deepEqual(rejectedSpeech.calls, [{ term: 'package', language: 'en-GB' }])
})

test('unsupported playback reports a stable failure', async () => {
  const speech = speechAdapter(false)
  const playback = useVocabularyPronunciation({ speech: speech.adapter })

  assert.equal(await playback.play({ term: 'receive', language: 'en-GB', audioUrl: null }), 'failed')
  assert.equal(playback.state.value, 'failed')
  assert.equal(playback.message.value, '暂时无法播放发音')
})

test('starting and stopping playback releases the previous source', async () => {
  const first = new FakeAudio()
  const second = new FakeAudio()
  const audios = [first, second]
  const speech = speechAdapter()
  const playback = useVocabularyPronunciation({
    createAudio: () => audios.shift()!,
    speech: speech.adapter,
  })

  await playback.play({ term: 'receive', language: 'en-GB', audioUrl: '/receive.mp3' })
  await playback.play({ term: 'package', language: 'en-US', audioUrl: '/package.mp3' })
  assert.equal(first.pauseCount, 1)
  assert.equal(first.onended, null)

  playback.stop()
  assert.equal(second.pauseCount, 1)
  assert.equal(playback.state.value, 'idle')
  assert.equal(playback.activeLanguage.value, null)
  assert.ok(speech.cancelCount() >= 1)
})

test('a stale audio completion cannot overwrite the latest playback state', async () => {
  let resolveFirst!: () => void
  const first = new FakeAudio()
  first.play = () => new Promise<void>((resolve) => { resolveFirst = resolve })
  const second = new FakeAudio()
  const audios = [first, second]
  const playback = useVocabularyPronunciation({
    createAudio: () => audios.shift()!,
    speech: speechAdapter().adapter,
  })

  const oldRequest = playback.play({ term: 'old', language: 'en-GB', audioUrl: '/old.mp3' })
  assert.equal(playback.state.value, 'loading')
  assert.equal(await playback.play({ term: 'new', language: 'en-US', audioUrl: '/new.mp3' }), 'audio')
  resolveFirst()
  assert.equal(await oldRequest, 'failed')
  assert.equal(playback.state.value, 'playing')
  assert.equal(playback.activeLanguage.value, 'en-US')
})
