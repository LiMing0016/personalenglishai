import type {
  AssistantAttachment,
  AssistantAttachmentMetadata,
  AssistantConversation,
  AssistantMessage,
} from './assistantMock.ts'

function messageKey(message: AssistantMessage) {
  return `${message.role}\u0000${message.content}`
}

function cloneAttachments(attachments: AssistantAttachment[]) {
  return attachments.map((attachment) => ({ ...attachment }))
}

function cloneMetadata(metadata: AssistantAttachmentMetadata[]) {
  return metadata.map((attachment) => ({ ...attachment }))
}

function cloneMessage(message: AssistantMessage): AssistantMessage {
  return {
    ...message,
    attachments: message.attachments?.length ? cloneAttachments(message.attachments) : undefined,
    attachmentMetadata: message.attachmentMetadata?.length
      ? cloneMetadata(message.attachmentMetadata)
      : undefined,
  }
}

export function mergeTransientMessageAttachments(
  previous: AssistantConversation | undefined,
  next: AssistantConversation,
): AssistantConversation {
  if (!previous) {
    return next
  }

  const attachmentQueues = new Map<string, AssistantAttachment[][]>()
  const metadataQueues = new Map<string, AssistantAttachmentMetadata[][]>()
  for (const message of previous.messages) {
    if (message.role !== 'user') {
      continue
    }
    const key = messageKey(message)
    if (message.attachments?.length) {
      const queue = attachmentQueues.get(key) ?? []
      queue.push(cloneAttachments(message.attachments))
      attachmentQueues.set(key, queue)
      continue
    }
    if (message.attachmentMetadata?.length) {
      const queue = metadataQueues.get(key) ?? []
      queue.push(cloneMetadata(message.attachmentMetadata))
      metadataQueues.set(key, queue)
    }
  }

  if (attachmentQueues.size === 0 && metadataQueues.size === 0) {
    return next
  }

  return {
    ...next,
    messages: next.messages.map((message) => {
      if (message.role !== 'user' || message.attachments?.length) {
        return message
      }
      const queue = attachmentQueues.get(messageKey(message))
      const attachments = queue?.shift()
      if (attachments?.length) {
        return {
          ...message,
          attachments,
          attachmentMetadata: cloneMetadata(attachments),
        }
      }
      const metadataQueue = metadataQueues.get(messageKey(message))
      const attachmentMetadata = metadataQueue?.shift()
      return attachmentMetadata?.length ? { ...message, attachmentMetadata } : message
    }),
  }
}

export function mergeRemoteConversationListWithTransientAttachments(
  current: AssistantConversation[],
  remote: AssistantConversation[],
): AssistantConversation[] {
  const currentById = new Map(current.map((conversation) => [conversation.id, conversation]))
  return remote.map((conversation) => {
    const previous = currentById.get(conversation.id)
    if (conversation.messages.length === 0 && previous?.messages.length) {
      return {
        ...conversation,
        messages: previous.messages.map(cloneMessage),
      }
    }
    return mergeTransientMessageAttachments(previous, conversation)
  })
}
