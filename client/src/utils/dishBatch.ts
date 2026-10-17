/**
 * 推荐卡片批次工具（口径见 docs/func/client/A2-推荐菜品.md「流末尾」）。
 *
 * 随机盲选 = 重新取一批，**排除当前批次已出现的菜品**；若过滤后为空（该食堂只剩这些菜）
 * 则退回未过滤结果，避免「点了没反应」。
 */
export function freshBatch<T extends { id: number }>(records: T[], seen: readonly T[]): T[] {
  const seenIds = new Set(seen.map((item) => item.id))
  const fresh = records.filter((item) => !seenIds.has(item.id))
  return fresh.length ? fresh : records
}
