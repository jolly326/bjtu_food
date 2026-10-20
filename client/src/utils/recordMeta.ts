/**
 * 记忆卡文案（唯一实现）：位置 `食堂 档口`（空格分隔）与价格 `14元` 分成两段各自取用。
 *
 * - 缺失项自动跳过（无档口即只显示食堂，无位置时位置行为空）
 * - 价格单位「分」→ 元，**整数去掉 `.00`**（沿用 `fenToYuan` 两位定点后转数值）
 */
import { fenToYuan } from './price'

/** 记忆卡文案所需的最小字段集（与 `RecordOut` 兼容） */
export interface RecordPlaceFields {
  canteenName?: string
  stallName?: string
  price?: number
}

/** 位置文案：`食堂 档口`（空格分隔，缺失项跳过；皆缺为空串） */
export function recordPlaceLine(record: RecordPlaceFields): string {
  return [record.canteenName, record.stallName].filter(Boolean).join(' ')
}

/** 价格文案：`14元`（无有效价格为空串） */
export function recordPriceText(record: RecordPlaceFields): string {
  return record.price && record.price > 0 ? `${Number(fenToYuan(record.price))}元` : ''
}