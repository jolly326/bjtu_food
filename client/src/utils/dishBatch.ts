/**
 * 推荐卡片批次工具（口径见 docs/func/client/A2-美味.md「流末尾」）。
 *
 * - 随机盲选 = 重新取一批，**排除当前批次已出现的菜品**；若过滤后为空（该食堂只剩这些菜）
 *   则退回未过滤结果，避免「点了没反应」；
 * - 按 id 去重：后端分页理论不重复，这里做防御 —— 重复 id 会让卡片栈少渲染一层。
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

/** 取「本批未出现过」的一批；全部都出现过则原样返回 */
export function freshBatch<T extends { id: number }>(records: readonly T[], seen: readonly T[]): T[] {
  const seenIds = new Set(seen.map((item) => item.id))
  const fresh = records.filter((item) => !seenIds.has(item.id))
  return fresh.length ? fresh : [...records]
}
