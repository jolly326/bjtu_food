import { get } from './http'

/**
 * 菜品描述四维字典项（`GET /dishes/attributes`，`project_spec.md` §7.40 R4）。
 *
 * - `field` **恒等于菜品出参字段名**（`dietType` / `ingredients` / `flavorTags` / `serveTemp`），
 *   消费方据此**直接匹配**渲染，不得另建「字典 field → VO 字段」的第二套映射（R13）；
 * - 业务数据仍下发**机器值**，中文仅由本字典提供 —— 保留筛选 / 统计锚点。
 */
export interface DishAttribute {
  /** 维度字段名（= 菜品出参字段名） */
  field: string
  /** 机器值（菜品出参里出现的值，如 `meat` / `chicken`） */
  value: string
  /** 中文标签（消费端直接渲染，如 `荤` / `鸡`） */
  label: string
  /** 组内展示顺序（升序） */
  order: number
}

/**
 * 拉取四维字典（公开接口）。
 *
 * **后端唯一真源**（`DishAttributeConst`）：小程序端与管理端共用同一份 ——
 * 两端均 SHALL NOT 再硬编码「机器值 → 中文」映射表或表单选项数组
 * （改动前两端各持一份，连同后端常量共成三套真源，违反 PR-12）。
 */
export async function getDishAttributes(): Promise<DishAttribute[]> {
  const raw = await get<DishAttribute[]>('/dishes/attributes')
  return raw || []
}
