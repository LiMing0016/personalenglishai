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
  source: 'local' | 'oxford' | string
  phonetics: DictionaryPhonetic[]
  entries: DictionaryEntry[]
  favorite?: boolean
  lookupCount?: number
}

export interface DictionaryWordStateResponse {
  word: string
  language?: DictionaryLanguage | string
  favorite: boolean
  lookupCount: number
}

export interface DictionaryFavoriteItem {
  word: string
  language?: DictionaryLanguage | string
  source?: string
  favorite: boolean
  lookupCount: number
  favoritedAt?: string
  lastLookupAt?: string
  phonetic?: string
  partOfSpeech?: string
  meaning?: string
}

export interface DictionaryFavoriteListResponse {
  items: DictionaryFavoriteItem[]
  total: number
  page: number
  size: number
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

export function setDictionaryFavorite(
  word: string,
  favorite: boolean,
  language: DictionaryLanguage | string = 'en-gb',
): Promise<DictionaryWordStateResponse> {
  return http
    .post<{ code: string; message: string; data?: DictionaryWordStateResponse }>(
      `/dictionary/words/${encodeURIComponent(word)}/favorite`,
      { favorite, language },
    )
    .then((res) => {
      if (!res.data.data) {
        throw new Error(res.data.message || 'dictionary favorite failed')
      }
      return res.data.data
    })
}

export function listDictionaryFavorites(params: {
  keyword?: string
  page?: number
  size?: number
} = {}): Promise<DictionaryFavoriteListResponse> {
  return http
    .get<{ code: string; message: string; data?: DictionaryFavoriteListResponse }>('/dictionary/favorites', {
      params,
    })
    .then((res) => {
      if (!res.data.data) {
        throw new Error(res.data.message || 'dictionary favorites failed')
      }
      return res.data.data
    })
}
