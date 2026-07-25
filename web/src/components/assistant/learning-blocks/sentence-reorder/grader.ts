import type { ActivityResult } from '../../learning-activities/contracts.ts'

export function gradeSentenceReorder(
  answer: string[],
  acceptedOrders: string[][],
): ActivityResult {
  const correct = acceptedOrders.some(
    (order) => order.length === answer.length && order.every((id, index) => id === answer[index]),
  )
  return {
    correct,
    answer: [...answer],
    expected: [...(acceptedOrders[0] ?? [])],
  }
}
