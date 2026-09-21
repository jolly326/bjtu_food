/**
 * 价格筛选区间的**共享口径**（唯一真源）。
 *
 * 抽离动机（2026-09-21 home-ui-refresh）：价格维度有两个消费方——搜索页私有的
 * `pages/find/FilterBar.vue`（胶囊行 + 下拉）与本次首页新增的「筛选」面板
 * （`pages/home/HomeFilterPanel.vue`，白底弹层）。预设清单 / 回显文案 / 选中项匹配
 * 若各自维护一份，必然漂移（同一区间在两处显示成不同文案、或预设命中口径不一致）。
 *
 * 口径红线：本文件的区间单位**一律为「元」**，与 store 的 `filterPrice`、
 * 组件 `price-select` 事件载荷同源；元→分换算只发生在 API 层（`utils/money.yuanToFen`），
 * 视图层禁止任何 ×/÷100 换算。
 */

/** 价格筛选区间（元；`undefined` = 该侧不限） */
export interface PriceRange {
  min?: number
  max?: number
}

/**
 * 预设区间：不限 / 0–10 / 10–20 / 20 元以上。
 * 顺序即面板渲染顺序（由窄到宽递进，末项为开口区间）。
 */
export const PRICE_PRESETS = [
  { key: 'all', label: '不限', min: undefined, max: undefined },
  { key: '0-10', label: '0–10 元', min: 0, max: 10 },
  { key: '10-20', label: '10–20 元', min: 10, max: 20 },
  { key: '20+', label: '20 元以上', min: 20, max: undefined },
] as const

export type PricePresetKey = (typeof PRICE_PRESETS)[number]['key']

/** 金额展示归一：最多两位小数、去掉整数的多余尾零（10 显示「10」，4.5 显示「4.5」）；空值兜底空串 */
export function formatYuan(v: number | null | undefined): string {
  if (v == null) return ''
  return String(Math.round(v * 100) / 100)
}

/**
 * 区间 → 回显文案（胶囊 / 按钮 / 面板摘要共用）：
 * 无限 = 「全部价格」；单侧 = 「X 元以上」/「X 元以下」；双侧 = 「X-Y 元」。
 */
export function priceRangeLabel(range: PriceRange): string {
  const min = range.min
  const max = range.max
  if (min == null && max == null) return '全部价格'
  if (min != null && max == null) return `${formatYuan(min)} 元以上`
  if (min == null && max != null) return `${formatYuan(max)} 元以下`
  return `${formatYuan(min)}-${formatYuan(max)} 元`
}

/** 当前区间命中的预设键；自定义区间不匹配任何预设 → 返回空串（面板不预选任何预设） */
export function matchPresetKey(range: PriceRange): PricePresetKey | '' {
  const hit = PRICE_PRESETS.find((p) => p.min === range.min && p.max === range.max)
  return hit ? hit.key : ''
}

/** 输入串（元）→ 元数值：空串 / 非法值返回 undefined（表示不限）。单位即元，禁止任何 ×/÷ 换算。 */
export function toYuan(v: string): number | undefined {
  if (v === '') return undefined
  const n = Number(v)
  if (!Number.isFinite(n)) return undefined
  return n
}

/** 区间合法性纠正：min > max 时交换（面板「确定」与预设提交共用同一纠正口径） */
export function normalizeRange(min?: number, max?: number): PriceRange {
  if (min !== undefined && max !== undefined && min > max) {
    return { min: max, max: min }
  }
  return { min, max }
}
