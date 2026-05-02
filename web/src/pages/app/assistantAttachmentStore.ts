import type { AssistantAttachment, AssistantAttachmentMetadata } from './assistantMock.ts'

const DB_NAME = 'peai-assistant-attachments'
const DB_VERSION = 1
const STORE_NAME = 'attachments'

export interface StoredAssistantAttachment extends AssistantAttachmentMetadata {
  blob: Blob
  createdAt?: number
}

export interface AssistantAttachmentBlobStore {
  put(record: StoredAssistantAttachment): Promise<void>
  get(id: string): Promise<StoredAssistantAttachment | null>
  deleteMany(ids: string[]): Promise<void>
}

export function createAttachmentMetadata(attachment: AssistantAttachment): AssistantAttachmentMetadata {
  return {
    id: attachment.id,
    name: attachment.name,
    size: attachment.size,
    type: attachment.type,
    kind: attachment.kind,
  }
}

export function createAttachmentFile(
  metadata: AssistantAttachmentMetadata,
  blob: Blob,
): AssistantAttachment {
  const file = new File([blob], metadata.name, {
    type: metadata.type || blob.type || 'application/octet-stream',
  })
  return {
    ...metadata,
    size: metadata.size || file.size,
    type: metadata.type || file.type,
    file,
  }
}

export function createMemoryAssistantAttachmentBlobStore(): AssistantAttachmentBlobStore {
  const records = new Map<string, StoredAssistantAttachment>()
  return {
    async put(record) {
      records.set(record.id, { ...record, createdAt: record.createdAt ?? Date.now() })
    },
    async get(id) {
      return records.get(id) ?? null
    },
    async deleteMany(ids) {
      for (const id of ids) {
        records.delete(id)
      }
    },
  }
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!globalThis.indexedDB) {
      reject(new Error('IndexedDB is unavailable'))
      return
    }

    const request = globalThis.indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('Failed to open attachment store'))
  })
}

function runTransaction<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T> | void,
): Promise<T | void> {
  return openDb().then(
    (db) =>
      new Promise<T | void>((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, mode)
        const store = transaction.objectStore(STORE_NAME)
        const request = operation(store)
        transaction.oncomplete = () => {
          db.close()
          resolve(request ? request.result : undefined)
        }
        transaction.onerror = () => {
          db.close()
          reject(transaction.error ?? new Error('Attachment store transaction failed'))
        }
      }),
  )
}

export function createBrowserAssistantAttachmentBlobStore(): AssistantAttachmentBlobStore {
  return {
    async put(record) {
      await runTransaction('readwrite', (store) =>
        store.put({ ...record, createdAt: record.createdAt ?? Date.now() }),
      )
    },
    async get(id) {
      const record = await runTransaction<StoredAssistantAttachment | undefined>('readonly', (store) => store.get(id))
      return record ?? null
    },
    async deleteMany(ids) {
      if (ids.length === 0) return
      await openDb().then(
        (db) =>
          new Promise<void>((resolve, reject) => {
            const transaction = db.transaction(STORE_NAME, 'readwrite')
            const store = transaction.objectStore(STORE_NAME)
            for (const id of ids) {
              store.delete(id)
            }
            transaction.oncomplete = () => {
              db.close()
              resolve()
            }
            transaction.onerror = () => {
              db.close()
              reject(transaction.error ?? new Error('Attachment delete failed'))
            }
          }),
      )
    },
  }
}
