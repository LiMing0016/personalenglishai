import { http } from './http'

export type DictionaryLanguage = 'en-gb' | 'en-us'

export interface DictionaryPhonetic {
  text?: string
  audioUrl?: string
}

export interface DictionaryEntry {
  partOfSpeech?: string
  definitions: string[]
  examples: string[]
}

export interface DictionaryLookupResponse {
  word: string
  language: DictionaryLanguage | string
  source: 'oxford'
  phonetics: DictionaryPhonetic[]
  entries: DictionaryEntry[]
}

interface DictionaryApiResponse {
  code: string
  message: string
  data?: DictionaryLookupResponse
}

export function lookupDictionary(
  word: string,
  language: DictionaryLanguage = 'en-gb',
): Promise<DictionaryLookupResponse> {
  return http
    .get<DictionaryApiResponse>('/dictionary/lookup', {
      params: { word, language },
      timeout: 15000,
    })
    .then((res) => {
      if (!res.data.data) {
        throw new Error(res.data.message || 'dictionary lookup failed')
      }
      return res.data.data
    })
}
