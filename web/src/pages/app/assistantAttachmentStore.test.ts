import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createAttachmentFile,
  createAttachmentMetadata,
  createMemoryAssistantAttachmentBlobStore,
} from './assistantAttachmentStore.ts'
import type { AssistantAttachment } from './assistantMock.ts'

function imageAttachment(): AssistantAttachment {
  return {
    id: 'attachment-1',
    name: 'screenshot.png',
    size: 5,
    type: 'image/png',
    kind: 'image',
    file: new File(['image'], 'screenshot.png', { type: 'image/png' }),
  }
}

test('createAttachmentMetadata strips file content', () => {
  const metadata = createAttachmentMetadata(imageAttachment())

  assert.deepEqual(metadata, {
    id: 'attachment-1',
    name: 'screenshot.png',
    size: 5,
    type: 'image/png',
    kind: 'image',
  })
  assert.equal('file' in metadata, false)
})

test('memory attachment blob store saves loads and deletes blobs', async () => {
  const store = createMemoryAssistantAttachmentBlobStore()
  const attachment = imageAttachment()
  const metadata = createAttachmentMetadata(attachment)

  await store.put({ ...metadata, blob: attachment.file })
  const loaded = await store.get('attachment-1')
  assert.equal(loaded?.name, 'screenshot.png')
  assert.equal(await loaded?.blob.text(), 'image')

  await store.deleteMany(['attachment-1'])
  assert.equal(await store.get('attachment-1'), null)
})

test('createAttachmentFile rebuilds a File from stored metadata and blob', async () => {
  const metadata = createAttachmentMetadata(imageAttachment())
  const file = createAttachmentFile(metadata, new Blob(['image'], { type: 'image/png' }))

  assert.equal(file.id, 'attachment-1')
  assert.equal(file.file.name, 'screenshot.png')
  assert.equal(file.file.type, 'image/png')
  assert.equal(await file.file.text(), 'image')
})
