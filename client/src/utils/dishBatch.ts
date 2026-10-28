/**
 * 菜品列表工具：按 `id` 去重。
 *
 * 美食地图全量铺放前统一去重 —— 重复 id 会让同一磁贴渲染两次并互相遮挡。
 */

/** 按 `id` 去重（保留首次出现的顺序） */
export function uniqueById<T extends { id: number }>(list: readonly T[]): T[] {
  const seen = new Set<number>()
  const output: T[] = []
  for (const item of list) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    output.push(item)
  }
  return output
}
