/**
 * 记忆卡辅助行文案（唯一实现）：`食堂 · 档口 · N元`。
 *
 * - 缺失项自动跳过（无价格即只显示位置，反之亦然）
 * - 价格单位「分」→ 元，**整数去掉 `.00`**（沿用 `fenToYuan` 两位定点后转数值）
 */
import { fenToYuan } from './price'

/** 记录卡辅助行所需的最小字段集（与 `RecordOut` 兼容） */
export interface RecordPlaceFields {
  canteenName?: string
  stallName?: string
  price?: number
}

export function recordMetaLine(record: RecordPlaceFields): string {
  const place = [record.canteenName, record.stallName].filter(Boolean).join(' · ')
  const yuan = record.price && record.price > 0 ? `${Number(fenToYuan(record.price))}元` : ''
  return [place, yuan].filter(Boolean).join(' · ')
}
