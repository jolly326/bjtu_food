/**
 * 纠错表单的**纯逻辑**：改动比对与售价校验（无响应式状态、无副作用，可完整单测）。
 *
 * 编排（详情加载 / 属性候选 / 提交与限频）留在 `useCorrection`，本文件只负责「算出改了哪些字段」。
 * 之所以独立成模块：比对口径错了会**静默产生「无意义改动」提交**（弹层里取消再勾回若按顺序比对，
 * 就会误判为有改动），是最易改坏的部分，必须能被测试覆盖。
 *
 * ⚠️ 金额红线：元 → 分 的换算只在组装 payload 时做（`yuanToFen`），比对层不碰金额单位。
 */
import { yuanToFen } from '@/utils/money'
import type { AttributeEditor, CorrectionFormModel } from './useCorrection'
import type { DishCorrectionPayload } from '@/types/feedback'

/** 预填基线快照（与 `form` 同构的只读副本，由加载完成后写入） */
export interface CorrectionBaseline {
  name: string
  price: string
  canteenName: string
  floor: string
  stallName: string
  images: string[]
  /** 维度键 → 预填时的选中值集合 */
  attributes: Record<string, string[]>
}

/**
 * 有序比对（图片顺序即展示顺序，顺序变化属真实改动）。
 *
 * <p>⚠️ 与 `sameValueSet` 的区别是**刻意的**：图片有前后顺序，属性值没有。
 */
export function sameList(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false
  return a.every((x, i) => x === b[i])
}

/**
 * 无序比对（属性值是无序集合）：弹层内「取消后再勾回」会按 toggle 追加改变 `selected` 次序，
 * 若按顺序比对，同一组值会被误判为「有改动」⇒ 空改动可提交、后端记为一次无意义修改。
 * 故维度值只比集合成员（长度 + 逐一命中），顺序不同不算改动。
 *
 * <p>注：只比「长度 + 成员命中」而非建双向 Set 相等，是因为前者在存在重复项时
 * 仍与后端语义一致（同一维度两次提交同一个值无意义，无需拆开）。
 */
export function sameValueSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false
  const set = new Set(a)
  return b.every((x) => set.has(x))
}

/** 售价格式：正数、至多两位小数（元），上限 9999 元 */
const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

/**
 * 售价格式校验。
 * @param raw 用户输入的**元字符串**（可能带首尾空白）
 */
export function priceValid(raw: string): boolean {
  const p = raw.trim()
  if (!p) return false
  const n = Number(p)
  return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
}

/**
 * 与基线逐项比对得出的**改动集合**（即提交请求体；空对象 = 无改动）。
 *
 * <p><b>局部提交</b>：只上传「当前值 ≠ 预填值」的字段，未改动的不上传。
 * <p><b>命名即口径</b>：`dishName` 为空 = 详情未就绪（此时全表单为空，比对无意义）⇒ 直接返回空改动，
 * 由调用方的「加载中」态承担拦截。
 */
export function buildCorrectionDiff(
  dishName: string,
  form: CorrectionFormModel,
  baseline: CorrectionBaseline,
): DishCorrectionPayload {
  const payload: DishCorrectionPayload = {}
  if (!dishName) return payload
  if (form.name.trim() !== baseline.name) payload.name = form.name.trim()
  // 金额：元字符串 → 分（金额红线；比对层只判是否改动）
  if (form.price.trim() !== baseline.price) payload.price = yuanToFen(Number(form.price.trim() || 0))
  if (form.canteenName.trim() !== baseline.canteenName) payload.canteenName = form.canteenName.trim()
  if (form.floor.trim() !== baseline.floor) payload.floor = form.floor.trim()
  if (form.stallName.trim() !== baseline.stallName) payload.stallName = form.stallName.trim()
  if (!sameList(form.images, baseline.images)) payload.images = form.images.filter(Boolean)
  const attrs: Record<string, string | string[]> = {}
  for (const ed of form.attributes) {
    // 维度值按集合比对（顺序不同不算改动），图片仍按顺序比对（见上）
    if (sameValueSet(ed.selected, baseline.attributes[ed.fieldKey] ?? [])) continue
    attrs[ed.fieldKey] = ed.valueType === 'multi' ? ed.selected : (ed.selected[0] ?? '')
  }
  if (Object.keys(attrs).length) payload.attributes = attrs
  return payload
}

/** 从编辑项快照基线（加载完成后调用：把当时的选中值冻结为「预填值」） */
export function snapshotAttributes(attributes: AttributeEditor[]): Record<string, string[]> {
  const map: Record<string, string[]> = {}
  for (const ed of attributes) map[ed.fieldKey] = [...ed.selected]
  return map
}