import test from 'node:test'
import assert from 'node:assert/strict'

import { extractImageFilesFromClipboardData } from './assistantClipboardFiles.ts'

function imageFile(name = 'screenshot.png') {
  return new File(['image'], name, { type: 'image/png' })
}

test('extractImageFilesFromClipboardData reads image files from clipboard items', () => {
  const file = imageFile()
  const clipboardData = {
    items: [
      {
        kind: 'file',
        type: 'image/png',
        getAsFile: () => file,
      },
    ],
    files: [],
  } as unknown as DataTransfer

  const files = extractImageFilesFromClipboardData(clipboardData)

  assert.equal(files.length, 1)
  assert.equal(files[0]?.type, 'image/png')
  assert.match(files[0]?.name ?? '', /^pasted-image-\d+-0\.png$/)
})

test('extractImageFilesFromClipboardData falls back to clipboard files', () => {
  const clipboardData = {
    items: [],
    files: [imageFile('clipboard.png')],
  } as unknown as DataTransfer

  const files = extractImageFilesFromClipboardData(clipboardData)

  assert.equal(files.length, 1)
  assert.equal(files[0]?.name, 'clipboard.png')
})
