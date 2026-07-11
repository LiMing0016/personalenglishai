import type { VocabularyCaptureResponse } from '@/api/vocabulary'

export function isVocabularyCaptureComplete(response: VocabularyCaptureResponse): boolean {
  return response.items.every((item) => item.action !== 'rejected')
}
