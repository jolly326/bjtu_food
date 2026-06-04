/** 模拟自增 ID */
let _id = 0
export function nextId<T = bigint>(): T {
  return ++_id as unknown as T
}

/** 按 id 查找数组下标 */
export function findIdx<T extends { id: bigint }>(arr: T[], id: number): number {
  return arr.findIndex(item => Number(item.id) === id)
}

